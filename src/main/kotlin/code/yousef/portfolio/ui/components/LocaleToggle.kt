package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun LocaleToggle(current: PortfolioLocale, path: String = "") {
    Row(
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .gap(PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
    ) {
        LocaleToggleButton(
            locale = PortfolioLocale.EN,
            current = current,
            path = path,
        )
        Text(
            text = "|",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.75.rem)
        )
        LocaleToggleButton(
            locale = PortfolioLocale.AR,
            current = current,
            path = path,
        )
    }
}

@Composable
private fun LocaleToggleButton(
    locale: PortfolioLocale,
    current: PortfolioLocale,
    path: String,
) {
    val isActive = locale == current
    val baseRoot = portfolioBaseUrl().trimEnd('/')
    val normalizedPath = path.trim().let { value ->
        when {
            value.isBlank() || value == "/" -> ""
            value.startsWith('/') -> value
            else -> "/$value"
        }
    }
    val href = if (locale == PortfolioLocale.EN) "$baseRoot$normalizedPath" else "$baseRoot/${locale.code}$normalizedPath"
    AnchorLink(
        label = locale.code.uppercase(),
        href = href,
        modifier = Modifier()
            .textDecoration(TextDecoration.None)
            .color(if (isActive) PortfolioTheme.Colors.BACKGROUND else PortfolioTheme.Colors.TEXT_SECONDARY)
            .backgroundColor(if (isActive) PortfolioTheme.Colors.ACCENT_ALT else "transparent")
            .fontSize(0.75.rem)
            .fontWeight(600)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderRadius(PortfolioTheme.Radii.pill)
            .whiteSpace(WhiteSpace.NoWrap),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("locale" to locale.code),
        navigationMode = LinkNavigationMode.Native
    )
}
