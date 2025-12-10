package code.yousef

import code.yousef.config.AppConfig
import code.yousef.firestore.FirestoreContentStore
import code.yousef.firestore.PortfolioMetaService
import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import com.google.cloud.firestore.Firestore
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.routes.portfolioRoutes
import code.yousef.portfolio.server.routes.docsRoutes
import code.yousef.portfolio.ssr.*
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.summonStaticAssets
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.summonCallbackHandler
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
    portfolioMetaService: PortfolioMetaService,
    firestore: Firestore
) {
    val bootInstant = Instant.now()
    val contentStore = FirestoreContentStore(firestore)
    val contentService = PortfolioContentService(contentStore)
    val adminContentService = AdminContentService(contentStore)
    
    // Use environment variable for credentials path, or fall back to relative path
    // The relative path is resolved from the project root (where gradlew is run)
    val credentialsPath = System.getenv("ADMIN_CREDENTIALS_PATH")
        ?.let { Paths.get(it) }
        ?: Paths.get("storage/admin-credentials.json").toAbsolutePath()
    
    val adminAuthService = AdminAuthService(credentialsPath = credentialsPath)
    val portfolioRenderer = PortfolioRenderer(contentService = contentService)
    val blogRenderer = BlogRenderer(contentService = contentService)
    val adminRenderer = AdminRenderer()
    val contactService = ContactService()
    // hydrationBundle removed as it is now embedded in Summon Core
    
    // Summon docs services
    val docsConfig = DocsConfig.fromEnv()
    val docsCache = DocsCache(docsConfig.cacheTtlSeconds)
    val docsService = DocsService(docsConfig, docsCache)
    val docsCatalog = DocsCatalog(docsConfig)
    val markdownRenderer = MarkdownRenderer()
    val linkRewriter = LinkRewriter()
    val seoExtractor = SeoExtractor(docsConfig)
    val docsRouter = DocsRouter(seoExtractor, docsConfig.publicOriginPortfolio, DocsBranding.summon())
    val summonLandingRenderer = SummonLandingRenderer()
    val webhookHandler = WebhookHandler(docsService, docsCache, docsConfig, docsCatalog)
    
    // Materia docs services
    val materiaDocsConfig = DocsConfig.materiaFromEnv()
    val materiaDocsCache = DocsCache(materiaDocsConfig.cacheTtlSeconds)
    val materiaDocsService = DocsService(materiaDocsConfig, materiaDocsCache)
    val materiaDocsCatalog = DocsCatalog(materiaDocsConfig)
    val materiaSeoExtractor = SeoExtractor(materiaDocsConfig)
    val materiaDocsRouter = DocsRouter(materiaSeoExtractor, materiaDocsConfig.publicOriginPortfolio, DocsBranding.materia())
    val materiaLandingRenderer = MateriaLandingRenderer()
    val materiaWebhookHandler = WebhookHandler(materiaDocsService, materiaDocsCache, materiaDocsConfig, materiaDocsCatalog)
    
    val summonLandingHosts =
        (System.getenv("SUMMON_LANDING_HOSTS") ?: "summon.yousef.codes,summon.dev.yousef.codes")
        .split(",")
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    val docsHosts = (System.getenv("DOCS_HOSTS")
        ?: "summon.yousef.codes,summon.dev.yousef.codes,summon.localhost,docs.localhost,summon.site")
        .split(",")
        .mapNotNull { it.trim().takeIf { host -> host.isNotEmpty() } }
    
    // Materia docs hosts (materia.yousef.codes and variants)
    val materiaDocsHosts = (System.getenv("MATERIA_DOCS_HOSTS")
        ?: "materia.yousef.codes,materia.dev.yousef.codes,materia.localhost")
        .split(",")
        .mapNotNull { it.trim().takeIf { host -> host.isNotEmpty() } }
    
    val configuredPort = appConfig.port

    routing {
        staticResources("/static", "static")

        // Summon: Serve hydration assets (JS, WASM) automatically from the library
        summonStaticAssets()

        // Summon: Handle callback requests for interactive components
        summonCallbackHandler()

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
                        val host = call.request.host()
                        val links = resolveEnvironmentLinks(host)
                        EnvironmentLinksRegistry.withLinks(links) {
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
                            basePath = "/docs",
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

        // Mount Materia docs on materia.* hosts at /docs
        materiaDocsHosts
            .flatMap { rawHost ->
                val parts = rawHost.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (port != null) listOf(host to port) else listOf(host to null, host to configuredPort)
            }
            .forEach { (hostName, port) ->
                val mountMateriaDocsForHost: Route.() -> Unit = {
                    get("/health") { call.respondHealth(bootInstant) }
                    get("/healthz") { call.respondHealthz(appConfig, bootInstant) }
                    // Serve Materia landing page at /
                    get("/") {
                        val host = call.request.host()
                        val links = resolveEnvironmentLinks(host)
                        EnvironmentLinksRegistry.withLinks(links) {
                            val page = materiaLandingRenderer.landingPage()
                            SummonRenderLock.withLock {
                                call.respondSummonHydrated {
                                    val renderer = getPlatformRenderer()
                                    renderer.renderHeadElements(page.head)
                                    page.content()
                                }
                            }
                        }
                    }
                    // Serve Materia docs at /docs on materia.* hosts
                    route("/docs") {
                        docsRoutes(
                            docsService = materiaDocsService,
                            markdownRenderer = markdownRenderer,
                            linkRewriter = linkRewriter,
                            docsRouter = materiaDocsRouter,
                            webhookHandler = materiaWebhookHandler,
                            config = materiaDocsConfig,
                            docsCatalog = materiaDocsCatalog,
                            pathResolver = { call ->
                                val raw = call.request.path()
                                raw.removePrefix("/docs").ifBlank { "/" }
                            },
                            basePath = "/docs",
                            registerInfrastructure = false
                        )
                    }
                }
                if (port != null) {
                    host(hostName, port) { mountMateriaDocsForHost() }
                } else {
                    host(hostName) { mountMateriaDocsForHost() }
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
                basePath = "/summon",
                registerInfrastructure = false
            )
        }

        // Materia docs accessible on portfolio at /materia
        route("/materia") {
            docsRoutes(
                docsService = materiaDocsService,
                markdownRenderer = markdownRenderer,
                linkRewriter = linkRewriter,
                docsRouter = materiaDocsRouter,
                webhookHandler = materiaWebhookHandler,
                config = materiaDocsConfig,
                docsCatalog = materiaDocsCatalog,
                pathResolver = { call ->
                    val raw = call.request.path()
                    val stripped = raw.removePrefix("/materia")
                    stripped.ifBlank { "/" }
                },
                basePath = "/materia",
                registerInfrastructure = false
            )
        }

        // Materia webhook endpoint (separate from Summon)
        post("/__hooks/github/materia") {
            materiaWebhookHandler.handle(call)
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
