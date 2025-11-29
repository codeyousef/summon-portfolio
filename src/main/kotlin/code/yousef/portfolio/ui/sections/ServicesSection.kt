package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.TruncatedText
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun ServicesSection(
    services: List<Service>,
    locale: PortfolioLocale,
    onRequestServices: () -> Unit,
    modifier: Modifier = Modifier()
) {
    // Responsive grid styles
    GlobalStyle(
        """
        .services-bento-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: ${PortfolioTheme.Spacing.md};
            width: 100%;
        }
        @media (max-width: 900px) {
            .services-bento-grid {
                grid-template-columns: 1fr !important;
            }
        }
        """
    )

    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
                .width("100%")
                .overflow(Overflow.Hidden)
        ) {
            Text(
                text = ServicesCopy.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
            )
            Text(
                text = ServicesCopy.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
            )

            Box(
                modifier = Modifier()
                    .className("services-bento-grid")
            ) {
                services.forEach { service ->
                    ServiceCard(service = service, locale = locale)
                }
            }

            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.Center)
            ) {
                // Use a real link to /services for proper navigation
                codes.yousef.summon.components.navigation.ButtonLink(
                    label = ServicesCopy.openModal.resolve(locale),
                    href = "/services",
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
                    dataAttributes = mapOf("cta" to "view-featured-services"),
                    navigationMode = codes.yousef.summon.components.navigation.LinkNavigationMode.Native
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(service: Service, locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .padding(PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .boxShadow(PortfolioTheme.Shadows.LOW)
            .height("100%") // Equal height cards
    ) {
        Text(
            text = service.title.resolve(locale),
            modifier = Modifier()
                .fontSize(1.2.rem)
                .fontWeight(600)
        )
        TruncatedText(
            text = service.description.resolve(locale),
            maxLines = 3,
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.6)
        )
    }
}

private object ServicesCopy {
    val title = LocalizedText("Services", "الخدمات")
    val subtitle = LocalizedText(
        en = "Bridging imagination with implementation. Each engagement spans research, architecture, and product polish.",
        ar = "أجسر الخيال بالتنفيذ — من البحث إلى البنية إلى الصقل النهائي."
    )
    val openModal = LocalizedText("View featured services", "عرض الخدمات المميزة")
}
