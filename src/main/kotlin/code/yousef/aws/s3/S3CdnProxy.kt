package code.yousef.aws.s3

import codes.yousef.aether.core.Exchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Development-only origin for `/cdn/media/<key>` and `/cdn/docs/<key>`
 * (`PORTFOLIO_CDN_PROXY_ENABLED=true`). Production CloudFront serves these paths straight
 * from S3 with the `/cdn/<bucket>/` prefix stripped; in dev they must stay behind the ALB
 * authentication gate, so CloudFront forwards them to the application, which streams the
 * same object (`key` = path without the prefix) from the configured bucket.
 */
class S3CdnProxy(
    private val client: S3Client,
    private val mediaBucket: String?,
    private val docsBucket: String?,
) {
    private val log = LoggerFactory.getLogger(S3CdnProxy::class.java)

    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        val path = exchange.request.path
        val (kind, bucket) = when {
            path.startsWith(MEDIA_PREFIX) -> "media" to mediaBucket
            path.startsWith(DOCS_PREFIX) -> "docs" to docsBucket
            else -> return next()
        }
        val method = exchange.request.method.name.uppercase()
        if (method != "GET" && method != "HEAD") {
            exchange.response.setHeader("Allow", "GET, HEAD")
            exchange.respond(405, "Method not allowed")
            return
        }
        val key = cdnObjectKey(path.removePrefix(if (kind == "media") MEDIA_PREFIX else DOCS_PREFIX))
        if (key == null || bucket == null) {
            exchange.respond(404, "Not found")
            return
        }
        val ifNoneMatch = exchange.request.headers["If-None-Match"]?.trim()?.takeIf(String::isNotEmpty)
        try {
            if (method == "HEAD") {
                val head = withContext(Dispatchers.IO) {
                    client.headObject { it.bucket(bucket).key(key).ifNoneMatch(ifNoneMatch) }
                }
                applyHeaders(exchange, kind, head.contentType(), head.eTag(), head.contentLength())
                exchange.response.statusCode = 200
                exchange.response.end()
                return
            }
            val stream: ResponseInputStream<GetObjectResponse> = withContext(Dispatchers.IO) {
                client.getObject { it.bucket(bucket).key(key).ifNoneMatch(ifNoneMatch) }
            }
            stream.use { body ->
                val response = body.response()
                applyHeaders(exchange, kind, response.contentType(), response.eTag(), response.contentLength())
                exchange.response.statusCode = 200
                val buffer = ByteArray(CHUNK_BYTES)
                while (true) {
                    val read = withContext(Dispatchers.IO) { body.read(buffer) }
                    if (read < 0) break
                    if (read > 0) exchange.response.write(buffer.copyOf(read))
                }
                exchange.response.end()
            }
        } catch (_: NoSuchKeyException) {
            exchange.respond(404, "Not found")
        } catch (failure: S3Exception) {
            when (failure.statusCode()) {
                304 -> {
                    failure.awsErrorDetails()?.sdkHttpResponse()?.firstMatchingHeader("ETag")?.orElse(null)
                        ?.let { exchange.response.setHeader("ETag", it) }
                    exchange.response.setHeader("Cache-Control", CACHE_CONTROL)
                    exchange.response.statusCode = 304
                    exchange.response.end()
                }
                // Without s3:ListBucket, S3 reports a missing key as 403.
                403, 404 -> {
                    if (failure.statusCode() == 403) log.debug("S3 CDN proxy denied or missing object for {}", kind)
                    exchange.respond(404, "Not found")
                }
                else -> throw failure
            }
        }
    }

    private fun applyHeaders(exchange: Exchange, kind: String, contentType: String?, eTag: String?, length: Long?) {
        val response = exchange.response
        response.setHeader("Content-Type", contentType ?: "application/octet-stream")
        eTag?.let { response.setHeader("ETag", it) }
        length?.let { response.setHeader("Content-Length", it.toString()) }
        response.setHeader("Cache-Control", CACHE_CONTROL)
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin")
        if (kind == "docs" && contentType?.startsWith("text/html") == true) {
            response.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'")
        }
    }

    companion object {
        const val MEDIA_PREFIX = "/cdn/media/"
        const val DOCS_PREFIX = "/cdn/docs/"
        private const val CHUNK_BYTES = 64 * 1024
        private const val CACHE_CONTROL = "public, max-age=31536000, immutable"
        private val SEGMENT = Regex("^[A-Za-z0-9._-]{1,255}$")

        /** Validates the object key below the prefix; null for empty or traversal paths. */
        internal fun cdnObjectKey(raw: String): String? {
            if (raw.isEmpty() || raw.length > 1_024) return null
            val segments = raw.split('/')
            if (segments.any { it.isEmpty() || it == "." || it == ".." || !SEGMENT.matches(it) }) return null
            return raw
        }
    }
}
