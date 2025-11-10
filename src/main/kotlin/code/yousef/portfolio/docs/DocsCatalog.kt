package code.yousef.portfolio.docs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

class DocsCatalog(
    private val config: DocsConfig
) {
    data class DocEntry(
        val slug: String,
        val title: String,
        val repoPath: String
    )

    private data class Snapshot(
        val entries: List<DocEntry>,
        val slugMap: Map<String, DocEntry>,
        val navTree: DocsNavTree,
        val orderedSlugs: List<String>
    )

    private val logger = LoggerFactory.getLogger(DocsCatalog::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: java.net.http.HttpClient = java.net.http.HttpClient.newBuilder().build()

    @Volatile
    private var snapshot: Snapshot = loadSnapshot()

    fun reload() {
        snapshot = loadSnapshot()
    }

    fun find(slug: String): DocEntry? = snapshot.slugMap[slug]

    fun navTree(): DocsNavTree = snapshot.navTree

    fun neighbors(slug: String): NeighborLinks {
        val ordered = snapshot.orderedSlugs
        val map = snapshot.slugMap
        val index = ordered.indexOf(slug).takeIf { it >= 0 } ?: return NeighborLinks(null, null)
        val previous = ordered.getOrNull(index - 1)?.let { map[it] }?.toNavLink()
        val next = ordered.getOrNull(index + 1)?.let { map[it] }?.toNavLink()
        return NeighborLinks(previous, next)
    }

    fun allSlugs(): List<String> = snapshot.orderedSlugs

    private fun DocEntry.toNavLink(): NavLink =
        NavLink(title = title, path = if (slug == SLUG_ROOT) "/" else "/$slug")

    private fun loadSnapshot(): Snapshot {
        val entries = loadEntries()
        return Snapshot(
            entries = entries,
            slugMap = entries.associateBy { it.slug },
            navTree = buildNavTree(entries),
            orderedSlugs = entries.map { it.slug }
        )
    }

    private fun loadEntries(): List<DocEntry> {
        return when (config.docsSource) {
            DocsSource.LOCAL -> loadLocalEntries()
            DocsSource.REMOTE -> loadRemoteEntries()
        }
    }

    private fun loadLocalEntries(): List<DocEntry> {
        val root = config.localDocsRoot
        if (!Files.exists(root)) return emptyList()
        val mdFiles = Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md", ignoreCase = true) }
                .toList()
        }

        val slugSeen = mutableSetOf<String>()
        return mdFiles.mapNotNull { path ->
            val relative = root.relativize(path).toString().replace('\\', '/')
            val firstSegment = relative.substringBefore('/', relative)
            if (firstSegment.equals("private", ignoreCase = true)) {
                return@mapNotNull null
            }
            val slug = slugFor(relative) ?: return@mapNotNull null
            if (!slugSeen.add(slug)) {
                return@mapNotNull null
            }
            val title = extractTitle(path) ?: defaultTitleForSlug(relative)
            DocEntry(
                slug = slug,
                title = title,
                repoPath = config.repoPathFor(relative)
            )
        }.sortedWith(compareBy<DocEntry> { it.slug != SLUG_ROOT }.thenBy { it.slug })
    }

    private fun loadRemoteEntries(): List<DocEntry> {
        val tree = fetchRemoteTree() ?: return emptyList()
        val normalizedRoot = config.normalizedDocsRoot.trim('/')
        val slugSeen = mutableSetOf<String>()
        return tree.asSequence()
            .filter { it.type == "blob" && it.path.endsWith(".md", ignoreCase = true) }
            .filter { entry ->
                val matchesRoot = normalizedRoot.isBlank() || entry.path.startsWith("$normalizedRoot/")
                if (!matchesRoot) return@filter false
                val relative = entry.path.removePrefix("$normalizedRoot/").trimStart('/')
                val firstSegment = relative.substringBefore('/', relative)
                !firstSegment.equals("private", ignoreCase = true)
            }
            .mapNotNull { node ->
                val relative = node.path.removePrefix("$normalizedRoot/").trimStart('/')
                val slug = slugFor(relative) ?: return@mapNotNull null
                if (!slugSeen.add(slug)) {
                    return@mapNotNull null
                }
                val title = defaultTitleForSlug(relative)
                DocEntry(
                    slug = slug,
                    title = title,
                    repoPath = config.repoPathFor(relative)
                )
            }
            .sortedWith(compareBy<DocEntry> { it.slug != SLUG_ROOT }.thenBy { it.slug })
            .toList()
    }

    private fun fetchRemoteTree(): List<GitTreeNode>? {
        val endpoint =
            "https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/git/trees/${config.defaultBranch}?recursive=1"
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Accept", "application/vnd.github+json")
            .GET()
        config.githubToken?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        val request = requestBuilder.build()
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.warn("Failed to load docs tree from GitHub: HTTP ${response.statusCode()}")
                null
            } else {
                json.decodeFromString(GitTreeResponse.serializer(), response.body()).tree
            }
        } catch (ex: IOException) {
            logger.error("Error fetching docs tree from GitHub", ex)
            null
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("Interrupted while fetching docs tree from GitHub", ex)
            null
        }
    }

    private fun slugFor(relativePath: String): String? {
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return SLUG_ROOT
        val lower = normalized.lowercase()
        return when {
            lower == "readme.md" || lower == "index.md" || lower == "summon-readme.md" -> SLUG_ROOT
            lower.endsWith("/readme.md") -> lower.removeSuffix("/readme.md")
            lower.endsWith("/index.md") -> lower.removeSuffix("/index.md")
            lower.endsWith("/summon-readme.md") -> lower.removeSuffix("/summon-readme.md")
            lower.endsWith(".md") -> lower.removeSuffix(".md")
            else -> null
        }?.trim('/').orEmpty()
    }

    private fun extractTitle(path: Path): String? {
        return try {
            Files.newBufferedReader(path).use { reader ->
                var inFrontMatter = false
                reader.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { line ->
                        when {
                            line == "---" -> {
                                inFrontMatter = !inFrontMatter
                                false
                            }

                            inFrontMatter -> false
                            line.startsWith("#") -> true
                            else -> false
                        }
                    }
                    ?.trimStart('#')
                    ?.trim()
                    ?.ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultTitleForSlug(relativePath: String): String {
        val slug = slugFor(relativePath) ?: SLUG_ROOT
        if (slug == SLUG_ROOT) {
            return "Overview"
        }
        val segment = slug.substringAfterLast('/')
        return segment.replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .ifBlank { relativePath }
    }

    private fun buildNavTree(entries: List<DocEntry>): DocsNavTree {
        if (entries.isEmpty()) {
            return DocsNavTree(emptyList())
        }
        val children = entries.map { entry ->
            DocsNavNode(
                title = entry.title,
                path = if (entry.slug == SLUG_ROOT) "/" else "/${entry.slug}",
                children = emptyList()
            )
        }
        return DocsNavTree(
            sections = listOf(
                DocsNavNode(
                    title = "Documentation",
                    path = "/",
                    children = children
                )
            )
        )
    }

    companion object {
        const val SLUG_ROOT = ""
    }
}

@Serializable
private data class GitTreeResponse(
    val tree: List<GitTreeNode> = emptyList()
)

@Serializable
private data class GitTreeNode(
    val path: String,
    val type: String
)
