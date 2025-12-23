package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.safeFragmentHref
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun Toc(entries: List<TocEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(codes.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.md)
            .flex(grow = 0, shrink = 0, basis = "160px")
            .width(160.px)
            .position(Position.Sticky)
            .top(PortfolioTheme.Spacing.lg)
            // Hide on mobile, show on large screens
            .display(Display.None)
            .mediaQuery(MediaQuery.MinWidth(1100)) {
                display(Display.Flex)
            }
    ) {
        Text(
            text = "On this page",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontWeight(600)
        )
        entries.forEach { entry ->
            DocsTocLink(entry)
        }
    }
}

@Composable
private fun DocsTocLink(entry: TocEntry) {
    val spacerWidth = if (entry.level == 3) PortfolioTheme.Spacing.md else "0px"
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
    ) {
        if (spacerWidth != "0px") {
            Box(
                modifier = Modifier()
                    .width(spacerWidth)
                    .height(1.px)
            ) {}
        }
        AnchorLink(
            label = entry.text,
            href = safeFragmentHref(entry.anchor),
            modifier = Modifier()
                .display(Display.Block)
                .padding(PortfolioTheme.Spacing.xs)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .visited(Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)),
            navigationMode = LinkNavigationMode.Native,
            dataAttributes = mapOf("toc-entry" to entry.anchor),
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
