package code.yousef.portfolio.content.store

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import java.nio.file.Files
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
    val blogPosts: List<BlogPost> = PortfolioContentSeed.blogPosts
) {
    fun toPortfolioContent(): PortfolioContent =
        PortfolioContent(hero = hero, projects = projects, services = services, blogPosts = blogPosts)
}

class FileContentStore(
    private val path: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) {

    private val lock = ReentrantLock()

    init {
        ensureFile()
    }

    fun loadPortfolioContent(): PortfolioContent = snapshot().toPortfolioContent()

    fun snapshot(): ContentSnapshot = lock.withLock { readSnapshot() }

    fun upsertProject(project: Project) = mutate { snap ->
        snap.copy(
            projects = (snap.projects.filterNot { it.id == project.id } + project)
                .sortedBy { it.order }
        )
    }

    fun deleteProject(id: String) = mutate { snap ->
        snap.copy(projects = snap.projects.filterNot { it.id == id })
    }

    fun upsertService(service: Service) = mutate { snap ->
        snap.copy(
            services = (snap.services.filterNot { it.id == service.id } + service)
                .sortedBy { it.order }
        )
    }

    fun deleteService(id: String) = mutate { snap ->
        snap.copy(services = snap.services.filterNot { it.id == id })
    }

    fun upsertBlogPost(post: BlogPost) = mutate { snap ->
        snap.copy(
            blogPosts = (snap.blogPosts.filterNot { it.id == post.id } + post)
                .sortedByDescending { it.publishedAt }
        )
    }

    fun deleteBlogPost(id: String) = mutate { snap ->
        snap.copy(blogPosts = snap.blogPosts.filterNot { it.id == id })
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
            writeSnapshot(ContentSnapshot())
        }
    }

    private fun readSnapshot(): ContentSnapshot {
        ensureFile()
        val text = path.readText()
        return if (text.isBlank()) {
            ContentSnapshot().also { writeSnapshot(it) }
        } else {
            json.decodeFromString(ContentSnapshot.serializer(), text)
        }
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
