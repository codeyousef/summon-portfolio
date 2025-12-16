package code.yousef.portfolio.ui.blog

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.BlogStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import java.time.format.DateTimeFormatter

@Composable
fun BlogDetailPage(
    post: BlogPost,
    locale: PortfolioLocale
) {
    val formatter = detailDateFormatter(locale)
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}
        ContentSection(surface = false) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.lg)

            ) {
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.xs)
                ) {
                    Text(
                        text = post.title.resolve(locale),
                        modifier = Modifier()
                            .fontSize(3.rem)
                            .fontWeight(700)
                    )
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.sm)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    ) {
                        Text(formatter.format(post.publishedAt))
                        Text("•")
                        Text(BlogStrings.Detail.byLabel.resolve(locale) + " " + post.author)
                        if (post.featured) {
                            Text("•")
                            Text(BlogStrings.Detail.featured.resolve(locale))
                        }
                    }
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.xs)
                            .flexWrap(FlexWrap.Wrap)
                    ) {
                        post.tags.forEach { tag ->
                            Text(
                                text = tag,
                                modifier = Modifier()
                                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                                    .borderWidth(1)
                                    .borderStyle(BorderStyle.Solid)
                                    .borderColor(PortfolioTheme.Colors.BORDER)
                                    .borderRadius(PortfolioTheme.Radii.pill)
                                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                    .fontSize(0.75.rem)
                            )
                        }
                    }
                }

                val paragraphs = post.content.resolve(locale).split("\n\n")
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.md)
                ) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph.trim(),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .lineHeight(1.8)
                        )
                    }
                }

                val backLabel = BlogStrings.Detail.back.resolve(locale)
                AnchorLink(
                    href = blogListHref(locale),
                    label = backLabel,
                    dataAttributes = mapOf("blog-link" to "back"),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.ACCENT_ALT)
                        .fontWeight(600),
                    dataHref = null
                )
            }
        }
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
    }
}

@Composable
fun BlogNotFoundPage(locale: PortfolioLocale) {
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}
        ContentSection(surface = false) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
                    .textAlign(TextAlign.Center)
            ) {
                Text(
                    text = BlogStrings.Detail.notFoundTitle.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.rem)
                        .fontWeight(700)
                )
                Text(
                    text = BlogStrings.Detail.notFoundBody.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
                val notFoundBackLabel = BlogStrings.Detail.back.resolve(locale)
                AnchorLink(
                    href = blogListHref(locale),
                    label = notFoundBackLabel,
                    dataAttributes = mapOf("blog-link" to "not-found-back"),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.ACCENT_ALT)
                        .fontWeight(600),
                    dataHref = null
                )
            }
        }
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
    }
}


private fun detailDateFormatter(locale: PortfolioLocale): DateTimeFormatter =
    if (locale == PortfolioLocale.AR)
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    else
        DateTimeFormatter.ofPattern("MMMM d, yyyy")
