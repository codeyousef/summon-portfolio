package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.alignItems
import codes.yousef.summon.modifier.LayoutModifiers.display
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.color
import java.time.Year

@Composable
fun PortfolioFooter(locale: PortfolioLocale) {
    val currentYear = Year.now().value
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            Text(text = "© $currentYear")
            Text(text = "Yousef")
        }
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xxs)
        ) {
            Text(
                text = "Built with",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Image(
                src = "/static/summon-logo.png",
                alt = "Summon",
                modifier = Modifier()
                    .width(24.px)
                    .height(24.px)
                    .display(Display.InlineFlex)
            )
            AnchorLink(
                label = "Summon",
                href = summonMarketingUrl(),
                modifier = Modifier()
                    .textDecoration(TextDecoration.Underline)
                    .color(PortfolioTheme.Colors.ACCENT_ALT),
                navigationMode = LinkNavigationMode.Native,
                dataAttributes = mapOf("footer-link" to "summon"),
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null
            )
        }
    }
}
