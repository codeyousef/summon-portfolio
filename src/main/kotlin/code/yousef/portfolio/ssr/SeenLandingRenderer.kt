package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.seen.SeenLandingPage
import codes.yousef.summon.seo.HeadScope

class SeenLandingRenderer(private val packagesEnabled: Boolean = false) {

    fun landingPage(): SummonPage = SummonPage(
        head = headBlock(),
        content = {
            val docsUrl = seenDocsBaseUrl()
            SeenLandingPage(
                playgroundUrl = "/playground",
                docsUrl = docsUrl,
                packagesUrl = if (packagesEnabled) "/packages" else null,
            )
        }
    )

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        val marketingUrl = seenMarketingUrl()
        val title = "Seen · Multi-Language Systems Programming Language"
        val description =
            "Seen is a multi-language systems programming language with a self-hosted compiler and LLVM backend. Write code using keywords in your native language."
        head.title(title)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("description", null, description, null, null)
        head.meta(null, "og:title", title, null, null)
        head.meta(null, "og:description", description, null, null)
        head.meta(null, "og:type", "website", null, null)
        head.meta(null, "og:url", marketingUrl, null, null)
        head.meta(null, "og:locale", "en_US", null, null)
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, title, null, null)
        head.meta("twitter:description", null, description, null, null)
        head.link("canonical", marketingUrl, null, null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "seen-hydration-runtime", "application/javascript", false, false, null)
    }
}
