package code.yousef.portfolio.ui.blog

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import java.time.format.DateTimeFormatter

@Composable
fun BlogDetailPage(
    post: BlogPost,
    locale: PortfolioLocale
) {
    val formatter = detailDateFormatter(locale)
    ContentSection(surface = false) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection("column")
                .gap(PortfolioTheme.Spacing.lg)

        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection("column")
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
                    Text(BlogDetailCopy.byLabel.resolve(locale) + " " + post.author)
                    if (post.featured) {
                        Text("•")
                        Text(BlogDetailCopy.featured.resolve(locale))
                    }
                }
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.xs)
                        .flexWrap("wrap")
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
                    .flexDirection("column")
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

            AnchorLink(
                href = blogListHref(locale),
                dataHref = blogListHref(locale),
                label = BlogDetailCopy.back.resolve(locale),
                dataAttributes = mapOf("blog-link" to "back"),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.ACCENT_ALT)
                    .fontWeight(600)
            )
        }
    }
}

@Composable
fun BlogNotFoundPage(locale: PortfolioLocale) {
    ContentSection(surface = false) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection("column")
                .gap(PortfolioTheme.Spacing.md)
                .textAlign(TextAlign.Center)
        ) {
            Text(
                text = BlogDetailCopy.notFoundTitle.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.rem)
                    .fontWeight(700)
            )
            Text(
                text = BlogDetailCopy.notFoundBody.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            AnchorLink(
                href = blogListHref(locale),
                dataHref = blogListHref(locale),
                label = BlogDetailCopy.back.resolve(locale),
                dataAttributes = mapOf("blog-link" to "not-found-back"),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.ACCENT_ALT)
                    .fontWeight(600)
            )
        }
    }
}

private object BlogDetailCopy {
    val byLabel = LocalizedText("By", "بواسطة")
    val featured = LocalizedText("Featured", "مميز")
    val back = LocalizedText("← Back to Blog", "← العودة إلى المدونة")
    val notFoundTitle = LocalizedText("Post not found", "المقال غير موجود")
    val notFoundBody = LocalizedText("Check other posts for more insights.", "اطلع على مقالات أخرى للمزيد من الأفكار.")
}

private fun detailDateFormatter(locale: PortfolioLocale): DateTimeFormatter =
    if (locale == PortfolioLocale.AR)
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    else
        DateTimeFormatter.ofPattern("MMMM d, yyyy")
