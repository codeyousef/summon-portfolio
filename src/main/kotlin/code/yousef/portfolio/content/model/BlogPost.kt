package code.yousef.portfolio.content.model

import code.yousef.portfolio.serialization.LocalDateIsoSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class BlogPost(
    val id: String,
    val slug: String,
    val title: String,
    val excerpt: String,
    val content: String,
    @Serializable(with = LocalDateIsoSerializer::class)
    val publishedAt: LocalDate,
    val featured: Boolean = false,
    val author: String,
    val tags: List<String> = emptyList()
)
