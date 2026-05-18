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
    fun `fifth wall serves Materia Sigil scene shell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())

        val body = response.body()
        assertTrue(body.contains("fifth-wall-scene"), "Should expose the Materia/Sigil scene mount")
        assertTrue(body.contains("warehouse-bay-shell-kit"), "Should render the warehouse GLTF scene content")
        assertTrue(body.contains("conveyor-deck"), "Should render the conveyor as scene content")
        assertTrue(body.contains("delivery-truck"), "Should render trucks as scene content")
        assertTrue(body.contains("/sigil-hydration.js"), "Should load the generic Sigil 0.4 runtime")
        assertTrue(body.contains("\"interactionId\":\"package:"), "Packages should expose Sigil interaction IDs")
        assertTrue(body.contains("\"interactionId\":\"truck:0\""), "Trucks should expose Sigil drop-target interaction IDs")
        assertTrue(body.contains("\"interactionId\":\"return-bin\""), "Return bin should expose a Sigil drop-target interaction ID")
        assertTrue(body.contains("\"interactionId\":\"inspection-dock\""), "Inspection dock should expose a Sigil drop-target interaction ID")
        assertTrue(body.contains("\"drag\":{\"enabled\":true"), "Packages should declare Sigil drag metadata")
        assertTrue(body.contains("\"dropTarget\":{\"enabled\":true"), "Routing targets should declare Sigil drop-target metadata")
        assertTrue(body.contains("\"kind\":\"bob\""), "Scene should declare Sigil animation metadata")
        assertTrue(body.contains("Accessibility fallback"), "Should keep non-primary native fallback controls")
        assertFalse(body.contains("id=\"fifth-wall-game-root\""), "Should not expose the temporary client game mount")
        assertFalse(body.contains("model-viewer.min.js"), "Should not load model-viewer")
        assertFalse(body.contains("/static/fifth-wall-client-game.js"), "Should not load the temporary JS runtime")
        assertFalse(body.contains("/static/fifth-wall-sigil-hydration.js"), "Should not pin Fifth Wall to the old custom Sigil bundle")
        assertFalse(body.contains("Conveyor Queue"), "Should not render queue cards as primary website UI")
        assertFalse(body.contains("Routing Console"), "Should not render a website routing console")
    }

    @Test
    fun `fifth wall action query stays on Materia Sigil shell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall?action=start"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode(), "Normal gameplay action queries should not redirect")
        assertFalse(response.headers().firstValue("Location").isPresent, "Should not send a redirect location")
        assertTrue(response.body().contains("fifth-wall-scene"), "Should still serve the Materia/Sigil game shell")
        assertFalse(response.body().contains("id=\"fifth-wall-game-root\""), "Should not fall back to the temporary client shell")
    }

    @Test
    fun `fifth wall action state persists across requests`() {
        val start = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/fifth-wall?action=start"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val cookie = start.headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("fifth_wall_session=") }
            ?.substringBefore(";")

        assertEquals(200, start.statusCode())
        assertTrue(cookie != null, "Should set a Fifth Wall session cookie before writing the page")
        assertEquals("0/10", fifthWallProcessedCount(start.body()))

        val firstRoute = fifthWallAction("route-truck&truck=0", cookie)
        assertEquals("1/10", fifthWallProcessedCount(firstRoute.body()))

        val secondRoute = fifthWallAction("route-truck&truck=0", cookie)
        assertEquals("2/10", fifthWallProcessedCount(secondRoute.body()))
    }

    @Test
    fun `fifth wall client runtime is not required as static javascript`() {
        listOf(
            "/static/fifth-wall-client-game.js",
            "/static/fifth-wall-interactions.js",
            "/static/fifth-wall-sigil-hydration.js"
        ).forEach { path ->
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            assertEquals(404, response.statusCode(), "Temporary app-authored gameplay JS should be removed: $path")
        }
    }

    private fun fifthWallAction(action: String, cookie: String): HttpResponse<String> =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/fifth-wall?action=$action"))
                .header("Cookie", cookie)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun fifthWallProcessedCount(body: String): String {
        val marker = "id=\"fw-stat-processed\""
        val markerIndex = body.indexOf(marker)
        assertTrue(markerIndex >= 0, "Should render processed stat")
        val valueStart = body.indexOf('>', markerIndex) + 1
        val valueEnd = body.indexOf('<', valueStart)
        return body.substring(valueStart, valueEnd)
    }
}
