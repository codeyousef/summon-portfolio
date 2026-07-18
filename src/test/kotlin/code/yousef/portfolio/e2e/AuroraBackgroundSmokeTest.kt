package code.yousef.portfolio.e2e

import code.yousef.buildApplication
import code.yousef.config.AppConfig
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuroraBackgroundSmokeTest {

    @Test
    fun `landing aurora canvas avoids page height DPR allocations`() {
        val testPort = 8084
        val resources = buildApplication(
            AppConfig(
                projectId = "test-project",
                emulatorHost = "localhost:8080",
                port = testPort,
                useLocalStore = true
            )
        )
        val server = VertxServer(VertxServerConfig(port = testPort), resources.pipeline) { exchange ->
            exchange.notFound("Route not found")
        }

        runBlocking {
            server.start()
        }

        try {
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$testPort/"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )

            assertEquals(200, response.statusCode())

            val body = response.body()
            assertTrue(body.contains("summon-hydration.js"), "Should contain summon-hydration.js script tag")
            assertTrue(body.contains("height: 120vh"), "Aurora background should use bounded viewport overscan")
            assertTrue(
                body.contains("""id="aurora-canvas-container" style="width: 100%; height: 120vh;"""),
                "Sigil canvas container should receive explicit viewport height instead of a collapsible 100%"
            )
            assertTrue(
                body.contains("data-aurora-blend=\"true\""),
                "Aurora background should render a blend layer over the canvas boundary"
            )
            assertTrue(
                body.contains("background-image: radial-gradient"),
                "Aurora container should use a valid typed background-image gradient declaration"
            )
            assertTrue(
                body.contains("\"respectDevicePixelRatio\":false"),
                "Decorative Aurora canvas should avoid DPR-scaled WebGL allocations"
            )
            assertFalse(body.contains("height: 3500px"), "Aurora background should not allocate a page-height canvas")
        } finally {
            runBlocking {
                server.stop()
                resources.onShutdown()
            }
        }
    }
}
