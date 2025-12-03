package code.yousef.portfolio.docs

import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl

/**
 * Branding configuration for documentation sites.
 * 
 * @param homeUrlProvider A function that returns the home URL, allowing dynamic resolution
 *                        based on the current environment (dev/prod).
 */
data class DocsBranding(
    val name: String,
    val homeUrlProvider: () -> String,
    val docsTitle: String,
    val accentColor: String,
    val logoPath: String
) {
    /** Resolves the home URL dynamically based on current environment context. */
    val homeUrl: String get() = homeUrlProvider()
    
    companion object {
        fun summon(homeUrlProvider: () -> String = ::summonMarketingUrl) = DocsBranding(
            name = "Summon",
            homeUrlProvider = homeUrlProvider,
            docsTitle = "Summon Docs",
            accentColor = "#ff89b0", // ACCENT_ALT
            logoPath = "/static/summon-logo.png"
        )

        fun materia(homeUrlProvider: () -> String = ::materiaMarketingUrl) = DocsBranding(
            name = "Materia",
            homeUrlProvider = homeUrlProvider,
            docsTitle = "Materia Docs",
            accentColor = "#6ad7ff", // LINK_HOVER - a nice cyan that's visible
            logoPath = "/static/materia-logo.png"
        )
    }
}
