package code.yousef.portfolio.ui.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun PrimaryButton(text: String, href: String, modifier: Modifier = Modifier()) {
    ButtonLink(
        label = text,
        href = href,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .height(52.px)
            .background(PortfolioTheme.Gradients.ACCENT)
            .multipleShadows(
                shadowConfig(0, 10, 30, 0, Color.hex("#b0123561")),
                shadowConfig(0, 1, 0, 0, Color.hex("#ffffff77"), true)
            )
            .color("#ffffff")
            .padding("0", PortfolioTheme.Spacing.lg)
            .borderRadius(PortfolioTheme.Radii.md)
            .fontWeight(800)
            .letterSpacing(0.3.px)
            .lineHeight(1.0)
            .whiteSpace(WhiteSpace.NoWrap),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("cta" to "hero-primary"),
        navigationMode = LinkNavigationMode.Native
    )
}

@Composable
fun GhostButton(text: String, href: String, modifier: Modifier = Modifier()) {
    ButtonLink(
        label = text,
        href = href,
        modifier = modifier
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .background(PortfolioTheme.Gradients.GLASS)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .padding("0", PortfolioTheme.Spacing.lg)
            .height(52.px)
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .borderRadius(PortfolioTheme.Radii.md)
            .lineHeight(1.0)
            .whiteSpace(WhiteSpace.NoWrap),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("cta" to "hero-secondary"),
        navigationMode = LinkNavigationMode.Native
    )
}

@Composable
fun GhostActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = { onClick() },
        label = text,
        modifier = Modifier()
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .background(PortfolioTheme.Gradients.GLASS)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .padding("0", PortfolioTheme.Spacing.lg)
            .height(52.px)
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .borderRadius(PortfolioTheme.Radii.md)
            .lineHeight(1.0)
            .whiteSpace(WhiteSpace.NoWrap),
        variant = ButtonVariant.SECONDARY,
        disabled = false,
        dataAttributes = mapOf("analytics" to "hero-services")
    )
}
