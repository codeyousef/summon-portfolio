package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.modifier.Modifier

@Composable
fun ServicesSection(
    services: List<Service>,
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val pathPrefix = locale.pathPrefix()
    val modalHref = if (pathPrefix.isEmpty()) "?modal=services#services" else "$pathPrefix?modal=services#services"

    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = ServicesCopy.title.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "2.5rem")
                    .style("font-weight", "700")
            )
            Text(
                text = ServicesCopy.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.textSecondary)
                    .style("line-height", "1.8")
            )

            Row(
                modifier = Modifier()
                    .style("display", "grid")
                    .style("grid-template-columns", "repeat(auto-fit, minmax(220px, 1fr))")
                    .style("gap", PortfolioTheme.Spacing.md)
            ) {
                services.forEach { service ->
                    ServiceCard(service = service, locale = locale)
                }
            }

            Row(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("justify-content", "center")
            ) {
                ButtonLink(
                    ServicesCopy.openModal.resolve(locale),
                    modalHref,
                    Modifier()
                        .backgroundColor(PortfolioTheme.Colors.accent)
                        .color("#ffffff")
                        .style("padding", "${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.xl}")
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .style("font-weight", "600")
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(service: Service, locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .style("display", "flex")
            .style("flex-direction", "column")
            .style("gap", PortfolioTheme.Spacing.sm)
            .style("padding", PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.surface)
            .border("1px", "solid", PortfolioTheme.Colors.border)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("box-shadow", PortfolioTheme.Shadows.low)
    ) {
        Text(
            text = service.title.resolve(locale),
            modifier = Modifier()
                .style("font-size", "1.2rem")
                .style("font-weight", "600")
        )
        Text(
            text = service.description.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.textSecondary)
                .style("line-height", "1.6")
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
