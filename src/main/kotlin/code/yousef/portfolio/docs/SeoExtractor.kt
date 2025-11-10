package code.yousef.portfolio.docs

class SeoExtractor(
    private val config: DocsConfig
) {
    fun build(requestPath: String, meta: MarkdownMeta): SeoMeta {
        val canonical = canonicalUrl(requestPath)
        return SeoMeta(
            title = meta.title,
            description = meta.description,
            canonicalUrl = canonical,
            ogUrl = canonical,
            tags = meta.tags
        )
    }

    fun canonical(path: String): String = canonicalUrl(path)

    private fun canonicalUrl(requestPath: String): String {
        val normalized = if (requestPath.isBlank()) "/" else requestPath
        return config.publicOriginDocs.trimEnd('/') + normalized
    }
}
