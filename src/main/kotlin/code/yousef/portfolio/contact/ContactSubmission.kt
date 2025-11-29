package code.yousef.portfolio.contact

import java.time.Instant

data class ContactSubmission(
    val id: String,
    val contact: String,
    val message: String,
    val createdAt: Instant
)
