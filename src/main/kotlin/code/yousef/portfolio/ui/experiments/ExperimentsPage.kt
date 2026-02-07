package code.yousef.portfolio.ui.experiments

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
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

object ExperimentsStrings {
    val pageTitle = LocalizedText(
        en = "Experiments",
        ar = "التجارب"
    )
    val pageSubtitle = LocalizedText(
        en = "Side quests, creative detours, and things that didn't fit anywhere else.",
        ar = "مهام جانبية، انعطافات إبداعية، وأشياء لم تجد مكانًا آخر."
    )
}

private data class ExperimentCard(
    val title: LocalizedText,
    val description: LocalizedText,
    val href: String,
    val emoji: String
)

private val experimentCards = listOf(
    ExperimentCard(
        title = LocalizedText("Art & Visual Work", "الفن والأعمال البصرية"),
        description = LocalizedText(
            "Digital explorations and visual experiments at the intersection of code and creativity.",
            "استكشافات رقمية وتجارب بصرية عند تقاطع الكود والإبداع."
        ),
        href = "/experiments/art",
        emoji = "\uD83C\uDFA8" // palette
    ),
    ExperimentCard(
        title = LocalizedText("Music & Audio", "الموسيقى والصوت"),
        description = LocalizedText(
            "Compositions, soundscapes, and audio experiments. From ambient explorations to orchestral arrangements.",
            "مؤلفات، مشاهد صوتية، وتجارب سمعية. من استكشافات محيطة إلى ترتيبات أوركسترالية."
        ),
        href = "/experiments/music",
        emoji = "\uD83C\uDFB5" // musical note
    ),
    ExperimentCard(
        title = LocalizedText("The Scratchpad", "المسودة"),
        description = LocalizedText(
            "Where ideas come to die. An infinite canvas of hot takes, dead projects, and questionable decisions.",
            "حيث تأتي الأفكار لتموت. لوحة لا نهائية من الآراء الحادة، المشاريع الميتة، والقرارات المشكوك فيها."
        ),
        href = "/experiments/scratchpad",
        emoji = "\uD83D\uDCA5" // collision
    )
)

@Composable
fun ExperimentsPage(locale: PortfolioLocale) {
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)

        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}

        // Hero
        SectionWrap {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = ExperimentsStrings.pageTitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(3.rem)
                        .fontWeight(800)
                        .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )

                Paragraph(
                    text = ExperimentsStrings.pageSubtitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(1.2.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.7)
                        .maxWidth(700)
                )
            }
        }

        // Cards grid
        SectionWrap {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                for (card in experimentCards) {
                    ExperimentCardComponent(card = card, locale = locale)
                }
            }
        }

        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
    }
}

@Composable
private fun ExperimentCardComponent(card: ExperimentCard, locale: PortfolioLocale) {
    Link(
        href = card.href,
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .padding(PortfolioTheme.Spacing.xl)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.md)
            .border("1px", "solid", PortfolioTheme.Colors.BORDER)
            .textDecoration(TextDecoration.None)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .flex(grow = 1, shrink = 1, basis = "280px")
            .maxWidth(400)
            .hover(
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                    .borderColor(PortfolioTheme.Colors.ACCENT)
                    .transform("translateY(-2px)")
            )
            .style("transition", "all 0.2s ease"),
        target = null
    ) {
        Text(
            text = card.emoji,
            modifier = Modifier().fontSize(2.rem)
        )
        Text(
            text = card.title.resolve(locale),
            modifier = Modifier()
                .fontSize(1.3.rem)
                .fontWeight(700)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        Paragraph(
            text = card.description.resolve(locale),
            modifier = Modifier()
                .fontSize(0.95.rem)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.6)
        )
    }
}
