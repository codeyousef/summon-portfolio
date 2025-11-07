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
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*

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
                .style("font-size", "0.9rem")
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )

        H1(
            text = hero.titlePrimary.resolve(locale),
            modifier = Modifier()
                .style("font-size", "4.5rem")
                .style("font-weight", "800")
                .style("letter-spacing", PortfolioTheme.Typography.HERO_TRACKING)
        )
        H1(
            text = hero.titleSecondary.resolve(locale),
            modifier = Modifier()
                .style("font-size", "4.5rem")
                .style("font-weight", "800")
                .style("letter-spacing", PortfolioTheme.Typography.HERO_TRACKING)
                .style("background", PortfolioTheme.Gradients.ACCENT)
                .style("-webkit-background-clip", "text")
                .style("color", "transparent")
        )

        Paragraph(
            text = hero.subtitle.resolve(locale),
            modifier = Modifier()
                .style("font-size", "1.2rem")
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("line-height", "1.8")
        )

        val primaryCta = hero.ctaPrimary.resolve(locale)
        val secondaryCta = hero.ctaSecondary.resolve(locale)

        Row(
            modifier = Modifier()
                .style("display", "flex")
                .style("gap", PortfolioTheme.Spacing.md)
                .style("flex-wrap", "wrap")
        ) {
            Link(
                primaryCta,
                Modifier()
                    .backgroundColor(PortfolioTheme.Colors.ACCENT_ALT)
                    .color("#050505")
                    .padding("${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.xl}")
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .style("font-weight", "600"),
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
                    .padding("${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.xl}")
                    .borderRadius(PortfolioTheme.Radii.pill)
            )
        }

        Row(
            modifier = Modifier()
                .style("display", "flex")
                .style("gap", PortfolioTheme.Spacing.md)
                .style("flex-wrap", "wrap")
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
            .style("gap", PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
            .style("min-width", 220.px)
            .style("flex", "1 1 220px")
    ) {
        Text(
            text = metric.value,
            modifier = Modifier()
                .style("font-size", "2rem")
                .style("font-weight", "700")
        )
        Text(
            text = metric.label.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("font-size", "0.9rem")
                .style("font-weight", "600")
        )
        Paragraph(
            text = metric.detail.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("font-size", "0.85rem")
        )
    }
}
