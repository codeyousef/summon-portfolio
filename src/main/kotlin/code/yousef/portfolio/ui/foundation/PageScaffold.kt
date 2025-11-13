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
import code.yousef.summon.runtime.LocalPlatformRenderer

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    enableAuroraEffects: Boolean = true,
    content: () -> Unit
) {
    InjectFontAssets()

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
        val isRtl = locale.direction.equals("rtl", ignoreCase = true)
        val flexColumnPadding = "calc(${PortfolioTheme.Spacing.xl} + ${PortfolioTheme.Spacing.md})"
        val columnModifier = Modifier()
            .position(Position.Relative)
            .zIndex(2)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.xl)
            .let { base ->
                if (isRtl) {
                    base.paddingLeft(flexColumnPadding)
                } else {
                    base.paddingRight(flexColumnPadding)
                }
            }
        Column(modifier = columnModifier) {
            content()
        }
        if (enableAuroraEffects) {
            WebGlScript()
        }
    }
}

@Composable
private fun InjectFontAssets() {
    val renderer = runCatching { LocalPlatformRenderer.current }.getOrNull() ?: return
    renderer.renderHeadElements {
        link("preconnect", "https://fonts.googleapis.com", null, null, null, null)
        link("preconnect", "https://fonts.gstatic.com", null, null, null, null)
        link(
            "stylesheet",
            "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
            null,
            "text/css",
            null,
            null
        )
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
