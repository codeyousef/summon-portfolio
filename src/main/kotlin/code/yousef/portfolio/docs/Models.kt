@file:OptIn(kotlin.time.ExperimentalTime::class)
package code.yousef.portfolio.docs

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import org.commonmark.node.Document
import org.commonmark.node.Heading

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
    val document: MarkdownDocument,
    val meta: MarkdownMeta,
    val toc: List<TocEntry>,
    val headings: List<TocEntry>
)

class MarkdownDocument internal constructor(
    internal val root: Document,
    internal val headingIds: Map<Heading, String>,
    internal val directives: Map<String, MarkdownDirective>,
    internal val requestPath: String,
    internal val linkContext: MarkdownLinkContext? = null,
    internal val pairParagraphsWithCode: Boolean = false
) {
    internal fun withLinkContext(context: MarkdownLinkContext): MarkdownDocument =
        MarkdownDocument(
            root = root,
            headingIds = headingIds,
            directives = directives,
            requestPath = requestPath,
            linkContext = context,
            pairParagraphsWithCode = pairParagraphsWithCode
        )

    internal fun withParagraphCodePairs(): MarkdownDocument =
        MarkdownDocument(
            root = root,
            headingIds = headingIds,
            directives = directives,
            requestPath = requestPath,
            linkContext = linkContext,
            pairParagraphsWithCode = true
        )
}

internal sealed interface MarkdownDirective {
    data class DetailsStart(val id: String, val summary: String) : MarkdownDirective
    data class DetailsEnd(val id: String) : MarkdownDirective
    data class AiRepl(val code: String) : MarkdownDirective
}

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
