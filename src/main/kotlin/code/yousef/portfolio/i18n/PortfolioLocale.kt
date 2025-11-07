package code.yousef.portfolio.i18n

enum class PortfolioLocale(val code: String, val direction: String) {
    EN("en", "ltr"),
    AR("ar", "rtl");

    companion object {
        fun fromCode(code: String?): PortfolioLocale =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

fun PortfolioLocale.pathPrefix(): String =
    if (this == PortfolioLocale.EN) "" else "/${this.code}"

data class LocalizedText(
    val en: String,
    val ar: String? = null
) {
    fun resolve(locale: PortfolioLocale): String =
        when (locale) {
            PortfolioLocale.EN -> en
            PortfolioLocale.AR -> ar ?: en
        }
}
