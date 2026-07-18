package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.AnimationName
import codes.yousef.summon.components.styles.AnimationIterationCount
import codes.yousef.summon.components.styles.StyleElement
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StylePseudoElement
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.components.styles.animation
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*

/**
 * Brutalist scaffold for the scratchpad page.
 * Black background, green text, monospace everything.
 */
object ScratchpadTheme {
    const val BG_PRIMARY = "#0a0a0a"
    const val BG_SECONDARY = "#111111"
    const val BG_SURFACE = "#1a1a1a"
    const val TEXT_PRIMARY = "#00ff00" // Matrix green
    const val TEXT_SECONDARY = "#00aa00"
    const val TEXT_MUTED = "#006600"
    const val ACCENT = "#ff00ff" // Magenta
    const val ACCENT_ALT = "#00ffff" // Cyan
    const val DANGER = "#ff0000"
    const val WARNING = "#ffff00"
    const val BORDER = "#333333"
    const val FONT_MONO = "\"JetBrains Mono\", \"Fira Code\", \"Cascadia Code\", ui-monospace, monospace"
}

@Composable
fun ScratchpadScaffold(
    content: () -> Unit
) {
    val flicker = AnimationName.named("scratchpad-flicker")
    val scrollbarThumb = StyleSelector.Universal.pseudoElement(StylePseudoElement.WebkitScrollbarThumb)
    TypedStyleSheet {
        rule(
            StyleSelector.element(StyleElement.Html).or(StyleSelector.element(StyleElement.Body)),
            Modifier()
                .margin(0)
                .padding(0)
                .overflow(Overflow.Hidden)
                .backgroundColor(ScratchpadTheme.BG_PRIMARY)
                .fontFamily(ScratchpadTheme.FONT_MONO)
                .color(ScratchpadTheme.TEXT_PRIMARY)
                .cursor(Cursor.Crosshair)
        )
        keyframes(flicker) {
            from(Modifier().opacity(1f))
            frame(50, Modifier().opacity(0.98f))
            to(Modifier().opacity(1f))
        }
        rule(
            StyleSelector.Universal.pseudoElement(StylePseudoElement.WebkitScrollbar),
            Modifier().width(8.px).height(8.px)
        )
        rule(
            StyleSelector.Universal.pseudoElement(StylePseudoElement.WebkitScrollbarTrack),
            Modifier().backgroundColor(ScratchpadTheme.BG_SECONDARY)
        )
        rule(
            scrollbarThumb,
            Modifier().backgroundColor(ScratchpadTheme.TEXT_MUTED).borderRadius(0)
        )
        rule(
            scrollbarThumb.pseudoClass(StylePseudoClass.Hover),
            Modifier().backgroundColor(ScratchpadTheme.TEXT_SECONDARY)
        )
        rule(
            StyleSelector.Universal.pseudoElement(StylePseudoElement.Selection),
            Modifier()
                .backgroundColor(ScratchpadTheme.TEXT_PRIMARY)
                .color(ScratchpadTheme.BG_PRIMARY)
        )
    }

    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.vw)
            .height(100.vh)
            .backgroundColor(ScratchpadTheme.BG_PRIMARY)
            .overflow(Overflow.Hidden)
            .className("scratchpad-container")
            .animation(
                name = flicker,
                duration = AnimationDuration.Fast,
                iterationCount = AnimationIterationCount.Infinite
            )
            .before {
                position(Position.Fixed)
                    .inset(0)
                    .width(100.percent)
                    .height(100.percent)
                    .backgroundLayers {
                        linearGradient(repeating = true) {
                            angle(0)
                            colorStop("transparent", "0")
                            colorStop("transparent", "2px")
                            colorStop("rgba(0, 255, 0, 0.03)", "2px")
                            colorStop("rgba(0, 255, 0, 0.03)", "4px")
                        }
                    }
                    .pointerEvents(PointerEvents.None)
                    .zIndex(10000)
            }
    ) {
        content()
    }
}
