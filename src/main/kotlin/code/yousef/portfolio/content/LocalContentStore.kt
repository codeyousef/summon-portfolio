package code.yousef.portfolio.content

import code.yousef.portfolio.content.model.*
import code.yousef.portfolio.content.seed.PortfolioContentSeed

/**
 * Local content store that uses seed data.
 * Used for local development when Firestore is not available.
 */
class LocalContentStore : ContentStore {
    
    private val projects: MutableList<Project> = PortfolioContentSeed.projects.toMutableList()
    private val services: MutableList<Service> = PortfolioContentSeed.services.toMutableList()
    private val blogPosts: MutableList<BlogPost> = PortfolioContentSeed.blogPosts.toMutableList()
    private val testimonials: MutableList<Testimonial> = PortfolioContentSeed.testimonials.toMutableList()
    private var hero = PortfolioContentSeed.hero
    
    override fun loadPortfolioContent(): PortfolioContent = PortfolioContent(
        hero = hero,
        projects = projects.sortedBy { it.order },
        services = services.sortedBy { it.order },
        blogPosts = blogPosts.sortedByDescending { it.publishedAt },
        testimonials = testimonials.sortedBy { it.order }
    )
    
    override fun listProjects(): List<Project> = projects.sortedBy { it.order }
    
    override fun upsertProject(project: Project) {
        projects.removeIf { it.id == project.id }
        projects.add(project)
    }
    
    override fun deleteProject(id: String) {
        projects.removeIf { it.id == id }
    }
    
    override fun listServices(): List<Service> = services.sortedBy { it.order }
    
    override fun upsertService(service: Service) {
        services.removeIf { it.id == service.id }
        services.add(service)
    }
    
    override fun deleteService(id: String) {
        services.removeIf { it.id == id }
    }
    
    override fun listBlogPosts(): List<BlogPost> = blogPosts.sortedByDescending { it.publishedAt }
    
    override fun upsertBlogPost(post: BlogPost) {
        blogPosts.removeIf { it.id == post.id }
        blogPosts.add(post)
    }
    
    override fun deleteBlogPost(id: String) {
        blogPosts.removeIf { it.id == id }
    }
    
    override fun listTestimonials(): List<Testimonial> = testimonials.sortedBy { it.order }
    
    override fun upsertTestimonial(testimonial: Testimonial) {
        testimonials.removeIf { it.id == testimonial.id }
        testimonials.add(testimonial)
    }
    
    override fun deleteTestimonial(id: String) {
        testimonials.removeIf { it.id == id }
    }
    
    override fun getHero(): HeroContent = hero
    
    override fun updateHero(newHero: HeroContent) {
        hero = newHero
    }
}
