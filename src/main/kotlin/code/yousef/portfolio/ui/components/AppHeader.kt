package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
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
    NavItem(LocalizedText("Projects", "المشاريع"), NavTarget.Page("/projects")),
    NavItem(LocalizedText("Services", "الخدمات"), NavTarget.Page("/services")),
    NavItem(LocalizedText("Blog", "المدونة"), NavTarget.Page("/blog")),
    NavItem(LocalizedText("Contact", "اتصل"), NavTarget.Section("contact"))
)

@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    onRequestServices: () -> Unit
) {
    val navItems = defaultNavItems
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

    Row(modifier = containerModifier) {
        Text(
            text = "YOUSEF BAITALMAL",
            modifier = Modifier()
                .fontSize(0.9.rem)
                .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                .fontWeight(700)
        )

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            navItems.forEach { item ->
                val href = item.target.href(locale)
                val label = item.label.resolve(locale)
                navLink(
                    label = label,
                    href = href,
                    modifier = Modifier()
                        .textDecoration("none")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                        .textTransform(TextTransform.Uppercase)
                        .letterSpacing(0.08.rem)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .opacity(0.9F),
                    dataAttributes = mapOf("nav" to label.lowercase()),
                    useClientNavigation = item.target is NavTarget.Section
                )
            }
        }

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Button(
                onClick = { onRequestServices() },
                label = LocalizedText("Hire Me", "توظيفي").resolve(locale),
                modifier = Modifier()
                    .backgroundColor(PortfolioTheme.Colors.ACCENT)
                    .color("#ffffff")
                    .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .fontWeight(600),
                variant = ButtonVariant.PRIMARY,
                disabled = false
            )
            LocaleToggle(current = locale)
        }
    }
}

@Composable
private fun LocaleToggle(current: PortfolioLocale) {
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
        LocaleToggleButton(locale = PortfolioLocale.EN, current = current)
        Text(
            text = "|",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.75.rem)
        )
        LocaleToggleButton(locale = PortfolioLocale.AR, current = current)
    }
}

@Composable
private fun LocaleToggleButton(locale: PortfolioLocale, current: PortfolioLocale) {
    val isActive = locale == current
    navLink(
        label = locale.code.uppercase(),
        href = if (locale == PortfolioLocale.EN) "/" else "/${locale.code}",
        modifier = Modifier()
            .textDecoration("none")
            .color(if (isActive) PortfolioTheme.Colors.BACKGROUND else PortfolioTheme.Colors.TEXT_SECONDARY)
            .backgroundColor(if (isActive) PortfolioTheme.Colors.ACCENT_ALT else "transparent")
            .fontSize(0.75.rem)
            .fontWeight(600)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderRadius(PortfolioTheme.Radii.pill),
        dataAttributes = mapOf("locale" to locale.code),
        useClientNavigation = false
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

private fun navLink(
    label: String,
    href: String,
    modifier: Modifier,
    dataAttributes: Map<String, String>,
    useClientNavigation: Boolean
) {
    if (useClientNavigation) {
        Box(
            modifier = modifier
                .attribute("role", "link")
                .attribute("tabindex", "0")
                .dataAttribute("href", href)
        ) {
            Text(text = label)
        }
    } else {
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
            dataAttributes = dataAttributes
        )
    }
}
