package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.summon.dataAttributes
import code.yousef.portfolio.docs.summon.htmlEscape
import code.yousef.portfolio.docs.summon.safeHref
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight

@Composable
fun DocsSidebar(tree: DocsNavTree, currentPath: String) {
    Column(
        modifier = Modifier()
            .attribute("class", "docs-sidebar")
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
    val classes = buildString {
        append("docs-sidebar__link")
        if (active) append(" docs-sidebar__link--active")
    }
    val html = """
        <a class="$classes" href="${safeHref(href)}"${dataAttributes(mapOf("docs-nav" to dataLabel))}>
          ${htmlEscape(label)}
        </a>
    """.trimIndent()
    RawHtml(html)
}
