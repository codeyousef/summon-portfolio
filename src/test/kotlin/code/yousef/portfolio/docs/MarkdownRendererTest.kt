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
        val markdown = java.nio.file.Files.readString(
            java.nio.file.Paths.get("docs/private/summon-docs/accessibility-and-seo.md")
        )

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
}
