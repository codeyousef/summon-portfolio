package code.yousef.portfolio.server

import code.yousef.portfolio.photography.PhotoAsset
import code.yousef.portfolio.photography.PhotoAssetStore
import code.yousef.portfolio.photography.PhotographyAssetBackfillService
import code.yousef.portfolio.photography.sha256Hex
import code.yousef.portfolio.finops.FinOpsDirection
import code.yousef.portfolio.finops.FinOpsEntry
import code.yousef.portfolio.finops.FinOpsRollupBackfillService
import code.yousef.portfolio.finops.FinOpsStatus
import code.yousef.portfolio.finops.InMemoryFinOpsRepository
import code.yousef.portfolio.finops.MoneyAmount
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.web.router
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortfolioEdgeJobRoutesE2ETest {
    private lateinit var server: VertxServer
    private lateinit var baseUrl: String
    private val environment = mutableMapOf(
        "EDGE_ORIGIN_TOKEN" to TOKEN,
        "PORTFOLIO_MIGRATION_EXECUTION_ENABLED" to "false",
        "PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED" to "false",
        "FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED" to "false",
    )
    private val source = MemoryPhotoAssetStore(contentAddressed = false)
    private val target = MemoryPhotoAssetStore(contentAddressed = true)
    private val finOpsSource = InMemoryFinOpsRepository()
    private val finOpsTarget = InMemoryFinOpsRepository()
    private val client = HttpClient.newHttpClient()

    @BeforeTest
    fun startServer() {
        source.assets[SOURCE_KEY] = PhotoAsset(ASSET_BYTES, "image/jpeg")
        val routes = router {
            registerPortfolioEdgeJobRoutes(
                mutationCoordinator = null,
                photographyAssetBackfillService = PhotographyAssetBackfillService(source, target),
                finOpsRollupBackfillService = FinOpsRollupBackfillService(finOpsSource, finOpsTarget),
                photographyMaxAssetBytes = 1024,
                environment = environment::get,
            )
        }
        val port = ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"
        server = VertxServer(VertxServerConfig(port = port), Pipeline().apply { use(routes.asMiddleware()) }) { exchange ->
            exchange.notFound("Route not found")
        }
        runBlocking { server.start() }
    }

    @AfterTest
    fun stopServer() {
        runBlocking { server.stop() }
    }

    @Test
    fun `photography staging requires an exact Bearer credential and its separate gate`() {
        assertEquals(401, post(envelope(), authorization = null).statusCode())
        assertEquals(401, post(envelope(), authorization = TOKEN).statusCode())
        assertEquals(503, post(envelope(), authorization = "Bearer $TOKEN").statusCode())
        assertTrue(target.assets.isEmpty())
    }

    @Test
    fun `enabled photography staging verifies approved inventory and returns a staging receipt`() {
        environment["PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED"] = "true"

        val response = post(envelope(), authorization = "Bearer $TOKEN")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"mirrorState\":\"staged\""))
        assertTrue(response.body().contains("\"sha256\":\"${sha256Hex(ASSET_BYTES)}\""))
        assertTrue(target.assets.keys.single().startsWith("sha256/${sha256Hex(ASSET_BYTES)}/"))
        assertTrue(source.assets.containsKey(SOURCE_KEY))
    }

    @Test
    fun `inventory mismatch fails closed without returning internal details`() {
        environment["PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED"] = "true"

        val response = post(envelope(expectedSize = ASSET_BYTES.size + 1L), authorization = "Bearer $TOKEN")

        assertEquals(503, response.statusCode())
        assertEquals("{\"error\":\"photography staging failed\",\"stage\":\"unclassified\"}", response.body())
        assertTrue(source.assets.containsKey(SOURCE_KEY))
    }

    @Test
    fun `FinOps v2 rollup jobs require their separate gate and return standard mutation receipts`() {
        val dayStart = 1_799_971_200_000L
        val entry = FinOpsEntry(
            id = "entry:rollup-e2e",
            source = "gcp",
            sourceRecordId = "rollup-e2e",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("USD", 1_000_000, 6),
            usdMicros = 1_000_000,
            incurredAt = dayStart + 1_000,
            invoiceMonth = "2027-01",
            project = "portfolio",
            environment = "dev",
            vendor = "google-cloud",
            service = "cloud-run",
            status = FinOpsStatus.FINALIZED,
            reconciliationKey = "gcp:2027-01",
            sourceHash = sha256Hex("rollup-e2e".encodeToByteArray()),
            recordedAt = dayStart + 1_000,
        )
        finOpsSource.putEntry(entry)
        finOpsSource.ensureDailyRollups(entry, emptyList())
        finOpsSource.ensureUnallocatedRollup(entry, emptyList())
        val backfillPayload = Json.parseToJsonElement(
            """{"from":$dayStart,"toExclusive":${dayStart + 86_400_000},"limit":100}""",
        )

        assertEquals(
            503,
            post(
                finOpsEnvelope("portfolio.finops.rollups.v2.backfill", backfillPayload),
                authorization = "Bearer $TOKEN",
                path = "portfolio.finops.rollups.v2.backfill",
            ).statusCode(),
        )
        environment["FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED"] = "true"
        val backfill = post(
            finOpsEnvelope("portfolio.finops.rollups.v2.backfill", backfillPayload),
            authorization = "Bearer $TOKEN",
            path = "portfolio.finops.rollups.v2.backfill",
        )
        assertEquals(200, backfill.statusCode())
        assertTrue(backfill.body().contains("\"mirrorState\":\"mirrored\""))
        val reconcilePayload = Json.parseToJsonElement(
            """{"from":$dayStart,"toExclusive":${dayStart + 86_400_000}}""",
        )
        val reconciliation = post(
            finOpsEnvelope("portfolio.finops.rollups.v2.reconcile", reconcilePayload),
            authorization = "Bearer $TOKEN",
            path = "portfolio.finops.rollups.v2.reconcile",
        )
        assertEquals(200, reconciliation.statusCode())
        assertTrue(reconciliation.body().contains("\"ready\":true"))
        assertTrue(reconciliation.body().contains("\"firestoreCommitTime\":"))
    }

    private fun envelope(expectedSize: Long = ASSET_BYTES.size.toLong()): String {
        val payload = Json.parseToJsonElement(
            """{"assets":[{"photoId":"$PHOTO_ID","sourceStorageKey":"$SOURCE_KEY","contentType":"image/jpeg","expectedSha256":"${sha256Hex(ASSET_BYTES)}","expectedSizeBytes":$expectedSize}]}""",
        )
        val payloadSha = MessageDigest.getInstance("SHA-256")
            .digest(canonicalEdgeJson(payload).encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return """{"id":"photography:stage:test","aggregateType":"portfolio.photography.backfill","aggregateId":"portfolio-dev","expectedRevision":0,"authorityEpoch":1,"occurredAt":"2026-08-23T00:00:00Z","payloadSha256":"$payloadSha","payload":$payload}"""
    }

    private fun finOpsEnvelope(aggregateType: String, payload: kotlinx.serialization.json.JsonElement): String {
        val payloadSha = MessageDigest.getInstance("SHA-256")
            .digest(canonicalEdgeJson(payload).encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return """{"id":"finops-rollup:e2e","aggregateType":"$aggregateType","aggregateId":"portfolio-dev","expectedRevision":0,"authorityEpoch":1,"occurredAt":"2026-08-23T00:00:00Z","payloadSha256":"$payloadSha","payload":$payload}"""
    }

    private fun post(
        body: String,
        authorization: String?,
        path: String = "portfolio.photography.backfill",
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/internal/edge/jobs/$path"))
            .header("content-type", "application/json")
        if (authorization != null) builder.header("authorization", authorization)
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
    }

    private class MemoryPhotoAssetStore(private val contentAddressed: Boolean) : PhotoAssetStore {
        val assets = mutableMapOf<String, PhotoAsset>()

        override fun keyFor(photoId: String, extension: String): String = "$photoId.$extension"

        override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String =
            if (contentAddressed) "sha256/${sha256Hex(bytes)}/$photoId.$extension" else keyFor(photoId, extension)

        override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
            assets[storageKey] = PhotoAsset(bytes.copyOf(), contentType)
        }

        override fun load(storageKey: String, contentType: String): PhotoAsset? = assets[storageKey]

        override fun delete(storageKey: String) {
            assets.remove(storageKey)
        }
    }

    companion object {
        private const val TOKEN = "edge-origin-token-00000000000000000000000000000000"
        private const val PHOTO_ID = "photo-1"
        private const val SOURCE_KEY = "photography/portfolio-dev/photo-1.jpg"
        private val ASSET_BYTES = "approved-photo-bytes".encodeToByteArray()
    }
}
