package code.yousef.portfolio.ui.aether

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
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*

@Composable
fun AetherLandingPage(
    docsUrl: String,
    apiReferenceUrl: String
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        LandingNavbar(
            branding = LandingBranding.aether(docsUrl, apiReferenceUrl)
        )
        AetherHero(docsUrl, apiReferenceUrl)
        AetherFeatureGrid()
        AetherCodeExample(docsUrl)
        SummonIntegrationCallout()
        AetherCtaFooter(docsUrl, apiReferenceUrl)
        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun AetherHero(docsUrl: String, apiReferenceUrl: String) {
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
                    src = "/static/aether-logo.png",
                    alt = "Aether Logo",
                    modifier = Modifier()
                        .width(cssClamp(48.px, 10.vw, 96.px))
                        .height(cssClamp(48.px, 10.vw, 96.px))
                        .style("object-fit", "contain")
                )
                Text(
                    text = "Aether",
                    modifier = Modifier()
                        .fontSize(cssClamp(48.px, 8.vw, 96.px))
                        .fontWeight(900)
                        .letterSpacing("-0.02em")
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                )
            }
            Paragraph(
                text = "Write Once, Deploy Anywhere. A Django-inspired Kotlin Multiplatform web framework targeting JVM (Vert.x + Virtual Threads) and WebAssembly. Build robust APIs and full-stack apps with familiar patterns.",
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
                        .backgroundColor(AETHER_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("aether-cta" to "docs"),
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
                    dataAttributes = mapOf("aether-cta" to "api"),
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
                text = "Aether combines the developer experience of Django with the performance of Kotlin and Vert.x virtual threads. Deploy the same codebase to JVM servers or WebAssembly runtimes like Cloudflare Workers.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

private data class Feature(val title: String, val description: String)

private val aetherFeatures = listOf(
    Feature(
        title = "Multiplatform Deployment",
        description = "Target JVM with Vert.x + Virtual Threads and WebAssembly (Cloudflare Workers/Browser) from a single Kotlin codebase. No rewrites, no compromises."
    ),
    Feature(
        title = "Type-Safe Routing",
        description = "Radix tree routing with parameter extraction and compile-time guarantees. Define routes once, get full type safety across your application."
    ),
    Feature(
        title = "Active Record ORM",
        description = "Django-inspired model system with automatic query generation and KSP-powered migrations. Write idiomatic Kotlin, get production-ready database access."
    ),
    Feature(
        title = "Built-in Security",
        description = "CSRF protection, session management, and JWT/Bearer/API Key authentication out of the box. Security defaults that keep your app safe."
    )
)

@Composable
private fun AetherFeatureGrid() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            aetherFeatures.forEach { feature ->
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
private fun AetherCodeExample(docsUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Get started in just a few lines",
                modifier = Modifier()
                    .fontSize(1.5.rem)
                    .fontWeight(700)
            )
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
                    text = """fun main() = aether {
    // Type-safe routing with parameter extraction
    get("/users/{id}") { exchange ->
        val id = exchange.pathParam("id")
        val user = User.findById(id) // Active Record ORM
        exchange.respondJson(user)
    }

    post("/users") { exchange ->
        val body = exchange.receiveJson<CreateUserRequest>()
        val user = User.create(body)
        exchange.respondJson(user, status = 201)
    }

    // Built-in auth middleware
    authenticate(JwtAuth) {
        get("/me") { exchange ->
            val user = exchange.user<User>()
            exchange.respondJson(user)
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
                text = "Aether handles routing, serialization, authentication, and database access automatically. Just describe your API and let the framework handle the boilerplate.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
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
                .background("linear-gradient(135deg, rgba(124,58,237,0.1) 0%, transparent 100%)")
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
                    text = "Aether pairs naturally with Summon, the Kotlin Multiplatform UI framework. Serve SSR pages from Aether routes and hydrate them client-side with Summon's Compose-style components.",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )
                AnchorLink(
                    label = "Learn more about Summon →",
                    href = summonMarketingUrl(),
                    modifier = Modifier()
                        .color(AETHER_ACCENT)
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
private fun AetherCtaFooter(docsUrl: String, apiReferenceUrl: String) {
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
                text = "Ready to build with Aether?",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(700)
                    .textAlign(TextAlign.Center)
            )
            Paragraph(
                text = "Get started with Aether today and build production-ready Kotlin Multiplatform web applications.",
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
                        .backgroundColor(AETHER_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("aether-cta" to "footer-docs"),
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
                    href = "https://github.com/codeyousef/Aether",
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
                    dataAttributes = mapOf("aether-cta" to "github"),
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

/** Aether accent color - violet/purple, distinct from pink (Summon), blue (Materia), and teal (Sigil) */
private const val AETHER_ACCENT = "#7c3aed"
