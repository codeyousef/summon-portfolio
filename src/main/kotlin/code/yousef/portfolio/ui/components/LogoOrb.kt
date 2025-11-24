package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun LogoOrb() {
    Box(
        modifier = Modifier()
            .width(38.px)
            .height(38.px)
            .borderRadius(14.px)
            .backgroundLayers {
                radialGradient {
                    size("120%", "120%")
                    position("30%", "20%")
                    colorStop(PortfolioTheme.Colors.ACCENT_ALT, "0%")
                    colorStop(PortfolioTheme.Colors.ACCENT, "35%")
                    colorStop("#5e0f27", "60%")
                    colorStop("#14070e", "100%")
                }
            }
            .multipleShadows(
                shadowConfig(0, 0, 20, 0, Color.hex("#00000099"), true),
                shadowConfig(0, 10, 30, 0, Color.hex("#00000099"))
            )
            .position(Position.Relative)
    ) {
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .inset((-1).px)
                .borderRadius(14.px)
                .backgroundLayers {
                    conicGradient {
                        from("210deg")
                        colorStop("#ffffff88")
                        colorStop("#ffffff00", "40%")
                        colorStop("#ffffff00", "70%")
                        colorStop("#ffffff33")
                    }
                }
                .filter { blur(0.5) }
                .mixBlendMode(BlendMode.Overlay)
        ) {}
    }
}
