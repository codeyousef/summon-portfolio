package code.yousef.firestore

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.content.model.*
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import code.yousef.portfolio.i18n.LocalizedText
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FirestoreContentStore(private val firestore: Firestore) {

    private val projectsCollection = firestore.collection("projects")
    private val servicesCollection = firestore.collection("services")
    private val blogPostsCollection = firestore.collection("blog_posts")
    private val testimonialsCollection = firestore.collection("testimonials")
    private val heroCollection = firestore.collection("hero")

    private val lock = ReentrantLock()

    init {
        runBlocking { ensureSeedData() }
    }

    fun loadPortfolioContent(): PortfolioContent = runBlocking {
        PortfolioContent(
            hero = getHero(),
            projects = listProjects(),
            services = listServices(),
            blogPosts = listBlogPosts(),
            testimonials = listTestimonials()
        )
    }

    // Projects
    fun listProjects(): List<Project> = runBlocking {
        withContext(Dispatchers.IO) {
            retry {
                projectsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toProject()
                }.sortedBy { it.order }
            }
        }
    }

    fun upsertProject(project: Project) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { projectsCollection.document(project.id).set(project.toMap()).get() }
            }
        }
    }

    fun deleteProject(id: String) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { projectsCollection.document(id).delete().get() }
            }
        }
    }

    // Services
    fun listServices(): List<Service> = runBlocking {
        withContext(Dispatchers.IO) {
            retry {
                servicesCollection.get().get().documents.mapNotNull { doc ->
                    doc.toService()
                }.sortedBy { it.order }
            }
        }
    }

    fun upsertService(service: Service) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { servicesCollection.document(service.id).set(service.toMap()).get() }
            }
        }
    }

    fun deleteService(id: String) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { servicesCollection.document(id).delete().get() }
            }
        }
    }

    // Blog Posts
    fun listBlogPosts(): List<BlogPost> = runBlocking {
        withContext(Dispatchers.IO) {
            retry {
                blogPostsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toBlogPost()
                }.sortedByDescending { it.publishedAt }
            }
        }
    }

    fun upsertBlogPost(post: BlogPost) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { blogPostsCollection.document(post.id).set(post.toMap()).get() }
            }
        }
    }

    fun deleteBlogPost(id: String) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { blogPostsCollection.document(id).delete().get() }
            }
        }
    }

    // Testimonials
    fun listTestimonials(): List<Testimonial> = runBlocking {
        withContext(Dispatchers.IO) {
            retry {
                testimonialsCollection.get().get().documents.mapNotNull { doc ->
                    doc.toTestimonial()
                }.sortedBy { it.order }
            }
        }
    }

    fun upsertTestimonial(testimonial: Testimonial) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { testimonialsCollection.document(testimonial.id).set(testimonial.toMap()).get() }
            }
        }
    }

    fun deleteTestimonial(id: String) = lock.withLock {
        runBlocking {
            withContext(Dispatchers.IO) {
                retry { testimonialsCollection.document(id).delete().get() }
            }
        }
    }

    // Hero
    private suspend fun getHero(): HeroContent = withContext(Dispatchers.IO) {
        retry {
            val doc = heroCollection.document("main").get().get()
            if (doc.exists()) doc.toHeroContent() ?: PortfolioContentSeed.hero
            else PortfolioContentSeed.hero
        }
    }

    private suspend fun ensureSeedData() = withContext(Dispatchers.IO) {
        // Check if data exists, if not seed it
        val projectsExist = retry { projectsCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!projectsExist) {
            PortfolioContentSeed.projects.forEach { project ->
                retry { projectsCollection.document(project.id).set(project.toMap()).get() }
            }
        }

        val servicesExist = retry { servicesCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!servicesExist) {
            PortfolioContentSeed.services.forEach { service ->
                retry { servicesCollection.document(service.id).set(service.toMap()).get() }
            }
        }

        val blogPostsExist = retry { blogPostsCollection.limit(1).get().get().documents.isNotEmpty() }
        if (!blogPostsExist) {
            PortfolioContentSeed.blogPosts.forEach { post ->
                retry { blogPostsCollection.document(post.id).set(post.toMap()).get() }
            }
        }

        val heroExists = retry { heroCollection.document("main").get().get().exists() }
        if (!heroExists) {
            retry { heroCollection.document("main").set(PortfolioContentSeed.hero.toMap()).get() }
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
                imageUrl = data["imageUrl"] as? String
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
                title = data.toLocalizedText("title") ?: return null,
                excerpt = data.toLocalizedText("excerpt") ?: return null,
                content = data.toLocalizedText("content") ?: return null,
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
        "imageUrl" to imageUrl
    )

    private fun Service.toMap(): Map<String, Any?> = mapOf(
        "title" to title.toMap(),
        "description" to description.toMap(),
        "featured" to featured,
        "order" to order
    )

    private fun BlogPost.toMap(): Map<String, Any?> = mapOf(
        "slug" to slug,
        "title" to title.toMap(),
        "excerpt" to excerpt.toMap(),
        "content" to content.toMap(),
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

    private fun HeroMetric.toMap(): Map<String, Any?> = mapOf(
        "value" to value,
        "label" to label.toMap(),
        "detail" to detail.toMap()
    )

    private fun LocalizedText.toMap(): Map<String, Any?> = mapOf(
        "en" to en,
        "ar" to ar
    )
}
