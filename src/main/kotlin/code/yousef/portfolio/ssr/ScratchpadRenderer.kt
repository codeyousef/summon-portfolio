package code.yousef.portfolio.ssr

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.scratchpad.ScratchpadPage
import codes.yousef.summon.seo.HeadScope

/**
 * Renderer for the Scratchpad page.
 * Uses a separate head configuration and keeps the page server-native.
 */
class ScratchpadRenderer {

    fun scratchpadPage(
        locale: PortfolioLocale = PortfolioLocale.EN,
        command: String? = null
    ): SummonPage {
        return SummonPage(
            head = scratchpadHeadBlock(),
            content = {
                ScratchpadPage(locale = locale, command = command)
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
    }
}
