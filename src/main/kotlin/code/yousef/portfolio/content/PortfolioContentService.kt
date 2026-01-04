package code.yousef.portfolio.content

import code.yousef.portfolio.content.repo.*

class PortfolioContentService(
    private val store: ContentStore
) {

    fun load(): PortfolioContent = store.loadPortfolioContent()

    fun listProjects() = store.listProjects()

    fun listServices() = store.listServices()

    fun listBlogPosts() = store.listBlogPosts()

    fun listTestimonials() = store.listTestimonials()

    fun findBlogPostBySlug(slug: String) = store.listBlogPosts().firstOrNull {
        it.slug.equals(slug, ignoreCase = true)
    }
}
