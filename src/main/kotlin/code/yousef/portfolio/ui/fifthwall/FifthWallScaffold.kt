package code.yousef.portfolio.ui.fifthwall

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.modifier.*

@Composable
fun FifthWallScaffold(
    content: () -> Unit
) {
    Box(
        modifier = Modifier()
            .position(Position.Relative)
            .width(100.percent)
            .minHeight(100.vh)
            .backgroundColor(FifthWallTheme.BASE)
            .overflow(Overflow.Hidden)
    ) {
        Box(
            modifier = Modifier()
                .width(100.percent)
                .minHeight(100.vh)
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
        ) {
            content()
        }
    }
}
