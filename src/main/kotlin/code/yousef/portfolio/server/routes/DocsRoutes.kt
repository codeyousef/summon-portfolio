package code.yousef.portfolio.server.routes

import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import code.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Route.docsRoutes(
    docsService: DocsService,
    markdownRenderer: MarkdownRenderer,
    linkRewriter: LinkRewriter,
    docsRouter: DocsRouter,
    webhookHandler: WebhookHandler,
    config: DocsConfig,
    docsCatalog: DocsCatalog,
    pathResolver: (ApplicationCall) -> String = { it.request.path() },
    registerPageRoutes: Boolean = true,
    registerInfrastructure: Boolean = true
) {
    suspend fun ApplicationCall.renderDocsPage() {
        val requestPath = pathResolver(this).ifBlank { "/" }
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
            val conn = request.local
            val defaultPort = if (conn.scheme == "https") 443 else 80
            val portValue = conn.serverPort
            val portPart = if (portValue == defaultPort || portValue == -1) "" else ":$portValue"
            "${conn.scheme}://${conn.serverHost}$portPart"
        }

        if (entry == null) {
            val page = docsRouter.notFound(requestPath, navTree, origin)
            respondDocsPage(page, HttpStatusCode.NotFound)
            return
        }

        val canonicalPath = canonicalPathForSlug(entry.slug)
        val fetchedDocument = try {
            docsService.fetchDocument(entry.repoPath, branch)
        } catch (_: DocsService.DocumentNotFound) {
            val page = docsRouter.notFound(requestPath, navTree, origin)
            respondDocsPage(page, HttpStatusCode.NotFound)
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
            neighbors = neighbors
        )

        fetchedDocument.etag?.let { response.headers.append(HttpHeaders.ETag, it, false) }
        fetchedDocument.lastModified?.let {
            response.headers.append(
                HttpHeaders.LastModified,
                DateTimeFormatter.RFC_1123_DATE_TIME.format(it.toJavaInstant().atZone(TimeZone.UTC.toJavaZoneId()))
            )
        }
        response.headers.append(HttpHeaders.CacheControl, "public, max-age=60", false)
        respondDocsPage(page)
    }

    if (registerInfrastructure) {
        get("/__asset/{assetPath...}") {
            val pathSegments = call.parameters.getAll("assetPath") ?: emptyList()
            val branch = call.request.queryParameters["ref"] ?: config.defaultBranch
            val assetPath = pathSegments.joinToString("/").trim('/')
            if (assetPath.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing asset path")
                return@get
            }
            val repoPath = "${config.normalizedDocsRoot}/$assetPath"
            runCatching { docsService.fetchAsset(repoPath, branch) }
                .onSuccess { asset ->
                    val contentType = asset.contentType?.let(ContentType::parse) ?: ContentType.Application.OctetStream
                    call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400", false)
                    asset.etag?.let { call.response.headers.append(HttpHeaders.ETag, it, false) }
                    call.respondBytes(asset.bytes, contentType)
                }
                .onFailure {
                    call.respond(HttpStatusCode.NotFound, "Asset not found")
                }
        }

        post("/__hooks/github") {
            webhookHandler.handle(call)
        }
    }

    if (registerPageRoutes) {
        get("/") {
            call.renderDocsPage()
        }

        get("{...}") {
            call.renderDocsPage()
        }
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

private suspend fun io.ktor.server.application.ApplicationCall.respondDocsPage(
    page: SummonPage,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondSummonHydrated(status) {
        val renderer = getPlatformRenderer()
        renderer.renderHeadElements(page.head)
        page.content()
    }
}

private fun kotlinx.datetime.TimeZone.toJavaZoneId(): ZoneId = ZoneId.of(id)

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
