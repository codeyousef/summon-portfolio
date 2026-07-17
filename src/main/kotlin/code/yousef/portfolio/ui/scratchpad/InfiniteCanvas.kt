package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

/**
 * A large native scroll canvas. It remains fully usable without client scripting.
 */
@Composable
fun InfiniteCanvas(
    canvasId: String = "infinite-canvas",
    content: () -> Unit
) {
    // Native scrolling keeps the canvas exploratory while requiring no browser script.
    Box(
        modifier = Modifier()
            .position(Position.Absolute)
            .top(0.px)
            .left(0.px)
            .right(0.px)
            .bottom(0.px)
            .overflow(Overflow.Auto)
            .cursor(Cursor.Grab)
            .backgroundLayers {
                linearGradient {
                    angle(0)
                    colorStop("rgba(51,51,51,0.3)", "1px")
                    colorStop("transparent", "1px")
                }
                linearGradient {
                    angle(90)
                    colorStop("rgba(51,51,51,0.3)", "1px")
                    colorStop("transparent", "1px")
                }
            }
            .backgroundSize("50px 50px")
            .backgroundPosition("0 0")
            .active(Modifier().cursor(Cursor.Grabbing))
            .className("canvas-wrapper")
            .id("canvas-wrapper")
    ) {
        // The actual scrollable canvas
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .top(0.px)
                .left(0.px)
                .width(1800.px)
                .height(1400.px)
                .className("infinite-canvas")
                .id(canvasId)
        ) {
            content()
        }
    }
}
