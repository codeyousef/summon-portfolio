package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SiteNavigationTest {
    @Test
    fun `renders one semantic shell with global and Seen context navigation`() {
        val html = render(host = "seen.dev.yousef.codes") {
            SiteNavigation(
                activeDestination = GlobalNavigationDestination.ECOSYSTEM,
                context = LandingBranding.seen(
                    docsUrl = "https://seen.dev.yousef.codes/docs",
                    apiReferenceUrl = "https://seen.dev.yousef.codes/docs/api-reference",
                    packagesUrl = "/packages",
                    playgroundUrl = "/playground",
                ).navigationContext(ContextNavigationIds.PACKAGES),
            )
        }
        val document = Jsoup.parse(html)
        val header = document.select("header.site-navigation-shell").single()

        assertNotNull(header.selectFirst("nav[aria-label=Primary][data-navigation-layer=global]"))
        val contextNav = header.selectFirst("nav[aria-label='Seen navigation'][data-navigation-layer=context]")
        assertNotNull(contextNav)
        assertEquals(
            listOf("context-overview", "context-packages", "context-playground", "context-documentation", "context-api-reference"),
            contextNav.select("a[data-nav-id^=context-]")
                .map { it.attr("data-nav-id") }
                .filterNot { it == "context-brand" },
        )
        assertEquals(
            listOf("context-packages"),
            contextNav.select("a[aria-current=page]").map { it.attr("data-nav-id") },
        )
        assertEquals("", contextNav.selectFirst("img")?.attr("alt"))
        assertTrue(header.select("a[href='https://dev.yousef.codes']").isNotEmpty())
        assertTrue(header.select("a[href='https://summon.dev.yousef.codes']").isNotEmpty())
        assertTrue(header.select("a[href='https://seen.dev.yousef.codes']").isNotEmpty())
        assertTrue(header.select("button.site-nav-primary-toggle[aria-expanded=false][aria-controls]").isNotEmpty())
        assertTrue(header.select("button.site-nav-primary-toggle[data-action]").isNotEmpty())
        assertEquals(1, header.select("a[data-nav-id=home]").size)
        assertEquals(1, header.select("a[aria-current=page]").size)
        assertFalse(header.select("[data-hamburger-toggle=true]").isNotEmpty())
    }

    @Test
    fun `keeps Arabic portfolio routes localized without inventing missing media routes`() {
        val html = render(host = "dev.yousef.codes", locale = PortfolioLocale.AR) {
            AppHeader(
                locale = PortfolioLocale.AR,
                activeDestination = GlobalNavigationDestination.WORK,
                context = workWithMeNavigationContext(
                    PortfolioLocale.AR,
                    ContextNavigationIds.CONSULTING,
                ),
            )
        }
        val document = Jsoup.parse(html)
        val hrefs = document.select("header.site-navigation-shell a").map { it.attr("href") }.toSet()

        assertTrue("https://dev.yousef.codes/ar/projects" in hrefs)
        assertTrue("https://dev.yousef.codes/ar/services" in hrefs)
        assertTrue("https://dev.yousef.codes/ar/full-time" in hrefs)
        assertTrue("https://dev.yousef.codes/services" in hrefs)
        assertTrue("#contact" in hrefs)
        assertTrue("https://dev.yousef.codes/photography" in hrefs)
        assertTrue("https://dev.yousef.codes/blog" in hrefs)
        assertFalse("https://dev.yousef.codes/ar/photography" in hrefs)
        assertFalse("https://dev.yousef.codes/ar/blog" in hrefs)
    }

    @Test
    fun `renders one current home link across responsive layouts`() {
        val document = Jsoup.parse(render(host = "dev.yousef.codes") {
            AppHeader(
                locale = PortfolioLocale.EN,
                activeDestination = GlobalNavigationDestination.HOME,
            )
        })
        val primary = assertNotNull(document.selectFirst("nav[aria-label=Primary]"))

        assertEquals(1, primary.select("a[data-nav-id=home]").size)
        assertEquals(1, primary.select("a[aria-current=page]").size)
        assertEquals(1, primary.select("a[data-nav-id=projects]").size)
        assertEquals(1, primary.select("a[data-nav-id=photography]").size)
        assertEquals(1, primary.select("a[data-nav-id=blog]").size)
        assertEquals(1, primary.select("a[data-nav-id=work]").size)
    }

    private fun render(
        host: String,
        locale: PortfolioLocale = PortfolioLocale.EN,
        content: @codes.yousef.summon.annotation.Composable () -> Unit,
    ): String = synchronized(renderLock) {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            runBlocking {
                EnvironmentLinksRegistry.withLinks(resolveEnvironmentLinks(host)) {
                    renderer.renderComposableRootWithHydration(locale.code, locale.direction, content)
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
