package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun GlassPill(text: String, emphasize: Boolean = false, modifier: Modifier = Modifier()) {
    Text(
        text = text,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
            .background(
                if (emphasize) PortfolioTheme.Gradients.ACCENT else PortfolioTheme.Gradients.GLASS
            )
            .fontWeight(if (emphasize) 800 else 600)
            .fontSize(0.85.rem)
    )
}
