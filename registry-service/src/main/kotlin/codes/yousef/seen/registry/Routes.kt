package codes.yousef.seen.registry

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.respondJson
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParamOrThrow
import codes.yousef.aether.web.router
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.util.Base64

class RegistryRoutes(
    private val service: RegistryService,
    private val auth: OpaqueDevWriterAuthenticator,
    private val clock: Clock,
    private val promotionMode: String = "disabled",
    private val json: kotlinx.serialization.json.Json = RegistryJson,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    val router: Router = router {
        get("/health") { exchange -> safe(exchange) { exchange.respondJson(200, mapOf("status" to "ok")) } }
        get("/health/ready") { exchange -> safe(exchange) {
            if (service.isReady()) exchange.respondJson(200, mapOf("status" to "ready"))
            else throw RegistryException(503, "temporarily_unavailable", "Registry bootstrap metadata is not ready", true, 30)
        } }
        get("/") { exchange -> safe(exchange) { exchange.respondHtml(200, CatalogRenderer.render(service.listPublicPackages().items)) } }
        get("/packages") { exchange -> safe(exchange) { exchange.respondHtml(200, CatalogRenderer.render(service.listPublicPackages().items)) } }

        get("/packages/api/v1/packages") { exchange -> safe(exchange) { exchange.respondJson(200, service.listPublicPackages(), json) } }
        post("/packages/api/v1/packages") { exchange -> safe(exchange) { requestId ->
            val principal = writer(exchange)
            idempotentJson<CreatePackageRequest, PackageRecord>(exchange, requestId, principal, 201, { value ->
                mapOf("Location" to value.links.self)
            }) { request ->
                auth.authorizeOwner(IdentityRules.requireIdentity(request.identity).first)
                service.createPackage(request, principal)
            }
        } }
        get("/packages/api/v1/packages/:owner/:name") { exchange -> safe(exchange) {
            exchange.respondJson(200, service.getPackage(identity(exchange), optionalWriter(exchange)), json)
        } }
        get("/packages/api/v1/packages/:owner/:name/releases") { exchange -> safe(exchange) {
            exchange.respondJson(200, service.listReleases(identity(exchange), optionalWriter(exchange)), json)
        } }
        post("/packages/api/v1/packages/:owner/:name/releases") { exchange -> safe(exchange) { requestId ->
            val owner = exchange.pathParamOrThrow("owner")
            val principal = writer(exchange)
            auth.authorizeOwner(owner)
            idempotentJson<ReserveReleaseRequest, ReleaseReservation>(exchange, requestId, principal, 201, { value ->
                mapOf("Location" to value.release.links.self)
            }) { request -> service.reserveRelease(identity(exchange), request, principal) }
        } }
        get("/packages/api/v1/packages/:owner/:name/releases/:version") { exchange -> safe(exchange) {
            exchange.respondJson(200, service.getRelease(identity(exchange), exchange.pathParamOrThrow("version"), optionalWriter(exchange)), json)
        } }
        put("/packages/api/v1/uploads/:uploadId/archive") { exchange -> safe(exchange) {
            val principal = writer(exchange)
            val bytes = exchange.request.bodyBytes()
            service.uploadArchive(exchange.pathParamOrThrow("uploadId"), exchange.request.headers["X-Seen-Archive-Sha256"], bytes, principal)
            exchange.response.statusCode = 204
            exchange.response.setHeader("X-Seen-Archive-Sha256", sha256(bytes))
            exchange.response.setHeader("ETag", "\"sha256:${sha256(bytes)}\"")
            exchange.response.end()
        } }
        post("/packages/api/v1/uploads/:uploadId/complete") { exchange -> safe(exchange) { requestId ->
            val principal = writer(exchange)
            idempotentJson<CompleteUploadRequest, ReleaseRecord>(exchange, requestId, principal, 202, { value ->
                mapOf("Location" to value.links.self)
            }) { request -> service.completeUpload(exchange.pathParamOrThrow("uploadId"), request, principal) }
        } }
        if (promotionMode == "test-static") {
            post("/packages/internal/v1/promote-due") { exchange -> safe(exchange) {
                requireIdempotency(exchange)
                writer(exchange)
                exchange.respondJson(200, mapOf("promoted" to service.promoteDue().size), json)
            } }
        }
        get("/packages/api/v1/metadata/:filename") { exchange -> safe(exchange) {
            val filename = exchange.pathParamOrThrow("filename")
            val bytes = service.metadata(filename)
            val digest = sha256(bytes)
            exchange.response.setHeader("X-Seen-Metadata-Sha256", digest)
            exchange.response.setHeader("ETag", "\"sha256:$digest\"")
            exchange.response.setHeader("Cache-Control", if (filename == "timestamp.json" || filename == "root.json") "public,max-age=300,must-revalidate" else "public,max-age=31536000,immutable")
            exchange.response.setHeader("Content-Length", bytes.size.toString())
            exchange.respondBytes(200, "application/vnd.seen.tuf+json", bytes)
        } }
        get("/packages/api/v1/blobs/sha256/:digest") { exchange -> safe(exchange) {
            val digest = exchange.pathParamOrThrow("digest")
            val bytes = service.publicBlob(digest)
            exchange.response.setHeader("X-Seen-Archive-Sha256", digest)
            exchange.response.setHeader("ETag", "\"sha256:$digest\"")
            exchange.response.setHeader("Cache-Control", "public,max-age=31536000,immutable")
            exchange.response.setHeader("Content-Length", bytes.size.toString())
            exchange.respondBytes(200, "application/gzip", bytes)
        } }
        get("/packages/api/v1/packages/:owner/:name/releases/:version/download") { exchange -> safe(exchange) {
            val (release, bytes) = service.downloadRelease(
                identity(exchange),
                exchange.pathParamOrThrow("version"),
                optionalWriter(exchange),
            )
            val digest = release.archive.sha256
            exchange.response.setHeader("X-Seen-Archive-Sha256", digest)
            exchange.response.setHeader("ETag", "\"sha256:$digest\"")
            exchange.response.setHeader("Cache-Control", "public,max-age=31536000,immutable")
            exchange.response.setHeader("Content-Length", bytes.size.toString())
            exchange.respondBytes(200, "application/gzip", bytes)
        } }
        get("/packages/:owner/:name") { exchange -> safeCatalog(exchange) {
            val identity = identity(exchange)
            val pkg = service.getPackage(identity)
            val releases = service.listReleases(identity).items
            exchange.response.setHeader("Cache-Control", "public,max-age=60,must-revalidate")
            exchange.respondHtml(200, CatalogRenderer.renderPackage(pkg, releases))
        } }
        get("/packages/:owner/:name/:version") { exchange -> safeCatalog(exchange) {
            val identity = identity(exchange)
            val pkg = service.getPackage(identity)
            val release = service.getRelease(identity, exchange.pathParamOrThrow("version"))
            exchange.response.setHeader("Cache-Control", "public,max-age=60,must-revalidate")
            exchange.respondHtml(200, CatalogRenderer.renderRelease(pkg, release))
        } }
    }

    private suspend inline fun safe(exchange: Exchange, crossinline action: suspend (String) -> Unit) {
        val requestId = requestId()
        exchange.response.setHeader("X-Request-Id", requestId)
        try {
            action(requestId)
        } catch (error: RegistryException) {
            if (error.status == 401) exchange.response.setHeader("WWW-Authenticate", "Bearer realm=\"seen-registry\"")
            if (error.retryAfterSeconds != null) exchange.response.setHeader("Retry-After", error.retryAfterSeconds.toString())
            if (exchange.request.headers["Idempotency-Key"] != null) exchange.response.setHeader("Idempotency-Replayed", "false")
            exchange.response.setHeader("Cache-Control", "no-store")
            exchange.respondJson(error.status, ErrorEnvelope(ApiError(
                code = error.code,
                message = error.publicMessage,
                requestId = requestId,
                occurredAt = clock.instant().utc(),
                retryable = error.retryable,
                retryAfterSeconds = error.retryAfterSeconds,
                details = JsonObject(emptyMap()),
            )), json)
        } catch (error: Exception) {
            log.error("Unhandled registry request failure requestId={}", requestId, error)
            exchange.response.setHeader("Cache-Control", "no-store")
            exchange.response.setHeader("Retry-After", "30")
            exchange.respondJson(500, ErrorEnvelope(ApiError(
                code = "internal_error",
                message = "The registry could not complete the request",
                requestId = requestId,
                occurredAt = clock.instant().utc(),
                retryable = true,
                retryAfterSeconds = 30,
            )), json)
        }
    }

    private suspend inline fun safeCatalog(exchange: Exchange, crossinline action: suspend () -> Unit) {
        val requestId = requestId()
        exchange.response.setHeader("X-Request-Id", requestId)
        try {
            action()
        } catch (error: RegistryException) {
            val notFound = error.status == 400 || error.status == 404
            exchange.response.setHeader("Cache-Control", "no-store")
            exchange.respondHtml(if (notFound) 404 else error.status, CatalogRenderer.renderUnavailable(notFound))
        } catch (error: Exception) {
            log.error("Unhandled registry catalog failure requestId={}", requestId, error)
            exchange.response.setHeader("Cache-Control", "no-store")
            exchange.respondHtml(500, CatalogRenderer.renderUnavailable(false))
        }
    }

    private suspend inline fun <reified Request, reified Result> idempotentJson(
        exchange: Exchange,
        requestId: String,
        principal: WriterPrincipal,
        successStatus: Int,
        noinline responseHeaders: (Result) -> Map<String, String> = { emptyMap() },
        crossinline operation: (Request) -> Result,
    ) {
        val key = requireIdempotency(exchange)
        val body = exchange.request.bodyBytes()
        val fingerprint = framedDigest(
            body,
            exchange.request.headers["Content-Type"].orEmpty().encodeToByteArray(),
            exchange.request.headers["X-Seen-Archive-Sha256"].orEmpty().encodeToByteArray(),
        )
        val scope = framedDigest(
            principal.subject.encodeToByteArray(),
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
        when (val begin = service.beginIdempotency(reservation, now)) {
            is IdempotencyBegin.Replay -> {
                sendStored(exchange, requireNotNull(begin.value.response), replayed = true)
                return
            }
            IdempotencyBegin.InProgress -> throw RegistryException(
                503, "temporarily_unavailable", "An identical request is still being processed", true, 5,
            )
            IdempotencyBegin.Acquired -> Unit
        }

        val stored = try {
            val request = try {
                json.decodeFromString<Request>(body.decodeToString())
            } catch (_: Exception) {
                throw RegistryException(400, "invalid_request", "Request body is invalid")
            }
            val result = operation(request)
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
            log.error("Unhandled idempotent registry mutation failure requestId={}", requestId, error)
            errorResponse(RegistryException(
                500,
                "internal_error",
                "The registry could not complete the request",
                retryable = true,
                retryAfterSeconds = 30,
            ), requestId)
        }
        if (!service.completeIdempotency(scope, fingerprint, requestId, stored)) {
            throw RegistryException(503, "temporarily_unavailable", "The idempotent request lease changed", true, 5)
        }
        sendStored(exchange, stored, replayed = false)
    }

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

    private fun framedDigest(vararg values: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value.size.toLong()).array())
            digest.update(value)
        }
        return digest.digest().toHex()
    }

    private fun writer(exchange: Exchange): WriterPrincipal = auth.authenticate(exchange.request.headers["Authorization"])
    private fun optionalWriter(exchange: Exchange): WriterPrincipal? = exchange.request.headers["Authorization"]?.let(auth::authenticate)
    private fun identity(exchange: Exchange): String = "${exchange.pathParamOrThrow("owner")}/${exchange.pathParamOrThrow("name")}"
    private fun requireIdempotency(exchange: Exchange): String {
        val value = exchange.request.headers["Idempotency-Key"] ?: throw RegistryException(400, "idempotency_key_required", "Idempotency-Key is required")
        if (value.toByteArray().size !in 16..128) throw RegistryException(400, "invalid_request", "Idempotency-Key is invalid")
        return value
    }
    private fun requestId(): String = "req_" + ByteArray(18).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
