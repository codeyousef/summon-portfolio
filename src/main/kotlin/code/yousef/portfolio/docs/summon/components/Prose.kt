package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.*

@Composable
fun Prose(html: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle("solid")
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.xl)
            .alignItems(code.yousef.summon.modifier.AlignItems.Stretch)
    ) {
        RawHtml(
            """
            <article class="prose-docs">
                $html
            </article>
            """.trimIndent()
        )
    }
}
