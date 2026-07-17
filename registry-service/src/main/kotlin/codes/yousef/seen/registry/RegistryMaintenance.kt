package codes.yousef.seen.registry

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.Base64

enum class RegistryMaintenanceMode(
    val command: String,
    val signingRoles: Set<String>,
    val signingOperation: TufSigningOperation?,
    val requiresPublicationLease: Boolean,
    val allowImmutableMetadataCreates: Boolean,
    val allowRootPointerWrite: Boolean,
    val requiresBootstrapEnvelopes: Boolean = false,
) {
    IMPORT_OFFLINE_BOOTSTRAP(
        command = "import-offline-bootstrap",
        signingRoles = emptySet(),
        signingOperation = null,
        requiresPublicationLease = false,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = true,
        requiresBootstrapEnvelopes = true,
    ),
    ONLINE_BOOTSTRAP(
        command = "bootstrap-online",
        signingRoles = TufRole.ONLINE.toSet(),
        signingOperation = TufSigningOperation.BOOTSTRAP,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    TARGETS_RENEWAL(
        command = "import-offline-targets-renewal",
        signingRoles = setOf(TufRole.SNAPSHOT, TufRole.TIMESTAMP),
        signingOperation = TufSigningOperation.TARGETS_RENEWAL,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    TARGETS_ROTATION_RELEASES(
        command = "import-offline-targets-rotation",
        signingRoles = setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
        signingOperation = TufSigningOperation.TARGETS_ROTATION_RELEASES,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    TARGETS_ROTATION_SECURITY(
        command = "import-offline-targets-rotation",
        signingRoles = setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
        signingOperation = TufSigningOperation.TARGETS_ROTATION_SECURITY,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    ROOT_VERIFY(
        command = "verify-root-chain",
        signingRoles = emptySet(),
        signingOperation = null,
        requiresPublicationLease = false,
        allowImmutableMetadataCreates = false,
        allowRootPointerWrite = false,
    ),
    ROOT_IMPORT(
        command = "import-offline-root-rotation",
        signingRoles = emptySet(),
        signingOperation = null,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = true,
    ),
    REFRESH_RELEASES(
        command = "refresh-releases-once",
        signingRoles = setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
        signingOperation = TufSigningOperation.RELEASE,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    REFRESH_SECURITY(
        command = "refresh-security-once",
        signingRoles = setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
        signingOperation = TufSigningOperation.SECURITY,
        requiresPublicationLease = true,
        allowImmutableMetadataCreates = true,
        allowRootPointerWrite = false,
    ),
    ;

    init {
        require((signingOperation == null) == signingRoles.isEmpty())
        require(signingRoles.all { requireNotNull(signingOperation).permitsRole(it) })
    }
}

data class RegistryMaintenanceInvocation(
    val mode: RegistryMaintenanceMode,
    val input: Path? = null,
) {
    companion object {
        fun parse(args: Array<String>): RegistryMaintenanceInvocation? = when (args.firstOrNull()) {
            "import-offline-bootstrap" -> {
                require(args.size == 1) { "Usage: registry-service import-offline-bootstrap" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP)
            }
            "bootstrap-online" -> {
                require(args.size == 1) { "Usage: registry-service bootstrap-online" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.ONLINE_BOOTSTRAP)
            }
            "import-offline-targets-renewal" -> {
                require(args.size == 2) { "Usage: registry-service import-offline-targets-renewal <N.targets.json>" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.TARGETS_RENEWAL, Path.of(args[1]))
            }
            "import-offline-targets-rotation" -> {
                require(args.size == 3) {
                    "Usage: registry-service import-offline-targets-rotation <releases|security> <N.targets.json>"
                }
                val mode = when (args[1]) {
                    TufRole.RELEASES -> RegistryMaintenanceMode.TARGETS_ROTATION_RELEASES
                    TufRole.SECURITY -> RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY
                    else -> throw IllegalArgumentException("Targets rotation role must be releases or security")
                }
                RegistryMaintenanceInvocation(mode, Path.of(args[2]))
            }
            "verify-root-chain" -> {
                require(args.size == 1) { "Usage: registry-service verify-root-chain" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.ROOT_VERIFY)
            }
            "import-offline-root-rotation" -> {
                require(args.size == 2) { "Usage: registry-service import-offline-root-rotation <N.root.json>" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.ROOT_IMPORT, Path.of(args[1]))
            }
            "refresh-releases-once" -> {
                require(args.size == 1) { "Usage: registry-service refresh-releases-once" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.REFRESH_RELEASES)
            }
            "refresh-security-once" -> {
                require(args.size == 1) { "Usage: registry-service refresh-security-once" }
                RegistryMaintenanceInvocation(RegistryMaintenanceMode.REFRESH_SECURITY)
            }
            "bootstrap" -> throw IllegalArgumentException(
                "Combined bootstrap authority is disabled; run import-offline-bootstrap and bootstrap-online as separate jobs",
            )
            else -> null
        }
    }
}

data class RegistryMaintenanceConfig(
    val mode: RegistryMaintenanceMode,
    val environment: String,
    val repositoryId: String,
    val registryOrigin: String,
    val storageMode: String,
    val projectId: String?,
    val firestoreDatabase: String?,
    val metadataBucket: String?,
    val objectPrefix: String,
    val onlinePublicKeysHex: Map<String, String>,
    val remoteOnlineSignerTargets: Map<String, RemoteTufSignerTarget>,
    val bootstrapRootEnvelopeBase64: String? = null,
    val bootstrapTargetsEnvelopeBase64: String? = null,
) {
    init {
        require(environment == "development") {
            "This staged maintenance runtime is development-only"
        }
        require(registryOrigin == "https://seen.dev.yousef.codes/packages") {
            "Maintenance is pinned to the official development registry origin"
        }
        require(onlinePublicKeysHex.keys == TufRole.ONLINE.toSet()) {
            "All and only online TUF public keys are required"
        }
        val publicKeys = onlinePublicKeysHex.values.map(String::hexToBytes)
        require(publicKeys.all { it.size == 32 } && publicKeys.map(::tufKeyId).toSet().size == TufRole.ONLINE.size) {
            "Online TUF roles must use four distinct Ed25519 public keys"
        }
        require(remoteOnlineSignerTargets.keys == mode.signingRoles) {
            "${mode.command} signer URLs must exactly match ${mode.signingRoles.sorted().joinToString(", ").ifEmpty { "the empty role set" }}"
        }
        require(remoteOnlineSignerTargets.values.map { it.endpoint }.toSet().size == remoteOnlineSignerTargets.size) {
            "Online TUF roles must use distinct signer endpoints"
        }
        require(remoteOnlineSignerTargets.values.map { it.audience }.toSet().size == remoteOnlineSignerTargets.size) {
            "Online TUF roles must use distinct signer audiences"
        }
        if (storageMode == "gcp") {
            require(!projectId.isNullOrBlank()) { "GOOGLE_CLOUD_PROJECT is required for GCP maintenance" }
            require(!metadataBucket.isNullOrBlank()) { "REGISTRY_METADATA_BUCKET is required for GCP maintenance" }
            if (mode.requiresPublicationLease) {
                require(!firestoreDatabase.isNullOrBlank()) { "REGISTRY_FIRESTORE_DATABASE is required for publishing maintenance" }
            } else {
                require(firestoreDatabase == null) { "This maintenance phase must not receive Firestore configuration" }
            }
        } else {
            require(storageMode == "memory") { "REGISTRY_STORAGE_MODE must be memory or gcp" }
            require(projectId == null && firestoreDatabase == null && metadataBucket == null) {
                "Memory maintenance must not receive cloud project, database, or bucket configuration"
            }
        }
        if (mode.requiresBootstrapEnvelopes) {
            require(!bootstrapRootEnvelopeBase64.isNullOrBlank() && !bootstrapTargetsEnvelopeBase64.isNullOrBlank()) {
                "Offline bootstrap import requires both pre-signed envelopes"
            }
        } else {
            require(bootstrapRootEnvelopeBase64 == null && bootstrapTargetsEnvelopeBase64 == null) {
                "Only the offline bootstrap importer may receive bootstrap envelopes"
            }
        }
    }

    companion object {
        fun fromEnvironment(
            mode: RegistryMaintenanceMode,
            env: Map<String, String> = System.getenv(),
        ): RegistryMaintenanceConfig {
            rejectMaintenanceEnvironment(mode, env)
            val storageMode = env["REGISTRY_STORAGE_MODE"] ?: "gcp"
            return RegistryMaintenanceConfig(
                mode = mode,
                environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
                repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
                registryOrigin = (env["REGISTRY_ORIGIN"] ?: "https://seen.dev.yousef.codes/packages").trimEnd('/'),
                storageMode = storageMode,
                projectId = env["GOOGLE_CLOUD_PROJECT"]?.takeIf { storageMode == "gcp" },
                firestoreDatabase = if (storageMode == "gcp" && mode.requiresPublicationLease) {
                    env["REGISTRY_FIRESTORE_DATABASE"] ?: "seen-registry-dev"
                } else null,
                metadataBucket = env["REGISTRY_METADATA_BUCKET"]?.takeIf { storageMode == "gcp" },
                objectPrefix = env["REGISTRY_OBJECT_PREFIX"]?.trim('/') ?: "v1",
                onlinePublicKeysHex = TufRole.ONLINE.mapNotNull { role ->
                    env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]?.let { role to it }
                }.toMap(),
                remoteOnlineSignerTargets = env.remoteSignerTargets(),
                bootstrapRootEnvelopeBase64 = env["REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64"]
                    ?.takeIf { mode.requiresBootstrapEnvelopes },
                bootstrapTargetsEnvelopeBase64 = env["REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64"]
                    ?.takeIf { mode.requiresBootstrapEnvelopes },
            )
        }
    }
}

private fun rejectMaintenanceEnvironment(
    mode: RegistryMaintenanceMode,
    env: Map<String, String>,
) {
    val exactAlwaysForbidden = setOf(
        "REGISTRY_SERVER_MODE",
        "REGISTRY_QUARANTINE_BUCKET",
        "REGISTRY_PUBLIC_BUCKET",
        "REGISTRY_WRITER_MODE",
        "REGISTRY_WRITER_TOKEN",
        "REGISTRY_WRITER_PRINCIPAL",
        "REGISTRY_OWNER_ALLOWLIST",
        "REGISTRY_WRITERS_ENABLED",
        "REGISTRY_PUBLIC_DELAY_SECONDS",
        "REGISTRY_TRUST_AND_SAFETY_TOKEN",
        "REGISTRY_TRUST_AND_SAFETY_PRINCIPAL",
        "REGISTRY_SECURITY_TOKEN",
        "REGISTRY_SECURITY_PRINCIPAL",
    )
    val allowedPublicKeyNames = TufRole.ONLINE.mapTo(mutableSetOf()) { role ->
        "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"
    }
    val allowedRemoteNames = mode.signingRoles.flatMapTo(mutableSetOf()) { role ->
        val prefix = "REGISTRY_TUF_${role.uppercase()}_SIGNER"
        listOf("${prefix}_URL", "${prefix}_AUDIENCE")
    }
    val allowedBootstrapNames = if (mode.requiresBootstrapEnvelopes) {
        setOf(
            "REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64",
            "REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64",
        )
    } else emptySet()

    val forbidden = env.keys.filter { name ->
        name in exactAlwaysForbidden ||
            name.startsWith("REGISTRY_OFFLINE_") ||
            name.startsWith("REGISTRY_GITHUB_") ||
            name.startsWith("REGISTRY_GITLAB_") ||
            name.startsWith("REGISTRY_TUF_SIGNER_") ||
            (name.startsWith("REGISTRY_BOOTSTRAP_") && name !in allowedBootstrapNames) ||
            (name.startsWith("REGISTRY_KMS_") && name !in allowedPublicKeyNames) ||
            (name.startsWith("REGISTRY_TUF_") && name !in allowedRemoteNames) ||
            name.contains("PRIVATE_KEY") ||
            name.contains("PKCS8") ||
            (!mode.requiresPublicationLease && name == "REGISTRY_FIRESTORE_DATABASE") ||
            (env["REGISTRY_STORAGE_MODE"] == "memory" && name in setOf(
                "GOOGLE_CLOUD_PROJECT",
                "REGISTRY_FIRESTORE_DATABASE",
                "REGISTRY_METADATA_BUCKET",
            ))
    }
    require(forbidden.isEmpty()) {
        "${mode.command} rejects unrelated authority and storage configuration: ${forbidden.sorted().joinToString(", ")}"
    }
}

class RegistryMaintenanceResources private constructor(
    private val config: RegistryMaintenanceConfig,
    private val repository: RegistryRepository?,
    private val storage: RegistryObjectStorage,
    private val onlineSigners: TufOnlineSigners,
    private val tuf: TufPublisher?,
    private val clock: Clock,
) : AutoCloseable {
    internal val activeSigningRoles: Set<String> get() = config.mode.signingRoles
    internal val hasPublicationLeaseRepository: Boolean get() = config.mode.requiresPublicationLease

    fun importOfflineBootstrap(): TufBootstrapResult {
        require(config.mode == RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP)
        return TufBootstrapImporter(
            storage = storage,
            online = onlineSigners.publicKeys(),
            environment = config.environment,
            repositoryId = config.repositoryId,
            registryOrigin = config.registryOrigin,
            clock = clock,
        ).import(
            Base64.getDecoder().decode(requireNotNull(config.bootstrapRootEnvelopeBase64)),
            Base64.getDecoder().decode(requireNotNull(config.bootstrapTargetsEnvelopeBase64)),
        )
    }

    fun bootstrapOnline(): Long {
        require(config.mode == RegistryMaintenanceMode.ONLINE_BOOTSTRAP)
        require(storage.getMetadata("timestamp.json") == null) {
            "Online bootstrap metadata already exists; use the purpose-specific refresh jobs"
        }
        return requireTuf().publish(emptyList())
    }

    fun importTargetsRenewal(candidate: ByteArray): TufTargetsRenewalImportResult {
        require(config.mode == RegistryMaintenanceMode.TARGETS_RENEWAL)
        return requireTuf().importTargetsRenewal(candidate)
    }

    fun importTargetsRotation(candidate: ByteArray): TufTargetsRenewalImportResult {
        val role = when (config.mode) {
            RegistryMaintenanceMode.TARGETS_ROTATION_RELEASES -> TufRole.RELEASES
            RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY -> TufRole.SECURITY
            else -> throw IllegalArgumentException("This runtime is not a targets rotation runtime")
        }
        return requireTuf().importTargetsRotation(candidate, role)
    }

    fun importRootRotation(candidate: ByteArray): TufRootRotationImportResult {
        require(config.mode == RegistryMaintenanceMode.ROOT_IMPORT)
        return requireTuf().importRootRotation(candidate)
    }

    fun verifyRootChain(): Long {
        require(config.mode == RegistryMaintenanceMode.ROOT_VERIFY)
        return requireTuf().verifyStoredRootChain()
    }

    fun forceRefresh(): Long = when (config.mode) {
        RegistryMaintenanceMode.REFRESH_RELEASES -> requireTuf().forceRefreshReleases()
        RegistryMaintenanceMode.REFRESH_SECURITY -> requireTuf().forceRefreshSecurity()
        else -> throw IllegalArgumentException("This runtime is not a purpose-specific refresh runtime")
    }

    private fun requireTuf(): TufPublisher = requireNotNull(tuf) {
        "The offline bootstrap importer has no online publisher"
    }

    override fun close() {
        onlineSigners.close()
        repository?.close()
    }

    companion object {
        fun create(
            config: RegistryMaintenanceConfig,
            clock: Clock = Clock.systemUTC(),
            leaseRepositoryFactory: (RegistryMaintenanceConfig) -> RegistryRepository = { maintenance ->
                if (maintenance.storageMode == "gcp") {
                    FirestoreRegistryRepository.create(
                        projectId = requireNotNull(maintenance.projectId),
                        databaseId = requireNotNull(maintenance.firestoreDatabase),
                    )
                } else {
                    InMemoryRegistryRepository()
                }
            },
            metadataStorageFactory: (RegistryMaintenanceConfig) -> RegistryObjectStorage = { maintenance ->
                if (maintenance.storageMode == "gcp") {
                    GcsMetadataOnlyRegistryObjectStorage.create(
                        projectId = requireNotNull(maintenance.projectId),
                        metadataBucket = requireNotNull(maintenance.metadataBucket),
                        prefix = maintenance.objectPrefix,
                        allowImmutableCreates = maintenance.mode.allowImmutableMetadataCreates,
                        allowRootPointerWrite = maintenance.mode.allowRootPointerWrite,
                    )
                } else {
                    RestrictedMetadataRegistryObjectStorage(
                        allowImmutableCreates = maintenance.mode.allowImmutableMetadataCreates,
                        allowRootPointerWrite = maintenance.mode.allowRootPointerWrite,
                        allowTimestampPointerWrite = maintenance.mode.signingRoles.isNotEmpty(),
                    )
                }
            },
            onlineSignersFactory: (
                RegistryMaintenanceConfig,
                (RemoteTufSignerTarget) -> RemoteTufTokenProvider,
            ) -> TufOnlineSigners = { maintenance, tokenProviderFactory ->
                if (maintenance.mode.signingRoles.isEmpty()) {
                    maintenancePublicKeyOnlySigners(maintenance.onlinePublicKeysHex)
                } else {
                    createRemoteTufOnlineSigners(
                        activeRoles = maintenance.mode.signingRoles,
                        operation = requireNotNull(maintenance.mode.signingOperation),
                        publicKeysHex = maintenance.onlinePublicKeysHex,
                        targets = maintenance.remoteOnlineSignerTargets,
                        tokenProviderFactory = tokenProviderFactory,
                    )
                }
            },
            remoteTokenProviderFactory: (RemoteTufSignerTarget) -> RemoteTufTokenProvider =
                { target -> GoogleCloudRunTufIdTokenProvider(target) },
        ): RegistryMaintenanceResources {
            val storage = metadataStorageFactory(config)
            val online = onlineSignersFactory(config, remoteTokenProviderFactory)
            if (config.mode == RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP) {
                return RegistryMaintenanceResources(config, null, storage, online, null, clock)
            }
            val repository = if (config.mode.requiresPublicationLease) {
                leaseRepositoryFactory(config)
            } else {
                InMemoryRegistryRepository()
            }
            val tuf = TufPublisher(
                repository = repository,
                storage = storage,
                online = online,
                environment = config.environment,
                repositoryId = config.repositoryId,
                registryOrigin = config.registryOrigin,
                clock = clock,
            )
            return RegistryMaintenanceResources(config, repository, storage, online, tuf, clock)
        }
    }
}

private fun maintenancePublicKeyOnlySigners(publicKeysHex: Map<String, String>): TufOnlineSigners {
    require(publicKeysHex.keys == TufRole.ONLINE.toSet())
    fun signer(role: String) = PublicKeyOnlyTufSigner(requireNotNull(publicKeysHex[role]).hexToBytes())
    return TufOnlineSigners(
        releases = signer(TufRole.RELEASES),
        security = signer(TufRole.SECURITY),
        snapshot = signer(TufRole.SNAPSHOT),
        timestamp = signer(TufRole.TIMESTAMP),
    )
}

object RegistryMaintenanceRuntime {
    fun run(
        invocation: RegistryMaintenanceInvocation,
        config: RegistryMaintenanceConfig = RegistryMaintenanceConfig.fromEnvironment(invocation.mode),
    ) {
        RegistryMaintenanceResources.create(config).use { resources ->
            when (invocation.mode) {
                RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP -> {
                    val result = resources.importOfflineBootstrap()
                    println("root_sha256=${sha256(result.root)}")
                    println("root_key_ids=${result.rootKeyIds.joinToString(",")}")
                    println("targets_sha256=${sha256(result.targets)}")
                    println("targets_key_ids=${result.targetsKeyIds.joinToString(",")}")
                }
                RegistryMaintenanceMode.ONLINE_BOOTSTRAP ->
                    println("online_transaction_version=${resources.bootstrapOnline()}")
                RegistryMaintenanceMode.TARGETS_RENEWAL -> {
                    val candidate = readInput(invocation)
                    val result = resources.importTargetsRenewal(candidate)
                    println("targets_version=${result.targetsVersion}")
                    println("targets_sha256=${sha256(candidate)}")
                    println("online_transaction_version=${result.onlineTransactionVersion}")
                }
                RegistryMaintenanceMode.TARGETS_ROTATION_RELEASES,
                RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY -> {
                    val candidate = readInput(invocation)
                    val result = resources.importTargetsRotation(candidate)
                    println("targets_version=${result.targetsVersion}")
                    println("targets_sha256=${sha256(candidate)}")
                    println("affected_role=${invocation.mode.signingOperation?.changingRole}")
                    println("online_transaction_version=${result.onlineTransactionVersion}")
                }
                RegistryMaintenanceMode.ROOT_VERIFY ->
                    println("root_version=${resources.verifyRootChain()}")
                RegistryMaintenanceMode.ROOT_IMPORT -> {
                    val candidate = readInput(invocation)
                    val result = resources.importRootRotation(candidate)
                    println("root_version=${result.rootVersion}")
                    println("root_sha256=${sha256(candidate)}")
                }
                RegistryMaintenanceMode.REFRESH_RELEASES,
                RegistryMaintenanceMode.REFRESH_SECURITY ->
                    println("online_transaction_version=${resources.forceRefresh()}")
            }
        }
    }

    private fun readInput(invocation: RegistryMaintenanceInvocation): ByteArray =
        Files.readAllBytes(requireNotNull(invocation.input))
}
