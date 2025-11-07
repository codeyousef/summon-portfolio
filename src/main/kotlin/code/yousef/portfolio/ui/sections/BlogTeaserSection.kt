package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.blog.blogDetailHref
import code.yousef.portfolio.ui.blog.blogListHref
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.Link
import code.yousef.summon.modifier.*
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
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = BlogCopy.title.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "2.5rem")
                    .style("font-weight", "700")
            )
            Text(
                text = BlogCopy.subtitle.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.textSecondary)
                    .style("line-height", "1.8")
            )

            val featured = posts.sortedByDescending { it.publishedAt }.take(2)
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.md)
            ) {
                featured.forEach { post ->
                    val detailHref = blogDetailHref(locale, post.slug)
                    Column(
                        modifier = Modifier()
                            .style("display", "flex")
                            .style("flex-direction", "column")
                            .style("gap", PortfolioTheme.Spacing.xs)
                            .backgroundColor(PortfolioTheme.Colors.surface)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.border)
                            .borderRadius(PortfolioTheme.Radii.lg)
                            .style("padding", PortfolioTheme.Spacing.lg)
                    ) {
                        Text(
                            text = post.title.resolve(locale),
                            modifier = Modifier()
                                .style("font-size", "1.5rem")
                                .style("font-weight", "600")
                        )
                        Text(
                            text = formatter.format(post.publishedAt),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.textSecondary)
                        )
                        Text(
                            text = post.excerpt.resolve(locale),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.textSecondary)
                                .style("line-height", "1.7")
                        )
                        Link(
                            BlogCopy.readMore.resolve(locale),
                            Modifier()
                                .color(PortfolioTheme.Colors.accentAlt)
                                .style("font-weight", "600"),
                            detailHref,
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

            Row(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("justify-content", "flex-end")
            ) {
                Link(
                    BlogCopy.viewAll.resolve(locale),
                    Modifier()
                        .color(PortfolioTheme.Colors.textSecondary)
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
}

private object BlogCopy {
    val title = LocalizedText("Latest Writing", "أحدث المقالات")
    val subtitle = LocalizedText(
        en = "Deep dives on systems design, tools, and creative engineering. New essays ship as soon as they're battle-tested.",
        ar = "مقالات متعمقة حول تصميم الأنظمة والأدوات والهندسة الإبداعية."
    )
    val readMore = LocalizedText("Read more →", "اقرأ المزيد ←")
    val viewAll = LocalizedText("View all posts", "عرض جميع المقالات")
}
