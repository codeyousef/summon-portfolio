package code.yousef.portfolio.content

import code.yousef.firestore.FirestoreContentStore
import code.yousef.portfolio.content.repo.*

class PortfolioContentService(
    private val store: FirestoreContentStore
) {

    fun load(): PortfolioContent = store.loadPortfolioContent()

    fun listProjects() = store.listProjects()

    fun listServices() = store.listServices()

    fun listBlogPosts() = store.listBlogPosts()

    fun findBlogPostBySlug(slug: String) = store.listBlogPosts().firstOrNull {
        it.slug.equals(slug, ignoreCase = true)
    }
}
