package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.Link
import code.yousef.summon.modifier.Modifier

private data class NavItem(
    val label: LocalizedText,
    val anchor: String
)

private val defaultNavItems = listOf(
    NavItem(LocalizedText("Hero", "الرئيسية"), "hero"),
    NavItem(LocalizedText("Projects", "المشاريع"), "projects"),
    NavItem(LocalizedText("Services", "الخدمات"), "services"),
    NavItem(LocalizedText("Blog", "المدونة"), "blog"),
    NavItem(LocalizedText("Contact", "اتصل"), "contact")
)

@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val navItems = defaultNavItems
    val containerModifier = modifier
        .style("width", "100%")
        .backgroundColor(PortfolioTheme.Colors.surface)
        .border("1px", "solid", PortfolioTheme.Colors.border)
        .borderRadius(PortfolioTheme.Radii.lg)
        .style("padding", PortfolioTheme.Spacing.md)
        .style("backdrop-filter", "blur(16px)")
        .style("display", "flex")
        .style("align-items", "center")
        .style("justify-content", "space-between")
        .style("gap", PortfolioTheme.Spacing.lg)

    val pathPrefix = locale.pathPrefix()
    val modalHref = if (pathPrefix.isEmpty()) "?modal=services#services" else "$pathPrefix?modal=services#services"

    Row(modifier = containerModifier) {
        Text(
            text = "YOUSEF BAITALMAL",
            modifier = Modifier()
                .style("font-size", "0.9rem")
                .style("letter-spacing", PortfolioTheme.Typography.heroTracking)
                .style("font-weight", "700")
        )

        Row(
            modifier = Modifier()
                .style("display", "flex")
                .style("align-items", "center")
                .style("gap", PortfolioTheme.Spacing.md)
        ) {
            navItems.forEach { item ->
                val anchorHref = if (pathPrefix.isEmpty()) "#${item.anchor}" else "$pathPrefix#${item.anchor}"
                Link(
                    item.label.resolve(locale),
                    Modifier()
                        .color(PortfolioTheme.Colors.textSecondary)
                        .style("font-size", "0.85rem")
                        .style("text-transform", "uppercase")
                        .style("letter-spacing", "0.08em")
                        .attribute("aria-label", item.label.resolve(locale)),
                    anchorHref,
                    "_self",
                    "",
                    false,
                    false,
                    "",
                    "",
                    {}
                )
            }
        }

        Row(
            modifier = Modifier()
                .style("display", "flex")
                .style("align-items", "center")
                .style("gap", PortfolioTheme.Spacing.md)
        ) {
            ButtonLink(
                LocalizedText("Hire Me", "توظيفي").resolve(locale),
                modalHref,
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.accent)
                    .color("#ffffff")
                    .style("padding", "${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.lg}")
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .style("font-weight", "600")
            )
            LocaleToggle(current = locale)
        }
    }
}

@Composable
private fun LocaleToggle(current: PortfolioLocale) {
    Row(
        modifier = Modifier()
            .style("display", "inline-flex")
            .style("align-items", "center")
            .style("gap", PortfolioTheme.Spacing.xs)
            .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
            .border("1px", "solid", PortfolioTheme.Colors.border)
            .borderRadius(PortfolioTheme.Radii.pill)
    ) {
        LocaleToggleButton(locale = PortfolioLocale.EN, current = current)
        Text(
            text = "|",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.textSecondary)
                .style("font-size", "0.75rem")
        )
        LocaleToggleButton(locale = PortfolioLocale.AR, current = current)
    }
}

@Composable
private fun LocaleToggleButton(locale: PortfolioLocale, current: PortfolioLocale) {
    val isActive = locale == current
    Link(
        locale.code.uppercase(),
        Modifier()
            .color(if (isActive) PortfolioTheme.Colors.background else PortfolioTheme.Colors.textSecondary)
            .backgroundColor(if (isActive) PortfolioTheme.Colors.accentAlt else "transparent")
            .style("font-size", "0.75rem")
            .style("font-weight", "600")
            .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
            .borderRadius(PortfolioTheme.Radii.pill),
        if (locale == PortfolioLocale.EN) "/" else "/${locale.code}",
        "_self",
        "",
        false,
        false,
        "",
        "",
        {}
    )
}
