package code.yousef.portfolio.ui.art

import code.yousef.portfolio.content.model.Artwork
import code.yousef.portfolio.content.seed.ArtworkSeed
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
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.runtime.rememberMutableStateOf

object ArtStrings {
    val pageTitle = LocalizedText(
        en = "Art & Visual Work",
        ar = "الفن والأعمال البصرية"
    )
    val pageSubtitle = LocalizedText(
        en = "Digital explorations, procedural generations, and visual experiments at the intersection of code and creativity.",
        ar = "استكشافات رقمية، توليد إجرائي، وتجارب بصرية عند تقاطع الكود والإبداع."
    )
    val galleryTitle = LocalizedText(
        en = "Gallery",
        ar = "المعرض"
    )
}

@Composable
fun ArtPage(
    artworks: List<Artwork> = ArtworkSeed.artworks,
    locale: PortfolioLocale
) {
    val selectedArtwork = rememberMutableStateOf<Artwork?>(null)

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
                    text = ArtStrings.pageTitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(3.rem)
                        .fontWeight(800)
                        .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                Paragraph(
                    text = ArtStrings.pageSubtitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.2.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.7)
                        .maxWidth(700)
                )
            }
        }

        // Gallery section
        SectionWrap {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.xl)
            ) {
                Text(
                    text = ArtStrings.galleryTitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.5.rem)
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .textTransform(TextTransform.Uppercase)
                        .letterSpacing(0.1.rem)
                )

                ArtworkGrid(
                    artworks = artworks,
                    locale = locale,
                    onArtworkClick = { artwork ->
                        selectedArtwork.value = artwork
                    }
                )
            }
        }

        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)

        // Lightbox overlay
        LightboxModal(
            artwork = selectedArtwork.value,
            locale = locale,
            onClose = { selectedArtwork.value = null }
        )
    }
}
