package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.alignItems
import codes.yousef.summon.modifier.LayoutModifiers.display
import codes.yousef.summon.modifier.LayoutModifiers.gap

/**
 * macOS-style window frame container with traffic light buttons.
 * Provides a visual container that resembles a desktop application window.
 */
@Composable
fun WindowFrame(
    title: String? = null,
    modifier: Modifier = Modifier(),
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
            .overflow(Overflow.Hidden)
    ) {
        // Header bar with traffic lights
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
                .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.md)
                .background(PortfolioTheme.Colors.SURFACE)
                .style("border-bottom", "1px solid ${PortfolioTheme.Colors.BORDER}")
        ) {
            // Traffic light dots
            TrafficLightDot("#ff5f56") // Red - close
            TrafficLightDot("#ffbd2e") // Yellow - minimize
            TrafficLightDot("#27c93f") // Green - maximize

            // Optional title
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.75.rem)
                        .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                        .flexGrow(1)
                        .textAlign(TextAlign.Center)
                        .marginRight(52) // Balance the traffic lights width
                )
            }
        }

        // Content area
        Box(
            modifier = Modifier()
                .flexGrow(1)
                .overflow(Overflow.Auto)
        ) {
            content()
        }
    }
}

@Composable
private fun TrafficLightDot(color: String) {
    Box(
        modifier = Modifier()
            .width(12.px)
            .height(12.px)
            .borderRadius(6.px)
            .background(color)
    ) {}
}
