package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.Modifier

@Composable
fun SectionWrap(
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    Column(
        modifier = modifier
            .style("width", "min(1200px, 92vw)")
            .style("margin", "0 auto")
            .style("padding", "clamp(22px, 4vw, 48px) 0")
            .gap(PortfolioTheme.Spacing.lg)
    ) {
        content()
    }
}
