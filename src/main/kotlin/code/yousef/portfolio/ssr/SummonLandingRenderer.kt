package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.summon.SummonLandingPage
import codes.yousef.summon.seo.HeadScope

class SummonLandingRenderer {

    fun landingPage(): SummonPage = SummonPage(
        head = headBlock(),
        content = {
            SummonLandingPage(
                docsUrl = docsUrl(),
                apiReferenceUrl = apiReferenceUrl()
            )
        }
    )

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        val marketingUrl = summonMarketingUrl()
        val title = "Summon · Kotlin Multiplatform frontend framework"
        val description =
            "Summon is a Kotlin Multiplatform frontend framework for shipping high-performance interfaces across web, mobile, and desktop."
        head.title(title)
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
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, true, null)
    }

    private fun docsUrl(): String {
        val override = System.getenv("SUMMON_DOCS_URL")?.takeIf { it.isNotBlank() }
        return (override ?: docsBaseUrl()).trimEnd('/')
    }

    private fun apiReferenceUrl(): String {
        val override = System.getenv("SUMMON_API_REFERENCE_URL")?.takeIf { it.isNotBlank() }
        return override ?: "${summonMarketingUrl().trimEnd('/')}/api-reference"
    }
}
