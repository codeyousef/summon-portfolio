package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.blog.BlogDetailPage
import code.yousef.portfolio.ui.blog.BlogListPage
import code.yousef.portfolio.ui.blog.BlogNotFoundPage
import codes.yousef.summon.seo.HeadScope
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
        head.title("Articles · Yousef")
        head.meta(
            "description",
            null,
            "Summon-first engineering notes, release write-ups, and systems breakdowns from the portfolio.",
            null,
            null
        )
        head.meta(null, "og:title", "Articles · Yousef", null, null)
        head.meta(
            null,
            "og:description",
            "Summon-first engineering notes, release write-ups, and systems breakdowns from the portfolio.",
            null,
            null
        )
        head.meta(null, "og:type", "website", null, null)
        head.meta(null, "og:url", canonical, null, null)
        head.meta(null, "og:locale", locale.code, null, null)
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, "Articles · Yousef Baitalmal", null, null)
        head.meta(
            "twitter:description",
            null,
            "Summon-first engineering notes, release write-ups, and systems breakdowns.",
            null,
            null
        )
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", blogCanonical(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", blogCanonical(PortfolioLocale.AR), "ar", null, null, null)
        head.script("/static/wasm-polyfill.js", "wasm-polyfill", "application/javascript", false, false, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
    }

    private fun detailHead(locale: PortfolioLocale, post: BlogPost?, slug: String): (HeadScope) -> Unit = { head ->
        val canonical = blogDetailCanonical(locale, slug)
        val title = post?.title?.resolve(locale) ?: "Article Not Found · Yousef"
        val description = post?.excerpt?.resolve(locale) ?: "The requested article could not be located."
        head.title(title)
        // Standard description
        head.meta("description", null, description, null, null)
        // OpenGraph
        head.meta(null, "og:title", title, null, null)
        head.meta(null, "og:description", description, null, null)
        head.meta(null, "og:type", if (post != null) "article" else "website", null, null)
        head.meta(null, "og:url", canonical, null, null)
        head.meta(null, "og:locale", locale.code, null, null)
        // Twitter
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, title, null, null)
        head.meta("twitter:description", null, description, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", blogDetailCanonical(PortfolioLocale.EN, slug), "en", null, null, null)
        head.link("alternate", blogDetailCanonical(PortfolioLocale.AR, slug), "ar", null, null, null)
        head.script("/static/wasm-polyfill.js", "wasm-polyfill", "application/javascript", false, false, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
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
