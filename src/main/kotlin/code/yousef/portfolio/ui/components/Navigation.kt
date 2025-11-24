package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ssr.docsBaseUrl
import code.yousef.portfolio.ssr.portfolioBaseUrl
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.modifier.Modifier

sealed interface NavTarget {
    data class Section(val id: String) : NavTarget
    data class Page(val path: String) : NavTarget
}

data class NavItem(
    val label: LocalizedText,
    val target: NavTarget
)

val defaultNavItems = listOf(
    NavItem(LocalizedText("About", "حول"), NavTarget.Section("hero")),
    NavItem(LocalizedText("Services", "الخدمات"), NavTarget.Section("services")),
    NavItem(LocalizedText("Blog", "المدونة"), NavTarget.Page("/blog")),
    NavItem(LocalizedText("Contact", "اتصل"), NavTarget.Section("contact"))
)

fun NavTarget.href(locale: PortfolioLocale): String {
    val prefix = locale.pathPrefix()
    return when (this) {
        is NavTarget.Section -> {
            val home = if (prefix.isEmpty()) "/" else prefix
            "$home#${this.id}"
        }

        is NavTarget.Page -> if (prefix.isEmpty()) path else "$prefix${this.path}"
    }
}

fun NavTarget.absoluteHref(locale: PortfolioLocale, nativeBaseUrl: String?): String {
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

fun resolveDocsHref(override: String?): String {
    val fallback = docsBaseUrl()
    val resolved = override?.takeIf { it.isNotBlank() } ?: fallback
    return resolved.trimEnd('/')
}

fun navLink(
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
