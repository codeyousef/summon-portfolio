package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun ContentSection(
    modifier: Modifier = Modifier(),
    surface: Boolean = true,
    content: () -> Unit
) {
    GlobalStyle(css = """
        /* Consistent mobile padding */
        @media (max-width: 768px) {
            [data-content-section="wrapper"] {
                padding: ${PortfolioTheme.Spacing.xs} !important;
                width: 100% !important;
                max-width: 100vw !important;
            }
            [data-content-section="inner"] {
                padding: ${PortfolioTheme.Spacing.md} !important;
            }
        }
    """)
    
    val wrapperModifier = modifier
        .maxWidth(1200.px)
        .width("min(100%, calc(100vw - ${PortfolioTheme.Spacing.sm}))")
        .marginHorizontalAutoZero()
        .padding(PortfolioTheme.Spacing.xl)
        .dataAttribute("content-section", "wrapper")

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
                .dataAttribute("content-section", "inner")
        ) {
            content()
        }
    }
}
