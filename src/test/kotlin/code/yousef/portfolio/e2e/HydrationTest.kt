package code.yousef.portfolio.e2e

import code.yousef.buildApplication
import code.yousef.ApplicationResources
import code.yousef.config.AppConfig
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import kotlinx.coroutines.runBlocking
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class HydrationTest {

    private lateinit var server: VertxServer
    private lateinit var resources: ApplicationResources
    private val testPort = 8082
    private val baseUrl = "http://localhost:$testPort"
    private val httpClient = HttpClient.newHttpClient()

    @BeforeTest
    fun setup() {
        val appConfig = AppConfig(
            projectId = "test-project",
            emulatorHost = "localhost:8080",
            port = testPort,
            useLocalStore = true
        )
        resources = buildApplication(appConfig)
        val config = VertxServerConfig(port = testPort)
        server = VertxServer(config, resources.pipeline) { exchange ->
            exchange.notFound("Route not found")
        }
        runBlocking {
            server.start()
        }
    }

    @AfterTest
    fun teardown() {
        runBlocking {
            server.stop()
            resources.onShutdown()
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
    fun `should serve Sigil default font asset`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/sigil-default-font.json"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"glyphs\""), "Should serve the bundled Sigil mesh-text font")
    }

    @Test
    fun `retired Fifth Wall browser assets are not served`() {
        listOf(
            "/static/fifth-wall-renderer.js",
            "/static/fifth-wall-scene-refresh.js",
            "/static/fifth-wall-fps.js",
            "/static/fifth-wall-pointer-guard.js",
            "/static/fifth-wall-telemetry.js",
            "/static/fifth-wall.css"
        ).forEach { path ->
            val response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create("$baseUrl$path")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(404, response.statusCode(), "Fifth Wall should not require app-authored browser asset $path")
        }
    }

    @Test
    fun `fifth wall serves Materia Sigil scene shell`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall?action=start"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())

        val body = response.body()
        assertTrue(body.contains("fifth-wall-scene"), "Should expose the Materia/Sigil scene mount")
        assertTrue(body.contains("warehouse-back-wall"), "Should render native Sigil warehouse geometry")
        assertTrue(body.contains("conveyor-belt"), "Should render native Sigil conveyor geometry")
        assertTrue(body.contains("optimized/delivery-truck.glb"), "Should render the textured truck LOD")
        assertTrue(
            body.contains("id=\"fifth-wall-scene-container\" style=\"width: 100%; height: calc(100vh - 80px);"),
            "The Sigil canvas should retain a definite responsive height through its SSR host wrapper"
        )
        assertTrue(body.contains("/sigil-hydration.js?v=0.4.3.1"), "Should load the released Sigil 0.4.3.1 runtime")
        assertTrue(body.contains("\"rendererPreference\":\"webgl\""), "Renderer preference should be declared through Sigil")
        assertTrue(body.contains("\"adaptiveResolution\""), "Scene should declare adaptive render resolution")
        assertTrue(body.contains("\"targetFps\":60.0"), "Adaptive resolution should target 60 FPS")
        assertTrue(body.contains("\"minimumDpr\":0.75"), "Adaptive resolution should retain its 0.75 DPR floor")
        assertTrue(body.contains("\"maximumDpr\":1.0"), "Adaptive resolution should avoid an expensive high-DPR warmup")
        assertTrue(body.contains("\"sampleWindow\":20"), "Adaptive resolution should react within a short warmup window")
        assertTrue(body.contains("\"type\":\"screenLayer\""), "Visible interface should use Sigil screen layers")
        assertTrue(body.contains("\"type\":\"frameStatsText\""), "FPS should use Sigil frame statistics text")
        assertTrue(body.contains("\"interactionId\":\"focus-package-0\""), "Stable package slots should expose focus actions")
        assertTrue(body.contains("\"interactionId\":\"route-truck-0\""), "Stable truck slots should expose route actions")
        assertTrue(body.contains("\"interactionId\":\"route-return-bin\""), "Return bin should expose a route action")
        assertTrue(body.contains("\"interactionId\":\"camera-overview\""), "Camera presets should be canvas controls")
        assertTrue(body.contains("\"events\":[\"click\"]"), "Packages should use click-to-focus interactions")
        assertSceneClickHandlerReload(body, interactionIdPrefix = "focus-package-", reloadOnSuccess = false)
        assertSceneClickHandlerReload(body, interactionIdPrefix = "route-truck-", reloadOnSuccess = false)
        assertSceneClickHandlerReload(body, interactionIdPrefix = "route-return-bin", reloadOnSuccess = false)
        assertSceneClickHandlerReload(body, interactionIdPrefix = "prompt-choice-", reloadOnSuccess = false)
        assertFalse(body.contains("\"reloadOnSuccess\":true"), "Fifth Wall actions should never request a page reload")
        assertFalse(body.contains("scene-refresh"), "All gameplay and bay transitions should use scene patches")
        assertEquals(3, Regex("package-slot-[0-2]-model").findAll(body).map { it.value }.toSet().size)
        assertEquals(4, Regex("truck-slot-[0-3]-model").findAll(body).map { it.value }.toSet().size)
        assertFalse(body.contains("\"drag\":{\"enabled\":true"), "Packages should not steal camera drags")
        assertTrue(body.contains("\"type\":\"text\""), "Scene should serialize in-canvas Sigil text labels")
        assertTrue(body.contains("\"facingMode\":\"BILLBOARD\""), "World labels should remain parented billboards")
        assertTrue(
            body.contains("\"fontUrl\":\"/static/fifth-wall-control-font.json\""),
            "Fifth Wall should load its readable vector font through Sigil"
        )
        listOf("PACKAGE MANIFEST", "RULES", "FOCUS PACKAGE 1", "INSPECT", "TRUCK A", "RETURN BIN", "RESET", "SOUND: LOW").forEach { label ->
            assertTrue(body.contains(label), "Canvas interface should contain $label")
        }
        assertFalse(body.contains("fw-scene-dashboard"), "Should not render a DOM dashboard overlay")
        assertFalse(body.contains("fw-scene-hud"), "Should not render a DOM guidance overlay")
        assertFalse(body.contains("fw-modal"), "Should not render DOM prompt modals")
        assertFalse(body.contains("fw-stat-processed"), "Processed stat should not be a DOM overlay")
        assertFalse(body.contains("id=\"fifth-wall-game-root\""), "Should not expose the temporary client game mount")
        assertFalse(body.contains("model-viewer.min.js"), "Should not load model-viewer")
        assertFalse(body.contains("/static/fifth-wall-client-game.js"), "Should not load the temporary JS runtime")
        assertFalse(body.contains("/static/fifth-wall-sigil-hydration.js"), "Should not pin Fifth Wall to the old custom Sigil bundle")
        assertFalse(body.contains("/static/fifth-wall.css"), "Should not load game-authored CSS")
        assertFalse(body.contains("/static/fifth-wall-renderer.js"), "Should not load game-authored renderer JS")
        assertFalse(body.contains("Conveyor Queue"), "Should not render queue cards as primary website UI")
        assertFalse(body.contains("Routing Console"), "Should not render a website routing console")
    }

    @Test
    fun `fifth wall SSR handles concurrent renders`() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..24).map {
                executor.submit<HttpResponse<String>> {
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/fifth-wall?action=start"))
                        .GET()
                        .build()
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }

            val responses = futures.map { it.get(20, TimeUnit.SECONDS) }

            responses.forEach { response ->
                assertEquals(200, response.statusCode())
                assertTrue(response.body().contains("fifth-wall-scene"), "Should render the game shell")
                assertFalse(
                    response.body().contains("ConcurrentModificationException"),
                    "Concurrent SSR should not corrupt Summon hydration attributes"
                )
                assertFalse(response.body().contains("Internal Server Error"), "Concurrent SSR should not fail")
            }
        } finally {
            executor.shutdownNow()
        }
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
        val body = response.body()
        assertTrue(body.contains("fifth-wall-scene"), "Should still serve the Materia/Sigil game shell")
        assertFalse(body.contains("id=\"fifth-wall-game-root\""), "Should not fall back to the temporary client shell")
        listOf(
            "cube-crate.glb",
            "cylinder-drum.glb",
            "rectangular-parcel.glb",
            "sphere-package-with-cradle.glb"
        ).forEach { asset ->
            assertTrue(body.contains(asset), "Package meshes should use the textured package model asset: $asset")
        }
        assertTrue(
            body.contains("/static/models/fifth-wall/optimized/"),
            "The game should use the performance-optimized GLB set"
        )
        listOf(
            "valid-geometry-insert-set.glb",
            "penrose-loop.glb",
            "escher-stair.glb",
            "impossible-trident.glb"
        ).forEach { asset ->
            assertFalse(body.contains(asset), "Geometry overlays should remain lightweight instead of repeated GLB asset: $asset")
        }
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
            "/static/fifth-wall-sigil-hydration.js",
            "/static/fifth-wall-renderer.js",
            "/static/fifth-wall-scene-refresh.js",
            "/static/fifth-wall-fps.js",
            "/static/fifth-wall-pointer-guard.js",
            "/static/fifth-wall-telemetry.js"
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
        val marker = "\"id\":\"desktop-info-footer\""
        val markerIndex = body.indexOf(marker)
        assertTrue(markerIndex >= 0, "Should render processed stat as in-canvas text")
        val window = body.substring(markerIndex, (markerIndex + 1_400).coerceAtMost(body.length))
        val match = Regex("PROCESSED ([0-9]+/[0-9]+)").find(window)
        assertTrue(match != null, "Should serialize processed stat text")
        return match.groupValues[1]
    }

    private fun assertSceneClickHandlerReload(
        body: String,
        interactionIdPrefix: String,
        reloadOnSuccess: Boolean
    ) {
        val actionsJson = Regex(
            """<script type="application/json" id="fifth-wall-scene-actions">(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(body)?.groupValues?.get(1)
        assertTrue(actionsJson != null, "Should serialize Fifth Wall scene action handlers")

        val interactionIndex = actionsJson.indexOf("\"interactionId\":\"$interactionIdPrefix")
        val handlerWindow = if (interactionIndex >= 0) {
            actionsJson.substring(interactionIndex, (interactionIndex + 12_000).coerceAtMost(actionsJson.length))
        } else {
            ""
        }
        assertTrue(
            interactionIndex >= 0 && handlerWindow.contains("\"reloadOnSuccess\":$reloadOnSuccess"),
            "Click handler for $interactionIdPrefix should serialize reloadOnSuccess=$reloadOnSuccess"
        )
    }
}
