package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.aether.AetherLandingPage
import codes.yousef.summon.seo.HeadScope

class AetherLandingRenderer {

    fun landingPage(): SummonPage = SummonPage(
        head = headBlock(),
        content = {
            AetherLandingPage(
                docsUrl = aetherDocsBaseUrl(),
                apiReferenceUrl = "${aetherDocsBaseUrl().trimEnd('/')}/api-reference"
            )
        }
    )

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        val marketingUrl = aetherMarketingUrl()
        val title = "Aether · Django-inspired Kotlin Multiplatform Web Framework"
        val description =
            "Aether is a Write Once, Deploy Anywhere web framework for Kotlin Multiplatform. Targets JVM (Vert.x + Virtual Threads) and WebAssembly. Type-safe routing, Active Record ORM, composable middleware, and built-in auth."
        head.title(title)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        // Standard description
        head.meta("description", null, description, null, null)
        // OpenGraph
        head.meta(null, "og:title", title, null, null)
        head.meta(null, "og:description", description, null, null)
        head.meta(null, "og:type", "website", null, null)
        head.meta(null, "og:url", marketingUrl, null, null)
        head.meta(null, "og:locale", "en_US", null, null)
        // Twitter
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, title, null, null)
        head.meta("twitter:description", null, description, null, null)
        head.link("canonical", marketingUrl, null, null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "aether-hydration-runtime", "application/javascript", false, false, null)
    }
}
