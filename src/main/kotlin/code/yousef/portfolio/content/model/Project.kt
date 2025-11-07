package code.yousef.portfolio.content.model

import code.yousef.portfolio.i18n.LocalizedText

enum class ProjectCategory(val label: LocalizedText) {
    WEB(LocalizedText("Web", "الويب")),
    MOBILE(LocalizedText("Mobile", "تطبيقات الهاتف")),
    GAME(LocalizedText("Game Dev", "تطوير الألعاب"));
}

data class Project(
    val id: String,
    val slug: String,
    val layerLabel: LocalizedText,
    val layerName: LocalizedText,
    val title: LocalizedText,
    val description: LocalizedText,
    val category: ProjectCategory,
    val featured: Boolean = false,
    val order: Int = 0,
    val technologies: List<String> = emptyList()
)
