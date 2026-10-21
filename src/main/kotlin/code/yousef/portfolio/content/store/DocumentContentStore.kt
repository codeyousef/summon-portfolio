package code.yousef.portfolio.content.store

import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import code.yousef.portfolio.content.store.PortfolioContentDocuments.BLOG_POSTS
import code.yousef.portfolio.content.store.PortfolioContentDocuments.CONTACT_SUBMISSIONS
import code.yousef.portfolio.content.store.PortfolioContentDocuments.HERO
import code.yousef.portfolio.content.store.PortfolioContentDocuments.HERO_DOCUMENT_ID
import code.yousef.portfolio.content.store.PortfolioContentDocuments.PHOTOGRAPHY_PHOTOS
import code.yousef.portfolio.content.store.PortfolioContentDocuments.PROJECTS
import code.yousef.portfolio.content.store.PortfolioContentDocuments.SERVICES
import code.yousef.portfolio.content.store.PortfolioContentDocuments.TESTIMONIALS
import code.yousef.portfolio.content.store.PortfolioContentDocuments.toDocument

/**
 * [ContentStore] over the generic document store, used by the DynamoDB backend. It shares
 * [PortfolioContentDocuments] with the Firestore content store, so the same collections and
 * document shapes are written regardless of backend.
 */
class DocumentContentStore(
    private val store: PortfolioFirestoreStore,
    seedOnInit: Boolean = false,
) : ContentStore {
    init {
        if (seedOnInit) ensureSeedData()
    }

    override fun loadPortfolioContent(): PortfolioContent = PortfolioContent(
        hero = getHero(),
        projects = listProjects(),
        services = listServices(),
        blogPosts = listBlogPosts(),
        testimonials = listTestimonials(),
        photographyPhotos = listPhotographyPhotos(),
    )

    override fun listProjects(): List<Project> = store.list(PROJECTS)
        .mapNotNull { PortfolioContentDocuments.project(it.id, it.data) }
        .sortedBy { it.order }

    override fun upsertProject(project: Project) = store.upsert(PROJECTS, project.id, project.toDocument())
    override fun deleteProject(id: String) = store.delete(PROJECTS, id)

    override fun listServices(): List<Service> = store.list(SERVICES)
        .mapNotNull { PortfolioContentDocuments.service(it.id, it.data) }
        .sortedBy { it.order }

    override fun upsertService(service: Service) = store.upsert(SERVICES, service.id, service.toDocument())
    override fun deleteService(id: String) = store.delete(SERVICES, id)

    override fun listBlogPosts(): List<BlogPost> = store.list(BLOG_POSTS)
        .mapNotNull { PortfolioContentDocuments.blogPost(it.id, it.data) }
        .sortedByDescending { it.publishedAt }

    override fun upsertBlogPost(post: BlogPost) = store.upsert(BLOG_POSTS, post.id, post.toDocument())
    override fun deleteBlogPost(id: String) = store.delete(BLOG_POSTS, id)

    override fun listTestimonials(): List<Testimonial> = store.list(TESTIMONIALS)
        .mapNotNull { PortfolioContentDocuments.testimonial(it.id, it.data) }
        .sortedBy { it.order }

    override fun upsertTestimonial(testimonial: Testimonial) =
        store.upsert(TESTIMONIALS, testimonial.id, testimonial.toDocument())

    override fun deleteTestimonial(id: String) = store.delete(TESTIMONIALS, id)

    override fun getHero(): HeroContent = store.get(HERO, HERO_DOCUMENT_ID)
        ?.let { PortfolioContentDocuments.hero(it.data) }
        ?: PortfolioContentSeed.hero

    override fun updateHero(hero: HeroContent) = store.upsert(HERO, HERO_DOCUMENT_ID, hero.toDocument())

    override fun listContactSubmissions(): List<ContactSubmission> = store.list(CONTACT_SUBMISSIONS)
        .mapNotNull { PortfolioContentDocuments.contactSubmission(it.id, it.data) }
        .sortedByDescending { it.createdAt }

    override fun upsertContactSubmission(submission: ContactSubmission) =
        store.upsert(CONTACT_SUBMISSIONS, submission.id, submission.toDocument())

    override fun deleteContactSubmission(id: String) = store.delete(CONTACT_SUBMISSIONS, id)

    override fun listPhotographyPhotos(): List<PhotographyPhoto> = store.list(PHOTOGRAPHY_PHOTOS)
        .mapNotNull { PortfolioContentDocuments.photographyPhoto(it.id, it.data) }
        .sortedWith(compareBy<PhotographyPhoto> { it.order }.thenByDescending { it.uploadedAt })

    override fun upsertPhotographyPhoto(photo: PhotographyPhoto) =
        store.upsert(PHOTOGRAPHY_PHOTOS, photo.id, photo.toDocument())

    override fun deletePhotographyPhoto(id: String) = store.delete(PHOTOGRAPHY_PHOTOS, id)

    private fun ensureSeedData() {
        if (store.list(PROJECTS).isEmpty()) {
            PortfolioContentSeed.projects.forEach(::upsertProject)
        }
        if (store.list(SERVICES).isEmpty()) {
            PortfolioContentSeed.services.forEach(::upsertService)
        }
        if (store.list(BLOG_POSTS).isEmpty()) {
            PortfolioContentSeed.blogPosts.forEach(::upsertBlogPost)
        }
        if (store.get(HERO, HERO_DOCUMENT_ID) == null) {
            updateHero(PortfolioContentSeed.hero)
        }
    }
}
