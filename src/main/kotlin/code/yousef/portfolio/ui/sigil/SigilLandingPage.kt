package code.yousef.portfolio.ui.sigil

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.LandingBranding
import code.yousef.portfolio.ui.components.LandingNavbar
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
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
import codes.yousef.summon.modifier.cssClamp
import java.net.URI

@Composable
fun SigilLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        LandingNavbar(
            branding = LandingBranding.sigil(docsUrl, apiReferenceUrl)
        )
        SigilHero(docsUrl, apiReferenceUrl)
        SigilFeatureGrid()
        SigilCodeExample(docsUrl)
        MateriaIntegrationCallout()
        SummonIntegrationCallout()
        SigilCtaFooter(docsUrl, apiReferenceUrl)
        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun SigilHero(docsUrl: String, apiReferenceUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                // Sigil logo image
                Image(
                    src = "/static/sigil-logo.png",
                    alt = "Sigil Logo",
                    modifier = Modifier()
                        .width(cssClamp(48.px, 10.vw, 96.px))
                        .height(cssClamp(48.px, 10.vw, 96.px))
                        .objectFit(ObjectFit.Contain)
                )
                Text(
                    text = "Sigil",
                    modifier = Modifier()
                        .fontSize(cssClamp(48.px, 8.vw, 96.px))
                        .fontWeight(900)
                        .letterSpacing("-0.02em")
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                )
            }
            Paragraph(
                text = "Declarative 3D for Kotlin Multiplatform & Jetpack Compose. Build reactive 3D scenes using familiar Compose syntax—powered by Materia's WebGPU/Vulkan engine.",
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
                        .backgroundColor(SIGIL_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("sigil-cta" to "docs"),
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
                    dataAttributes = mapOf("sigil-cta" to "api"),
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
                text = "Sigil bridges Compose's reactivity with Materia's high-performance 3D rendering. Use standard Compose state (mutableStateOf, animate*AsState) to drive animations and scene updates.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

private data class Feature(val title: String, val description: String)

private val sigilFeatures = listOf(
    Feature(
        title = "Declarative 3D Syntax",
        description = "Build scenes with Box, Sphere, Group, and Light composables. No manual loop management or context handling required."
    ),
    Feature(
        title = "Reactive State",
        description = "Drive animations and scene updates using standard Compose state—mutableStateOf, animate*AsState, and more."
    ),
    Feature(
        title = "Multiplatform Ready",
        description = "JVM (Desktop) with Vulkan, Web (JS/Wasm) with WebGPU/WebGL2. Android & iOS support coming soon."
    ),
    Feature(
        title = "PBR Materials",
        description = "Physically Based Rendering for realistic lighting and materials. Control metalness, roughness, and color with type-safe APIs."
    )
)

@Composable
private fun SigilFeatureGrid() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            sigilFeatures.forEach { feature ->
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
private fun SigilCodeExample(docsUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Create stunning 3D scenes in just a few lines",
                modifier = Modifier()
                    .fontSize(1.5.rem)
                    .fontWeight(700)
            )
            // Code example block
            Column(
                modifier = Modifier()
                    .backgroundColor("#0d1117")
                    .borderRadius(PortfolioTheme.Radii.lg)
                    .padding(PortfolioTheme.Spacing.lg)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .overflow(Overflow.Auto)
            ) {
                Text(
                    text = """@Composable
fun RotatingCube() {
    var rotationY by remember { mutableStateOf(0f) }

    MateriaCanvas(modifier = Modifier.fillMaxSize()) {
        // Lighting
        AmbientLight(intensity = 0.5f)
        DirectionalLight(position = Vector3(5f, 10f, 5f))

        // Objects
        Group(rotation = Vector3(0f, rotationY, 0f)) {
            Box(
                color = 0xFF4488FF.toInt(),
                metalness = 0.5f,
                roughness = 0.1f
            )
        }
    }
}""",
                    modifier = Modifier()
                        .fontFamily("'JetBrains Mono', 'Fira Code', monospace")
                        .fontSize(0.85.rem)
                        .lineHeight(1.6)
                        .color("#e6edf3")
                        .whiteSpace(WhiteSpace.Pre)
                )
            }
            Paragraph(
                text = "Sigil handles the scene graph, resource management, and GPU synchronization automatically. Just describe your scene and let the engine do the heavy lifting.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun MateriaIntegrationCallout() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
                .flexWrap(FlexWrap.Wrap)
                .padding(PortfolioTheme.Spacing.xl)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .background("linear-gradient(135deg, rgba(106,215,255,0.1) 0%, transparent 100%)")
        ) {
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "300px")
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = "Powered by Materia",
                    modifier = Modifier()
                        .fontSize(1.25.rem)
                        .fontWeight(700)
                )
                Paragraph(
                    text = "Sigil is built on top of the Materia engine, which provides modern WebGPU/Vulkan/Metal graphics backends. Get high-performance 3D rendering with automatic platform selection.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                AnchorLink(
                    label = "Learn more about Materia →",
                    href = materiaMarketingUrl(),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.LINK)
                        .textDecoration(TextDecoration.None)
                        .fontWeight(600)
                        .hover(Modifier().textDecoration(TextDecoration.Underline)),
                    navigationMode = LinkNavigationMode.Native,
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap()
                )
            }
        }
    }
}

@Composable
private fun SummonIntegrationCallout() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
                .flexWrap(FlexWrap.Wrap)
                .padding(PortfolioTheme.Spacing.xl)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .background("linear-gradient(135deg, rgba(255,137,176,0.1) 0%, transparent 100%)")
        ) {
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "300px")
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = "Works with Summon",
                    modifier = Modifier()
                        .fontSize(1.25.rem)
                        .fontWeight(700)
                )
                Paragraph(
                    text = "Sigil's sigil-summon module provides seamless integration with Summon, allowing you to embed 3D canvases in your Kotlin Multiplatform web applications with SSR support.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                AnchorLink(
                    label = "Learn more about Summon →",
                    href = summonMarketingUrl(),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.ACCENT_ALT)
                        .textDecoration(TextDecoration.None)
                        .fontWeight(600)
                        .hover(Modifier().textDecoration(TextDecoration.Underline)),
                    navigationMode = LinkNavigationMode.Native,
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap()
                )
            }
        }
    }
}

@Composable
private fun SigilCtaFooter(docsUrl: String, apiReferenceUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
                .padding(PortfolioTheme.Spacing.xxl, 0.px)
        ) {
            Text(
                text = "Ready to build 3D with Compose?",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(700)
                    .textAlign(TextAlign.Center)
            )
            Paragraph(
                text = "Get started with Sigil today and bring declarative 3D to your Kotlin Multiplatform projects.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .textAlign(TextAlign.Center)
                    .maxWidth(600.px)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.md)
                    .flexWrap(FlexWrap.Wrap)
                    .justifyContent(JustifyContent.Center)
            ) {
                ButtonLink(
                    label = "Get Started",
                    href = docsUrl,
                    modifier = Modifier()
                        .backgroundColor(SIGIL_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("sigil-cta" to "footer-docs"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                ButtonLink(
                    label = "View on GitHub",
                    href = "https://github.com/codeyousef/sigil",
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("sigil-cta" to "github"),
                    target = "_blank",
                    rel = "noopener noreferrer",
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

/** Sigil accent color - teal/cyan to differentiate from Summon (pink) and Materia (blue) */
private const val SIGIL_ACCENT = "#06b6d4"
