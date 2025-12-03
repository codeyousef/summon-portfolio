package code.yousef.portfolio.ui.summon

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight
import java.net.URI

@Composable
fun SummonLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {

        SummonHero(docsUrl, apiReferenceUrl)
        SummonFeatureGrid()
        SummonRuntimeCallout(docsUrl)
        MateriaPromoCallout()
        SummonCtaFooter(docsUrl, apiReferenceUrl)
        PortfolioFooter(locale = PortfolioLocale.EN)
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
                text = "A Kotlin Multiplatform frontend framework for high-performance apps across JVM, JS, and WASM.",
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
                        .textDecoration(TextDecoration.None)
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
                val snippet = """
@Component
fun PricingCard(plan: Plan) {
  Column(
    modifier = Modifier()
      .borderRadius(24.px)
      .padding(24.px)
      .backgroundColor(PortfolioTheme.Colors.SURFACE)
  ) {
    Text(plan.name)
    Text(
      plan.price,
      modifier = Modifier()
        .fontSize(2.rem)
        .fontWeight(700)
    )
    ButtonLink(
      label = "Deploy",
      href = plan.url,
      navigationMode = LinkNavigationMode.Native
    )
  }
}
                """.trimIndent()
                Text(
                    text = snippet,
                    modifier = Modifier()
                        .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                        .fontSize(0.9.rem)
                        .lineHeight(1.5)
                        .whiteSpace(WhiteSpace.PreWrap)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            }
        }
    }
}

@Composable
private fun MateriaPromoCallout() {
    SectionWrap {
        Column(
            modifier = Modifier()
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .padding(PortfolioTheme.Spacing.lg)
                .backgroundColor(PortfolioTheme.Colors.SURFACE)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            Text(
                text = "Looking for UI components?",
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.2.rem)
            )
            Paragraph(
                text = "Check out Materia — a Material Design 3 component library built for Summon. Get beautiful, accessible buttons, cards, dialogs, and more out of the box.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            ButtonLink(
                label = "Explore Materia →",
                href = materiaMarketingUrl(),
                modifier = Modifier()
                    .backgroundColor(PortfolioTheme.Colors.ACCENT)
                    .color("#ffffff")
                    .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                    .borderRadius(PortfolioTheme.Radii.md)
                    .fontWeight(700)
                    .textDecoration(TextDecoration.None)
                    .whiteSpace(WhiteSpace.NoWrap)
                    .marginTop(PortfolioTheme.Spacing.sm),
                navigationMode = LinkNavigationMode.Native,
                dataAttributes = mapOf("summon-cta" to "materia-promo"),
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
private fun SummonCtaFooter(docsUrl: String, apiReferenceUrl: String) {
    val docsLabel = humanReadableLabel(docsUrl, "/docs")
    val apiLabel = humanReadableLabel(apiReferenceUrl, "/api-reference")
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
                text = "Docs live at $docsLabel and the API reference is always up to date at $apiLabel.",
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
                    label = docsLabel,
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
                    label = apiLabel,
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

private fun humanReadableLabel(url: String, fallbackPath: String): String {
    return runCatching {
        val uri = URI(url)
        val host = uri.host ?: return@runCatching url
        val path = uri.path?.trim('/')
        if (path.isNullOrBlank()) host else "$host/$path"
    }.getOrElse {
        val sanitized = url.removePrefix("https://").removePrefix("http://")
        if (sanitized.contains('/')) sanitized else "$sanitized$fallbackPath"
    }
}
