package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.StyleAttribute
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*

@Composable
fun ContentSection(
    modifier: Modifier = Modifier(),
    surface: Boolean = true,
    content: () -> Unit
) {
    TypedStyleSheet {
        media(MediaQuery.MaxWidth(768)) {
            rule(
                StyleSelector.Universal.attribute(StyleAttribute.data("content-section"), "wrapper"),
                Modifier()
                    .padding(PortfolioTheme.Spacing.xs)
                    .width(100.percent)
                    .maxWidth(100.vw),
                priority = StyleRulePriority.Important,
            )
            rule(
                StyleSelector.Universal.attribute(StyleAttribute.data("content-section"), "inner"),
                Modifier().padding(PortfolioTheme.Spacing.md),
                priority = StyleRulePriority.Important,
            )
        }
    }
    
    val wrapperModifier = modifier
        .maxWidth(1200.px)
        .width("min(100%, calc(100vw - ${PortfolioTheme.Spacing.sm}))")
        .marginHorizontalAutoZero()
        .padding(PortfolioTheme.Spacing.xl)
        .dataAttribute("content-section", "wrapper")

    Box(modifier = wrapperModifier) {
        Column(
            modifier = Modifier()
                .backgroundColor(if (surface) PortfolioTheme.Colors.SURFACE else "transparent")
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.lg)
                .backdropBlur(20.px)
                .multipleShadows(
                    shadowConfig(
                        0,
                        30,
                        120,
                        0,
                        Color.hex("#02041873")
                    )
                )
                .dataAttribute("content-section", "inner")
        ) {
            content()
        }
    }
}
