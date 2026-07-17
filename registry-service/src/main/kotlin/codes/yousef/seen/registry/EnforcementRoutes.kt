package codes.yousef.seen.registry

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParamOrThrow
import codes.yousef.aether.web.router
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

fun interface EnforcementAuthenticator {
    fun authenticate(authorization: String?): EnforcementPrincipal
}

class OpaqueEnforcementCredential(
    val token: String,
    val principal: EnforcementPrincipal,
)

/**
 * Development bearer boundary for publisher, reporter, reviewer, and security
 * actors. Tokens are retained only as fixed-size digests and all configured
 * credentials are compared before an authentication result is returned.
 */
class OpaqueEnforcementAuthenticator(
    credentials: List<OpaqueEnforcementCredential>,
) : EnforcementAuthenticator {
    private data class Credential(
        val tokenDigest: ByteArray,
        val principal: EnforcementPrincipal,
    )

    private val credentials: List<Credential> = credentials.map { credential ->
        val tokenBytes = credential.token.toByteArray(StandardCharsets.UTF_8)
        require(tokenBytes.size in 32..4096) { "Enforcement bearer tokens must contain 32 to 4096 bytes" }
        require(credential.principal.principalId.isNotBlank()) { "Enforcement principal IDs must not be blank" }
        require(credential.principal.roles.all(ALLOWED_ROLES::contains)) { "Unknown enforcement actor role" }
        require(
            EnforcementRoles.PUBLISHER !in credential.principal.roles ||
                credential.principal.roles.none(PRIVILEGED_ROLES::contains),
        ) { "Publisher credentials cannot carry privileged registry roles" }
        Credential(digest(tokenBytes), credential.principal)
    }.also { configured ->
        require(configured.isNotEmpty()) { "At least one enforcement credential is required" }
        require(configured.indices.all { index ->
            configured.indices.none { other ->
                other > index && MessageDigest.isEqual(configured[index].tokenDigest, configured[other].tokenDigest)
            }
        }) { "Enforcement bearer tokens must be distinct" }
    }

    override fun authenticate(authorization: String?): EnforcementPrincipal {
        val presented = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = false) }
            ?.removePrefix("Bearer ")
            ?.toByteArray(StandardCharsets.UTF_8)
            ?: ByteArray(0)
        val presentedDigest = digest(presented)
        var match: EnforcementPrincipal? = null
        credentials.forEach { credential ->
            if (MessageDigest.isEqual(credential.tokenDigest, presentedDigest)) match = credential.principal
        }
        return match ?: throw RegistryException(401, "unauthenticated", "Authentication is required")
    }

    private companion object {
        val ALLOWED_ROLES = setOf(
            EnforcementRoles.PUBLISHER,
            EnforcementRoles.TRUST_AND_SAFETY,
            EnforcementRoles.SECURITY,
        )
        val PRIVILEGED_ROLES = setOf(EnforcementRoles.TRUST_AND_SAFETY, EnforcementRoles.SECURITY)

        fun digest(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    }
}

/** Publishes the signed resolver/security state associated with an action. */
interface EnforcementMetadataPublisher {
    fun publishReleaseAvailability(release: StoredRelease)

    fun publishSecurityQuarantine(
        subject: EnforcementReleaseSubject,
        request: SecurityQuarantineRequest,
        incidentId: String,
    ): SignedMetadataReference

    fun publishReviewedReinstatement(
        subject: EnforcementReleaseSubject,
        request: ReviewedReinstatementRequest,
    ): SignedMetadataReference

    /** Reasserts the state-derived deny override after a failed reinstatement commit. */
    fun restoreSecurityQuarantine(subject: EnforcementReleaseSubject)
}

class EnforcementRoutes(
    private val service: EnforcementService,
    private val repository: RegistryRepository,
    private val auth: EnforcementAuthenticator,
    private val metadataPublisher: EnforcementMetadataPublisher,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = RegistryJson,
    private val surfaces: Set<EnforcementRouteSurface> = EnforcementRouteSurface.entries.toSet(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    val router: Router = router {
        // Liveness belongs to the transport surface, not to public package
        // routes. Keep it available on the isolated action services so Cloud
        // Run can distinguish a healthy process from an action-route failure.
        get("/health") { exchange ->
            respondJson(exchange, 200, mapOf("status" to "ok"))
        }

        if (EnforcementRouteSurface.REPORTS_AND_APPEALS in surfaces) {
            post("/packages/api/v1/reports") { exchange -> safe(exchange) { requestId ->
                val actor = actor(exchange)
                idempotentMutation<SecurityReportRecord>(
                    exchange = exchange,
                    requestId = requestId,
                    actor = actor,
                    successStatus = 201,
                    responseHeaders = { record ->
                        mapOf("Location" to "/packages/api/v1/reports/${record.reportId}")
                    },
                ) { body -> service.createSecurityReport(decode(body), actor) }
            } }
            get("/packages/api/v1/reports/:reportId") { exchange -> safe(exchange) {
                val record = service.getSecurityReport(exchange.pathParamOrThrow("reportId"), actor(exchange))
                respondAccessControlled(exchange, record)
            } }

            post("/packages/api/v1/incidents/:incidentId/appeals") { exchange -> safe(exchange) { requestId ->
                val actor = actor(exchange)
                idempotentMutation<EnforcementAppealRecord>(
                    exchange = exchange,
                    requestId = requestId,
                    actor = actor,
                    successStatus = 201,
                    responseHeaders = { record ->
                        mapOf("Location" to "/packages/api/v1/appeals/${record.appealId}")
                    },
                ) { body ->
                    service.createEnforcementAppeal(
                        exchange.pathParamOrThrow("incidentId"),
                        decode(body),
                        actor,
                    )
                }
            } }
            get("/packages/api/v1/appeals/:appealId") { exchange -> safe(exchange) {
                val record = service.getEnforcementAppeal(exchange.pathParamOrThrow("appealId"), actor(exchange))
                respondAccessControlled(exchange, record)
            } }
            post("/packages/api/v1/appeals/:appealId/reviews") { exchange -> safe(exchange) { requestId ->
                val actor = actor(exchange)
                idempotentMutation<AppealReviewRecord>(
                    exchange = exchange,
                    requestId = requestId,
                    actor = actor,
                    successStatus = 201,
                    responseHeaders = { record ->
                        mapOf("Location" to "/packages/api/v1/appeals/${record.appealId}/reviews/${record.reviewId}")
                    },
                ) { body ->
                    service.reviewEnforcementAppeal(
                        exchange.pathParamOrThrow("appealId"),
                        decode(body),
                        actor,
                    )
                }
            } }
        }

        if (EnforcementRouteSurface.RELEASE_ACTIONS in surfaces) {
            post("/packages/api/v1/packages/:owner/:name/releases/:version/actions/yank") { exchange ->
                safe(exchange) { requestId ->
                    val actor = actor(exchange)
                    idempotentMutation<ReleaseRecord>(exchange, requestId, actor, 200) { body ->
                        val request = if (body.isEmpty()) YankReleaseRequest() else decode(body)
                        service.yankRelease(identity(exchange), version(exchange), request, actor) { release ->
                            metadataPublisher.publishReleaseAvailability(release)
                        }
                    }
                }
            }
            post("/packages/api/v1/packages/:owner/:name/releases/:version/actions/unyank") { exchange ->
                safe(exchange) { requestId ->
                    val actor = actor(exchange)
                    idempotentMutation<ReleaseRecord>(exchange, requestId, actor, 200) { body ->
                        if (body.isNotEmpty()) invalidRequest()
                        service.unyankRelease(identity(exchange), version(exchange), actor) { release ->
                            metadataPublisher.publishReleaseAvailability(release)
                        }
                    }
                }
            }
        }

        if (EnforcementRouteSurface.SECURITY_ACTIONS in surfaces) {
            post("/packages/api/v1/packages/:owner/:name/releases/:version/actions/security-quarantine") { exchange ->
                safe(exchange) { requestId ->
                    val actor = actor(exchange)
                    idempotentMutation<SecurityActionRecord>(exchange, requestId, actor, 200) { body ->
                        val request = decode<SecurityQuarantineRequest>(body)
                        val subject = EnforcementReleaseSubject(identity(exchange), version(exchange))
                        service.securityQuarantineRelease(
                            subject.packageIdentity,
                            requireNotNull(subject.version),
                            request,
                            actor,
                        ) { incidentId ->
                            metadataPublisher.publishSecurityQuarantine(subject, request, incidentId)
                        }
                    }
                }
            }
            post("/packages/api/v1/packages/:owner/:name/releases/:version/actions/security-reinstate") { exchange ->
                safe(exchange) { requestId ->
                    val actor = actor(exchange)
                    idempotentMutation<SecurityActionRecord>(exchange, requestId, actor, 200) { body ->
                        val request = decode<ReviewedReinstatementRequest>(body)
                        val subject = EnforcementReleaseSubject(identity(exchange), version(exchange))
                        service.reviewedReinstateRelease(
                            packageIdentity = subject.packageIdentity,
                            version = requireNotNull(subject.version),
                            request = request,
                            actor = actor,
                            publishSignedMetadata = {
                                metadataPublisher.publishReviewedReinstatement(subject, request)
                            },
                            restoreSecurityQuarantine = {
                                metadataPublisher.restoreSecurityQuarantine(subject)
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend inline fun safe(exchange: Exchange, crossinline action: suspend (String) -> Unit) {
        val requestId = requestId()
        exchange.response.setHeader("X-Request-Id", requestId)
        try {
            action(requestId)
        } catch (error: RegistryException) {
            finishResponse(requestId) {
                if (error.status == 401) {
                    exchange.response.setHeader("WWW-Authenticate", "Bearer realm=\"seen-registry\"")
                }
                error.retryAfterSeconds?.let { exchange.response.setHeader("Retry-After", it.toString()) }
                if (exchange.request.headers["Idempotency-Key"] != null) {
                    exchange.response.setHeader("Idempotency-Replayed", "false")
                }
                exchange.response.setHeader("Cache-Control", "no-store")
                respondJson(exchange, error.status, ErrorEnvelope(ApiError(
                    code = error.code,
                    message = error.publicMessage,
                    requestId = requestId,
                    occurredAt = clock.instant().utc(),
                    retryable = error.retryable,
                    retryAfterSeconds = error.retryAfterSeconds,
                    details = JsonObject(emptyMap()),
                )))
            }
        } catch (error: Exception) {
            if (transportCompleted(requestId, error)) return
            log.error("Unhandled registry enforcement request failure requestId={}", requestId, error)
            finishResponse(requestId) {
                exchange.response.setHeader("Cache-Control", "no-store")
                exchange.response.setHeader("Retry-After", "30")
                respondJson(exchange, 500, ErrorEnvelope(ApiError(
                    code = "internal_error",
                    message = "The registry could not complete the request",
                    requestId = requestId,
                    occurredAt = clock.instant().utc(),
                    retryable = true,
                    retryAfterSeconds = 30,
                )))
            }
        }
    }

    private suspend inline fun finishResponse(requestId: String, crossinline response: suspend () -> Unit) {
        try {
            response()
        } catch (error: Exception) {
            if (!transportCompleted(requestId, error)) throw error
        }
    }

    private fun transportCompleted(requestId: String, error: Throwable): Boolean {
        if (!error.isClosedChannelTransportFailure()) return false
        log.debug(
            "Registry enforcement response transport completed requestId={} cause={}",
            requestId,
            error.javaClass.simpleName,
        )
        return true
    }

    private suspend inline fun <reified Result> idempotentMutation(
        exchange: Exchange,
        requestId: String,
        actor: EnforcementPrincipal,
        successStatus: Int,
        noinline responseHeaders: (Result) -> Map<String, String> = { emptyMap() },
        crossinline operation: (ByteArray) -> Result,
    ) {
        val key = requireIdempotency(exchange)
        val body = exchange.request.bodyBytes()
        val fingerprint = framedDigest(
            body,
            exchange.request.headers["Content-Type"].orEmpty().encodeToByteArray(),
        )
        val scope = framedDigest(
            actor.principalId.encodeToByteArray(),
            exchange.request.method.name.encodeToByteArray(),
            exchange.request.path.encodeToByteArray(),
            key.encodeToByteArray(),
        )
        val now = clock.instant()
        val reservation = StoredIdempotency(
            scope = scope,
            fingerprint = fingerprint,
            attemptId = requestId,
            createdAt = now.utc(),
            processingExpiresAt = now.plus(Duration.ofMinutes(2)).utc(),
            expiresAt = now.plus(Duration.ofHours(24)).utc(),
        )
        when (val begin = repository.beginIdempotency(reservation, now)) {
            is IdempotencyBegin.Replay -> {
                sendStored(exchange, requireNotNull(begin.value.response), replayed = true)
                return
            }
            IdempotencyBegin.InProgress -> throw RegistryException(
                503,
                "temporarily_unavailable",
                "An identical request is still being processed",
                retryable = true,
                retryAfterSeconds = 5,
            )
            IdempotencyBegin.Acquired -> Unit
        }

        val stored = try {
            val result = operation(body)
            val responseBody = json.encodeToString(result).encodeToByteArray()
            StoredIdempotencyResponse(
                status = successStatus,
                bodyBase64 = Base64.getEncoder().encodeToString(responseBody),
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "Content-Length" to responseBody.size.toString(),
                    "X-Request-Id" to requestId,
                ) + responseHeaders(result),
            )
        } catch (error: RegistryException) {
            errorResponse(error, requestId)
        } catch (error: Exception) {
            log.error("Unhandled idempotent enforcement mutation failure requestId={}", requestId, error)
            errorResponse(
                RegistryException(
                    500,
                    "internal_error",
                    "The registry could not complete the request",
                    retryable = true,
                    retryAfterSeconds = 30,
                ),
                requestId,
            )
        }
        if (!repository.completeIdempotency(scope, fingerprint, requestId, stored)) {
            throw RegistryException(
                503,
                "temporarily_unavailable",
                "The idempotent request lease changed",
                retryable = true,
                retryAfterSeconds = 5,
            )
        }
        sendStored(exchange, stored, replayed = false)
    }

    private inline fun <reified Request> decode(body: ByteArray): Request {
        if (body.isEmpty()) invalidRequest()
        return try {
            json.decodeFromString(body.decodeToString())
        } catch (_: Exception) {
            invalidRequest()
        }
    }

    private fun invalidRequest(): Nothing =
        throw RegistryException(400, "invalid_request", "Request body is invalid")

    private fun errorResponse(error: RegistryException, requestId: String): StoredIdempotencyResponse {
        val body = json.encodeToString(ErrorEnvelope(ApiError(
            code = error.code,
            message = error.publicMessage,
            requestId = requestId,
            occurredAt = clock.instant().utc(),
            retryable = error.retryable,
            retryAfterSeconds = error.retryAfterSeconds,
            details = JsonObject(emptyMap()),
        ))).encodeToByteArray()
        val headers = mutableMapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Content-Length" to body.size.toString(),
            "X-Request-Id" to requestId,
            "Cache-Control" to "no-store",
        )
        if (error.status == 401) headers["WWW-Authenticate"] = "Bearer realm=\"seen-registry\""
        error.retryAfterSeconds?.let { headers["Retry-After"] = it.toString() }
        return StoredIdempotencyResponse(error.status, Base64.getEncoder().encodeToString(body), headers)
    }

    private suspend fun sendStored(exchange: Exchange, stored: StoredIdempotencyResponse, replayed: Boolean) {
        val body = Base64.getDecoder().decode(stored.bodyBase64)
        stored.headers.forEach(exchange.response::setHeader)
        exchange.response.setHeader("Idempotency-Replayed", replayed.toString())
        exchange.response.statusCode = stored.status
        exchange.response.write(body)
        exchange.response.end()
    }

    private suspend inline fun <reified Value> respondAccessControlled(exchange: Exchange, value: Value) {
        val bytes = json.encodeToString(value).encodeToByteArray()
        exchange.response.setHeader("ETag", "\"sha256:${sha256(bytes)}\"")
        exchange.response.setHeader("Cache-Control", "private,no-store")
        respondBytes(exchange, 200, bytes)
    }

    private suspend inline fun <reified Value> respondJson(exchange: Exchange, status: Int, value: Value) =
        respondBytes(exchange, status, json.encodeToString(value).encodeToByteArray())

    private suspend fun respondBytes(exchange: Exchange, status: Int, bytes: ByteArray) {
        exchange.response.statusCode = status
        exchange.response.setHeader("Content-Type", "application/json; charset=utf-8")
        exchange.response.setHeader("Content-Length", bytes.size.toString())
        exchange.response.write(bytes)
        exchange.response.end()
    }

    private fun actor(exchange: Exchange): EnforcementPrincipal =
        auth.authenticate(exchange.request.headers["Authorization"])

    private fun identity(exchange: Exchange): String =
        "${exchange.pathParamOrThrow("owner")}/${exchange.pathParamOrThrow("name")}"

    private fun version(exchange: Exchange): String = exchange.pathParamOrThrow("version")

    private fun requireIdempotency(exchange: Exchange): String {
        val value = exchange.request.headers["Idempotency-Key"]
            ?: throw RegistryException(400, "idempotency_key_required", "Idempotency-Key is required")
        if (value.toByteArray().size !in 16..128) invalidRequest()
        return value
    }

    private fun requestId(): String = "req_" + ByteArray(18).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun framedDigest(vararg values: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value.size.toLong()).array())
            digest.update(value)
        }
        return digest.digest().toHex()
    }
}

enum class EnforcementRouteSurface {
    REPORTS_AND_APPEALS,
    RELEASE_ACTIONS,
    SECURITY_ACTIONS,
}
