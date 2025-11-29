package code.yousef.portfolio.ui.components

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.modifier.Modifier

/**
 * Container that maintains a specific aspect ratio.
 * Uses CSS aspect-ratio property for consistent sizing.
 */
@Composable
fun AspectRatioBox(
    ratio: Double = 16.0 / 9.0,
    modifier: Modifier = Modifier(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .style("aspect-ratio", ratio.toString())
            .width("100%")
    ) {
        content()
    }
}
