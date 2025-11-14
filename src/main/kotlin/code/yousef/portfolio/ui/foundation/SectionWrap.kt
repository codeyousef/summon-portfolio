package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.cssClamp
import codes.yousef.summon.modifier.cssMin
import codes.yousef.summon.modifier.marginHorizontalAutoZero

@Composable
fun SectionWrap(
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    Column(
        modifier = modifier
            .width(cssMin(1200.px, 92.vw))
            .marginHorizontalAutoZero()
            .padding("${cssClamp(22.px, 4.vw, 48.px)} ${cssClamp(16.px, 6.vw, 48.px)}")
            .gap(PortfolioTheme.Spacing.lg)
    ) {
        content()
    }
}
