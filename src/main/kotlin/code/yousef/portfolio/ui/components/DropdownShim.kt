package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.EventModifiers.onClick
import codes.yousef.summon.modifier.LayoutModifiers.positionInset
import codes.yousef.summon.modifier.LayoutModifiers.top

enum class DropdownTriggerBehavior { HOVER, CLICK, BOTH }

@Composable
fun Dropdown(
    trigger: (toggle: () -> Unit) -> Unit,
    modifier: Modifier = Modifier(),
    triggerBehavior: DropdownTriggerBehavior = DropdownTriggerBehavior.CLICK,
    content: () -> Unit
) {
    val open = codes.yousef.summon.runtime.rememberMutableStateOf(false)
    val withClick = true // fallback to click-only toggle

    Box(
        modifier = modifier
            .position(Position.Relative)
    ) {
        val toggle = { open.value = !open.value }
        Box(
            modifier = Modifier()
                .cursor(Cursor.Pointer)
                .let { m -> if (withClick) m.onClick { toggle() } else m }
        ) { trigger(toggle) }
        if (open.value) {
            Box(
                modifier = Modifier()
                    .position(Position.Absolute)
                    .top(36.px)
                    .positionInset(left = "0")
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .backgroundColor(PortfolioTheme.Colors.SURFACE)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.md)
                    .padding(PortfolioTheme.Spacing.xs)
                    .zIndex(1000)
            ) { content() }
        }
    }
}

@Composable
fun DropdownItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier()
) {
    Button(
        onClick = onClick,
        label = label,
        modifier = modifier
            .display(Display.Block)
            .whiteSpace(WhiteSpace.NoWrap),
        variant = ButtonVariant.SECONDARY,
        disabled = false
    )
}

@Composable
fun DropdownDivider() {
    Box(
        modifier = Modifier()
            .height(1.px)
            .backgroundColor(PortfolioTheme.Colors.BORDER)
            .margin(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.xs)
    ) {}
}
