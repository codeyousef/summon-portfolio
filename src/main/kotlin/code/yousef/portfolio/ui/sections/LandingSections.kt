@file:Suppress("FunctionName")

package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.HeroMetric
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.display.Image
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.core.style.Color
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.extensions.vw
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.color
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import code.yousef.summon.modifier.TextDecoration
import java.time.Year

@Composable
fun HeroSection(
    hero: HeroContent,
    locale: PortfolioLocale,
    onRequestServices: () -> Unit
) {
    val primaryTitle = hero.titlePrimary.resolve(locale)
    val secondaryTitle = hero.titleSecondary.resolve(locale)
    val subtitle = hero.subtitle.resolve(locale)
    SectionWrap(modifier = Modifier().id("hero")) {
        HeroIntroCard(
            tagline = hero.eyebrow.resolve(locale),
            locale = locale
        )
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Row)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xl)
                .flexWrap(FlexWrap.Wrap)
        ) {
            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.md)
                    .flex(grow = 1, shrink = 1, basis = "480px")
            ) {
                Text(
                    text = primaryTitle,
                    modifier = Modifier()
                        .fontSize(cssClamp(38.px, 7.2.vw, 96.px))
                        .fontWeight(900)
                        .letterSpacing("-0.02em")
                        .backgroundLayers {
                            linearGradient {
                                direction("180deg")
                                colorStop("#ffffff", "0%")
                                colorStop("#dfdfea", "65%")
                                colorStop("#c5c5d4", "100%")
                            }
                        }
                        .backgroundClipText()
                        .color(Color.TRANSPARENT)
                        .style("text-shadow", "0 1px 0 #ffffff33")
                )
                Text(
                    text = secondaryTitle,
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 6.vw, 72.px))
                        .fontWeight(700)
                        .letterSpacing("-0.02em")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
                Paragraph(
                    text = subtitle,
                    modifier = Modifier()
                        .color("#cfcfe0")
                        .fontSize(cssClamp(16.px, 2.2.vw, 22.px))
                        .lineHeight(1.5)
                )
                CtaRow(
                    primaryLabel = hero.ctaPrimary.resolve(locale),
                    secondaryLabel = hero.ctaSecondary.resolve(locale),
                    onRequestServices = onRequestServices
                )
                HeroMetricsRow(metrics = hero.metrics, locale = locale)
            }
            HeroMockCard()
        }
    }
}

private val availabilityText = LocalizedText(
    en = "Open for select 2025 collaborations",
    ar = "متاح لمشاريع مختارة في 2025"
)

private val baseLocationText = LocalizedText(
    en = "Based in Dubai · Working globally",
    ar = "مقيم في دبي · أعمل عالميًا"
)

@Composable
private fun HeroIntroCard(tagline: String, locale: PortfolioLocale) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(PortfolioTheme.Spacing.md)
            .flexWrap(FlexWrap.Wrap)
            .padding(PortfolioTheme.Spacing.md)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .background(PortfolioTheme.Gradients.GLASS)
            .boxShadow(PortfolioTheme.Shadows.LOW)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            LogoOrb()
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                Text(
                    text = "Yousef Baitalmal",
                    modifier = Modifier().fontWeight(800)
                )
                Text(
                    text = tagline,
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                )
                Text(
                    text = baseLocationText.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.8.rem)
                )
            }
        }
        GlassPill(
            text = availabilityText.resolve(locale),
            emphasize = true
        )
    }
}

@Composable
private fun LogoOrb() {
    Box(
        modifier = Modifier()
            .width(38.px)
            .height(38.px)
            .borderRadius(14.px)
            .backgroundLayers {
                radialGradient {
                    size("120%", "120%")
                    position("30%", "20%")
                    colorStop(PortfolioTheme.Colors.ACCENT_ALT, "0%")
                    colorStop(PortfolioTheme.Colors.ACCENT, "35%")
                    colorStop("#5e0f27", "60%")
                    colorStop("#14070e", "100%")
                }
            }
            .multipleShadows(
                shadowConfig(0, 0, 20, 0, Color.hex("#00000099"), true),
                shadowConfig(0, 10, 30, 0, Color.hex("#00000099"))
            )
            .position(Position.Relative)
    ) {
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .inset((-1).px)
                .borderRadius(14.px)
                .backgroundLayers {
                    conicGradient {
                        from("210deg")
                        colorStop("#ffffff88")
                        colorStop("#ffffff00", "40%")
                        colorStop("#ffffff00", "70%")
                        colorStop("#ffffff33")
                    }
                }
                .filter { blur(0.5) }
                .mixBlendMode(BlendMode.Overlay)
        ) {}
    }
}

@Composable
private fun GlassPill(text: String, emphasize: Boolean = false, modifier: Modifier = Modifier()) {
    Text(
        text = text,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
            .background(
                if (emphasize) PortfolioTheme.Gradients.ACCENT else PortfolioTheme.Gradients.GLASS
            )
            .fontWeight(if (emphasize) 800 else 600)
            .fontSize(0.85.rem)
    )
}

@Composable
private fun CtaRow(
    primaryLabel: String,
    secondaryLabel: String,
    onRequestServices: () -> Unit
) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .gap(PortfolioTheme.Spacing.sm)
            .flexWrap(FlexWrap.Wrap)
            .justifyContent(JustifyContent.FlexStart)
    ) {
        PrimaryButton(
            text = primaryLabel,
            href = "#projects"
        )
        GhostButton(
            text = secondaryLabel,
            href = "#contact"
        )
        GhostActionButton(text = "Featured Services", onClick = onRequestServices)
    }
}

@Composable
private fun HeroMetricsRow(
    metrics: List<HeroMetric>,
    locale: PortfolioLocale
) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .gap(PortfolioTheme.Spacing.sm)
            .flexWrap(FlexWrap.Wrap)
    ) {
        metrics.forEach { metric ->
            HeroMetricCard(metric = metric, locale = locale)
        }
    }
}

@Composable
private fun HeroMetricCard(metric: HeroMetric, locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .gap(PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.md)
            .flex(grow = 1, shrink = 1, basis = "220px")
            .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
    ) {
        Text(
            text = metric.value,
            modifier = Modifier()
                .fontSize(2.rem)
                .fontWeight(700)
        )
        Text(
            text = metric.label.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.95.rem)
                .fontWeight(600)
        )
        Paragraph(
            text = metric.detail.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.4)
        )
    }
}

@Composable
private fun HeroMockCard() {
    Column(
        modifier = Modifier()
            .flex(grow = 1, shrink = 1, basis = "360px")
            .gap(PortfolioTheme.Spacing.md)
    ) {
        Column(
            modifier = Modifier()
                .aspectRatio(1.35 / 1)
                .borderRadius(PortfolioTheme.Radii.lg)
                .background(PortfolioTheme.Gradients.CARD)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .boxShadow(PortfolioTheme.Shadows.LOW)
                .padding(PortfolioTheme.Spacing.md)
                .position(Position.Relative)
        ) {
            Row(
                modifier = Modifier()
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.md)
                    .height(42.px)
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.sm)
                    .padding("0", PortfolioTheme.Spacing.md)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier()
                            .width(10.px)
                            .height(10.px)
                            .borderRadius(PortfolioTheme.Radii.pill)
                            .backgroundColor("#ffffff22")
                    ) {}
                }
                Text(
                    text = "portfolio/pages/Home.kt",
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
            }
            CodeBlock(
                lines = listOf(
                    "val Home = page(\"/\") {",
                    "  html {",
                    "    head { title(\"Yousef — Portfolio\") }",
                    "    body {",
                    "      header { h1(\"Hello from Yousef\") }",
                    "      section { p(\"Engineering from first principles.\") }",
                    "      clientIsland { /* mount your widget here */ }",
                    "    }",
                    "  }",
                    "}"
                )
            )
        }
    }
}

@Composable
fun PortfolioFooter(locale: PortfolioLocale) {
    val currentYear = Year.now().value
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            Text(text = "© $currentYear")
            Text(text = "Yousef Baitalmal")
        }
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xxs)
        ) {
            Text(
                text = "Built with",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Image(
                src = "/static/logo.png",
                alt = "Summon",
                modifier = Modifier()
                    .width(24.px)
                    .height(24.px)
                    .display(Display.InlineFlex)
            )
            AnchorLink(
                label = "Summon",
                href = summonMarketingUrl(),
                modifier = Modifier()
                    .textDecoration(TextDecoration.Underline)
                    .color(PortfolioTheme.Colors.ACCENT_ALT),
                navigationMode = LinkNavigationMode.Native,
                dataAttributes = mapOf("footer-link" to "summon"),
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null
            )
        }
    }
}


@Composable
private fun PrimaryButton(text: String, href: String, modifier: Modifier = Modifier()) {
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
private fun GhostButton(text: String, href: String, modifier: Modifier = Modifier()) {
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
private fun GhostActionButton(text: String, onClick: () -> Unit) {
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

@Composable
private fun CodeBlock(lines: List<String>, showCopyButton: Boolean = false) {
    Column(
        modifier = Modifier()
            .backgroundColor("#0b0d12")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#ffffff14")
            .borderRadius(PortfolioTheme.Radii.md)
            .padding(PortfolioTheme.Spacing.md)
            .fontWeight(FontWeight.Medium)
            .fontSize(14.px)
            .lineHeight(1.6)
            .fontFamily(PortfolioTheme.Typography.FONT_MONO)
            .overflow(Overflow.Auto)
    ) {
        if (showCopyButton) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.FlexEnd)
                    .marginBottom(PortfolioTheme.Spacing.sm)
            ) {
                Button(
                    onClick = null,
                    label = "Copy",
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor(PortfolioTheme.Gradients.GLASS)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill),
                    variant = ButtonVariant.SECONDARY,
                    disabled = false,
                    dataAttributes = mapOf("copy" to "code")
                )
            }
        }
        lines.forEach { line ->
            Text(line, modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY))
        }
    }
}
