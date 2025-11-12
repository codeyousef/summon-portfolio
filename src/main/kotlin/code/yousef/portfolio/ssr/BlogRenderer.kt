package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.blog.BlogDetailPage
import code.yousef.portfolio.ui.blog.BlogListPage
import code.yousef.portfolio.ui.blog.BlogNotFoundPage
import code.yousef.summon.seo.HeadScope
import io.ktor.http.*

class BlogRenderer(
    private val contentService: PortfolioContentService = PortfolioContentService.default()
) {
    fun renderList(locale: PortfolioLocale): SummonPage {
        val posts = contentService.load().blogPosts
        return SummonPage(
            head = listHead(locale),
            content = { BlogListPage(posts = posts, locale = locale) }
        )
    }

    fun renderDetail(locale: PortfolioLocale, slug: String): RenderResult {
        val post = contentService.load().blogPosts.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
        val page = SummonPage(
            head = detailHead(locale, post, slug),
            content = {
                if (post != null) {
                    BlogDetailPage(post = post, locale = locale)
                } else {
                    BlogNotFoundPage(locale = locale)
                }
            }
        )
        val status = if (post == null) HttpStatusCode.NotFound else HttpStatusCode.OK
        return RenderResult(page = page, status = status)
    }

    data class RenderResult(
        val page: SummonPage,
        val status: HttpStatusCode
    )

    private fun listHead(locale: PortfolioLocale): (HeadScope) -> Unit = { head ->
        val canonical = blogCanonical(locale)
        head.title("Articles · Yousef Baitalmal")
        head.meta(
            "description",
            "Summon-first engineering notes, release write-ups, and systems breakdowns from the portfolio.",
            null,
            null,
            null
        )
        head.meta(null, "Articles · Yousef Baitalmal", "og:title", null, null)
        head.meta(
            null,
            "Summon-first engineering notes, release write-ups, and systems breakdowns from the portfolio.",
            "og:description",
            null,
            null
        )
        head.meta(null, "website", "og:type", null, null)
        head.meta(null, canonical, "og:url", null, null)
        head.meta(null, locale.code, "og:locale", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", "Articles · Yousef Baitalmal", null, null, null)
        head.meta(
            "twitter:description",
            "Summon-first engineering notes, release write-ups, and systems breakdowns.",
            null,
            null,
            null
        )
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", blogCanonical(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", blogCanonical(PortfolioLocale.AR), "ar", null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
    }

    private fun detailHead(locale: PortfolioLocale, post: BlogPost?, slug: String): (HeadScope) -> Unit = { head ->
        val canonical = blogDetailCanonical(locale, slug)
        val title = post?.title?.resolve(locale) ?: "Article Not Found · Yousef Baitalmal"
        val description = post?.excerpt?.resolve(locale) ?: "The requested article could not be located."
        head.title(title)
        head.meta("description", description, null, null, null)
        head.meta(null, title, "og:title", null, null)
        head.meta(null, description, "og:description", null, null)
        head.meta(null, if (post != null) "article" else "website", "og:type", null, null)
        head.meta(null, canonical, "og:url", null, null)
        head.meta(null, locale.code, "og:locale", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", title, null, null, null)
        head.meta("twitter:description", description, null, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", blogDetailCanonical(PortfolioLocale.EN, slug), "en", null, null, null)
        head.link("alternate", blogDetailCanonical(PortfolioLocale.AR, slug), "ar", null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
    }

    private fun blogCanonical(locale: PortfolioLocale): String {
        val base = portfolioBaseUrl().trimEnd('/')
        return when (locale) {
            PortfolioLocale.EN -> "$base/blog"
            else -> "$base/${locale.code}/blog"
        }
    }

    private fun blogDetailCanonical(locale: PortfolioLocale, slug: String): String {
        val base = portfolioBaseUrl().trimEnd('/')
        return when (locale) {
            PortfolioLocale.EN -> "$base/blog/$slug"
            else -> "$base/${locale.code}/blog/$slug"
        }
    }
}
