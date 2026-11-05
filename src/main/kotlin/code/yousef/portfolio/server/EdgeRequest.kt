package code.yousef.portfolio.server

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.Request
import java.security.MessageDigest

internal const val EDGE_VIEWER_HOST_HEADER = "X-Viewer-Host"
internal const val EDGE_ORIGIN_VERIFY_HEADER = "X-Origin-Verify"

/**
 * A Lambda function URL requires its own Host header, so CloudFront carries the
 * viewer host separately. The header is trusted only behind [EdgeOriginGuard].
 */
internal object EdgeHostTrust {
    @Volatile
    var viewerHostTrusted: Boolean = false
}

internal fun Request.publicHostHeader(): String? {
    val viewerHost = headers[EDGE_VIEWER_HOST_HEADER]?.takeIf { EdgeHostTrust.viewerHostTrusted }
    return viewerHost ?: headers["Host"]
}

/** Rejects requests that did not come through CloudFront. */
internal class EdgeOriginGuard(secret: String) {
    private val expected = secret.toByteArray(Charsets.UTF_8)

    init {
        require(expected.isNotEmpty()) { "The edge origin secret must not be empty" }
    }

    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        val presented = exchange.request.headers[EDGE_ORIGIN_VERIFY_HEADER].orEmpty().toByteArray(Charsets.UTF_8)
        if (MessageDigest.isEqual(expected, presented)) {
            next()
        } else {
            exchange.respond(403, "Forbidden")
        }
    }
}
