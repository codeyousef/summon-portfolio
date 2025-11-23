package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.PortfolioLandingPage
import code.yousef.portfolio.ui.projects.ProjectsPage
import code.yousef.portfolio.ui.services.ServicesPage
import codes.yousef.summon.seo.HeadScope

class PortfolioRenderer(
    private val contentService: PortfolioContentService = PortfolioContentService.default()
) {

    fun landingPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Yousef · Portfolio",
                description = "Systems, frameworks, and immersive experiences crafted from first principles."
            ),
            content = {
                PortfolioLandingPage(content = content, locale = locale, servicesModalOpen = servicesModalOpen)
            }
        )
    }

    fun projectsPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Projects · Yousef",
                description = "Featured language, framework, and experience builds from the studio."
            ),
            content = {
                ProjectsPage(
                    content = content,
                    locale = locale,
                    servicesModalOpen = servicesModalOpen
                )
            }
        )
    }

    fun servicesPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Services · Yousef",
                description = "Engagements across systems engineering, framework design, and interactive experiences."
            ),
            content = {
                ServicesPage(
                    content = content,
                    locale = locale,
                    servicesModalOpen = servicesModalOpen
                )
            }
        )
    }

    private fun headBlockFor(
        locale: PortfolioLocale,
        pageTitle: String,
        description: String
    ): (HeadScope) -> Unit = { head ->
        val canonical = canonicalUrl(locale)
        head.title(pageTitle)
        // Standard name=description
        head.meta("description", null, description, null, null)
        // OpenGraph: property attributes
        head.meta(null, "og:title", pageTitle, null, null)
        head.meta(null, "og:description", description, null, null)
        head.meta(null, "og:type", "website", null, null)
        head.meta(null, "og:url", canonical, null, null)
        head.meta(null, "og:locale", if (locale == PortfolioLocale.EN) "en_US" else locale.code, null, null)
        // Twitter: name attributes
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, pageTitle, null, null)
        head.meta("twitter:description", null, description, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.AR), "ar", null, null, null)
        // Material Icons
        head.link("stylesheet", "https://fonts.googleapis.com/icon?family=Material+Icons", null, null, null, null)
        // Hydration script also loads synchronously to ensure polyfill is applied
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
        // Non-critical cleanup script (async)
        head.script("/static/textarea-cleanup.js", "textarea-cleanup", "application/javascript", true, false, null)
        // (Structured data currently omitted until HeadScope gains inline support)
    }

    private fun canonicalUrl(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> portfolioBaseUrl()
            else -> "${portfolioBaseUrl().trimEnd('/')}/${locale.code}"
        }
}
