package codes.yousef.seen.registry

import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.respondJson
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    if (args.singleOrNull() == "serve-tuf-signer") {
        TufSignerServerRuntime.run(TufSignerServerConfig.fromEnvironment())
        return
    }
    args.singleOrNull()?.let(RegistryWorkerMode::fromArgument)?.let { mode ->
        RegistryWorkerRuntime.run(RegistryWorkerConfig.fromEnvironment(mode))
        return
    }
    if (args.singleOrNull() == "describe-signing-policy") {
        println("seen-tuf-development-v1 root=offline:2/3 targets=offline:2/2 releases=role-locked-signer:1/1 security=role-locked-signer:1/1 snapshot=role-locked-signer:1/1 timestamp=role-locked-signer:1/1 public-delay=259200 promotion=disabled")
        return
    }
    if (args.firstOrNull() == "prepare-offline-bootstrap") {
        require(args.size == 2) { "Usage: registry-service prepare-offline-bootstrap <output-dir>" }
        val result = prepareOfflineBootstrap(java.nio.file.Path.of(args[1]), OfflineBootstrapConfig.fromEnvironment())
        println("root_sha256=${sha256(result.root)}")
        println("root_key_ids=${result.rootKeyIds.joinToString(",")}")
        println("targets_sha256=${sha256(result.targets)}")
        println("targets_key_ids=${result.targetsKeyIds.joinToString(",")}")
        return
    }
    if (args.firstOrNull() == "prepare-offline-targets-renewal") {
        require(args.size == 4) {
            "Usage: registry-service prepare-offline-targets-renewal <trusted-root.json> <current-N.targets.json> <output-dir>"
        }
        val result = prepareOfflineTargetsRenewal(
            outputDirectory = java.nio.file.Path.of(args[3]),
            trustedRoot = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1])),
            currentTargets = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[2])),
            config = OfflineTargetsRenewalConfig.fromEnvironment(),
        )
        println("targets_version=${result.version}")
        println("targets_sha256=${sha256(result.targets)}")
        println("targets_key_ids=${result.targetsKeyIds.joinToString(",")}")
        return
    }
    if (args.firstOrNull() == "prepare-offline-targets-rotation") {
        require(args.size == 4) {
            "Usage: registry-service prepare-offline-targets-rotation <trusted-root.json> <current-N.targets.json> <output-dir>"
        }
        val result = prepareOfflineTargetsRotation(
            outputDirectory = java.nio.file.Path.of(args[3]),
            trustedRoot = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1])),
            currentTargets = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[2])),
            config = OfflineTargetsRotationConfig.fromEnvironment(),
        )
        println("targets_version=${result.version}")
        println("targets_sha256=${sha256(result.targets)}")
        println("targets_key_ids=${result.targetsKeyIds.joinToString(",")}")
        return
    }
    if (args.firstOrNull() == "prepare-offline-root-rotation") {
        require(args.size == 3) {
            "Usage: registry-service prepare-offline-root-rotation <current-N.root.json> <output-dir>"
        }
        val result = prepareOfflineRootRotation(
            outputDirectory = java.nio.file.Path.of(args[2]),
            currentRoot = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1])),
            config = OfflineRootRotationConfig.fromEnvironment(),
        )
        println("root_version=${result.version}")
        println("root_sha256=${sha256(result.root)}")
        println("previous_root_key_ids=${result.previousRootKeyIds.joinToString(",")}")
        println("next_root_key_ids=${result.nextRootKeyIds.joinToString(",")}")
        return
    }
    RegistryMaintenanceInvocation.parse(args)?.let { invocation ->
        RegistryMaintenanceRuntime.run(invocation)
        return
    }
    val requestedServerMode = args.singleOrNull()?.let(RegistryServerMode::fromArgument)
    require(args.isEmpty() || requestedServerMode != null) {
        "Usage: registry-service [serve-public-api|serve-release-actions|serve-security-actions|serve-tuf-signer|describe-signing-policy|verify-source-once|scan-once|promote-once|prepare-offline-bootstrap <output-dir>|prepare-offline-targets-renewal <trusted-root.json> <current-N.targets.json> <output-dir>|prepare-offline-targets-rotation <trusted-root.json> <current-N.targets.json> <output-dir>|prepare-offline-root-rotation <current-N.root.json> <output-dir>|import-offline-bootstrap|bootstrap-online|import-offline-targets-renewal <N.targets.json>|import-offline-targets-rotation <releases|security> <N.targets.json>|verify-root-chain|import-offline-root-rotation <N.root.json>|refresh-releases-once|refresh-security-once]"
    }
    val config = RegistryConfig.fromEnvironment(serverModeOverride = requestedServerMode)
    val resources = RegistryResources.create(config)
    resources.startAndWait()
}

class RegistryResources private constructor(
    private val config: RegistryConfig,
    private val repository: RegistryRepository,
    private val storage: RegistryObjectStorage,
    private val onlineSigners: TufOnlineSigners,
    val tuf: TufPublisher,
    private val clock: Clock,
    private val routes: RegistryRoutes?,
    private val enforcementRoutes: EnforcementRoutes,
    private val serverMode: RegistryServerMode,
    internal val activeSigningRoles: Set<String>,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private var server: RegistryHttpServer? = null

    fun startAndWait() {
        tuf.requireBootstrap()
        tuf.verifyFreshTransaction()
        val pipeline = routePipeline()
        server = RegistryHttpServer(
            config = VertxServerConfig(
                port = config.port,
                decompressionSupported = false,
                maxRequestBodySize = ArchivePolicy.MAX_COMPRESSED_BYTES.toInt() + 1,
            ),
            pipeline = pipeline,
            routes = routes,
            streamingUploadsEnabled = serverMode.exposesRegistryRoutes,
            fallback = { exchange -> exchange.respondJson(404, ErrorEnvelope(ApiError(
                code = "not_found",
                message = "Resource was not found",
                requestId = "req_0000000000000000",
                occurredAt = Clock.systemUTC().instant().utc(),
                retryable = false,
            )), RegistryJson) },
        )
        requireNotNull(server).start()
        val latch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread { close(); latch.countDown() })
        log.info("Seen registry listening on port {}", config.port)
        latch.await()
    }

    internal fun routePipeline(): Pipeline = Pipeline().apply {
        use(enforcementRoutes.router.asMiddleware())
        routes?.let { use(it.router.asMiddleware()) }
    }

    internal val hasRegistryRoutes: Boolean get() = routes != null

    override fun close() {
        server?.close()
        onlineSigners.close()
        repository.close()
    }

    companion object {
        fun create(
            config: RegistryConfig,
            clock: Clock = Clock.systemUTC(),
            signingRoles: Set<String> = config.configuredSigningRoles,
            remoteTokenProviderFactory: (RemoteTufSignerTarget) -> RemoteTufTokenProvider =
                { target -> GoogleCloudRunTufIdTokenProvider(target) },
        ): RegistryResources {
            require(signingRoles.all(TufRole.ONLINE::contains)) { "Unknown online signing role" }
            require(signingRoles == config.configuredSigningRoles) {
                "Runtime signing roles must match its validated configuration"
            }
            val repository: RegistryRepository = if (config.storageMode == "gcp") FirestoreRegistryRepository.create(config) else InMemoryRegistryRepository()
            val storage: RegistryObjectStorage = when {
                config.storageMode == "gcp" && config.serverMode == RegistryServerMode.PUBLIC_API ->
                    GcsRegistryObjectStorage.create(config)
                config.storageMode == "gcp" -> GcsMetadataOnlyRegistryObjectStorage.create(
                    projectId = requireNotNull(config.projectId),
                    metadataBucket = requireNotNull(config.metadataBucket),
                    prefix = config.objectPrefix,
                    allowImmutableCreates = true,
                    allowRootPointerWrite = false,
                )
                config.serverMode == RegistryServerMode.PUBLIC_API -> InMemoryRegistryObjectStorage()
                else -> RestrictedMetadataRegistryObjectStorage(
                    allowImmutableCreates = true,
                    allowRootPointerWrite = false,
                    allowTimestampPointerWrite = true,
                )
            }
            val online = if (config.storageMode == "gcp") {
                if (signingRoles.isEmpty()) {
                    publicKeyOnlyOnlineSigners(config.kmsOnlinePublicKeysHex)
                } else {
                    createRemoteTufOnlineSigners(
                        activeRoles = signingRoles,
                        operation = requireNotNull(config.signingOperation) {
                            "A signing runtime requires one pinned TUF operation"
                        },
                        publicKeysHex = config.kmsOnlinePublicKeysHex,
                        targets = config.remoteOnlineSignerTargets,
                        tokenProviderFactory = remoteTokenProviderFactory,
                    )
                }
            } else {
                fun signer(role: String): TufSigner {
                    val local = LocalEd25519Signer.fromPkcs8Base64(
                        requireNotNull(config.localOnlineSigningKeysPkcs8Base64[role]),
                    )
                    if (role in signingRoles) return local
                    return PublicKeyOnlyTufSigner(local.publicKey.copyOf()).also { local.close() }
                }
                TufOnlineSigners(
                    signer(TufRole.RELEASES),
                    signer(TufRole.SECURITY),
                    signer(TufRole.SNAPSHOT),
                    signer(TufRole.TIMESTAMP),
                )
            }
            val tuf = TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock)
            val registryRoutes = if (config.serverMode == RegistryServerMode.PUBLIC_API) {
                val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
                val auth = OpaqueDevWriterAuthenticator(config.writerToken, config.writerPrincipal, config.ownerAllowlist)
                RegistryRoutes(
                    service = service,
                    auth = auth,
                    clock = clock,
                    catalogNavigationLinks = CatalogNavigationLinks.fromRegistryOrigin(config.registryOrigin),
                )
            } else null
            val enforcementCredentials = when (config.serverMode) {
                RegistryServerMode.PUBLIC_API -> listOf(
                    OpaqueEnforcementCredential(
                        config.writerToken,
                        EnforcementPrincipal(config.writerPrincipal, setOf(EnforcementRoles.PUBLISHER)),
                    ),
                    OpaqueEnforcementCredential(
                        requireNotNull(config.trustAndSafetyToken),
                        EnforcementPrincipal(
                            config.trustAndSafetyPrincipal,
                            setOf(EnforcementRoles.TRUST_AND_SAFETY),
                        ),
                    ),
                )
                RegistryServerMode.RELEASE_ACTIONS -> listOf(OpaqueEnforcementCredential(
                    config.writerToken,
                    EnforcementPrincipal(config.writerPrincipal, setOf(EnforcementRoles.PUBLISHER)),
                ))
                RegistryServerMode.SECURITY_ACTIONS -> listOf(OpaqueEnforcementCredential(
                    requireNotNull(config.securityToken),
                    EnforcementPrincipal(config.securityPrincipal, setOf(EnforcementRoles.SECURITY)),
                ))
            }
            val enforcementAuth = OpaqueEnforcementAuthenticator(enforcementCredentials)
            val enforcement = EnforcementRoutes(
                service = EnforcementService(repository, config.environment, clock),
                repository = repository,
                auth = enforcementAuth,
                metadataPublisher = if (config.serverMode == RegistryServerMode.PUBLIC_API) {
                    UnusedEnforcementMetadataPublisher
                } else {
                    TufEnforcementMetadataPublisher(repository, tuf)
                },
                clock = clock,
                surfaces = when (config.serverMode) {
                    RegistryServerMode.PUBLIC_API -> setOf(EnforcementRouteSurface.REPORTS_AND_APPEALS)
                    RegistryServerMode.RELEASE_ACTIONS -> setOf(EnforcementRouteSurface.RELEASE_ACTIONS)
                    RegistryServerMode.SECURITY_ACTIONS -> setOf(EnforcementRouteSurface.SECURITY_ACTIONS)
                },
            )
            return RegistryResources(
                config,
                repository,
                storage,
                online,
                tuf,
                clock,
                registryRoutes,
                enforcement,
                config.serverMode,
                signingRoles.toSet(),
            )
        }
    }
}

private fun publicKeyOnlyOnlineSigners(publicKeysHex: Map<String, String>): TufOnlineSigners {
    require(publicKeysHex.keys == TufRole.ONLINE.toSet()) { "All and only online TUF public keys are required" }
    fun signer(role: String) = PublicKeyOnlyTufSigner(requireNotNull(publicKeysHex[role]).hexToBytes())
    return TufOnlineSigners(
        releases = signer(TufRole.RELEASES),
        security = signer(TufRole.SECURITY),
        snapshot = signer(TufRole.SNAPSHOT),
        timestamp = signer(TufRole.TIMESTAMP),
    )
}

private object UnusedEnforcementMetadataPublisher : EnforcementMetadataPublisher {
    private fun unsupported(): Nothing = error("This route surface cannot publish enforcement metadata")
    override fun publishReleaseAvailability(release: StoredRelease) = unsupported()
    override fun publishSecurityQuarantine(
        subject: EnforcementReleaseSubject,
        request: SecurityQuarantineRequest,
        incidentId: String,
    ): SignedMetadataReference = unsupported()
    override fun publishReviewedReinstatement(
        subject: EnforcementReleaseSubject,
        request: ReviewedReinstatementRequest,
    ): SignedMetadataReference = unsupported()
    override fun restoreSecurityQuarantine(subject: EnforcementReleaseSubject) = unsupported()
}
