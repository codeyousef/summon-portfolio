package codes.yousef.seen.registry

import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.respondJson
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
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
        "Usage: registry-service [bootstrap|describe-signing-policy|prepare-offline-bootstrap <output-dir>|prepare-offline-targets-renewal <trusted-root.json> <current-N.targets.json> <output-dir>|import-offline-targets-renewal <N.targets.json>]"
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
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "registry-maintenance").apply { isDaemon = true }
    }
    private var server: VertxServer? = null

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
        val pipeline = Pipeline().apply { use(routes.router.asMiddleware()) }
        server = VertxServer(
            config = VertxServerConfig(port = config.port, maxRequestBodySize = ArchivePolicy.MAX_COMPRESSED_BYTES.toInt() + 1),
            pipeline = pipeline,
            handler = { exchange -> exchange.respondJson(404, ErrorEnvelope(ApiError(
                code = "not_found",
                message = "Resource was not found",
                requestId = "req_0000000000000000",
                occurredAt = Clock.systemUTC().instant().utc(),
                retryable = false,
            )), RegistryJson) },
        )
        runBlocking { requireNotNull(server).start() }
        scheduler.scheduleWithFixedDelay({
            runCatching { tuf.ensureFreshTransaction() }.onFailure { log.error("Scheduled metadata refresh failed", it) }
        }, 1, 5, TimeUnit.MINUTES)
        if (config.promotionMode != "disabled") {
            scheduler.scheduleWithFixedDelay({
                runCatching { service.promoteDue() }.onFailure { log.error("Scheduled promotion failed", it) }
            }, 1, 1, TimeUnit.MINUTES)
        }
        val latch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread { close(); latch.countDown() })
        log.info("Seen registry listening on port {}", config.port)
        latch.await()
    }

    fun importTargetsRenewal(candidate: ByteArray): TufTargetsRenewalImportResult = tuf.importTargetsRenewal(candidate)

    override fun close() {
        scheduler.shutdownNow()
        runBlocking { server?.close() }
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
            return RegistryResources(config, repository, storage, online, tuf, clock, service, RegistryRoutes(service, auth, clock, config.promotionMode))
        }
    }
}
