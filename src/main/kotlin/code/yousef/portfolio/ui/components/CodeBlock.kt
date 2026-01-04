package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun CodeBlock(lines: List<String>, showCopyButton: Boolean = false) {
    Column(
        modifier = Modifier()
            .backgroundColor("#0b0d12")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#ffffff14")
            .borderRadius(PortfolioTheme.Radii.md)
            .padding(PortfolioTheme.Spacing.md)
            .fontWeight(FontWeight.Medium)
            .fontSize(14.px)
            .lineHeight(1.6)
            .fontFamily(PortfolioTheme.Typography.FONT_MONO)
            .overflow(Overflow.Auto)
    ) {
        if (showCopyButton) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.FlexEnd)
                    .marginBottom(PortfolioTheme.Spacing.sm)
            ) {
                Button(
                    onClick = null,
                    label = "Copy",
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor(PortfolioTheme.Gradients.GLASS)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill),
                    variant = ButtonVariant.SECONDARY,
                    disabled = false,
                    dataAttributes = mapOf("copy" to "code")
                )
            }
        }
        lines.forEach { line ->
            Text(
                text = line,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .whiteSpace(WhiteSpace.Pre)
            )
        }
    }
}
