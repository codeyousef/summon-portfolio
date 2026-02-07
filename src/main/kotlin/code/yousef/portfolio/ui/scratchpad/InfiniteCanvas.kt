package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

/**
 * Infinite canvas that can be panned and zoomed.
 * Uses CSS transforms controlled by JavaScript.
 */
@Composable
fun InfiniteCanvas(
    canvasId: String = "infinite-canvas",
    content: () -> Unit
) {
    GlobalStyle(
        css = """
        /* Infinite canvas styles */
        #$canvasId {
            transform-origin: 0 0;
            transition: none;
            will-change: transform;
        }

        /* Grid background pattern */
        #$canvasId::before {
            content: "";
            position: absolute;
            top: -5000px;
            left: -5000px;
            width: 10000px;
            height: 10000px;
            background-image:
                linear-gradient(${ScratchpadTheme.BORDER} 1px, transparent 1px),
                linear-gradient(90deg, ${ScratchpadTheme.BORDER} 1px, transparent 1px);
            background-size: 50px 50px;
            pointer-events: none;
            opacity: 0.3;
        }

        /* Grab cursor when hovering canvas */
        .canvas-wrapper {
            cursor: grab;
        }
        .canvas-wrapper:active {
            cursor: grabbing;
        }
    """
    )

    // Wrapper for handling pan/zoom events
    Box(
        modifier = Modifier()
            .position(Position.Absolute)
            .top(0.px)
            .left(0.px)
            .right(0.px)
            .bottom(0.px)
            .overflow(Overflow.Hidden)
            .className("canvas-wrapper")
            .id("canvas-wrapper")
    ) {
        // The actual transformable canvas
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .top(0.px)
                .left(0.px)
                .width(10000.px)
                .height(10000.px)
                .className("infinite-canvas")
                .id(canvasId)
        ) {
            content()
        }
    }
}
