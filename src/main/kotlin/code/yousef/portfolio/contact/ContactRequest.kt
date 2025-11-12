package code.yousef.portfolio.contact

import kotlinx.serialization.Serializable

@Serializable
data class ContactRequest(
    val name: String,
    val email: String?,
    val whatsapp: String?,
    val requirements: String
)
