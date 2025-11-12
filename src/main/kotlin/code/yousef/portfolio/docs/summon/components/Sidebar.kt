package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.summon.safeHref
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.vh
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.top
import code.yousef.summon.modifier.StylingModifiers.fontWeight

@Composable
fun DocsSidebar(tree: DocsNavTree, currentPath: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(code.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .flex(grow = 0, shrink = 0, basis = "260px")
            .width("260px")
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .position(Position.Sticky)
            .top(PortfolioTheme.Spacing.lg)
            .maxHeight(80.vh)
            .overflowY(Overflow.Auto)
    ) {
        tree.sections.forEach { section ->
            val nodes = if (section.children.isEmpty()) listOf(section) else section.children
            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                Text(
                    text = section.title,
                    modifier = Modifier()
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
                nodes.forEach { node ->
                    val active = normalize(currentPath) == normalize(node.path)
                    DocsSidebarLink(
                        label = node.title,
                        href = node.path,
                        active = active,
                        dataLabel = node.title.lowercase()
                    )
                }
            }
        }
    }
}

private fun normalize(path: String): String = if (path.isBlank()) "/" else path.trimEnd('/')

@Composable
private fun DocsSidebarLink(
    label: String,
    href: String,
    active: Boolean,
    dataLabel: String
) {
    val baseModifier = Modifier()
        .display(Display.Block)
        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
        .borderRadius(PortfolioTheme.Radii.md)
        .color(
            if (active) PortfolioTheme.Colors.ACCENT_ALT else PortfolioTheme.Colors.TEXT_PRIMARY
        )
        .backgroundColor(
            if (active) PortfolioTheme.Colors.SURFACE_STRONG else "transparent"
        )
    AnchorLink(
        label = label,
        href = safeHref(href),
        modifier = baseModifier,
        navigationMode = LinkNavigationMode.Native,
        dataAttributes = mapOf("docs-nav" to dataLabel),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null
    )
}
