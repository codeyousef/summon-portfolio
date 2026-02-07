package code.yousef.portfolio.ssr

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.seed.ArtworkSeed
import code.yousef.portfolio.content.seed.MusicSeed
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.PortfolioLandingPage
import code.yousef.portfolio.ui.art.ArtPage
import code.yousef.portfolio.ui.music.MusicPage
import code.yousef.portfolio.ui.projects.ProjectsPage
import code.yousef.portfolio.ui.summon.SummonLandingPage
import code.yousef.portfolio.ui.workwithme.FullTimePage
import code.yousef.portfolio.ui.workwithme.ServicesPage
import codes.yousef.summon.seo.HeadScope

class PortfolioRenderer(
    private val contentService: PortfolioContentService
) {

    fun landingPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Yousef",
                description = "Systems, frameworks, and immersive experiences crafted from first principles."
            ),
            content = {
                PortfolioLandingPage(content = content, locale = locale, servicesModalOpen = servicesModalOpen)
            },
            locale = locale
        )
    }

    fun projectsPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Projects | Yousef",
                description = "Featured language, framework, and experience builds from the studio."
            ),
            content = {
                ProjectsPage(
                    content = content,
                    locale = locale,
                    servicesModalOpen = servicesModalOpen
                )
            },
            locale = locale
        )
    }

    fun servicesPage(locale: PortfolioLocale, servicesModalOpen: Boolean = false): SummonPage {
        val content = contentService.load()
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Consulting & Services | Yousef",
                description = "Engineering partner for high-impact projects. 3D graphics, cross-platform development, and tooling expertise."
            ),
            content = {
                ServicesPage(locale = locale)
            },
            locale = locale
        )
    }

    fun fullTimePage(locale: PortfolioLocale): SummonPage {
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Full-Time Opportunities | Yousef",
                description = "Systems architect and graphics engineer seeking Staff/Senior roles. Builder of Summon, Materia, and Sigil."
            ),
            content = {
                FullTimePage(locale = locale)
            },
            locale = locale
        )
    }

    fun summonLandingPage(docsUrl: String, apiReferenceUrl: String): SummonPage {
        return SummonPage(
            head = headBlockFor(
                locale = PortfolioLocale.EN,
                pageTitle = "Summon | The Kotlin Multiplatform SSR Framework",
                description = "Build type-safe, server-rendered web applications with Kotlin. Summon unifies backend and frontend with a single language and zero context switching."
            ),
            content = {
                SummonLandingPage(docsUrl = docsUrl, apiReferenceUrl = apiReferenceUrl)
            },
            locale = PortfolioLocale.EN
        )
    }

    fun artPage(locale: PortfolioLocale): SummonPage {
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Art & Visual Work | Yousef",
                description = "Digital explorations, procedural generations, and visual experiments at the intersection of code and creativity."
            ),
            content = {
                ArtPage(artworks = ArtworkSeed.artworks, locale = locale)
            },
            locale = locale
        )
    }

    fun musicPage(locale: PortfolioLocale): SummonPage {
        return SummonPage(
            head = headBlockFor(
                locale = locale,
                pageTitle = "Music & Audio | Yousef",
                description = "Compositions, soundscapes, and audio experiments. From ambient explorations to orchestral arrangements."
            ),
            content = {
                MusicPage(tracks = MusicSeed.tracks, locale = locale)
            },
            locale = locale
        )
    }

    private fun headBlockFor(
        locale: PortfolioLocale,
        pageTitle: String,
        description: String
    ): (HeadScope) -> Unit = { head ->
        val canonical = canonicalUrl(locale)
        head.title(pageTitle)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        // Sigil hydration bundle auto-loads via sigilStaticAssets() - no manual script needed
        // Non-critical cleanup script (async)
        head.script("/static/textarea-cleanup.js", "textarea-cleanup", "application/javascript", true, false, null)
        // Konami code for scratchpad access (async, deferred)
        head.script("/static/konami.js", "konami-code", "application/javascript", true, true, null)
        // (Structured data currently omitted until HeadScope gains inline support)
    }

    private fun canonicalUrl(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> portfolioBaseUrl()
            else -> "${portfolioBaseUrl().trimEnd('/')}/${locale.code}"
        }
}
