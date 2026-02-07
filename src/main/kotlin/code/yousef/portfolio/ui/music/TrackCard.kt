package code.yousef.portfolio.ui.music

import code.yousef.portfolio.content.model.MusicTrack
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun TrackCard(
    track: MusicTrack,
    locale: PortfolioLocale
) {
    GlobalStyle(
        css = """
        .track-card {
            transition: transform ${PortfolioTheme.Motion.DEFAULT}, box-shadow ${PortfolioTheme.Motion.DEFAULT};
        }
        .track-card:hover {
            transform: translateY(-2px);
            box-shadow: ${PortfolioTheme.Shadows.MEDIUM};
        }
    """
    )

    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .overflow(Overflow.Hidden)
            .boxShadow(PortfolioTheme.Shadows.LOW)
            .className("track-card")
    ) {
        // Cover art and info row
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.lg)
                .padding(PortfolioTheme.Spacing.lg)
        ) {
            // Cover art
            Box(
                modifier = Modifier()
                    .width(120.px)
                    .height(120.px)
                    .flexShrink(0)
                    .borderRadius(PortfolioTheme.Radii.md)
                    .overflow(Overflow.Hidden)
                    .backgroundColor(PortfolioTheme.Colors.BACKGROUND_ALT)
            ) {
                if (track.coverArtUrl != null) {
                    Image(
                        src = track.coverArtUrl,
                        alt = track.title.resolve(locale),
                        modifier = Modifier()
                            .width(100.percent)
                            .height(100.percent)
                            .objectFit(ObjectFit.Cover)
                    )
                } else {
                    // Placeholder with music note
                    Box(
                        modifier = Modifier()
                            .width(100.percent)
                            .height(100.percent)
                            .display(Display.Flex)
                            .alignItems(AlignItems.Center)
                            .justifyContent(JustifyContent.Center)
                            .background(PortfolioTheme.Gradients.CARD)
                    ) {
                        Text(
                            text = "♪",
                            modifier = Modifier()
                                .fontSize(3.rem)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }

            // Track info
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flex(grow = 1, shrink = 1, basis = "0%")
            ) {
                // Title
                Text(
                    text = track.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.4.rem)
                        .fontWeight(700)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                // Genre and year
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.md)
                        .alignItems(AlignItems.Center)
                        .flexWrap(FlexWrap.Wrap)
                ) {
                    Box(
                        modifier = Modifier()
                            .backgroundColor(PortfolioTheme.Colors.ACCENT)
                            .color("#ffffff")
                            .fontSize(0.75.rem)
                            .fontWeight(600)
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .borderRadius(PortfolioTheme.Radii.sm)
                            .textTransform(TextTransform.Uppercase)
                    ) {
                        Text(text = track.genre.label.resolve(locale))
                    }

                    Text(
                        text = track.year.toString(),
                        modifier = Modifier()
                            .fontSize(0.9.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )

                    Text(
                        text = track.durationFormatted,
                        modifier = Modifier()
                            .fontSize(0.9.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                    )

                    track.bpm?.let { bpm ->
                        Text(
                            text = "$bpm BPM",
                            modifier = Modifier()
                                .fontSize(0.9.rem)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }

                // Description
                track.description?.let { desc ->
                    Text(
                        text = desc.resolve(locale),
                        modifier = Modifier()
                            .fontSize(0.95.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .lineHeight(1.6)
                    )
                }

                // Tags
                if (track.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.xs)
                            .flexWrap(FlexWrap.Wrap)
                    ) {
                        track.tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                modifier = Modifier()
                                    .fontSize(0.8.rem)
                                    .color(PortfolioTheme.Colors.LINK)
                            )
                        }
                    }
                }

                // External links
                if (track.externalLinks.isNotEmpty()) {
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.md)
                            .marginTop(PortfolioTheme.Spacing.xs)
                    ) {
                        track.externalLinks.forEach { (platform, url) ->
                            AnchorLink(
                                label = platform.replaceFirstChar { it.uppercase() },
                                href = url,
                                modifier = Modifier()
                                    .fontSize(0.85.rem)
                                    .color(PortfolioTheme.Colors.LINK)
                                    .textDecoration(TextDecoration.None)
                                    .hover(Modifier().textDecoration(TextDecoration.Underline)),
                                navigationMode = LinkNavigationMode.Native,
                                target = "_blank",
                                rel = "noopener noreferrer"
                            )
                        }
                    }
                }
            }
        }

        // Audio player section
        Box(
            modifier = Modifier()
                .padding(PortfolioTheme.Spacing.lg)
                .paddingTop(0.px)
        ) {
            AudioPlayer(
                audioUrl = track.audioUrl,
                trackId = track.id
            )
        }
    }
}
