package code.yousef.portfolio.ui.components

import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.ServicesStrings
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.feedback.Modal
import codes.yousef.summon.components.feedback.ModalSize
import codes.yousef.summon.components.feedback.ModalVariant
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

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
                .flexDirection(FlexDirection.Column)
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
                    text = ServicesStrings.Overlay.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.5.rem)
                        .fontWeight(700)
                )
                Button(
                    onClick = { onClose() },
                    label = ServicesStrings.Overlay.close.resolve(locale),
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
                text = ServicesStrings.Overlay.subtitle.resolve(locale),
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
                            .flexDirection(FlexDirection.Column)
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
                val ctaLabel = ServicesStrings.Overlay.cta.resolve(locale)
                ButtonLink(
                    label = ctaLabel,
                    href = contactHref,
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .fontWeight(600)
                        .lineHeight(1.0)
                        .whiteSpace(WhiteSpace.NoWrap),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = mapOf("cta" to "services-overlay"),
                    navigationMode = LinkNavigationMode.Native
                )
            }
        }
    }
}

