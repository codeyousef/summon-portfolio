package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.safeFragmentHref
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.top
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.textDecoration
import code.yousef.summon.extensions.px

@Composable
fun Toc(entries: List<TocEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(code.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.md)
            .flex(grow = 0, shrink = 0, basis = "220px")
            .width("220px")
            .position(Position.Sticky)
            .top(PortfolioTheme.Spacing.lg)
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
                .textDecoration(TextDecoration.None)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .cursor(Cursor.Pointer)
                .hover(
                    Modifier()
                        .color(PortfolioTheme.Colors.ACCENT)
                ),
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
