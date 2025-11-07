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
import code.yousef.summon.components.input.IconPosition
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.modifier.*

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
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.lg)
                .style("padding", PortfolioTheme.Spacing.xl)
        ) {
            Row(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("justify-content", "space-between")
                    .style("align-items", "center")
            ) {
                Text(
                    text = ServicesOverlayCopy.title.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "2.5rem")
                        .style("font-weight", "700")
                )
                Button(
                    onClick = { onClose() },
                    label = ServicesOverlayCopy.close.resolve(locale),
                    modifier = Modifier()
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.textSecondary)
                        .style("font-weight", "600")
                        .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}"),
                    variant = ButtonVariant.SECONDARY,
                    false,
                    "",
                    IconPosition.START
                )
            }

            Text(
                text = ServicesOverlayCopy.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.textSecondary)
                    .style("line-height", "1.8")
            )

            Row(
                modifier = Modifier()
                    .style("display", "grid")
                    .style("grid-template-columns", "repeat(auto-fit, minmax(240px, 1fr))")
                    .style("gap", PortfolioTheme.Spacing.lg)
            ) {
                services.filter { it.featured }.forEach { service ->
                    Column(
                        modifier = Modifier()
                            .style("display", "flex")
                            .style("flex-direction", "column")
                            .style("gap", PortfolioTheme.Spacing.sm)
                            .backgroundColor(PortfolioTheme.Colors.surface)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.border)
                            .borderRadius(PortfolioTheme.Radii.lg)
                            .style("padding", PortfolioTheme.Spacing.lg)
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
            }

            Row(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("justify-content", "flex-end")
            ) {
                ButtonLink(
                    ServicesOverlayCopy.cta.resolve(locale),
                    contactHref,
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

private object ServicesOverlayCopy {
    val title = LocalizedText("Services", "الخدمات")
    val subtitle = LocalizedText(
        en = "I help teams build foundational, high-performance products—covering research, architecture, and craft.",
        ar = "أساعد الفرق على بناء منتجات عالية الأداء عبر البحث والبنية والصقل."
    )
    val close = LocalizedText("Close", "إغلاق")
    val cta = LocalizedText("Contact me", "تواصل معي")
}
