package code.yousef.portfolio.content.store

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.HeroMetric
import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.PhotographySourceKind
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.i18n.LocalizedText
import java.time.Instant
import java.time.LocalDate

/**
 * The document representation of Portfolio content, shared by every document-database
 * backend so Firestore and DynamoDB store byte-for-byte identical business data.
 */
object PortfolioContentDocuments {
    const val PROJECTS = "projects"
    const val SERVICES = "services"
    const val BLOG_POSTS = "blog_posts"
    const val TESTIMONIALS = "testimonials"
    const val HERO = "hero"
    const val HERO_DOCUMENT_ID = "main"
    const val CONTACT_SUBMISSIONS = "contact_submissions"
    const val PHOTOGRAPHY_PHOTOS = "photography_photos"

    fun project(id: String, data: Map<String, Any?>): Project? = try {
        Project(
            id = id,
            slug = data["slug"] as? String ?: return null,
            layerLabel = data.localizedText("layerLabel") ?: return null,
            layerName = data.localizedText("layerName") ?: return null,
            title = data.localizedText("title") ?: return null,
            description = data.localizedText("description") ?: return null,
            category = (data["category"] as? String)?.let {
                runCatching { ProjectCategory.valueOf(it) }.getOrNull()
            } ?: ProjectCategory.WEB,
            featured = data["featured"] as? Boolean ?: false,
            order = (data["order"] as? Number)?.toInt() ?: 0,
            technologies = (data["technologies"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            imageUrl = data["imageUrl"] as? String,
            githubUrl = data["githubUrl"] as? String,
        )
    } catch (_: Exception) {
        null
    }

    fun service(id: String, data: Map<String, Any?>): Service? = try {
        Service(
            id = id,
            title = data.localizedText("title") ?: return null,
            description = data.localizedText("description") ?: return null,
            featured = data["featured"] as? Boolean ?: false,
            order = (data["order"] as? Number)?.toInt() ?: 0,
        )
    } catch (_: Exception) {
        null
    }

    fun blogPost(id: String, data: Map<String, Any?>): BlogPost? = try {
        BlogPost(
            id = id,
            slug = data["slug"] as? String ?: return null,
            title = data["title"] as? String ?: return null,
            excerpt = data["excerpt"] as? String ?: "",
            content = data["content"] as? String ?: "",
            publishedAt = (data["publishedAt"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            featured = data["featured"] as? Boolean ?: false,
            author = data["author"] as? String ?: "Unknown",
            tags = (data["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        )
    } catch (_: Exception) {
        null
    }

    fun testimonial(id: String, data: Map<String, Any?>): Testimonial? = try {
        Testimonial(
            id = id,
            quote = data.localizedText("quote") ?: return null,
            author = data["author"] as? String ?: return null,
            role = data.localizedText("role") ?: return null,
            company = data.localizedText("company") ?: return null,
            featured = data["featured"] as? Boolean ?: false,
            order = (data["order"] as? Number)?.toInt() ?: 0,
        )
    } catch (_: Exception) {
        null
    }

    fun hero(data: Map<String, Any?>): HeroContent? = try {
        HeroContent(
            eyebrow = data.localizedText("eyebrow") ?: return null,
            titlePrimary = data.localizedText("titlePrimary") ?: return null,
            titleSecondary = data.localizedText("titleSecondary") ?: return null,
            subtitle = data.localizedText("subtitle") ?: return null,
            ctaPrimary = data.localizedText("ctaPrimary") ?: return null,
            ctaSecondary = data.localizedText("ctaSecondary") ?: return null,
            metrics = (data["metrics"] as? List<*>)?.mapNotNull { it.heroMetric() } ?: emptyList(),
        )
    } catch (_: Exception) {
        null
    }

    fun contactSubmission(id: String, data: Map<String, Any?>): ContactSubmission? = try {
        ContactSubmission(
            id = id,
            contact = data["contact"] as? String ?: return null,
            message = data["message"] as? String ?: "",
            createdAt = (data["createdAt"] as? String)?.let { Instant.parse(it) } ?: Instant.now(),
        )
    } catch (_: Exception) {
        null
    }

    fun photographyPhoto(id: String, data: Map<String, Any?>): PhotographyPhoto? = try {
        PhotographyPhoto(
            id = id,
            title = data["title"] as? String ?: return null,
            altText = data["altText"] as? String ?: return null,
            caption = data["caption"] as? String,
            takenAt = (data["takenAt"] as? String)?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            order = (data["order"] as? Number)?.toInt() ?: 0,
            published = data["published"] as? Boolean ?: false,
            storageKey = data["storageKey"] as? String ?: return null,
            contentType = data["contentType"] as? String ?: "application/octet-stream",
            originalFilename = data["originalFilename"] as? String,
            sizeBytes = (data["sizeBytes"] as? Number)?.toLong() ?: 0L,
            mediaType = (data["mediaType"] as? String)?.let {
                runCatching { PhotographyMediaType.valueOf(it) }.getOrNull()
            } ?: PhotographyMediaType.PHOTO,
            sourceKind = (data["sourceKind"] as? String)?.let {
                runCatching { PhotographySourceKind.valueOf(it) }.getOrNull()
            } ?: PhotographySourceKind.UPLOAD,
            category = data["category"] as? String ?: "Uncategorized",
            albumTitle = data["albumTitle"] as? String,
            externalUrl = data["externalUrl"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            featured = data["featured"] as? Boolean ?: false,
            uploadedAt = (data["uploadedAt"] as? String)?.let { Instant.parse(it) } ?: Instant.now(),
        )
    } catch (_: Exception) {
        null
    }

    fun Project.toDocument(): Map<String, Any?> = mapOf(
        "slug" to slug,
        "layerLabel" to layerLabel.toDocument(),
        "layerName" to layerName.toDocument(),
        "title" to title.toDocument(),
        "description" to description.toDocument(),
        "category" to category.name,
        "featured" to featured,
        "order" to order,
        "technologies" to technologies,
        "imageUrl" to imageUrl,
        "githubUrl" to githubUrl,
    )

    fun Service.toDocument(): Map<String, Any?> = mapOf(
        "title" to title.toDocument(),
        "description" to description.toDocument(),
        "featured" to featured,
        "order" to order,
    )

    fun BlogPost.toDocument(): Map<String, Any?> = mapOf(
        "slug" to slug,
        "title" to title,
        "excerpt" to excerpt,
        "content" to content,
        "publishedAt" to publishedAt.toString(),
        "featured" to featured,
        "author" to author,
        "tags" to tags,
    )

    fun Testimonial.toDocument(): Map<String, Any?> = mapOf(
        "quote" to quote.toDocument(),
        "author" to author,
        "role" to role.toDocument(),
        "company" to company.toDocument(),
        "featured" to featured,
        "order" to order,
    )

    fun HeroContent.toDocument(): Map<String, Any?> = mapOf(
        "eyebrow" to eyebrow.toDocument(),
        "titlePrimary" to titlePrimary.toDocument(),
        "titleSecondary" to titleSecondary.toDocument(),
        "subtitle" to subtitle.toDocument(),
        "ctaPrimary" to ctaPrimary.toDocument(),
        "ctaSecondary" to ctaSecondary.toDocument(),
        "metrics" to metrics.map { it.toDocument() },
    )

    fun ContactSubmission.toDocument(): Map<String, Any?> = mapOf(
        "contact" to contact,
        "message" to message,
        "createdAt" to createdAt.toString(),
    )

    fun PhotographyPhoto.toDocument(): Map<String, Any?> = mapOf(
        "title" to title,
        "altText" to altText,
        "caption" to caption,
        "takenAt" to takenAt?.toString(),
        "order" to order,
        "published" to published,
        "storageKey" to storageKey,
        "contentType" to contentType,
        "originalFilename" to originalFilename,
        "sizeBytes" to sizeBytes,
        "mediaType" to mediaType.name,
        "sourceKind" to sourceKind.name,
        "category" to category,
        "albumTitle" to albumTitle,
        "externalUrl" to externalUrl,
        "thumbnailUrl" to thumbnailUrl,
        "featured" to featured,
        "uploadedAt" to uploadedAt.toString(),
    )

    private fun HeroMetric.toDocument(): Map<String, Any?> = mapOf(
        "value" to value,
        "label" to label.toDocument(),
        "detail" to detail.toDocument(),
    )

    private fun LocalizedText.toDocument(): Map<String, Any?> = mapOf(
        "en" to en,
        "ar" to ar,
    )

    private fun Map<String, Any?>.localizedText(key: String): LocalizedText? = when (val value = this[key]) {
        is Map<*, *> -> LocalizedText(en = value["en"] as? String ?: "", ar = value["ar"] as? String)
        is String -> LocalizedText(en = value, ar = null)
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.heroMetric(): HeroMetric? {
        val map = this as? Map<String, Any?> ?: return null
        return try {
            HeroMetric(
                value = map["value"] as? String ?: return null,
                label = map.localizedText("label") ?: return null,
                detail = map.localizedText("detail") ?: return null,
            )
        } catch (_: Exception) {
            null
        }
    }
}
