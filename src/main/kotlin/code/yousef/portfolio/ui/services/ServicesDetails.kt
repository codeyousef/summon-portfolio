package code.yousef.portfolio.ui.services

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight

@Composable
fun ServiceDetailsList(
    services: List<Service>,
    locale: PortfolioLocale
) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            services.sortedBy { it.order }.forEach { service ->
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.sm)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .background(PortfolioTheme.Gradients.CARD)
                        .padding(PortfolioTheme.Spacing.xl)
                ) {
                    Text(
                        text = service.title.resolve(locale),
                        modifier = Modifier()
                            .fontSize(1.5.rem)
                            .fontWeight(700)
                    )
                    Paragraph(
                        text = service.description.resolve(locale),
                        modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .justifyContent(JustifyContent.FlexEnd)
                    ) {
                        ButtonLink(
                            label = "Get a proposal",
                            href = "#contact",
                            modifier = Modifier()
                                .backgroundColor(PortfolioTheme.Colors.ACCENT)
                                .color("#ffffff")
                                .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                                .borderRadius(PortfolioTheme.Radii.pill)
                                .fontWeight(600),
                            target = null,
                            rel = null,
                            title = null,
                            id = null,
                            ariaLabel = null,
                            ariaDescribedBy = null,
                            dataHref = null,
                            dataAttributes = mapOf("cta" to "service-proposal"),
                            navigationMode = LinkNavigationMode.Native
                        )
                    }
                }
            }
        }
    }
}
