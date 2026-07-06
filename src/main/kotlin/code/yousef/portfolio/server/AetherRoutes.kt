@file:OptIn(kotlin.time.ExperimentalTime::class)
package code.yousef.portfolio.server

import code.yousef.portfolio.admin.auth.AdminAuthProvider
import code.yousef.portfolio.admin.auth.AdminAuthProvider.AuthResult
import code.yousef.portfolio.admin.auth.AdminSession
import code.yousef.portfolio.ai.AiProgressStore
import code.yousef.portfolio.api.toDto
import code.yousef.portfolio.contact.ContactRequest
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.photography.MultipartFormData
import code.yousef.portfolio.photography.PhotographyService
import code.yousef.portfolio.photography.extractMultipartBoundary
import code.yousef.portfolio.photography.parseMultipartFormData
import code.yousef.portfolio.seen.SeenExecutionService
import code.yousef.portfolio.seen.SeenPlaygroundRenderer
import code.yousef.portfolio.seen.SeenRunRequest
import code.yousef.portfolio.ssr.*
import code.yousef.portfolio.ui.fifthwall.FifthWallController
import code.yousef.portfolio.ui.fifthwall.FifthWallSessionStore
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetryPayload
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetryStore
import code.yousef.portfolio.ui.admin.AdminChangePasswordPage
import code.yousef.portfolio.ui.admin.AdminLoginPage
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.jvm.receiveParameters
import codes.yousef.aether.core.respondJson
import codes.yousef.aether.core.session.session
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParam
import codes.yousef.aether.web.pathParamOrThrow
import codes.yousef.sigil.schema.SigilJson
import codes.yousef.sigil.summon.canvas.SigilSceneCallbackRegistry
import codes.yousef.sigil.summon.canvas.SigilSceneEventCallbackResponse
import codes.yousef.summon.runtime.CallbackRegistry
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

    get("/docs-debug") { exchange ->
        val debugInfo = mapOf(
            "source" to docsConfig.docsSource.name,
            "owner" to docsConfig.githubOwner,
            "repo" to docsConfig.githubRepo,
            "branch" to docsConfig.defaultBranch,
            "root" to docsConfig.docsRoot,
            "localRoot" to docsConfig.localDocsRoot.toString(),
            "localRootExists" to Files.exists(docsConfig.localDocsRoot),
            "slugs" to docsCatalog.allSlugs()
        )
        exchange.respondJson(200, debugInfo)
    }
}

internal fun Router.portfolioRoutes(
    portfolioRenderer: PortfolioRenderer,
    blogRenderer: BlogRenderer,
    fifthWallRenderer: FifthWallRenderer,
    contactService: ContactService,
    contentService: PortfolioContentService,
    adminAuthService: AdminAuthProvider,
    photographyService: PhotographyService,
    aiCurriculumRenderer: AiCurriculumRenderer? = null,
    aiProgressStore: AiProgressStore? = null,
    fifthWallTelemetryStore: FifthWallTelemetryStore? = null,
    fifthWallSessionStore: FifthWallSessionStore = FifthWallSessionStore(),
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

    get("/photography") { exchange ->
        val page = portfolioRenderer.photographyPage(photographyService.publicPhotos())
        exchange.respondSummonPage(page)
    }

    get("/uploads/photography/:id") { exchange ->
        val id = URLDecoder.decode(exchange.pathParam("id").orEmpty(), StandardCharsets.UTF_8)
        val asset = photographyService.assetForPublishedPhoto(id)
        if (asset == null) {
            exchange.respond(404, "Photo not found")
            return@get
        }
        exchange.response.setHeader("Cache-Control", "public, max-age=604800")
        exchange.response.setHeader("X-Content-Type-Options", "nosniff")
        exchange.respondBytes(200, asset.contentType, asset.bytes)
    }

    get("/fifth-wall") { exchange ->
        val controller = fifthWallSessionStore.controllerFor(exchange)
        exchange.request.queryParameter("action")
            ?.takeIf { it.isNotBlank() }
            ?.let { action -> exchange.handleFifthWallAction(controller, action) }
        val page = fifthWallRenderer.fifthWallPage(
            controller = controller,
            state = controller.state.value,
            locale = PortfolioLocale.EN
        )
        exchange.respondSummonPage(page)
    }

    post("/summon/callback/:callbackId") { exchange ->
        val callbackId = exchange.pathParam("callbackId").orEmpty()
        if (callbackId.isBlank()) {
            exchange.respondJson(400, mapOf("error" to "Missing callback id"))
            return@post
        }

        val executed = CallbackRegistry.executeCallback(callbackId)
        if (executed) {
            exchange.respondJson(200, mapOf("ok" to true))
        } else {
            exchange.respondJson(404, mapOf("error" to "Callback not found"))
        }
    }

    post("/sigil/callback/:callbackId") { exchange ->
        val callbackId = exchange.pathParam("callbackId").orEmpty()
        if (callbackId.isBlank()) {
            exchange.respondJson(400, mapOf("error" to "Missing callback id"))
            return@post
        }

        val result = SigilSceneCallbackRegistry.executeCallback(callbackId)
        val body = SigilJson.encodeToString(
            SigilSceneEventCallbackResponse.serializer(),
            result.response
        )
        exchange.response.setHeader("Content-Type", "application/json")
        exchange.respond(result.statusCode, body)
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

    // Arabic locale routes
    get("/ar") { exchange ->
        val servicesParam = exchange.request.queryParameter("services")
        val page = portfolioRenderer.landingPage(
            locale = PortfolioLocale.AR,
            servicesModalOpen = servicesParam == "true"
        )
        exchange.respondSummonPage(page)
    }

    get("/ar/projects") { exchange ->
        val page = portfolioRenderer.projectsPage(locale = PortfolioLocale.AR)
        exchange.respondSummonPage(page)
    }

    get("/ar/services") { exchange ->
        val servicesParam = exchange.request.queryParameter("services")
        val page = portfolioRenderer.servicesPage(
            locale = PortfolioLocale.AR,
            servicesModalOpen = servicesParam == "true"
        )
        exchange.respondSummonPage(page)
    }

    get("/ar/full-time") { exchange ->
        val page = portfolioRenderer.fullTimePage(locale = PortfolioLocale.AR)
        exchange.respondSummonPage(page)
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

    if (fifthWallTelemetryStore != null) {
        post("/api/fifth-wall/telemetry") { exchange ->
            val body = exchange.request.bodyText().trim()
            if (body.isBlank()) {
                exchange.respondJson(400, mapOf("error" to "Invalid request body"))
                return@post
            }

            val payload = runCatching {
                json.decodeFromString<FifthWallTelemetryPayload>(body)
            }.getOrElse {
                exchange.respondJson(400, mapOf("error" to "Invalid telemetry payload"))
                return@post
            }

            fifthWallTelemetryStore.merge(payload)
            exchange.respondJson(
                202,
                mapOf(
                    "ok" to "true",
                    "received" to payload.events.size.toString(),
                    "revision" to payload.revision.toString()
                )
            )
        }
    }

    // AI Curriculum Routes (auth-gated by middleware in Application.kt)
    if (aiCurriculumRenderer != null && aiProgressStore != null) {
        // API routes registered FIRST (before :slug wildcard)
        get("/ai/api/progress") { exchange ->
            val data = aiProgressStore.getProgress()
            exchange.respondJson(200, data)
        }

        post("/ai/api/progress") { exchange ->
            val params = exchange.receiveParameters()
            val id = params["id"]?.trim() ?: ""
            val completed = params["completed"]?.trim()?.toBooleanStrictOrNull()
            val redirect = params["redirect"]?.trim()

            if (id.isEmpty() || completed == null) {
                exchange.respondJson(400, mapOf("error" to "Missing id or completed field"))
                return@post
            }
            if (!id.matches(Regex("\\d+\\.\\d+(\\.\\d+)?"))) {
                exchange.respondJson(400, mapOf("error" to "Invalid subsection ID"))
                return@post
            }
            aiProgressStore.updateProgress(id, completed)
            if (!redirect.isNullOrBlank() && redirect.startsWith("/ai")) {
                exchange.redirect(redirect)
            } else {
                exchange.respondJson(200, mapOf("ok" to true))
            }
        }

        // Overview dashboard
        get("/ai") { exchange ->
            exchange.respondSummonPage(aiCurriculumRenderer.overviewPage())
        }

        // Sub-lesson pages (must be before :slug to avoid capture)
        get("/ai/:slug/:sub") { exchange ->
            val slug = exchange.pathParam("slug") ?: ""
            val sub = exchange.pathParam("sub")?.toIntOrNull() ?: 0
            val page = aiCurriculumRenderer.subLessonPage(slug, sub)
            if (page != null) exchange.respondSummonPage(page)
            else exchange.respond(404, "Sub-lesson not found")
        }

        // Lesson landing pages
        get("/ai/:slug") { exchange ->
            val slug = exchange.pathParam("slug") ?: ""
            val page = aiCurriculumRenderer.lessonLandingPage(slug)
            if (page != null) exchange.respondSummonPage(page)
            else exchange.respond(404, "Lesson not found")
        }
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

    get("/admin") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login?next=/admin")
            return@get
        }
        if (session.mustChangePassword) {
            exchange.redirect("/admin/change-password")
            return@get
        }
        exchange.respondSummonPage(portfolioRenderer.adminHomePage(session.username))
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
        LoggerFactory.getLogger("AetherRoutes").info("change-password params: {}", params)

        val rawUsername = params["username"].orEmpty().trim()
        val password = params["password"].orEmpty()
        val confirm = params["confirm"].orEmpty()

        // If username is omitted, reuse the current session username so users only need to set a new password.
        val username = if (rawUsername.isNotBlank()) rawUsername else session.username

        when {
            password.isBlank() -> {
                val page = adminChangePasswordPage(session.username, "Password cannot be empty")
                exchange.respondSummonPage(page, 400)
            }
            confirm.isNotBlank() && confirm != password -> {
                val page = adminChangePasswordPage(session.username, "Passwords do not match")
                exchange.respondSummonPage(page, 400)
            }
            username.isBlank() -> {
                val page = adminChangePasswordPage(session.username, "Username cannot be empty")
                exchange.respondSummonPage(page, 400)
            }
            else -> {
                adminAuthService.updateCredentials(username, password)
                exchange.redirect("/admin")
            }
        }
    }

    get("/admin/photography") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login?next=/admin/photography")
            return@get
        }
        val successMessage = when {
            exchange.request.queryParameter("uploaded") == "true" -> "Photo uploaded."
            exchange.request.queryParameter("saved") == "true" -> "Photo updated."
            exchange.request.queryParameter("deleted") == "true" -> "Photo deleted."
            else -> null
        }
        exchange.respondSummonPage(
            portfolioRenderer.photographyAdminPage(
                photos = photographyService.adminPhotos(),
                successMessage = successMessage
            )
        )
    }

    post("/admin/photography") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login?next=/admin/photography")
            return@post
        }

        val boundary = extractMultipartBoundary(exchange.request.headers["Content-Type"].orEmpty())
        if (boundary == null) {
            exchange.respondSummonPage(
                portfolioRenderer.photographyAdminPage(
                    photos = photographyService.adminPhotos(),
                    errorMessage = "Invalid upload request."
                ),
                400
            )
            return@post
        }

        val formData = parseMultipartFormData(exchange.request.bodyBytes(), boundary, fileFieldName = "photo")
        when (val result = photographyService.upload(formData.fields, formData.file)) {
            is PhotographyService.UploadResult.Success -> exchange.redirect("/admin/photography?uploaded=true")
            is PhotographyService.UploadResult.Error -> exchange.respondSummonPage(
                portfolioRenderer.photographyAdminPage(
                    photos = photographyService.adminPhotos(),
                    errorMessage = result.message
                ),
                400
            )
        }
    }

    post("/admin/photography/:id") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login?next=/admin/photography")
            return@post
        }
        val id = exchange.pathParam("id").orEmpty()
        val contentType = exchange.request.headers["Content-Type"].orEmpty()
        val formData = if (contentType.contains("multipart/form-data", ignoreCase = true)) {
            val boundary = extractMultipartBoundary(contentType)
            if (boundary == null) {
                exchange.respondSummonPage(
                    portfolioRenderer.photographyAdminPage(
                        photos = photographyService.adminPhotos(),
                        errorMessage = "Invalid upload request."
                    ),
                    400
                )
                return@post
            }
            parseMultipartFormData(exchange.request.bodyBytes(), boundary, fileFieldName = "photo")
        } else {
            MultipartFormData(fields = exchange.receiveParameters(), file = null)
        }
        when (val result = photographyService.update(id, formData.fields, formData.file)) {
            is PhotographyService.UpdateResult.Success -> exchange.redirect("/admin/photography?saved=true")
            is PhotographyService.UpdateResult.Error -> exchange.respondSummonPage(
                portfolioRenderer.photographyAdminPage(
                    photos = photographyService.adminPhotos(),
                    errorMessage = result.message
                ),
                400
            )
        }
    }

    post("/admin/photography/:id/delete") { exchange ->
        val session = exchange.getAdminSession()
        if (session == null) {
            exchange.redirect("/admin/login?next=/admin/photography")
            return@post
        }
        val id = exchange.pathParam("id").orEmpty()
        when (val result = photographyService.delete(id)) {
            is PhotographyService.DeleteResult.Success -> exchange.redirect("/admin/photography?deleted=true")
            is PhotographyService.DeleteResult.Error -> exchange.respondSummonPage(
                portfolioRenderer.photographyAdminPage(
                    photos = photographyService.adminPhotos(),
                    errorMessage = result.message
                ),
                400
            )
        }
    }
}

fun Router.seenRoutes(
    landingRenderer: SeenLandingRenderer,
    playgroundRenderer: SeenPlaygroundRenderer,
    executionService: SeenExecutionService
) {
    get("/") { exchange ->
        val page = landingRenderer.landingPage()
        exchange.respondSummonPage(page)
    }

    get("/playground") { exchange ->
        val page = playgroundRenderer.playgroundPage()
        exchange.respondSummonPage(page)
    }

    post("/api/seen/run") { exchange ->
        val body = try {
            json.decodeFromString<SeenRunRequest>(exchange.request.bodyText())
        } catch (e: Exception) {
            exchange.respondJson(400, mapOf("error" to "Invalid request body"))
            return@post
        }
        val code = body.code.take(10_000)
        if (code.isBlank()) {
            exchange.respondJson(400, mapOf("error" to "No code provided"))
            return@post
        }
        val result = executionService.execute(code, body.language)
        exchange.respondJson(200, result)
    }
}

private suspend fun Exchange.handleFifthWallAction(
    controller: FifthWallController,
    action: String
) {
    when (action) {
        "start" -> controller.startShift()
        "reset" -> controller.reset()
        "fake-restart" -> controller.fakeRestart()
        "advance" -> controller.advanceLevel()
        "select" -> request.queryParameter("package")
            .decodeQueryComponent()
            ?.takeIf { it.isNotBlank() }
            ?.let(controller::selectPackage)
        "route-truck" -> request.queryParameter("truck")
            ?.toIntOrNull()
            ?.let(controller::routeToTruck)
        "route-return" -> controller.routeToReturn()
        "drop-truck" -> request.queryParameter("truck")
            ?.toIntOrNull()
            ?.let { index -> controller.dropOnTruck(index, request.queryParameter("package").decodeQueryComponent()) }
        "drop-return" -> controller.dropOnReturn(request.queryParameter("package").decodeQueryComponent())
        "repair" -> controller.repairGlitch()
        "confidence" -> request.queryParameter("value")
            .decodeQueryComponent()
            ?.takeIf { it.isNotBlank() }
            ?.let(controller::recordConfidence)
        "submit-prediction" -> {
            controller.updatePredictionInput(request.queryParameter("value").decodeQueryComponent().orEmpty())
            controller.submitPrediction()
        }
        "submit-rule-guess" -> {
            controller.updateRuleGuess(request.queryParameter("value").decodeQueryComponent().orEmpty())
            controller.submitRuleGuess()
        }
        "submit-discussion" -> {
            controller.updateDiscussionReply(request.queryParameter("value").decodeQueryComponent().orEmpty())
            controller.submitDiscussionReply()
        }
        "send-chat" -> {
            controller.updateChatDraft(request.queryParameter("value").decodeQueryComponent().orEmpty())
            controller.sendChatMessage()
        }
    }
}

private fun String?.decodeQueryComponent(): String? =
    this?.let { encoded ->
        runCatching {
            URLDecoder.decode(encoded, StandardCharsets.UTF_8)
        }.getOrDefault(encoded)
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
        LoggerFactory.getLogger("DocsRoutes").info("Docs request: path={}, branch={}, slug={}", requestPath, branch, slug)
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
            val page = docsRouter.notFound(requestPath, navTree, origin, basePath)
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
            val page = docsRouter.notFound(requestPath, navTree, origin, basePath)
            respondSummonPage(page, 404)
            return
        }

        val rendered = markdownRenderer.render(fetchedDocument.body, canonicalPath)
        val rewrittenHtml = linkRewriter.rewriteHtml(
            html = rendered.html,
            requestPath = canonicalPath,
            repoPath = entry.repoPath,
            docsRoot = config.normalizedDocsRoot,
            branch = branch,
            basePath = basePath
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

    val prefix = if (basePath.isBlank()) "" else basePath.trimEnd('/')

    get("$prefix/__asset/*") { exchange ->
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

    post("$prefix/__hooks/github") { exchange ->
        webhookHandler.handle(exchange)
    }

    if (prefix.isNotEmpty()) {
        get(prefix) { exchange ->
            exchange.renderDocsPage()
        }
    }

    get("$prefix/") { exchange ->
        exchange.renderDocsPage()
    }

    get("$prefix/*") { exchange ->
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
