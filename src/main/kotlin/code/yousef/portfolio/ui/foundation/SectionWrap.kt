package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Column
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.vw
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.Modifier
import code.yousef.summon.modifier.cssClamp
import code.yousef.summon.modifier.cssMin
import code.yousef.summon.modifier.marginHorizontalAutoZero

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
