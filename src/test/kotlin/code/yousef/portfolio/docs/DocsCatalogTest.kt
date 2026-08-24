package code.yousef.portfolio.docs

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DocsCatalogTest {
    @Test
    fun `catalog snapshot loads lazily once and reload remains explicit`() {
        var loads = 0
        val catalog = DocsCatalog(config(), entryLoader = {
            loads += 1
            emptyList()
        })

        assertEquals(0, loads)
        assertEquals(emptyList(), catalog.allSlugs())
        assertEquals(1, loads)
        assertEquals(emptyList(), catalog.allSlugs())
        assertEquals(1, loads)

        catalog.reload()
        assertEquals(2, loads)
    }

    private fun config() = DocsConfig(
        githubOwner = "owner",
        githubRepo = "repo",
        defaultBranch = "main",
        docsRoot = "docs",
        useContentsApi = false,
        githubToken = null,
        cacheTtlSeconds = 3600,
        enableRedis = false,
        publicOriginDocs = "https://docs.example.com",
        publicOriginPortfolio = "https://example.com",
        docsSource = DocsSource.LOCAL,
        localDocsRoot = Path.of("missing-docs"),
    )
}
