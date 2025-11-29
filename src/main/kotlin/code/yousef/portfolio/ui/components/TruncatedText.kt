package code.yousef.portfolio.ui.components

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.modifier.*

/**
 * Text component that truncates content with ellipsis after a specified number of lines.
 * Uses CSS line-clamp for multi-line text truncation.
 */
@Composable
fun TruncatedText(
    text: String,
    maxLines: Int = 3,
    modifier: Modifier = Modifier()
) {
    Text(
        text = text,
        modifier = modifier
            .style("display", "-webkit-box")
            .style("-webkit-line-clamp", maxLines.toString())
            .style("-webkit-box-orient", "vertical")
            .style("overflow", "hidden")
            .style("text-overflow", "ellipsis")
    )
}
