package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.AnimationIterationCount
import codes.yousef.summon.components.styles.AnimationName
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.components.styles.animation
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

/**
 * A native link that asks the server to reveal a colorful ball burst.
 */
@Composable
fun DontClickButton(
    x: Int,
    y: Int
) {
    val pulse = AnimationName.named("scratchpad-pulse-warning")
    val button = StyleSelector.className("dont-click-btn")
    TypedStyleSheet {
        keyframes(pulse) {
            from(Modifier().boxShadow("0 0 0 0 rgba(255, 0, 0, 0.7)"))
            frame(50, Modifier().boxShadow("0 0 0 10px rgba(255, 0, 0, 0)"))
            to(Modifier().boxShadow("0 0 0 0 rgba(255, 0, 0, 0.7)"))
        }
        rule(
            button,
            Modifier()
                .transition(
                    property = TransitionProperty.All,
                    duration = 150,
                    timingFunction = TransitionTimingFunction.Ease,
                )
                .animation(
                    name = pulse,
                    duration = AnimationDuration.VerySlow,
                    iterationCount = AnimationIterationCount.Infinite
                )
        )
        rule(
            button.pseudoClass(StylePseudoClass.Hover),
            Modifier()
                .transform(TransformFunction.Scale to "1.1")
                .backgroundColor(ScratchpadTheme.DANGER),
            StyleRulePriority.Important
        )
        rule(
            button.pseudoClass(StylePseudoClass.Active),
            Modifier().transform(TransformFunction.Scale to "0.95"),
            StyleRulePriority.Important
        )
    }

    AnchorLink(
        label = "[ DON'T CLICK ]",
        href = "/scratchpad?command=spawn%20balls",
        modifier = Modifier()
            .position(Position.Absolute)
            .left(x.px)
            .top(y.px)
            .backgroundColor(ScratchpadTheme.BG_SURFACE)
            .borderWidth(3)
            .borderStyle(BorderStyle.Solid)
            .borderColor(ScratchpadTheme.DANGER)
            .padding(16.px, 24.px)
            .cursor(Cursor.Pointer)
            .className("dont-click-btn")
            .id("dont-click-btn")
            .fontSize(1.rem)
            .fontWeight(700)
            .color(ScratchpadTheme.DANGER)
            .letterSpacing(0.1.rem)
            .textTransform(TextTransform.Uppercase)
            .textDecoration(TextDecoration.None)
            .fontFamily(ScratchpadTheme.FONT_MONO),
        navigationMode = LinkNavigationMode.Native
    )
}
