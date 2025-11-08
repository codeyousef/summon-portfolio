package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight

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

    val pathPrefix = locale.pathPrefix()

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
                val anchorHref = if (pathPrefix.isEmpty()) "#${item.anchor}" else "$pathPrefix#${item.anchor}"
                AnchorLink(
                    href = anchorHref,
                    dataHref = anchorHref,
                    label = item.label.resolve(locale),
                    dataAttributes = mapOf("nav" to item.anchor),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                        .textTransform(TextTransform.Uppercase)
                        .letterSpacing(0.08.rem)
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
    AnchorLink(
        href = if (locale == PortfolioLocale.EN) "/" else "/${locale.code}",
        dataHref = if (locale == PortfolioLocale.EN) "/" else "/${locale.code}",
        label = locale.code.uppercase(),
        dataAttributes = mapOf("locale" to locale.code),
        modifier = Modifier()
            .style("text-decoration", "none")
            .color(if (isActive) PortfolioTheme.Colors.BACKGROUND else PortfolioTheme.Colors.TEXT_SECONDARY)
            .backgroundColor(if (isActive) PortfolioTheme.Colors.ACCENT_ALT else "transparent")
            .fontSize(0.75.rem)
            .fontWeight(600)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderRadius(PortfolioTheme.Radii.pill)
    )
}
