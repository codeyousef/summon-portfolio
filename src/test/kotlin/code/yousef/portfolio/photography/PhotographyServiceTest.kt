package code.yousef.portfolio.photography

import code.yousef.portfolio.content.LocalContentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotographyServiceTest {

    @Test
    fun `uploads photo metadata and asset`() {
        val contentStore = LocalContentStore()
        val assetStore = FakePhotoAssetStore()
        val service = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)

        val result = service.upload(
            fields = mapOf(
                "title" to "Morning",
                "altText" to "Morning light",
                "caption" to "A first frame",
                "takenAt" to "2026-07-05",
                "order" to "3",
                "published" to "on"
            ),
            file = MultipartFilePart("photo", "morning.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        )

        val success = assertIs<PhotographyService.UploadResult.Success>(result)
        assertEquals("Morning", success.photo.title)
        assertEquals("Morning light", success.photo.altText)
        assertEquals("A first frame", success.photo.caption)
        assertEquals(3, success.photo.order)
        assertTrue(success.photo.published)
        assertEquals(1, contentStore.listPhotographyPhotos().size)
        assertEquals(byteArrayOf(1, 2, 3).toList(), assetStore.assets[success.photo.storageKey]?.bytes?.toList())
    }

    @Test
    fun `rejects unsupported content type`() {
        val service = PhotographyService(LocalContentStore(), FakePhotoAssetStore(), maxUploadBytes = 100)

        val result = service.upload(
            fields = mapOf("title" to "Bad", "altText" to "Bad file"),
            file = MultipartFilePart("photo", "bad.gif", "image/gif", byteArrayOf(1))
        )

        assertIs<PhotographyService.UploadResult.Error>(result)
    }

    @Test
    fun `updates metadata and filters unpublished public photos`() {
        val contentStore = LocalContentStore()
        val assetStore = FakePhotoAssetStore()
        val service = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)
        val upload = assertIs<PhotographyService.UploadResult.Success>(
            service.upload(
                fields = mapOf("title" to "Draft", "altText" to "Draft alt"),
                file = MultipartFilePart("photo", "draft.png", "image/png", byteArrayOf(4))
            )
        )

        assertTrue(service.publicPhotos().isEmpty())
        val update = service.update(
            upload.photo.id,
            mapOf("title" to "Published", "altText" to "Published alt", "published" to "on", "order" to "7")
        )

        assertIs<PhotographyService.UpdateResult.Success>(update)
        assertEquals(listOf("Published"), service.publicPhotos().map { it.title })
        assertEquals(7, service.publicPhotos().first().order)
    }

    @Test
    fun `deletes metadata and asset`() {
        val contentStore = LocalContentStore()
        val assetStore = FakePhotoAssetStore()
        val service = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)
        val upload = assertIs<PhotographyService.UploadResult.Success>(
            service.upload(
                fields = mapOf("title" to "Gone", "altText" to "Gone alt", "published" to "on"),
                file = MultipartFilePart("photo", "gone.webp", "image/webp", byteArrayOf(9))
            )
        )

        val delete = service.delete(upload.photo.id)

        assertIs<PhotographyService.DeleteResult.Success>(delete)
        assertTrue(contentStore.listPhotographyPhotos().isEmpty())
        assertNull(assetStore.assets[upload.photo.storageKey])
    }

    private class FakePhotoAssetStore : PhotoAssetStore {
        val assets = mutableMapOf<String, PhotoAsset>()

        override fun keyFor(photoId: String, extension: String): String = "$photoId.$extension"

        override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
            assets[storageKey] = PhotoAsset(bytes = bytes, contentType = contentType)
        }

        override fun load(storageKey: String, contentType: String): PhotoAsset? = assets[storageKey]

        override fun delete(storageKey: String) {
            assets.remove(storageKey)
        }
    }
}
