package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText

data class Service(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val featured: Boolean = false,
    val order: Int = 0
)
