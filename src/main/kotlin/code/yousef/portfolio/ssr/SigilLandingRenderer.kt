package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.sigil.SigilLandingPage
import codes.yousef.summon.seo.HeadScope

class SigilLandingRenderer {

    fun landingPage(): SummonPage = SummonPage(
        head = headBlock(),
        content = {
            SigilLandingPage(
                docsUrl = sigilDocsBaseUrl(),
                apiReferenceUrl = "${sigilDocsBaseUrl().trimEnd('/')}/api-reference"
            )
        }
    )

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        val marketingUrl = sigilMarketingUrl()
        val title = "Sigil · Declarative 3D for Kotlin Multiplatform & Compose"
        val description =
            "Sigil brings declarative 3D rendering to Kotlin Multiplatform using Compose syntax. Build reactive 3D scenes with Box, Sphere, Group, and Light composables—powered by Materia's WebGPU/Vulkan backends."
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
        head.script(HYDRATION_SCRIPT_PATH, "sigil-hydration-runtime", "application/javascript", false, false, null)
    }
}
