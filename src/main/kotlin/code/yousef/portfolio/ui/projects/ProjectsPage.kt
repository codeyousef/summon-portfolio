package code.yousef.portfolio.ui.projects

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.portfolio.ui.sections.ProjectsSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.runtime.rememberMutableStateOf

@Composable
fun ProjectsPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val contactHref = "#contact"
    val servicesModalState = rememberMutableStateOf(servicesModalOpen)
    val openServicesModal = { servicesModalState.value = true }
    val closeServicesModal = { servicesModalState.value = false }

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        SectionWrap {
            Text(
                text = "Featured Projects",
                modifier = Modifier().fontWeight(800)
            )
            Paragraph(
                text = "Language, framework, and experience layers that ship expressive, high-performance products.",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
        ProjectsSection(
            projects = content.projects,
            locale = locale,
            modifier = Modifier()
        )
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
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
