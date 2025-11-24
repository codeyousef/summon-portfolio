package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ssr.docsBaseUrl
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.display.MaterialIcon
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.ResponsiveLayout
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.layout.ScreenSize
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.HamburgerMenu
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.alignItems
import codes.yousef.summon.modifier.LayoutModifiers.display
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.justifyContent
import codes.yousef.summon.modifier.LayoutModifiers.positionInset
import codes.yousef.summon.modifier.LayoutModifiers.top
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.ModifierExtras.onClick
import codes.yousef.summon.runtime.remember
import codes.yousef.summon.runtime.mutableStateOf

private val projectsLabel = LocalizedText("Projects", "المشاريع")
private val startProjectLabel = LocalizedText("Start your project", "ابدأ مشروعك")

@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    forceNativeLinks: Boolean = false,
    nativeBaseUrl: String? = null,
    docsBaseUrl: String? = null,
    forcePortfolioAnchors: Boolean = false
) {
    val paddingStart = if (locale.direction.equals("rtl", ignoreCase = true)) {
        "calc(${PortfolioTheme.Spacing.xl} + ${PortfolioTheme.Spacing.md})"
    } else {
        PortfolioTheme.Spacing.xl
    }
    val paddingEnd = if (locale.direction.equals("rtl", ignoreCase = true)) {
        PortfolioTheme.Spacing.xl
    } else {
        "calc(${PortfolioTheme.Spacing.xl} + ${PortfolioTheme.Spacing.md})"
    }
    val containerPaddingStart = "calc(${PortfolioTheme.Spacing.md} + $paddingStart)"
    val containerPaddingEnd = "calc(${PortfolioTheme.Spacing.md} + $paddingEnd)"

    val containerModifier = modifier
        .width(100.percent)
        .backgroundColor(PortfolioTheme.Colors.SURFACE)
        .padding(PortfolioTheme.Spacing.md)
        .position(Position.Fixed)
        .top(0.px)
        .positionInset(left = "0", right = "0")
        .zIndex(50)
        .let { base ->
            if (locale.direction.equals("rtl", ignoreCase = true)) {
                base
                    .paddingRight(containerPaddingStart)
                    .paddingLeft(containerPaddingEnd)
            } else {
                base
                    .paddingLeft(containerPaddingStart)
                    .paddingRight(containerPaddingEnd)
            }
        }

    val desktopContent = @Composable {
        DesktopHeader(
            locale = locale,
            modifier = Modifier().width(100.percent),
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl,
            docsBaseUrl = docsBaseUrl,
            forcePortfolioAnchors = forcePortfolioAnchors
        )
    }

    val mobileContent = @Composable {
        MobileHeader(
            locale = locale,
            modifier = Modifier().width(100.percent),
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl,
            docsBaseUrl = docsBaseUrl,
            forcePortfolioAnchors = forcePortfolioAnchors
        )
    }

    ResponsiveLayout(
        content = mapOf(
            ScreenSize.SMALL to mobileContent,
            ScreenSize.MEDIUM to mobileContent,
            ScreenSize.LARGE to desktopContent,
            ScreenSize.XLARGE to desktopContent
        ),
        defaultContent = desktopContent,
        modifier = containerModifier,
        serverSideScreenSize = ScreenSize.LARGE
    )
}

@Composable
private fun DesktopHeader(
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
                    navigationMode = LinkNavigationMode.Native
                )
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

@Composable
private fun MobileHeader(
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

    // Pre-calculate hire link props
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

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier()
                .width(100.percent)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap(PortfolioTheme.Spacing.lg)
                .flexWrap(FlexWrap.Wrap) // Allow wrapping so menu content can break to new line if needed
        ) {
            // Logo
            Box(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 1, shrink = 1, basis = "auto")
            ) {
                Text(
                    text = "YOUSEF",
                    modifier = Modifier()
                        .fontSize(0.9.rem)
                        .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                        .fontWeight(700)
                )
            }

            // Hamburger Menu
            HamburgerMenu(
                modifier = Modifier()
                    .width("40px")
                    .height("40px")
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .zIndex(100),
                menuContent = {
                    Column(
                        modifier = Modifier()
                            .width(100.percent)
                            .padding("0", "16px", "16px", "16px")
                            .gap("12px")
                            .style("border-top", "1px solid #eee")
                            .paddingTop("16px")
                            .style("min-width", "200px")
                            .flex(grow = 1, shrink = 0, basis = "100%") // Force full width
                    ) {
                        val baseNavModifier = Modifier()
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
                            val href = if (forceNativeLinks) {
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
                                navigationMode = linkMode
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
                            navigationMode = LinkNavigationMode.Native
                        )
                        
                        if (chrome.isAdminSession) {
                            val adminHref = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"
                            navLink(
                                label = "Admin",
                                href = adminHref,
                                modifier = baseNavModifier,
                                dataAttributes = mapOf("nav" to "admin"),
                                navigationMode = LinkNavigationMode.Native
                            )
                            navLink(
                                label = "Logout",
                                href = "/admin/logout",
                                modifier = baseNavModifier,
                                dataAttributes = mapOf("nav" to "logout"),
                                navigationMode = LinkNavigationMode.Native
                            )
                        }

                        // Moved Actions
                        Box(modifier = Modifier().height("1px").backgroundColor(PortfolioTheme.Colors.BORDER).width(100.percent).marginTop(PortfolioTheme.Spacing.sm).marginBottom(PortfolioTheme.Spacing.sm)) {}

                        ButtonLink(
                            label = startProjectLabel.resolve(locale),
                            href = hireHref,
                            modifier = Modifier()
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
                            navigationMode = hireNavigation
                        )
                        
                        Box(modifier = Modifier().display(Display.Flex).justifyContent(JustifyContent.Center).paddingTop(PortfolioTheme.Spacing.sm)) {
                            LocaleToggle(current = locale, forceNativeLinks = forceNativeLinks, nativeBaseUrl = nativeBaseUrl)
                        }
                    }
                }
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
