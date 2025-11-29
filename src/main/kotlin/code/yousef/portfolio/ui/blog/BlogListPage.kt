package code.yousef.portfolio.ui.blog

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.sections.ContactSection
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
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import java.time.format.DateTimeFormatter

@Composable
fun BlogListPage(
    posts: List<BlogPost>,
    locale: PortfolioLocale
) {
    val formatter = dateFormatter(locale)
    val sortedPosts = posts.sortedByDescending { it.publishedAt }
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}
        ContentSection(surface = false) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.xl)
            ) {
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = BlogListCopy.title.resolve(locale),
                        modifier = Modifier()
                            .fontSize(3.rem)
                            .fontWeight(700)
                    )
                    Text(
                        text = BlogListCopy.subtitle.resolve(locale),
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .lineHeight(1.8)
                    )
                }

                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.lg)
                ) {
                    sortedPosts.forEach { post ->
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
                                text = post.title.resolve(locale),
                                modifier = Modifier()
                                    .fontSize(1.75.rem)
                                    .fontWeight(600)
                            )
                            Row(
                                modifier = Modifier()
                                    .display(Display.Flex)
                                    .gap(PortfolioTheme.Spacing.sm)
                                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            ) {
                                Text(formatter.format(post.publishedAt))
                                Text("•")
                                Text(BlogListCopy.byLabel.resolve(locale) + " " + post.author)
                            }
                            Text(
                                text = post.excerpt.resolve(locale),
                                modifier = Modifier()
                                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                    .lineHeight(1.7)
                            )
                            val readMoreLabel = BlogListCopy.readMore.resolve(locale)
                            AnchorLink(
                                href = blogDetailHref(locale, post.slug),
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
            }
        }
        ContactSection(locale = locale)
        PortfolioFooter(locale = locale)
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
