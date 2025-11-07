package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.sections.*
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.layout.Spacer
import code.yousef.summon.modifier.Modifier

@Composable
fun PortfolioLandingPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val basePath = if (locale == PortfolioLocale.EN) "/" else "/${locale.code}"
    val contactHref = if (locale == PortfolioLocale.EN) "/#contact" else "/${locale.code}#contact"

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Spacer(modifier = Modifier().style("height", "12px"))
        HeroSection(hero = content.hero, locale = locale, modifier = Modifier().id("hero"))
        ProjectsSection(projects = content.projects, locale = locale, modifier = Modifier().id("projects"))
        ServicesSection(services = content.services, locale = locale, modifier = Modifier().id("services"))
        ContactSection(locale = locale, modifier = Modifier().id("contact"))
        BlogTeaserSection(posts = content.blogPosts, locale = locale, modifier = Modifier().id("blog"))
        ServicesOverlay(
            open = servicesModalOpen,
            services = content.services,
            locale = locale,
            closeHref = basePath,
            contactHref = contactHref
        )
    }
}
