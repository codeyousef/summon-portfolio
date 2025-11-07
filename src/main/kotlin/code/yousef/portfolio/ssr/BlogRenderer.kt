package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.repo.BlogRepository
import code.yousef.portfolio.content.repo.StaticBlogRepository
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.blog.BlogDetailPage
import code.yousef.portfolio.ui.blog.BlogListPage
import code.yousef.summon.runtime.PlatformRenderer

class BlogRenderer(
    private val rendererFactory: () -> PlatformRenderer = { PlatformRenderer() },
    private val repository: BlogRepository = StaticBlogRepository()
) {
    fun renderList(locale: PortfolioLocale): String {
        val renderer = rendererFactory()
        val posts = repository.list()
        return renderer.renderComposableRoot {
            BlogListPage(posts = posts, locale = locale)
        }
    }

    fun renderDetail(locale: PortfolioLocale, slug: String): RenderResult {
        val renderer = rendererFactory()
        val post = repository.findBySlug(slug)
        val html = renderer.renderComposableRoot {
            if (post != null) {
                BlogDetailPage(post = post, locale = locale)
            } else {
                code.yousef.portfolio.ui.blog.BlogNotFoundPage(locale = locale)
            }
        }
        return RenderResult(html = html, post = post)
    }

    data class RenderResult(
        val html: String,
        val post: BlogPost?
    )
}
