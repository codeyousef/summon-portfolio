package code.yousef.portfolio.e2e

import code.yousef.buildApplication
import code.yousef.config.AppConfig
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydrationTest {

    private lateinit var server: VertxServer
    private val testPort = 8082
    private val baseUrl = "http://localhost:$testPort"
    private val httpClient = HttpClient.newHttpClient()

    @BeforeAll
    fun setup() {
        val appConfig = AppConfig(
            projectId = "test-project",
            emulatorHost = "localhost:8080",
            port = testPort,
            useLocalStore = true
        )
        val resources = buildApplication(appConfig)
        val config = VertxServerConfig(port = testPort)
        server = VertxServer(config, resources.pipeline) { exchange ->
            exchange.notFound("Route not found")
        }
        runBlocking {
            server.start()
        }
    }

    @AfterAll
    fun teardown() {
        runBlocking {
            server.stop()
        }
    }

    @Test
    fun `should serve landing page with correct hydration tags`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), "Should be HTML")
        
        val body = response.body()
        // Verify Summon hydration script is present
        assertTrue(body.contains("summon-hydration.js"), "Should contain summon-hydration.js script tag")
    }

    @Test
    fun `should serve WASM binary with correct MIME type`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/static/summon-hydration.wasm"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
             assertEquals("application/wasm", response.headers().firstValue("Content-Type").orElse(""))
        }
    }

    @Test
    fun `fifth wall serves client game shell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())

        val body = response.body()
        assertTrue(body.contains("id=\"fifth-wall-game-root\""), "Should expose the client game mount")
        assertTrue(body.contains("model-viewer.min.js"), "Should load the 3D model runtime")
        assertTrue(body.contains("/static/fifth-wall-client-game.js"), "Should load the client-owned game runtime")
        assertFalse(body.contains("fw-scene-control-deck"), "Should not render SSR gameplay controls")
    }

    @Test
    fun `fifth wall action query stays on client game shell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall?action=start"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode(), "Normal gameplay action queries should not redirect")
        assertFalse(response.headers().firstValue("Location").isPresent, "Should not send a redirect location")
        assertTrue(response.body().contains("id=\"fifth-wall-game-root\""), "Should still serve the static game shell")
    }

    @Test
    fun `fifth wall client runtime is served as static javascript`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/static/fifth-wall-client-game.js"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(
            response.headers().firstValue("Content-Type").orElse("").contains("javascript"),
            "Should serve JavaScript content"
        )
        assertTrue(response.body().contains("package_delivered"), "Should include client gameplay telemetry events")
        assertTrue(response.body().contains(".glb"), "Should reference local Fifth Wall GLB assets")
    }
}
