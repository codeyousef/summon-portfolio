package code.yousef.portfolio.ui.fifthwall

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.GlobalNavigationDestination
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.BoxSizing
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.Overflow
import codes.yousef.summon.modifier.backgroundColor
import codes.yousef.summon.modifier.borderRadius
import codes.yousef.summon.modifier.boxSizing
import codes.yousef.summon.modifier.color
import codes.yousef.summon.modifier.height
import codes.yousef.summon.modifier.minHeight
import codes.yousef.summon.modifier.overflow
import codes.yousef.summon.modifier.width

@Composable
internal fun FifthWallPage(
    controller: FifthWallController,
    state: FifthWallUiState
) {
    val level = FifthWallLevels[state.levelIndex.coerceIn(FifthWallLevels.indices)]
    val focusedPackage = state.focusPackage()

    FifthWallScaffold {
        AppHeader(
            locale = PortfolioLocale.EN,
            modifier = Modifier()
                .boxSizing(BoxSizing.BorderBox)
                .color(FifthWallTheme.TEXT_PRIMARY),
            activeDestination = GlobalNavigationDestination.PROJECTS,
            compact = true,
        )
        Box(
            modifier = Modifier()
                .width(100.percent)
                .height("calc(100vh - 76px)")
                .minHeight(640.px)
                .backgroundColor(FifthWallTheme.BASE)
                .overflow(Overflow.Hidden)
                .borderRadius(8.px)
        ) {
            FifthWallScene(
                controller = controller,
                level = level,
                state = state,
                focusedPackage = focusedPackage
            )
        }
    }
}
