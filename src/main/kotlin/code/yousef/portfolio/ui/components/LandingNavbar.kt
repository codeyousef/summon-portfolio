package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable

/** Product identity and local destinations rendered beneath the global row. */
data class LandingBranding(
    val name: String,
    val logoPath: String,
    val homeUrl: String,
    val docsUrl: String,
    val apiReferenceUrl: String,
    val accentColor: String,
    val leadingNavigationItems: List<LandingNavItem> = emptyList(),
) {
    fun navigationContext(
        activeItemId: String = ContextNavigationIds.OVERVIEW,
    ): PageNavigationContext = PageNavigationContext(
        name = name,
        logoPath = logoPath,
        homeHref = homeUrl,
        accentColor = accentColor,
        activeItemId = activeItemId,
        items = buildList {
            add(LandingNavItem(ContextNavigationIds.OVERVIEW, "Overview", homeUrl).toContextItem())
            addAll(leadingNavigationItems.map(LandingNavItem::toContextItem))
            add(LandingNavItem(ContextNavigationIds.DOCUMENTATION, "Documentation", docsUrl).toContextItem())
            add(LandingNavItem(ContextNavigationIds.API_REFERENCE, "API Reference", apiReferenceUrl).toContextItem())
        },
    )

    companion object {
        fun summon(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Summon",
            logoPath = "/static/summon-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = PortfolioTheme.Colors.ACCENT_ALT,
        )

        fun materia(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Materia",
            logoPath = "/static/materia-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = PortfolioTheme.Colors.LINK,
        )

        fun sigil(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Sigil",
            logoPath = "/static/sigil-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = "#06b6d4",
        )

        fun seen(
            docsUrl: String,
            apiReferenceUrl: String,
            packagesUrl: String? = null,
            playgroundUrl: String? = null,
        ) = LandingBranding(
            name = "Seen",
            logoPath = "/static/seen-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = "#58a6ff",
            leadingNavigationItems = buildList {
                packagesUrl?.let { add(LandingNavItem(ContextNavigationIds.PACKAGES, "Packages", it)) }
                playgroundUrl?.let { add(LandingNavItem(ContextNavigationIds.PLAYGROUND, "Playground", it)) }
            },
        )

        fun aether(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Aether",
            logoPath = "/static/aether-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = "#7c3aed",
        )
    }
}

data class LandingNavItem(
    val id: String,
    val label: String,
    val href: String,
)

private fun LandingNavItem.toContextItem(): ContextNavigationItem =
    ContextNavigationItem(id = id, label = label, href = href)

/** Shared global navigation plus the product-specific context rail. */
@Composable
fun LandingNavbar(
    branding: LandingBranding,
    activeItemId: String = ContextNavigationIds.OVERVIEW,
) {
    SiteNavigation(
        locale = PortfolioLocale.EN,
        activeDestination = GlobalNavigationDestination.ECOSYSTEM,
        context = branding.navigationContext(activeItemId),
        showLocale = false,
    )
}
