package code.yousef.portfolio.contact

import kotlinx.serialization.Serializable

@Serializable
data class ContactRequest(
    val contact: String,
    val message: String
)
