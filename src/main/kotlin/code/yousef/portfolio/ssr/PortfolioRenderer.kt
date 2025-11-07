package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.PortfolioLandingPage
import code.yousef.summon.seo.HeadScope

class PortfolioRenderer(
    private val contentService: PortfolioContentService = PortfolioContentService.default()
) {

    fun landingPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(locale),
            content = {
                PortfolioLandingPage(content = content, locale = locale, servicesModalOpen = servicesModalOpen)
            }
        )
    }

    private fun headBlockFor(locale: PortfolioLocale): (HeadScope) -> Unit = { head ->
        val canonical = canonicalUrl(locale)
        head.title("Yousef Baitalmal · Summon Portfolio")
        head.meta(
            "description",
            "Engineering from first principles — systems, frameworks, and immersive experiences powered by Summon and Ktor.",
            null,
            null,
            null
        )
        head.meta(null, "Yousef Baitalmal · Engineering from First Principles", "og:title", null, null)
        head.meta(
            null,
            "Follow the journey across language, framework, and experience layers built with Summon.",
            "og:description",
            null,
            null
        )
        head.meta(null, "website", "og:type", null, null)
        head.meta(null, canonical, "og:url", null, null)
        head.meta(null, locale.code, "og:locale", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", "Yousef Baitalmal · Summon Portfolio", null, null, null)
        head.meta(
            "twitter:description",
            "Summon-powered storytelling for systems, frameworks, and immersive experiences.",
            null,
            null,
            null
        )
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.AR), "ar", null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
    }

    private fun canonicalUrl(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> SITE_URL
            else -> "$SITE_URL/${locale.code}"
        }
}
