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
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.positionInset
import codes.yousef.summon.modifier.LayoutModifiers.top
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.ModifierExtras.onClick
import codes.yousef.summon.runtime.remember
import codes.yousef.summon.runtime.mutableStateOf

private sealed interface NavTarget {
    data class Section(val id: String) : NavTarget
    data class Page(val path: String) : NavTarget
}

private data class NavItem(
    val label: LocalizedText,
    val target: NavTarget
)

private val defaultNavItems = listOf(
    NavItem(LocalizedText("About", "حول"), NavTarget.Section("hero")),
    NavItem(LocalizedText("Services", "الخدمات"), NavTarget.Section("services")),
    NavItem(LocalizedText("Blog", "المدونة"), NavTarget.Page("/blog")),
    NavItem(LocalizedText("Contact", "اتصل"), NavTarget.Section("contact"))
)

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
    val isOpen = remember { mutableStateOf(false) }

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
                .flexWrap(FlexWrap.NoWrap)
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

            // Actions (Hire, Hamburger, Locale)
            Box(
                modifier = Modifier()
                    .flex(grow = 0, shrink = 1, basis = "auto")
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
                    
                    // Hamburger Button
                    MaterialIcon(
                        name = if (isOpen.value) "close" else "menu",
                        modifier = Modifier()
                            .fontSize(1.5.rem)
                            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .cursor(Cursor.Pointer)
                            .display(Display.Flex)
                            .alignItems(AlignItems.Center)
                            .justifyContent(JustifyContent.Center)
                            .role("button")
                            .tabIndex(0)
                            .position(Position.Relative)
                            .zIndex(100)
                            .style("user-select", "none"),
                        onClick = { isOpen.value = !isOpen.value }
                    )

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

        // Mobile Menu Content
        if (isOpen.value) {
            Box(
                modifier = Modifier()
                    .width(100.percent)
                    .backgroundColor(PortfolioTheme.Colors.SURFACE)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.lg)
                    .padding(PortfolioTheme.Spacing.md)
                    .marginTop(PortfolioTheme.Spacing.md)
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
                
                Column(modifier = Modifier().gap(PortfolioTheme.Spacing.sm)) {
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
                }
            }
        }
    }
}

@Composable
private fun LocaleToggle(current: PortfolioLocale, forceNativeLinks: Boolean, nativeBaseUrl: String?) {
    Row(
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .gap(PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
    ) {
        LocaleToggleButton(
            locale = PortfolioLocale.EN,
            current = current,
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl
        )
        Text(
            text = "|",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.75.rem)
        )
        LocaleToggleButton(
            locale = PortfolioLocale.AR,
            current = current,
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl
        )
    }
}

@Composable
private fun LocaleToggleButton(
    locale: PortfolioLocale,
    current: PortfolioLocale,
    forceNativeLinks: Boolean,
    nativeBaseUrl: String?
) {
    val isActive = locale == current
    val href = if (forceNativeLinks) {
        val baseRoot = nativeBaseUrl?.trimEnd('/') ?: portfolioBaseUrl().trimEnd('/')
        if (locale == PortfolioLocale.EN) baseRoot else "$baseRoot/${locale.code}"
    } else {
        if (locale == PortfolioLocale.EN) "/" else "/${locale.code}"
    }
    navLink(
        label = locale.code.uppercase(),
        href = href,
        modifier = Modifier()
            .textDecoration(TextDecoration.None)
            .color(if (isActive) PortfolioTheme.Colors.BACKGROUND else PortfolioTheme.Colors.TEXT_SECONDARY)
            .backgroundColor(if (isActive) PortfolioTheme.Colors.ACCENT_ALT else "transparent")
            .fontSize(0.75.rem)
            .fontWeight(600)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderRadius(PortfolioTheme.Radii.pill)
            .whiteSpace(WhiteSpace.NoWrap),
        dataAttributes = mapOf("locale" to locale.code),
        navigationMode = LinkNavigationMode.Native
    )
}

private fun NavTarget.href(locale: PortfolioLocale): String {
    val prefix = locale.pathPrefix()
    return when (this) {
        is NavTarget.Section -> {
            val home = if (prefix.isEmpty()) "/" else prefix
            "$home#${this.id}"
        }

        is NavTarget.Page -> if (prefix.isEmpty()) path else "$prefix${this.path}"
    }
}

private fun NavTarget.absoluteHref(locale: PortfolioLocale, nativeBaseUrl: String?): String {
    val defaultBase = portfolioBaseUrl().trimEnd('/')
    val suppliedBase = nativeBaseUrl?.trimEnd('/')
    val base = suppliedBase ?: when (locale) {
        PortfolioLocale.EN -> defaultBase
        else -> "$defaultBase/${locale.code}"
    }
    return when (this) {
        is NavTarget.Section -> "$base#${this.id}"
        is NavTarget.Page -> if (path.startsWith("http")) path else "$base${this.path}"
    }
}

private fun navLink(
    label: String,
    href: String,
    modifier: Modifier,
    dataAttributes: Map<String, String>,
    navigationMode: LinkNavigationMode
) {
    AnchorLink(
        label = label,
        href = href,
        modifier = modifier,
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = dataAttributes,
        navigationMode = navigationMode
    )
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

private fun resolveDocsHref(override: String?): String {
    val fallback = docsBaseUrl()
    val resolved = override?.takeIf { it.isNotBlank() } ?: fallback
    return resolved.trimEnd('/')
}
