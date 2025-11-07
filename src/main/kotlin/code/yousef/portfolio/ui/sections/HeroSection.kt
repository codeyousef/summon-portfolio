package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.HeroMetric
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.H1
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.Link
import code.yousef.summon.core.style.Color
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.minWidth
import code.yousef.summon.modifier.StylingModifiers.color
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun HeroSection(
    hero: HeroContent,
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Text(
            text = hero.eyebrow.resolve(locale),
            modifier = Modifier()
                .fontSize(0.9.rem)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )

        H1(
            text = hero.titlePrimary.resolve(locale),
            modifier = Modifier()
                .fontSize(4.5.rem)
                .fontWeight(800)
                .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
        )
        H1(
            text = hero.titleSecondary.resolve(locale),
            modifier = Modifier()
                .fontSize(4.5.rem)
                .fontWeight(800)
                .letterSpacing(PortfolioTheme.Typography.HERO_TRACKING)
                .background(PortfolioTheme.Gradients.ACCENT)
                .color(Color.TRANSPARENT)
                .style("-webkit-background-clip", "text")
        )

        Paragraph(
            text = hero.subtitle.resolve(locale),
            modifier = Modifier()
                .fontSize(1.2.rem)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.8)
        )

        val primaryCta = hero.ctaPrimary.resolve(locale)
        val secondaryCta = hero.ctaSecondary.resolve(locale)

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap("wrap")
        ) {
            Link(
                primaryCta,
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.ACCENT_ALT)
                    .color("#050505")
                    .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .fontWeight(600),
                "#projects",
                "_self",
                "",
                false,
                false,
                "",
                "",
                {}
            )
            ButtonLink(
                secondaryCta,
                "#contact",
                Modifier()
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
                    .borderRadius(PortfolioTheme.Radii.pill)
            )
        }

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap("wrap")
        ) {
            hero.metrics.forEach { metric ->
                HeroMetricCard(metric = metric, locale = locale)
            }
        }
    }
}

@Composable
private fun HeroMetricCard(metric: HeroMetric, locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .gap(PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
            .minWidth(220.px)
            .style("flex", "1 1 220px")
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
                .fontSize(0.9.rem)
                .fontWeight(600)
        )
        Paragraph(
            text = metric.detail.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.85.rem)
        )
    }
}
