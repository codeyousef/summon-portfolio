package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.modifier.*
import codes.yousef.summon.extensions.rem
import java.time.Year

@Composable
fun PortfolioFooter(locale: PortfolioLocale) {
    val currentYear = Year.now().value
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
                .paddingTop(PortfolioTheme.Spacing.xl)
                .paddingBottom(PortfolioTheme.Spacing.xl)
        ) {
            // Technical sign-off
            Text(
                text = "Architected in Kotlin. Rendered with Materia. Powered by Aether.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(0.9.rem)
            )
            
            // Social links
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.lg)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                FooterSocialLink(label = "X (Twitter)", href = "https://x.com/deepissuemassaj")
                FooterSocialLink(label = "GitHub", href = "https://github.com/codeyousef")
            }
            
            // Copyright
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = "© $currentYear Yousef.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                )
            }
        }
    }
}

@Composable
private fun FooterSocialLink(label: String, href: String) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            .textDecoration(TextDecoration.None)
            .fontSize(0.9.rem)
            .fontWeight(500)
            .hover(Modifier().color(PortfolioTheme.Colors.ACCENT).textDecoration(TextDecoration.Underline)),
        navigationMode = LinkNavigationMode.Native,
        target = "_blank",
        rel = "noopener noreferrer",
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("footer-social" to label.lowercase().replace(" ", "-"))
    )
}
