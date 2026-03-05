package code.yousef.portfolio.seen

import code.yousef.portfolio.ssr.HYDRATION_SCRIPT_PATH
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ui.seen.SeenPlaygroundPage
import codes.yousef.summon.seo.HeadScope

class SeenPlaygroundRenderer {

    fun playgroundPage(): SummonPage {
        return SummonPage(
            head = playgroundHeadBlock(),
            content = {
                SeenPlaygroundPage()
            }
        )
    }

    private fun playgroundHeadBlock(): (HeadScope) -> Unit = { head ->
        head.title("Seen Playground - Try Seen Programming Language")

        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta(null, "og:title", "Seen Playground", null, null)
        head.meta(null, "og:description", "Try the Seen programming language in your browser", null, null)
        head.meta(null, "og:type", "website", null, null)
        head.meta(
            "description",
            null,
            "Interactive playground for the Seen programming language. Write, compile, and run Seen code directly in your browser.",
            null,
            null
        )

        // Monaco Editor loader
        head.script(
            "https://cdn.jsdelivr.net/npm/monaco-editor@0.45.0/min/vs/loader.js",
            "monaco-loader",
            "application/javascript",
            false,
            false,
            null
        )

        // Playground script
        head.script("/static/seen-playground.js", "seen-playground", "application/javascript", false, true, null)

        // Hydration
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
    }
}
