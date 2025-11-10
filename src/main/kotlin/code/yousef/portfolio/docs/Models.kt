package code.yousef.portfolio.docs

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

data class CachedDoc(
    val repoPath: String,
    val body: String,
    val etag: String?,
    val lastModified: Instant?,
    val fetchedAt: Instant
)

data class CachedAsset(
    val repoPath: String,
    val bytes: ByteArray,
    val contentType: String?,
    val etag: String?,
    val lastModified: Instant?,
    val fetchedAt: Instant
)

data class FetchedDoc(
    val repoPath: String,
    val body: String,
    val etag: String?,
    val lastModified: Instant?
)

data class FetchedAsset(
    val repoPath: String,
    val bytes: ByteArray,
    val contentType: String?,
    val etag: String?,
    val lastModified: Instant?
)

data class MarkdownMeta(
    val title: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val section: String? = null,
    val sectionTitle: String? = null,
    val order: Int = Int.MAX_VALUE
)

data class TocEntry(
    val level: Int,
    val text: String,
    val anchor: String
)

data class MarkdownRenderResult(
    val html: String,
    val meta: MarkdownMeta,
    val toc: List<TocEntry>,
    val headings: List<TocEntry>
)

@Serializable
data class DocsNavNode(
    val title: String,
    val path: String,
    val children: List<DocsNavNode> = emptyList()
)

@Serializable
data class DocsNavTree(
    val sections: List<DocsNavNode> = emptyList()
) {
    fun flatten(): List<DocsNavNode> = sections.flatMap { section ->
        section.children.flatMap { it.flattenNodes() }
    }

    private fun DocsNavNode.flattenNodes(): List<DocsNavNode> =
        listOf(this.copy(children = emptyList())) + children.flatMap { it.flattenNodes() }
}

data class NeighborLinks(
    val previous: NavLink?,
    val next: NavLink?
)

@Serializable
data class NavLink(
    val title: String,
    val path: String
)

data class SeoMeta(
    val title: String,
    val description: String,
    val canonicalUrl: String,
    val ogType: String = "article",
    val ogUrl: String = canonicalUrl,
    val tags: List<String> = emptyList()
)
