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
import code.yousef.portfolio.i18n.LocalizedText
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
                    val data = doc.data ?: return@mapNotNull null
                    try {
                        ContactSubmission(
                            id = doc.id,
                            contact = data["contact"] as? String ?: return@mapNotNull null,
                            message = data["message"] as? String ?: "",
                            createdAt = (data["createdAt"] as? String)?.let { Instant.parse(it) } ?: Instant.now()
                        )
                    } catch (e: Exception) { null }
                }.sortedByDescending { it.createdAt }
            }
        }
    }

    override fun upsertContactSubmission(submission: ContactSubmission) {
        applyMutation(
            mutationCoordinator.newUpsert(
                CONTACT_SUBMISSIONS,
                submission.id,
                mapOf(
                    "contact" to submission.contact,
                    "message" to submission.message,
                    "createdAt" to submission.createdAt.toString(),
                ),
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

    // Conversion extensions
    private fun com.google.cloud.firestore.DocumentSnapshot.toProject(): Project? {
        val data = this.data ?: return null
        return try {
            Project(
                id = id,
                slug = data["slug"] as? String ?: return null,
                layerLabel = data.toLocalizedText("layerLabel") ?: return null,
                layerName = data.toLocalizedText("layerName") ?: return null,
                title = data.toLocalizedText("title") ?: return null,
                description = data.toLocalizedText("description") ?: return null,
                category = (data["category"] as? String)?.let {
                    runCatching { ProjectCategory.valueOf(it) }.getOrNull()
                } ?: ProjectCategory.WEB,
                featured = data["featured"] as? Boolean ?: false,
                order = (data["order"] as? Number)?.toInt() ?: 0,
                technologies = (data["technologies"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                imageUrl = data["imageUrl"] as? String,
                githubUrl = data["githubUrl"] as? String
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toService(): Service? {
        val data = this.data ?: return null
        return try {
            Service(
                id = id,
                title = data.toLocalizedText("title") ?: return null,
                description = data.toLocalizedText("description") ?: return null,
                featured = data["featured"] as? Boolean ?: false,
                order = (data["order"] as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBlogPost(): BlogPost? {
        val data = this.data ?: return null
        return try {
            BlogPost(
                id = id,
                slug = data["slug"] as? String ?: return null,
                title = data["title"] as? String ?: return null,
                excerpt = data["excerpt"] as? String ?: "",
                content = data["content"] as? String ?: "",
                publishedAt = (data["publishedAt"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
                featured = data["featured"] as? Boolean ?: false,
                author = data["author"] as? String ?: "Unknown",
                tags = (data["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toTestimonial(): Testimonial? {
        val data = this.data ?: return null
        return try {
            Testimonial(
                id = id,
                quote = data.toLocalizedText("quote") ?: return null,
                author = data["author"] as? String ?: return null,
                role = data.toLocalizedText("role") ?: return null,
                company = data.toLocalizedText("company") ?: return null,
                featured = data["featured"] as? Boolean ?: false,
                order = (data["order"] as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toHeroContent(): HeroContent? {
        val data = this.data ?: return null
        return try {
            HeroContent(
                eyebrow = data.toLocalizedText("eyebrow") ?: return null,
                titlePrimary = data.toLocalizedText("titlePrimary") ?: return null,
                titleSecondary = data.toLocalizedText("titleSecondary") ?: return null,
                subtitle = data.toLocalizedText("subtitle") ?: return null,
                ctaPrimary = data.toLocalizedText("ctaPrimary") ?: return null,
                ctaSecondary = data.toLocalizedText("ctaSecondary") ?: return null,
                metrics = (data["metrics"] as? List<*>)?.mapNotNull { it.toHeroMetric() } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toPhotographyPhoto(): PhotographyPhoto? {
        val data = this.data ?: return null
        return try {
            PhotographyPhoto(
                id = id,
                title = data["title"] as? String ?: return null,
                altText = data["altText"] as? String ?: return null,
                caption = data["caption"] as? String,
                takenAt = (data["takenAt"] as? String)?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                order = (data["order"] as? Number)?.toInt() ?: 0,
                published = data["published"] as? Boolean ?: false,
                storageKey = data["storageKey"] as? String ?: return null,
                contentType = data["contentType"] as? String ?: "application/octet-stream",
                originalFilename = data["originalFilename"] as? String,
                sizeBytes = (data["sizeBytes"] as? Number)?.toLong() ?: 0L,
                mediaType = (data["mediaType"] as? String)?.let {
                    runCatching { PhotographyMediaType.valueOf(it) }.getOrNull()
                } ?: PhotographyMediaType.PHOTO,
                sourceKind = (data["sourceKind"] as? String)?.let {
                    runCatching { PhotographySourceKind.valueOf(it) }.getOrNull()
                } ?: PhotographySourceKind.UPLOAD,
                category = data["category"] as? String ?: "Uncategorized",
                albumTitle = data["albumTitle"] as? String,
                externalUrl = data["externalUrl"] as? String,
                thumbnailUrl = data["thumbnailUrl"] as? String,
                featured = data["featured"] as? Boolean ?: false,
                uploadedAt = (data["uploadedAt"] as? String)?.let { Instant.parse(it) } ?: Instant.now()
            )
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toLocalizedText(key: String): LocalizedText? {
        val value = this[key] ?: return null
        return when (value) {
            is Map<*, *> -> {
                val map = value as? Map<String, Any> ?: return null
                LocalizedText(
                    en = map["en"] as? String ?: "",
                    ar = map["ar"] as? String
                )
            }
            is String -> LocalizedText(en = value, ar = null)
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toHeroMetric(): HeroMetric? {
        val map = this as? Map<String, Any> ?: return null
        return try {
            HeroMetric(
                value = map["value"] as? String ?: return null,
                label = map.toLocalizedText("label") ?: return null,
                detail = map.toLocalizedText("detail") ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    // Conversion to Map for Firestore
    private fun Project.toMap(): Map<String, Any?> = mapOf(
        "slug" to slug,
        "layerLabel" to layerLabel.toMap(),
        "layerName" to layerName.toMap(),
        "title" to title.toMap(),
        "description" to description.toMap(),
        "category" to category.name,
        "featured" to featured,
        "order" to order,
        "technologies" to technologies,
        "imageUrl" to imageUrl,
        "githubUrl" to githubUrl
    )

    private fun Service.toMap(): Map<String, Any?> = mapOf(
        "title" to title.toMap(),
        "description" to description.toMap(),
        "featured" to featured,
        "order" to order
    )

    private fun BlogPost.toMap(): Map<String, Any?> = mapOf(
        "slug" to slug,
        "title" to title,
        "excerpt" to excerpt,
        "content" to content,
        "publishedAt" to publishedAt.toString(),
        "featured" to featured,
        "author" to author,
        "tags" to tags
    )

    private fun Testimonial.toMap(): Map<String, Any?> = mapOf(
        "quote" to quote.toMap(),
        "author" to author,
        "role" to role.toMap(),
        "company" to company.toMap(),
        "featured" to featured,
        "order" to order
    )

    private fun HeroContent.toMap(): Map<String, Any?> = mapOf(
        "eyebrow" to eyebrow.toMap(),
        "titlePrimary" to titlePrimary.toMap(),
        "titleSecondary" to titleSecondary.toMap(),
        "subtitle" to subtitle.toMap(),
        "ctaPrimary" to ctaPrimary.toMap(),
        "ctaSecondary" to ctaSecondary.toMap(),
        "metrics" to metrics.map { it.toMap() }
    )

    private fun PhotographyPhoto.toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "altText" to altText,
        "caption" to caption,
        "takenAt" to takenAt?.toString(),
        "order" to order,
        "published" to published,
        "storageKey" to storageKey,
        "contentType" to contentType,
        "originalFilename" to originalFilename,
        "sizeBytes" to sizeBytes,
        "mediaType" to mediaType.name,
        "sourceKind" to sourceKind.name,
        "category" to category,
        "albumTitle" to albumTitle,
        "externalUrl" to externalUrl,
        "thumbnailUrl" to thumbnailUrl,
        "featured" to featured,
        "uploadedAt" to uploadedAt.toString()
    )

    private fun HeroMetric.toMap(): Map<String, Any?> = mapOf(
        "value" to value,
        "label" to label.toMap(),
        "detail" to detail.toMap()
    )

    private fun LocalizedText.toMap(): Map<String, Any?> = mapOf(
        "en" to en,
        "ar" to ar
    )

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
