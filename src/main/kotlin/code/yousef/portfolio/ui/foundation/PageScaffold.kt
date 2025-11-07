package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.Modifier

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    val scaffoldModifier = modifier
        .style("min-height", "100vh")
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND)
        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        .style("font-family", PortfolioTheme.Typography.FONT_SANS)
        .style("position", "relative")
        .style("overflow", "hidden")
        .attribute("lang", locale.code)
        .attribute("dir", locale.direction)

    Box(modifier = scaffoldModifier) {
        GradientBackdrop()
        Column(
            modifier = Modifier()
                .style("position", "relative")
                .style("z-index", "2")
                .padding(PortfolioTheme.Spacing.xl)
                .style("gap", PortfolioTheme.Spacing.xl)
        ) {
            content()
        }
    }
}

@Composable
private fun GradientBackdrop() {
    Box(
        modifier = Modifier()
            .style("position", "absolute")
            .style("inset", "0")
            .style("background", PortfolioTheme.Gradients.HERO)
            .style("filter", "blur(90px) saturate(130%)")
            .style("opacity", "0.9")
            .style("z-index", "1")
    ) { }
}
