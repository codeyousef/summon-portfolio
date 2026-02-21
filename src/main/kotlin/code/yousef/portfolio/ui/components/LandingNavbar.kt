package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.HamburgerMenu
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
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

        fun aether(docsUrl: String, apiReferenceUrl: String) = LandingBranding(
            name = "Aether",
            logoPath = "/static/aether-logo.png",
            homeUrl = "/",
            docsUrl = docsUrl,
            apiReferenceUrl = apiReferenceUrl,
            accentColor = "#7c3aed" // Violet/purple accent for Aether
        )
    }
}

/**
 * Navbar component for landing pages with library name, docs and API reference links.
 */
@Composable
fun LandingNavbar(branding: LandingBranding) {
    // CSS for responsive visibility
    GlobalStyle("""
        .landing-nav-desktop { display: block !important; }
        .landing-nav-mobile { display: none !important; }
        
        @media (max-width: 960px) {
            .landing-nav-desktop { display: none !important; }
            .landing-nav-mobile { display: block !important; }
        }
    """)

    Box(modifier = Modifier().width(100.percent)) {
        // Desktop Version
        Box(modifier = Modifier().className("landing-nav-desktop").width(100.percent)) {
            DesktopLandingNavbar(branding)
        }
        
        // Mobile Version
        Box(modifier = Modifier().className("landing-nav-mobile").width(100.percent)) {
            MobileLandingNavbar(branding)
        }
    }
}

@Composable
private fun DesktopLandingNavbar(branding: LandingBranding) {
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
private fun MobileLandingNavbar(branding: LandingBranding) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1, BorderSide.Bottom)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
    ) {
        // Hamburger Menu (Left)
        HamburgerMenu(
            modifier = Modifier()
                .position(Position.Relative)
                .width(40.px)
                .height(40.px)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .zIndex(100),
            menuContainerModifier = Modifier()
                .position(Position.Absolute)
                .top(100.percent)
                .left(0.px)
                .marginTop(8.px)
                .backgroundColor("#0a1628")
                .borderRadius(8.px)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .zIndex(1000)
                .minWidth(200.px)
                .width("max-content"),
            menuContent = {
                Column(
                    modifier = Modifier()
                        .width(100.percent)
                        .padding(16.px)
                        .gap(12.px)
                        .backgroundColor("#0a1628")
                ) {
                    LandingNavLink(label = "Documentation", href = branding.docsUrl, accentColor = branding.accentColor)
                    LandingNavLink(label = "API Reference", href = branding.apiReferenceUrl, accentColor = branding.accentColor)
                }
            }
        )

        // Logo and name (Right/Center)
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
                    .width(28.px)
                    .height(28.px)
            )
            AnchorLink(
                label = branding.name,
                href = branding.homeUrl,
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.1.rem)
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
