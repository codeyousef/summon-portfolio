package code.yousef.portfolio.ui.components

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.feedback.Modal
import code.yousef.summon.components.feedback.ModalSize
import code.yousef.summon.components.feedback.ModalVariant
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
fun ServicesOverlay(
    open: Boolean,
    services: List<Service>,
    locale: PortfolioLocale,
    contactHref: String,
    onClose: () -> Unit
) {
    Modal(
        isOpen = open,
        onDismiss = onClose,
        variant = ModalVariant.DEFAULT,
        size = ModalSize.LARGE,
        dismissOnBackdropClick = true,
        showCloseButton = true
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection("column")
                .gap(PortfolioTheme.Spacing.lg)
                .padding(PortfolioTheme.Spacing.xl)
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .alignItems(AlignItems.Center)
            ) {
                Text(
                    text = ServicesOverlayCopy.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.5.rem)
                        .fontWeight(700)
                )
                Button(
                    onClick = { onClose() },
                    label = ServicesOverlayCopy.close.resolve(locale),
                    modifier = Modifier()
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontWeight(600)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm),
                    variant = ButtonVariant.SECONDARY,
                    disabled = false
                )
            }

            Text(
                text = ServicesOverlayCopy.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
            )

            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .gridTemplateColumns("repeat(auto-fit, minmax(240px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                services.filter { it.featured }.forEach { service ->
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .flexDirection("column")
                            .gap(PortfolioTheme.Spacing.sm)
                            .backgroundColor(PortfolioTheme.Colors.SURFACE)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.lg)
                            .padding(PortfolioTheme.Spacing.lg)
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
            }

            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.FlexEnd)
            ) {
                Button(
                    onClick = null,
                    label = ServicesOverlayCopy.cta.resolve(locale),
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .fontWeight(600)
                        .lineHeight(1.0)
                        .style("white-space", "nowrap")
                        .attribute("type", "button")
                        .attribute("data-href", contactHref),
                    variant = ButtonVariant.PRIMARY,
                    disabled = false,
                    dataAttributes = mapOf("cta" to "services-overlay")
                )
            }
        }
    }
}

private object ServicesOverlayCopy {
    val title = LocalizedText("Services", "الخدمات")
    val subtitle = LocalizedText(
        en = "I help teams build foundational, high-performance products—covering research, architecture, and craft.",
        ar = "أساعد الفرق على بناء منتجات عالية الأداء عبر البحث والبنية والصقل."
    )
    val close = LocalizedText("Close", "إغلاق")
    val cta = LocalizedText("Contact me", "تواصل معي")
}
