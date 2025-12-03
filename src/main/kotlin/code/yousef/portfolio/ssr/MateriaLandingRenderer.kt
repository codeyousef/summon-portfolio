package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.materia.MateriaLandingPage
import codes.yousef.summon.seo.HeadScope

class MateriaLandingRenderer {

    fun landingPage(): SummonPage = SummonPage(
        head = headBlock(),
        content = {
            MateriaLandingPage(
                docsUrl = materiaDocsBaseUrl(),
                apiReferenceUrl = "${materiaDocsBaseUrl().trimEnd('/')}/api-reference"
            )
        }
    )

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        val marketingUrl = materiaMarketingUrl()
        val title = "Materia · Kotlin Multiplatform 3D Graphics Library"
        val description =
            "Kotlin Multiplatform 3D graphics library with WebGPU/Vulkan backends. Write 3D apps once, deploy on JVM, Web, Android, iOS & Native with type-safe math, scene graph, materials & more."
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
        head.script(HYDRATION_SCRIPT_PATH, "materia-hydration-runtime", "application/javascript", false, false, null)
    }
}
