package code.yousef

import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.routes.portfolioRoutes
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val portfolioRenderer = PortfolioRenderer()
    val blogRenderer = BlogRenderer()
    val contactService = ContactService()
    val hydrationBundle = environment.classLoader.getResource("static/summon-hydration.js")?.readBytes()

    routing {
        staticResources("/static", "static")
        hydrationBundle?.let { bundle ->
            get("/summon-hydration.js") {
                call.respondBytes(bundle, ContentType.Application.JavaScript)
            }
        }
        route("/") {
            portfolioRoutes(portfolioRenderer, blogRenderer, contactService)
        }
    }
}
