package code.yousef

import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.routes.portfolioRoutes
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val portfolioRenderer = PortfolioRenderer()
    val blogRenderer = BlogRenderer()
    val contactService = ContactService()

    routing {
        route("/") {
            portfolioRoutes(portfolioRenderer, blogRenderer, contactService)
        }
    }
}
