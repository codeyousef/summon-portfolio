package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.core.style.Color
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun ContentSection(
    modifier: Modifier = Modifier(),
    surface: Boolean = true,
    content: () -> Unit
) {
    ContentSectionStyles()
    val wrapperModifier = modifier
        .maxWidth(1200.px)
        .width(100.percent)
        .marginHorizontalAutoZero()
        .padding(PortfolioTheme.Spacing.xl)
        .attribute("class", "content-section")

    Box(modifier = wrapperModifier) {
        Column(
            modifier = Modifier()
                .attribute("class", "content-section__inner")
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
        ) {
            content()
        }
    }
}

@Composable
private fun ContentSectionStyles() {
    val mobileInset = PortfolioTheme.Spacing.sm
    RawHtml(
        """
        <style>
          @media (max-width: 768px) {
            .content-section {
              max-width: none !important;
              width: calc(100vw - $mobileInset) !important;
              margin-left: calc(50% - 50vw + $mobileInset) !important;
              margin-right: calc(50% - 50vw) !important;
              padding: ${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.md} !important;
            }
            .content-section__inner {
              padding: ${PortfolioTheme.Spacing.md} !important;
              border-radius: 0;
            }
          }
        </style>
        """.trimIndent()
    )
}
