package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*

@Composable
fun ContentSection(
    modifier: Modifier = Modifier(),
    surface: Boolean = true,
    content: () -> Unit
) {
    val wrapperModifier = modifier
        .style("max-width", 1200.px)
        .style("margin", "0 auto")
        .style("width", "100%")
        .padding(PortfolioTheme.Spacing.xl)

    Box(modifier = wrapperModifier) {
        Column(
            modifier = Modifier()
                .backgroundColor(if (surface) PortfolioTheme.Colors.SURFACE else "transparent")
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .padding(PortfolioTheme.Spacing.xl)
                .style("gap", PortfolioTheme.Spacing.lg)
                .style("box-shadow", "0 30px 120px rgba(2,4,24,0.45)")
        ) {
            content()
        }
    }
}
