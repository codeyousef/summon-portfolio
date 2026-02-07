package code.yousef.portfolio.ui.art

import code.yousef.portfolio.content.model.Artwork
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.*
import codes.yousef.summon.modifier.*

@Composable
fun LightboxModal(
    artwork: Artwork?,
    locale: PortfolioLocale,
    onClose: () -> Unit
) {
    if (artwork == null) return

    // Backdrop
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .right(0.px)
            .bottom(0.px)
            .backgroundColor("rgba(0, 0, 0, 0.95)")
            .zIndex(9999)
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .cursor("pointer")
            .onClick { onClose() }
    ) {
        // Content container - stop propagation
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
                .maxWidth(90.vw)
                .maxHeight(90.vh)
                .cursor("default")
                .onClick { /* Stop propagation */ }
        ) {
            // Close button
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .width(100.percent)
                    .justifyContent(JustifyContent.FlexEnd)
            ) {
                Button(
                    label = "Close",
                    onClick = { onClose() },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier()
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .fontSize(1.rem)
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.md)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.sm)
                        .hover(
                            Modifier()
                                .backgroundColor(PortfolioTheme.Colors.SURFACE)
                        )
                )
            }

            // Image
            Image(
                src = artwork.imageUrl,
                alt = artwork.title.resolve(locale),
                modifier = Modifier()
                    .maxWidth(85.vw)
                    .maxHeight(70.vh)
                    .objectFit(ObjectFit.Contain)
                    .borderRadius(PortfolioTheme.Radii.sm)
            )

            // Info panel
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.sm)
            ) {
                Text(
                    text = artwork.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.5.rem)
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.md)
                        .alignItems(AlignItems.Center)
                ) {
                    Text(
                        text = artwork.medium.label.resolve(locale),
                        modifier = Modifier()
                            .fontSize(0.9.rem)
                            .color(PortfolioTheme.Colors.ACCENT)
                    )
                    Text(
                        text = artwork.year.toString(),
                        modifier = Modifier()
                            .fontSize(0.9.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                    artwork.dimensions?.let { dims ->
                        Text(
                            text = dims,
                            modifier = Modifier()
                                .fontSize(0.9.rem)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }

                artwork.description?.let { desc ->
                    Text(
                        text = desc.resolve(locale),
                        modifier = Modifier()
                            .fontSize(1.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .textAlign(TextAlign.Center)
                            .maxWidth(600.px)
                            .lineHeight(1.6)
                    )
                }
            }
        }
    }
}
