package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
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
    maxWidthPx: Int = 1200,
    content: () -> Unit
) {
    GlobalStyle(css = """
        /* Consistent mobile padding for all sections */
        @media (max-width: 768px) {
            [data-section-wrap="true"] {
                padding: ${PortfolioTheme.Spacing.sm} !important;
                width: calc(100vw - ${PortfolioTheme.Spacing.xs}) !important;
            }
        }
    """)
    
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
