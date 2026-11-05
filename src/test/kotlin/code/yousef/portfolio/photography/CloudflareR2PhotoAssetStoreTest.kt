package code.yousef.portfolio.photography

import code.yousef.config.PhotographyAssetWriteMode
import code.yousef.config.loadAppConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFails

class CloudflareR2PhotoAssetStoreTest {
    @Test
    fun `photography backfill factory is inert unless its separate execution gate is enabled`() {
        val config = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))

        assertNull(PortfolioPhotoAssetStoreFactory.backfillFromEnvironment(config, emptyMap()))
        assertNull(
            PortfolioPhotoAssetStoreFactory.backfillFromEnvironment(
                config,
                mapOf("PORTFOLIO_MIGRATION_EXECUTION_ENABLED" to "true"),
            ),
        )
    }

    @Test
    fun `enabled photography backfill factory fails closed without source and edge configuration`() {
        val config = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))

        assertFails {
            PortfolioPhotoAssetStoreFactory.backfillFromEnvironment(
                config,
                mapOf("PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED" to "true"),
            )
        }
    }

    @Test
    fun `HTTP transport matches the authenticated Worker media contract`() {
        val bytes = "transport-media".encodeToByteArray()
        val digest = sha256Hex(bytes)
        val key = "sha256/$digest/photo-http.webp"
        val methods = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/internal/media/v1/asset") { exchange ->
            methods += exchange.requestMethod
            assertEquals("Bearer $TOKEN", exchange.requestHeaders.getFirst("authorization"))
            assertEquals(key, exchange.requestHeaders.getFirst("x-portfolio-media-key"))
            assertEquals(digest, exchange.requestHeaders.getFirst("x-portfolio-media-sha256"))
            when (exchange.requestMethod) {
                "PUT" -> {
                    assertEquals("*", exchange.requestHeaders.getFirst("if-none-match"))
                    assertContentEquals(bytes, exchange.requestBody.readBytes())
                    exchange.sendResponseHeaders(201, -1)
                }
                "GET" -> {
                    assertEquals("image/webp", exchange.requestHeaders.getFirst("x-portfolio-media-content-type"))
                    exchange.responseHeaders.add("content-type", "image/webp")
                    exchange.responseHeaders.add("x-portfolio-media-sha256", digest)
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                "DELETE" -> exchange.sendResponseHeaders(204, -1)
                else -> exchange.sendResponseHeaders(405, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            val store = CloudflareR2PhotoAssetStore(
                HttpEdgePhotoAssetTransport(
                    endpoint = "http://127.0.0.1:${server.address.port}/internal/media",
                    bearerToken = TOKEN,
                    maxAssetBytes = 1024,
                ),
            )
            store.save(key, "image/webp", bytes)
            assertContentEquals(bytes, store.load(key, "image/webp")?.bytes)
            store.delete(key)
            assertEquals(listOf("PUT", "GET", "DELETE"), methods)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `uses immutable content addressed keys and verifies writes`() {
        val transport = FakeEdgePhotoAssetTransport()
        val store = CloudflareR2PhotoAssetStore(transport)
        val bytes = "portfolio-media".encodeToByteArray()
        val digest = sha256Hex(bytes)

        val key = store.keyFor("photo-123", "JPG", bytes)

        assertEquals("sha256/$digest/photo-123.jpg", key)
        store.save(key, "image/jpeg", bytes)
        assertContentEquals(bytes, transport.assets.getValue(key).bytes)
        assertFailsWith<IllegalArgumentException> {
            store.save("sha256/${"0".repeat(64)}/photo-123.jpg", "image/jpeg", bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            store.save(key, "text/html", bytes)
        }
    }

    @Test
    fun `SHA-256 keys match the cross-runtime lowercase hex contract`() {
        assertEquals(
            "953ed960580e648dd89671316e65f3a64bb8c5b6fe0365b36b1ecf965fc67368",
            sha256Hex("immutable portfolio media".encodeToByteArray()),
        )
    }

    @Test
    fun `dual mode keeps source authoritative and repairs only canonical mirrors`() {
        val source = RecordingPhotoAssetStore()
        val target = RecordingPhotoAssetStore(contentAddressed = true)
        val store = MigratingPhotoAssetStore(source, target, PhotographyAssetWriteMode.DUAL, reverseMirror = false)
        val bytes = "new-media".encodeToByteArray()
        val key = store.keyFor("photo-1", "webp", bytes)

        store.save(key, "image/webp", bytes)
        assertContentEquals(bytes, source.assets.getValue(key).bytes)
        assertContentEquals(bytes, target.assets.getValue(key).bytes)

        source.assets["photography/legacy.jpg"] = PhotoAsset(bytes, "image/jpeg")
        assertContentEquals(bytes, store.load("photography/legacy.jpg", "image/jpeg")?.bytes)
        assertNull(target.assets["photography/legacy.jpg"])

        target.assets.clear()
        assertContentEquals(bytes, store.load(key, "image/webp")?.bytes)
        assertContentEquals(bytes, target.assets.getValue(key).bytes)
    }

    @Test
    fun `target mode reads target and reverse mirrors only when explicitly enabled`() {
        val source = RecordingPhotoAssetStore()
        val target = RecordingPhotoAssetStore(contentAddressed = true)
        val bytes = "cutover-media".encodeToByteArray()
        val store = MigratingPhotoAssetStore(source, target, PhotographyAssetWriteMode.TARGET, reverseMirror = true)
        val key = store.keyFor("photo-2", "png", bytes)

        store.save(key, "image/png", bytes)
        assertContentEquals(bytes, target.assets.getValue(key).bytes)
        assertContentEquals(bytes, source.assets.getValue(key).bytes)

        source.assets[key] = PhotoAsset("stale".encodeToByteArray(), "image/png")
        assertContentEquals(bytes, store.load(key, "image/png")?.bytes)
        store.delete(key)
        assertNull(target.assets[key])
        assertNull(source.assets[key])
    }

    @Test
    fun `GCS mirror keeps canonical R2 keys inside the configured source prefix`() {
        val digest = "a".repeat(64)
        val canonicalKey = "sha256/$digest/photo-3.jpg"

        assertEquals(
            listOf(canonicalKey, "photography/$canonicalKey"),
            gcsCandidateKeys("photography", canonicalKey),
        )
        assertEquals(
            listOf("photography/$canonicalKey"),
            gcsCandidateKeys("photography", "photography/$canonicalKey"),
        )
    }

    private class FakeEdgePhotoAssetTransport : EdgePhotoAssetTransport {
        val assets = mutableMapOf<String, PhotoAsset>()

        override fun put(storageKey: String, digest: String, contentType: String, bytes: ByteArray) {
            assertEquals(digest, sha256Hex(bytes))
            assets[storageKey] = PhotoAsset(bytes, contentType)
        }

        override fun get(storageKey: String, digest: String, expectedContentType: String): PhotoAsset? =
            assets[storageKey]

        override fun delete(storageKey: String, digest: String) {
            assets.remove(storageKey)
        }
    }

    private class RecordingPhotoAssetStore(
        private val contentAddressed: Boolean = false,
    ) : PhotoAssetStore {
        val assets = mutableMapOf<String, PhotoAsset>()

        override fun keyFor(photoId: String, extension: String): String = "$photoId.$extension"

        override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String =
            if (contentAddressed) "sha256/${sha256Hex(bytes)}/$photoId.$extension" else keyFor(photoId, extension)

        override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
            assets[storageKey] = PhotoAsset(bytes, contentType)
        }

        override fun load(storageKey: String, contentType: String): PhotoAsset? = assets[storageKey]

        override fun delete(storageKey: String) {
            assets.remove(storageKey)
        }
    }

    private companion object {
        const val TOKEN = "edge-origin-token-with-at-least-32-characters"
    }
}
