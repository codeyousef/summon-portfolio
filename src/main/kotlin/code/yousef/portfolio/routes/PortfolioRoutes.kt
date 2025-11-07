package code.yousef.portfolio.routes

import code.yousef.portfolio.api.toDto
import code.yousef.portfolio.contact.ContactRequest
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.repo.BlogRepository
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import code.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.portfolioRoutes(
    portfolioRenderer: PortfolioRenderer,
    blogRenderer: BlogRenderer,
    contactService: ContactService,
    contentService: PortfolioContentService,
    blogRepository: BlogRepository,
    adminRenderer: AdminRenderer
) {
    get("/") {
        val page = portfolioRenderer.landingPage(
            locale = PortfolioLocale.EN,
            servicesModalOpen = call.shouldOpenServicesModal()
        )
        call.respondSummonPage(page)
    }
    get("/blog") {
        val page = blogRenderer.renderList(PortfolioLocale.EN)
        call.respondSummonPage(page)
    }
    get("/blog/{slug}") {
        val slug = call.parameters["slug"].orEmpty()
        val result = blogRenderer.renderDetail(PortfolioLocale.EN, slug)
        call.respondSummonPage(result.page, result.status)
    }
    route("/api") {
        get("/projects") {
            val locale = call.apiLocale()
            val projects = contentService.load().projects.map { it.toDto(locale) }
            call.respond(projects)
        }
        get("/services") {
            val locale = call.apiLocale()
            val services = contentService.load().services.map { it.toDto(locale) }
            call.respond(services)
        }
        get("/blog") {
            val locale = call.apiLocale()
            val posts = blogRepository.list().map { it.toDto(locale) }
            call.respond(posts)
        }
        get("/blog/{slug}") {
            val locale = call.apiLocale()
            val slug = call.parameters["slug"].orEmpty()
            val post = blogRepository.findBySlug(slug)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
            } else {
                call.respond(post.toDto(locale))
            }
        }
        get("/contacts") {
            val contacts = contactService.list().map { it.toDto() }
            call.respond(contacts)
        }
        post("/contact") {
            val request = call.receiveContactRequest()
            if (request == null) {
                call.respond(HttpStatusCode.UnsupportedMediaType, mapOf("error" to "Unsupported content type"))
                return@post
            }
            when (val result = contactService.submit(request)) {
                is ContactService.Result.Success -> call.respond(HttpStatusCode.Created, mapOf("status" to "ok"))
                is ContactService.Result.Error -> call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to result.reason)
                )
            }
        }
    }
    get("/admin") {
        val content = contentService.load()
        val page = adminRenderer.dashboard(
            locale = PortfolioLocale.EN,
            projects = content.projects,
            services = content.services,
            contacts = contactService.list()
        )
        call.respondSummonPage(page)
    }
    get("/{locale}/admin") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val content = contentService.load()
            val page = adminRenderer.dashboard(
                locale = locale,
                projects = content.projects,
                services = content.services,
                contacts = contactService.list()
            )
            call.respondSummonPage(page)
        }
    }
    get("/{locale}") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val page = portfolioRenderer.landingPage(locale, servicesModalOpen = call.shouldOpenServicesModal())
            call.respondSummonPage(page)
        }
    }
    get("/{locale}/blog") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val page = blogRenderer.renderList(locale)
            call.respondSummonPage(page)
        }
    }
    get("/{locale}/blog/{slug}") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val slug = call.parameters["slug"].orEmpty()
            val result = blogRenderer.renderDetail(locale, slug)
            call.respondSummonPage(result.page, result.status)
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

private fun ApplicationCall.shouldOpenServicesModal(): Boolean =
    request.queryParameters["modal"]?.equals("services", ignoreCase = true) == true

private fun ApplicationCall.apiLocale(): PortfolioLocale =
    request.queryParameters["locale"]?.let { PortfolioLocale.exact(it) } ?: PortfolioLocale.EN

private suspend fun ApplicationCall.respondSummonPage(page: SummonPage, status: HttpStatusCode = HttpStatusCode.OK) {
    respondSummonHydrated(status) {
        val renderer = getPlatformRenderer()
        renderer.renderHeadElements(page.head)
        page.content()
    }
}
