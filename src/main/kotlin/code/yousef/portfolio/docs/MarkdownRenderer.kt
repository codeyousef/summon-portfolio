package code.yousef.portfolio.docs

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MarkdownRenderer(
    extensions: List<Extension> = listOf(YamlFrontMatterExtension.create(), AutolinkExtension.create())
) {
    private val parser: Parser = Parser.builder().extensions(extensions).build()
    private val sanitizer: PolicyFactory = HtmlPolicyBuilder()
        .allowElements(
            "a",
            "p",
            "strong",
            "em",
            "code",
            "pre",
            "blockquote",
            "ul",
            "ol",
            "li",
            "table",
            "thead",
            "tbody",
            "tr",
            "th",
            "td",
            "img",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "hr",
            "span"
        )
        .allowUrlProtocols("https", "http", "mailto")
        .allowAttributes("href", "title", "target", "rel").onElements("a")
        .allowAttributes("src", "alt", "title").onElements("img")
        .allowAttributes("class").onElements("code", "pre", "table")
        .allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6")
        .allowStyling()
        .toFactory()

    fun render(markdown: String, requestPath: String): MarkdownRenderResult {
        val document = parser.parse(markdown) as Document
        val frontMatter = YamlFrontMatterVisitor().also { document.accept(it) }
        val headingCollector = HeadingCollector()
        document.accept(headingCollector)

        val slugMap = headingCollector.slugMap

        val renderer = HtmlRenderer.builder()
            .attributeProviderFactory {
                AttributeProvider { node, _, attributes ->
                    if (node is Heading) {
                        slugMap[node]?.let { attributes["id"] = it }
                    }
                }
            }
            .build()

        val rawHtml = renderer.render(document)
        val sanitized = sanitizer.sanitize(rawHtml)

        val meta = resolveMeta(headingCollector, frontMatter)
        val toc = headingCollector.tocEntries()

        return MarkdownRenderResult(
            html = sanitized,
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

    private class HeadingCollector : org.commonmark.node.AbstractVisitor() {
        val slugMap: MutableMap<Node, String> = ConcurrentHashMap()
        private val seenSlugs = mutableMapOf<String, Int>()
        private val toc = mutableListOf<TocEntry>()
        var firstHeading: String? = null
        var firstParagraph: String? = null

        override fun visit(heading: Heading) {
            val textContent = heading.textContent()
            if (heading.level == 1 && firstHeading == null) {
                firstHeading = textContent
            }
            if (heading.level in 2..3) {
                val anchor = slugFor(textContent)
                slugMap[heading] = anchor
                toc += TocEntry(level = heading.level, text = textContent, anchor = anchor)
            } else {
                slugMap[heading] = slugFor(textContent)
            }
            super.visit(heading)
        }

        override fun visit(paragraph: Paragraph) {
            if (firstParagraph == null) {
                firstParagraph = paragraph.textContent()
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

        private fun Node.textContent(): String {
            val collector = StringBuilder()
            this.accept(object : org.commonmark.node.AbstractVisitor() {
                override fun visit(text: Text) {
                    collector.append(text.literal)
                }
            })
            return collector.toString()
        }
    }
}
