package code.yousef.portfolio.server

import code.yousef.portfolio.ssr.SeenLandingRenderer
import code.yousef.portfolio.ssr.SummonPage
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SummonDocumentHeadTest {
    @Test
    fun `custom Seen title replaces Summon fallback title`() {
        val html = render(SeenLandingRenderer(packagesEnabled = true).landingPage())

        assertEquals(
            listOf("Seen · Multi-Language Systems Programming Language"),
            Jsoup.parse(html).select("head > title").eachText(),
        )
        assertFalse(html.contains("<title>Summon App</title>"))
    }

    @Test
    fun `Summon fallback title remains when page has no custom title`() {
        val html = render(SummonPage(content = {}))

        assertEquals(listOf("Summon App"), Jsoup.parse(html).select("head > title").eachText())
    }

    private fun render(page: SummonPage): String = synchronized(renderLock) {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            renderer.renderSummonDocument(page)
        } finally {
            clearPlatformRenderer()
        }
    }

    private companion object {
        val renderLock = Any()
    }
}
