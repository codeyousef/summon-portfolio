package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight

@Composable
fun Toc(entries: List<TocEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier()
            .attribute("class", "docs-toc")
            .display(Display.Flex)
            .flexDirection(code.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle("solid")
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.md)
            .flex(grow = 0, shrink = 0, basis = "220px")
            .width("220px")
            .style("position", "sticky")
            .style("top", PortfolioTheme.Spacing.lg)
    ) {
        Text(
            text = "On this page",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontWeight(600)
        )
        entries.forEach { entry ->
            AnchorLink(
                label = entry.text,
                href = "#${entry.anchor}",
                modifier = Modifier()
                    .display(Display.Block)
                    .padding(PortfolioTheme.Spacing.xs)
                    .paddingLeft(
                        if (entry.level == 3) PortfolioTheme.Spacing.md else PortfolioTheme.Spacing.sm
                    )
                    .textDecoration("none")
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .fontSize(0.95.rem),
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null,
                dataAttributes = mapOf("toc-entry" to entry.anchor),
                navigationMode = LinkNavigationMode.Client
            )
        }
    }
}
