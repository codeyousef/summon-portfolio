package code.yousef.portfolio.photography

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotographyAssetBackfillServiceTest {
    @Test
    fun `stage preserves legacy source and verifies content-addressed source and target copies`() {
        val bytes = "photo bytes".encodeToByteArray()
        val source = FakeStore().apply { assets[LEGACY_KEY] = PhotoAsset(bytes, "image/jpeg") }
        val target = FakeStore(contentAddressed = true)
        val service = PhotographyAssetBackfillService(source, target)

        val receipt = service.stage(request())

        val expectedDigest = sha256Hex(bytes)
        val expectedKey = "sha256/$expectedDigest/$PHOTO_ID.jpg"
        assertEquals(expectedKey, receipt.contentAddressedStorageKey)
        assertEquals(expectedDigest, receipt.sha256)
        assertEquals(bytes.size.toLong(), receipt.sizeBytes)
        assertContentEquals(bytes, assertNotNull(source.assets[LEGACY_KEY]).bytes)
        assertContentEquals(bytes, assertNotNull(source.assets["photography/portfolio-dev/$expectedKey"]).bytes)
        assertContentEquals(bytes, assertNotNull(target.assets[expectedKey]).bytes)
    }

    @Test
    fun `identical replay returns the same receipt without removing the legacy key`() {
        val source = FakeStore().apply { assets[LEGACY_KEY] = PhotoAsset(BYTES, "image/jpeg") }
        val target = FakeStore(contentAddressed = true)
        val service = PhotographyAssetBackfillService(source, target)

        val first = service.stage(request())
        val replay = service.stage(request())

        assertEquals(first, replay)
        assertTrue(LEGACY_KEY in source.assets)
        assertEquals(2, target.saveAttempts)
        assertEquals(1, target.assets.size)
    }

    @Test
    fun `missing source and corrupted verification fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            PhotographyAssetBackfillService(FakeStore(), FakeStore(contentAddressed = true)).stage(request())
        }

        val source = FakeStore().apply { assets[LEGACY_KEY] = PhotoAsset(BYTES, "image/jpeg") }
        val corruptTarget = FakeStore(contentAddressed = true, corruptReads = true)
        val error = assertFailsWith<PhotographyAssetBackfillException> {
            PhotographyAssetBackfillService(source, corruptTarget).stage(request())
        }
        assertEquals("target-verify", error.stageCode)
        assertTrue(LEGACY_KEY in source.assets)
    }

    @Test
    fun `source content type must agree with Firestore metadata`() {
        val source = FakeStore().apply { assets[LEGACY_KEY] = PhotoAsset(BYTES, "image/png") }

        assertFailsWith<IllegalArgumentException> {
            PhotographyAssetBackfillService(source, FakeStore(contentAddressed = true)).stage(request())
        }
    }

    private class FakeStore(
        private val contentAddressed: Boolean = false,
        private val corruptReads: Boolean = false,
    ) : PhotoAssetStore {
        val assets = linkedMapOf<String, PhotoAsset>()
        var saveAttempts = 0

        override fun keyFor(photoId: String, extension: String): String = "$photoId.$extension"

        override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String = if (contentAddressed) {
            "sha256/${sha256Hex(bytes)}/$photoId.${extension.lowercase()}"
        } else {
            keyFor(photoId, extension)
        }

        override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
            saveAttempts += 1
            val previous = assets[storageKey]
            require(previous == null || (previous.contentType == contentType && previous.bytes.contentEquals(bytes))) {
                "conflicting immutable copy"
            }
            assets[storageKey] = PhotoAsset(bytes.copyOf(), contentType)
        }

        override fun load(storageKey: String, contentType: String): PhotoAsset? = assets[storageKey]?.let { asset ->
            if (corruptReads && parseContentAddressedPhotoKey(storageKey) != null) {
                PhotoAsset(asset.bytes + 0, asset.contentType)
            } else {
                PhotoAsset(asset.bytes.copyOf(), asset.contentType)
            }
        }

        override fun delete(storageKey: String) {
            assets.remove(storageKey)
        }
    }

    private fun request() = PhotographyAssetBackfillRequest(PHOTO_ID, LEGACY_KEY, "image/jpeg")

    private companion object {
        const val PHOTO_ID = "e3db9c6b-eb07-46aa-bded-e4354874bb96"
        const val LEGACY_KEY = "photography/portfolio-dev/$PHOTO_ID.jpg"
        val BYTES = "photo bytes".encodeToByteArray()
    }
}
