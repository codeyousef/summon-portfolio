package code.yousef.portfolio.ui.art

import code.yousef.portfolio.content.model.Artwork
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun ArtworkGrid(
    artworks: List<Artwork>,
    locale: PortfolioLocale,
    onArtworkClick: (Artwork) -> Unit
) {
    TypedStyleSheet {
        val card = StyleSelector.className("artwork-card")
        val overlay = StyleSelector.className("artwork-overlay")
        rule(
            card,
            Modifier().transition(
                property = TransitionProperty.All,
                duration = 200,
                timingFunction = TransitionTimingFunction.Ease,
            ),
        )
        rule(
            card.pseudoClass(StylePseudoClass.Hover),
            Modifier()
                .transform(
                    TransformFunction.TranslateY to "-4px",
                    TransformFunction.Scale to "1.02",
                )
                .boxShadow(PortfolioTheme.Shadows.MEDIUM),
        )
        rule(card.pseudoClass(StylePseudoClass.Hover).descendant(overlay), Modifier().opacity(1f))
        rule(
            overlay,
            Modifier()
                .opacity(0f)
                .transition(TransitionProperty.Opacity, duration = 200, timingFunction = TransitionTimingFunction.Ease),
        )
        media(MediaQuery.MaxWidth(768)) {
            rule(
                StyleSelector.className("artwork-grid"),
                Modifier().gridTemplateColumns(
                    gridAutoFill(gridMinMax(gridTrack(280.px), gridFraction())),
                ),
                priority = codes.yousef.summon.components.styles.StyleRulePriority.Important,
            )
        }
    }

    Row(
        modifier = Modifier()
            .display(Display.Grid)
            .gridTemplateColumns(gridAutoFill(gridMinMax(gridTrack(320.px), gridFraction())))
            .gap(PortfolioTheme.Spacing.lg)
            .width(100.percent)
            .className("artwork-grid")
    ) {
        artworks.sortedBy { it.order }.forEach { artwork ->
            ArtworkCard(
                artwork = artwork,
                locale = locale,
                onClick = { onArtworkClick(artwork) }
            )
        }
    }
}

@Composable
private fun ArtworkCard(
    artwork: Artwork,
    locale: PortfolioLocale,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier()
            .position(Position.Relative)
            .borderRadius(PortfolioTheme.Radii.md)
            .overflow(Overflow.Hidden)
            .cursor("pointer")
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .boxShadow(PortfolioTheme.Shadows.LOW)
            .className("artwork-card")
            .onClick { onClick() }
    ) {
        // Image
        Image(
            src = artwork.thumbnail,
            alt = artwork.title.resolve(locale),
            modifier = Modifier()
                .width(100.percent)
                .height(280.px)
                .objectFit(ObjectFit.Cover)
        )

        // Hover overlay with info
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .top(0.px)
                .left(0.px)
                .right(0.px)
                .bottom(0.px)
                .background("linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.3) 50%, transparent 100%)")
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .justifyContent(JustifyContent.FlexEnd)
                .padding(PortfolioTheme.Spacing.lg)
                .className("artwork-overlay")
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                Text(
                    text = artwork.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.2.rem)
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.sm)
                        .alignItems(AlignItems.Center)
                ) {
                    Text(
                        text = artwork.medium.label.resolve(locale),
                        modifier = Modifier()
                            .fontSize(0.85.rem)
                            .color(PortfolioTheme.Colors.ACCENT)
                            .fontWeight(500)
                    )
                    Text(
                        text = artwork.year.toString(),
                        modifier = Modifier()
                            .fontSize(0.85.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
        }

        // Featured badge
        if (artwork.featured) {
            Box(
                modifier = Modifier()
                    .position(Position.Absolute)
                    .top(PortfolioTheme.Spacing.sm)
                    .right(PortfolioTheme.Spacing.sm)
                    .backgroundColor(PortfolioTheme.Colors.ACCENT)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .fontSize(0.7.rem)
                    .fontWeight(600)
                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                    .borderRadius(PortfolioTheme.Radii.sm)
                    .textTransform(TextTransform.Uppercase)
                    .letterSpacing(0.05.rem)
            ) {
                Text(text = "Featured")
            }
        }
    }
}
