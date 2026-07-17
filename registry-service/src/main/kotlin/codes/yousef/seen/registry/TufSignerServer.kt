package codes.yousef.seen.registry

import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyManagementServiceSettings
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configuration for one private, IAM-protected Cloud Run signing service.
 *
 * The process receives exactly one role/key pair plus read-only access to the
 * public metadata bucket. Only the timestamp role also receives conditional
 * update authority for timestamp.json. It has no repository, database,
 * writer-token, general metadata write authority, or offline-key config.
 */
data class TufSignerServerConfig(
    val cloudRunService: String,
    val environment: String,
    val repositoryId: String,
    val role: String,
    val kmsKeyVersion: String,
    val publicKeyHex: String,
    val port: Int = 8080,
    val acceptedVersions: LongRange = 1L..MAXIMUM_METADATA_VERSION,
    val maximumExpiry: Duration = roleMaximumExpiry(role),
    val maximumRequestBytes: Int = RemoteTufSigner.DEFAULT_MAXIMUM_REQUEST_BYTES,
    val signingTimeout: Duration = Duration.ofSeconds(10),
    val maximumConcurrentRequests: Int = 8,
    val stateGuardConfig: TufSignerStateGuardConfig? = null,
) {
    val publicKey: ByteArray
        get() = publicKeyHex.hexToBytes()

    init {
        require(CLOUD_RUN_SERVICE.matches(cloudRunService)) { "K_SERVICE is invalid" }
        require(environment in ENVIRONMENTS) { "REGISTRY_ENVIRONMENT must be development or production" }
        val expectedRepositoryPrefix = if (environment == "development") "seen-dev-" else "seen-prod-"
        require(REPOSITORY_ID.matches(repositoryId) && repositoryId.startsWith(expectedRepositoryPrefix)) {
            "REGISTRY_REPOSITORY_ID is not bound to REGISTRY_ENVIRONMENT"
        }
        require(role in TufRole.ONLINE) { "REGISTRY_TUF_SIGNER_ROLE must name one online TUF role" }
        require(PUBLIC_KEY_HEX.matches(publicKeyHex)) {
            "REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX must contain 32 lowercase-hex Ed25519 bytes"
        }
        require(port in 0..65_535) { "PORT is invalid" }
        require(!acceptedVersions.isEmpty() && acceptedVersions.first >= 1L && acceptedVersions.last <= MAXIMUM_METADATA_VERSION) {
            "TUF signer version range is invalid"
        }
        require(!maximumExpiry.isZero && !maximumExpiry.isNegative && maximumExpiry <= roleMaximumExpiry(role)) {
            "TUF signer expiry bound exceeds the role policy"
        }
        require(maximumRequestBytes in 1..ABSOLUTE_MAXIMUM_REQUEST_BYTES) {
            "TUF signer request size bound is invalid"
        }
        require(!signingTimeout.isZero && !signingTimeout.isNegative && signingTimeout <= ABSOLUTE_MAXIMUM_SIGNING_TIMEOUT) {
            "TUF signer timeout must be positive and no more than 30 seconds"
        }
        require(maximumConcurrentRequests in 1..ABSOLUTE_MAXIMUM_CONCURRENT_REQUESTS) {
            "TUF signer concurrency bound is invalid"
        }

        val match = KMS_KEY_VERSION.matchEntire(kmsKeyVersion)
            ?: throw IllegalArgumentException("REGISTRY_TUF_SIGNER_KMS_KEY_VERSION is not a concrete Cloud KMS key version")
        val environmentSuffix = if (environment == "development") "dev" else "prod"
        require(match.groups[1]?.value == "seen-registry-$environmentSuffix") {
            "TUF signer key ring is not bound to REGISTRY_ENVIRONMENT"
        }
        require(match.groups[2]?.value == "seen-registry-$environmentSuffix-$role") {
            "TUF signer KMS key is not bound to its configured role"
        }
    }

    fun signingPolicy(): RoleLockedTufSigningPolicy = RoleLockedTufSigningPolicy(
        role = role,
        environment = environment,
        repositoryId = repositoryId,
        acceptedVersions = acceptedVersions,
        maximumExpiry = maximumExpiry,
        maximumRequestBytes = maximumRequestBytes,
    )

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): TufSignerServerConfig {
            val forbidden = env.keys.filter(::isForbiddenSignerEnvironmentVariable).sorted()
            require(forbidden.isEmpty()) {
                "The TUF signer process rejects unrelated authority or persistence configuration: ${forbidden.joinToString(", ")}"
            }
            fun required(name: String): String = env[name]?.trim()?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("$name is required")
            fun boundedLong(name: String, default: Long): Long = env[name]?.let {
                it.toLongOrNull() ?: throw IllegalArgumentException("$name is invalid")
            } ?: default

            val role = required("REGISTRY_TUF_SIGNER_ROLE")
            val minimumVersion = boundedLong("REGISTRY_TUF_SIGNER_MIN_VERSION", 1L)
            val maximumVersion = boundedLong("REGISTRY_TUF_SIGNER_MAX_VERSION", MAXIMUM_METADATA_VERSION)
            val roleExpiry = roleMaximumExpiry(role)
            return TufSignerServerConfig(
                cloudRunService = required("K_SERVICE"),
                environment = required("REGISTRY_ENVIRONMENT"),
                repositoryId = required("REGISTRY_REPOSITORY_ID"),
                role = role,
                kmsKeyVersion = required("REGISTRY_TUF_SIGNER_KMS_KEY_VERSION"),
                publicKeyHex = required("REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX"),
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                acceptedVersions = minimumVersion..maximumVersion,
                maximumExpiry = Duration.ofSeconds(boundedLong("REGISTRY_TUF_SIGNER_MAX_EXPIRY_SECONDS", roleExpiry.seconds)),
                maximumRequestBytes = boundedLong(
                    "REGISTRY_TUF_SIGNER_MAX_REQUEST_BYTES",
                    RemoteTufSigner.DEFAULT_MAXIMUM_REQUEST_BYTES.toLong(),
                ).let {
                    require(it in 1..Int.MAX_VALUE.toLong()) { "REGISTRY_TUF_SIGNER_MAX_REQUEST_BYTES is invalid" }
                    it.toInt()
                },
                signingTimeout = Duration.ofMillis(boundedLong("REGISTRY_TUF_SIGNER_TIMEOUT_MILLIS", 10_000L)),
                maximumConcurrentRequests = boundedLong("REGISTRY_TUF_SIGNER_MAX_CONCURRENCY", 8L).let {
                    require(it in 1..Int.MAX_VALUE.toLong()) { "REGISTRY_TUF_SIGNER_MAX_CONCURRENCY is invalid" }
                    it.toInt()
                },
                stateGuardConfig = TufSignerStateGuardConfig.fromEnvironment(env),
            ).also {
                require(it.port != 0) { "PORT must be between 1 and 65535" }
            }
        }

        internal const val MAXIMUM_METADATA_VERSION = 9_007_199_254_740_991L
        private const val ABSOLUTE_MAXIMUM_REQUEST_BYTES = 8 * 1024 * 1024
        private const val ABSOLUTE_MAXIMUM_CONCURRENT_REQUESTS = 32
        private val ABSOLUTE_MAXIMUM_SIGNING_TIMEOUT = Duration.ofSeconds(30)
        private val CLOUD_RUN_SERVICE = Regex("^[a-z][a-z0-9-]{0,62}$")
        private val ENVIRONMENTS = setOf("development", "production")
        private val REPOSITORY_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val PUBLIC_KEY_HEX = Regex("^[0-9a-f]{64}$")
        private val KMS_KEY_VERSION = Regex(
            "^projects/[a-z0-9][a-z0-9-]{3,62}/locations/[a-z0-9-]{1,63}/keyRings/([A-Za-z0-9_-]{1,63})/" +
                "cryptoKeys/([A-Za-z0-9_-]{1,63})/cryptoKeyVersions/[1-9][0-9]*$",
        )
    }
}

private fun roleMaximumExpiry(role: String): Duration = when (role) {
    TufRole.RELEASES -> Duration.ofDays(7)
    TufRole.SECURITY -> Duration.ofHours(6)
    TufRole.SNAPSHOT -> Duration.ofDays(1)
    TufRole.TIMESTAMP -> Duration.ofHours(6)
    else -> throw IllegalArgumentException("REGISTRY_TUF_SIGNER_ROLE must name one online TUF role")
}

private fun isForbiddenSignerEnvironmentVariable(name: String): Boolean =
    name == "GOOGLE_APPLICATION_CREDENTIALS" ||
        name in setOf(
            "REGISTRY_STORAGE_MODE",
            "REGISTRY_FIRESTORE_DATABASE",
            "REGISTRY_QUARANTINE_BUCKET",
            "REGISTRY_PUBLIC_BUCKET",
            "REGISTRY_METADATA_BUCKET",
            "REGISTRY_WRITER_TOKEN",
            "REGISTRY_TRUST_AND_SAFETY_TOKEN",
            "REGISTRY_SECURITY_TOKEN",
            "REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64",
            "REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64",
        ) ||
        Regex("^REGISTRY_KMS_(RELEASES|SECURITY|SNAPSHOT|TIMESTAMP)_(KEY_VERSION|PUBLIC_KEY_HEX)$").matches(name) ||
        Regex("^REGISTRY_(RELEASES|SECURITY|SNAPSHOT|TIMESTAMP)_SIGNING_KEY_PKCS8_BASE64$").matches(name) ||
        Regex("^REGISTRY_OFFLINE_.*(KEY|ENVELOPE).*$").matches(name)

fun interface TufSignerServerSignerFactory {
    fun create(config: TufSignerServerConfig): TufSigner
}

/**
 * State-aware authorization hook evaluated immediately before a validated
 * metadata object reaches KMS. Snapshot and timestamp signing cannot safely be
 * authorized from object shape alone: their references must be checked against
 * the currently committed metadata chain to prevent a higher-version rollback.
 */
data class TufSignerAuthorizationRequest(
    val role: String,
    val operation: TufSigningOperation,
    val bearerToken: String,
    val canonicalSignedBytes: ByteArray,
    val commitDeadline: java.time.Instant? = null,
)

fun interface TufSignerStatePolicyGuard {
    fun authorize(request: TufSignerAuthorizationRequest)
}

fun interface TufSignerStatePolicyGuardFactory {
    fun create(config: TufSignerServerConfig): TufSignerStatePolicyGuard
}

/** Every production role fails at startup until a committed-state guard is injected. */
object FailClosedTufSignerStatePolicyGuardFactory : TufSignerStatePolicyGuardFactory {
    override fun create(config: TufSignerServerConfig): TufSignerStatePolicyGuard {
        throw IllegalStateException(
            "${config.role} signing requires an injected committed-metadata state policy guard",
        )
    }
}

/** Creates a bounded signer with only asymmetricSign authority on the pinned key version. */
object KmsTufSignerServerSignerFactory : TufSignerServerSignerFactory {
    override fun create(config: TufSignerServerConfig): TufSigner {
        val settings = KeyManagementServiceSettings.newBuilder().apply {
            asymmetricSignSettings().setSimpleTimeoutNoRetriesDuration(config.signingTimeout)
        }.build()
        val client = KeyManagementServiceClient.create(settings)
        return KmsEd25519Signer(client, config.kmsKeyVersion, config.publicKey)
    }
}

/**
 * Minimal signer-only HTTP surface. Cloud Run IAM is the authentication
 * authority; the application also rejects requests without the bearer envelope
 * IAM evaluates. Bearer presence alone is not treated as identity validation.
 */
class TufSignerHttpServer private constructor(
    private val config: TufSignerServerConfig,
    private val service: RoleLockedTufSigningService,
    private val statePolicyGuard: TufSignerStatePolicyGuard,
    private val clock: Clock,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val vertx = Vertx.vertx()
    private val capacity = Semaphore(config.maximumConcurrentRequests)
    private val closed = AtomicBoolean()
    private var server: HttpServer? = null
    val actualPort: Int get() = requireNotNull(server) { "TUF signer server is not started" }.actualPort()

    fun start() {
        check(server == null) { "TUF signer server is already started" }
        server = vertx.createHttpServer()
            .requestHandler(::handle)
            .listen(config.port, "0.0.0.0")
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS)
    }

    private fun handle(request: HttpServerRequest) {
        val bearerToken = request.cloudRunIamBearerToken()
        val operation = request.singleHeader(JdkRemoteTufHttpTransport.OPERATION_HEADER)
            ?.let { runCatching { TufSigningOperation.parse(it) }.getOrNull() }
        when {
            request.path() != SIGN_PATH -> reject(request.response(), 404)
            request.method() != HttpMethod.POST -> reject(request.response(), 405, "Allow" to "POST")
            request.singleHeader("X-Forwarded-Proto") != "https" -> reject(request.response(), 400)
            bearerToken == null -> reject(
                request.response(),
                401,
                "WWW-Authenticate" to "Bearer realm=\"seen-tuf-signer\"",
            )
            request.singleHeader("Content-Type") != JdkRemoteTufHttpTransport.SIGNED_METADATA_CONTENT_TYPE ->
                reject(request.response(), 415)
            request.singleHeader("Accept") != JdkRemoteTufHttpTransport.SIGNATURE_CONTENT_TYPE ->
                reject(request.response(), 406)
            request.singleHeader(JdkRemoteTufHttpTransport.ROLE_HEADER) != config.role -> reject(request.response(), 403)
            operation == null || !operation.permitsRole(config.role) -> reject(request.response(), 403)
            !capacity.tryAcquire() -> reject(request.response(), 429, "Retry-After" to "1")
            else -> receiveBody(request, bearerToken, operation)
        }
    }

    private fun receiveBody(
        request: HttpServerRequest,
        bearerToken: String,
        operation: TufSigningOperation,
    ) {
        val declaredLength = request.singleHeader("Content-Length")?.toLongOrNull()
        if (declaredLength != null && declaredLength !in 1..config.maximumRequestBytes.toLong()) {
            capacity.release()
            rejectAndClose(request.response(), 413)
            return
        }
        val body = BoundedSigningBody(config.maximumRequestBytes)
        val completed = AtomicBoolean()
        request.handler { chunk ->
            if (!completed.get() && !body.append(chunk.bytes) && completed.compareAndSet(false, true)) {
                request.pause()
                capacity.release()
                rejectAndClose(request.response(), 413)
            }
        }
        request.endHandler {
            if (completed.compareAndSet(false, true)) {
                if (body.size == 0 || declaredLength != null && declaredLength != body.size.toLong()) {
                    capacity.release()
                    reject(request.response(), 400)
                } else {
                    sign(request, bearerToken, operation, body.bytes())
                }
            }
        }
        request.exceptionHandler {
            if (completed.compareAndSet(false, true)) {
                capacity.release()
                if (!request.response().headWritten()) reject(request.response(), 400)
            }
        }
    }

    private fun sign(
        request: HttpServerRequest,
        bearerToken: String,
        operation: TufSigningOperation,
        body: ByteArray,
    ) {
        val response = request.response()
        vertx.executeBlocking(java.util.concurrent.Callable<ByteArray> {
            // The guard is intentionally first: authorization must be based on
            // committed state before this process permits any KMS operation.
            // RoleLockedTufSigningService then performs the complete canonical
            // shape/environment/repository/expiry validation before signing.
            val authorization = TufSignerAuthorizationRequest(
                role = config.role,
                operation = operation,
                bearerToken = bearerToken,
                canonicalSignedBytes = body.copyOf(),
                commitDeadline = clock.instant().plus(config.signingTimeout),
            )
            statePolicyGuard.authorize(authorization)
            if (
                config.role == TufRole.TIMESTAMP &&
                !clock.instant().isBefore(requireNotNull(authorization.commitDeadline))
            ) {
                throw TufSigningRequestException("Timestamp signing authorization expired before KMS")
            }
            val signature = service.sign(config.role, body)
            if (config.role == TufRole.TIMESTAMP) {
                val committer = statePolicyGuard as? TufTimestampCommitStatePolicyGuard
                    ?: error("Timestamp signing requires a conditional commit policy guard")
                committer.commitTimestamp(authorization, signature, config.publicKey)
            }
            signature
        })
            .onComplete { result ->
                capacity.release()
                when {
                    result.succeeded() -> signature(response, requireNotNull(result.result()))
                    result.cause() is TufSigningRequestException -> reject(response, 422)
                    else -> {
                        log.warn("TUF role signer request failed role={}", config.role, result.cause())
                        reject(response, 503, "Retry-After" to "1")
                    }
                }
            }
    }

    private fun signature(response: HttpServerResponse, bytes: ByteArray) {
        if (bytes.size != SIGNATURE_BYTES) {
            reject(response, 503, "Retry-After" to "1")
            return
        }
        response.statusCode = 200
        response.putHeader("Content-Type", JdkRemoteTufHttpTransport.SIGNATURE_CONTENT_TYPE)
        response.putHeader("Content-Length", bytes.size.toString())
        response.putHeader("Cache-Control", "no-store")
        response.putHeader("X-Content-Type-Options", "nosniff")
        response.end(io.vertx.core.buffer.Buffer.buffer(bytes))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server?.close()?.toCompletionStage()?.toCompletableFuture()?.get(30, TimeUnit.SECONDS) }
        runCatching { vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS) }
        runCatching { service.close() }
    }

    companion object {
        const val SIGN_PATH = "/sign"
        private const val SIGNATURE_BYTES = 64

        fun create(
            config: TufSignerServerConfig,
            signer: TufSigner,
            statePolicyGuard: TufSignerStatePolicyGuard,
            clock: Clock = Clock.systemUTC(),
        ): TufSignerHttpServer {
            require(MessageDigest.isEqual(config.publicKey, signer.publicKey)) {
                "TUF signer runtime key does not match the configured public key"
            }
            val guardedService = RoleLockedTufSigningService(config.signingPolicy(), signer, clock)
            return TufSignerHttpServer(config, guardedService, statePolicyGuard, clock)
        }
    }
}

private fun HttpServerRequest.singleHeader(name: String): String? =
    headers().getAll(name).takeIf { it.size == 1 }?.single()

private fun HttpServerRequest.cloudRunIamBearerToken(): String? {
    // The coordinator uses Authorization so this process can independently
    // verify the Google signature and bind the caller email to one operation.
    // X-Serverless-Authorization may have its signature removed by Cloud Run
    // and is therefore deliberately not accepted by this application layer.
    val authorization = headers().getAll("Authorization")
    val selected = authorization.takeIf { it.size == 1 }?.single() ?: return null
    if (!selected.startsWith("Bearer ")) return null
    val token = selected.removePrefix("Bearer ")
    return token.takeIf {
        it.isNotEmpty() && it.length <= MAXIMUM_BEARER_BYTES && it.none { character ->
            character.isWhitespace() || character.isISOControl()
        }
    }
}

private class BoundedSigningBody(private val maximumBytes: Int) {
    private val output = java.io.ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024))
    val size: Int get() = output.size()

    fun append(bytes: ByteArray): Boolean {
        if (bytes.size > maximumBytes - output.size()) return false
        output.write(bytes)
        return true
    }

    fun bytes(): ByteArray = output.toByteArray()
}

private fun reject(response: HttpServerResponse, status: Int, vararg headers: Pair<String, String>) {
    if (response.ended()) return
    response.statusCode = status
    response.putHeader("Content-Length", "0")
    response.putHeader("Cache-Control", "no-store")
    response.putHeader("X-Content-Type-Options", "nosniff")
    headers.forEach { (name, value) -> response.putHeader(name, value) }
    response.end()
}

private fun rejectAndClose(response: HttpServerResponse, status: Int) {
    response.putHeader("Connection", "close")
    reject(response, status)
}

object TufSignerServerRuntime {
    fun run(
        config: TufSignerServerConfig,
        signerFactory: TufSignerServerSignerFactory = KmsTufSignerServerSignerFactory,
        statePolicyGuardFactory: TufSignerStatePolicyGuardFactory = GcsTufSignerStatePolicyGuardFactory,
    ) {
        // Establish the committed-state authorization boundary before opening
        // even a client handle to the role's sole KMS authority.
        val statePolicyGuard = statePolicyGuardFactory.create(config)
        val signer = signerFactory.create(config)
        val server = try {
            TufSignerHttpServer.create(config, signer, statePolicyGuard)
        } catch (failure: Exception) {
            signer.close()
            throw failure
        }
        server.use {
            it.start()
            val latch = CountDownLatch(1)
            Runtime.getRuntime().addShutdownHook(Thread { it.close(); latch.countDown() })
            latch.await()
        }
    }
}

private const val MAXIMUM_BEARER_BYTES = 16 * 1024
