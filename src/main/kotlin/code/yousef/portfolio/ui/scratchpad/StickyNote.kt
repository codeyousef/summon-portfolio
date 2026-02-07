package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

enum class StickyNoteColor(val bg: String, val text: String) {
    YELLOW("#ffeb3b", "#333333"),
    PINK("#ff69b4", "#ffffff"),
    GREEN("#00ff00", "#0a0a0a"),
    CYAN("#00ffff", "#0a0a0a"),
    ORANGE("#ff9800", "#333333"),
    PURPLE("#9c27b0", "#ffffff")
}

/**
 * A positioned sticky note with brutalist styling.
 * Can contain hot takes, thoughts, or any scattered content.
 */
@Composable
fun StickyNote(
    content: String,
    x: Int,
    y: Int,
    rotation: Float = 0f,
    color: StickyNoteColor = StickyNoteColor.YELLOW,
    maxWidth: Int = 200,
    title: String? = null
) {
    GlobalStyle(
        css = """
        .sticky-note {
            transition: transform 0.2s ease, box-shadow 0.2s ease;
            transform-origin: center center;
        }
        .sticky-note:hover {
            transform: rotate(0deg) scale(1.05) !important;
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.4);
            z-index: 100;
        }

        /* Tape effect at top */
        .sticky-note::before {
            content: "";
            position: absolute;
            top: -8px;
            left: 50%;
            transform: translateX(-50%);
            width: 40px;
            height: 16px;
            background: rgba(255, 255, 255, 0.5);
            border-radius: 2px;
        }
    """
    )

    Box(
        modifier = Modifier()
            .position(Position.Absolute)
            .left(x.px)
            .top(y.px)
            .width(maxWidth.px)
            .backgroundColor(color.bg)
            .padding(16.px)
            .paddingTop(24.px)
            .boxShadow("4px 4px 0 rgba(0, 0, 0, 0.3)")
            .transform("rotate(${rotation}deg)")
            .className("sticky-note")
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(8.px)
        ) {
            title?.let {
                Text(
                    text = it,
                    modifier = Modifier()
                        .fontSize(0.85.rem)
                        .fontWeight(700)
                        .color(color.text)
                        .textTransform(TextTransform.Uppercase)
                        .letterSpacing(0.05.rem)
                        .style("border-bottom", "1px dashed rgba(0,0,0,0.2)")
                        .paddingBottom(6.px)
                )
            }

            Text(
                text = content,
                modifier = Modifier()
                    .fontSize(0.9.rem)
                    .color(color.text)
                    .lineHeight(1.5)
                    .fontFamily(ScratchpadTheme.FONT_MONO)
            )
        }
    }
}
