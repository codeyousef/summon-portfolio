package code.yousef.portfolio.docs

import java.util.concurrent.ConcurrentHashMap

private data class NavEntry(
    val title: String,
    val path: String,
    val sectionKey: String,
    val sectionTitle: String?,
    val order: Int
)

class DocsNavIndex {
    private val entries = ConcurrentHashMap<String, NavEntry>()

    fun record(path: String, meta: MarkdownMeta) {
        val normalizedPath = normalizePath(path)
        val sectionKey = meta.section?.ifBlank { null } ?: normalizedPath.trim('/')
            .substringBefore('/', "")
            .ifBlank { "overview" }
        val sectionTitle =
            meta.sectionTitle ?: sectionKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        entries[normalizedPath] = NavEntry(
            title = meta.title,
            path = normalizedPath,
            sectionKey = sectionKey,
            sectionTitle = sectionTitle,
            order = meta.order
        )
    }

    fun buildTree(): DocsNavTree {
        val grouped = entries.values.groupBy { it.sectionKey }
        val sections = grouped.entries.sortedBy { it.key }.map { (sectionKey, nodes) ->
            val sectionTitle = nodes.firstNotNullOfOrNull { it.sectionTitle } ?: sectionKey.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            val sectionPath = if (sectionKey == "overview") "/" else "/$sectionKey"
            DocsNavNode(
                title = sectionTitle,
                path = sectionPath,
                children = nodes.sortedWith(compareBy<NavEntry> { it.order }.thenBy { it.title.lowercase() })
                    .map { DocsNavNode(it.title, it.path) }
            )
        }
        return DocsNavTree(sections)
    }

    private fun normalizePath(path: String): String {
        return "/" + path.trim().trim('/').ifEmpty { "" }
    }
}
