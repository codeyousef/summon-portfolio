package code.yousef.portfolio.ui.seen

import code.yousef.portfolio.seen.ExecutionResult
import code.yousef.portfolio.seen.SEEN_PLAYGROUND_MAX_CODE_LENGTH
import code.yousef.portfolio.seen.SeenPlaygroundCatalog
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SeenPlaygroundPageTest {
    @Test
    fun `renders native bounded load and run forms`() {
        val document = Jsoup.parse(render(SeenPlaygroundCatalog.state(language = "ar", sample = "classes")))
        val loadForm = assertNotNull(document.selectFirst("form[action='/playground'][method=get]"))
        val runForm = assertNotNull(document.selectFirst("form[action='/playground/run'][method=post]"))
        val source = assertNotNull(runForm.selectFirst("textarea[name=code]"))

        assertEquals("true", loadForm.attr("data-native-form"))
        assertEquals("true", runForm.attr("data-native-form"))
        assertEquals(SEEN_PLAYGROUND_MAX_CODE_LENGTH.toString(), source.attr("maxlength"))
        assertEquals("ar", runForm.selectFirst("input[name=language]")?.attr("value"))
        assertEquals("classes", runForm.selectFirst("input[name=sample]")?.attr("value"))
        assertEquals("ar", loadForm.selectFirst("select[name=language] option[selected]")?.attr("value"))
        assertTrue(source.text().contains("فئة نقطة"))
        assertFalse(document.html().contains("seen-playground.js"))
        assertFalse(document.html().contains("monaco-editor"))
    }

    @Test
    fun `renders execution errors as text in an accessible output panel`() {
        val result = ExecutionResult(
            output = "",
            error = "<script>not markup</script>",
            exitCode = 1,
            compileTimeMs = 17,
            runTimeMs = 0,
        )
        val document = Jsoup.parse(
            render(
                SeenPlaygroundCatalog.state(
                    code = "fun main() {}",
                    result = result,
                )
            )
        )
        val output = assertNotNull(document.selectFirst("[role=alert][aria-live=polite]"))

        assertEquals("<script>not markup</script>", output.text())
        assertEquals(0, output.select("script").size)
        assertTrue(document.text().contains("compile + run: 17 ms"))
        assertTrue(document.text().contains("Failed (1)"))
    }

    @Test
    fun `normalizes unsupported options to the default sample`() {
        val state = SeenPlaygroundCatalog.state(language = "unsupported", sample = "missing")

        assertEquals("en", state.language)
        assertEquals("hello-world", state.sample)
        assertTrue(state.code.contains("Hello, World!"))
    }

    private fun render(state: code.yousef.portfolio.seen.SeenPlaygroundViewState): String =
        synchronized(renderLock) {
            val renderer = PlatformRenderer()
            setPlatformRenderer(renderer)
            try {
                renderer.renderComposableRootWithHydration("en", "ltr") {
                    SeenPlaygroundPage(state = state, packagesUrl = "/packages")
                }
            } finally {
                clearPlatformRenderer()
            }
        }

    private companion object {
        val renderLock = Any()
    }
}
