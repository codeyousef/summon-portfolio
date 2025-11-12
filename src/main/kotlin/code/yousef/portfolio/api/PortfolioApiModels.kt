package code.yousef.portfolio.api

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val slug: String,
    val layerLabel: String,
    val layerName: String,
    val title: String,
    val description: String,
    val category: String,
    val featured: Boolean,
    val order: Int,
    val technologies: List<String>
)

@Serializable
data class ServiceDto(
    val id: String,
    val title: String,
    val description: String,
    val featured: Boolean,
    val order: Int
)

@Serializable
data class BlogPostDto(
    val id: String,
    val slug: String,
    val title: String,
    val excerpt: String,
    val content: String,
    val publishedAt: String,
    val featured: Boolean,
    val author: String,
    val tags: List<String>
)

@Serializable
data class ContactSubmissionDto(
    val id: String,
    val name: String,
    val email: String?,
    val whatsapp: String?,
    val requirements: String,
    val createdAt: String
)

fun Project.toDto(locale: PortfolioLocale): ProjectDto =
    ProjectDto(
        id = id,
        slug = slug,
        layerLabel = layerLabel.resolve(locale),
        layerName = layerName.resolve(locale),
        title = title.resolve(locale),
        description = description.resolve(locale),
        category = category.label.resolve(locale),
        featured = featured,
        order = order,
        technologies = technologies
    )

fun Service.toDto(locale: PortfolioLocale): ServiceDto =
    ServiceDto(
        id = id,
        title = title.resolve(locale),
        description = description.resolve(locale),
        featured = featured,
        order = order
    )

fun BlogPost.toDto(locale: PortfolioLocale): BlogPostDto =
    BlogPostDto(
        id = id,
        slug = slug,
        title = title.resolve(locale),
        excerpt = excerpt.resolve(locale),
        content = content.resolve(locale),
        publishedAt = publishedAt.toString(),
        featured = featured,
        author = author,
        tags = tags
    )

fun ContactSubmission.toDto(): ContactSubmissionDto =
    ContactSubmissionDto(
        id = id,
        name = name,
        email = email,
        whatsapp = whatsapp,
        requirements = requirements,
        createdAt = createdAt.toString()
    )
