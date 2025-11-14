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
        head.meta(null, description, "description", null, null)
        head.meta("og:title", pageTitle, null, null, null)
        head.meta(
            "og:description",
            description,
            null,
            null,
            null
        )
        head.meta("og:type", "website", null, null, null)
        head.meta("og:url", canonical, null, null, null)
        head.meta("og:locale", locale.code, null, null, null)
        head.meta(null, "summary_large_image", "twitter:card", null, null)
        head.meta(null, pageTitle, "twitter:title", null, null)
        head.meta(null, description, "twitter:description", null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.EN), "en", null, null, null)
        head.link("alternate", canonicalUrl(PortfolioLocale.AR), "ar", null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, true, null)
        head.script("/static/textarea-cleanup.js", "textarea-cleanup", "application/javascript", true, false, null)
    }

    private fun canonicalUrl(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> portfolioBaseUrl()
            else -> "${portfolioBaseUrl().trimEnd('/')}/${locale.code}"
        }
}
