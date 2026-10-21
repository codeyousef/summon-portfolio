package code.yousef.firestore

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.FirestoreMutationBackend
import code.yousef.firestore.migration.MutationCoordinator
import code.yousef.firestore.migration.MutationEnvelope
import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.content.model.*
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import code.yousef.portfolio.content.store.PortfolioContentDocuments
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FirestoreContentStore(
    private val firestore: Firestore,
    private val mutationCoordinator: MutationCoordinator = MutationCoordinator(
        writeMode = FirestoreWriteMode.SOURCE,
        source = FirestoreMutationBackend(firestore),
        target = null,
    ),
    seedOnInit: Boolean = false,
) : ContentStore {

    private val projectsCollection = firestore.collection("projects")
    private val servicesCollection = firestore.collection("services")
    private val blogPostsCollection = firestore.collection("blog_posts")
    private val testimonialsCollection = firestore.collection("testimonials")
    private val heroCollection = firestore.collection("hero")
    private val contactSubmissionsCollection = firestore.collection("contact_submissions")
    private val photographyPhotosCollection = firestore.collection("photography_photos")

    private val lock = ReentrantLock()

    init {
        if (seedOnInit) runBlocking { ensureSeedData() }
    }

    override fun loadPortfolioContent(): PortfolioContent = runBlocking {
        coroutineScope {
            val hero = async { readHero() }
            val projects = async { readProjects() }
            val services = async { readServices() }
            val blogPosts = async { readBlogPosts() }
            val testimonials = async { readTestimonials() }
            val photographyPhotos = async { readPhotographyPhotos() }
            PortfolioContent(
                hero = hero.await(),
                projects = projects.await(),
                services = services.await(),
                blogPosts = blogPosts.await(),
                testimonials = testimonials.await(),
                photographyPhotos = photographyPhotos.await(),
            )
        }
    }

    // Projects
    override fun listProjects(): List<Project> = runBlocking { readProjects() }

    private suspend fun readProjects(): List<Project> =
        withContext(Dispatchers.IO) {
            retry {
                projectsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toProject()
                }.sortedBy { it.order }
            }
        }

    override fun upsertProject(project: Project) {
        applyMutation(mutationCoordinator.newUpsert(PROJECTS, project.id, project.toMap()))
    }

    override fun deleteProject(id: String) {
        applyMutation(mutationCoordinator.newDelete(PROJECTS, id))
    }

    // Services
    override fun listServices(): List<Service> = runBlocking { readServices() }

    private suspend fun readServices(): List<Service> =
        withContext(Dispatchers.IO) {
            retry {
                servicesCollection.get().get().documents.mapNotNull { doc ->
                    doc.toService()
                }.sortedBy { it.order }
            }
        }

    override fun upsertService(service: Service) {
        applyMutation(mutationCoordinator.newUpsert(SERVICES, service.id, service.toMap()))
    }

    override fun deleteService(id: String) {
        applyMutation(mutationCoordinator.newDelete(SERVICES, id))
    }

    // Blog Posts
    override fun listBlogPosts(): List<BlogPost> = runBlocking { readBlogPosts() }

    private suspend fun readBlogPosts(): List<BlogPost> =
        withContext(Dispatchers.IO) {
            retry {
                blogPostsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toBlogPost()
                }.sortedByDescending { it.publishedAt }
            }
        }

    override fun upsertBlogPost(post: BlogPost) {
        applyMutation(mutationCoordinator.newUpsert(BLOG_POSTS, post.id, post.toMap()))
    }

    override fun deleteBlogPost(id: String) {
        applyMutation(mutationCoordinator.newDelete(BLOG_POSTS, id))
    }

    // Testimonials
    override fun listTestimonials(): List<Testimonial> = runBlocking { readTestimonials() }

    private suspend fun readTestimonials(): List<Testimonial> =
        withContext(Dispatchers.IO) {
            retry {
                testimonialsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toTestimonial()
                }.sortedBy { it.order }
            }
        }

    override fun upsertTestimonial(testimonial: Testimonial) {
        applyMutation(mutationCoordinator.newUpsert(TESTIMONIALS, testimonial.id, testimonial.toMap()))
    }

    override fun deleteTestimonial(id: String) {
        applyMutation(mutationCoordinator.newDelete(TESTIMONIALS, id))
    }

    // Hero
    override fun getHero(): HeroContent = runBlocking { readHero() }

    private suspend fun readHero(): HeroContent =
        withContext(Dispatchers.IO) {
            retry {
                val doc = heroCollection.document("main").get().get()
                if (doc.exists()) doc.toHeroContent() ?: PortfolioContentSeed.hero
                else PortfolioContentSeed.hero
            }
        }

    override fun updateHero(hero: HeroContent) {
        applyMutation(mutationCoordinator.newUpsert(HERO, "main", hero.toMap()))
    }

    // Contact Submissions
    override fun listContactSubmissions(): List<ContactSubmission> = runBlocking {
        withContext(Dispatchers.IO) {
            retry {
                contactSubmissionsCollection.get().get().documents.mapNotNull { doc ->
                    doc.data?.let { PortfolioContentDocuments.contactSubmission(doc.id, it) }
                }.sortedByDescending { it.createdAt }
            }
        }
    }

    override fun upsertContactSubmission(submission: ContactSubmission) {
        applyMutation(
            mutationCoordinator.newUpsert(
                CONTACT_SUBMISSIONS,
                submission.id,
                with(PortfolioContentDocuments) { submission.toDocument() },
            ),
        )
    }

    override fun deleteContactSubmission(id: String) {
        applyMutation(mutationCoordinator.newDelete(CONTACT_SUBMISSIONS, id))
    }

    override fun listPhotographyPhotos(): List<PhotographyPhoto> = runBlocking { readPhotographyPhotos() }

    private suspend fun readPhotographyPhotos(): List<PhotographyPhoto> =
        withContext(Dispatchers.IO) {
            retry {
                photographyPhotosCollection.get().get().documents.mapNotNull { doc ->
                    doc.toPhotographyPhoto()
                }.sortedWith(compareBy<PhotographyPhoto> { it.order }.thenByDescending { it.uploadedAt })
            }
        }

    override fun upsertPhotographyPhoto(photo: PhotographyPhoto) {
        applyMutation(mutationCoordinator.newUpsert(PHOTOGRAPHY_PHOTOS, photo.id, photo.toMap()))
    }

    override fun deletePhotographyPhoto(id: String) {
        applyMutation(mutationCoordinator.newDelete(PHOTOGRAPHY_PHOTOS, id))
    }

    private fun applyMutation(envelope: MutationEnvelope) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    retry { mutationCoordinator.apply(envelope) }
                }
            }
        }
    }

    private suspend fun ensureSeedData() = withContext(Dispatchers.IO) {
        // Check if data exists, if not seed it
        val projectsExist = retry { projectsCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!projectsExist) {
            PortfolioContentSeed.projects.forEach { project ->
                val mutation = mutationCoordinator.newUpsert(PROJECTS, project.id, project.toMap())
                retry { mutationCoordinator.apply(mutation) }
            }
        }

        val servicesExist = retry { servicesCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!servicesExist) {
            PortfolioContentSeed.services.forEach { service ->
                val mutation = mutationCoordinator.newUpsert(SERVICES, service.id, service.toMap())
                retry { mutationCoordinator.apply(mutation) }
            }
        }

        val blogPostsExist = retry { blogPostsCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!blogPostsExist) {
            PortfolioContentSeed.blogPosts.forEach { post ->
                val mutation = mutationCoordinator.newUpsert(BLOG_POSTS, post.id, post.toMap())
                retry { mutationCoordinator.apply(mutation) }
            }
        }

        val heroExists = retry { heroCollection.document("main").get().get().exists() }
        if (!heroExists) {
            val mutation = mutationCoordinator.newUpsert(HERO, "main", PortfolioContentSeed.hero.toMap())
            retry { mutationCoordinator.apply(mutation) }
        }
    }

    // Conversion extensions (shared with every document backend)
    private fun com.google.cloud.firestore.DocumentSnapshot.toProject(): Project? =
        data?.let { PortfolioContentDocuments.project(id, it) }

    private fun com.google.cloud.firestore.DocumentSnapshot.toService(): Service? =
        data?.let { PortfolioContentDocuments.service(id, it) }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBlogPost(): BlogPost? =
        data?.let { PortfolioContentDocuments.blogPost(id, it) }

    private fun com.google.cloud.firestore.DocumentSnapshot.toTestimonial(): Testimonial? =
        data?.let { PortfolioContentDocuments.testimonial(id, it) }

    private fun com.google.cloud.firestore.DocumentSnapshot.toHeroContent(): HeroContent? =
        data?.let(PortfolioContentDocuments::hero)

    private fun com.google.cloud.firestore.DocumentSnapshot.toPhotographyPhoto(): PhotographyPhoto? =
        data?.let { PortfolioContentDocuments.photographyPhoto(id, it) }

    private fun Project.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }
    private fun Service.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }
    private fun BlogPost.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }
    private fun Testimonial.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }
    private fun HeroContent.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }
    private fun PhotographyPhoto.toMap(): Map<String, Any?> = with(PortfolioContentDocuments) { toDocument() }

    private companion object {
        const val PROJECTS = "projects"
        const val SERVICES = "services"
        const val BLOG_POSTS = "blog_posts"
        const val TESTIMONIALS = "testimonials"
        const val HERO = "hero"
        const val CONTACT_SUBMISSIONS = "contact_submissions"
        const val PHOTOGRAPHY_PHOTOS = "photography_photos"
    }
}
