package code.yousef.portfolio.ssr

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.scratchpad.ScratchpadPage
import codes.yousef.summon.seo.HeadScope

/**
 * Renderer for the Scratchpad page.
 * Uses a separate head configuration to:
 * - Prevent search engine indexing (noindex)
 * - Load scratchpad-specific JavaScript files
 * - Skip standard portfolio scripts (no Sigil, no Konami on scratchpad)
 */
class ScratchpadRenderer {

    fun scratchpadPage(locale: PortfolioLocale = PortfolioLocale.EN): SummonPage {
        return SummonPage(
            head = scratchpadHeadBlock(),
            content = {
                ScratchpadPage(locale = locale)
            },
            locale = locale
        )
    }

    private fun scratchpadHeadBlock(): (HeadScope) -> Unit = { head ->
        head.title("The Scratchpad | Yousef")

        // Prevent indexing - this is a hidden page
        head.meta("robots", null, "noindex, nofollow", null, null)

        // Basic viewport
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)

        // OpenGraph (for when people share the link anyway)
        head.meta(null, "og:title", "The Scratchpad", null, null)
        head.meta(null, "og:description", "Where ideas come to die.", null, null)
        head.meta(null, "og:type", "website", null, null)

        // Base hydration script
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)

        // Scratchpad-specific scripts (deferred to load after DOM)
        head.script("/static/scratchpad-canvas.js", "scratchpad-canvas", "application/javascript", false, true, null)
        head.script("/static/scratchpad-physics.js", "scratchpad-physics", "application/javascript", false, true, null)
        head.script(
            "/static/scratchpad-terminal.js",
            "scratchpad-terminal",
            "application/javascript",
            false,
            true,
            null
        )
    }
}
