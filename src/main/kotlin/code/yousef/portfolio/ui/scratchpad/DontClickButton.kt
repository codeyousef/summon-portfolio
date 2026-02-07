package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

/**
 * A button that spawns colorful bouncing balls when clicked.
 * Uses physics simulation in scratchpad-physics.js
 */
@Composable
fun DontClickButton(
    x: Int,
    y: Int
) {
    GlobalStyle(
        css = """
        .dont-click-btn {
            transition: all 0.15s ease;
            animation: pulse-warning 1.5s infinite;
        }

        @keyframes pulse-warning {
            0%, 100% {
                box-shadow: 0 0 0 0 rgba(255, 0, 0, 0.7);
            }
            50% {
                box-shadow: 0 0 0 10px rgba(255, 0, 0, 0);
            }
        }

        .dont-click-btn:hover {
            transform: scale(1.1);
            background: ${ScratchpadTheme.DANGER} !important;
        }

        .dont-click-btn:active {
            transform: scale(0.95);
        }
    """
    )

    Box(
        modifier = Modifier()
            .position(Position.Absolute)
            .left(x.px)
            .top(y.px)
            .backgroundColor(ScratchpadTheme.BG_SURFACE)
            .borderWidth(3)
            .borderStyle(BorderStyle.Solid)
            .borderColor(ScratchpadTheme.DANGER)
            .padding(16.px, 24.px)
            .cursor("pointer")
            .className("dont-click-btn")
            .id("dont-click-btn")
    ) {
        Text(
            text = "[ DON'T CLICK ]",
            modifier = Modifier()
                .fontSize(1.rem)
                .fontWeight(700)
                .color(ScratchpadTheme.DANGER)
                .letterSpacing(0.1.rem)
                .textTransform(TextTransform.Uppercase)
                .fontFamily(ScratchpadTheme.FONT_MONO)
        )
    }

    // Canvas for physics balls overlay
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .right(0.px)
            .bottom(0.px)
            .pointerEvents(PointerEvents.None)
            .zIndex(9000)
            .id("physics-canvas-container")
    ) {
        // Canvas will be injected by JavaScript
    }
}
