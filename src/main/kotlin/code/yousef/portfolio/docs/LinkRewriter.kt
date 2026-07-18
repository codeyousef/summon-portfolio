package code.yousef.portfolio.docs

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class LinkRewriter {

    fun rewrite(
        document: MarkdownDocument,
        requestPath: String,
        repoPath: String,
        docsRoot: String,
        branch: String,
        basePath: String = ""
    ): MarkdownDocument = document.withLinkContext(
        MarkdownLinkContext(
            requestPath = requestPath,
            repoPath = repoPath,
            docsRoot = docsRoot,
            branch = branch,
            basePath = basePath
        )
    )
}

internal data class MarkdownLinkContext(
    val requestPath: String,
    val repoPath: String,
    val docsRoot: String,
    val branch: String,
    val basePath: String
)

internal data class ResolvedMarkdownLink(
    val href: String,
    val target: String? = null,
    val rel: String? = null,
    val isDocLink: Boolean = false
)

internal fun MarkdownDocument.resolveMarkdownLink(rawDestination: String): ResolvedMarkdownLink? {
    val href = safeUrl(rawDestination, allowMailto = true) ?: return null
    if (href.isExternalUrl() || href.startsWith("mailto:", ignoreCase = true)) {
        return ResolvedMarkdownLink(href = href, target = "_blank", rel = "noopener")
    }
    if (href.startsWith("#")) return ResolvedMarkdownLink(href = href)

    val context = linkContext ?: return ResolvedMarkdownLink(href = href)
    val repoDirectory = context.repoPath.substringBeforeLast('/', context.docsRoot)
    val (pathPart, fragment) = href.split("#", limit = 2).let { parts ->
        parts[0] to parts.getOrNull(1)
    }
    val resolved = resolvePath(pathPart, repoDirectory) ?: return null
    val looksLikeMarkdown =
        resolved.endsWith(".md", ignoreCase = true) ||
            !resolved.substringAfterLast('/', resolved).contains('.')

    if (looksLikeMarkdown) {
        val normalized = if (resolved.endsWith(".md", ignoreCase = true)) resolved else "$resolved.md"
        val stripped = stripDocsRoot(normalized, context.docsRoot).removeSuffix(".md")
        return ResolvedMarkdownLink(
            href = buildDocsHref(stripped, fragment, context.basePath),
            rel = "noopener",
            isDocLink = true
        )
    }

    val assetPath = stripDocsRoot(resolved, context.docsRoot)
    val rewritten = buildAssetPath(assetPath, context.branch)
    return ResolvedMarkdownLink(
        href = fragment?.let { "$rewritten#$it" } ?: rewritten
    )
}

internal fun MarkdownDocument.resolveMarkdownImage(rawSource: String): String? {
    val source = safeUrl(rawSource, allowMailto = false) ?: return null
    if (source.isExternalUrl()) return source

    val context = linkContext ?: return source
    val repoDirectory = context.repoPath.substringBeforeLast('/', context.docsRoot)
    val resolved = resolvePath(source, repoDirectory) ?: return null
    val assetPath = stripDocsRoot(resolved, context.docsRoot)
    return buildAssetPath(assetPath, context.branch)
}

private fun safeUrl(raw: String, allowMailto: Boolean): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    if (value.any { it.code < 0x20 || it.code == 0x7f }) return null
    if (value.startsWith("//") || value.startsWith("\\\\")) return null

    val schemeMatch = SCHEME.find(value)
    if (schemeMatch != null) {
        val scheme = schemeMatch.groupValues[1].lowercase()
        when (scheme) {
            "http", "https" -> if (!value.startsWith("$scheme://", ignoreCase = true)) return null
            "mailto" -> if (!allowMailto || value.length <= "mailto:".length) return null
            else -> return null
        }
    }
    return value
}

private fun String.isExternalUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun resolvePath(segment: String, baseDirectory: String): String? = runCatching {
    val base = if (baseDirectory.isBlank()) Paths.get(".") else Paths.get(baseDirectory)
    base.resolve(segment).normalize().toString().replace('\\', '/').trimStart('/')
}.getOrNull()?.takeUnless { it == ".." || it.startsWith("../") }

private fun stripDocsRoot(path: String, docsRoot: String): String {
    val normalizedRoot = docsRoot.trim('/').ifBlank { "docs" }
    return path.removePrefix("$normalizedRoot/").trimStart('/')
}

private fun buildDocsHref(target: String, fragment: String?, basePath: String): String {
    val prefix = if (target.isBlank()) basePath.ifBlank { "" } else "$basePath/${target.trimStart('/')}"
    return if (fragment != null) "$prefix#$fragment" else prefix.ifBlank { "/" }
}

private fun buildAssetPath(assetPath: String, branch: String): String {
    val normalized = assetPath.trimStart('/')
    val encodedBranch = URLEncoder.encode(branch, StandardCharsets.UTF_8).replace("+", "%20")
    return "/__asset/$normalized?ref=$encodedBranch"
}

private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.-]*):""")
