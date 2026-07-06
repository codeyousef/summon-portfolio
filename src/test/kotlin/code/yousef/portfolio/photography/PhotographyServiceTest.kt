package code.yousef.portfolio.photography

import code.yousef.portfolio.content.LocalContentStore
import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographySourceKind
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
        assertEquals(PhotographyMediaType.PHOTO, success.photo.mediaType)
        assertEquals(PhotographySourceKind.UPLOAD, success.photo.sourceKind)
        assertEquals("Uncategorized", success.photo.category)
        assertEquals(1, contentStore.listPhotographyPhotos().size)
        assertEquals(byteArrayOf(1, 2, 3).toList(), assetStore.assets[success.photo.storageKey]?.bytes?.toList())
    }

    @Test
    fun `uploads video metadata and asset`() {
        val contentStore = LocalContentStore()
        val assetStore = FakePhotoAssetStore()
        val service = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)

        val result = service.upload(
            fields = mapOf(
                "title" to "City Drift",
                "altText" to "Moving through the city",
                "mediaType" to "VIDEO",
                "sourceKind" to "UPLOAD",
                "category" to "Street",
                "albumTitle" to "Night Walk",
                "featured" to "on",
                "published" to "on"
            ),
            file = MultipartFilePart("photo", "city.mp4", "video/mp4", byteArrayOf(4, 5, 6))
        )

        val success = assertIs<PhotographyService.UploadResult.Success>(result)
        assertEquals(PhotographyMediaType.VIDEO, success.photo.mediaType)
        assertEquals(PhotographySourceKind.UPLOAD, success.photo.sourceKind)
        assertEquals("Street", success.photo.category)
        assertEquals("Night Walk", success.photo.albumTitle)
        assertTrue(success.photo.featured)
        assertEquals("video/mp4", success.photo.contentType)
        assertEquals(byteArrayOf(4, 5, 6).toList(), assetStore.assets[success.photo.storageKey]?.bytes?.toList())
    }

    @Test
    fun `saves external video and external 360 media without an asset`() {
        val contentStore = LocalContentStore()
        val assetStore = FakePhotoAssetStore()
        val service = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)

        val video = service.upload(
            fields = mapOf(
                "title" to "Reference Cut",
                "altText" to "Reference cut",
                "mediaType" to "VIDEO",
                "sourceKind" to "EXTERNAL",
                "category" to "Motion",
                "externalUrl" to "https://youtu.be/abc123",
                "published" to "on"
            ),
            file = null
        )
        val video360 = service.upload(
            fields = mapOf(
                "title" to "Room Scan",
                "altText" to "Room scan",
                "mediaType" to "VIDEO_360",
                "sourceKind" to "EXTERNAL",
                "category" to "360",
                "externalUrl" to "https://vimeo.com/123456",
                "published" to "on"
            ),
            file = null
        )

        val savedVideo = assertIs<PhotographyService.UploadResult.Success>(video).photo
        val saved360 = assertIs<PhotographyService.UploadResult.Success>(video360).photo
        assertEquals(PhotographySourceKind.EXTERNAL, savedVideo.sourceKind)
        assertEquals(PhotographyMediaType.VIDEO_360, saved360.mediaType)
        assertTrue(assetStore.assets.isEmpty())
        assertNull(service.assetForPublishedPhoto(savedVideo.id))
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
    fun `rejects media type and source mismatches`() {
        val service = PhotographyService(LocalContentStore(), FakePhotoAssetStore(), maxUploadBytes = 100)

        val videoAsPhoto = service.upload(
            fields = mapOf("title" to "Bad", "altText" to "Bad file", "mediaType" to "PHOTO"),
            file = MultipartFilePart("photo", "bad.mp4", "video/mp4", byteArrayOf(1))
        )
        val externalWithoutUrl = service.upload(
            fields = mapOf("title" to "Missing URL", "altText" to "Missing URL", "sourceKind" to "EXTERNAL"),
            file = null
        )

        assertIs<PhotographyService.UploadResult.Error>(videoAsPhoto)
        assertIs<PhotographyService.UploadResult.Error>(externalWithoutUrl)
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
