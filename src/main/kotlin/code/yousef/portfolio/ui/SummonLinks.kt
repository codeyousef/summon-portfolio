package code.yousef.portfolio.ui

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale

private val summonLabel = LocalizedText("Summon", "Summon")

data class SummonInlineText(
    val value: String,
    val textSegments: List<String>,
    val summonLinkLabel: String?
) {
    val hasSummonLink: Boolean get() = summonLinkLabel != null
}

fun LocalizedText.resolveSummonInline(locale: PortfolioLocale): SummonInlineText {
    val token = "%SUMMON%"
    val resolved = this.resolve(locale)
    val hasToken = resolved.contains(token)
    if (!hasToken) {
        return SummonInlineText(
            value = resolved,
            textSegments = listOf(resolved),
            summonLinkLabel = null
        )
    }
    val parts = resolved.split(token)
    val plainLabel = summonLabel.resolve(locale)
    val plainBuilder = StringBuilder(resolved.length)
    parts.forEachIndexed { index, part ->
        plainBuilder.append(part)
        if (index < parts.lastIndex) {
            plainBuilder.append(plainLabel)
        }
    }
    return SummonInlineText(
        value = plainBuilder.toString(),
        textSegments = parts,
        summonLinkLabel = plainLabel
    )
}
