package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
data class HeroContent(
    val eyebrow: LocalizedText,
    val titlePrimary: LocalizedText,
    val titleSecondary: LocalizedText,
    val subtitle: LocalizedText,
    val ctaPrimary: LocalizedText,
    val ctaSecondary: LocalizedText,
    val metrics: List<HeroMetric>
)

@Serializable
data class HeroMetric(
    val value: String,
    val label: LocalizedText,
    val detail: LocalizedText
)
