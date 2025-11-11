package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.Canvas
import code.yousef.summon.components.foundation.RawHtml
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
    enableAuroraEffects: Boolean = true,
    content: () -> Unit
) {
    RawHtml(
        """
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
        <style>
          body {
            font-family: ${PortfolioTheme.Typography.FONT_SANS};
            color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
            background-color: ${PortfolioTheme.Colors.BACKGROUND};
          }
          a {
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
            text-decoration: none;
            transition: color 200ms ease, box-shadow 200ms ease, transform 200ms ease;
          }
          a:hover {
            color: ${PortfolioTheme.Colors.LINK_HOVER};
          }
          a:focus-visible,
          button:focus-visible {
            outline: 2px solid ${PortfolioTheme.Colors.ACCENT_ALT};
            outline-offset: 3px;
          }
          a[data-cta] {
            box-shadow: 0 0 20px rgba(255, 70, 104, 0.45);
          }
          a[data-cta]:hover {
            color: #ffffff;
            box-shadow: 0 0 30px rgba(255, 70, 104, 0.6);
            transform: translateY(-2px);
          }
        </style>
        """.trimIndent()
    )

    val scaffoldModifier = modifier
        .minHeight("100vh")
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND)
        .backgroundLayers {
            radialGradient {
                position("20% 30%")
                colorStop("rgba(255,70,104,0.2)", "0%")
                colorStop("transparent", "60%")
            }
            radialGradient {
                position("80% 70%")
                colorStop("rgba(106,215,255,0.15)", "0%")
                colorStop("transparent", "60%")
            }
            linearGradient {
                direction("180deg")
                colorStop("#001a2c", "0%")
                colorStop("#05294a", "100%")
            }
        }
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND_ALT)
        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        .position(Position.Relative)
        .overflow(Overflow.Hidden)
        .attribute("lang", locale.code)
        .attribute("dir", locale.direction)

    Box(modifier = scaffoldModifier) {
        if (enableAuroraEffects) {
            WebGlCanvas()
            GrainLayer()
        }
        Column(
            modifier = Modifier()
                .position(Position.Relative)
                .zIndex(2)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            content()
        }
        if (enableAuroraEffects) {
            WebGlScript()
        }
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
