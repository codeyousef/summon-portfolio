package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
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
            .borderStyle("solid")
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("position", "sticky")
            .style("top", PortfolioTheme.Spacing.lg)
            .style("max-height", "80vh")
            .style("overflow-y", "auto")
    ) {
        tree.sections.forEach { section ->
            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                Text(
                    text = section.title,
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontWeight(600)
                )
                section.children.forEach { node ->
                    val active = normalize(currentPath) == normalize(node.path)
                    AnchorLink(
                        label = node.title,
                        href = node.path,
                        modifier = Modifier()
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .color(
                                if (active) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.TEXT_PRIMARY
                            )
                            .textDecoration("none"),
                        navigationMode = LinkNavigationMode.Native,
                        target = null,
                        rel = null,
                        title = null,
                        id = null,
                        ariaLabel = null,
                        ariaDescribedBy = null,
                        dataHref = null,
                        dataAttributes = mapOf("nav" to node.title.lowercase())
                    )
                }
            }
        }
    }
}

private fun normalize(path: String): String = if (path.isBlank()) "/" else path.trimEnd('/')
