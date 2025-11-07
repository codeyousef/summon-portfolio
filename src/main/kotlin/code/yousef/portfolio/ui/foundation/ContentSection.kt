package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun ContentSection(
    modifier: Modifier = Modifier(),
    surface: Boolean = true,
    content: () -> Unit
) {
    val wrapperModifier = modifier
        .maxWidth(1200.px)
        .width(100.percent)
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
                .gap(PortfolioTheme.Spacing.lg)
                .boxShadow("0 30px 120px rgba(2,4,24,0.45)")
        ) {
            content()
        }
    }
}
