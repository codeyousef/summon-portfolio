package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.modifier.*

/**
 * Responsive split view layout.
 * Desktop: Side-by-side (50/50)
 * Mobile (<768px): Stacked vertically
 * Supports RTL layouts and order reversal.
 */
@Composable
fun SplitView(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    reverseOrder: Boolean = false,
    locale: PortfolioLocale? = null,
    gapValue: String = PortfolioTheme.Spacing.xl,
    modifier: Modifier = Modifier()
) {
    TypedStyleSheet {
        val splitView = StyleSelector.className("split-view")
        val pane = StyleSelector.className("split-view-pane")
        rule(splitView, Modifier().display(Display.Flex).flexDirection(FlexDirection.Row))
        rule(pane, Modifier().flex(grow = 1, shrink = 1, basis = "0%").minWidth(0))
        media(MediaQuery.MaxWidth(768)) {
            rule(splitView, Modifier().flexDirection(FlexDirection.Column), StyleRulePriority.Important)
            rule(pane, Modifier().width(100.percent))
        }
    }

    // Determine actual direction based on RTL and reverseOrder
    val isRtl = locale?.direction == "rtl"
    val shouldReverse = if (isRtl) !reverseOrder else reverseOrder
    val direction = if (shouldReverse) FlexDirection.RowReverse else FlexDirection.Row

    Row(
        modifier = modifier
            .flexDirection(direction)
            .gap(gapValue)
            .alignItems(AlignItems.Stretch)
            .className("split-view")
    ) {
        Box(modifier = Modifier().className("split-view-pane")) {
            left()
        }
        Box(modifier = Modifier().className("split-view-pane")) {
            right()
        }
    }
}
