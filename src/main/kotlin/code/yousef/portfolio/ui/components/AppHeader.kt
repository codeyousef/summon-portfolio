package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ssr.docsBaseUrl
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.components.styles.GlobalStyle
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.positionInset
import code.yousef.summon.modifier.LayoutModifiers.top
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.textDecoration
import code.yousef.summon.modifier.TextDecoration
import code.yousef.summon.runtime.rememberMutableStateOf

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
    val chrome = LocalPageChrome.current
    val navItems = defaultNavItems
    val docsHref = resolveDocsHref(docsBaseUrl)
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
    val menuOpenState = rememberMutableStateOf(false)
    val containerModifier = modifier
        .width(100.percent)
        .backgroundColor(PortfolioTheme.Colors.SURFACE)
        .padding(PortfolioTheme.Spacing.md)
        .display(Display.Flex)
        .alignItems(AlignItems.Center)
        .justifyContent(JustifyContent.SpaceBetween)
        .gap(PortfolioTheme.Spacing.lg)
        .flexWrap(FlexWrap.Wrap)
        .position(Position.Fixed)
        .top(0.px)
        .positionInset(left = "0", right = "0")
        .zIndex(50)
        .let { base ->
            if (locale.direction.equals("rtl", ignoreCase = true)) {
                base
                    .style("padding-right", containerPaddingStart)
                    .style("padding-left", containerPaddingEnd)
            } else {
                base
                    .style("padding-left", containerPaddingStart)
                    .style("padding-right", containerPaddingEnd)
            }
        }

    Row(
        modifier = containerModifier
            .attribute("class", "app-header")
            .attribute("data-menu-open", if (menuOpenState.value) "true" else "false")
    ) {
        AppHeaderStyles()
        Box(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
                .flex(grow = 1, shrink = 1, basis = "220px")
                .attribute("class", "app-header__brand")
        ) {
            val toggleOpenLabel = LocalizedText("Open menu", "افتح القائمة").resolve(locale)
            val toggleCloseLabel = LocalizedText("Close menu", "أغلق القائمة").resolve(locale)
            val toggleLabel = if (menuOpenState.value) toggleCloseLabel else toggleOpenLabel
            Button(
                onClick = { menuOpenState.value = !menuOpenState.value },
                label = toggleLabel,
                modifier = Modifier()
                    .attribute("class", "app-header__toggle")
                    .attribute("aria-controls", "app-header-nav app-header-actions")
                    .attribute("aria-expanded", if (menuOpenState.value) "true" else "false")
                    .attribute("aria-label", toggleLabel),
                variant = ButtonVariant.SECONDARY,
                disabled = false
            )
            Text(
                text = "YOUSEF BAITALMAL",
                modifier = Modifier()
                    .fontSize(0.9.rem)
                    .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                    .fontWeight(700)
            )
        }

        Box(
            modifier = Modifier()
                .flex(grow = 1, shrink = 1, basis = "360px")
                .attribute("class", "app-header__nav-wrapper")
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 1, shrink = 1, basis = "360px")
                    .flexWrap(FlexWrap.Wrap)
                    .attribute("class", "app-header__nav")
                    .attribute("id", "app-header-nav")
            ) {
                val baseNavModifier = Modifier()
                    .textDecoration(TextDecoration.None)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
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
                navLink(
                    label = projectsLabel.resolve(locale),
                    href = docsHref,
                    modifier = baseNavModifier,
                    dataAttributes = mapOf("nav" to "projects"),
                    navigationMode = LinkNavigationMode.Native
                )
            }
        }

        Box(
            modifier = Modifier()
                .flex(grow = 0, shrink = 1, basis = "240px")
                .attribute("class", "app-header__actions-wrapper")
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 0, shrink = 0, basis = "auto")
                    .justifyContent(JustifyContent.FlexEnd)
                    .flexWrap(FlexWrap.NoWrap)
                    .attribute("class", "app-header__actions")
                    .attribute("id", "app-header-actions")
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
            .attribute("class", "app-header__locale-toggle")
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

private fun resolveDocsHref(override: String?): String {
    val fallback = docsBaseUrl()
    val resolved = override?.takeIf { it.isNotBlank() } ?: fallback
    return resolved.trimEnd('/')
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun AppHeaderStyles() {
    GlobalStyle(
        """
        .app-header {
          width: 100%;
          border-radius: 0 !important;
          border-width: 0 !important;
          background: rgba(5, 20, 35, 0.92);
          border-bottom: 1px solid ${PortfolioTheme.Colors.BORDER};
          backdrop-filter: blur(18px);
          box-shadow: 0 12px 40px rgba(0,0,0,0.45);
        }
        .app-header__nav-wrapper {
          flex: 1 1 auto;
        }
        .app-header__actions-wrapper {
          flex: 0 0 auto;
        }
        .app-header__nav {
          width: 100%;
        }
        .app-header__nav [data-nav] {
          display: inline-flex;
          align-items: center;
          justify-content: center;
        }
        .app-header__actions {
          width: auto;
        }
        .app-header__brand {
          display: flex;
          align-items: center;
          gap: ${PortfolioTheme.Spacing.sm};
          min-width: 0;
        }
        .app-header__toggle {
          width: 44px;
          height: 38px;
          border-radius: ${PortfolioTheme.Radii.md};
          border: 1px solid ${PortfolioTheme.Colors.BORDER};
          background: ${PortfolioTheme.Colors.SURFACE};
          display: inline-flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: background ${PortfolioTheme.Motion.DEFAULT};
          position: relative;
          padding: 0;
          font-size: 0;
        }
        .app-header__toggle:focus-visible {
          outline: 2px solid ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        .app-header__toggle::before,
        .app-header__toggle::after {
          content: "";
          position: absolute;
          left: 12px;
          right: 12px;
          height: 2px;
          background: ${PortfolioTheme.Colors.TEXT_PRIMARY};
          transition: transform ${PortfolioTheme.Motion.DEFAULT}, box-shadow ${PortfolioTheme.Motion.DEFAULT};
        }
        .app-header__toggle::before {
          top: 12px;
          box-shadow: 0 10px 0 ${PortfolioTheme.Colors.TEXT_PRIMARY};
        }
        .app-header__toggle::after {
          top: 24px;
        }
        .app-header[data-menu-open="true"] .app-header__toggle::before {
          transform: translateY(6px) rotate(45deg);
          box-shadow: none;
        }
        .app-header[data-menu-open="true"] .app-header__toggle::after {
          transform: translateY(-6px) rotate(-45deg);
        }
        @media (min-width: 960px) {
          .app-header__brand {
            flex: 0 1 auto;
          }
          .app-header__brand .app-header__toggle {
            display: none;
          }
          .app-header__nav-wrapper,
          .app-header__actions-wrapper {
            display: flex !important;
          }
        }
        @media (max-width: 959px) {
          .app-header__brand {
            width: 100%;
            justify-content: flex-start;
          }
          .app-header__nav-wrapper,
          .app-header__actions-wrapper {
            display: none !important;
            width: 100%;
            flex-direction: column;
            gap: ${PortfolioTheme.Spacing.md};
            margin-top: ${PortfolioTheme.Spacing.sm};
            max-height: 0;
            opacity: 0;
            transform: translateY(-12px);
            overflow: hidden;
            transition: max-height ${PortfolioTheme.Motion.DEFAULT}, opacity ${PortfolioTheme.Motion.DEFAULT}, transform ${PortfolioTheme.Motion.DEFAULT};
          }
          .app-header__nav {
            flex-direction: column;
            align-items: stretch;
            gap: ${PortfolioTheme.Spacing.sm};
          }
          .app-header__nav [data-nav] {
            width: 100% !important;
            display: flex !important;
            justify-content: flex-start;
            padding: ${PortfolioTheme.Spacing.sm} 0 !important;
            border-radius: 0 !important;
            border-bottom: 1px solid ${PortfolioTheme.Colors.BORDER};
          }
          .app-header__nav [data-nav]:last-child {
            border-bottom: none;
          }
          .app-header[data-menu-open="true"] .app-header__nav-wrapper,
          .app-header[data-menu-open="true"] .app-header__actions-wrapper {
            display: flex !important;
            max-height: 600px;
            opacity: 1;
            transform: translateY(0);
          }
          .app-header__actions {
            flex-direction: column;
            align-items: stretch;
            gap: ${PortfolioTheme.Spacing.md};
            width: 100%;
          }
          .app-header__actions [data-nav] {
            width: 100% !important;
            justify-content: center;
          }
          .app-header__locale-toggle {
            width: 100%;
            justify-content: center;
          }
        }
        """.trimIndent()
    )
}
