package code.yousef.portfolio.contact

import code.yousef.portfolio.serialization.InstantIsoSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ContactSubmission(
    val id: String,
    val contact: String,
    val message: String,
    @Serializable(with = InstantIsoSerializer::class)
    val createdAt: Instant
)
