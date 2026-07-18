package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.StyleAttribute
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.MediaQuery
import codes.yousef.summon.modifier.cssClamp
import codes.yousef.summon.modifier.cssMin
import codes.yousef.summon.modifier.dataAttribute
import codes.yousef.summon.modifier.gap
import codes.yousef.summon.modifier.marginHorizontalAutoZero
import codes.yousef.summon.modifier.maxWidth
import codes.yousef.summon.modifier.padding
import codes.yousef.summon.modifier.width

@Composable
fun SectionWrap(
    modifier: Modifier = Modifier(),
    maxWidthPx: Int = 1200,
    content: () -> Unit
) {
    TypedStyleSheet {
        media(MediaQuery.MaxWidth(768)) {
            rule(
                StyleSelector.Universal.attribute(StyleAttribute.data("section-wrap"), "true"),
                Modifier()
                    .padding(PortfolioTheme.Spacing.md)
                    .width(100.percent)
                    .maxWidth(100.vw),
                priority = StyleRulePriority.Important,
            )
        }
    }
    
    Column(
        modifier = modifier
            .width(cssMin(maxWidthPx.px, 92.vw))
            .marginHorizontalAutoZero()
            .padding("${cssClamp(22.px, 4.vw, 48.px)} ${cssClamp(16.px, 6.vw, 48.px)}")
            .gap(PortfolioTheme.Spacing.lg)
            .dataAttribute("section-wrap", "true")
    ) {
        content()
    }
}
