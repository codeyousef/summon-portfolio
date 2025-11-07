package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.minHeight

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    val scaffoldModifier = modifier
        .minHeight("100vh")
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND)
        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        .position(Position.Relative)
        .overflow(Overflow.Hidden)
        .attribute("lang", locale.code)
        .attribute("dir", locale.direction)

    Box(modifier = scaffoldModifier) {
        GradientBackdrop()
        Column(
            modifier = Modifier()
                .position(Position.Relative)
                .zIndex(2)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            content()
        }
    }
}

@Composable
private fun GradientBackdrop() {
    Box(
        modifier = Modifier()
            .position(Position.Absolute)
            .backgroundColor(PortfolioTheme.Gradients.HERO)
            .opacity(0.9F)
            .zIndex(1)
            .style("inset", "0")
            .style("filter", "blur(90px) saturate(130%)")
    ) { }
}
