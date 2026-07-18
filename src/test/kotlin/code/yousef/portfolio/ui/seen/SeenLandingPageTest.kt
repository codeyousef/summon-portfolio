package code.yousef.portfolio.ui.seen

import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SeenLandingPageTest {
    @Test
    fun `shows packages in the Seen context rail and hero only when enabled`() {
        val enabled = render(packagesUrl = "/packages")
        val disabled = render(packagesUrl = null)
        val enabledDocument = Jsoup.parse(enabled)
        val contextNav = enabledDocument.selectFirst("nav[aria-label='Seen navigation']")

        assertNotNull(contextNav)
        assertEquals(1, contextNav.select("a[href=/packages][data-nav-id=context-packages]").size)
        assertEquals(2, Regex("href=\\\"/packages\\\"").findAll(enabled).count())
        assertEquals(0, Regex("href=\\\"/packages\\\"").findAll(disabled).count())
    }

    private fun render(packagesUrl: String?): String = synchronized(renderLock) {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            renderer.renderComposableRootWithHydration("en", "ltr") {
                SeenLandingPage(
                    playgroundUrl = "/playground",
                    docsUrl = "/docs",
                    packagesUrl = packagesUrl,
                )
            }
        } finally {
            clearPlatformRenderer()
        }
    }

    private companion object {
        val renderLock = Any()
    }
}
