package code.yousef.portfolio.docs.summon

import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.MarkdownMeta
import code.yousef.portfolio.docs.NeighborLinks
import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.components.DocsSidebar
import code.yousef.portfolio.docs.summon.components.Prose
import code.yousef.portfolio.docs.summon.components.Toc
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.sections.PortfolioFooter
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

@Composable
fun DocsShell(
    requestPath: String,
    html: String,
    toc: List<TocEntry>,
    sidebar: DocsNavTree,
    meta: MarkdownMeta,
    neighbors: NeighborLinks
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.lg)
    ) {
        Text(
            text = meta.title,
            modifier = Modifier()
                .fontSize(2.5.rem)
                .fontWeight(700)
        )
        Text(
            text = meta.description,
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.lg)
                .alignItems(AlignItems.FlexStart)
                .flexWrap(FlexWrap.Wrap)
                .width("100%")
        ) {
            DocsSidebar(tree = sidebar, currentPath = requestPath)
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "0%")
                    .minWidth("0px")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                Prose(html = html)
                NeighborRow(neighbors)
            }
            Toc(entries = toc)
        }
        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun NeighborRow(neighbors: NeighborLinks) {
    if (neighbors.previous == null && neighbors.next == null) return
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(PortfolioTheme.Spacing.md)
    ) {
        neighbors.previous?.let { link ->
            DocsNeighborLink(label = "← ${link.title}", href = link.path, slot = "prev")
        }
        neighbors.next?.let { link ->
            DocsNeighborLink(label = "${link.title} →", href = link.path, slot = "next")
        }
    }
}

@Composable
private fun DocsNeighborLink(label: String, href: String, slot: String) {
    AnchorLink(
        label = label,
        href = safeHref(href),
        modifier = Modifier()
            .fontWeight(600)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .visited(Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)),
        navigationMode = LinkNavigationMode.Native,
        dataAttributes = mapOf("neighbor" to slot),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null
    )
}
