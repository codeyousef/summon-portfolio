package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
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
    GlobalStyle(
        css = """
        /* Reset and base styles for scratchpad */
        html, body {
            margin: 0;
            padding: 0;
            overflow: hidden;
            background: ${ScratchpadTheme.BG_PRIMARY};
            font-family: ${ScratchpadTheme.FONT_MONO};
            color: ${ScratchpadTheme.TEXT_PRIMARY};
            cursor: crosshair;
        }

        /* Scanline effect */
        .scratchpad-container::before {
            content: "";
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: repeating-linear-gradient(
                0deg,
                transparent,
                transparent 2px,
                rgba(0, 255, 0, 0.03) 2px,
                rgba(0, 255, 0, 0.03) 4px
            );
            pointer-events: none;
            z-index: 10000;
        }

        /* CRT flicker effect (subtle) */
        @keyframes flicker {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.98; }
        }

        .scratchpad-container {
            animation: flicker 0.15s infinite;
        }

        /* Custom scrollbar */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: ${ScratchpadTheme.BG_SECONDARY};
        }
        ::-webkit-scrollbar-thumb {
            background: ${ScratchpadTheme.TEXT_MUTED};
            border-radius: 0;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: ${ScratchpadTheme.TEXT_SECONDARY};
        }

        /* Selection color */
        ::selection {
            background: ${ScratchpadTheme.TEXT_PRIMARY};
            color: ${ScratchpadTheme.BG_PRIMARY};
        }

        /* Disable text selection on canvas */
        .infinite-canvas {
            user-select: none;
        }
    """
    )

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
    ) {
        content()
    }
}
