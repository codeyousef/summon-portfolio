package code.yousef

import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.routes.portfolioRoutes
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.SITE_URL
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

    routing {
        staticResources("/static", "static")
        hydrationBundle?.let { bundle ->
            get("/summon-hydration.js") {
                call.respondBytes(bundle, ContentType.Application.JavaScript)
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
            val uptime = Duration.between(bootInstant, Instant.now()).seconds
            call.respond(mapOf("status" to "ok", "uptimeSeconds" to uptime))
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
