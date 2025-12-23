package code.yousef.portfolio.server

import code.yousef.portfolio.ssr.SummonPage
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

    // Create stable callback context for this request to ensure callbacks
    // registered during rendering can be reliably collected even if the
    // coroutine switches threads
    val callbackContext = CallbackContextElement()

    try {
        // CRITICAL: Install callback context BEFORE rendering starts
        val html = withContext(callbackContext) {
            // The context is now properly installed in the thread-local before rendering
            // Note: We use the simpler renderComposableRoot because renderComposableRootWithHydration signature mismatch
            // TODO: Fix hydration signature match
            try {
                val content = renderer.renderComposableRoot {
                    // Render head elements
                    renderer.renderHeadElements(page.head)
                    page.content()
                }
                // Add DOCTYPE to ensure standards mode (not quirks mode)
                "<!DOCTYPE html>$content"
            } catch (e: Exception) {
                System.err.println("ERROR in renderComposableRoot: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
        try {
            respondHtml(status, html)
        } catch (e: Exception) {
            System.err.println("ERROR in respondHtml: ${e.message}")
            e.printStackTrace()
            throw e
        }
    } catch (e: Exception) {
        System.err.println("ERROR in respondSummonPage: ${e.message}")
        e.printStackTrace()
        // Send error response
        response.statusCode = 500
        response.setHeader("Content-Type", "text/plain")
        response.write("Internal Server Error: ${e.message}")
        response.end()
    } finally {
        clearPlatformRenderer()
    }
}

