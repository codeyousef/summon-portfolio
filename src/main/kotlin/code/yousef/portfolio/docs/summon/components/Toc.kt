package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.TocEntry
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
fun Toc(entries: List<TocEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(code.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle("solid")
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.md)
    ) {
        Text(
            text = "On this page",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontWeight(600)
        )
        entries.forEach { entry ->
            RawHtml(
                """
                <a class="docs-toc-link" href="#${entry.anchor}" data-toc="${entry.anchor}" style="
                    display: block;
                    padding: ${PortfolioTheme.Spacing.xs};
                    padding-left: ${if (entry.level == 3) PortfolioTheme.Spacing.md else PortfolioTheme.Spacing.sm};
                    color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
                    text-decoration: none;
                    font-size: 0.95rem;">
                    ${entry.text}
                </a>
                """.trimIndent()
            )
        }
    }
}
