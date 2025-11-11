package code.yousef.portfolio.ui

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale

const val SUMMON_MARKETING_URL = "https://summon.yousef.codes"

private val summonLabel = LocalizedText("Summon", "سُمّون")

data class SummonInlineText(val value: String, val hasSummonLink: Boolean)

fun summonInlineAnchor(
    locale: PortfolioLocale,
    label: LocalizedText = summonLabel
): String = "<a href=\"$SUMMON_MARKETING_URL\" class=\"summon-inline-link\" data-cta=\"summon-link\">${label.resolve(locale)}</a>"

fun LocalizedText.resolveSummonInline(locale: PortfolioLocale): SummonInlineText {
    val token = "%SUMMON%"
    val resolved = this.resolve(locale)
    val hasToken = resolved.contains(token)
    return if (hasToken) {
        SummonInlineText(resolved.replace(token, summonInlineAnchor(locale)), true)
    } else {
        SummonInlineText(resolved, false)
    }
}

fun LocalizedText.resolveWithSummonLink(locale: PortfolioLocale): String =
    resolveSummonInline(locale).value
