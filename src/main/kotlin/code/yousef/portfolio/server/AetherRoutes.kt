@file:OptIn(kotlin.time.ExperimentalTime::class)
package code.yousef.portfolio.server

import kotlinx.serialization.builtins.serializer
import code.yousef.portfolio.routes.forms.*
import code.yousef.portfolio.api.toDto
import code.yousef.portfolio.contact.ContactRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.admin.auth.AdminAuthService.AuthResult
import code.yousef.portfolio.admin.auth.AdminSession
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.docsBaseUrl
import code.yousef.portfolio.ui.admin.AdminChangePasswordPage
import code.yousef.portfolio.ui.admin.AdminLoginPage
import code.yousef.portfolio.ui.admin.AdminSectionPage
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.jvm.receiveParameters
import codes.yousef.aether.core.respondJson
import codes.yousef.aether.core.session.session
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParam
import codes.yousef.aether.web.pathParamOrThrow
import kotlinx.datetime.TimeZone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun Router.summonRoutes(
    portfolioRenderer: PortfolioRenderer,
    docsService: DocsService,
    markdownRenderer: MarkdownRenderer,
    linkRewriter: LinkRewriter,
    docsRouter: DocsRouter,
    webhookHandler: WebhookHandler,
    docsConfig: DocsConfig,
    docsCatalog: DocsCatalog
) {
    get("/") { exchange ->
        val docsUrl = docsBaseUrl()
        val apiReferenceUrl = "$docsUrl/api-reference"
        val page = portfolioRenderer.summonLandingPage(docsUrl, apiReferenceUrl)
        exchange.respondSummonPage(page)
    }

    docsRoutes(
        docsService,
        markdownRenderer,
        linkRewriter,
        docsRouter,
        webhookHandler,
        docsConfig,
        docsCatalog,
        basePath = "/docs"
    )
}

fun Router.portfolioRoutes(
    portfolioRenderer: PortfolioRenderer,
    blogRenderer: BlogRenderer,
    contactService: ContactService,
    contentService: PortfolioContentService,
    adminRenderer: AdminRenderer,
    adminContentService: AdminContentService,
    adminAuthService: AdminAuthService
) {
    get("/version") { exchange ->
        exchange.respondJson(200, mapOf("version" to "0.6.2.0-debug-2"))
    }

    get("/") { exchange ->
        try {
            val servicesParam = exchange.request.queryParameter("services")
            val page = portfolioRenderer.landingPage(
                locale = PortfolioLocale.EN,
                servicesModalOpen = servicesParam == "true"
            )
            exchange.respondSummonPage(page)
        } catch (e: Exception) {
            val errorBody = "Error in route handler: ${e.message}\n${e.stackTraceToString()}"
            val errorBytes = errorBody.toByteArray(Charsets.UTF_8)
            exchange.response.statusCode = 500
            exchange.response.setHeader("Content-Type", "text/plain")
            exchange.response.setHeader("Content-Length", errorBytes.size.toString())
            exchange.response.write(errorBytes)
            exchange.response.end()
        }
    }

    get("/projects") { exchange ->
        val page = portfolioRenderer.projectsPage(locale = PortfolioLocale.EN)
        exchange.respondSummonPage(page)
    }

    get("/services") { exchange ->
        val servicesParam = exchange.request.queryParameter("services")
        val page = portfolioRenderer.servicesPage(
            locale = PortfolioLocale.EN,
            servicesModalOpen = servicesParam == "true"
        )
        exchange.respondSummonPage(page)
    }

    get("/full-time") { exchange ->
        val page = portfolioRenderer.fullTimePage(locale = PortfolioLocale.EN)
        exchange.respondSummonPage(page)
    }

    get("/blog") { exchange ->
        val page = blogRenderer.renderList(PortfolioLocale.EN)
        exchange.respondSummonPage(page)
    }

    get("/blog/:slug") { exchange ->
        val slug = exchange.pathParam("slug") ?: ""
        val result = blogRenderer.renderDetail(PortfolioLocale.EN, slug)
        exchange.respondSummonPage(result.page, result.status.value)
    }

    post("/contact") { exchange ->
        val contentType = exchange.request.headers["Content-Type"] ?: ""
        val request = if (contentType.contains("application/json")) {
             try {
                 json.decodeFromString<ContactRequest>(exchange.request.bodyText())
             } catch (e: Exception) {
                 null
             }
        } else {
             val params = exchange.receiveParameters()
             ContactRequest(
                 contact = params["contact"].orEmpty(),
                 message = params["message"].orEmpty()
             )
        }

        if (request == null) {
            exchange.respond(415, "Unsupported content type or invalid data")
            return@post
        }
        
        when (val result = contactService.submit(request)) {
            is ContactService.Result.Success -> exchange.respondJson(201, mapOf("status" to "ok"))
            is ContactService.Result.Error -> exchange.respondJson(400, mapOf("error" to result.reason))
        }
    }

    get("/api/projects") { exchange ->
        val projects = contentService.load().projects.map { it.toDto(PortfolioLocale.EN) }
        exchange.respondJson(200, projects)
    }

    get("/api/services") { exchange ->
        val services = contentService.load().services.map { it.toDto(PortfolioLocale.EN) }
        exchange.respondJson(200, services)
    }

    get("/api/blog") { exchange ->
        val posts = contentService.load().blogPosts.map { it.toDto(PortfolioLocale.EN) }
        exchange.respondJson(200, posts)
    }

    get("/api/blog/:slug") { exchange ->
        val slug = exchange.pathParamOrThrow("slug")
        val post = contentService.load().blogPosts.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
        if (post == null) {
            exchange.respondJson(404, mapOf("error" to "Not found"))
        } else {
            exchange.respondJson(200, post.toDto(PortfolioLocale.EN))
        }
    }

    get("/api/contacts") { exchange ->
        val contacts = contactService.list().map { it.toDto() }
        exchange.respondJson(200, contacts)
    }

    // Admin Routes
    get("/admin/login") { exchange ->
        val next = exchange.request.queryParameter("next")?.sanitizeNextPath()
        val session = exchange.getAdminSession()
        if (session != null && !session.mustChangePassword) {
            exchange.redirect(next ?: "/admin")
            return@get
        }
        exchange.respondSummonPage(adminLoginPage(errorMessage = null, nextPath = next))
    }

    post("/admin/login") { exchange ->
        val params = exchange.receiveParameters()
        val username = params["username"].orEmpty().trim()
        val password = params["password"].orEmpty()
        val next = params["next"]?.sanitizeNextPath()
        
        when (val result = adminAuthService.authenticate(username, password)) {
            is AuthResult.Invalid -> {
                exchange.respondSummonPage(
                    adminLoginPage(errorMessage = "Invalid credentials.", nextPath = next),
                    401
                )
            }
            is AuthResult.Success -> {
                exchange.setAdminSession(AdminSession(username, result.mustChangePassword))
                if (result.mustChangePassword) {
                    exchange.redirect("/admin/change-password")
                } else {
                    exchange.redirect(next ?: "/admin")
                }
            }
        }
    }

    get("/admin/change-password") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login")
            return@get
        }
        exchange.respondSummonPage(
            adminChangePasswordPage(
                currentUsername = adminAuthService.currentUsername(),
                errorMessage = null
            )
        )
    }

    post("/admin/change-password") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login")
            return@post
        }
        val params = exchange.receiveParameters()
        val username = params["username"].orEmpty().trim()
        val password = params["password"].orEmpty()
        
        if (username.isNotBlank() && password.isNotBlank()) {
            adminAuthService.updateCredentials(username, password)
            exchange.redirect("/admin")
        } else {
             val page = adminChangePasswordPage(session.username, "Username and password cannot be empty")
             exchange.respondSummonPage(page)
        }
    }
    
    get("/admin") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login")
            return@get
        }
        
        val projects = contentService.listProjects()
        val services = contentService.listServices()
        val blogPosts = contentService.listBlogPosts()
        val testimonials = contentService.listTestimonials()
        val contacts = contactService.list()
        
        val page = adminRenderer.dashboard(
            locale = PortfolioLocale.EN,
            projects = projects,
            services = services,
            blogPosts = blogPosts,
            testimonials = testimonials,
            contacts = contacts,
            section = AdminSectionPage.PROJECTS
        )
        exchange.respondSummonPage(page)
    }

    get("/admin/:section") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login")
            return@get
        }
        val sectionParam = exchange.pathParam("section")
        val section = AdminSectionPage.entries.find { it.pathSegment() == sectionParam } ?: AdminSectionPage.PROJECTS
        
        val projects = contentService.listProjects()
        val services = contentService.listServices()
        val blogPosts = contentService.listBlogPosts()
        val testimonials = contentService.listTestimonials()
        val contacts = contactService.list()
        
        val page = adminRenderer.dashboard(
            locale = PortfolioLocale.EN,
            projects = projects,
            services = services,
            blogPosts = blogPosts,
            testimonials = testimonials,
            contacts = contacts,
            section = section
        )
        exchange.respondSummonPage(page)
    }

    post("/admin/projects/upsert") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleProjectUpsert(adminContentService, "/admin/projects")
    }
    post("/admin/projects/delete") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleProjectDelete(adminContentService, "/admin/projects")
    }
    post("/admin/services/upsert") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleServiceUpsert(adminContentService, "/admin/services")
    }
    post("/admin/services/delete") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleServiceDelete(adminContentService, "/admin/services")
    }
    post("/admin/blog/upsert") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleBlogUpsert(adminContentService, "/admin/blog")
    }
    post("/admin/blog/delete") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleBlogDelete(adminContentService, "/admin/blog")
    }
    post("/admin/testimonials/upsert") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleTestimonialUpsert(adminContentService, "/admin/testimonials")
    }
    post("/admin/testimonials/delete") { exchange ->
        exchange.requireAdminSession() ?: return@post
        exchange.handleTestimonialDelete(adminContentService, "/admin/testimonials")
    }
}

private suspend fun Exchange.getAdminSession(): AdminSession? {
    val session = session() ?: return null
    val username = session.get("username") as? String ?: return null
    val mustChangePassword = session.get("mustChangePassword")?.toString()?.toBoolean() ?: false
    return AdminSession(username, mustChangePassword)
}

private suspend fun Exchange.setAdminSession(adminSession: AdminSession) {
    val session = session() ?: throw IllegalStateException("Session middleware not installed")
    session.set("username", adminSession.username)
    session.set("mustChangePassword", adminSession.mustChangePassword.toString())
}

private suspend fun Exchange.requireAdminSession(): AdminSession? {
    val session = getAdminSession()
    if (session == null) {
        val encoded = URLEncoder.encode(request.uri, StandardCharsets.UTF_8)
        redirect("/admin/login?next=$encoded")
        return null
    }
    if (session.mustChangePassword && !request.path.startsWith("/admin/change-password")) {
        redirect("/admin/change-password")
        return null
    }
    return session
}

private suspend fun Exchange.handleProjectUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val project = params.toProject()
    if (project == null) {
        redirect("$redirectTarget?error=project")
    } else {
        adminContentService.saveProject(project)
        redirect("$redirectTarget?success=project")
    }
}

private suspend fun Exchange.handleProjectDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        redirect("$redirectTarget?error=project-delete")
    } else {
        adminContentService.deleteProject(id)
        redirect("$redirectTarget?success=project-delete")
    }
}

private suspend fun Exchange.handleServiceUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val service = params.toService()
    if (service == null) {
        redirect("$redirectTarget?error=service")
    } else {
        adminContentService.saveService(service)
        redirect("$redirectTarget?success=service")
    }
}

private suspend fun Exchange.handleServiceDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        redirect("$redirectTarget?error=service-delete")
    } else {
        adminContentService.deleteService(id)
        redirect("$redirectTarget?success=service-delete")
    }
}

private suspend fun Exchange.handleBlogUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val post = params.toBlogPost()
    if (post == null) {
        redirect("$redirectTarget?error=blog")
    } else {
        adminContentService.saveBlogPost(post)
        redirect("$redirectTarget?success=blog")
    }
}

private suspend fun Exchange.handleBlogDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        redirect("$redirectTarget?error=blog-delete")
    } else {
        adminContentService.deleteBlogPost(id)
        redirect("$redirectTarget?success=blog-delete")
    }
}

private suspend fun Exchange.handleTestimonialUpsert(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val testimonial = params.toTestimonial()
    if (testimonial == null) {
        redirect("$redirectTarget?error=testimonial")
    } else {
        adminContentService.saveTestimonial(testimonial)
        redirect("$redirectTarget?success=testimonial")
    }
}

private suspend fun Exchange.handleTestimonialDelete(
    adminContentService: AdminContentService,
    redirectTarget: String
) {
    val params = receiveParameters()
    val id = params["id"].orEmpty()
    if (id.isBlank()) {
        redirect("$redirectTarget?error=testimonial-delete")
    } else {
        adminContentService.deleteTestimonial(id)
        redirect("$redirectTarget?success=testimonial-delete")
    }
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

private fun String?.sanitizeNextPath(): String? {
    val path = this?.trim()
    return if (!path.isNullOrBlank() && path.startsWith("/") && !path.startsWith("//")) path else null
}

fun Router.docsRoutes(
    docsService: DocsService,
    markdownRenderer: MarkdownRenderer,
    linkRewriter: LinkRewriter,
    docsRouter: DocsRouter,
    webhookHandler: WebhookHandler,
    config: DocsConfig,
    docsCatalog: DocsCatalog,
    basePath: String = ""
) {
    suspend fun Exchange.renderDocsPage() {
        // When mounted under a subpath (e.g. /docs), we need to strip that prefix to get the relative path
        // for docs lookup. However, Aether's router might not strip it automatically if using 'use'.
        // But here we are inside a router that is mounted.
        // If we use 'use("/docs", docsRouter.asMiddleware())', the exchange.request.path will still be full path.
        // We need to handle basePath stripping.
        
        val fullPath = request.path
        val relativePath = if (basePath.isNotEmpty() && fullPath.startsWith(basePath)) {
             fullPath.removePrefix(basePath)
        } else {
             fullPath
        }
        
        val requestPath = relativePath.ifBlank { "/" }
        val (branch, pathPart) = extractBranch(requestPath, config)
        val slug = normalizeSlug(pathPart)
        var navTree = docsCatalog.navTree()
        var entry = docsCatalog.find(slug)
        if (entry == null) {
            docsCatalog.reload()
            navTree = docsCatalog.navTree()
            entry = docsCatalog.find(slug)
        }

        val origin = run {
            val scheme = "http"
            val host = request.headers["Host"] ?: "localhost"
            "$scheme://$host"
        }

        if (entry == null) {
            val normalizedRequest = normalize(requestPath).lowercase()
            val firstChild = navTree.sections
                .firstOrNull { normalize(it.path).lowercase() == normalizedRequest }
                ?.children?.firstOrNull()?.path
            if (firstChild != null) {
                redirect(basePath + firstChild)
                return
            }
            val firstMatchingEntry = docsCatalog.firstEntryStartingWith(slug)
            if (firstMatchingEntry != null) {
                val redirectPath = if (firstMatchingEntry.slug == DocsCatalog.SLUG_ROOT) "/" else "/${firstMatchingEntry.slug}"
                redirect(basePath + redirectPath)
                return
            }
            val page = docsRouter.notFound(requestPath, navTree, origin)
            respondSummonPage(page, 404)
            return
        }

        val firstChildEntry = docsCatalog.firstEntryStartingWith(slug)
        if (firstChildEntry != null && firstChildEntry.slug != slug) {
            redirect(basePath + "/${firstChildEntry.slug}")
            return
        }

        val canonicalPath = canonicalPathForSlug(entry.slug)
        val fetchedDocument = try {
            docsService.fetchDocument(entry.repoPath, branch)
        } catch (_: DocsService.DocumentNotFound) {
            val page = docsRouter.notFound(requestPath, navTree, origin)
            respondSummonPage(page, 404)
            return
        }

        val rendered = markdownRenderer.render(fetchedDocument.body, canonicalPath)
        val rewrittenHtml = linkRewriter.rewriteHtml(
            html = rendered.html,
            requestPath = canonicalPath,
            repoPath = entry.repoPath,
            docsRoot = config.normalizedDocsRoot,
            branch = branch
        )
        val neighbors = docsCatalog.neighbors(entry.slug)
        val page = docsRouter.render(
            requestPath = canonicalPath,
            origin = origin,
            html = rewrittenHtml,
            meta = rendered.meta,
            toc = rendered.toc,
            sidebar = navTree,
            neighbors = neighbors,
            basePath = basePath
        )

        fetchedDocument.etag?.let { response.setHeader("ETag", it) }
        fetchedDocument.lastModified?.let {
            response.setHeader(
                "Last-Modified",
                DateTimeFormatter.RFC_1123_DATE_TIME.format(it.toJavaInstant().atZone(ZoneId.of("UTC")))
            )
        }
        response.setHeader("Cache-Control", "public, max-age=60")
        respondSummonPage(page)
    }

    get("/__asset/*") { exchange ->
        val assetPath = exchange.pathParam("*")
        val branch = exchange.request.queryParameter("ref") ?: config.defaultBranch
        
        if (assetPath.isNullOrBlank()) {
            exchange.respond(400, "Missing asset path")
            return@get
        }
        val repoPath = "${config.normalizedDocsRoot}/$assetPath"
        runCatching { docsService.fetchAsset(repoPath, branch) }
            .onSuccess { asset ->
                val contentType = asset.contentType ?: "application/octet-stream"
                exchange.response.setHeader("Cache-Control", "public, max-age=86400")
                asset.etag?.let { exchange.response.setHeader("ETag", it) }
                exchange.respondBytes(200, contentType, asset.bytes)
            }
            .onFailure {
                exchange.respond(404, "Asset not found")
            }
    }

    post("/__hooks/github") { exchange ->
        webhookHandler.handle(exchange)
    }

    get("/") { exchange ->
        exchange.renderDocsPage()
    }

    get("/*") { exchange ->
        exchange.renderDocsPage()
    }
}

private fun extractBranch(path: String, config: DocsConfig): Pair<String, String> {
    val segments = path.trim().trim('/').split('/').filter { it.isNotBlank() }
    if (segments.size >= 2 && segments.first().equals("v", ignoreCase = true)) {
        val branch = segments[1]
        val remainder = segments.drop(2).joinToString("/")
        return branch to remainder
    }
    return config.defaultBranch to segments.joinToString("/")
}

private fun canonicalPathForSlug(slug: String): String =
    if (slug == DocsCatalog.SLUG_ROOT) "/" else "/$slug"

private fun normalizeSlug(pathPart: String): String {
    var slug = pathPart.trim().trim('/')
    if (slug.isBlank()) return DocsCatalog.SLUG_ROOT

    slug = removeSuffixIgnoreCase(slug, ".md")
    slug = removeSuffixIgnoreCase(slug, "/readme")
    slug = removeSuffixIgnoreCase(slug, "/summon-readme")
    slug = removeSuffixIgnoreCase(slug, "/index")

    slug = slug.trim('/')
    return slug.lowercase().ifBlank { DocsCatalog.SLUG_ROOT }
}

private fun removeSuffixIgnoreCase(value: String, suffix: String): String {
    return if (value.endsWith(suffix, ignoreCase = true)) {
        value.substring(0, value.length - suffix.length)
    } else {
        value
    }
}

private fun normalize(path: String): String =
    if (path.isBlank()) "/" else path.trim().trimEnd('/')
