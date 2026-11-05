package code.yousef.portfolio.server

import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.pipeline.Middleware
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import codes.yousef.summon.runtime.CallbackContextElement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val summonSsrRenderMutex = Mutex()
private val summonResponseLogger = LoggerFactory.getLogger("SummonResponse")

/**
 * Workaround for Aether 0.2.0.0 bug - sets Content-Length to avoid Vert.x chunked encoding error.
 * TODO: Remove once Aether 0.2.0.1 is released (which enables chunked encoding automatically)
 */
suspend fun Exchange.respondHtmlWithLength(statusCode: Int = 200, html: String) {
    response.statusCode = statusCode
    response.setHeader("Content-Type", "text/html; charset=utf-8")
    val bytes = html.toByteArray(Charsets.UTF_8)
    response.setHeader("Content-Length", bytes.size.toString())
    response.write(bytes)
    response.end()
}

/**
 * Aether 0.5.1.0 does not set Content-Length on an empty redirect response.
 * Vert.x rejects that response after its Location header has already been
 * written, and the outer recovery boundary then turns it into a misleading
 * 500. Keep redirects bodyless and explicit until the framework fix lands.
 */
suspend fun Exchange.redirectWithLength(location: String, permanent: Boolean = false) {
    require(location.startsWith("/") || location.startsWith("https://")) { "unsafe redirect location" }
    response.statusCode = if (permanent) 301 else 302
    response.setHeader("Location", location)
    response.setHeader("Content-Length", "0")
    response.setHeader("Cache-Control", "no-store")
    response.end()
}

class StaticResourceHandler(
    private val resourcePackage: String,
    private val urlPrefix: String = "/"
) {
    private data class StaticAsset(
        val bytes: ByteArray,
        val contentType: String,
        val cacheControl: String,
        val etag: String
    )

    private val assetCache = ConcurrentHashMap<String, StaticAsset>()

    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        val path = exchange.request.path
        if (path.startsWith(urlPrefix)) {
            val relativePath = path.removePrefix(urlPrefix).trimStart('/')
            if (relativePath.isBlank()) {
                next()
                return
            }
            
            val resourcePath = if (resourcePackage.isEmpty()) relativePath else "$resourcePackage/$relativePath"
            val asset = loadAsset(resourcePath, relativePath)

            if (asset != null) {
                exchange.response.setHeader("Cache-Control", asset.cacheControl)
                exchange.response.setHeader("ETag", asset.etag)
                exchange.response.setHeader("X-Content-Type-Options", "nosniff")

                val requestEtags = exchange.request.headers["If-None-Match"]
                    ?.split(',')
                    ?.map { it.trim() }
                    .orEmpty()
                if ("*" in requestEtags || asset.etag in requestEtags) {
                    exchange.response.statusCode = 304
                    exchange.response.end()
                    return
                }

                exchange.respondBytes(200, asset.contentType, asset.bytes)
                return
            }
        }
        next()
    }

    private fun loadAsset(resourcePath: String, relativePath: String): StaticAsset? {
        assetCache[resourcePath]?.let { return it }

        val bytes = (Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: javaClass.classLoader.getResourceAsStream(resourcePath))
            ?.use { it.readBytes() }
            ?: return null

        val loaded = StaticAsset(
            bytes = bytes,
            contentType = contentTypeFor(relativePath),
            cacheControl = cacheControlFor(relativePath),
            etag = "W/\"${bytes.size}-${bytes.contentHashCode().toUInt().toString(16)}\""
        )
        return assetCache.putIfAbsent(resourcePath, loaded) ?: loaded
    }

    private fun contentTypeFor(relativePath: String): String {
        return when (relativePath.substringAfterLast('.', "").lowercase()) {
            "css" -> "text/css; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            "json", "map" -> "application/json; charset=utf-8"
            "gltf" -> "model/gltf+json"
            "glb" -> "model/gltf-binary"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "webp" -> "image/webp"
            "wasm" -> "application/wasm"
            "bin" -> "application/octet-stream"
            else -> URLConnection.guessContentTypeFromName(relativePath) ?: "application/octet-stream"
        }
    }

    private fun cacheControlFor(relativePath: String): String {
        val normalizedPath = relativePath.lowercase()
        val extension = normalizedPath.substringAfterLast('.', "")
        return when {
            normalizedPath.startsWith("models/") -> "public, max-age=604800"
            extension in setOf("jpg", "jpeg", "png", "gif", "svg", "ico", "webp", "glb", "gltf", "bin") ->
                "public, max-age=604800"
            extension in setOf("css", "js") -> "public, max-age=3600, must-revalidate"
            else -> "public, max-age=3600"
        }
    }
}

class HostRouter(private val hostMap: Map<String, Middleware>) {
    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        val host = exchange.request.headers["Host"]?.substringBefore(":")
        val handler = hostMap.entries.find { (key, _) -> 
            key == "*" || host == key || (key.startsWith("*.") && host?.endsWith(key.removePrefix("*.")) == true)
        }?.value
        
        if (handler != null) {
            handler(exchange, next)
        } else {
            next()
        }
    }
}

internal fun PlatformRenderer.renderSummonDocument(page: SummonPage): String {
    // Head elements must be registered before rendering because Summon reads them
    // synchronously while it creates the hydrated document.
    renderHeadElements(page.head)

    return renderComposableRootWithHydration(page.locale.code, page.locale.direction) {
        page.content()
    }
}

suspend fun Exchange.respondSummonPage(page: SummonPage, status: Int = 200) {
    // Resolve environment links from the Host header for proper URL generation
    val host = request.headers["Host"]
    val links = resolveEnvironmentLinks(host)

    // Create stable callback context for this request to ensure callbacks
    // registered during rendering can be reliably collected even if the
    // coroutine switches threads
    val callbackContext = CallbackContextElement()

    try {
        val renderStartedAt = System.nanoTime()
        // CRITICAL: Install callback context BEFORE rendering starts
        val html = summonSsrRenderMutex.withLock {
            withContext(callbackContext) {
                val renderer = PlatformRenderer()
                setPlatformRenderer(renderer)
                try {
                    // Set environment links context for URL resolution during rendering
                    EnvironmentLinksRegistry.withLinks(links) {
                        try {
                            // Use renderComposableRootWithHydration to include the bootloader script
                            // which handles data-action toggles for HamburgerMenu, Dropdown, etc.
                            renderer.renderSummonDocument(page)
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                } finally {
                    clearPlatformRenderer()
                }
            }
        }
        response.setHeader("Server-Timing", summonRenderServerTiming(System.nanoTime() - renderStartedAt))
        try {
            respondHtmlWithLength(status, html)
        } catch (e: Exception) {
            throw e
        }
    } catch (e: Exception) {
        respondInternalServerError(summonResponseLogger, "Summon SSR response", e)
    }
}

internal fun summonRenderServerTiming(durationNanos: Long): String {
    val boundedNanos = durationNanos.coerceIn(0L, 120_000_000_000L)
    val durationMs = boundedNanos / 1_000_000.0
    return "summon_render;dur=${String.format(Locale.ROOT, "%.1f", durationMs)}"
}
