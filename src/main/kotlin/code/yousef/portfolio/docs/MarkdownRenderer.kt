package code.yousef.portfolio.docs

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.Locale

class MarkdownRenderer(
    private val extensions: List<Extension> = listOf(
        YamlFrontMatterExtension.create(),
        AutolinkExtension.create(),
        TablesExtension.create()
    )
) {
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    fun render(markdown: String, requestPath: String): MarkdownRenderResult {
        val preprocessed = preprocessDirectives(markdown)
        val document = parser.parse(preprocessed.markdown) as Document
        val frontMatter = YamlFrontMatterVisitor().also { document.accept(it) }
        val headingCollector = HeadingCollector(preprocessed.directives.keys)
        document.accept(headingCollector)

        val meta = resolveMeta(headingCollector, frontMatter)
        val toc = headingCollector.tocEntries()
        val typedDocument = MarkdownDocument(
            root = document,
            headingIds = IdentityHashMap(headingCollector.slugMap),
            directives = preprocessed.directives.toMap(),
            requestPath = requestPath
        )

        return MarkdownRenderResult(
            document = typedDocument,
            meta = meta,
            toc = toc,
            headings = toc
        )
    }

    private fun resolveMeta(
        collector: HeadingCollector,
        frontMatter: YamlFrontMatterVisitor
    ): MarkdownMeta {
        val fm = frontMatter.data
        val title = fm["title"]?.firstOrNull() ?: collector.firstHeading ?: "Documentation"
        val description = fm["description"]?.firstOrNull() ?: collector.firstParagraph ?: ""
        val tags = fm["tags"] ?: emptyList()
        val section = fm["section"]?.firstOrNull()
        val sectionTitle = fm["section_title"]?.firstOrNull()
        val order = fm["order"]?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE
        return MarkdownMeta(
            title = title,
            description = description,
            tags = tags,
            section = section,
            sectionTitle = sectionTitle,
            order = order
        )
    }

    private class HeadingCollector(
        private val directiveMarkers: Set<String>
    ) : org.commonmark.node.AbstractVisitor() {
        val slugMap: MutableMap<Heading, String> = IdentityHashMap()
        private val seenSlugs = mutableMapOf<String, Int>()
        private val toc = mutableListOf<TocEntry>()
        var firstHeading: String? = null
        var firstParagraph: String? = null

        override fun visit(heading: Heading) {
            val textContent = heading.plainText()
            if (heading.level == 1 && firstHeading == null) {
                firstHeading = textContent
            }
            val anchor = slugFor(textContent)
            slugMap[heading] = anchor
            if (heading.level in 2..3) {
                toc += TocEntry(level = heading.level, text = textContent, anchor = anchor)
            }
            super.visit(heading)
        }

        override fun visit(paragraph: Paragraph) {
            val textContent = paragraph.plainText()
            if (firstParagraph == null && textContent !in directiveMarkers) {
                firstParagraph = textContent
            }
            super.visit(paragraph)
        }

        fun tocEntries(): List<TocEntry> = toc.toList()

        private fun slugFor(text: String): String {
            val base = text.lowercase(Locale.getDefault())
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .trim()
                .replace("\\s+".toRegex(), "-")
                .ifBlank { "section" }
            val count = seenSlugs.compute(base) { _, value -> (value ?: 0) + 1 } ?: 1
            return if (count == 1) base else "$base-$count"
        }
    }

    private data class PreprocessedMarkdown(
        val markdown: String,
        val directives: Map<String, MarkdownDirective>
    )

    private data class Fence(val marker: Char, val length: Int)

    private fun preprocessDirectives(markdown: String): PreprocessedMarkdown {
        val lines = markdown.lines()
        val output = mutableListOf<String>()
        val directives = linkedMapOf<String, MarkdownDirective>()
        val detailsStack = ArrayDeque<String>()
        val markerPrefix = generateSequence(DIRECTIVE_MARKER_PREFIX) { "${it}_" }
            .first { candidate -> candidate !in markdown }
        var directiveIndex = 0
        var fence: Fence? = null
        var index = 0

        fun addDirective(directive: MarkdownDirective) {
            val marker = "$markerPrefix${directiveIndex++}"
            directives[marker] = directive
            if (output.lastOrNull()?.isNotBlank() == true) output += ""
            output += marker
            output += ""
        }

        while (index < lines.size) {
            val line = lines[index]
            val activeFence = fence
            if (activeFence != null) {
                output += line
                if (isFenceClose(line, activeFence)) fence = null
                index += 1
                continue
            }

            val replOpening = replFenceOpening(line)
            if (replOpening != null) {
                val code = mutableListOf<String>()
                index += 1
                while (index < lines.size && !isFenceClose(lines[index], replOpening)) {
                    code += lines[index]
                    index += 1
                }
                require(index < lines.size) { "Unclosed ai-repl fence" }
                addDirective(MarkdownDirective.AiRepl(code.joinToString("\n")))
                index += 1
                continue
            }

            val opening = fenceOpening(line)
            if (opening != null) {
                fence = opening
                output += line
                index += 1
                continue
            }

            if (!hasDirectiveIndent(line)) {
                output += line
                index += 1
                continue
            }

            val trimmed = line.trim()
            val details = DETAILS_DIRECTIVE.matchEntire(trimmed)
            if (details != null) {
                val id = "details-${directiveIndex}"
                detailsStack.addLast(id)
                addDirective(
                    MarkdownDirective.DetailsStart(
                        id = id,
                        summary = details.groupValues[1].trim().ifBlank { "Details" }
                    )
                )
                index += 1
                continue
            }

            if (DIRECTIVE_CLOSE.matches(trimmed) && detailsStack.isNotEmpty()) {
                addDirective(MarkdownDirective.DetailsEnd(detailsStack.removeLast()))
                index += 1
                continue
            }

            output += line
            index += 1
        }

        require(detailsStack.isEmpty()) { "Unclosed details directive" }

        return PreprocessedMarkdown(
            markdown = output.joinToString("\n"),
            directives = directives
        )
    }

    private fun hasDirectiveIndent(line: String): Boolean {
        val leading = line.takeWhile { it == ' ' }.length
        return leading <= 3 && !line.startsWith('\t')
    }

    private fun fenceOpening(line: String): Fence? {
        if (!hasDirectiveIndent(line)) return null
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = trimmed.takeWhile { it == marker }.length
        return if (length >= 3) Fence(marker, length) else null
    }

    private fun replFenceOpening(line: String): Fence? {
        val opening = fenceOpening(line) ?: return null
        val trimmed = line.trimStart()
        val info = trimmed.drop(opening.length).trim()
        return opening.takeIf { info == AI_REPL_FENCE }
    }

    private fun isFenceClose(line: String, fence: Fence): Boolean {
        if (!hasDirectiveIndent(line)) return false
        val trimmed = line.trimStart()
        val length = trimmed.takeWhile { it == fence.marker }.length
        return length >= fence.length && trimmed.drop(length).isBlank()
    }

    private companion object {
        const val DIRECTIVE_MARKER_PREFIX = "PORTFOLIO_TYPED_MARKDOWN_DIRECTIVE_"
        const val AI_REPL_FENCE = "ai-repl"
        val DETAILS_DIRECTIVE = Regex("""^:::details(?:\s+(.*))?$""")
        val DIRECTIVE_CLOSE = Regex("""^:::\s*$""")
    }
}

internal fun Node.plainText(): String {
    val collector = StringBuilder()

    fun appendNode(node: Node) {
        when (node) {
            is Text -> collector.append(node.literal)
            is Code -> collector.append(node.literal)
            is SoftLineBreak, is HardLineBreak -> collector.append(' ')
        }
        var child = node.firstChild
        while (child != null) {
            appendNode(child)
            child = child.next
        }
    }

    appendNode(this)
    return collector.toString().trim()
}
