package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText
import java.time.LocalDate

data class BlogPost(
    val id: String,
    val slug: String,
    val title: LocalizedText,
    val excerpt: LocalizedText,
    val content: LocalizedText,
    val publishedAt: LocalDate,
    val featured: Boolean = false,
    val author: String,
    val tags: List<String> = emptyList()
)
