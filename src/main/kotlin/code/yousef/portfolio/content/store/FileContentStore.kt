package code.yousef.portfolio.content.store

import code.yousef.portfolio.content.ContentStore
import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.withLock

@Serializable
data class ContentSnapshot(
    val hero: HeroContent = PortfolioContentSeed.hero,
    val projects: List<Project> = PortfolioContentSeed.projects,
    val services: List<Service> = PortfolioContentSeed.services,
    val blogPosts: List<BlogPost> = PortfolioContentSeed.blogPosts,
    val testimonials: List<Testimonial> = emptyList(),
    /**
     * Version marker to distinguish intentionally-empty content from missing/corrupt files.
     * If null, data was migrated from an older format or newly created with defaults.
     */
    val version: Int? = CURRENT_VERSION
) {
    fun toPortfolioContent(): PortfolioContent =
        PortfolioContent(hero = hero, projects = projects, services = services, blogPosts = blogPosts, testimonials = testimonials)

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * File-based content store that persists data to a JSON file.
 * Used for local development to ensure changes survive server restarts.
 * 
 * NOTE: In containerized deployments (Docker/Kubernetes), ensure the storage
 * path is mounted as a persistent volume, otherwise data will be lost on restarts.
 */
class FileContentStore(
    private val path: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) : ContentStore {

    private val log = LoggerFactory.getLogger(FileContentStore::class.java)
    private val lock = ReentrantLock()

    init {
        ensureFile()
        log.info("FileContentStore initialized with path: ${path.toAbsolutePath()}")
    }

    override fun loadPortfolioContent(): PortfolioContent = snapshot().toPortfolioContent()

    fun snapshot(): ContentSnapshot = lock.withLock { readSnapshot() }

    override fun listProjects(): List<Project> = snapshot().projects.sortedBy { it.order }

    override fun upsertProject(project: Project) = mutate { snap ->
        snap.copy(
            projects = (snap.projects.filterNot { it.id == project.id } + project)
                .sortedBy { it.order }
        )
    }

    override fun deleteProject(id: String) = mutate { snap ->
        snap.copy(projects = snap.projects.filterNot { it.id == id })
    }

    override fun listServices(): List<Service> = snapshot().services.sortedBy { it.order }

    override fun upsertService(service: Service) = mutate { snap ->
        snap.copy(
            services = (snap.services.filterNot { it.id == service.id } + service)
                .sortedBy { it.order }
        )
    }

    override fun deleteService(id: String) = mutate { snap ->
        snap.copy(services = snap.services.filterNot { it.id == id })
    }

    override fun listBlogPosts(): List<BlogPost> = snapshot().blogPosts.sortedByDescending { it.publishedAt }

    override fun upsertBlogPost(post: BlogPost) = mutate { snap ->
        snap.copy(
            blogPosts = (snap.blogPosts.filterNot { it.id == post.id } + post)
                .sortedByDescending { it.publishedAt }
        )
    }

    override fun deleteBlogPost(id: String) = mutate { snap ->
        snap.copy(blogPosts = snap.blogPosts.filterNot { it.id == id })
    }

    override fun listTestimonials(): List<Testimonial> = snapshot().testimonials.sortedBy { it.order }

    override fun upsertTestimonial(testimonial: Testimonial) = mutate { snap ->
        snap.copy(
            testimonials = (snap.testimonials.filterNot { it.id == testimonial.id } + testimonial)
                .sortedBy { it.order }
        )
    }

    override fun deleteTestimonial(id: String) = mutate { snap ->
        snap.copy(testimonials = snap.testimonials.filterNot { it.id == id })
    }

    override fun getHero(): HeroContent = snapshot().hero

    override fun updateHero(hero: HeroContent) = mutate { snap ->
        snap.copy(hero = hero)
    }

    private fun mutate(transform: (ContentSnapshot) -> ContentSnapshot) {
        lock.withLock {
            val updated = transform(readSnapshot())
            writeSnapshot(updated)
        }
    }

    private fun ensureFile() {
        if (!path.exists()) {
            path.parent?.createDirectories()
            // Create with empty content but WITH version marker
            // This distinguishes "intentionally empty" from "corrupted/missing"
            val emptySnapshot = ContentSnapshot(
                hero = PortfolioContentSeed.hero,
                projects = emptyList(),
                services = emptyList(),
                blogPosts = emptyList(),
                testimonials = emptyList(),
                version = ContentSnapshot.CURRENT_VERSION
            )
            log.warn("No content file found at ${path.toAbsolutePath()}, creating new file with default hero content")
            writeSnapshot(emptySnapshot)
        }
    }

    private fun readSnapshot(): ContentSnapshot {
        ensureFile()
        val text = path.readText()
        
        // Empty file or empty JSON object with no version = corrupted/incomplete state
        if (text.isBlank()) {
            log.warn("Content file is blank, initializing with seed data")
            return createAndSaveSeedSnapshot()
        }
        
        val trimmed = text.trim()
        if (trimmed == "{}" || trimmed == "{ }") {
            log.warn("Content file contains empty JSON object, initializing with seed data")
            return createAndSaveSeedSnapshot()
        }
        
        return runCatching {
            val snapshot = json.decodeFromString(ContentSnapshot.serializer(), text)
            // If no version marker, this is old data - preserve it but add version
            if (snapshot.version == null) {
                log.info("Migrating content data to versioned format")
                val migrated = snapshot.copy(version = ContentSnapshot.CURRENT_VERSION)
                writeSnapshot(migrated)
                migrated
            } else {
                snapshot
            }
        }.getOrElse { e ->
            log.error("Failed to parse content file, initializing with seed data", e)
            createAndSaveSeedSnapshot()
        }
    }
    
    private fun createAndSaveSeedSnapshot(): ContentSnapshot {
        val snapshot = ContentSnapshot(
            hero = PortfolioContentSeed.hero,
            projects = PortfolioContentSeed.projects,
            services = PortfolioContentSeed.services,
            blogPosts = PortfolioContentSeed.blogPosts,
            testimonials = emptyList(),
            version = ContentSnapshot.CURRENT_VERSION
        )
        writeSnapshot(snapshot)
        return snapshot
    }

    private fun writeSnapshot(snapshot: ContentSnapshot) {
        path.parent?.createDirectories()
        path.writeText(json.encodeToString(ContentSnapshot.serializer(), snapshot))
    }

    companion object {
        fun fromEnvironment(): FileContentStore {
            val location = System.getenv("PORTFOLIO_CONTENT_PATH") ?: "storage/content.json"
            return FileContentStore(Path.of(location))
        }
    }
}
