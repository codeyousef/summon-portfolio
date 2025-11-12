package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.RichText
import code.yousef.summon.components.layout.Column
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap

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
            .alignItems(code.yousef.summon.modifier.AlignItems.Stretch)
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
