package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.RichText
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.styles.GlobalStyle
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun Prose(html: String) {
    DocsProseStyles()
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.md)
            .alignItems(code.yousef.summon.modifier.AlignItems.Stretch)
    ) {
        RichText(
            html,
            modifier = Modifier()
                .attribute("class", "docs-prose")
                .fontSize(1.rem)
                .lineHeight(1.7)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        )
    }
}

@Composable
private fun DocsProseStyles() {
    GlobalStyle(
        """
        .docs-prose > *:first-child {
          margin-top: 0;
        }
        .docs-prose > * + * {
          margin-top: ${PortfolioTheme.Spacing.md};
        }
        .docs-prose h2,
        .docs-prose h3,
        .docs-prose h4 {
          margin-top: ${PortfolioTheme.Spacing.xl};
          margin-bottom: ${PortfolioTheme.Spacing.sm};
          font-weight: 700;
          color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
          letter-spacing: -0.01em;
        }
        .docs-prose p {
          margin: ${PortfolioTheme.Spacing.sm} 0;
          color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
        }
        .docs-prose a {
          color: ${PortfolioTheme.Colors.ACCENT};
          text-decoration: none;
          border-bottom: 1px solid ${PortfolioTheme.Colors.ACCENT};
        }
        .docs-prose a:hover {
          color: ${PortfolioTheme.Colors.ACCENT_ALT};
          border-bottom-color: ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        .docs-prose ul,
        .docs-prose ol {
          padding-left: ${PortfolioTheme.Spacing.lg};
          margin: ${PortfolioTheme.Spacing.sm} 0;
          display: flex;
          flex-direction: column;
          gap: ${PortfolioTheme.Spacing.xs};
        }
        .docs-prose blockquote {
          margin: ${PortfolioTheme.Spacing.md} 0;
          padding: ${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.md};
          border-left: 3px solid ${PortfolioTheme.Colors.ACCENT};
          background: rgba(255,255,255,0.03);
          color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
        }
        .docs-prose pre {
          padding: ${PortfolioTheme.Spacing.md};
          background: rgba(13,17,28,0.95);
          border-radius: ${PortfolioTheme.Radii.lg};
          border: 1px solid ${PortfolioTheme.Colors.BORDER};
          overflow-x: auto;
          font-family: ${PortfolioTheme.Typography.FONT_MONO};
          font-size: 0.9rem;
        }
        .docs-prose code {
          font-family: ${PortfolioTheme.Typography.FONT_MONO};
          font-size: 0.9em;
          background: rgba(255,255,255,0.06);
          padding: 0.1em 0.4em;
          border-radius: ${PortfolioTheme.Radii.sm};
        }
        .docs-prose table {
          width: 100%;
          border-collapse: collapse;
          margin: ${PortfolioTheme.Spacing.md} 0;
        }
        .docs-prose table th,
        .docs-prose table td {
          border: 1px solid ${PortfolioTheme.Colors.BORDER};
          padding: ${PortfolioTheme.Spacing.sm};
          text-align: left;
        }
        .docs-prose hr {
          border: none;
          border-top: 1px solid ${PortfolioTheme.Colors.BORDER};
          margin: ${PortfolioTheme.Spacing.lg} 0;
        }
        """.trimIndent()
    )
}
