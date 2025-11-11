package code.yousef.portfolio.ui.summon

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.extensions.vw
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun SummonLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        SummonHero(docsUrl, apiReferenceUrl)
        SummonFeatureGrid()
        SummonRuntimeCallout(docsUrl)
        SummonCtaFooter(docsUrl, apiReferenceUrl)
    }
}

@Composable
private fun SummonHero(docsUrl: String, apiReferenceUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Summon",
                modifier = Modifier()
                    .fontSize(cssClamp(48.px, 8.vw, 96.px))
                    .fontWeight(900)
                    .letterSpacing("-0.02em")
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = "A Kotlin Multiplatform UI framework for glassy, high-performance apps across JVM, JS, and WASM.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.3.rem)
                    .lineHeight(1.7)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                ButtonLink(
                    label = "Read the docs",
                    href = docsUrl,
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("summon-cta" to "docs"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                ButtonLink(
                    label = "API reference",
                    href = apiReferenceUrl,
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("summon-cta" to "api"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
            }
            Paragraph(
                text = "Summon ships a single composition model across JVM, JS, and WASM so your components, modifiers, and routing behave identically on every runtime.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

private data class Feature(val title: String, val description: String)

private val summonFeatures = listOf(
    Feature(
        title = "One codebase",
        description = "Compose UI once and deploy across browsers, WASM surfaces, and JVM servers without branching logic."
    ),
    Feature(
        title = "First-class SSR",
        description = "Summon renders on the server, streams HTML instantly, and hydrates without flicker."
    ),
    Feature(
        title = "Modifier-driven styling",
        description = "Apply fluent modifiers for layout, motion, and accessibility—they translate to clean CSS automatically."
    )
)

@Composable
private fun SummonFeatureGrid() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            summonFeatures.forEach { feature ->
                Column(
                    modifier = Modifier()
                        .flex(grow = 1, shrink = 1, basis = "280px")
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = feature.title,
                        modifier = Modifier()
                            .fontWeight(700)
                    )
                    Paragraph(
                        text = feature.description,
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummonRuntimeCallout(docsUrl: String) {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.lg)
                .flexWrap(FlexWrap.Wrap)
        ) {
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "320px")
                    .gap(PortfolioTheme.Spacing.sm)
            ) {
                Text(
                    text = "Runtime-aware components",
                    modifier = Modifier()
                        .fontWeight(700)
                        .fontSize(1.2.rem)
                )
                Paragraph(
                    text = "Summon ships platform renderers that understand modifiers, hydration hooks, and accessibility attributes so your components remain declarative.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                Paragraph(
                    text = "Explore hydration, routing, and SSR APIs in the docs.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
                ButtonLink(
                    label = "Open documentation",
                    href = docsUrl,
                    modifier = Modifier()
                        .textDecoration("none")
                        .color(PortfolioTheme.Colors.ACCENT_ALT),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("summon-cta" to "docs-secondary"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
            }
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "320px")
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.lg)
                    .padding(PortfolioTheme.Spacing.lg)
                    .backgroundColor(PortfolioTheme.Colors.SURFACE)
            ) {
                RawHtml(
                    """
                    <pre style=\"margin:0;font-size:0.9rem;line-height:1.5;white-space:pre-wrap;\">
@Component
fun PricingCard(plan: Plan) {
  Column(
    modifier = Modifier
      .borderRadius(\"24px\")
      .padding(\"24px\")
      .backgroundColor(\"rgba(255,255,255,0.04)\")
  ) {
    Text(plan.name)
    Text(plan.price, modifier = Modifier
      .fontSize(\"2rem\")
      .fontWeight(700))
    ButtonLink(
      label = \"Deploy\",
      href = plan.url,
      navigationMode = LinkNavigationMode.Native
    )
  }
}
                    </pre>
                    """.trimIndent()
                )
            }
        }
    }
}

@Composable
private fun SummonCtaFooter(docsUrl: String, apiReferenceUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.sm)
                .alignItems(AlignItems.FlexStart)
        ) {
            Text(
                text = "Build with Summon today",
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.4.rem)
            )
            Paragraph(
                text = "Docs live at summon.site/docs and the API reference is always up to date at /api-reference.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                ButtonLink(
                    label = "summon.site/docs",
                    href = docsUrl,
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("summon-cta" to "docs-footer"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                ButtonLink(
                    label = "summon.site/api-reference",
                    href = apiReferenceUrl,
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("summon-cta" to "api-footer"),
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
}
