package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.core.style.Color
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
        .marginHorizontalAutoZero()
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
                .backdropBlur(20.px)
                .multipleShadows(
                    shadowConfig(
                        0,
                        30,
                        120,
                        0,
                        Color.hex("#02041873")
                    )
                )
        ) {
            content()
        }
    }
}
