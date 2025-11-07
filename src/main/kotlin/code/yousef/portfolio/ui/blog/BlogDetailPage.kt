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
import code.yousef.summon.components.navigation.Link
import code.yousef.summon.modifier.*
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
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.lg)
        ) {
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.xs)
            ) {
                Text(
                    text = post.title.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "3rem")
                        .style("font-weight", "700")
                )
                Row(
                    modifier = Modifier()
                        .style("display", "flex")
                        .style("gap", PortfolioTheme.Spacing.sm)
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
                        .style("display", "flex")
                        .style("gap", PortfolioTheme.Spacing.xs)
                        .style("flex-wrap", "wrap")
                ) {
                    post.tags.forEach { tag ->
                        Text(
                            text = tag,
                            modifier = Modifier()
                                .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
                                .borderWidth(1)
                                .borderStyle(BorderStyle.Solid)
                                .borderColor(PortfolioTheme.Colors.BORDER)
                                .borderRadius(PortfolioTheme.Radii.pill)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .style("font-size", "0.75rem")
                        )
                    }
                }
            }

            val paragraphs = post.content.resolve(locale).split("\n\n")
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.md)
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph.trim(),
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .style("line-height", "1.8")
                    )
                }
            }

            Link(
                BlogDetailCopy.back.resolve(locale),
                Modifier()
                    .color(PortfolioTheme.Colors.ACCENT_ALT)
                    .style("font-weight", "600"),
                blogListHref(locale),
                "_self",
                "",
                false,
                false,
                "",
                "",
                {}
            )
        }
    }
}

@Composable
fun BlogNotFoundPage(locale: PortfolioLocale) {
    ContentSection(surface = false) {
        Column(
            modifier = Modifier()
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.md)
                .style("text-align", "center")
        ) {
            Text(
                text = BlogDetailCopy.notFoundTitle.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "2rem")
                    .style("font-weight", "700")
            )
            Text(
                text = BlogDetailCopy.notFoundBody.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Link(
                BlogDetailCopy.back.resolve(locale),
                Modifier()
                    .color(PortfolioTheme.Colors.ACCENT_ALT)
                    .style("font-weight", "600"),
                blogListHref(locale),
                "_self",
                "",
                false,
                false,
                "",
                "",
                {}
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
