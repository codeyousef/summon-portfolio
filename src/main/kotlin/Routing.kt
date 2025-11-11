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
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.SITE_URL
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
    val webhookHandler = WebhookHandler(docsService, docsCache, docsConfig, docsCatalog)
    val docsHosts = (System.getenv("DOCS_HOSTS") ?: "summon.yousef.codes,summon.localhost,docs.localhost")
        .split(",")
        .mapNotNull { it.trim().takeIf { host -> host.isNotEmpty() } }
    val configuredPort = appConfig.port

    routing {
        staticResources("/static", "static")
        hydrationBundle?.let { bundle ->
            get("/summon-hydration.js") {
                call.respondBytes(bundle, ContentType.Application.JavaScript)
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

        docsHosts
            .flatMap { rawHost ->
                val parts = rawHost.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (port != null) listOf(host to port)
                else listOf(host to null, host to configuredPort)
            }
            .forEach { (hostName, port) ->
                fun Route.mountDocsRoutes(pathResolver: (ApplicationCall) -> String = { call -> call.request.path() }) {
                    get("/health") {
                        call.respondHealth(bootInstant)
                    }
                    docsRoutes(
                        docsService = docsService,
                        markdownRenderer = markdownRenderer,
                        linkRewriter = linkRewriter,
                        docsRouter = docsRouter,
                        webhookHandler = webhookHandler,
                        config = docsConfig,
                        docsCatalog = docsCatalog,
                        pathResolver = pathResolver,
                        registerInfrastructure = false
                    )
                }
                if (port != null) {
                    host(hostName, port) {
                        mountDocsRoutes()
                    }
                } else {
                    host(hostName) {
                        mountDocsRoutes()
                    }
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
            val uptime = Duration.between(bootInstant, Instant.now()).seconds
            call.respond(
                HealthzResponse(
                    ok = true,
                    projectId = appConfig.projectId,
                    emulator = appConfig.emulatorHost != null,
                    uptimeSeconds = uptime
                )
            )
        }
        get("/health") {
            call.respondHealth(bootInstant)
        }
        get("/db-test") {
            val now = System.currentTimeMillis()
            portfolioMetaService.touchHello(now)
            val data = portfolioMetaService.fetchHello()
            val payload = DbTestResponse(
                ok = true,
                exists = data != null,
                data = data?.let(::toJsonElement) ?: JsonNull
            )
            call.respond(payload)
        }
        get("/sitemap.xml") {
            val sitemap = generateSitemapXml(contentService)
            call.respondText(sitemap, ContentType.Application.Xml)
        }
    }
}

private fun generateSitemapXml(contentService: PortfolioContentService): String {
    val urls = mutableSetOf(
        "$SITE_URL/",
        "$SITE_URL/blog",
        "$SITE_URL/ar",
        "$SITE_URL/ar/blog"
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
    val data: JsonElement
)

private suspend fun ApplicationCall.respondHealth(bootInstant: Instant) {
    val uptime = Duration.between(bootInstant, Instant.now()).seconds
    respond(HealthStatus(status = "ok", uptimeSeconds = uptime))
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
