package code.yousef.portfolio.ui.materia

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
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
fun MateriaLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        MateriaHero(docsUrl, apiReferenceUrl)
        MateriaFeatureGrid()
        MateriaComponentCallout(docsUrl)
        SummonPromoCallout()
        MateriaCtaFooter(docsUrl, apiReferenceUrl)
        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun MateriaHero(docsUrl: String, apiReferenceUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Materia",
                modifier = Modifier()
                    .fontSize(cssClamp(48.px, 8.vw, 96.px))
                    .fontWeight(900)
                    .letterSpacing("-0.02em")
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = "A Material Design 3 component library for Summon. Build beautiful, accessible UIs with Kotlin Multiplatform.",
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
                    dataAttributes = mapOf("materia-cta" to "docs"),
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
                    dataAttributes = mapOf("materia-cta" to "api"),
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
                text = "Materia brings Material Design 3 components to the Summon framework, with full theming support, accessibility built-in, and seamless integration across all platforms.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

private data class Feature(val title: String, val description: String)

private val materiaFeatures = listOf(
    Feature(
        title = "Material Design 3",
        description = "Full implementation of Google's latest design system with dynamic color, updated components, and modern aesthetics."
    ),
    Feature(
        title = "Accessible by default",
        description = "Every component ships with proper ARIA attributes, keyboard navigation, and screen reader support."
    ),
    Feature(
        title = "Theming system",
        description = "Customize colors, typography, and shapes with a powerful theming API that works across all platforms."
    )
)

@Composable
private fun MateriaFeatureGrid() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            materiaFeatures.forEach { feature ->
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
private fun MateriaComponentCallout(docsUrl: String) {
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
                    text = "Rich component library",
                    modifier = Modifier()
                        .fontWeight(700)
                        .fontSize(1.2.rem)
                )
                Paragraph(
                    text = "Materia includes buttons, cards, dialogs, navigation, forms, and more—all following Material Design 3 specifications.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                Paragraph(
                    text = "Explore the full component catalog in the docs.",
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
                    dataAttributes = mapOf("materia-cta" to "docs-secondary"),
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
@Composable
fun ProfileCard(user: User) {
  Card(
    modifier = Modifier()
      .fillMaxWidth()
  ) {
    Column(
      modifier = Modifier()
        .padding(16.px)
        .gap(12.px)
    ) {
      Text(
        user.name,
        style = MaterialTheme.typography.headlineSmall
      )
      Text(
        user.email,
        style = MaterialTheme.typography.bodyMedium
      )
      FilledButton(
        onClick = { /* ... */ },
        label = "View Profile"
      )
    }
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
private fun SummonPromoCallout() {
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
                text = "Interested in web development with Kotlin?",
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.2.rem)
            )
            Paragraph(
                text = "Check out Summon — the Kotlin Multiplatform frontend framework that powers Materia. Build high-performance apps across JVM, JS, and WASM with a single codebase.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            ButtonLink(
                label = "Explore Summon →",
                href = summonMarketingUrl(),
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
                dataAttributes = mapOf("materia-cta" to "summon-promo"),
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
private fun MateriaCtaFooter(docsUrl: String, apiReferenceUrl: String) {
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
                text = "Build with Materia today",
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
                    dataAttributes = mapOf("materia-cta" to "docs-footer"),
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
                    dataAttributes = mapOf("materia-cta" to "api-footer"),
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
