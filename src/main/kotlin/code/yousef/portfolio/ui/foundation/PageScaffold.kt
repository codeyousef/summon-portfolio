package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.Canvas
import code.yousef.summon.components.foundation.ScriptTag
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.minHeight

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    val scaffoldModifier = modifier
        .minHeight("100vh")
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND)
        .backgroundLayers {
            radialGradient {
                size("1300px", "900px")
                position("22%", "4%")              // replaces the CSS “at 22% 4%”
                colorStop("rgba(255,59,106,0.48)", "0%")
                colorStop("rgba(15,17,23,0.02)", "65%")
            }
            radialGradient {
                size("1000px", "780px")
                position("78%", "-8%")
                colorStop("rgba(46,130,220,0.42)", "0%")
                colorStop("rgba(8,9,12,0.05)", "55%")
            }
            radialGradient {
                size("2200px", "1500px")
                position("40%", "-40%")
                colorStop("rgba(226,68,122,0.3)", "0%")
                colorStop("rgba(10,11,13,0)", "70%")
            }
            radialGradient {
                size("1200px", "900px")
                position("25%", "12%")
                colorStop("#15161c", "0%")
                colorStop(PortfolioTheme.Colors.BACKGROUND.toString(), "55%")
            }
        }
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND_ALT)
        .style("background-blend-mode", "screen, screen, screen, normal, normal")
        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        .position(Position.Relative)
        .overflow(Overflow.Hidden)
        .attribute("lang", locale.code)
        .attribute("dir", locale.direction)

    Box(modifier = scaffoldModifier) {
        WebGlCanvas()
        GrainLayer()
        Column(
            modifier = Modifier()
                .position(Position.Relative)
                .zIndex(2)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            content()
        }
        WebGlScript()
    }
}

@Composable
private fun WebGlCanvas() {
    Canvas(
        id = "gl",
        modifier = Modifier()
            .position(Position.Fixed)
            .inset("0")
            .width(100.percent)
            .height(3000.px)
            .pointerEvents(PointerEvents.None)
            .zIndex(0)
    )
}

@Composable
private fun GrainLayer() {
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .inset("-20vmax")
            .opacity(0.06F)
            .pointerEvents(PointerEvents.None)
            .mixBlendMode(BlendMode.Multiply)
            .backgroundLayers {
                linearGradient(repeating = true) {
                    direction("0deg")
                    colorStop("transparent")
                    colorStop("transparent", "2px")
                    colorStop("rgba(0,0,0,0.35)", "3px")
                    colorStop("transparent", "4px")
                }
            }
            .zIndex(1)
    ) {}
}

@Composable
private fun WebGlScript() {
    ScriptTag(
        id = "aurora-gl-script",
        src = "/static/aurora-bg.js",
        defer = true
    )
}

