package code.yousef.portfolio.admin.auth

import kotlinx.serialization.Serializable

@Serializable
data class AdminSession(
    val username: String,
    val mustChangePassword: Boolean
)
