package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.HamburgerMenu
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.alignItems
import codes.yousef.summon.modifier.LayoutModifiers.display
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.justifyContent
import codes.yousef.summon.modifier.LayoutModifiers.top
import codes.yousef.summon.modifier.ModifierExtras.onClick
import codes.yousef.summon.modifier.StylingModifiers.fontWeight

@Composable
fun MobileHeader(
    locale: PortfolioLocale,
    modifier: Modifier,
    forceNativeLinks: Boolean,
    nativeBaseUrl: String?,
    docsBaseUrl: String?,
    forcePortfolioAnchors: Boolean,
) {
    val chrome = LocalPageChrome.current
    val navItems = defaultNavItems
    val docsHref = resolveDocsHref(docsBaseUrl)

    // Style the hamburger menu dropdown container
    GlobalStyle(css = """
        [id^="hamburger-menu-"] {
            position: absolute !important;
            top: 100% !important;
            left: 0 !important;
            margin-top: 8px !important;
            background: #0a1628 !important;
            background-color: #0a1628 !important;
            border-radius: 8px !important;
            border: 1px solid ${PortfolioTheme.Colors.BORDER} !important;
            z-index: 1000 !important;
            min-width: 280px !important;
            width: max-content !important;
        }
    """)

    // Pre-calculate hire link props
    val hireHref =
        when {
            forceNativeLinks -> NavTarget.Section("contact").absoluteHref(locale, nativeBaseUrl)
            forcePortfolioAnchors -> "${portfolioBaseUrl().trimEnd('/')}${NavTarget.Section("contact").href(locale)}"
            else -> NavTarget.Section("contact").href(locale)
        }
    val hireNavigation =
        if (forceNativeLinks || forcePortfolioAnchors) {
            LinkNavigationMode.Native
        } else {
            LinkNavigationMode.Client
        }

    Column(
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier()
                    .width(100.percent)
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .gap(PortfolioTheme.Spacing.lg)
                    .flexWrap(FlexWrap.Wrap), // Allow wrapping so menu content can break to new line if needed
        ) {
            // Hamburger Menu (on the left)
            HamburgerMenu(
                modifier =
                    Modifier()
                        .position(Position.Relative)
                        .width("40px")
                        .height("40px")
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.Center)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .zIndex(100),
                menuContent = {
                    Column(
                        modifier =
                            Modifier()
                                .width(100.percent)
                                .padding("16px")
                                .gap("12px")
                                .minWidth("200px"),
                    ) {
                        val baseNavModifier =
                            Modifier()
                                .display(Display.Block)
                                .textDecoration(TextDecoration.None)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .visited(Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY))
                                .fontSize(1.rem)
                                .padding(PortfolioTheme.Spacing.sm)
                                .borderRadius(PortfolioTheme.Radii.md)
                                .whiteSpace(WhiteSpace.NoWrap)
                        val prefixOverride = if (forcePortfolioAnchors) portfolioBaseUrl().trimEnd('/') else null
                        val linkMode =
                            if (forceNativeLinks || forcePortfolioAnchors) LinkNavigationMode.Native else LinkNavigationMode.Client

                        navItems.forEach { item ->
                            val href =
                                if (forceNativeLinks) {
                                    item.target.absoluteHref(locale, nativeBaseUrl)
                                } else if (prefixOverride != null) {
                                    prefixOverride + item.target.href(locale)
                                } else {
                                    item.target.href(locale)
                                }
                            navLink(
                                label = item.label.resolve(locale),
                                href = href,
                                modifier = baseNavModifier,
                                dataAttributes = mapOf("nav" to item.label.resolve(locale).lowercase()),
                                navigationMode = linkMode,
                            )
                        }
                        AnchorLink(
                            label = "Summon",
                            href = docsHref,
                            modifier = baseNavModifier,
                            target = null,
                            rel = null,
                            title = null,
                            id = null,
                            ariaLabel = null,
                            ariaDescribedBy = null,
                            dataHref = null,
                            dataAttributes = mapOf("nav" to "summon"),
                            navigationMode = LinkNavigationMode.Native,
                        )

                        if (chrome.isAdminSession) {
                            val adminHref = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"
                            navLink(
                                label = "Admin",
                                href = adminHref,
                                modifier = baseNavModifier,
                                dataAttributes = mapOf("nav" to "admin"),
                                navigationMode = LinkNavigationMode.Native,
                            )
                            navLink(
                                label = "Logout",
                                href = "/admin/logout",
                                modifier = baseNavModifier,
                                dataAttributes = mapOf("nav" to "logout"),
                                navigationMode = LinkNavigationMode.Native,
                            )
                        }

                        // Moved Actions
                        Box(
                            modifier =
                                Modifier()
                                    .height(
                                        "1px",
                                    ).backgroundColor(
                                        PortfolioTheme.Colors.BORDER,
                                    ).width(100.percent)
                                    .marginTop(PortfolioTheme.Spacing.sm)
                                    .marginBottom(PortfolioTheme.Spacing.sm),
                        ) {
                        }

                        ButtonLink(
                            label = startProjectLabel.resolve(locale),
                            href = hireHref,
                            modifier =
                                Modifier()
                                    .display(Display.Flex)
                                    .width(100.percent)
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
                            navigationMode = hireNavigation,
                        )

                        Box(
                            modifier =
                                Modifier()
                                    .display(
                                        Display.Flex,
                                    ).justifyContent(JustifyContent.Center)
                                    .paddingTop(PortfolioTheme.Spacing.sm),
                        ) {
                            LocaleToggle(current = locale, forceNativeLinks = forceNativeLinks, nativeBaseUrl = nativeBaseUrl)
                        }
                    }
                },
            )

            // Logo
            Box(
                modifier =
                    Modifier()
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .gap(PortfolioTheme.Spacing.md)
                        .flex(grow = 1, shrink = 1, basis = "auto"),
            ) {
                Text(
                    text = "YOUSEF",
                    modifier =
                        Modifier()
                            .fontSize(0.9.rem)
                            .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                            .fontWeight(700),
                )
            }
        }
    }
}
