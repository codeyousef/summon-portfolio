package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.AboutStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun AboutMeSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = AboutStrings.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
            )
            Text(
                text = AboutStrings.body.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
                    .fontSize(1.1.rem)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .marginTop(PortfolioTheme.Spacing.sm)
            ) {
                AnchorLink(
                    label = "X (Twitter)",
                    href = "https://x.com/deepissuemassaj",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.ACCENT)
                        .fontWeight(600)
                        .textDecoration(TextDecoration.Underline)
                        .style("text-underline-offset", "4px"),
                    target = "_blank",
                    rel = "noopener noreferrer",
                    navigationMode = LinkNavigationMode.Native,
                    title = null,
                    id = null,
                    ariaLabel = "Follow on X (Twitter)",
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap()
                )
            }
        }
    }
}

