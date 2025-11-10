package code.yousef

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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

fun Application.configureRouting() {
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
    val configuredPort = environment.config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull()
        ?: System.getenv("PORT")?.toIntOrNull()
        ?: 8080

    routing {
        staticResources("/static", "static")
        hydrationBundle?.let { bundle ->
            get("/summon-hydration.js") {
                call.respondBytes(bundle, ContentType.Application.JavaScript)
            }
        }

        docsHosts
            .flatMap { rawHost ->
                val parts = rawHost.split(":", limit = 2)
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (port != null) listOf(host to port)
                else listOf(host to null, host to configuredPort)
            }
            .forEach { (hostName, port) ->
                fun Route.mountDocsRoutes() {
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
                        docsCatalog = docsCatalog
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
        get("/health") {
            call.respondHealth(bootInstant)
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

private suspend fun ApplicationCall.respondHealth(bootInstant: Instant) {
    val uptime = Duration.between(bootInstant, Instant.now()).seconds
    respond(HealthStatus(status = "ok", uptimeSeconds = uptime))
}
