package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.ssr.sigilMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.alignItems
import codes.yousef.summon.modifier.LayoutModifiers.display
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.justifyContent
import codes.yousef.summon.modifier.LayoutModifiers.left
import codes.yousef.summon.modifier.LayoutModifiers.top
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.runtime.mutableStateOf
import codes.yousef.summon.runtime.remember

@Composable
fun DesktopHeader(
    locale: PortfolioLocale,
    modifier: Modifier,
    forceNativeLinks: Boolean,
    nativeBaseUrl: String?,
    docsBaseUrl: String?,
    forcePortfolioAnchors: Boolean
) {
    val chrome = LocalPageChrome.current
    val navItems = defaultNavItems
    val docsHref = resolveDocsHref(docsBaseUrl)

    Row(
        modifier = modifier
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(PortfolioTheme.Spacing.lg)
            .flexWrap(FlexWrap.NoWrap)
    ) {
        // Logo
        Box(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
                .flex(grow = 1, shrink = 1, basis = "220px")
        ) {
            Text(
                text = "YOUSEF",
                modifier = Modifier()
                    .fontSize(0.9.rem)
                    .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                    .fontWeight(700)
            )
        }

        // Desktop Nav
        Box(
            modifier = Modifier()
                .flex(grow = 1, shrink = 1, basis = "360px")
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 1, shrink = 1, basis = "360px")
                    .flexWrap(FlexWrap.Wrap)
            ) {
                val baseNavModifier = Modifier()
                    .textDecoration(TextDecoration.None)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .visited(Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY))
                    .fontSize(0.85.rem)
                    .letterSpacing(0.08.rem)
                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .opacity(0.9F)
                val prefixOverride = if (forcePortfolioAnchors) portfolioBaseUrl().trimEnd('/') else null
                val linkMode = if (forceNativeLinks || forcePortfolioAnchors) {
                    LinkNavigationMode.Native
                } else {
                    LinkNavigationMode.Client
                }
                navItems.forEach { item ->
                    val href =
                        if (forceNativeLinks) {
                            item.target.absoluteHref(locale, nativeBaseUrl)
                        } else if (prefixOverride != null) {
                            prefixOverride + item.target.href(locale)
                        } else {
                            item.target.href(locale)
                        }
                    val label = item.label.resolve(locale)
                    navLink(
                        label = label,
                        href = href,
                        modifier = baseNavModifier,
                        dataAttributes = mapOf("nav" to label.lowercase()),
                        navigationMode = linkMode
                    )
                }
                // Projects dropdown with Summon, Materia, Sigil
                ProjectsDropdownNav(baseNavModifier = baseNavModifier)
            }
        }

        // Actions
        Box(
            modifier = Modifier()
                .flex(grow = 0, shrink = 1, basis = "240px")
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 0, shrink = 0, basis = "auto")
                    .justifyContent(JustifyContent.FlexEnd)
                    .flexWrap(FlexWrap.NoWrap)
            ) {
                if (chrome.isAdminSession) {
                    val adminHref = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"
                    navLink(
                        label = "Admin",
                        href = adminHref,
                        modifier = Modifier()
                            .textDecoration(TextDecoration.None)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .fontSize(0.85.rem)
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .borderRadius(PortfolioTheme.Radii.pill)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER),
                        dataAttributes = mapOf("nav" to "admin"),
                        navigationMode = LinkNavigationMode.Native
                    )
                    navLink(
                        label = "Logout",
                        href = "/admin/logout",
                        modifier = Modifier()
                            .textDecoration(TextDecoration.None)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .fontSize(0.85.rem)
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .borderRadius(PortfolioTheme.Radii.pill),
                        dataAttributes = mapOf("nav" to "logout"),
                        navigationMode = LinkNavigationMode.Native
                    )
                }
                val hireHref = when {
                    forceNativeLinks -> NavTarget.Section("contact").absoluteHref(locale, nativeBaseUrl)
                    forcePortfolioAnchors -> "${portfolioBaseUrl().trimEnd('/')}${NavTarget.Section("contact").href(locale)}"
                    else -> NavTarget.Section("contact").href(locale)
                }
                val hireNavigation = if (forceNativeLinks || forcePortfolioAnchors) {
                    LinkNavigationMode.Native
                } else {
                    LinkNavigationMode.Client
                }
                ButtonLink(
                    label = startProjectLabel.resolve(locale),
                    href = hireHref,
                    modifier = Modifier()
                        .display(Display.InlineFlex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.Center)
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .fontWeight(600)
                        .textDecoration(TextDecoration.None)
                        .whiteSpace(WhiteSpace.NoWrap),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = mapOf("nav" to "hire"),
                    navigationMode = hireNavigation
                )
                LocaleToggle(current = locale, forceNativeLinks = forceNativeLinks, nativeBaseUrl = nativeBaseUrl)
            }
        }
    }
}

/**
 * Projects dropdown navigation showing Summon, Materia, and Sigil libraries.
 * Uses a simple hover-based approach with relative positioning.
 */
@Composable
private fun ProjectsDropdownNav(baseNavModifier: Modifier) {
    val isOpen = remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier()
            .position(Position.Relative)
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .attribute("data-dropdown", "projects")
    ) {
        // Dropdown trigger - clicking toggles the menu
        Button(
            onClick = { isOpen.value = !isOpen.value },
            label = "Projects ▾",
            modifier = baseNavModifier
                .backgroundColor("transparent")
                .borderWidth(0)
                .cursor(Cursor.Pointer),
            variant = ButtonVariant.SECONDARY,
            disabled = false
        )
        
        // Dropdown menu - conditionally rendered
        if (isOpen.value) {
            Box(
                modifier = Modifier()
                    .position(Position.Absolute)
                    .top(100.percent)
                    .left(0.px)
                    .marginTop(4.px)
                    .backgroundColor("#0a1628")
                    .borderRadius(8.px)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .zIndex(1000)
                    .minWidth(200.px)
                    .padding(8.px)
            ) {
                Column(
                    modifier = Modifier()
                        .gap(4.px)
                ) {
                    // Summon
                    ProjectDropdownItem(
                        label = "Summon",
                        description = "Frontend Framework",
                        logoPath = "/static/summon-logo.png",
                        href = summonMarketingUrl()
                    )
                    // Materia
                    ProjectDropdownItem(
                        label = "Materia",
                        description = "3D Graphics Library",
                        logoPath = "/static/materia-logo.png",
                        href = materiaMarketingUrl()
                    )
                    // Sigil
                    ProjectDropdownItem(
                        label = "Sigil",
                        description = "Declarative 3D for Compose",
                        logoPath = "/static/sigil-logo.png",
                        href = sigilMarketingUrl()
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectDropdownItem(
    label: String,
    description: String,
    logoPath: String,
    href: String
) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .gap(10.px)
            .padding(8.px, 12.px)
            .borderRadius(6.px)
            .hover(
                Modifier()
                    .backgroundColor("rgba(255,255,255,0.05)")
            )
    ) {
        Image(
            src = logoPath,
            alt = label,
            modifier = Modifier()
                .width(24.px)
                .height(24.px)
        )
        Column(
            modifier = Modifier()
                .gap(2.px)
                .flex(grow = 1, shrink = 1, basis = "auto")
        ) {
            AnchorLink(
                label = label,
                href = href,
                modifier = Modifier()
                    .fontWeight(600)
                    .fontSize(0.9.rem)
                    .textDecoration(TextDecoration.None)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY),
                navigationMode = LinkNavigationMode.Native,
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null,
                dataAttributes = mapOf("nav" to "project-${label.lowercase()}")
            )
            Text(
                text = description,
                modifier = Modifier()
                    .fontSize(0.75.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun ProjectsDropdown(
    locale: PortfolioLocale,
    baseModifier: Modifier,
    docsHref: String,
    projectsNavigationMode: LinkNavigationMode
) {
    val open = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier()
            .position(Position.Relative)
    ) {
        Button(
            onClick = { open.value = !open.value },
            label = projectsLabel.resolve(locale),
            modifier = baseModifier,
            variant = ButtonVariant.SECONDARY,
            disabled = false
        )
        if (open.value) {
            Box(
                modifier = Modifier()
                    .position(Position.Absolute)
            ) {
                AnchorLink(
                    label = LocalizedText("Portfolio Projects", "المشاريع").resolve(locale),
                    href = NavTarget.Page("/projects").href(locale),
                    modifier = baseModifier
                        .display(Display.Block)
                        .whiteSpace(WhiteSpace.NoWrap),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = mapOf("nav" to "projects-portfolio"),
                    navigationMode = projectsNavigationMode
                )
                AnchorLink(
                    label = LocalizedText("Summon", "Summon").resolve(locale),
                    href = docsHref,
                    modifier = baseModifier
                        .display(Display.Block)
                        .whiteSpace(WhiteSpace.NoWrap),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = mapOf("nav" to "projects-summon"),
                    navigationMode = LinkNavigationMode.Native
                )
            }
        }
    }
}
