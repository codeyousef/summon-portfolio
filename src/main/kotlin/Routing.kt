package code.yousef

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.PortfolioRenderer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val renderer = PortfolioRenderer()
    routing {
        get("/") {
            val html = renderer.renderLandingPage(PortfolioLocale.EN)
            call.respondText(html, ContentType.Text.Html)
        }
        get("/{locale}") {
            val slug = call.parameters["locale"]?.lowercase().orEmpty()
            val locale = PortfolioLocale.fromCode(slug)
            if (slug.isNotBlank() && locale.code != slug) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val html = renderer.renderLandingPage(locale)
                call.respondText(html, ContentType.Text.Html)
            }
        }
    }
}
