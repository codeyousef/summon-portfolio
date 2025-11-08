package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.sections.*
import code.yousef.summon.annotation.Composable
import code.yousef.summon.modifier.Modifier
import code.yousef.summon.runtime.rememberMutableStateOf

@Composable
fun PortfolioLandingPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val contactHref = if (locale == PortfolioLocale.EN) "/#contact" else "/${locale.code}#contact"
    val servicesModalState = rememberMutableStateOf(servicesModalOpen)
    val openServicesModal = { servicesModalState.value = true }
    val closeServicesModal = { servicesModalState.value = false }

    PageScaffold(locale = locale) {
        AppHeader(locale = locale, onRequestServices = openServicesModal)
        HeroSection(hero = content.hero, locale = locale, onRequestServices = openServicesModal)
        ProjectsSection(
            projects = content.projects,
            locale = locale,
            modifier = Modifier().id("projects")
        )
        ServicesSection(
            services = content.services,
            locale = locale,
            onRequestServices = openServicesModal,
            modifier = Modifier().id("services")
        )
        BlogTeaserSection(
            posts = content.blogPosts,
            locale = locale,
            modifier = Modifier().id("blog")
        )
        ContactSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
        ServicesOverlay(
            open = servicesModalState.value,
            services = content.services,
            locale = locale,
            contactHref = contactHref,
            onClose = closeServicesModal
        )
    }
}
