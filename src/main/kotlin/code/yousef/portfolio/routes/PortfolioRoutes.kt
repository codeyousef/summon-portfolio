package code.yousef.portfolio.routes

import code.yousef.portfolio.contact.ContactRequest
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.portfolioRoutes(
    portfolioRenderer: PortfolioRenderer,
    blogRenderer: BlogRenderer,
    contactService: ContactService
) {
    get("/") {
        val html = portfolioRenderer.renderLandingPage(PortfolioLocale.EN)
        call.respondText(html, ContentType.Text.Html)
    }
    get("/blog") {
        val html = blogRenderer.renderList(PortfolioLocale.EN)
        call.respondText(html, ContentType.Text.Html)
    }
    get("/blog/{slug}") {
        val slug = call.parameters["slug"].orEmpty()
        val result = blogRenderer.renderDetail(PortfolioLocale.EN, slug)
        val status = if (result.post == null) HttpStatusCode.NotFound else HttpStatusCode.OK
        call.respondText(result.html, ContentType.Text.Html, status)
    }
    post("/api/contact") {
        val request = call.receiveContactRequest()
        if (request == null) {
            call.respond(HttpStatusCode.UnsupportedMediaType, mapOf("error" to "Unsupported content type"))
            return@post
        }
        when (val result = contactService.submit(request)) {
            is ContactService.Result.Success -> call.respond(HttpStatusCode.Created, mapOf("status" to "ok"))
            is ContactService.Result.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.reason))
        }
    }
    get("/{locale}") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val html = portfolioRenderer.renderLandingPage(locale)
            call.respondText(html, ContentType.Text.Html)
        }
    }
    get("/{locale}/blog") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val html = blogRenderer.renderList(locale)
            call.respondText(html, ContentType.Text.Html)
        }
    }
    get("/{locale}/blog/{slug}") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val slug = call.parameters["slug"].orEmpty()
            val result = blogRenderer.renderDetail(locale, slug)
            val status = if (result.post == null) HttpStatusCode.NotFound else HttpStatusCode.OK
            call.respondText(result.html, ContentType.Text.Html, status)
        }
    }
    post("/{locale}/api/contact") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val request = call.receiveContactRequest()
            if (request == null) {
                call.respond(HttpStatusCode.UnsupportedMediaType, mapOf("error" to "Unsupported content type"))
                return@post
            }
            when (val result = contactService.submit(request)) {
                is ContactService.Result.Success -> call.respond(HttpStatusCode.Created, mapOf("status" to "ok"))
                is ContactService.Result.Error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.reason))
            }
        }
    }
}

private suspend fun ApplicationCall.receiveContactRequest(): ContactRequest? {
    val contentType = request.contentType()
    return when {
        contentType == ContentType.Application.Json -> receive<ContactRequest>()
        contentType == ContentType.Application.FormUrlEncoded -> {
            val params = receiveParameters()
            ContactRequest(
                name = params["name"].orEmpty(),
                email = params["email"],
                whatsapp = params["whatsapp"].orEmpty(),
                requirements = params["requirements"].orEmpty()
            )
        }

        else -> null
    }
}
