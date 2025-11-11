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
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight

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
        RawHtml(
            """
            <style>
            .prose-docs {
              line-height: 1.7;
              font-size: 1rem;
              color: #e4e4f0;
            }
            .prose-docs pre {
              padding: 16px;
              background: rgba(255,255,255,0.04);
              border-radius: 12px;
              overflow-x: auto;
            }
            .prose-docs code {
              font-family: "JetBrains Mono", monospace;
              font-size: 0.95rem;
            }
            .prose-docs table {
              width: 100%;
              border-collapse: collapse;
            }
            .prose-docs table td,
            .prose-docs table th {
              border: 1px solid rgba(255,255,255,0.06);
              padding: 10px;
            }
            .prose-docs a {
              color: ${PortfolioTheme.Colors.ACCENT_ALT};
              text-decoration: none;
            }
            .prose-docs a:hover {
              text-decoration: underline;
            }
            .prose-docs h1,
            .prose-docs h2,
            .prose-docs h3,
            .prose-docs h4,
            .prose-docs h5,
            .prose-docs h6 {
              color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
              margin-top: 28px;
              margin-bottom: 12px;
            }
            .prose-docs p,
            .prose-docs li {
              color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
            }
            .docs-sidebar__link {
              display: block;
              padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
              border-radius: ${PortfolioTheme.Radii.md};
              text-decoration: none;
              color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
              transition: background ${PortfolioTheme.Motion.DEFAULT}, color ${PortfolioTheme.Motion.DEFAULT};
            }
            .docs-sidebar__link:hover {
              background-color: ${PortfolioTheme.Colors.SURFACE_STRONG};
              color: ${PortfolioTheme.Colors.ACCENT_ALT};
            }
            .docs-sidebar__link--active {
              background-color: ${PortfolioTheme.Colors.SURFACE_STRONG};
              color: ${PortfolioTheme.Colors.ACCENT_ALT};
            }
            .docs-toc__link {
              display: block;
              padding: ${PortfolioTheme.Spacing.xs};
              text-decoration: none;
              color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
              font-size: 0.95rem;
            }
            .docs-toc__link:hover {
              color: ${PortfolioTheme.Colors.ACCENT_ALT};
            }
            .docs-neighbor__link {
              text-decoration: none;
              font-weight: 600;
              color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
            }
            .docs-neighbor__link:hover {
              color: ${PortfolioTheme.Colors.ACCENT_ALT};
            }
            @media (max-width: 1024px) {
              .docs-sidebar,
              .docs-toc {
                position: relative !important;
                top: auto !important;
                width: 100% !important;
                max-width: 100% !important;
                flex-basis: 100% !important;
              }
              .docs-toc {
                margin-top: ${PortfolioTheme.Spacing.md};
              }
            }
            </style>
            """.trimIndent()
        )
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
    val html = """
        <a class="docs-neighbor__link" href="${safeHref(href)}"${dataAttributes(mapOf("neighbor" to slot))}>
          ${htmlEscape(label)}
        </a>
    """.trimIndent()
    RawHtml(html)
}
