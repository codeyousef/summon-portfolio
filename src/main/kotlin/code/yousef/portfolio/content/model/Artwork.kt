package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText
import kotlinx.serialization.Serializable

enum class ArtworkMedium(val label: LocalizedText) {
    DIGITAL(LocalizedText("Digital", "رقمي")),
    TRADITIONAL(LocalizedText("Traditional", "تقليدي")),
    PHOTOGRAPHY(LocalizedText("Photography", "تصوير")),
    MIXED_MEDIA(LocalizedText("Mixed Media", "وسائط متعددة"));
}

@Serializable
data class Artwork(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText? = null,
    val medium: ArtworkMedium,
    val year: Int,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val featured: Boolean = false,
    val order: Int = 0,
    val dimensions: String? = null,
    val tags: List<String> = emptyList()
) {
    val thumbnail: String get() = thumbnailUrl ?: imageUrl
}
