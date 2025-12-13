package code.yousef.portfolio.content

import code.yousef.portfolio.content.model.*

/**
 * Common interface for content storage backends.
 * Implementations: FirestoreContentStore, LocalContentStore
 */
interface ContentStore {
    fun loadPortfolioContent(): PortfolioContent
    fun listProjects(): List<Project>
    fun upsertProject(project: Project)
    fun deleteProject(id: String)
    fun listServices(): List<Service>
    fun upsertService(service: Service)
    fun deleteService(id: String)
    fun listBlogPosts(): List<BlogPost>
    fun upsertBlogPost(post: BlogPost)
    fun deleteBlogPost(id: String)
    fun listTestimonials(): List<Testimonial>
    fun upsertTestimonial(testimonial: Testimonial)
    fun deleteTestimonial(id: String)
    fun getHero(): HeroContent
    fun updateHero(hero: HeroContent)
}
