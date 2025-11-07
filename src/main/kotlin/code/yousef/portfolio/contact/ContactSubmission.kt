package code.yousef.portfolio.contact

import java.time.Instant

data class ContactSubmission(
    val id: String,
    val name: String,
    val email: String?,
    val whatsapp: String,
    val requirements: String,
    val createdAt: Instant
)
