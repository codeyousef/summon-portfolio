package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.RichText
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun Prose(html: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.md)
            .alignItems(codes.yousef.summon.modifier.AlignItems.Stretch)
    ) {
        RichText(
            html,
            modifier = Modifier()
                .fontSize(1.rem)
                .lineHeight(1.7)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        )
    }
}
