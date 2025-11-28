package code.yousef.portfolio.ssr

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ui.admin.AdminDashboardContent
import code.yousef.portfolio.ui.admin.AdminDashboardPage
import code.yousef.portfolio.ui.admin.AdminSectionPage

class AdminRenderer {
    fun dashboard(
        locale: PortfolioLocale,
        projects: List<Project>,
        services: List<Service>,
        blogPosts: List<BlogPost>,
        testimonials: List<Testimonial>,
        contacts: List<ContactSubmission>,
        section: AdminSectionPage
    ): SummonPage {
        val content = AdminDashboardContent(
            projects = projects,
            services = services,
            blogPosts = blogPosts,
            testimonials = testimonials,
            contacts = contacts
        )
        return SummonPage(
            head = { head ->
                head.title("Admin · Summon Portfolio")
                head.meta("robots", "noindex", null, null, null)
            },
            content = {
                AdminDashboardPage(locale = locale, content = content, activeSection = section)
            }
        )
    }
}
