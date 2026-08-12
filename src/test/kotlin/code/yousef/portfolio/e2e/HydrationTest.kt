package code.yousef.portfolio.e2e

import code.yousef.buildApplication
import code.yousef.ApplicationResources
import code.yousef.config.AppConfig
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(
            1,
            Regex("<title(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).findAll(body).count(),
            "SSR documents should contain exactly one title",
        )
        assertFalse(body.contains("<title>Summon App</title>"), "The route title should replace Summon's fallback")
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
        assertTrue(body.contains("/sigil-hydration.js?v=0.4.3.2"), "Should load the released Sigil 0.4.3.2 runtime")
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
    fun `fifth wall serializes stable package drag and drop bindings`() {
        val body = fifthWallPage()
        val nodes = fifthWallSceneNodes(body)

        repeat(3) { index ->
            val interaction = nodes.singleNode("package-slot-$index").objectValue("interaction")
            assertEquals("focus-package-$index", interaction.stringValue("interactionId"))
            assertEquals(listOf("activate", "package"), interaction.stringList("actions"))
            assertEquals(
                listOf("click", "dragstart", "drag", "dragend"),
                interaction.stringList("events"),
                "Package $index should keep click focus while opting into pointer drag events"
            )
            val drag = interaction.objectValue("drag")
            assertEquals(true, drag.booleanValue("enabled"))
            assertEquals("horizontal", drag.stringValue("mode"))
            assertEquals(listOf("fifth-wall-routing-target"), drag.stringList("dropGroups"))
        }

        (0..3).forEach { index ->
            assertRoutingDropTarget(
                interaction = nodes.singleNode("truck-slot-$index").objectValue("interaction"),
                targetId = "route-truck-$index"
            )
        }
        assertRoutingDropTarget(
            interaction = nodes.singleNode("return-bin").objectValue("interaction"),
            targetId = "route-return-bin"
        )

        val actions = fifthWallSceneActions(body)
        val dropActions = actions.filter { it.objectValue("match").stringValue("type") == "drop" }
        assertEquals(15, dropActions.size, "Every package slot should bind to four trucks and the return bin")
        repeat(3) { sourceIndex ->
            val targets = (0..3).map { "route-truck-$it" } + "route-return-bin"
            targets.forEach { targetId ->
                val action = dropActions.single { action ->
                    val match = action.objectValue("match")
                    match.stringValue("sourceInteractionId") == "focus-package-$sourceIndex" &&
                        match.stringValue("targetInteractionId") == targetId
                }
                val match = action.objectValue("match")
                assertEquals(true, match.booleanValue("accepted"))
                assertEquals("fifth-wall:route", action.stringValue("requestKey"))
                assertEquals(true, action.booleanValue("suppressWhilePending"))
                assertEquals(false, action.booleanValue("reloadOnSuccess"))
                assertCanonicalPackagePosition(action.optimisticNode("package-slot-$sourceIndex"), sourceIndex)
            }
        }

        val snapActions = actions.filter { it.objectValue("match").stringValue("type") == "dragend" }
        assertEquals(3, snapActions.size, "Each stable package slot should locally snap back after drag completion")
        repeat(3) { sourceIndex ->
            val action = snapActions.single {
                it.objectValue("match").stringValue("sourceInteractionId") == "focus-package-$sourceIndex"
            }
            assertEquals("fifth-wall:drag-snap-$sourceIndex", action.stringValue("requestKey"))
            assertEquals(false, action.booleanValue("suppressWhilePending"))
            assertEquals(false, action.booleanValue("reloadOnSuccess"))
            listOf("callbackId", "callbackUrl", "localHandlerId", "url").forEach { key ->
                assertTrue(
                    action[key] == null || action[key] == JsonNull,
                    "Local drag snap binding should not serialize $key"
                )
            }
            assertCanonicalPackagePosition(action.optimisticNode("package-slot-$sourceIndex"), sourceIndex)
        }
    }

    @Test
    fun `fifth wall serializes shape aware color wraps and redundant labels`() {
        val body = fifthWallPage()
        val nodes = fifthWallSceneNodes(body)
        val colors = listOf("red", "blue", "green", "yellow", "gray", "purple")

        repeat(3) { index ->
            colors.forEach { color ->
                nodes.singleNode("package-slot-$index-color-$color")
                listOf("front", "vertical", "top").forEach { face ->
                    nodes.singleNode("package-slot-$index-color-wrap-$face-$color")
                }
            }

            val label = nodes.singleNode("package-slot-$index-world-label").stringValue("text")
            val labelMatch = Regex("P${index + 1} • (RED|BLUE|GREEN|YELLOW|GRAY|PURPLE)").matchEntire(label)
            assertTrue(labelMatch != null, "Package $index should spell out its logical color in-world")
            val activeColor = labelMatch.groupValues[1].lowercase()
            assertEquals(true, nodes.singleNode("package-slot-$index-color-$activeColor").booleanValue("visible"))

            val selectionColor = nodes.singleNode("package-slot-$index-selection").stringValue("materialColor")
            val wrapColor = nodes.singleNode("package-slot-$index-color-wrap-front-$activeColor").stringValue("materialColor")
            assertFalse(
                selectionColor == wrapColor,
                "Cyan focus feedback should remain distinct from the package's logical color wrap"
            )
        }
    }

    @Test
    fun `fifth wall color wraps stay outside every authored package shape`() {
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
        assertTrue(cookie != null, "Should retain the package geometry session")
        assertPackageWrapGeometry(
            body = start.body(),
            expected = listOf(CUBE_WRAP, RECT_WRAP, CYLINDER_WRAP)
        )

        val shifted = fifthWallAction("route-truck&truck=0", cookie)

        assertEquals(200, shifted.statusCode())
        assertPackageWrapGeometry(
            body = shifted.body(),
            expected = listOf(RECT_WRAP, CYLINDER_WRAP, SPHERE_WRAP)
        )
    }

    @Test
    fun `fifth wall drop callback routes a nonfocused package and returns canonical geometry`() {
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
        assertTrue(cookie != null, "Should retain the routed game session")
        assertEquals("0/10", fifthWallProcessedCount(start.body()))
        val dropAction = fifthWallSceneActions(start.body()).single { action ->
            val match = action.objectValue("match")
            match.stringValue("type") == "drop" &&
                match.stringValue("sourceInteractionId") == "focus-package-1" &&
                match.stringValue("targetInteractionId") == "route-truck-0" &&
                match.booleanValue("accepted")
        }
        val callbackId = dropAction.stringValue("callbackId")

        val firstDrop = fifthWallCallback(callbackId, cookie)

        assertEquals(200, firstDrop.statusCode())
        val callbackResponse = Json.parseToJsonElement(firstDrop.body()).jsonObject
        assertEquals("patch", callbackResponse.stringValue("action"))
        assertEquals("ok", callbackResponse.stringValue("status"))
        val responseNode = callbackResponse.objectValue("scenePatch")
            .getValue("nodes")
            .jsonArray
            .map { it.jsonObject }
            .single { it.stringValue("id") == "package-slot-1" }
        assertCanonicalPackagePosition(responseNode, index = 1)
        assertEquals("1/10", fifthWallProcessedCount(fifthWallPage(cookie, startShift = false)))
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

    private fun fifthWallPage(cookie: String? = null, startShift: Boolean = true): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/fifth-wall${if (startShift) "?action=start" else ""}"))
            .GET()
        cookie?.let { request.header("Cookie", it) }
        val response = httpClient.send(
            request.build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, response.statusCode())
        return response.body()
    }

    private fun fifthWallCallback(callbackId: String, cookie: String): HttpResponse<String> =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/sigil/callback/$callbackId"))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun fifthWallSceneNodes(body: String): List<JsonObject> {
        val scene = Json.parseToJsonElement(fifthWallScriptJson(body, "fifth-wall-scene-data")).jsonObject
        val nodes = mutableListOf<JsonObject>()

        fun collect(element: JsonElement) {
            val node = element.jsonObject
            nodes += node
            node["children"]?.jsonArray?.forEach(::collect)
        }

        scene.getValue("rootNodes").jsonArray.forEach(::collect)
        return nodes
    }

    private fun fifthWallSceneActions(body: String): List<JsonObject> =
        Json.parseToJsonElement(fifthWallScriptJson(body, "fifth-wall-scene-actions"))
            .jsonArray
            .map { it.jsonObject }

    private fun fifthWallScriptJson(body: String, id: String): String {
        val json = Regex(
            """<script type="application/json" id="$id">(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(body)?.groupValues?.get(1)
        assertTrue(json != null, "Should serialize $id")
        return json
    }

    private fun assertRoutingDropTarget(interaction: JsonObject, targetId: String) {
        assertEquals(targetId, interaction.stringValue("interactionId"))
        assertTrue(interaction.stringList("events").containsAll(listOf("click", "dragenter", "dragleave", "drop")))
        val dropTarget = interaction.objectValue("dropTarget")
        assertEquals(true, dropTarget.booleanValue("enabled"))
        assertEquals(targetId, dropTarget.stringValue("targetId"))
        assertEquals(listOf("fifth-wall-routing-target"), dropTarget.stringList("groups"))
        assertEquals(listOf("package"), dropTarget.stringList("accepts"))
        assertEquals(setOf("hover", "active", "valid", "invalid"), dropTarget.objectValue("states").keys)
    }

    private fun assertPackageWrapGeometry(body: String, expected: List<PackageWrapExpectation>) {
        val nodes = fifthWallSceneNodes(body)
        val colors = listOf("red", "blue", "green", "yellow", "gray", "purple")

        expected.forEachIndexed { index, wrap ->
            val modelUrl = nodes.singleNode("package-slot-$index-model").stringValue("url")
            assertTrue(modelUrl.contains(wrap.modelFile), "Package $index should render ${wrap.modelFile}")

            val expectedCenterY = wrap.baseY + wrap.height / 2.0
            val expectedFrontPosition = listOf(0.0, expectedCenterY, wrap.frontZ + 0.095)
            val expectedFrontSize = listOf(
                wrap.width * wrap.frontWidthRatio,
                wrap.height * wrap.frontHeightRatio,
                0.08
            )
            val expectedVerticalPosition = listOf(0.0, expectedCenterY, wrap.frontZ + 0.1)
            val expectedVerticalSize = listOf(
                wrap.width * wrap.verticalWidthRatio,
                wrap.height * wrap.verticalHeightRatio,
                0.09
            )
            val expectedTopPosition = listOf(0.0, wrap.baseY + wrap.height + 0.095, 0.0)
            val expectedTopSize = listOf(
                wrap.width * wrap.topWidthRatio,
                0.08,
                wrap.depth * wrap.topDepthRatio
            )

            colors.forEach { color ->
                val prefix = "package-slot-$index-color-wrap"
                val front = nodes.singleNode("$prefix-front-$color")
                val vertical = nodes.singleNode("$prefix-vertical-$color")
                val top = nodes.singleNode("$prefix-top-$color")

                assertVectorEquals(expectedFrontPosition, front.doubleList("position"), "$prefix-front-$color position")
                assertVectorEquals(expectedFrontSize, front.doubleList("scale"), "$prefix-front-$color dimensions")
                assertVectorEquals(
                    expectedVerticalPosition,
                    vertical.doubleList("position"),
                    "$prefix-vertical-$color position"
                )
                assertVectorEquals(
                    expectedVerticalSize,
                    vertical.doubleList("scale"),
                    "$prefix-vertical-$color dimensions"
                )
                assertVectorEquals(expectedTopPosition, top.doubleList("position"), "$prefix-top-$color position")
                assertVectorEquals(expectedTopSize, top.doubleList("scale"), "$prefix-top-$color dimensions")

                val frontBackFace = front.doubleList("position")[2] - front.doubleList("scale")[2] / 2.0
                val verticalBackFace = vertical.doubleList("position")[2] - vertical.doubleList("scale")[2] / 2.0
                val topBottomFace = top.doubleList("position")[1] - top.doubleList("scale")[1] / 2.0
                val modelFront = wrap.frontZ
                val modelTop = wrap.baseY + wrap.height

                assertTrue(frontBackFace > modelFront, "$prefix-front-$color should clear the model front")
                assertTrue(verticalBackFace > modelFront, "$prefix-vertical-$color should clear the model front")
                assertTrue(topBottomFace > modelTop, "$prefix-top-$color should clear the model top")
                assertEquals(0.055, frontBackFace - modelFront, 0.0001, "Front wrap clearance should remain stable")
                assertEquals(0.055, verticalBackFace - modelFront, 0.0001, "Vertical wrap clearance should remain stable")
                assertEquals(0.055, topBottomFace - modelTop, 0.0001, "Top wrap clearance should remain stable")
            }
        }
    }

    private fun assertVectorEquals(expected: List<Double>, actual: List<Double>, label: String) {
        assertEquals(expected.size, actual.size, "$label component count")
        expected.indices.forEach { component ->
            assertEquals(expected[component], actual[component], 0.0001, "$label component $component")
        }
    }

    private fun assertCanonicalPackagePosition(node: JsonObject, index: Int) {
        val position = node.getValue("position").jsonArray.map { it.jsonPrimitive.content.toDouble() }
        assertEquals(3, position.size)
        assertEquals(-5.4 + index * 3.05, position[0], 0.0001, "Package $index should snap to its canonical x position")
        assertEquals(0.0, position[1], 0.0001, "Package $index should snap to the conveyor height")
        assertEquals(-0.5, position[2], 0.0001, "Package $index should snap to the canonical conveyor lane")
    }

    private fun JsonObject.optimisticNode(id: String): JsonObject =
        objectValue("optimisticPatch")
            .getValue("nodes")
            .jsonArray
            .map { it.jsonObject }
            .single { it.stringValue("id") == id }

    private fun List<JsonObject>.singleNode(id: String): JsonObject =
        single { it.stringValue("id") == id }

    private fun JsonObject.objectValue(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.stringValue(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.booleanValue(key: String): Boolean = stringValue(key).toBoolean()

    private fun JsonObject.stringList(key: String): List<String> =
        getValue(key).jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.doubleList(key: String): List<Double> =
        getValue(key).jsonArray.map { it.jsonPrimitive.content.toDouble() }

    private data class PackageWrapExpectation(
        val modelFile: String,
        val baseY: Double,
        val width: Double,
        val height: Double,
        val depth: Double,
        val frontZ: Double,
        val frontWidthRatio: Double,
        val frontHeightRatio: Double,
        val verticalWidthRatio: Double,
        val verticalHeightRatio: Double,
        val topWidthRatio: Double,
        val topDepthRatio: Double
    )

    private companion object {
        val CUBE_WRAP = PackageWrapExpectation(
            "cube-crate.glb", 0.84, 1.357343757, 1.113164063, 1.224531216, 0.612265608,
            0.82, 0.27, 0.22, 0.74, 0.22, 0.86
        )
        val RECT_WRAP = PackageWrapExpectation(
            "rectangular-parcel.glb", 0.84, 2.075937510, 0.774414063, 1.077890630, 0.538945315,
            0.82, 0.34, 0.16, 0.72, 0.16, 0.84
        )
        val CYLINDER_WRAP = PackageWrapExpectation(
            "cylinder-drum.glb", 0.84, 1.166512703, 1.086406250, 1.351626454, 0.680616993,
            0.66, 0.28, 0.25, 0.72, 0.26, 0.68
        )
        val SPHERE_WRAP = PackageWrapExpectation(
            "sphere-package-with-cradle.glb", 0.9, 1.481062319, 0.942968750, 1.483240093, 0.742556690,
            0.5, 0.32, 0.2, 0.62, 0.3, 0.3
        )
    }

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
