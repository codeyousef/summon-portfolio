package code.yousef.portfolio.ssr

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.fifthwall.FifthWallPage
import code.yousef.portfolio.ui.fifthwall.FifthWallUiState
import codes.yousef.summon.seo.HeadScope

class FifthWallRenderer {

    internal fun fifthWallPage(
        state: FifthWallUiState,
        locale: PortfolioLocale = PortfolioLocale.EN
    ): SummonPage {
        return SummonPage(
            head = headBlock(),
            content = {
                FifthWallPage(state)
            },
            locale = locale
        )
    }

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        head.title("Fifth Wall | Courier Protocol 3D")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("description", null, "Courier Protocol is a native Summon, Sigil, and Materia 3D delivery puzzle.", null, null)
        head.meta(null, "og:title", "Fifth Wall | Courier Protocol 3D", null, null)
        head.meta(null, "og:description", "Route packages through a real 3D warehouse built with Sigil and Materia.", null, null)
        head.meta(null, "og:type", "website", null, null)

        head.link("preconnect", "https://fonts.googleapis.com", null, null, null, null)
        head.link("preconnect", "https://fonts.gstatic.com", null, null, null, "anonymous")
        head.link(
            "stylesheet",
            "https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Space+Grotesk:wght@400;600;700&display=swap",
            null,
            null,
            null,
            null
        )

        head.link("stylesheet", "/static/fifth-wall.css", null, null, null, null)
        head.script("/static/fifth-wall-sigil-hydration.js", "sigil-hydration-runtime", "application/javascript", false, false, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
        head.script("/static/fifth-wall-interactions.js", "fifth-wall-interactions", "application/javascript", false, false, null)
        head.script("/static/fifth-wall-telemetry.js", "fifth-wall-telemetry", "application/javascript", false, false, null)
    }
}
