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
import code.yousef.summon.modifier.Modifier
import java.time.format.DateTimeFormatter

@Composable
fun BlogListPage(
    posts: List<BlogPost>,
    locale: PortfolioLocale
) {
    val formatter = dateFormatter(locale)
    ContentSection(surface = false) {
        Column(
            modifier = Modifier()
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.sm)
            ) {
                Text(
                    text = BlogListCopy.title.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "3rem")
                        .style("font-weight", "700")
                )
                Text(
                    text = BlogListCopy.subtitle.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.textSecondary)
                        .style("line-height", "1.8")
                )
            }

            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.lg)
            ) {
                posts.sortedByDescending { it.publishedAt }.forEach { post ->
                    Column(
                        modifier = Modifier()
                            .style("display", "flex")
                            .style("flex-direction", "column")
                            .style("gap", PortfolioTheme.Spacing.xs)
                            .backgroundColor(PortfolioTheme.Colors.surface)
                            .border("1px", "solid", PortfolioTheme.Colors.border)
                            .borderRadius(PortfolioTheme.Radii.lg)
                            .style("padding", PortfolioTheme.Spacing.lg)
                    ) {
                        Text(
                            text = post.title.resolve(locale),
                            modifier = Modifier()
                                .style("font-size", "1.75rem")
                                .style("font-weight", "600")
                        )
                        Row(
                            modifier = Modifier()
                                .style("display", "flex")
                                .style("gap", PortfolioTheme.Spacing.sm)
                                .color(PortfolioTheme.Colors.textSecondary)
                        ) {
                            Text(formatter.format(post.publishedAt))
                            Text("•")
                            Text(BlogListCopy.byLabel.resolve(locale) + " " + post.author)
                        }
                        Text(
                            text = post.excerpt.resolve(locale),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.textSecondary)
                                .style("line-height", "1.7")
                        )
                        Link(
                            BlogListCopy.readMore.resolve(locale),
                            Modifier()
                                .color(PortfolioTheme.Colors.accentAlt)
                                .style("font-weight", "600"),
                            blogDetailHref(locale, post.slug),
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
    }
}

private object BlogListCopy {
    val title = LocalizedText("Thoughts & Writing", "أفكار وكتابات")
    val subtitle = LocalizedText(
        en = "Deep dives on systems, frameworks, and creative engineering.",
        ar = "مقالات متعمقة حول الأنظمة والأطر والهندسة الإبداعية."
    )
    val readMore = LocalizedText("Read more →", "اقرأ المزيد ←")
    val byLabel = LocalizedText("By", "بواسطة")
}

fun blogDetailHref(locale: PortfolioLocale, slug: String): String =
    if (locale == PortfolioLocale.EN) "/blog/$slug" else "/${locale.code}/blog/$slug"

fun blogListHref(locale: PortfolioLocale): String =
    if (locale == PortfolioLocale.EN) "/blog" else "/${locale.code}/blog"

private fun dateFormatter(locale: PortfolioLocale): DateTimeFormatter =
    if (locale == PortfolioLocale.AR)
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    else
        DateTimeFormatter.ofPattern("MMMM d, yyyy")
