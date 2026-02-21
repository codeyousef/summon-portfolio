package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.NavigationStrings
import code.yousef.portfolio.ssr.aetherMarketingUrl
import code.yousef.portfolio.ssr.blogUrl
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.sigilMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Dropdown
import codes.yousef.summon.components.navigation.DropdownTrigger
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

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

        // Center Nav: The Ecosystem (Summon, Sigil, Materia)
        Box(
            modifier = Modifier()
                .flex(grow = 1, shrink = 1, basis = "400px")
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .gap(PortfolioTheme.Spacing.lg)
                    .flex(grow = 1, shrink = 1, basis = "400px")
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
                    .hover(Modifier().opacity(1.0F).backgroundColor(PortfolioTheme.Colors.SURFACE))
                
                // Ecosystem links
                EcosystemNavLink(
                    logoSrc = "/static/summon-logo.png",
                    label = "Summon",
                    href = summonMarketingUrl(),
                    modifier = baseNavModifier
                )
                EcosystemNavLink(
                    logoSrc = "/static/sigil-logo.png",
                    label = "Sigil",
                    href = sigilMarketingUrl(),
                    modifier = baseNavModifier
                )
                EcosystemNavLink(
                    logoSrc = "/static/materia-logo.png",
                    label = "Materia",
                    href = materiaMarketingUrl(),
                    modifier = baseNavModifier
                )
                EcosystemNavLink(
                    logoSrc = "/static/aether-logo.png",
                    label = "Aether",
                    href = aetherMarketingUrl(),
                    modifier = baseNavModifier
                )
            }
        }

        // Right Nav: Blog + Work With Me dropdown
        Box(
            modifier = Modifier()
                .flex(grow = 0, shrink = 1, basis = "320px")
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
                val baseNavModifier = Modifier()
                    .textDecoration(TextDecoration.None)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .visited(Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY))
                    .fontSize(0.85.rem)
                    .letterSpacing(0.08.rem)
                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .opacity(0.9F)
                    .hover(Modifier().opacity(1.0F).backgroundColor(PortfolioTheme.Colors.SURFACE))
                
                // Blog link - always use environment-aware URL for cross-site navigation
                val blogHref = blogUrl()
                navLink(
                    label = NavigationStrings.blog.resolve(locale),
                    href = blogHref,
                    modifier = baseNavModifier,
                    dataAttributes = mapOf("nav" to "blog"),
                    navigationMode = LinkNavigationMode.Native
                )

                navLink(
                    label = NavigationStrings.experiments.resolve(locale),
                    href = "/experiments",
                    modifier = baseNavModifier,
                    dataAttributes = mapOf("nav" to "experiments"),
                    navigationMode = LinkNavigationMode.Native
                )

                if (chrome.isAdminSession) {
                    val adminHref = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"
                    navLink(
                        label = NavigationStrings.admin.resolve(locale),
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
                }
                
                // Work With Me dropdown
                WorkWithMeDropdown(baseNavModifier = baseNavModifier, locale = locale, forceNativeLinks = forceNativeLinks, nativeBaseUrl = nativeBaseUrl)
                
                LocaleToggle(current = locale, forceNativeLinks = forceNativeLinks, nativeBaseUrl = nativeBaseUrl)
            }
        }
    }
}

/**
 * Ecosystem navigation link with logo and label.
 */
@Composable
private fun EcosystemNavLink(
    logoSrc: String,
    label: String,
    href: String,
    modifier: Modifier
) {
    Link(
        href = href,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .gap(6.px),
        target = "_blank"
    ) {
        Image(
            src = logoSrc,
            alt = "$label logo",
            modifier = Modifier()
                .width(18.px)
                .height(18.px)
                .style("object-fit", "contain")
        )
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(0.85.rem)
                .fontWeight(500)
        )
    }
}

/**
 * Work With Me dropdown with Full-Time and Consulting options.
 */
@Composable
private fun WorkWithMeDropdown(
    baseNavModifier: Modifier,
    locale: PortfolioLocale,
    forceNativeLinks: Boolean,
    nativeBaseUrl: String?
) {
    Dropdown(
        trigger = {
            Row(
                modifier = Modifier()
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .gap(6.px)
                    .cursor(Cursor.Pointer)
                    .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .backgroundColor(PortfolioTheme.Colors.ACCENT)
                    .color("#ffffff")
                    .fontWeight(600)
            ) {
                Text(text = NavigationStrings.workWithMe.resolve(locale), modifier = Modifier().whiteSpace(WhiteSpace.NoWrap))
                Text(text = "▼", modifier = Modifier().fontSize(0.6.rem).opacity(0.8F))
            }
        },
        modifier = Modifier(),
        triggerBehavior = DropdownTrigger.CLICK,
        closeOnItemClick = true
    ) {
        Column(
            modifier = Modifier()
                .backgroundColor(PortfolioTheme.Colors.SURFACE)
                .borderRadius(PortfolioTheme.Radii.md)
                .border("1px", "solid", PortfolioTheme.Colors.BORDER)
                .overflow(Overflow.Hidden)
                .width(100.percent)
        ) {
            WorkWithMeDropdownLink(
                label = NavigationStrings.fullTime.resolve(locale),
                href = "/full-time"
            )
            WorkWithMeDropdownLink(
                label = NavigationStrings.consulting.resolve(locale),
                href = "/services"
            )
        }
    }
}

/**
 * A dropdown item for Work With Me menu.
 */
@Composable
private fun WorkWithMeDropdownLink(
    label: String,
    href: String
) {
    Link(
        href = href,
        modifier = Modifier()
            .display(Display.Block)
            .padding(12.px, 20.px)
            .textDecoration(TextDecoration.None)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .style("border-bottom", "1px solid ${PortfolioTheme.Colors.BORDER}")
            .hover(
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            )
            .whiteSpace(WhiteSpace.NoWrap),
        target = null
    ) {
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(0.9.rem)
                .fontWeight(500)
        )
    }
}

/**
 * Projects navigation dropdown showing Summon, Materia, and Sigil libraries.
 * @deprecated Use EcosystemNavLink instead for the new nav structure
 */
@Composable
private fun ProjectsDropdownNav(baseNavModifier: Modifier) {
    Dropdown(
        trigger = {
            Row(
                modifier = Modifier()
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .gap(4.px)
                    .cursor(Cursor.Pointer)
            ) {
                Text(text = "Projects", modifier = baseNavModifier)
                Text(text = "▼", modifier = Modifier().fontSize(0.6.rem).opacity(0.7F))
            }
        },
        modifier = Modifier(),
        triggerBehavior = DropdownTrigger.CLICK,
        closeOnItemClick = true
    ) {
        ProjectDropdownLink(
            logoSrc = "/static/summon-logo.png",
            label = "Summon",
            href = summonMarketingUrl()
        )
        ProjectDropdownLink(
            logoSrc = "/static/materia-logo.png",
            label = "Materia",
            href = materiaMarketingUrl()
        )
        ProjectDropdownLink(
            logoSrc = "/static/sigil-logo.png",
            label = "Sigil",
            href = sigilMarketingUrl()
        )
        ProjectDropdownLink(
            logoSrc = "/static/aether-logo.png",
            label = "Aether",
            href = aetherMarketingUrl()
        )
    }
}

/**
 * A dropdown item with a project logo and label.
 */
@Composable
private fun ProjectDropdownLink(
    logoSrc: String,
    label: String,
    href: String
) {
    Link(
        href = href,
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .gap(10.px)
            .padding(8.px, 16.px)
            .textDecoration(TextDecoration.None)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .style("border-bottom", "1px solid ${PortfolioTheme.Colors.BORDER}")
            .hover(
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            ),
        target = "_blank"
    ) {
        Image(
            src = logoSrc,
            alt = "$label logo",
            modifier = Modifier()
                .width(20.px)
                .height(20.px)
                .style("object-fit", "contain")
        )
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(0.9.rem)
                .fontWeight(500)
        )
    }
}
