package code.yousef.portfolio.photography

import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.PhotographySourceKind
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PhotographyService(
    private val contentStore: ContentStore,
    private val assetStore: PhotoAssetStore,
    private val maxUploadBytes: Long
) {
    private val log = LoggerFactory.getLogger(PhotographyService::class.java)

    fun publicPhotos(): List<PhotographyPhoto> =
        contentStore.listPhotographyPhotos()
            .filter { it.published }
            .sortedWith(compareBy<PhotographyPhoto> { it.order }.thenByDescending { it.uploadedAt })

    fun adminPhotos(): List<PhotographyPhoto> =
        contentStore.listPhotographyPhotos()
            .sortedWith(compareBy<PhotographyPhoto> { it.order }.thenByDescending { it.uploadedAt })

    fun assetForPublishedPhoto(id: String): PhotoAsset? {
        val photo = contentStore.listPhotographyPhotos().firstOrNull { photo ->
            photo.published && (photo.id == id || photo.storageKey.assetFileName() == id || photo.storageKey == id)
        } ?: return null
        if (photo.sourceKind != PhotographySourceKind.UPLOAD || photo.storageKey.isBlank()) return null
        return assetStore.load(photo.storageKey, photo.contentType)
    }

    fun upload(fields: Map<String, String>, file: MultipartFilePart?): UploadResult {
        val metadata = parseMetadata(fields, requireTitle = true, requireAltText = true)
            ?: return UploadResult.Error("Title and alt text are required.")
        val id = UUID.randomUUID().toString()

        if (metadata.sourceKind == PhotographySourceKind.EXTERNAL) {
            val externalUrl = metadata.externalUrl
                ?: return UploadResult.Error("External media requires a valid URL.")
            val photo = PhotographyPhoto(
                id = id,
                title = metadata.title,
                altText = metadata.altText,
                caption = metadata.caption,
                takenAt = metadata.takenAt,
                order = metadata.order,
                published = metadata.published,
                mediaType = metadata.mediaType,
                sourceKind = PhotographySourceKind.EXTERNAL,
                category = metadata.category,
                albumTitle = metadata.albumTitle,
                externalUrl = externalUrl,
                thumbnailUrl = metadata.thumbnailUrl,
                featured = metadata.featured,
                contentType = "text/uri-list",
                uploadedAt = Instant.now()
            )
            return try {
                contentStore.upsertPhotographyPhoto(photo)
                UploadResult.Success(photo)
            } catch (e: Exception) {
                log.error("Failed to save external photography media", e)
                UploadResult.Error("Could not save media. Please try again.")
            }
        }

        if (file == null || file.bytes.isEmpty()) return UploadResult.Error("Choose a media file to upload.")
        if (file.bytes.size > maxUploadBytes) return UploadResult.Error("Media file is too large. Maximum upload size is ${maxUploadBytes / 1_048_576} MB.")

        val contentType = file.contentType.substringBefore(';').trim().lowercase()
        val extension = extensionFor(contentType, metadata.mediaType)
            ?: return UploadResult.Error(uploadTypeError(metadata.mediaType))
        val storageKey = assetStore.keyFor(id, extension)
        val photo = PhotographyPhoto(
            id = id,
            title = metadata.title,
            altText = metadata.altText,
            caption = metadata.caption,
            takenAt = metadata.takenAt,
            order = metadata.order,
            published = metadata.published,
            storageKey = storageKey,
            contentType = contentType,
            originalFilename = file.originalFilename,
            sizeBytes = file.bytes.size.toLong(),
            mediaType = metadata.mediaType,
            sourceKind = PhotographySourceKind.UPLOAD,
            category = metadata.category,
            albumTitle = metadata.albumTitle,
            thumbnailUrl = metadata.thumbnailUrl,
            featured = metadata.featured,
            uploadedAt = Instant.now()
        )

        return try {
            assetStore.save(storageKey, contentType, file.bytes)
            try {
                contentStore.upsertPhotographyPhoto(photo)
            } catch (e: Exception) {
                runCatching { assetStore.delete(storageKey) }
                    .onFailure { cleanupError -> log.warn("Failed to delete orphaned photo asset {}", storageKey, cleanupError) }
                throw e
            }
            UploadResult.Success(photo)
        } catch (e: Exception) {
            log.error("Failed to upload photography photo", e)
            UploadResult.Error("Could not save photo. Please try again.")
        }
    }

    fun update(id: String, fields: Map<String, String>): UpdateResult {
        val existing = contentStore.listPhotographyPhotos().firstOrNull { it.id == id }
            ?: return UpdateResult.Error("Photo not found.")
        val metadata = parseMetadata(fields, requireTitle = true, requireAltText = true)
            ?: return UpdateResult.Error("Title and alt text are required.")
        if (metadata.sourceKind == PhotographySourceKind.EXTERNAL && metadata.externalUrl == null) {
            return UpdateResult.Error("External media requires a valid URL.")
        }
        if (metadata.sourceKind == PhotographySourceKind.UPLOAD) {
            if (existing.storageKey.isBlank()) {
                return UpdateResult.Error("Uploaded media requires a saved file.")
            }
            if (extensionFor(existing.contentType, metadata.mediaType) == null) {
                return UpdateResult.Error(uploadTypeError(metadata.mediaType))
            }
        }

        return try {
            contentStore.upsertPhotographyPhoto(
                existing.copy(
                    title = metadata.title,
                    altText = metadata.altText,
                    caption = metadata.caption,
                    takenAt = metadata.takenAt,
                    order = metadata.order,
                    published = metadata.published,
                    mediaType = metadata.mediaType,
                    sourceKind = metadata.sourceKind,
                    category = metadata.category,
                    albumTitle = metadata.albumTitle,
                    externalUrl = metadata.externalUrl,
                    thumbnailUrl = metadata.thumbnailUrl,
                    featured = metadata.featured
                )
            )
            UpdateResult.Success
        } catch (e: Exception) {
            log.error("Failed to update photography photo {}", id, e)
            UpdateResult.Error("Could not update photo.")
        }
    }

    fun delete(id: String): DeleteResult {
        val existing = contentStore.listPhotographyPhotos().firstOrNull { it.id == id }
            ?: return DeleteResult.Error("Photo not found.")
        return try {
            contentStore.deletePhotographyPhoto(id)
            if (existing.storageKey.isNotBlank()) {
                runCatching { assetStore.delete(existing.storageKey) }
                    .onFailure { log.warn("Failed to delete photo asset {}", existing.storageKey, it) }
            }
            DeleteResult.Success
        } catch (e: Exception) {
            log.error("Failed to delete photography photo {}", id, e)
            DeleteResult.Error("Could not delete photo.")
        }
    }

    private fun parseMetadata(
        fields: Map<String, String>,
        requireTitle: Boolean,
        requireAltText: Boolean
    ): PhotoMetadata? {
        val title = fields["title"].orEmpty().trim()
        val altText = fields["altText"].orEmpty().trim()
        if (requireTitle && title.isBlank()) return null
        if (requireAltText && altText.isBlank()) return null
        val mediaType = parseMediaType(fields["mediaType"])
        val sourceKind = parseSourceKind(fields["sourceKind"])
        val externalUrl = fields["externalUrl"].orEmpty().trim().takeIf { it.isNotBlank() }?.let {
            normalizeExternalUrl(it) ?: return null
        }
        val thumbnailUrl = fields["thumbnailUrl"].orEmpty().trim().takeIf { it.isNotBlank() }?.let {
            normalizeExternalUrl(it) ?: return null
        }
        val takenAt = fields["takenAt"].orEmpty().trim().takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull() ?: return null
        }
        return PhotoMetadata(
            title = title,
            altText = altText,
            caption = fields["caption"].orEmpty().trim().takeIf { it.isNotBlank() },
            takenAt = takenAt,
            order = fields["order"].orEmpty().trim().toIntOrNull() ?: 0,
            published = fields["published"].isOn(),
            mediaType = mediaType,
            sourceKind = sourceKind,
            category = fields["category"].orEmpty().trim().takeIf { it.isNotBlank() } ?: "Uncategorized",
            albumTitle = fields["albumTitle"].orEmpty().trim().takeIf { it.isNotBlank() },
            externalUrl = externalUrl,
            thumbnailUrl = thumbnailUrl,
            featured = fields["featured"].isOn()
        )
    }

    private fun extensionFor(contentType: String, mediaType: PhotographyMediaType): String? {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return when (mediaType) {
            PhotographyMediaType.PHOTO -> when (normalized) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> null
            }
            PhotographyMediaType.VIDEO,
            PhotographyMediaType.VIDEO_360 -> when (normalized) {
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "video/quicktime" -> "mov"
                else -> null
            }
        }
    }

    private fun uploadTypeError(mediaType: PhotographyMediaType): String =
        when (mediaType) {
            PhotographyMediaType.PHOTO -> "Unsupported image type. Upload JPEG, PNG, or WebP."
            PhotographyMediaType.VIDEO,
            PhotographyMediaType.VIDEO_360 -> "Unsupported video type. Upload MP4, WebM, or QuickTime."
        }

    private fun parseMediaType(value: String?): PhotographyMediaType =
        runCatching { PhotographyMediaType.valueOf(value.orEmpty().trim().uppercase()) }
            .getOrDefault(PhotographyMediaType.PHOTO)

    private fun parseSourceKind(value: String?): PhotographySourceKind =
        runCatching { PhotographySourceKind.valueOf(value.orEmpty().trim().uppercase()) }
            .getOrDefault(PhotographySourceKind.UPLOAD)

    private fun normalizeExternalUrl(value: String): String? =
        runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) uri.toString() else null
        }.getOrNull()

    private fun String?.isOn(): Boolean = this == "on" || this == "true" || this == "1"

    private fun String.assetFileName(): String = replace('\\', '/').substringAfterLast('/')

    private data class PhotoMetadata(
        val title: String,
        val altText: String,
        val caption: String?,
        val takenAt: LocalDate?,
        val order: Int,
        val published: Boolean,
        val mediaType: PhotographyMediaType,
        val sourceKind: PhotographySourceKind,
        val category: String,
        val albumTitle: String?,
        val externalUrl: String?,
        val thumbnailUrl: String?,
        val featured: Boolean
    )

    sealed class UploadResult {
        data class Success(val photo: PhotographyPhoto) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    sealed class UpdateResult {
        object Success : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    sealed class DeleteResult {
        object Success : DeleteResult()
        data class Error(val message: String) : DeleteResult()
    }
}
