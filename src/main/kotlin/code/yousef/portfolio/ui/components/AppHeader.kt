package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ssr.SITE_URL
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.top
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.textDecoration

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
    NavItem(LocalizedText("Blog", "المدونة"), NavTarget.Section("blog")),
    NavItem(LocalizedText("Contact", "اتصل"), NavTarget.Section("contact"))
)

private val projectsLabel = LocalizedText("Projects", "المشاريع")
private val summonLabel = LocalizedText("Summon", "سُمّون")
private val startProjectLabel = LocalizedText("Start your project", "ابدأ مشروعك")

@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    forceNativeLinks: Boolean = false,
    nativeBaseUrl: String? = null,
    docsBaseUrl: String? = null
) {
    if (forceNativeLinks) {
        NativeAppHeader(locale = locale, baseUrl = nativeBaseUrl, docsBaseUrl = docsBaseUrl)
        return
    }

    val chrome = LocalPageChrome.current
    val navItems = defaultNavItems
    val docsHref = resolveDocsHref(docsBaseUrl)
    val containerModifier = modifier
        .width(100.percent)
        .backgroundColor(PortfolioTheme.Colors.SURFACE)
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor(PortfolioTheme.Colors.BORDER)
        .borderRadius(PortfolioTheme.Radii.lg)
        .padding(PortfolioTheme.Spacing.md)
        .backdropBlur(16.px)
        .display(Display.Flex)
        .alignItems(AlignItems.Center)
        .justifyContent(JustifyContent.SpaceBetween)
        .gap(PortfolioTheme.Spacing.lg)
        .flexWrap(FlexWrap.Wrap)
        .position(Position.Sticky)
        .top(PortfolioTheme.Spacing.md)
        .zIndex(20)
        .boxShadow("0 25px 80px rgba(0,0,0,0.35)")

    Row(modifier = containerModifier) {
        NavDropdownStyles()
        Text(
            text = "YOUSEF BAITALMAL",
            modifier = Modifier()
                .fontSize(0.9.rem)
                .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                .fontWeight(700)
                .flex(grow = 0, shrink = 1, basis = "180px")
        )

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
                .flex(grow = 1, shrink = 1, basis = "360px")
                .flexWrap(FlexWrap.Wrap)
        ) {
            navItems.forEach { item ->
                val href =
                    if (forceNativeLinks) item.target.absoluteHref(locale, nativeBaseUrl) else item.target.href(locale)
                val label = item.label.resolve(locale)
                navLink(
                    label = label,
                    href = href,
                    modifier = Modifier()
                        .textDecoration("none")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                        .letterSpacing(0.08.rem)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .opacity(0.9F),
                    dataAttributes = mapOf("nav" to label.lowercase()),
                    navigationMode = if (forceNativeLinks) LinkNavigationMode.Native else LinkNavigationMode.Client
                )
            }
            ProjectsDropdown(locale = locale, docsHref = docsHref)
        }

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
                .flex(grow = 1, shrink = 0, basis = "220px")
                .justifyContent(JustifyContent.FlexEnd)
                .flexWrap(FlexWrap.Wrap)
        ) {
            if (chrome.isAdminSession) {
                val adminHref = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"
                navLink(
                    label = "Admin",
                    href = adminHref,
                    modifier = Modifier()
                        .textDecoration("none")
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
                        .textDecoration("none")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill),
                    dataAttributes = mapOf("nav" to "logout"),
                    navigationMode = LinkNavigationMode.Native
                )
            }
            val hireHref = if (forceNativeLinks) {
                NavTarget.Section("contact").absoluteHref(locale, nativeBaseUrl)
            } else {
                NavTarget.Section("contact").href(locale)
            }
            val hireNavigation = if (forceNativeLinks) LinkNavigationMode.Native else LinkNavigationMode.Client
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
                    .textDecoration("none")
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
        val baseRoot = nativeBaseUrl?.trimEnd('/') ?: SITE_URL.trimEnd('/')
        if (locale == PortfolioLocale.EN) baseRoot else "$baseRoot/${locale.code}"
    } else {
        if (locale == PortfolioLocale.EN) "/" else "/${locale.code}"
    }
    navLink(
        label = locale.code.uppercase(),
        href = href,
        modifier = Modifier()
            .textDecoration("none")
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
    val defaultBase = SITE_URL.trimEnd('/')
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
private fun NativeAppHeader(locale: PortfolioLocale, baseUrl: String?, docsBaseUrl: String?) {
    val root = baseUrl?.trimEnd('/') ?: SITE_URL.trimEnd('/')
    val localeBase = if (locale == PortfolioLocale.EN) root else "$root/${locale.code}"
    val navHtml = buildString {
        defaultNavItems.forEach { item ->
            val href = item.target.absoluteHref(locale, localeBase)
            append("""<a class="native-header__link" href="$href">${item.label.resolve(locale)}</a>""")
        }
    }
    val docsHref = resolveDocsHref(docsBaseUrl)
    val hireHref = NavTarget.Section("contact").absoluteHref(locale, localeBase)
    val hireLabel = LocalizedText("Hire Me", "توظيفي").resolve(locale)
    val enHref = root
    val arHref = "$root/${PortfolioLocale.AR.code}"
    val projectsLabelValue = projectsLabel.resolve(locale)
    val summonText = summonLabel.resolve(locale)
    RawHtml(
        """
        <style>
          .native-header {
            width: 100%;
            background: ${PortfolioTheme.Colors.SURFACE};
            border: 1px solid ${PortfolioTheme.Colors.BORDER};
            border-radius: ${PortfolioTheme.Radii.lg};
            padding: ${PortfolioTheme.Spacing.md};
            backdrop-filter: blur(16px);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: ${PortfolioTheme.Spacing.lg};
            flex-wrap: wrap;
          }
          .native-header__logo {
            font-size: 0.9rem;
            font-weight: 700;
            letter-spacing: ${PortfolioTheme.Typography.HERO_TRACKING};
          }
          .native-header__links {
            display: flex;
            align-items: center;
            gap: ${PortfolioTheme.Spacing.md};
            flex-wrap: wrap;
          }
          .native-header__link {
            text-decoration: none;
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
            font-size: 0.85rem;
            letter-spacing: 0.08rem;
            padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
            border-radius: ${PortfolioTheme.Radii.pill};
            opacity: 0.9;
          }
          .native-header__dropdown {
            position: relative;
            padding-bottom: 0;
          }
          .native-header__dropdown::after {
            content: "";
            position: absolute;
            left: 0;
            right: 0;
            top: 100%;
            height: ${PortfolioTheme.Spacing.md};
          }
          .native-header__dropdown:hover .native-header__menu,
          .native-header__dropdown:focus-within .native-header__menu {
            display: flex;
          }
          .native-header__dropdown-button {
            background: transparent;
            border: none;
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
            font-size: 0.85rem;
            letter-spacing: 0.08rem;
            padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
            border-radius: ${PortfolioTheme.Radii.pill};
            display: inline-flex;
            align-items: center;
            gap: 4px;
            cursor: pointer;
          }
          .native-header__dropdown-button:focus-visible {
            outline: 2px solid ${PortfolioTheme.Colors.ACCENT_ALT};
          }
          .native-header__dropdown-caret {
            font-size: 0.75rem;
            opacity: 0.7;
          }
          .native-header__menu {
            position: absolute;
            top: calc(100% + 6px);
            left: 0;
            display: none;
            flex-direction: column;
            background: ${PortfolioTheme.Colors.BACKGROUND};
            border: 1px solid ${PortfolioTheme.Colors.BORDER};
            border-radius: ${PortfolioTheme.Radii.md};
            min-width: 200px;
            padding: 8px;
            box-shadow: 0 18px 40px rgba(0,0,0,0.45);
            z-index: 30;
          }
          .native-header__menu a {
            text-decoration: none;
            color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
            padding: 8px 10px;
            border-radius: 12px;
            font-size: 0.9rem;
            white-space: nowrap;
          }
          .native-header__menu a:hover {
            background: ${PortfolioTheme.Colors.SURFACE};
            color: ${PortfolioTheme.Colors.ACCENT_ALT};
          }
          .native-header__actions {
            display: flex;
            align-items: center;
            gap: ${PortfolioTheme.Spacing.md};
            flex-wrap: wrap;
            justify-content: flex-end;
          }
          .native-header__hire {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            background: ${PortfolioTheme.Colors.ACCENT};
            color: #ffffff;
            padding: ${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.lg};
            border-radius: ${PortfolioTheme.Radii.pill};
            font-weight: 600;
            text-decoration: none;
          }
          .native-header__locale {
            display: inline-flex;
            align-items: center;
            gap: ${PortfolioTheme.Spacing.xs};
            padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
            border: 1px solid ${PortfolioTheme.Colors.BORDER};
            border-radius: ${PortfolioTheme.Radii.pill};
          }
          .native-header__locale a {
            text-decoration: none;
            font-size: 0.75rem;
            font-weight: 600;
            padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
            border-radius: ${PortfolioTheme.Radii.pill};
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
          }
          .native-header__locale a.active {
            color: ${PortfolioTheme.Colors.BACKGROUND};
            background: ${PortfolioTheme.Colors.ACCENT_ALT};
          }
          @media (max-width: 768px) {
            .native-header__links,
            .native-header__actions {
              width: 100%;
              justify-content: flex-start;
            }
            .native-header__actions {
              gap: ${PortfolioTheme.Spacing.sm};
            }
          }
        </style>
        <div class="native-header">
          <span class="native-header__logo">YOUSEF BAITALMAL</span>
          <nav class="native-header__links">
            $navHtml
            <div class="native-header__dropdown">
              <button class="native-header__dropdown-button" type="button" aria-haspopup="true">
                <span>$projectsLabelValue</span>
                <span class="native-header__dropdown-caret" aria-hidden="true">▾</span>
              </button>
              <div class="native-header__menu">
                <a href="$docsHref">$summonText</a>
              </div>
            </div>
          </nav>
          <div class="native-header__actions">
            <a class="native-header__hire" href="$hireHref">$hireLabel</a>
            <div class="native-header__locale">
              <a class="${if (locale == PortfolioLocale.EN) "active" else ""}" href="$enHref">EN</a>
              <span style="color:${PortfolioTheme.Colors.TEXT_SECONDARY}; font-size:0.75rem;">|</span>
              <a class="${if (locale == PortfolioLocale.AR) "active" else ""}" href="$arHref">AR</a>
            </div>
          </div>
        </div>
        """.trimIndent()
    )
}

@Composable
private fun ProjectsDropdown(locale: PortfolioLocale, docsHref: String) {
    val label = projectsLabel.resolve(locale)
    val summonText = summonLabel.resolve(locale)
    RawHtml(
        """
        <div class="nav-dropdown">
          <button class="nav-dropdown__button" type="button" aria-haspopup="true">
            <span>$label</span>
            <span class="nav-dropdown__caret" aria-hidden="true">▾</span>
          </button>
          <div class="nav-dropdown__menu">
            <a href="$docsHref">$summonText</a>
          </div>
        </div>
        """.trimIndent()
    )
}

@Composable
private fun NavDropdownStyles() {
    RawHtml(
        """
        <style>
        .nav-dropdown {
          position: relative;
          display: inline-flex;
          flex-direction: column;
          align-items: flex-start;
          padding-bottom: 0;
        }
        .nav-dropdown::after {
          content: "";
          position: absolute;
          left: 0;
          right: 0;
          top: 100%;
          height: ${PortfolioTheme.Spacing.md};
        }
        .nav-dropdown__button {
          background: transparent;
          border: none;
          cursor: pointer;
          text-decoration: none;
          color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
          font-size: 0.85rem;
          letter-spacing: 0.08rem;
          padding: ${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm};
          border-radius: ${PortfolioTheme.Radii.pill};
          display: inline-flex;
          align-items: center;
          gap: 4px;
          text-transform: none;
        }
        .nav-dropdown__button:focus-visible {
          outline: 2px solid ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        .nav-dropdown__caret {
          font-size: 0.75rem;
          opacity: 0.7;
        }
        .nav-dropdown__menu {
          position: absolute;
          top: 100%;
          margin-top: 6px;
          left: 0;
          display: none;
          flex-direction: column;
          background: ${PortfolioTheme.Colors.BACKGROUND};
          border: 1px solid ${PortfolioTheme.Colors.BORDER};
          border-radius: ${PortfolioTheme.Radii.md};
          min-width: 200px;
          padding: 8px;
          box-shadow: 0 18px 40px rgba(0,0,0,0.45);
          z-index: 25;
        }
        .nav-dropdown:hover .nav-dropdown__menu,
        .nav-dropdown:focus-within .nav-dropdown__menu {
          display: flex;
        }
        .nav-dropdown__menu a {
          text-decoration: none;
          color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
          padding: 8px 10px;
          border-radius: 12px;
          font-size: 0.9rem;
          white-space: nowrap;
        }
        .nav-dropdown__menu a:hover,
        .nav-dropdown__menu a:focus {
          background: ${PortfolioTheme.Colors.SURFACE};
          color: ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        </style>
        """.trimIndent()
    )
}

private val docsBaseEnv: String? = System.getenv("DOCS_BASE_URL")?.takeIf { it.isNotBlank() }

private fun resolveDocsHref(overrideValue: String?): String {
    val candidate = overrideValue?.takeIf { it.isNotBlank() }
        ?: docsBaseEnv
        ?: "/summon"
    return candidate.trimEnd('/').ifBlank { "/summon" }
}
