package code.yousef.portfolio.photography

import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.model.PhotographyPhoto
import org.slf4j.LoggerFactory
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
        val photo = contentStore.listPhotographyPhotos().firstOrNull { it.id == id && it.published } ?: return null
        return assetStore.load(photo.storageKey, photo.contentType)
    }

    fun upload(fields: Map<String, String>, file: MultipartFilePart?): UploadResult {
        val metadata = parseMetadata(fields, requireTitle = true, requireAltText = true)
            ?: return UploadResult.Error("Title and alt text are required.")
        if (file == null || file.bytes.isEmpty()) return UploadResult.Error("Choose a photo to upload.")
        if (file.bytes.size > maxUploadBytes) return UploadResult.Error("Photo is too large. Maximum upload size is ${maxUploadBytes / 1_048_576} MB.")

        val contentType = file.contentType.substringBefore(';').trim().lowercase()
        val extension = extensionFor(contentType) ?: return UploadResult.Error("Unsupported image type. Upload JPEG, PNG, or WebP.")
        val id = UUID.randomUUID().toString()
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

        return try {
            contentStore.upsertPhotographyPhoto(
                existing.copy(
                    title = metadata.title,
                    altText = metadata.altText,
                    caption = metadata.caption,
                    takenAt = metadata.takenAt,
                    order = metadata.order,
                    published = metadata.published
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
            runCatching { assetStore.delete(existing.storageKey) }
                .onFailure { log.warn("Failed to delete photo asset {}", existing.storageKey, it) }
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
        val takenAt = fields["takenAt"].orEmpty().trim().takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull() ?: return null
        }
        return PhotoMetadata(
            title = title,
            altText = altText,
            caption = fields["caption"].orEmpty().trim().takeIf { it.isNotBlank() },
            takenAt = takenAt,
            order = fields["order"].orEmpty().trim().toIntOrNull() ?: 0,
            published = fields["published"].isOn()
        )
    }

    private fun extensionFor(contentType: String): String? =
        when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> null
        }

    private fun String?.isOn(): Boolean = this == "on" || this == "true" || this == "1"

    private data class PhotoMetadata(
        val title: String,
        val altText: String,
        val caption: String?,
        val takenAt: LocalDate?,
        val order: Int,
        val published: Boolean
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
