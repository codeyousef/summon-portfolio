package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun ServicesSection(
    services: List<Service>,
    locale: PortfolioLocale,
    onRequestServices: () -> Unit,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
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

            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .gridTemplateColumns("repeat(auto-fit, minmax(220px, 1fr))")
                    .gap(PortfolioTheme.Spacing.md)
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
                Button(
                    onClick = { onRequestServices() },
                    label = ServicesCopy.openModal.resolve(locale),
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .fontWeight(600),
                    variant = ButtonVariant.PRIMARY,
                    disabled = false
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
    ) {
        Text(
            text = service.title.resolve(locale),
            modifier = Modifier()
                .fontSize(1.2.rem)
                .fontWeight(600)
        )
        Text(
            text = service.description.resolve(locale),
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
