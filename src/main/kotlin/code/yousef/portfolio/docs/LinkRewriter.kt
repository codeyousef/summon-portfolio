package code.yousef.portfolio.docs

import org.jsoup.Jsoup
import java.nio.file.Paths

class LinkRewriter {

    fun rewriteHtml(
        html: String,
        requestPath: String,
        repoPath: String,
        docsRoot: String,
        branch: String,
        basePath: String = ""
    ): String {
        val document = Jsoup.parseBodyFragment(html)
        val repoDirectory = repoPath.substringBeforeLast('/', docsRoot)

        document.select("a[href]").forEach { element ->
            val href = element.attr("href").trim()
            if (href.isBlank()) return@forEach

            if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) {
                element.attr("rel", "noopener")
                element.attr("target", "_blank")
                return@forEach
            }

            if (href.startsWith("#")) {
                return@forEach
            }

            val (pathPart, fragment) = href.split("#", limit = 2).let {
                it[0] to it.getOrNull(1)
            }
            val resolved = resolvePath(pathPart, repoDirectory)
            val looksLikeMarkdown =
                resolved.endsWith(".md") || !resolved.substringAfterLast('/', resolved).contains('.')

            if (looksLikeMarkdown) {
                val normalized = if (resolved.endsWith(".md")) resolved else "$resolved.md"
                val stripped = stripDocsRoot(normalized, docsRoot).removeSuffix(".md")
                val newHref = buildDocsHref(requestPath, stripped, fragment, basePath)
                element.attr("href", newHref)
                element.attr("data-doc-link", "true")
                element.attr("rel", element.attr("rel").ifBlank { "noopener" })
                return@forEach
            }

            // non-markdown relative link treated as asset
            val assetPath = stripDocsRoot(resolved, docsRoot)
            val rewritten = buildAssetPath(assetPath, branch)
            val finalHref = fragment?.let { "$rewritten#$it" } ?: rewritten
            element.attr("href", finalHref)
        }

        document.select("img[src]").forEach { element ->
            val src = element.attr("src").trim()
            if (src.isBlank()) return@forEach
            if (src.startsWith("http://") || src.startsWith("https://")) return@forEach
            val resolved = resolvePath(src, repoDirectory)
            val assetPath = stripDocsRoot(resolved, docsRoot)
            val rewritten = buildAssetPath(assetPath, branch)
            element.attr("src", rewritten)
        }

        return document.body().html()
    }

    private fun resolvePath(segment: String, baseDirectory: String): String {
        val base = if (baseDirectory.isBlank()) Paths.get(".") else Paths.get(baseDirectory)
        return base.resolve(segment).normalize().toString().trimStart('/')
    }

    private fun stripDocsRoot(path: String, docsRoot: String): String {
        val normalizedRoot = docsRoot.trim('/').ifBlank { "docs" }
        return path.removePrefix("$normalizedRoot/").trimStart('/')
    }

    private fun buildDocsHref(currentPath: String, target: String, fragment: String?, basePath: String = ""): String {
        val prefix = if (target.isBlank()) basePath.ifBlank { "" } else "$basePath/${target.trimStart('/')}"
        return if (fragment != null) "$prefix#$fragment" else prefix.ifBlank { "/" }
    }

    private fun buildAssetPath(assetPath: String, branch: String): String {
        val normalized = assetPath.trimStart('/')
        return "/__asset/$normalized?ref=$branch"
    }
}
