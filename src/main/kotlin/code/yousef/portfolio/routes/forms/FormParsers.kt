package code.yousef.portfolio.routes.forms

import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.i18n.LocalizedText
import io.ktor.http.Parameters
import java.time.LocalDate
import java.util.UUID

fun Parameters.toProject(): Project? {
    val slug = this["slug"]?.trim().orEmpty()
    if (slug.isBlank()) return null
    val id = this["id"].orEmpty().ifBlank { slug }
    val layerLabel = localizedText("layerLabel") ?: return null
    val layerName = localizedText("layerName") ?: return null
    val title = localizedText("title") ?: return null
    val description = localizedText("description") ?: return null
    val category = this["category"]
        ?.takeIf { it.isNotBlank() }
        ?.runCatching { ProjectCategory.valueOf(this.uppercase()) }
        ?.getOrNull()
        ?: ProjectCategory.WEB
    val featured = this["featured"].isOn()
    val order = this["order"].orZero()
    val technologies = this["technologies"]
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        ?: emptyList()
    return Project(
        id = id,
        slug = slug,
        layerLabel = layerLabel,
        layerName = layerName,
        title = title,
        description = description,
        category = category,
        featured = featured,
        order = order,
        technologies = technologies
    )
}

fun Parameters.toService(): Service? {
    val id = this["id"].orEmpty().ifBlank { UUID.randomUUID().toString() }
    val title = localizedText("title") ?: return null
    val description = localizedText("description") ?: return null
    val featured = this["featured"].isOn()
    val order = this["order"].orZero()
    return Service(
        id = id,
        title = title,
        description = description,
        featured = featured,
        order = order
    )
}

fun Parameters.toBlogPost(): BlogPost? {
    val slug = this["slug"]?.trim().orEmpty()
    if (slug.isBlank()) return null
    val id = this["id"].orEmpty().ifBlank { slug }
    val title = localizedText("title") ?: return null
    val excerpt = localizedText("excerpt") ?: return null
    val content = localizedText("content") ?: return null
    val dateString = this["published_at"]?.trim().orEmpty()
    val publishedAt = runCatching { LocalDate.parse(dateString) }.getOrNull() ?: return null
    val featured = this["featured"].isOn()
    val author = this["author"]?.trim().orEmpty()
    if (author.isBlank()) return null
    val tags = this["tags"]
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        ?: emptyList()
    return BlogPost(
        id = id,
        slug = slug,
        title = title,
        excerpt = excerpt,
        content = content,
        publishedAt = publishedAt,
        featured = featured,
        author = author,
        tags = tags
    )
}

fun Parameters.toTestimonial(): Testimonial? {
    val id = this["id"].orEmpty().ifBlank { UUID.randomUUID().toString() }
    val quote = localizedText("quote") ?: return null
    val author = this["author"]?.trim().orEmpty()
    if (author.isBlank()) return null
    val role = localizedText("role") ?: return null
    val company = localizedText("company") ?: return null
    val featured = this["featured"].isOn()
    val order = this["order"].orZero()
    return Testimonial(
        id = id,
        quote = quote,
        author = author,
        role = role,
        company = company,
        featured = featured,
        order = order
    )
}

private fun Parameters.localizedText(prefix: String): LocalizedText? {
    val en = this["${prefix}_en"]?.trim().orEmpty()
    if (en.isBlank()) return null
    val ar = this["${prefix}_ar"]?.trim().takeIf { !it.isNullOrEmpty() }
    return LocalizedText(en = en, ar = ar)
}

private fun String?.isOn(): Boolean = this == "on"

private fun String?.orZero(): Int = this?.toIntOrNull() ?: 0
