package codes.yousef.seen.registry

import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.respondJson
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    args.singleOrNull()?.let(RegistryWorkerMode::fromArgument)?.let { mode ->
        RegistryWorkerRuntime.run(RegistryWorkerConfig.fromEnvironment(mode))
        return
    }
    if (args.singleOrNull() == "describe-signing-policy") {
        println("seen-tuf-development-v1 root=offline:2/3 targets=offline:2/2 releases=kms:1/1 security=kms:1/1 snapshot=kms:1/1 timestamp=kms:1/1 public-delay=259200 promotion=disabled")
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
    val config = RegistryConfig.fromEnvironment()
    val resources = RegistryResources.create(config)
    if (args.singleOrNull() == "bootstrap") {
        resources.use {
            val result = it.bootstrap()
            println("root_sha256=${sha256(result.root)}")
            println("root_key_ids=${result.rootKeyIds.joinToString(",")}")
            println("targets_sha256=${sha256(result.targets)}")
            println("targets_key_ids=${result.targetsKeyIds.joinToString(",")}")
        }
        return
    }
    if (args.firstOrNull() == "import-offline-targets-renewal") {
        require(args.size == 2) { "Usage: registry-service import-offline-targets-renewal <N.targets.json>" }
        resources.use {
            val candidate = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[1]))
            val result = it.importTargetsRenewal(candidate)
            println("targets_version=${result.targetsVersion}")
            println("targets_sha256=${sha256(candidate)}")
            println("online_transaction_version=${result.onlineTransactionVersion}")
        }
        return
    }
    require(args.isEmpty()) {
        "Usage: registry-service [bootstrap|describe-signing-policy|verify-source-once|scan-once|promote-once|prepare-offline-bootstrap <output-dir>|prepare-offline-targets-renewal <trusted-root.json> <current-N.targets.json> <output-dir>|import-offline-targets-renewal <N.targets.json>]"
    }
    resources.startAndWait()
}

class RegistryResources private constructor(
    private val config: RegistryConfig,
    private val repository: RegistryRepository,
    private val storage: RegistryObjectStorage,
    private val onlineSigners: TufOnlineSigners,
    val tuf: TufPublisher,
    private val clock: Clock,
    private val service: RegistryService,
    private val routes: RegistryRoutes,
    private val enforcementRoutes: EnforcementRoutes,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "registry-maintenance").apply { isDaemon = true }
    }
    private var server: RegistryHttpServer? = null

    fun bootstrap(): TufBootstrapResult {
        val rootEnvelope = config.bootstrapRootEnvelopeBase64
        val targetsEnvelope = config.bootstrapTargetsEnvelopeBase64
        val result = if (rootEnvelope != null || targetsEnvelope != null) {
            require(rootEnvelope != null && targetsEnvelope != null) { "Both pre-signed bootstrap envelopes are required" }
            TufBootstrapImporter(
                storage = storage,
                online = onlineSigners.publicKeys(),
                environment = config.environment,
                repositoryId = config.repositoryId,
                registryOrigin = config.registryOrigin,
                clock = clock,
            ).import(
                java.util.Base64.getDecoder().decode(rootEnvelope),
                java.util.Base64.getDecoder().decode(targetsEnvelope),
            )
        } else {
            require(config.storageMode == "memory") { "GCP bootstrap accepts only pre-signed envelopes from the offline ceremony" }
            val rootSigners = config.offlineRootSigningKeysPkcs8Base64.map(LocalEd25519Signer::fromPkcs8Base64)
            val targetsSigners = config.offlineTargetsSigningKeysPkcs8Base64.map(LocalEd25519Signer::fromPkcs8Base64)
            try {
                TufBootstrapper(
                    storage = storage,
                    rootPublicKeys = config.offlineRootPublicKeysHex.map(String::hexToBytes),
                    rootSigners = rootSigners,
                    targetsPublicKeys = config.offlineTargetsPublicKeysHex.map(String::hexToBytes),
                    targetsSigners = targetsSigners,
                    online = onlineSigners.publicKeys(),
                    environment = config.environment,
                    repositoryId = config.repositoryId,
                    clock = clock,
                ).bootstrap()
            } finally {
                rootSigners.forEach(TufSigner::close)
                targetsSigners.forEach(TufSigner::close)
            }
        }
        tuf.ensureInitialTransaction()
        return result
    }

    fun startAndWait() {
        tuf.requireBootstrap()
        tuf.ensureInitialTransaction()
        val pipeline = routePipeline()
        server = RegistryHttpServer(
            config = VertxServerConfig(
                port = config.port,
                decompressionSupported = false,
                maxRequestBodySize = ArchivePolicy.MAX_COMPRESSED_BYTES.toInt() + 1,
            ),
            pipeline = pipeline,
            routes = routes,
            fallback = { exchange -> exchange.respondJson(404, ErrorEnvelope(ApiError(
                code = "not_found",
                message = "Resource was not found",
                requestId = "req_0000000000000000",
                occurredAt = Clock.systemUTC().instant().utc(),
                retryable = false,
            )), RegistryJson) },
        )
        requireNotNull(server).start()
        scheduler.scheduleWithFixedDelay({
            runCatching { tuf.ensureFreshTransaction() }.onFailure { log.error("Scheduled metadata refresh failed", it) }
        }, 1, 5, TimeUnit.MINUTES)
        val latch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread { close(); latch.countDown() })
        log.info("Seen registry listening on port {}", config.port)
        latch.await()
    }

    internal fun routePipeline(): Pipeline = Pipeline().apply {
        use(enforcementRoutes.router.asMiddleware())
        use(routes.router.asMiddleware())
    }

    fun importTargetsRenewal(candidate: ByteArray): TufTargetsRenewalImportResult = tuf.importTargetsRenewal(candidate)

    override fun close() {
        scheduler.shutdownNow()
        server?.close()
        onlineSigners.close()
        repository.close()
    }

    companion object {
        fun create(config: RegistryConfig, clock: Clock = Clock.systemUTC()): RegistryResources {
            val repository: RegistryRepository = if (config.storageMode == "gcp") FirestoreRegistryRepository.create(config) else InMemoryRegistryRepository()
            val storage: RegistryObjectStorage = if (config.storageMode == "gcp") GcsRegistryObjectStorage.create(config) else InMemoryRegistryObjectStorage()
            val online = if (config.storageMode == "gcp") {
                fun kms(role: String) = KmsEd25519Signer(
                    com.google.cloud.kms.v1.KeyManagementServiceClient.create(),
                    requireNotNull(config.kmsOnlineKeyVersions[role]),
                    requireNotNull(config.kmsOnlinePublicKeysHex[role]).hexToBytes(),
                )
                TufOnlineSigners(kms(TufRole.RELEASES), kms(TufRole.SECURITY), kms(TufRole.SNAPSHOT), kms(TufRole.TIMESTAMP))
            } else {
                fun local(role: String) = LocalEd25519Signer.fromPkcs8Base64(requireNotNull(config.localOnlineSigningKeysPkcs8Base64[role]))
                TufOnlineSigners(local(TufRole.RELEASES), local(TufRole.SECURITY), local(TufRole.SNAPSHOT), local(TufRole.TIMESTAMP))
            }
            val tuf = TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock)
            val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
            val auth = OpaqueDevWriterAuthenticator(config.writerToken, config.writerPrincipal, config.ownerAllowlist)
            val enforcementAuth = OpaqueEnforcementAuthenticator(buildList {
                add(OpaqueEnforcementCredential(
                    config.writerToken,
                    EnforcementPrincipal(config.writerPrincipal, setOf(EnforcementRoles.PUBLISHER)),
                ))
                config.trustAndSafetyToken?.let { token ->
                    add(OpaqueEnforcementCredential(
                        token,
                        EnforcementPrincipal(
                            config.trustAndSafetyPrincipal,
                            setOf(EnforcementRoles.TRUST_AND_SAFETY),
                        ),
                    ))
                }
                config.securityToken?.let { token ->
                    add(OpaqueEnforcementCredential(
                        token,
                        EnforcementPrincipal(config.securityPrincipal, setOf(EnforcementRoles.SECURITY)),
                    ))
                }
            })
            val enforcement = EnforcementRoutes(
                service = EnforcementService(repository, config.environment, clock),
                repository = repository,
                auth = enforcementAuth,
                metadataPublisher = TufEnforcementMetadataPublisher(repository, tuf),
                clock = clock,
            )
            return RegistryResources(
                config,
                repository,
                storage,
                online,
                tuf,
                clock,
                service,
                RegistryRoutes(service, auth, clock),
                enforcement,
            )
        }
    }
}
