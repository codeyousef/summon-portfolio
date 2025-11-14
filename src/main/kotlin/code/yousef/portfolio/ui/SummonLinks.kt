package code.yousef.portfolio.ui

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.summonMarketingUrl

private val summonLabel = LocalizedText("Summon", "Summon")

data class SummonInlineText(
    val value: String,
    val html: String,
    val hasSummonLink: Boolean
)

fun summonInlineAnchor(
    locale: PortfolioLocale,
    label: LocalizedText = summonLabel
): String {
    val href = summonMarketingUrl()
    val safeLabel = htmlEscape(label.resolve(locale))
    return "<a href=\"$href\" style=\"color: inherit; text-decoration: underline;\">$safeLabel</a>"
}

fun LocalizedText.resolveSummonInline(locale: PortfolioLocale): SummonInlineText {
    val token = "%SUMMON%"
    val resolved = this.resolve(locale)
    val hasToken = resolved.contains(token)
    if (!hasToken) {
        return SummonInlineText(
            value = resolved,
            html = htmlEscape(resolved),
            hasSummonLink = false
        )
    }
    val parts = resolved.split(token)
    val plainLabel = summonLabel.resolve(locale)
    val anchor = summonInlineAnchor(locale)
    val plainBuilder = StringBuilder(resolved.length)
    val htmlBuilder = StringBuilder(resolved.length + anchor.length)
    parts.forEachIndexed { index, part ->
        plainBuilder.append(part)
        htmlBuilder.append(htmlEscape(part))
        if (index < parts.lastIndex) {
            plainBuilder.append(plainLabel)
            htmlBuilder.append(anchor)
        }
    }
    return SummonInlineText(
        value = plainBuilder.toString(),
        html = htmlBuilder.toString(),
        hasSummonLink = true
    )
}

fun LocalizedText.resolveWithSummonLink(locale: PortfolioLocale): String =
    resolveSummonInline(locale).html

private fun htmlEscape(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
