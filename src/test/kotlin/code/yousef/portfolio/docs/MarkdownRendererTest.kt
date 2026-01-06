package code.yousef.portfolio.docs

import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownRendererTest {
    private val renderer = MarkdownRenderer()

    @Test
    fun `should keep heading ids in sanitized html`() {
        val markdown = """
            # Title
            \n
            ### Meta Tags
            Paragraph
        """.trimIndent()

        val result = renderer.render(markdown, "/test")

        assertTrue(
            actual = result.html.contains("id=\"meta-tags\""),
            message = "Expected html to contain slugged heading id"
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
        val rewritten = LinkRewriter().rewriteHtml(
            html = rendered.html,
            requestPath = "/accessibility-and-seo",
            repoPath = "docs/private/summon-docs/accessibility-and-seo.md",
            docsRoot = "docs/private/summon-docs",
            branch = "main"
        )

        assertTrue(
            actual = rewritten.contains("id=\"meta-tags\""),
            message = "Expected rewritten html to retain heading ids"
        )
    }

    @Test
    fun `should render GFM tables correctly`() {
        val markdown = """
            # Table Test

            | Header 1 | Header 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
            | Cell 3   | Cell 4   |
        """.trimIndent()

        val result = renderer.render(markdown, "/test")
        println("Rendered HTML: ${result.html}")

        assertTrue(
            actual = result.html.contains("<table>"),
            message = "Expected html to contain <table> tag but got: ${result.html}"
        )
        assertTrue(
            actual = result.html.contains("<th>"),
            message = "Expected html to contain <th> tag"
        )
        assertTrue(
            actual = result.html.contains("<td>"),
            message = "Expected html to contain <td> tag"
        )
    }

    @Test
    fun `link rewriter should prepend basePath to markdown links`() {
        val markdown = """
            # Components Guide

            Check out [styling](styling.md) for more info.
            
            Also see [api-reference](api-reference/index.md).
        """.trimIndent()

        val rendered = renderer.render(markdown, "/components")
        val rewritten = LinkRewriter().rewriteHtml(
            html = rendered.html,
            requestPath = "/components",
            repoPath = "docs/private/summon-docs/components.md",
            docsRoot = "docs/private/summon-docs",
            branch = "main",
            basePath = "/docs"
        )

        assertTrue(
            actual = rewritten.contains("href=\"/docs/styling\""),
            message = "Expected link to styling to have /docs prefix but got: $rewritten"
        )
        assertTrue(
            actual = rewritten.contains("href=\"/docs/api-reference/index\""),
            message = "Expected link to api-reference to have /docs prefix but got: $rewritten"
        )
    }
}
