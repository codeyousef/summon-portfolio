package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.percent
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
    title: String? = null,
    noteId: String = ""
) {
    val stickyNote = StyleSelector.className("sticky-note")
    TypedStyleSheet {
        rule(
            stickyNote,
            Modifier()
                .transition("transform 0.2s ease, box-shadow 0.2s ease")
                .cursor(Cursor.Grab)
        )
        rule(
            stickyNote.pseudoClass(StylePseudoClass.Hover),
            Modifier()
                .transform(
                    TransformFunction.Rotate to "0deg",
                    TransformFunction.Scale to "1.05"
                )
                .boxShadow("0 8px 20px rgba(0, 0, 0, 0.4)")
                .zIndex(100),
            StyleRulePriority.Important
        )
    }

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
            .transform(TransformFunction.Rotate to "${rotation}deg")
            .className("sticky-note")
            .let { if (noteId.isNotEmpty()) it.dataAttribute("note-id", noteId) else it }
            .before {
                position(Position.Absolute)
                    .top((-8).px)
                    .left(50.percent)
                    .transform(TransformFunction.TranslateX to "-50%")
                    .width(40.px)
                    .height(16.px)
                    .backgroundColor("rgba(255, 255, 255, 0.5)")
                    .borderRadius(2.px)
            }
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
                        .border(BorderSide.Bottom, 1, BorderStyle.Dashed, "rgba(0,0,0,0.2)")
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
