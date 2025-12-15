package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.i18n.strings.PortfolioStrings
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.sigilMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.components.CodeBlock
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
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
import codes.yousef.summon.runtime.LocalPlatformRenderer
import codes.yousef.summon.runtime.rememberMutableStateOf

@Composable
fun PortfolioLandingPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val servicesModalState = rememberMutableStateOf(servicesModalOpen)
    val openServicesModal = { servicesModalState.value = true }
    val closeServicesModal = { servicesModalState.value = false }

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Box(modifier = Modifier().height(PortfolioTheme.Spacing.xxl)) {}
        
        // 2. Hero Section (The Hook)
        HeroSection(locale)
        
        // 3. The Trinity Showcase (The Core Value)
        TrinityShowcase(locale)
        
        // 4. The Philosophy (Manifesto)
        PhilosophySection(locale)
        
        // 5. Selected Engineering (The Products)
        SelectedEngineeringSection(locale)
        
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
        ServicesOverlay(
            open = servicesModalState.value,
            services = content.services,
            locale = locale,
            contactHref = "#contact",
            onClose = closeServicesModal
        )
        StructuredDataSnippet()
    }
}

// =============================================================================
// 2. HERO SECTION
// =============================================================================

@Composable
private fun HeroSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("hero")) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xl)
                .paddingTop(PortfolioTheme.Spacing.xxl)
                .paddingBottom(PortfolioTheme.Spacing.xxl)
        ) {
            // Headline
            Text(
                text = PortfolioStrings.Hero.headline.resolve(locale),
                modifier = Modifier()
                    .fontSize(cssClamp(42.px, 7.vw, 84.px))
                    .fontWeight(900)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                    .backgroundLayers {
                        linearGradient {
                            direction("90deg")
                            colorStop("#ffffff", "0%")
                            colorStop("#aeefff", "100%")
                        }
                    }
                    .backgroundClipText()
                    .color("transparent")
                    .letterSpacing("-0.02em")
            )
            
            // Sub-headline
            Paragraph(
                text = PortfolioStrings.Hero.subheadline.resolve(locale),
                modifier = Modifier()
                    .color("rgba(255,255,255,0.88)")
                    .fontSize(cssClamp(18.px, 2.5.vw, 26.px))
                    .lineHeight(1.6)
                    .fontWeight(500)
                    .maxWidth(700.px)
            )
            
            // CTAs
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.md)
                    .flexWrap(FlexWrap.Wrap)
                    .marginTop(PortfolioTheme.Spacing.md)
            ) {
                // Primary CTA: View the Stack
                ButtonLink(
                    label = PortfolioStrings.Hero.viewStack.resolve(locale),
                    href = "#trinity",
                    modifier = Modifier()
                        .display(Display.InlineFlex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.Center)
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .textDecoration(TextDecoration.None)
                        .fontWeight(600)
                        .letterSpacing("-0.01em")
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Client,
                    dataAttributes = mapOf("cta" to "view-stack"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                
                // Secondary CTA: Source Code (GitHub)
                ButtonLink(
                    label = PortfolioStrings.Hero.sourceCode.resolve(locale),
                    href = "https://github.com/codeyousef",
                    modifier = Modifier()
                        .display(Display.InlineFlex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.Center)
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .backgroundColor("rgba(255,255,255,0.03)")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .textDecoration(TextDecoration.None)
                        .fontWeight(600)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    target = "_blank",
                    rel = "noopener",
                    dataAttributes = mapOf("cta" to "source-code"),
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

// =============================================================================
// 3. THE TRINITY SHOWCASE
// =============================================================================

@Composable
private fun TrinityShowcase(locale: PortfolioLocale) {
    ContentSection(modifier = Modifier().id("trinity")) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xxl)
        ) {
            // Block A: Summon (The Interface)
            Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(PortfolioTheme.Spacing.sm)) {
                Text(
                    text = "Summon",
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 5.vw, 48.px))
                        .fontWeight(800)
                        .color("#4ECDC4")
                )
                TrinityBlock(
                    tagline = PortfolioStrings.Trinity.summonTagline.resolve(locale),
                    description = PortfolioStrings.Trinity.summonDescription.resolve(locale),
                    techBadges = listOf("Kotlin", "Wasm", "Reactive"),
                    codeSnippet = SUMMON_CODE_SNIPPET,
                    imageOnLeft = false,
                    accentColor = "#4ECDC4"
                )
            }

            // Block B: Sigil (The Bridge)
            Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(PortfolioTheme.Spacing.sm)) {
                Text(
                    text = "Sigil",
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 5.vw, 48.px))
                        .fontWeight(800)
                        .color("#A855F7")
                )
                TrinityBlock(
                    tagline = PortfolioStrings.Trinity.sigilTagline.resolve(locale),
                    description = PortfolioStrings.Trinity.sigilDescription.resolve(locale),
                    techBadges = listOf("Declarative 3D", "Compose", "Multiplatform"),
                    codeSnippet = SIGIL_CODE_SNIPPET,
                    imageOnLeft = true,
                    accentColor = "#A855F7"
                )
            }

            // Block C: Materia (The Engine)
            Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(PortfolioTheme.Spacing.sm)) {
                Text(
                    text = "Materia",
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 5.vw, 48.px))
                        .fontWeight(800)
                        .color("#FF6B6B")
                )
                TrinityBlock(
                    tagline = PortfolioStrings.Trinity.materiaTagline.resolve(locale),
                    description = PortfolioStrings.Trinity.materiaDescription.resolve(locale),
                    techBadges = listOf("Vulkan", "Metal", "WebGL"),
                    codeSnippet = MATERIA_ARCHITECTURE_SNIPPET,
                    imageOnLeft = false,
                    accentColor = "#FF6B6B"
                )
            }
        }
    }
}

@Composable
private fun TrinityBlock(
    tagline: String,
    description: String,
    techBadges: List<String>,
    codeSnippet: String,
    imageOnLeft: Boolean,
    accentColor: String
) {
    val direction = if (imageOnLeft) FlexDirection.Row else FlexDirection.RowReverse
    
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(direction)
            .alignItems(AlignItems.Center)
            .gap(PortfolioTheme.Spacing.xxl)
            .flexWrap(FlexWrap.Wrap)
    ) {
        // Code/Visual side
        Box(
            modifier = Modifier()
                .flex(grow = 1, shrink = 1, basis = "480px")
                .minWidth(320.px)
        ) {
            CodeBlock(
                lines = codeSnippet.lines(),
                showCopyButton = false
            )
        }
        
        // Copy side
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
                .flex(grow = 1, shrink = 1, basis = "400px")
                .minWidth(280.px)
        ) {
            Text(
                text = tagline,
                modifier = Modifier()
                    .fontSize(cssClamp(24.px, 4.vw, 36.px))
                    .fontWeight(700)
                    .color(accentColor)
            )
            Paragraph(
                text = description,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.1.rem)
                    .lineHeight(1.7)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                techBadges.forEach { badge ->
                    TechBadge(text = badge, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun TechBadge(text: String, accentColor: String) {
    Box(
        modifier = Modifier()
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderRadius(PortfolioTheme.Radii.pill)
            .backgroundColor("${accentColor}22")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("${accentColor}44")
    ) {
        Text(
            text = text,
            modifier = Modifier()
                .fontSize(0.75.rem)
                .fontWeight(600)
                .color(accentColor)
                .letterSpacing("0.05em")
        )
    }
}

// =============================================================================
// 4. THE PHILOSOPHY (MANIFESTO)
// =============================================================================

@Composable
private fun PhilosophySection(locale: PortfolioLocale) {
    ContentSection(
        surface = false,
        modifier = Modifier()
            .backgroundColor("#0a0a0f")
            .paddingTop(PortfolioTheme.Spacing.scale(16))
            .paddingBottom(PortfolioTheme.Spacing.scale(16))
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
                .maxWidth(800.px)
                .marginLeft("auto")
                .marginRight("auto")
                .textAlign(TextAlign.Center)
        ) {
            Text(
                text = PortfolioStrings.Philosophy.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(cssClamp(28.px, 5.vw, 48.px))
                    .fontWeight(700)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = PortfolioStrings.Philosophy.body.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.2.rem)
                    .lineHeight(1.8)
            )
        }
    }
}

// =============================================================================
// 5. SELECTED ENGINEERING (THE PRODUCTS)
// =============================================================================

@Composable
private fun SelectedEngineeringSection(locale: PortfolioLocale) {
    ContentSection(modifier = Modifier().id("projects")) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            Text(
                text = PortfolioStrings.Engineering.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.rem)
                    .fontWeight(700)
            )
            
            // Grid of 3 cards
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.lg)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                EngineeringCard(
                    locale = locale,
                    logoSrc = "/static/summon-logo.png",
                    title = "Summon",
                    subtitle = PortfolioStrings.Engineering.summonSubtitle.resolve(locale),
                    docsHref = "${summonMarketingUrl()}/docs",
                    githubHref = "https://github.com/codeyousef/summon",
                    accentColor = "#4ECDC4"
                )
                EngineeringCard(
                    locale = locale,
                    logoSrc = "/static/sigil-logo.png",
                    title = "Sigil",
                    subtitle = PortfolioStrings.Engineering.sigilSubtitle.resolve(locale),
                    docsHref = "${sigilMarketingUrl()}/docs",
                    githubHref = "https://github.com/codeyousef/sigil",
                    accentColor = "#A855F7"
                )
                EngineeringCard(
                    locale = locale,
                    logoSrc = "/static/materia-logo.png",
                    title = "Materia",
                    subtitle = PortfolioStrings.Engineering.materiaSubtitle.resolve(locale),
                    docsHref = "${materiaMarketingUrl()}/docs",
                    githubHref = "https://github.com/codeyousef/materia",
                    accentColor = "#FF6B6B"
                )
            }
        }
    }
}

@Composable
private fun EngineeringCard(
    locale: PortfolioLocale,
    logoSrc: String,
    title: String,
    subtitle: String,
    docsHref: String,
    githubHref: String,
    accentColor: String
) {
    Column(
        modifier = Modifier()
            .flex(grow = 1, shrink = 1, basis = "300px")
            .minWidth(280.px)
            .padding(PortfolioTheme.Spacing.xl)
            .borderRadius(PortfolioTheme.Radii.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Image(
                src = logoSrc,
                alt = "$title logo",
                modifier = Modifier()
                    .width(48.px)
                    .height(48.px)
                    .style("object-fit", "contain")
            )
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(2.px)
            ) {
                Text(
                    text = title,
                    modifier = Modifier()
                        .fontSize(1.25.rem)
                        .fontWeight(700)
                )
                Text(
                    text = subtitle,
                    modifier = Modifier()
                        .fontSize(0.875.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
            }
        }
        
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.sm)
                .marginTop(PortfolioTheme.Spacing.sm)
        ) {
            AnchorLink(
                label = PortfolioStrings.Engineering.docs.resolve(locale),
                href = docsHref,
                modifier = Modifier()
                    .color(accentColor)
                    .fontWeight(600)
                    .fontSize(0.875.rem)
                    .textDecoration(TextDecoration.None)
                    .hover(Modifier().textDecoration(TextDecoration.Underline)),
                navigationMode = LinkNavigationMode.Native,
                target = "_blank",
                rel = "noopener",
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null,
                dataAttributes = emptyMap()
            )
            Text(
                text = "|",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY).opacity(0.5F)
            )
            AnchorLink(
                label = PortfolioStrings.Engineering.github.resolve(locale),
                href = githubHref,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontWeight(500)
                    .fontSize(0.875.rem)
                    .textDecoration(TextDecoration.None)
                    .hover(Modifier().textDecoration(TextDecoration.Underline)),
                navigationMode = LinkNavigationMode.Native,
                target = "_blank",
                rel = "noopener",
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

// =============================================================================
// CODE SNIPPETS
// =============================================================================

private val SUMMON_CODE_SNIPPET = """
@Composable
fun UserCard(user: User) {
    Card {
        Row {
            Avatar(src = user.avatarUrl)
            Column {
                Text(user.name, style = Title)
                Text(user.email, style = Subtitle)
            }
        }
        Button("View Profile") {
            navigate("/users/${'$'}{user.id}")
        }
    }
}
""".trimIndent()

private val SIGIL_CODE_SNIPPET = """
@Composable
fun Scene() {
    AmbientLight(intensity = 0.5f)
    PointLight(
        position = vec3(10f, 10f, 10f),
        color = Color.White
    )
    Mesh(geometry = BoxGeometry()) {
        StandardMaterial(
            color = Color.Blue,
            metalness = 0.5f
        )
    }
}
""".trimIndent()

private val MATERIA_ARCHITECTURE_SNIPPET = """
// Unified rendering pipeline
val engine = MateriaEngine {
    backend = auto() // Vulkan, Metal, or WebGL
    features {
        hdr = true
        shadows = ShadowQuality.High
        antialiasing = MSAA(4)
    }
}

// Same code, every platform
engine.render(scene) {
    camera = perspectiveCamera
    postProcess = bloom + tonemap
}
""".trimIndent()

// =============================================================================
// STRUCTURED DATA
// =============================================================================

@Composable
private fun StructuredDataSnippet() {
    val renderer = runCatching { LocalPlatformRenderer.current }.getOrNull() ?: return
    val schema = """
        {
          "@context": "https://schema.org",
          "@type": "Person",
          "name": "Yousef",
          "url": "https://dev.yousef.codes",
          "sameAs": [
            "https://www.linkedin.com/in/yousef-baitalmal/",
            "https://github.com/codeyousef",
            "https://x.com/deepissuemassaj"
          ],
          "knowsAbout": ["Kotlin", "Compose Multiplatform", "WebGPU", "Graphics Programming", "Framework Architecture"],
          "jobTitle": "Framework Architect",
          "hasOfferCatalog": {
            "@type": "OfferCatalog",
            "name": "Engineering Services",
            "itemListElement": [
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Framework Development"}},
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Graphics Engine Consulting"}},
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Kotlin Multiplatform Architecture"}}
            ]
          }
        }
    """.trimIndent()
    renderer.renderHeadElements {
        script(
            src = null,
            content = schema,
            type = "application/ld+json",
            async = false,
            defer = false,
            crossorigin = null
        )
    }
}
