package code.yousef.portfolio.ui.components

import codes.yousef.summon.action.UiAction
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import kotlinx.serialization.json.Json

@Composable
fun SafeHamburgerMenu(
    modifier: Modifier = Modifier(),
    menuContainerModifier: Modifier = Modifier(),
    menuId: String,
    menuContent: @Composable () -> Unit
) {
    val toggleAction = Json.encodeToString(
        UiAction.serializer(),
        UiAction.ToggleVisibility(menuId)
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier()
                .cursor(Cursor.Pointer)
                .padding(8.px)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .style("min-width", "40px")
                .style("min-height", "40px")
                .attribute("role", "button")
                .attribute("tabindex", "0")
                .attribute("aria-label", "Open menu")
                .attribute("aria-expanded", "false")
                .attribute("aria-controls", menuId)
                .attribute("data-test-id", "hamburger-button")
                .attribute("data-action", toggleAction)
                .attribute("data-hamburger-toggle", "true")
        ) {
            Column(
                modifier = Modifier()
                    .width(18.px)
                    .gap(4.px)
                    .alignItems(AlignItems.Center)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier()
                            .width(18.px)
                            .height(2.px)
                            .borderRadius(2.px)
                            .backgroundColor("currentColor")
                    ) {
                    }
                }
            }
        }
        Box(
            modifier = Modifier()
                .fillMaxWidth()
                .zIndex(1000)
                .then(menuContainerModifier)
                .attribute("id", menuId)
                .style("display", "none")
        ) {
            menuContent()
        }
    }
}
