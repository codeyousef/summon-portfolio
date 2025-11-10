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
    config: DocsConfig
) {
    suspend fun ApplicationCall.renderDocsPage() {
        val requestPath = request.path().ifBlank { "/" }
        val (branch, pathPart) = extractBranch(requestPath, config)
        val normalizedPath = if (requestPath.endsWith("/") && pathPart.isNotEmpty()) "$pathPart/" else pathPart
        val repoCandidates = resolveRepoCandidates(normalizedPath)
        var fetchedDocument: FetchedDoc? = null
        var repoPathUsed: String? = null
        for (candidate in repoCandidates) {
            val fullPath = "${config.normalizedDocsRoot}/$candidate"
            try {
                val doc = docsService.fetchDocument(fullPath, branch)
                fetchedDocument = doc
                repoPathUsed = fullPath
                break
            } catch (_: DocsService.DocumentNotFound) {
                continue
            }
        }

        val origin = run {
            val conn = request.local
            val defaultPort = if (conn.scheme == "https") 443 else 80
            val portValue = conn.serverPort
            val portPart = if (portValue == defaultPort || portValue == -1) "" else ":$portValue"
            "${conn.scheme}://${conn.serverHost}$portPart"
        }

        if (fetchedDocument == null || repoPathUsed == null) {
            val nav = docsService.currentNavTree()
            val page = docsRouter.notFound(requestPath, nav, origin)
            respondDocsPage(page, HttpStatusCode.NotFound)
            return
        }

        val rendered = markdownRenderer.render(fetchedDocument.body, requestPath)
        val rewrittenHtml = linkRewriter.rewriteHtml(
            html = rendered.html,
            requestPath = requestPath,
            repoPath = repoPathUsed,
            docsRoot = config.normalizedDocsRoot,
            branch = branch
        )
        docsService.recordNavEntry(requestPath, rendered.meta)
        val navTree = docsService.currentNavTree()
        val neighbors = docsService.neighborLinks(requestPath)
        val page = docsRouter.render(
            requestPath = requestPath,
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

    get("/") {
        call.renderDocsPage()
    }

    get("{...}") {
        call.renderDocsPage()
    }
}

private fun resolveRepoCandidates(path: String): List<String> {
    val normalized = path.trim('/').ifBlank { "" }
    if (normalized.isBlank()) return listOf("README.md")
    val endsWithSlash = path.endsWith("/")
    if (normalized.endsWith(".md")) return listOf(normalized)
    val candidates = mutableListOf<String>()
    if (!endsWithSlash) {
        candidates += "$normalized.md"
    }
    candidates += "$normalized/index.md"
    return candidates
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
