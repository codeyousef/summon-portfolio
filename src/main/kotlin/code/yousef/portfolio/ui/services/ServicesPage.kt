package code.yousef.portfolio.ui.services

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.portfolio.ui.sections.ServicesSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.modifier.Modifier
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.runtime.rememberMutableStateOf

@Composable
fun ServicesPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val contactHref = "#contact"
    val servicesModalState = rememberMutableStateOf(servicesModalOpen)
    val openServicesModal = { servicesModalState.value = true }
    val closeServicesModal = { servicesModalState.value = false }

    PageScaffold(locale = locale) {
        AppHeader(locale = locale, onRequestServices = openServicesModal)
        SectionWrap {
            Text(
                text = "Services",
                modifier = Modifier().fontWeight(800)
            )
            Paragraph(
                text = "Partner on bespoke systems engineering, framework design, and interactive experience builds.",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
        ServicesSection(
            services = content.services,
            locale = locale,
            onRequestServices = openServicesModal,
            modifier = Modifier()
        )
        ContactSection(locale = locale, modifier = Modifier())
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
