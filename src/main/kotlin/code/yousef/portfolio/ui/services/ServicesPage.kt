package code.yousef.portfolio.ui.services

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.runtime.rememberMutableStateOf

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
        AppHeader(locale = locale)
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
        ServiceDetailsList(
            services = content.services,
            locale = locale
        )
        ContactSection(locale = locale, modifier = Modifier())
        PortfolioFooter(locale = locale)
    }
}
