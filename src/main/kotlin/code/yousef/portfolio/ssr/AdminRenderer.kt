package code.yousef.portfolio.ssr

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.admin.AdminDashboardContent
import code.yousef.portfolio.ui.admin.AdminDashboardPage

class AdminRenderer {
    fun dashboard(
        locale: PortfolioLocale,
        projects: List<Project>,
        services: List<Service>,
        contacts: List<ContactSubmission>
    ): SummonPage {
        val content = AdminDashboardContent(
            projects = projects,
            services = services,
            contacts = contacts
        )
        return SummonPage(
            head = { head ->
                head.title("Admin · Summon Portfolio")
                head.meta("robots", "noindex", null, null, null)
            },
            content = {
                AdminDashboardPage(locale = locale, content = content)
            }
        )
    }
}
