package code.yousef.portfolio.ui.music

import code.yousef.portfolio.content.model.MusicTrack
import code.yousef.portfolio.content.seed.MusicSeed
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

object MusicStrings {
    val pageTitle = LocalizedText(
        en = "Music & Audio",
        ar = "الموسيقى والصوت"
    )
    val pageSubtitle = LocalizedText(
        en = "Compositions, soundscapes, and audio experiments. From ambient explorations to orchestral arrangements.",
        ar = "مؤلفات، مشاهد صوتية، وتجارب سمعية. من استكشافات محيطة إلى ترتيبات أوركسترالية."
    )
    val tracksTitle = LocalizedText(
        en = "Tracks",
        ar = "المقطوعات"
    )
    val featuredTitle = LocalizedText(
        en = "Featured",
        ar = "مميز"
    )
}

@Composable
fun MusicPage(
    tracks: List<MusicTrack> = MusicSeed.tracks,
    locale: PortfolioLocale
) {
    val featuredTracks = tracks.filter { it.featured }.sortedBy { it.order }
    val otherTracks = tracks.filter { !it.featured }.sortedBy { it.order }

    GlobalStyle(
        css = """
        @media (max-width: 768px) {
            .music-tracks-grid {
                gap: ${PortfolioTheme.Spacing.md} !important;
            }
        }
    """
    )

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)

        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}

        // Hero section
        SectionWrap {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = MusicStrings.pageTitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(3.rem)
                        .fontWeight(800)
                        .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                Paragraph(
                    text = MusicStrings.pageSubtitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.2.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.7)
                        .maxWidth(700)
                )
            }
        }

        // Featured tracks section
        if (featuredTracks.isNotEmpty()) {
            SectionWrap {
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.xl)
                ) {
                    Text(
                        text = MusicStrings.featuredTitle.resolve(locale),
                        modifier = Modifier()
                            .fontSize(1.5.rem)
                            .fontWeight(600)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .textTransform(TextTransform.Uppercase)
                            .letterSpacing(0.1.rem)
                    )

                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .flexDirection(FlexDirection.Column)
                            .gap(PortfolioTheme.Spacing.lg)
                            .width(100.percent)
                            .className("music-tracks-grid")
                    ) {
                        featuredTracks.forEach { track ->
                            TrackCard(track = track, locale = locale)
                        }
                    }
                }
            }
        }

        // Other tracks section
        if (otherTracks.isNotEmpty()) {
            SectionWrap {
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.xl)
                ) {
                    Text(
                        text = MusicStrings.tracksTitle.resolve(locale),
                        modifier = Modifier()
                            .fontSize(1.5.rem)
                            .fontWeight(600)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .textTransform(TextTransform.Uppercase)
                            .letterSpacing(0.1.rem)
                    )

                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .flexDirection(FlexDirection.Column)
                            .gap(PortfolioTheme.Spacing.lg)
                            .width(100.percent)
                            .className("music-tracks-grid")
                    ) {
                        otherTracks.forEach { track ->
                            TrackCard(track = track, locale = locale)
                        }
                    }
                }
            }
        }

        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
    }
}
