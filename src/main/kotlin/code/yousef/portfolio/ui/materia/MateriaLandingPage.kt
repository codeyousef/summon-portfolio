package code.yousef.portfolio.ui.materia

import code.yousef.portfolio.i18n.PortfolioLocale
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
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*
import java.net.URI

@Composable
fun MateriaLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        LandingNavbar(
            branding = LandingBranding.materia(docsUrl, apiReferenceUrl)
        )
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
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Image(
                    src = "/static/materia-logo.png",
                    alt = "Materia",
                    modifier = Modifier()
                        .width(cssClamp(64.px, 12.vw, 128.px))
                        .height(cssClamp(64.px, 12.vw, 128.px))
                )
                Text(
                    text = "Materia",
                    modifier = Modifier()
                        .fontSize(cssClamp(48.px, 8.vw, 96.px))
                        .fontWeight(900)
                        .letterSpacing("-0.02em")
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                )
            }
            Paragraph(
                text = "Kotlin Multiplatform 3D graphics library with WebGPU/Vulkan backends. Write 3D apps once, deploy on JVM, Web, Android, iOS & Native.",
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
                text = "Materia provides Three.js-equivalent capabilities for Kotlin with type-safe math, scene graph, materials, lighting, and more—all with seamless multiplatform deployment.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

private data class Feature(val title: String, val description: String)

private val materiaFeatures = listOf(
    Feature(
        title = "WebGPU & Vulkan",
        description = "Modern graphics backends with automatic fallback. High-performance rendering across all platforms."
    ),
    Feature(
        title = "Type-safe 3D Math",
        description = "Vectors, matrices, quaternions, and transforms with full Kotlin type safety and operator overloading."
    ),
    Feature(
        title = "Scene Graph & Materials",
        description = "Hierarchical scene management, PBR materials, lighting systems, and camera controls out of the box."
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
                    text = "Full 3D toolkit",
                    modifier = Modifier()
                        .fontWeight(700)
                        .fontSize(1.2.rem)
                )
                Paragraph(
                    text = "Materia includes meshes, geometries, textures, shaders, lights, cameras, and post-processing effects—everything you need for 3D graphics.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                Paragraph(
                    text = "Explore the full API and examples in the docs.",
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
// Create scene and camera
val scene = Scene()
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 0.1f,
    far = 1000f
).apply {
    position.set(0f, 2f, 5f)
    lookAt(Vector3.ZERO)
}

// Create geometry and material
val geometry = BoxGeometry(1f, 1f, 1f)
val material = MeshStandardMaterial(
    color = Color(0f, 1f, 0f),
    metalness = 0.3f,
    roughness = 0.4f
)

// Create mesh and add to scene
val cube = Mesh(geometry, material)
scene.add(cube)

// Render
renderer.render(scene, camera)
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
                text = "Building web apps with Kotlin?",
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.2.rem)
            )
            Paragraph(
                text = "Check out Summon — a Kotlin Multiplatform frontend framework for high-performance apps across JVM, JS, and WASM with a single codebase.",
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
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .backgroundColor("transparent")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .textDecoration(TextDecoration.None),
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
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .backgroundColor("transparent")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .textDecoration(TextDecoration.None),
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
                ButtonLink(
                    label = "Follow on X →",
                    href = "https://x.com/DeepIssueMassaj",
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .backgroundColor("transparent")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .textDecoration(TextDecoration.None),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("materia-cta" to "x-follow"),
                    target = "_blank",
                    rel = "noopener noreferrer",
                    title = null,
                    id = null,
                    ariaLabel = "Follow on X (Twitter)",
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
