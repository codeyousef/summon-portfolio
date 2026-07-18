package code.yousef.portfolio.content.model

import code.yousef.portfolio.serialization.InstantIsoSerializer
import code.yousef.portfolio.serialization.LocalDateIsoSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
enum class PhotographyMediaType {
    PHOTO,
    VIDEO,
    VIDEO_360
}

@Serializable
enum class PhotographySourceKind {
    UPLOAD,
    EXTERNAL
}

@Serializable
data class PhotographyPhoto(
    val id: String,
    val title: String,
    val altText: String,
    val caption: String? = null,
    @Serializable(with = LocalDateIsoSerializer::class)
    val takenAt: LocalDate? = null,
    val order: Int = 0,
    val published: Boolean = false,
    val storageKey: String = "",
    val contentType: String = "application/octet-stream",
    val originalFilename: String? = null,
    val sizeBytes: Long = 0,
    val mediaType: PhotographyMediaType = PhotographyMediaType.PHOTO,
    val sourceKind: PhotographySourceKind = PhotographySourceKind.UPLOAD,
    val category: String = "Uncategorized",
    val albumTitle: String? = null,
    val externalUrl: String? = null,
    val thumbnailUrl: String? = null,
    val featured: Boolean = false,
    @Serializable(with = InstantIsoSerializer::class)
    val uploadedAt: Instant = Instant.now()
)
