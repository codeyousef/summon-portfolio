package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.foundation.Canvas
import codes.yousef.summon.components.foundation.ScriptTag
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.minHeight
import codes.yousef.summon.runtime.LocalPlatformRenderer

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    enableAuroraEffects: Boolean = true,
    content: () -> Unit
) {
    InjectFontAssets()
    PostHogAnalytics()

    // Global mobile styles
    GlobalStyle(
        """
        @media (max-width: 768px) {
            [data-page-content="true"] {
                padding: ${PortfolioTheme.Spacing.sm} !important;
                padding-left: ${PortfolioTheme.Spacing.sm} !important;
                padding-right: ${PortfolioTheme.Spacing.sm} !important;
            }
        }
        """
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
        .overflow(Overflow.Visible)


    Box(modifier = scaffoldModifier) {
        if (enableAuroraEffects) {
            WebGlCanvas()
            GrainLayer()
        }
        val columnModifier = Modifier()
            .position(Position.Relative)
            .zIndex(2)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.xl)
            .dataAttribute("page-content", "true")
            .let { base ->
                val extra = PortfolioTheme.Spacing.md
                if (locale.direction.equals("rtl", ignoreCase = true)) {
                    base.paddingLeft("calc(${PortfolioTheme.Spacing.xl} + $extra)")
                } else {
                    base.paddingRight("calc(${PortfolioTheme.Spacing.xl} + $extra)")
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
        link("preconnect", "https://fonts.gstatic.com", null, null, null, "anonymous")
        link(
            "stylesheet",
            "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
            null,
            null,
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
            .height(3500.px)
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
