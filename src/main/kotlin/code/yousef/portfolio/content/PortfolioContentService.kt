package code.yousef.portfolio.content

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.repo.*
import code.yousef.portfolio.db.fixMojibake

class PortfolioContentService(
    private val store: ContentStore
) {

    fun load(): PortfolioContent {
        val content = store.loadPortfolioContent()
        return content.copy(blogPosts = content.blogPosts.map { it.repairMojibake() })
    }

    fun listProjects() = store.listProjects()

    fun listServices() = store.listServices()

    fun listBlogPosts() = store.listBlogPosts().map { it.repairMojibake() }

    fun listTestimonials() = store.listTestimonials()

    fun listPhotographyPhotos() = store.listPhotographyPhotos()

    fun findBlogPostBySlug(slug: String) = store.listBlogPosts().firstOrNull {
        it.slug.equals(slug, ignoreCase = true)
    }?.repairMojibake()

    private fun BlogPost.repairMojibake() = copy(
        title = fixMojibake(title),
        excerpt = fixMojibake(excerpt),
        content = fixMojibake(content),
        author = fixMojibake(author)
    )
}
