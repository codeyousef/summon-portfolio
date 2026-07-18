package codes.yousef.seen.registry

import java.time.Clock
import java.time.Duration

enum class RegistryWorkerMode(val argument: String) {
    SOURCE("verify-source-once"),
    SCAN("scan-once"),
    PROMOTE("promote-once");

    companion object {
        fun fromArgument(value: String): RegistryWorkerMode? = entries.firstOrNull { it.argument == value }
    }
}

data class RegistryWorkerConfig(
    val mode: RegistryWorkerMode,
    val projectId: String,
    val firestoreDatabase: String,
    val quarantineBucket: String,
    val publicBucket: String?,
    val metadataBucket: String?,
    val objectPrefix: String,
    val environment: String,
    val repositoryId: String,
    val registryOrigin: String,
    val publicDelay: Duration,
    val githubAppId: String?,
    val githubAppPrivateKeyPem: String?,
    val gitlabToken: String?,
    val kmsPublicKeysHex: Map<String, String>,
    val remoteSignerTargets: Map<String, RemoteTufSignerTarget>,
) {
    init {
        require(environment == "development") { "Review workers are development-only" }
        require(publicDelay >= Duration.ofHours(72)) { "Public review delay must be at least 72 hours" }
        require(objectPrefix.isNotBlank())
        val githubAppConfigured = !githubAppId.isNullOrBlank() && !githubAppPrivateKeyPem.isNullOrBlank()
        require(githubAppId.isNullOrBlank() == githubAppPrivateKeyPem.isNullOrBlank()) {
            "GitHub App ID and private key must be configured together"
        }
        if (mode == RegistryWorkerMode.SOURCE) {
            require(githubAppConfigured || !gitlabToken.isNullOrBlank()) {
                "A GitHub App credential or GitLab token is required for source verification"
            }
        }
        if (mode == RegistryWorkerMode.PROMOTE) {
            require(!publicBucket.isNullOrBlank() && !metadataBucket.isNullOrBlank()) {
                "Promotion requires public and metadata buckets"
            }
            require(kmsPublicKeysHex.keys == TufRole.ONLINE.toSet()) {
                "Promotion requires all and only the four online TUF public keys"
            }
            require(remoteSignerTargets.keys == PROMOTION_SIGNING_ROLES) {
                "Promotion requires exactly the releases, snapshot, and timestamp remote signers"
            }
        } else {
            require(remoteSignerTargets.isEmpty()) { "Source and scanner workers must not receive signer URLs" }
        }
    }

    companion object {
        fun fromEnvironment(mode: RegistryWorkerMode, env: Map<String, String> = System.getenv()): RegistryWorkerConfig {
            rejectCoordinatorKeyVersions(env)
            fun required(name: String): String = env[name]?.takeIf(String::isNotBlank)
                ?: error("$name is required for ${mode.argument}")
            fun token(name: String): String? = env[name]?.trim()?.takeIf(String::isNotEmpty)?.also {
                require(it.none(Char::isWhitespace)) { "$name must not contain whitespace" }
            }
            return RegistryWorkerConfig(
                mode = mode,
                projectId = required("GOOGLE_CLOUD_PROJECT"),
                firestoreDatabase = required("REGISTRY_FIRESTORE_DATABASE"),
                quarantineBucket = required("REGISTRY_QUARANTINE_BUCKET"),
                publicBucket = env["REGISTRY_PUBLIC_BUCKET"]?.takeIf(String::isNotBlank),
                metadataBucket = env["REGISTRY_METADATA_BUCKET"]?.takeIf(String::isNotBlank),
                objectPrefix = env["REGISTRY_OBJECT_PREFIX"]?.trim('/')?.takeIf(String::isNotBlank) ?: "v1",
                environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
                repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
                registryOrigin = (env["REGISTRY_ORIGIN"] ?: "https://seen.dev.yousef.codes/packages").trimEnd('/'),
                publicDelay = Duration.ofSeconds(env["REGISTRY_PUBLIC_DELAY_SECONDS"]?.toLongOrNull() ?: 259_200L),
                githubAppId = token("REGISTRY_GITHUB_APP_ID"),
                githubAppPrivateKeyPem = env["REGISTRY_GITHUB_APP_PRIVATE_KEY_PEM"]?.takeIf(String::isNotBlank),
                gitlabToken = token("REGISTRY_GITLAB_FORGE_TOKEN"),
                kmsPublicKeysHex = TufRole.ONLINE.mapNotNull { role ->
                    env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]?.takeIf(String::isNotBlank)?.let { role to it }
                }.toMap(),
                remoteSignerTargets = env.remoteSignerTargets(),
            )
        }

        val PROMOTION_SIGNING_ROLES: Set<String> =
            setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
    }
}

object RegistryWorkerRuntime {
    fun run(
        config: RegistryWorkerConfig,
        clock: Clock = Clock.systemUTC(),
        remoteTokenProviderFactory: (RemoteTufSignerTarget) -> RemoteTufTokenProvider =
            { target -> GoogleCloudRunTufIdTokenProvider(target) },
    ): ReviewWorkerOutcome {
        val repository = FirestoreRegistryRepository.create(config.projectId, config.firestoreDatabase)
        val storage = GcsRegistryObjectStorage.create(
            projectId = config.projectId,
            quarantineBucket = config.quarantineBucket,
            publicBucket = config.publicBucket ?: config.quarantineBucket,
            metadataBucket = config.metadataBucket ?: config.quarantineBucket,
            prefix = config.objectPrefix,
        )
        try {
            val inspector = StorageReviewArchiveInspector(storage, ArchiveValidator())
            val stateMachine = ReviewStateMachine(config.publicDelay)
            val outcome = when (config.mode) {
                RegistryWorkerMode.SOURCE -> runSource(config, repository, inspector, stateMachine, clock)
                RegistryWorkerMode.SCAN -> ScanReviewWorker(
                    repository,
                    inspector,
                    IsolatedPackageScanEngine(),
                    stateMachine,
                    clock,
                ).runOnce()
                RegistryWorkerMode.PROMOTE -> runPromoter(
                    config,
                    repository,
                    storage,
                    inspector,
                    stateMachine,
                    clock,
                    remoteTokenProviderFactory,
                )
            }
            println("review_worker=${config.mode.argument} outcome=${outcome.name.lowercase()}")
            if (outcome == ReviewWorkerOutcome.RETRYABLE_FAILURE) {
                error("Review worker recorded a retryable failure")
            }
            return outcome
        } finally {
            repository.close()
        }
    }

    private fun runSource(
        config: RegistryWorkerConfig,
        repository: RegistryRepository,
        inspector: ReviewArchiveInspector,
        stateMachine: ReviewStateMachine,
        clock: Clock,
    ): ReviewWorkerOutcome {
        val github = config.githubAppId?.let { appId ->
            GithubAppInstallationTokenProvider(appId, requireNotNull(config.githubAppPrivateKeyPem))
        }
        val client = JdkHttpForgeSourceClient(ForgeBearerTokenProvider { forge, repositoryId, installationIdentity ->
            when (forge) {
                SourceForge.GITHUB -> github?.credential(repositoryId, installationIdentity)
                    ?: throw SourceVerificationException(SourceVerificationFailure.FORGE_UNAVAILABLE)
                SourceForge.GITLAB -> config.gitlabToken?.let(::ForgeBearerCredential)
                    ?: throw SourceVerificationException(SourceVerificationFailure.FORGE_UNAVAILABLE)
            }
        })
        return SourceVerifier(client, clock).use { verifier ->
            SourceReviewWorker(
                repository,
                inspector,
                DefaultSourceProofEngine(verifier),
                stateMachine,
                clock,
            ).runOnce()
        }
    }

    private fun runPromoter(
        config: RegistryWorkerConfig,
        repository: RegistryRepository,
        storage: RegistryObjectStorage,
        inspector: ReviewArchiveInspector,
        stateMachine: ReviewStateMachine,
        clock: Clock,
        remoteTokenProviderFactory: (RemoteTufSignerTarget) -> RemoteTufTokenProvider,
    ): ReviewWorkerOutcome {
        val signers = createRemoteTufOnlineSigners(
            activeRoles = RegistryWorkerConfig.PROMOTION_SIGNING_ROLES,
            operation = TufSigningOperation.RELEASE,
            publicKeysHex = config.kmsPublicKeysHex,
            targets = config.remoteSignerTargets,
            tokenProviderFactory = remoteTokenProviderFactory,
        )
        return signers.use {
            val tuf = TufPublisher(
                repository,
                storage,
                signers,
                config.environment,
                config.repositoryId,
                config.registryOrigin,
                clock,
            )
            PromotionReviewWorker(
                repository,
                storage,
                inspector,
                ReleaseMetadataPublisher(tuf::publish),
                stateMachine,
                clock,
            ).runOnce()
        }
    }
}
