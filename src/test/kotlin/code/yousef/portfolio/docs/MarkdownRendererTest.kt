package code.yousef.portfolio.docs

import code.yousef.portfolio.docs.summon.components.Prose
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownRendererTest {
    private val renderer = MarkdownRenderer()

    @Test
    fun `should keep heading ids in typed output`() {
        val markdown = """
            # Title

            ### Meta Tags
            Paragraph
        """.trimIndent()

        val html = render(renderer.render(markdown, "/test").document)

        assertTrue(
            actual = html.contains("id=\"meta-tags\""),
            message = "Expected typed output to contain slugged heading id"
        )
    }

    @Test
    fun `link rewriter should preserve heading ids`() {
        val markdown = """
            # Accessibility and SEO

            ### Meta Tags
            Some content about meta tags.
        """.trimIndent()

        val rendered = renderer.render(markdown, "/accessibility-and-seo")
        val rewritten = LinkRewriter().rewrite(
            document = rendered.document,
            requestPath = "/accessibility-and-seo",
            repoPath = "docs/accessibility-and-seo.md",
            docsRoot = "docs",
            branch = "main"
        )

        assertTrue(render(rewritten).contains("id=\"meta-tags\""))
    }

    @Test
    fun `should render GFM tables with typed elements`() {
        val markdown = """
            # Table Test

            | Header 1 | Header 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
            | Cell 3   | Cell 4   |
        """.trimIndent()

        val document = Jsoup.parse(render(renderer.render(markdown, "/test").document))

        assertNotNull(document.selectFirst("table"))
        assertEquals(2, document.select("th").size)
        assertEquals(4, document.select("td").size)
    }

    @Test
    fun `link rewriter should prepend basePath to markdown links`() {
        val markdown = """
            # Components Guide

            Check out [styling](styling.md) for more info.

            Also see [api-reference](api-reference/index.md).
        """.trimIndent()

        val rendered = renderer.render(markdown, "/components")
        val rewritten = LinkRewriter().rewrite(
            document = rendered.document,
            requestPath = "/components",
            repoPath = "docs/components.md",
            docsRoot = "docs",
            branch = "main",
            basePath = "/docs"
        )
        val document = Jsoup.parse(render(rewritten))

        assertEquals(1, document.select("a[href='/docs/styling'][data-doc-link=true]").size)
        assertEquals(1, document.select("a[href='/docs/api-reference/index'][data-doc-link=true]").size)
    }

    @Test
    fun `unsafe URL schemes never become links`() {
        val markdown = "[unsafe](javascript:alert(1)) and [safe](https://example.com)"
        val document = Jsoup.parse(render(renderer.render(markdown, "/test").document))

        assertFalse(document.html().contains("javascript:", ignoreCase = true))
        val safe = assertNotNull(document.selectFirst("a[href='https://example.com']"))
        assertEquals("_blank", safe.attr("target"))
        assertEquals("noopener", safe.attr("rel"))
    }

    @Test
    fun `details and repl markdown directives become typed components`() {
        val markdown = """
            :::details Show solution

            **Typed answer**

            :::

            ~~~ai-repl
            print("safe")
            ~~~
        """.trimIndent()

        val document = Jsoup.parse(render(renderer.render(markdown, "/ai/test").document))

        assertEquals("Show solution", document.selectFirst("details > summary")?.text())
        assertEquals("print(\"safe\")", document.selectFirst("div.ai-repl")?.attr("data-code"))
        assertEquals("Typed answer", document.selectFirst("details strong")?.text())
    }

    private fun render(document: MarkdownDocument): String = synchronized(renderLock) {
        val platformRenderer = PlatformRenderer()
        setPlatformRenderer(platformRenderer)
        try {
            platformRenderer.renderComposableRootWithHydration("en", "ltr") {
                Prose(document)
            }
        } finally {
            clearPlatformRenderer()
        }
    }

    private companion object {
        val renderLock = Any()
    }
}
