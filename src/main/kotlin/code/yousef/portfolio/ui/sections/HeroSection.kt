package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.HeroMetric
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.HeroStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.*
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.color
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

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
                        .boxShadow("0 1px 0 #ffffff33")
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
                    text = "Yousef",
                    modifier = Modifier().fontWeight(800)
                )
                Text(
                    text = tagline,
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.85.rem)
                )
                Text(
                    text = HeroStrings.baseLocation.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize(0.8.rem)
                )
            }
        }
        GlassPill(
            text = HeroStrings.availability.resolve(locale),
            emphasize = true
        )
    }
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
        ButtonLink(
            label = "View featured services",
            href = "/services",
            modifier = Modifier().textDecoration(TextDecoration.None).color(PortfolioTheme.Colors.TEXT_PRIMARY),
            target = null,
            rel = null,
            title = null,
            id = null,
            ariaLabel = null,
            ariaDescribedBy = null,
            dataHref = null,
            dataAttributes = emptyMap(),
            navigationMode = LinkNavigationMode.Native
        )
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
