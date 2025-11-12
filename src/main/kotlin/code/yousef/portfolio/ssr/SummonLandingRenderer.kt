package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.summon.SummonLandingPage
import code.yousef.summon.seo.HeadScope

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
        head.meta("description", description, null, null, null)
        head.meta(null, title, "og:title", null, null)
        head.meta(null, description, "og:description", null, null)
        head.meta(null, "website", "og:type", null, null)
        head.meta(null, marketingUrl, "og:url", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", title, null, null, null)
        head.meta("twitter:description", description, null, null, null)
        head.link("canonical", marketingUrl, null, null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
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
