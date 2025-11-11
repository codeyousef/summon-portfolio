package code.yousef.portfolio.routes

import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.admin.auth.AdminAuthService.AuthResult
import code.yousef.portfolio.admin.auth.AdminSession
import code.yousef.portfolio.api.toDto
import code.yousef.portfolio.contact.ContactRequest
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.routes.forms.toBlogPost
import code.yousef.portfolio.routes.forms.toProject
import code.yousef.portfolio.routes.forms.toService
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.SummonRenderLock
import code.yousef.portfolio.ui.admin.AdminChangePasswordPage
import code.yousef.portfolio.ui.admin.AdminLoginPage
import code.yousef.portfolio.ui.admin.AdminSectionPage
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import code.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import code.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.net.URLEncoder

fun Route.portfolioRoutes(
    portfolioRenderer: PortfolioRenderer,
    blogRenderer: BlogRenderer,
    contactService: ContactService,
    contentService: PortfolioContentService,
    adminRenderer: AdminRenderer,
    adminContentService: AdminContentService,
    adminAuthService: AdminAuthService
) {
    get("/") {
        val page = portfolioRenderer.landingPage(
            locale = PortfolioLocale.EN,
            servicesModalOpen = call.shouldOpenServicesModal()
        )
        call.respondSummonPage(page)
    }
    get("/admin/login") {
        val next = call.request.queryParameters["next"].sanitizeNextPath()
        val session = call.sessions.get<AdminSession>()
        if (session != null && !session.mustChangePassword) {
            call.respondRedirect(next ?: "/admin")
            return@get
        }
        call.respondSummonPage(adminLoginPage(errorMessage = null, nextPath = next))
    }
    post("/admin/login") {
        val params = call.receiveParameters()
        val username = params["username"].orEmpty().trim()
        val password = params["password"].orEmpty()
        val next = params["next"].sanitizeNextPath()
        when (val result = adminAuthService.authenticate(username, password)) {
            is AuthResult.Invalid -> {
                call.respondSummonPage(
                    adminLoginPage(errorMessage = "Invalid credentials.", nextPath = next),
                    HttpStatusCode.Unauthorized
                )
            }

            is AuthResult.Success -> {
                call.sessions.set(AdminSession(username, result.mustChangePassword))
                if (result.mustChangePassword) {
                    call.respondRedirect("/admin/change-password")
                } else {
                    call.respondRedirect(next ?: "/admin")
                }
            }
        }
    }
    get("/admin/change-password") {
        val session = call.sessions.get<AdminSession>()
        if (session == null) {
            call.redirectToLogin()
            return@get
        }
        call.respondSummonPage(
            adminChangePasswordPage(
                currentUsername = adminAuthService.currentUsername(),
                errorMessage = null
            )
        )
    }
    post("/admin/change-password") {
        val session = call.sessions.get<AdminSession>()
        if (session == null) {
            call.redirectToLogin()
            return@post
        }
        val params = call.receiveParameters()
        val username = params["username"].orEmpty().trim()
        val password = params["password"].orEmpty()
        val confirm = params["confirm"].orEmpty()
        val error = when {
            username.isBlank() -> "Username cannot be blank."
            password.length < 8 -> "Password must be at least 8 characters."
            password != confirm -> "Passwords do not match."
            else -> null
        }
        if (error != null) {
            call.respondSummonPage(
                adminChangePasswordPage(
                    currentUsername = username.ifBlank { adminAuthService.currentUsername() },
                    errorMessage = error
                ),
                HttpStatusCode.BadRequest
            )
            return@post
        }
        adminAuthService.updateCredentials(username, password)
        call.sessions.set(AdminSession(username, mustChangePassword = false))
        call.respondRedirect("/admin")
    }
    post("/admin/logout") {
        call.sessions.clear<AdminSession>()
        call.respondRedirect("/admin/login")
    }
    get("/admin/logout") {
        call.sessions.clear<AdminSession>()
        call.respondRedirect("/admin/login")
    }
    get("/projects") {
        val page = portfolioRenderer.projectsPage(
            locale = PortfolioLocale.EN,
            servicesModalOpen = call.shouldOpenServicesModal()
        )
        call.respondSummonPage(page)
    }
    get("/services") {
        val page = portfolioRenderer.servicesPage(
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
            val posts = contentService.load().blogPosts.map { it.toDto(locale) }
            call.respond(posts)
        }
        get("/blog/{slug}") {
            val locale = call.apiLocale()
            val slug = call.parameters["slug"].orEmpty()
            val post = contentService.load().blogPosts.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
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
        call.requireAdminSession() ?: return@get
        call.respondRedirect("/admin/${AdminSectionPage.PROJECTS.pathSegment()}")
    }
    get("/admin/") {
        call.requireAdminSession() ?: return@get
        call.respondRedirect("/admin/${AdminSectionPage.PROJECTS.pathSegment()}")
    }
    get("/admin/{section}") {
        call.requireAdminSession() ?: return@get
        val section = call.parameters["section"].toAdminSection()
        val content = contentService.load()
        val page = adminRenderer.dashboard(
            locale = PortfolioLocale.EN,
            projects = content.projects,
            services = content.services,
            blogPosts = content.blogPosts,
            contacts = contactService.list(),
            section = section
        )
        call.respondSummonPage(page)
    }
    get("/{locale}/admin") {
        call.requireAdminSession() ?: return@get
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondRedirect(locale.adminRedirectPath(AdminSectionPage.PROJECTS))
        }
    }
    get("/{locale}/admin/") {
        call.requireAdminSession() ?: return@get
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondRedirect(locale.adminRedirectPath(AdminSectionPage.PROJECTS))
        }
    }
    get("/{locale}/admin/{section}") {
        call.requireAdminSession() ?: return@get
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        val section = call.parameters["section"].toAdminSection()
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val content = contentService.load()
            val page = adminRenderer.dashboard(
                locale = locale,
                projects = content.projects,
                services = content.services,
                blogPosts = content.blogPosts,
                contacts = contactService.list(),
                section = section
            )
            call.respondSummonPage(page)
        }
    }
    post("/admin/projects/upsert") {
        call.requireAdminSession() ?: return@post
        call.handleProjectUpsert(adminContentService, "/admin/${AdminSectionPage.PROJECTS.pathSegment()}")
    }
    post("/admin/projects/delete") {
        call.requireAdminSession() ?: return@post
        call.handleProjectDelete(adminContentService, "/admin/${AdminSectionPage.PROJECTS.pathSegment()}")
    }
    post("/admin/services/upsert") {
        call.requireAdminSession() ?: return@post
        call.handleServiceUpsert(adminContentService, "/admin/${AdminSectionPage.SERVICES.pathSegment()}")
    }
    post("/admin/services/delete") {
        call.requireAdminSession() ?: return@post
        call.handleServiceDelete(adminContentService, "/admin/${AdminSectionPage.SERVICES.pathSegment()}")
    }
    post("/admin/blog/upsert") {
        call.requireAdminSession() ?: return@post
        call.handleBlogUpsert(adminContentService, "/admin/${AdminSectionPage.BLOG.pathSegment()}")
    }
    post("/admin/blog/delete") {
        call.requireAdminSession() ?: return@post
        call.handleBlogDelete(adminContentService, "/admin/${AdminSectionPage.BLOG.pathSegment()}")
    }
    get("/{locale}") {
        val rawLocale = call.parameters["locale"]?.lowercase()
        if (rawLocale == "admin") {
            call.respondRedirect("/admin/${AdminSectionPage.PROJECTS.pathSegment()}")
            return@get
        }
        val locale = rawLocale?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val page = portfolioRenderer.landingPage(locale, servicesModalOpen = call.shouldOpenServicesModal())
            call.respondSummonPage(page)
        }
    }
    post("/{locale}/admin/projects/upsert") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleProjectUpsert(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.PROJECTS)
            )
        }
    }
    post("/{locale}/admin/projects/delete") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleProjectDelete(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.PROJECTS)
            )
        }
    }
    post("/{locale}/admin/services/upsert") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleServiceUpsert(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.SERVICES)
            )
        }
    }
    post("/{locale}/admin/services/delete") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleServiceDelete(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.SERVICES)
            )
        }
    }
    post("/{locale}/admin/blog/upsert") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleBlogUpsert(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.BLOG)
            )
        }
    }
    post("/{locale}/admin/blog/delete") {
        call.requireAdminSession() ?: return@post
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.handleBlogDelete(
                adminContentService,
                locale.adminRedirectPath(AdminSectionPage.BLOG)
            )
        }
    }
    get("/{locale}/admin/logout") {
        call.sessions.clear<AdminSession>()
        call.respondRedirect("/admin/login")
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
    get("/{locale}/projects") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val page = portfolioRenderer.projectsPage(
                locale = locale,
                servicesModalOpen = call.shouldOpenServicesModal()
            )
            call.respondSummonPage(page)
        }
    }
    get("/{locale}/services") {
        val locale = call.parameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) }
        if (locale == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val page = portfolioRenderer.servicesPage(
                locale = locale,
                servicesModalOpen = call.shouldOpenServicesModal()
            )
            call.respondSummonPage(page)
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
    request.queryParameters["locale"]?.lowercase()?.let { PortfolioLocale.exact(it) } ?: PortfolioLocale.EN

private fun String?.toAdminSection(): AdminSectionPage =
    when (this?.lowercase()) {
        "services" -> AdminSectionPage.SERVICES
        "blog" -> AdminSectionPage.BLOG
        "contacts" -> AdminSectionPage.CONTACTS
        else -> AdminSectionPage.PROJECTS
    }

private fun PortfolioLocale.adminRedirectPath(section: AdminSectionPage? = null): String {
    val base = if (this == PortfolioLocale.EN) "/admin" else "/${this.code}/admin"
    return section?.let { "$base/${it.pathSegment()}" } ?: base
}

private suspend fun ApplicationCall.requireAdminSession(): AdminSession? {
    val session = sessions.get<AdminSession>()
    if (session == null) {
        redirectToLogin()
        return null
    }
    if (session.mustChangePassword && !request.path().startsWith("/admin/change-password")) {
        respondRedirect("/admin/change-password")
        return null
    }
    return session
}

private suspend fun ApplicationCall.redirectToLogin() {
    val encoded = URLEncoder.encode(request.uri, Charsets.UTF_8)
    respondRedirect("/admin/login?next=$encoded")
}

private fun String?.sanitizeNextPath(): String? {
    if (this.isNullOrBlank()) return null
    if (!startsWith("/")) return null
    if (startsWith("//")) return null
    return this
}

private fun adminLoginPage(errorMessage: String?, nextPath: String?): SummonPage =
    SummonPage(
        head = { head ->
            head.title("Admin Login · Summon Portfolio")
            head.meta("robots", "noindex", null, null, null)
        },
        content = { AdminLoginPage(errorMessage = errorMessage, nextPath = nextPath) }
    )

private fun adminChangePasswordPage(currentUsername: String, errorMessage: String?): SummonPage =
    SummonPage(
        head = { head ->
            head.title("Update Admin Credentials · Summon Portfolio")
            head.meta("robots", "noindex", null, null, null)
        },
        content = { AdminChangePasswordPage(currentUsername = currentUsername, errorMessage = errorMessage) }
    )

private suspend fun ApplicationCall.handleProjectUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val project = params.toProject()
    if (project == null) {
        respondRedirect("$redirectTarget?error=project")
    } else {
        adminContentService.saveProject(project)
        respondRedirect("$redirectTarget?success=project")
    }
}

private suspend fun ApplicationCall.handleProjectDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        respondRedirect("$redirectTarget?error=project-delete")
    } else {
        adminContentService.deleteProject(id)
        respondRedirect("$redirectTarget?success=project-delete")
    }
}

private suspend fun ApplicationCall.handleServiceUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val service = params.toService()
    if (service == null) {
        respondRedirect("$redirectTarget?error=service")
    } else {
        adminContentService.saveService(service)
        respondRedirect("$redirectTarget?success=service")
    }
}

private suspend fun ApplicationCall.handleServiceDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        respondRedirect("$redirectTarget?error=service-delete")
    } else {
        adminContentService.deleteService(id)
        respondRedirect("$redirectTarget?success=service-delete")
    }
}

private suspend fun ApplicationCall.handleBlogUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val post = params.toBlogPost()
    if (post == null) {
        respondRedirect("$redirectTarget?error=blog")
    } else {
        adminContentService.saveBlogPost(post)
        respondRedirect("$redirectTarget?success=blog")
    }
}

private suspend fun ApplicationCall.handleBlogDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        respondRedirect("$redirectTarget?error=blog-delete")
    } else {
        adminContentService.deleteBlogPost(id)
        respondRedirect("$redirectTarget?success=blog-delete")
    }
}

private suspend fun ApplicationCall.respondSummonPage(page: SummonPage, status: HttpStatusCode = HttpStatusCode.OK) {
    SummonRenderLock.withLock {
        respondSummonHydrated(status) {
            val renderer = getPlatformRenderer()
            renderer.renderHeadElements(page.head)
            val session = sessions.get<AdminSession>()
            val chrome =
                session?.let { page.chrome.copy(isAdminSession = true, adminUsername = it.username) } ?: page.chrome
            val provider = LocalPageChrome.provides(chrome)
            provider.current
            page.content()
        }
    }
}
