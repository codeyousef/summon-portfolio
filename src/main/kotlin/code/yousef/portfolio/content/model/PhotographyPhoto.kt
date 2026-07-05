package code.yousef.portfolio.content.model

import code.yousef.portfolio.serialization.InstantIsoSerializer
import code.yousef.portfolio.serialization.LocalDateIsoSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

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
    val storageKey: String,
    val contentType: String,
    val originalFilename: String? = null,
    val sizeBytes: Long = 0,
    @Serializable(with = InstantIsoSerializer::class)
    val uploadedAt: Instant = Instant.now()
)
