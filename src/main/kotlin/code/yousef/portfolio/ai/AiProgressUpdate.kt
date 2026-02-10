package code.yousef.portfolio.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiProgressUpdate(val id: String, val completed: Boolean)
