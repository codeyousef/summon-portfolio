package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.BlogStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.blog.blogDetailHref
import code.yousef.portfolio.ui.blog.blogListHref
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import java.time.format.DateTimeFormatter

@Composable
fun BlogTeaserSection(
    posts: List<BlogPost>,
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val formatter = if (locale == PortfolioLocale.AR) {
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    } else {
        DateTimeFormatter.ofPattern("MMMM d, yyyy")
    }
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = BlogStrings.Teaser.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
            )
            Text(
                text = BlogStrings.Teaser.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
            )

            val featured = posts.sortedByDescending { it.publishedAt }.take(2)
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                featured.forEach { post ->
                    val detailHref = blogDetailHref(locale, post.slug)
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .flexDirection(FlexDirection.Column)
                            .gap(PortfolioTheme.Spacing.xs)
                            .backgroundColor(PortfolioTheme.Colors.SURFACE)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.lg)
                            .padding(PortfolioTheme.Spacing.lg)
                    ) {
                        Text(
                            text = post.title,
                            modifier = Modifier()
                                .fontSize(1.5.rem)
                                .fontWeight(600)
                        )
                        Text(
                            text = formatter.format(post.publishedAt),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                        Text(
                            text = post.excerpt,
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .lineHeight(1.7)
                        )
                        val readMoreLabel = BlogStrings.Teaser.readMore.resolve(locale)
                        AnchorLink(
                            href = detailHref,
                            label = readMoreLabel,
                            dataAttributes = mapOf("blog-link" to post.slug),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.ACCENT_ALT)
                                .fontWeight(600),
                            dataHref = null
                        )
                    }
                }
            }

            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.FlexEnd)
            ) {
                val viewAllLabel = BlogStrings.Teaser.viewAll.resolve(locale)
                AnchorLink(
                    href = blogListHref(locale),
                    label = viewAllLabel,
                    dataAttributes = mapOf("blog-link" to "view-all"),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontWeight(600),
                    dataHref = null
                )
            }
        }
    }
}

