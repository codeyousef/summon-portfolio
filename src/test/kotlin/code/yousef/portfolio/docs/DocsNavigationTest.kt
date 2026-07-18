package code.yousef.portfolio.docs

import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DocsNavigationTest {
    @Test
    fun `Seen docs use shared navigation and select the longest API context path`() {
        val router = DocsRouter(
            seoExtractor = SeoExtractor(DocsConfig.seenFromEnv()),
            seenPackagesEnabled = true,
        )
        val page = router.notFound(
            requestPath = "/docs/api-reference/types/missing",
            sidebar = DocsNavTree(),
            origin = "https://seen.dev.yousef.codes",
            basePath = "/docs",
        )
        val document = Jsoup.parse(render(page, "seen.dev.yousef.codes"))

        assertNotNull(document.selectFirst("header.site-navigation-shell"))
        assertNotNull(document.selectFirst("nav[aria-label=Primary][data-navigation-layer=global]"))
        val contextNav = assertNotNull(
            document.selectFirst("nav[aria-label='Seen navigation'][data-navigation-layer=context]"),
        )
        assertEquals(
            listOf(
                "context-overview",
                "context-packages",
                "context-playground",
                "context-documentation",
                "context-api-reference",
            ),
            contextNav.select("a[data-nav-id^=context-]")
                .map { it.attr("data-nav-id") }
                .filterNot { it == "context-brand" },
        )
        assertEquals(
            listOf("context-api-reference"),
            contextNav.select("a[aria-current=page]").map { it.attr("data-nav-id") },
        )
    }

    private fun render(page: SummonPage, host: String): String = synchronized(renderLock) {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            runBlocking {
                EnvironmentLinksRegistry.withLinks(resolveEnvironmentLinks(host)) {
                    renderer.renderComposableRootWithHydration(page.locale.code, page.locale.direction) {
                        page.content()
                    }
                }
            }
        } finally {
            clearPlatformRenderer()
        }
    }

    private companion object {
        val renderLock = Any()
    }
}
