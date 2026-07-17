package code.yousef.portfolio.ui.seen

import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

class SeenLandingPageTest {
    @Test
    fun `shows packages in desktop mobile and hero navigation only when enabled`() {
        val enabled = render(packagesUrl = "/packages")
        val disabled = render(packagesUrl = null)

        assertEquals(3, Regex("href=\\\"/packages\\\"").findAll(enabled).count())
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
