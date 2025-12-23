package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.styles.GlobalStyle
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
    // Register responsive styles
    GlobalStyle(
        css = """
        .split-view {
            display: flex;
            flex-direction: row;
        }
        .split-view-pane {
            flex: 1;
            min-width: 0;
        }
        @media (max-width: 768px) {
            .split-view {
                flex-direction: column !important;
            }
            .split-view-pane {
                width: 100%;
            }
        }
        """
    )

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
