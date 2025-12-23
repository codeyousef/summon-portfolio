package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

/**
 * Configuration for landing page navbar branding.
 */
data class LandingBranding(
    val name: String,
    val logoPath: String,
    val homeUrl: String,
    val docsUrl: String,
    val apiReferenceUrl: String,
    val accentColor: String
) {
    companion object {
        fun summon(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Summon",
            logoPath = "/static/summon-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = PortfolioTheme.Colors.ACCENT_ALT
        )

        fun materia(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Materia",
            logoPath = "/static/materia-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = PortfolioTheme.Colors.LINK
        )

        fun sigil(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Sigil",
            logoPath = "/static/sigil-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = "#06b6d4" // Teal/cyan accent for Sigil
        )
    }
}

/**
 * Navbar component for landing pages with library name, docs and API reference links.
 */
@Composable
fun LandingNavbar(branding: LandingBranding) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1, BorderSide.Bottom)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
    ) {
        // Logo and name
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            Image(
                src = branding.logoPath,
                alt = branding.name,
                modifier = Modifier()
                    .width(32.px)
                    .height(32.px)
            )
            AnchorLink(
                label = branding.name,
                href = branding.homeUrl,
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.25.rem)
                    .color(branding.accentColor)
                    .textDecoration(TextDecoration.None),
                navigationMode = LinkNavigationMode.Native,
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null,
                dataAttributes = emptyMap()
            )
        }

        // Navigation links
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            LandingNavLink(label = "Documentation", href = branding.docsUrl, accentColor = branding.accentColor)
            LandingNavLink(label = "API Reference", href = branding.apiReferenceUrl, accentColor = branding.accentColor)
        }
    }
}

@Composable
private fun LandingNavLink(label: String, href: String, accentColor: String) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .textDecoration(TextDecoration.None)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .hover(
                Modifier()
                    .color(accentColor)
            ),
        navigationMode = LinkNavigationMode.Native,
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = emptyMap()
    )
}
