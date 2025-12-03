package code.yousef.portfolio.docs

/**
 * Branding configuration for documentation sites.
 */
data class DocsBranding(
    val name: String,
    val homeUrl: String,
    val docsTitle: String,
    val accentColor: String,
    val logoPath: String
) {
    companion object {
        fun summon(homeUrl: String) = DocsBranding(
            name = "Summon",
            homeUrl = homeUrl,
            docsTitle = "Summon Docs",
            accentColor = "#ff89b0", // ACCENT_ALT
            logoPath = "/static/summon-logo.png"
        )

        fun materia(homeUrl: String) = DocsBranding(
            name = "Materia",
            homeUrl = homeUrl,
            docsTitle = "Materia Docs",
            accentColor = "#6ad7ff", // LINK_HOVER - a nice cyan that's visible
            logoPath = "/static/materia-logo.png"
        )
    }
}
