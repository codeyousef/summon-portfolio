package code.yousef.portfolio.docs.summon

internal fun htmlEscape(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

internal fun safeHref(raw: String): String = htmlEscape(raw.ifBlank { "/" })

internal fun safeFragmentHref(anchor: String): String {
    val normalized = anchor.trim().removePrefix("#")
    return htmlEscape("#${normalized}")
}

internal fun dataAttributes(attributes: Map<String, String>): String {
    if (attributes.isEmpty()) return ""
    return buildString(attributes.size * 16) {
        attributes.forEach { (key, rawValue) ->
            val normalizedKey = key.lowercase().replace(Regex("[^a-z0-9-]"), "-")
            append(' ')
            append("data-")
            append(normalizedKey)
            append('=')
            append('"')
            append(htmlEscape(rawValue))
            append('"')
        }
    }
}
