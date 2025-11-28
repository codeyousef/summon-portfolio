package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
data class Testimonial(
    val id: String,
    val quote: LocalizedText,
    val author: String,
    val role: LocalizedText,
    val company: LocalizedText,
    val featured: Boolean = false,
    val order: Int = 0
)
