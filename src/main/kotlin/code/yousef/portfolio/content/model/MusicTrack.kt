package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText
import kotlinx.serialization.Serializable

enum class MusicGenre(val label: LocalizedText) {
    ELECTRONIC(LocalizedText("Electronic", "إلكتروني")),
    AMBIENT(LocalizedText("Ambient", "أجواء")),
    EXPERIMENTAL(LocalizedText("Experimental", "تجريبي")),
    ORCHESTRAL(LocalizedText("Orchestral", "أوركسترالي")),
    SOUNDTRACK(LocalizedText("Soundtrack", "موسيقى تصويرية"));
}

@Serializable
data class MusicTrack(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText? = null,
    val genre: MusicGenre,
    val year: Int,
    val audioUrl: String,
    val coverArtUrl: String? = null,
    val duration: Int? = null, // seconds
    val featured: Boolean = false,
    val order: Int = 0,
    val bpm: Int? = null,
    val tags: List<String> = emptyList(),
    val externalLinks: Map<String, String> = emptyMap() // e.g., "spotify" to url, "soundcloud" to url
) {
    val durationFormatted: String
        get() = duration?.let {
            val minutes = it / 60
            val seconds = it % 60
            "$minutes:${seconds.toString().padStart(2, '0')}"
        } ?: "--:--"
}
