package code.yousef.aws.s3

import code.yousef.portfolio.finops.receiptSha256Hex
import code.yousef.portfolio.photography.sha256Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class S3StoresTest {
    private val s3 = FakeS3Client()

    @Test
    fun `photo assets keep the R2 content-addressed key layout in the media bucket`() {
        val store = S3PhotoAssetStore(s3, "portfolio-media", prefix = null, maxAssetBytes = 1_000)
        val bytes = "jpeg-bytes".toByteArray()
        val key = store.keyFor("photo-1", "JPG", bytes)
        assertEquals("sha256/${sha256Hex(bytes)}/photo-1.jpg", key)

        store.save(key, "image/jpeg", bytes)
        // Public URL /cdn/media/<key> maps to object key <key> with the prefix stripped.
        val stored = s3.objects.getValue("portfolio-media" to key)
        assertEquals("image/jpeg", stored.contentType)
        assertEquals("public, max-age=31536000, immutable", stored.cacheControl)
        assertEquals(sha256Hex(bytes), stored.metadata["sha256"])
        assertEquals("*", s3.putRequests.last().ifNoneMatch())

        // Idempotent re-save of an immutable object succeeds without replacing it.
        store.save(key, "image/jpeg", bytes)
        val loaded = requireNotNull(store.load(key, "image/jpeg"))
        assertContentEquals(bytes, loaded.bytes)
        assertNull(store.load("sha256/${"0".repeat(64)}/missing.jpg", "image/jpeg"))

        assertFailsWith<IllegalArgumentException> { store.save(key, "image/jpeg", "other".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { store.save("photography/legacy.jpg", "image/jpeg", bytes) }
        assertFailsWith<IllegalArgumentException> { store.load(key, "image/png") }
        store.delete(key)
        assertNull(store.load(key, "image/jpeg"))
    }

    @Test
    fun `media prefix is applied to object keys only`() {
        val store = S3PhotoAssetStore(s3, "portfolio-media", prefix = "/cdn/media/", maxAssetBytes = 1_000)
        val bytes = byteArrayOf(1, 2, 3)
        val key = store.keyFor("p", "png", bytes)
        store.save(key, "image/png", bytes)
        assertTrue(s3.objects.containsKey("portfolio-media" to "cdn/media/$key"))
        assertFailsWith<IllegalArgumentException> { S3PhotoAssetStore(s3, "b-1", prefix = "../x", maxAssetBytes = 1) }
    }

    @Test
    fun `receipts are immutable private objects verified on read`() {
        val store = S3FinOpsReceiptStore(s3, "portfolio-receipts", prefix = "receipts", maxReceiptBytes = 1_000)
        val bytes = "%PDF-1.7 receipt".toByteArray()
        val uploadId = "0123456789abcdef0123456789abcdef"
        val reference = store.save("invoice.pdf", "application/pdf", bytes, uploadId)
        assertEquals("sha256/${receiptSha256Hex(bytes)}/uploads/$uploadId/invoice.pdf", reference.storageKey)
        val stored = s3.objects.getValue("portfolio-receipts" to "receipts/${reference.storageKey}")
        assertEquals("private, no-store", stored.cacheControl)
        assertEquals("attachment; filename=\"invoice.pdf\"", stored.contentDisposition)
        assertEquals("pending", stored.metadata["lifecycle"])
        assertEquals(uploadId, stored.metadata["uploadid"])

        assertEquals(reference, store.save("invoice.pdf", "application/pdf", bytes, uploadId))
        store.requirePresent(reference)
        assertContentEquals(bytes, store.load(reference))
        assertFailsWith<IllegalArgumentException> { store.requirePresent(reference.copy(contentType = "image/png")) }
        assertFailsWith<NoSuchElementException> {
            store.load(reference.copy(storageKey = "sha256/${reference.sha256}/uploads/${"f".repeat(32)}/invoice.pdf", uploadId = "f".repeat(32)))
        }
        assertFailsWith<IllegalArgumentException> { store.save("../x.pdf", "application/pdf", bytes) }
        assertFailsWith<IllegalArgumentException> { store.save("x.exe", "application/x-msdownload", bytes) }
    }

    @Test
    fun `CDN proxy keys reject traversal and empty segments`() {
        assertEquals("sha256/${"a".repeat(64)}/p.jpg", S3CdnProxy.cdnObjectKey("sha256/${"a".repeat(64)}/p.jpg"))
        listOf("", "/abs", "a//b", "a/../b", "..", "./a", "a/", "a\\b", "a%2Fb", "a b").forEach { raw ->
            assertNull(S3CdnProxy.cdnObjectKey(raw), raw)
        }
    }
}
