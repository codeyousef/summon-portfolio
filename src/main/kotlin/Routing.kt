package code.yousef

import code.yousef.config.AppConfig
import code.yousef.firestore.PortfolioMetaService
import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.routes.portfolioRoutes
import code.yousef.portfolio.server.routes.docsRoutes
import code.yousef.portfolio.ssr.*
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import codes.yousef.summon.runtime.CallbackRegistry
import codes.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

fun Application.configureRouting(
    appConfig: AppConfig,
    portfolioMetaService: PortfolioMetaService
) {
    val bootInstant = Instant.now()
    val contentStore = FileContentStore.fromEnvironment()
    val contentService = PortfolioContentService.default(contentStore)
    val adminContentService = AdminContentService(contentStore)
    val adminAuthService = AdminAuthService(
        credentialsPath = Paths.get("storage/admin-credentials.json")
    )
    val portfolioRenderer = PortfolioRenderer(contentService = contentService)
    val blogRenderer = BlogRenderer(contentService = contentService)
    val adminRenderer = AdminRenderer()
    val contactService = ContactService()
    val hydrationBundle = environment.classLoader.getResource("static/summon-hydration.js")?.readBytes()
    val docsConfig = DocsConfig.fromEnv()
    val docsCache = DocsCache(docsConfig.cacheTtlSeconds)
    val docsService = DocsService(docsConfig, docsCache)
    val docsCatalog = DocsCatalog(docsConfig)
    val markdownRenderer = MarkdownRenderer()
    val linkRewriter = LinkRewriter()
    val seoExtractor = SeoExtractor(docsConfig)
    val docsRouter = DocsRouter(seoExtractor, docsConfig.publicOriginPortfolio)
    val summonLandingRenderer = SummonLandingRenderer()
    val webhookHandler = WebhookHandler(docsService, docsCache, docsConfig, docsCatalog)
    val summonLandingHosts =
        (System.getenv("SUMMON_LANDING_HOSTS") ?: "summon.yousef.codes,summon.dev.yousef.codes,summon.uat.yousef.codes")
        .split(",")
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    val docsHosts = (System.getenv("DOCS_HOSTS")
        ?: "summon.yousef.codes,summon.dev.yousef.codes,summon.localhost,docs.localhost,summon.site")
        .split(",")
        .mapNotNull { it.trim().takeIf { host -> host.isNotEmpty() } }
    val configuredPort = appConfig.port

    routing {
        staticResources("/static", "static")

        // Explicitly serve WASM files from static with correct MIME type
        get("/static/{filename}.wasm") {
            val filename = call.parameters["filename"]
            val resource = environment.classLoader.getResource("static/$filename.wasm")
            if (resource != null) {
                call.respondBytes(resource.readBytes(), ContentType("application", "wasm"))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Serve WASM file with correct MIME type (legacy/fallback)
        get("/summon-hydration.wasm") {
            val wasmBytes = environment.classLoader.getResource("static/summon-hydration.wasm")?.readBytes()
            if (wasmBytes != null) {
                call.respondBytes(wasmBytes, ContentType("application", "wasm"))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/summon-hydration.wasm.js") {
            val wasmJsBytes = environment.classLoader.getResource("static/summon-hydration.wasm.js")?.readBytes()
            if (wasmJsBytes != null) {
                call.respondBytes(wasmJsBytes, ContentType.Application.JavaScript)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        hydrationBundle?.let { bundle ->
            get("/summon-hydration.js") {
                call.respondBytes(bundle, ContentType.Application.JavaScript)
            }
        }
        
        // Summon callback endpoint for handling onClick and other interactive events
        post("/summon/callback") {
            val request = call.receive<CallbackRequest>()
            val callbackId = request.callbackId
            
            try {
                val executed = codes.yousef.summon.runtime.CallbackRegistry.executeCallback(callbackId)
                if (executed) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    log.warn("Callback not found: $callbackId")
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Callback not found"))
                }
            } catch (e: Exception) {
                log.error("Failed to execute callback $callbackId", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        docsRoutes(
            docsService = docsService,
            markdownRenderer = markdownRenderer,
            linkRewriter = linkRewriter,
            docsRouter = docsRouter,
            webhookHandler = webhookHandler,
            config = docsConfig,
            docsCatalog = docsCatalog,
            registerPageRoutes = false,
            registerInfrastructure = true
        )

        // Mount Summon landing on summon.* hosts at '/'
        summonLandingHosts
            .flatMap { rawHost ->
                val parts = rawHost.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (port != null) listOf(host to port) else listOf(host to null, host to configuredPort)
            }
            .forEach { (hostName, port) ->
                val mountSummonForHost: Route.() -> Unit = {
                    get("/") {
                        val page = summonLandingRenderer.landingPage()
                        SummonRenderLock.withLock {
                            call.respondSummonHydrated {
                                val renderer = getPlatformRenderer()
                                renderer.renderHeadElements(page.head)
                                page.content()
                            }
                        }
                    }
                }
                if (port != null) {
                    host(hostName, port) { mountSummonForHost() }
                } else {
                    host(hostName) { mountSummonForHost() }
                }
            }

        // Keep docs mounted under /docs on all hosts and under /summon on portfolio host
        docsHosts
            .flatMap { rawHost ->
                val parts = rawHost.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (port != null) listOf(host to port) else listOf(host to null, host to configuredPort)
            }
            .forEach { (hostName, port) ->
                val mountDocsForHost: Route.() -> Unit = {
                    get("/health") { call.respondHealth(bootInstant) }
                    get("/healthz") { call.respondHealthz(appConfig, bootInstant) }
                    // Serve docs at /docs on docs hosts
                    route("/docs") {
                        docsRoutes(
                            docsService = docsService,
                            markdownRenderer = markdownRenderer,
                            linkRewriter = linkRewriter,
                            docsRouter = docsRouter,
                            webhookHandler = webhookHandler,
                            config = docsConfig,
                            docsCatalog = docsCatalog,
                            pathResolver = { call ->
                                val raw = call.request.path()
                                raw.removePrefix("/docs").ifBlank { "/" }
                            },
                            registerInfrastructure = false
                        )
                    }
                }
                if (port != null) {
                    host(hostName, port) { mountDocsForHost() }
                } else {
                    host(hostName) { mountDocsForHost() }
                }
            }

        route("/summon") {
            docsRoutes(
                docsService = docsService,
                markdownRenderer = markdownRenderer,
                linkRewriter = linkRewriter,
                docsRouter = docsRouter,
                webhookHandler = webhookHandler,
                config = docsConfig,
                docsCatalog = docsCatalog,
                pathResolver = { call ->
                    val raw = call.request.path()
                    val stripped = raw.removePrefix("/summon")
                    stripped.ifBlank { "/" }
                },
                registerInfrastructure = false
            )
        }

        // Redirect portfolio-hosted /docs and /api-reference paths to the public docs site
        route("/docs") {
            get {
                call.respondRedirect(docsBaseUrl(), permanent = false)
            }
            get("{path...}") {
                val segs = call.parameters.getAll("path") ?: emptyList()
                val suffix = segs.joinToString("/").trim('/')
                val target = if (suffix.isBlank()) docsBaseUrl() else "${docsBaseUrl().trimEnd('/')}/$suffix"
                call.respondRedirect(target, permanent = false)
            }
        }
        route("/api-reference") {
            get {
                call.respondRedirect("${docsBaseUrl().trimEnd('/')}/api-reference", permanent = false)
            }
            get("{path...}") {
                val segs = call.parameters.getAll("path") ?: emptyList()
                val suffix = segs.joinToString("/").trim('/')
                val base = "${docsBaseUrl().trimEnd('/')}/api-reference"
                val target = if (suffix.isBlank()) base else "$base/$suffix"
                call.respondRedirect(target, permanent = false)
            }
        }

        route("/") {
            portfolioRoutes(
                portfolioRenderer = portfolioRenderer,
                blogRenderer = blogRenderer,
                contactService = contactService,
                contentService = contentService,
                adminRenderer = adminRenderer,
                adminContentService = adminContentService,
                adminAuthService = adminAuthService
            )
        }
        get("/healthz") {
            call.respondHealthz(appConfig, bootInstant)
        }
        get("/health") {
            call.respondHealth(bootInstant)
        }
        get("/db-test") {
            val response = runCatching {
                val now = System.currentTimeMillis()
                portfolioMetaService.touchHello(now)
                val data = portfolioMetaService.fetchHello()
                DbTestResponse(
                    ok = true,
                    exists = data != null,
                    data = data?.let(::toJsonElement) ?: JsonNull,
                    error = null
                )
            }.getOrElse { cause ->
                log.error("db-test failed", cause)
                val message = cause.message ?: cause::class.simpleName ?: "Internal server error"
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    DbTestResponse(ok = false, exists = false, data = JsonNull, error = message)
                )
            }
            call.respond(response)
        }
        get("/sitemap.xml") {
            val sitemap = generateSitemapXml(contentService)
            call.respondText(sitemap, ContentType.Application.Xml)
        }

    }
}

private fun generateSitemapXml(contentService: PortfolioContentService): String {
    val base = portfolioBaseUrl().trimEnd('/')
    val urls = mutableSetOf(
        "$base/",
        "$base/blog",
        "$base/ar",
        "$base/ar/blog"
    )
    contentService.load().blogPosts.forEach { post ->
        urls += "https://portfolio.summon.local/blog/${post.slug}"
        urls += "https://portfolio.summon.local/ar/blog/${post.slug}"
    }
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
        urls.sorted().forEach { url ->
            appendLine("  <url>")
            appendLine("    <loc>$url</loc>")
            appendLine("  </url>")
        }
        appendLine("</urlset>")
    }
}

@Serializable
private data class CallbackRequest(val callbackId: String)

@Serializable
private data class HealthStatus(val status: String, val uptimeSeconds: Long)

@Serializable
private data class HealthzResponse(
    val ok: Boolean,
    val projectId: String,
    val emulator: Boolean,
    val uptimeSeconds: Long
)

@Serializable
private data class DbTestResponse(
    val ok: Boolean,
    val exists: Boolean,
    val data: JsonElement,
    val error: String?
)

private suspend fun ApplicationCall.respondHealth(bootInstant: Instant) {
    val uptime = Duration.between(bootInstant, Instant.now()).seconds
    respond(HealthStatus(status = "ok", uptimeSeconds = uptime))
}

private suspend fun ApplicationCall.respondHealthz(appConfig: AppConfig, bootInstant: Instant) {
    val uptime = Duration.between(bootInstant, Instant.now()).seconds
    respond(
        HealthzResponse(
            ok = true,
            projectId = appConfig.projectId,
            emulator = appConfig.emulatorHost != null,
            uptimeSeconds = uptime
        )
    )
}

private fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        value.forEach { (key, inner) ->
            if (key is String) {
                put(key, toJsonElement(inner))
            }
        }
    }
    is Iterable<*> -> buildJsonArray {
        value.forEach { add(toJsonElement(it)) }
    }
    else -> JsonPrimitive(value.toString())
}
