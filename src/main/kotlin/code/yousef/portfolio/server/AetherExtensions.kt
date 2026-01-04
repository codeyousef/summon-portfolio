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
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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

class StaticResourceHandler(
    private val resourcePackage: String,
    private val urlPrefix: String = "/"
) {
    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        val path = exchange.request.path
        if (path.startsWith(urlPrefix)) {
            val relativePath = path.removePrefix(urlPrefix).trimStart('/')
            if (relativePath.isBlank()) {
                next()
                return
            }
            
            val resourcePath = if (resourcePackage.isEmpty()) relativePath else "$resourcePackage/$relativePath"
            val resourceStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
                ?: javaClass.classLoader.getResourceAsStream(resourcePath)

            if (resourceStream != null) {
                val contentType = URLConnection.guessContentTypeFromName(relativePath) ?: "application/octet-stream"
                exchange.respondBytes(200, contentType, resourceStream.readBytes())
                return
            }
        }
        next()
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

suspend fun Exchange.respondSummonPage(page: SummonPage, status: Int = 200) {
    val renderer = PlatformRenderer()
    setPlatformRenderer(renderer)

    // Resolve environment links from the Host header for proper URL generation
    val host = request.headers["Host"]
    val links = resolveEnvironmentLinks(host)

    // Create stable callback context for this request to ensure callbacks
    // registered during rendering can be reliably collected even if the
    // coroutine switches threads
    val callbackContext = CallbackContextElement()

    try {
        // CRITICAL: Install callback context BEFORE rendering starts
        val html = withContext(callbackContext) {
            // Set environment links context for URL resolution during rendering
            EnvironmentLinksRegistry.withLinks(links) {
                // The context is now properly installed in the thread-local before rendering
                try {
                    // IMPORTANT: Render head elements BEFORE renderComposableRootWithHydration
                    // The head section is created inside the render function and reads headElements synchronously,
                    // so they must be added before the render function is called
                    renderer.renderHeadElements(page.head)
                    
                    // Use renderComposableRootWithHydration to include the bootloader script
                    // which handles data-action toggles for HamburgerMenu, Dropdown, etc.
                    val lang = page.locale.code
                    val dir = page.locale.direction
                    renderer.renderComposableRootWithHydration(lang, dir) {
                        page.content()
                    }
                } catch (e: Exception) {
                    System.err.println("ERROR in renderComposableRootWithHydration: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
            }
        }
        try {
            respondHtmlWithLength(status, html)
        } catch (e: Exception) {
            System.err.println("ERROR in respondHtml: ${e.message}")
            e.printStackTrace()
            throw e
        }
    } catch (e: Exception) {
        System.err.println("ERROR in respondSummonPage: ${e.message}")
        e.printStackTrace()
        // Send error response with Content-Length to avoid Vert.x chunked encoding error
        val errorBody = "Internal Server Error: ${e.message}\n\nStack Trace:\n${e.stackTraceToString()}"
        val errorBytes = errorBody.toByteArray(Charsets.UTF_8)
        response.statusCode = 500
        response.setHeader("Content-Type", "text/plain")
        response.setHeader("Content-Length", errorBytes.size.toString())
        response.write(errorBytes)
        response.end()
    } finally {
        clearPlatformRenderer()
    }
}

