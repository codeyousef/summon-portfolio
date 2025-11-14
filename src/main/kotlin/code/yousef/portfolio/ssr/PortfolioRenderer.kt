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
                pageTitle = "Yousef Baitalmal · Portfolio",
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
                pageTitle = "Projects · Yousef Baitalmal",
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
                pageTitle = "Services · Yousef Baitalmal",
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
        head.meta("description", description, null, null, null)
        head.meta(null, pageTitle, "og:title", null, null)
        head.meta(
            null,
            description,
            "og:description",
            null,
            null
        )
        head.meta(null, "website", "og:type", null, null)
        head.meta(null, canonical, "og:url", null, null)
        head.meta(null, locale.code, "og:locale", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", pageTitle, null, null, null)
        head.meta("twitter:description", description, null, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.AR), "ar", null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
        head.script("/static/textarea-cleanup.js", "application/javascript", "textarea-cleanup", true, false, null)
    }

    private fun canonicalUrl(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> portfolioBaseUrl()
            else -> "${portfolioBaseUrl().trimEnd('/')}/${locale.code}"
        }
}
