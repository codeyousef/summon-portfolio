@file:Suppress("FunctionName")

package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.HeroContent
import code.yousef.portfolio.content.model.HeroMetric
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import java.time.Year

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
        HeroNav(eyebrow = hero.eyebrow.resolve(locale))
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection("row")
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xl)
                .flexWrap("wrap")
        ) {
            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.md)
                    .style("flex", "1 1 480px")
            ) {
                Text(
                    text = primaryTitle,
                    modifier = Modifier()
                        .fontSize("clamp(38px, 7.2vw, 96px)")
                        .fontWeight(900)
                        .letterSpacing("-0.02em")
                        .style(
                            "background",
                            "linear-gradient(180deg, #fff, #dfdfea 65%, #c5c5d4)"
                        )
                        .style("-webkit-background-clip", "text")
                        .style("color", "transparent")
                        .style("text-shadow", "0 1px 0 #ffffff33")
                )
                Text(
                    text = secondaryTitle,
                    modifier = Modifier()
                        .fontSize("clamp(32px, 6vw, 72px)")
                        .fontWeight(700)
                        .letterSpacing("-0.02em")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
                Paragraph(
                    text = subtitle,
                    modifier = Modifier()
                        .color("#cfcfe0")
                        .fontSize("clamp(16px, 2.2vw, 22px)")
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
private fun HeroNav(eyebrow: String) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(PortfolioTheme.Spacing.md)
            .flexWrap("wrap")
            .padding(PortfolioTheme.Spacing.md)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("background", PortfolioTheme.Gradients.GLASS)
            .boxShadow(PortfolioTheme.Shadows.LOW)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            LogoOrb()
            Column {
                Text(
                    text = "Yousef Baitalmal",
                    modifier = Modifier().fontWeight(800)
                )
                Text(
                    text = eyebrow,
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontSize("0.85rem")
                )
            }
            GlassPill(text = "Summon", emphasize = true)
        }
        GlassPill(
            text = "Palette ▸ Maroon",
            modifier = Modifier()
                .id("palette")
                .cursor("pointer")
                .attribute("data-palette", "toggle")
        )
    }
}

@Composable
private fun LogoOrb() {
    Box(
        modifier = Modifier()
            .style("width", "38px")
            .style("height", "38px")
            .borderRadius("14px")
            .style(
                "background",
                "radial-gradient(120% 120% at 30% 20%, ${PortfolioTheme.Colors.ACCENT_ALT}," +
                        " ${PortfolioTheme.Colors.ACCENT} 35%, #5e0f27 60%, #14070e 100%)"
            )
            .boxShadow("inset 0 0 20px #0009, 0 10px 30px #0009")
            .position(Position.Relative)
    ) {
        Box(
            modifier = Modifier()
                .position(Position.Absolute)
                .style("inset", "-1px")
                .borderRadius("14px")
                .style(
                    "background",
                    "conic-gradient(from 210deg, #ffffff88, #ffffff00 40% 70%, #ffffff33)"
                )
                .style("filter", "blur(0.5px)")
                .style("mix-blend-mode", "overlay")
        ) {}
    }
}

@Composable
private fun GlassPill(text: String, emphasize: Boolean = false, modifier: Modifier = Modifier()) {
    Text(
        text = text,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
            .style("background", if (emphasize) PortfolioTheme.Gradients.ACCENT else PortfolioTheme.Gradients.GLASS)
            .fontWeight(if (emphasize) 800 else 600)
            .fontSize("0.85rem")
    )
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
            .flexWrap("wrap")
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
        GhostActionButton(text = "Featured Services", onClick = onRequestServices)
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
            .flexWrap("wrap")
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
            .style("flex", "1 1 220px")
            .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
    ) {
        Text(
            text = metric.value,
            modifier = Modifier()
                .fontSize("2rem")
                .fontWeight(700)
        )
        Text(
            text = metric.label.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize("0.95rem")
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
            .style("flex", "1 1 360px")
            .gap(PortfolioTheme.Spacing.md)
    ) {
        Column(
            modifier = Modifier()
                .style("aspect-ratio", "1.35 / 1")
                .borderRadius(PortfolioTheme.Radii.lg)
                .style("background", PortfolioTheme.Gradients.CARD)
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
                    .style("height", "42px")
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(PortfolioTheme.Spacing.sm)
                    .padding("0", PortfolioTheme.Spacing.md)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier()
                            .style("width", "10px")
                            .style("height", "10px")
                            .borderRadius(PortfolioTheme.Radii.pill)
                            .backgroundColor("#ffffff22")
                    ) {}
                }
                Text(
                    text = "summon/pages/Home.kt",
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                )
            }
            CodeBlock(
                lines = listOf(
                    "val Home = page(\"/\") {",
                    "  html {",
                    "    head { title(\"Yousef — Portfolio\") }",
                    "    body {",
                    "      header { h1(\"Hello from Summon\") }",
                    "      section { p(\"SSR first paint. Islands for interactivity.\") }",
                    "      clientIsland { /* mount your widget here */ }",
                    "    }",
                    "  }",
                    "}"
                )
            )
        }
    }
}

@Composable
fun QuickStartSection() {
    SectionWrap(modifier = Modifier().id("get-started")) {
        H2("Summon in 60 seconds")
        Lead("Scaffold a Ktor app, register a page, ship. You can swap Ktor for Spring/Quarkus later.")
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(280px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            CardSurface {
                Text("Install & run", modifier = Modifier().fontWeight(600))
                CodeBlock(listOf("./gradlew :apps:app:run"), showCopyButton = true)
                Lead("Open http://localhost:8080. You’ll see the Home page rendered by Summon.")
            }
            CardSurface {
                Text("Minimal page (Kotlin)", modifier = Modifier().fontWeight(600))
                CodeBlock(
                    listOf(
                        "val Home = page(\"/\") {",
                        "  html { head { title(\"Summon\") };",
                        "    body { h1(\"It works\") }",
                        "  }",
                        " }"
                    ),
                    showCopyButton = true
                )
                Lead("Register it once in your server and you’re done.")
            }
        }
    }
}

@Composable
fun FeatureSection() {
    SectionWrap(modifier = Modifier().id("features")) {
        H2("Why Summon")
        Lead("Purpose-built for Kotlin web apps that need SEO, speed, and control without a mountain of JS.")
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(240px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            FeatureCard(
                "SSR first • hydrate later",
                "Search-friendly HTML on first paint; hydrate only islands that need it."
            )
            FeatureCard("Backend-agnostic", "Ktor today. Spring/Quarkus adapters ready when you are.")
            FeatureCard("Router & SEO", "File or function-based pages, meta helpers, and sitemap hooks.")
        }
    }
}

@Composable
fun ProjectSection() {
    SectionWrap(modifier = Modifier().id("project")) {
        H2("Project: Summon")
        Lead("Roadmap: 0.2 adds hydrated response helpers, router bridge, and head/SEO templates.")
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(280px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            CardSurface {
                Text("Install (Gradle)", modifier = Modifier().fontWeight(600))
                CodeBlock(
                    listOf(
                        "repositories { mavenCentral() }",
                        "dependencies {",
                        "  implementation(\"io.github.codeyousef.summon:runtime:<ver>\")",
                        "  implementation(\"io.github.codeyousef.summon:ktor:<ver>\")",
                        "}"
                    ),
                    showCopyButton = true
                )
            }
            CardSurface {
                Text("Register routes (Ktor)", modifier = Modifier().fontWeight(600))
                CodeBlock(
                    listOf(
                        "fun Application.configureSummonPages() = routing {",
                        "  summonRouting { mount(Home) }",
                        "}"
                    ),
                    showCopyButton = true
                )
            }
        }
    }
}

@Composable
fun ContactCtaSection() {
    SectionWrap(modifier = Modifier().id("contact")) {
        H2("Get in touch")
        Lead("Want to collaborate or use Summon in production? Reach out and I’ll help you ship.")
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.sm)
                .flexWrap("wrap")
        ) {
            Button(
                onClick = null,
                label = "Copy Email",
                modifier = Modifier()
                    .id("copyEmail")
                    .style("height", "52px")
                    .padding("0", PortfolioTheme.Spacing.lg)
                    .borderRadius(PortfolioTheme.Radii.md)
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .style("background", PortfolioTheme.Gradients.ACCENT)
                    .color("#ffffff")
                    .fontWeight(800),
                dataAttributes = mapOf("copy" to "email"),
                variant = ButtonVariant.PRIMARY,
                disabled = false
            )
            ButtonLink(
                label = "View Repo",
                href = "https://github.com/codeyousef/summon",
                target = "_blank",
                dataHref = "https://github.com/codeyousef/summon",
                dataAttributes = mapOf("cta" to "contact-repo"),
                modifier = Modifier()
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .style("background", PortfolioTheme.Gradients.GLASS)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .padding("0", PortfolioTheme.Spacing.lg)
                    .style("height", "52px")
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .borderRadius(PortfolioTheme.Radii.md)
            )
            ButtonLink(
                label = "Docs (this page)",
                href = "#get-started",
                dataHref = "#get-started",
                dataAttributes = mapOf("cta" to "contact-docs"),
                modifier = Modifier()
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .style("background", PortfolioTheme.Gradients.GLASS)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .padding("0", PortfolioTheme.Spacing.lg)
                    .style("height", "52px")
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .borderRadius(PortfolioTheme.Radii.md)
            )
        }
    }
}

@Composable
fun PortfolioFooter() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexWrap("wrap")
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            listOf(
                "Get Started" to "#get-started",
                "Features" to "#features",
                "Project" to "#project",
                "Contact" to "#contact"
            ).forEach { (label, href) ->
                AnchorLink(
                    label = label,
                    href = href,
                    dataHref = href,
                    dataAttributes = mapOf("footer-link" to label.lowercase()),
                    modifier = Modifier()
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .backgroundColor("#ffffff10")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            }
        }
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            Text(text = "©")
            Text(
                text = Year.now().value.toString(),
                modifier = Modifier().id("year")
            )
            Text(text = "Yousef Baitalmal — Summon")
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String) {
    CardSurface {
        Text(title, modifier = Modifier().fontWeight(600))
        Lead(description)
    }
}

@Composable
private fun CardSurface(content: () -> Unit) {
    Column(
        modifier = Modifier()
            .backgroundColor(PortfolioTheme.Gradients.CARD)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.md)
            .boxShadow(PortfolioTheme.Shadows.LOW)
            .gap(PortfolioTheme.Spacing.sm)
            .padding(PortfolioTheme.Spacing.md)
    ) { content() }
}

@Composable
private fun H2(text: String) {
    Text(
        text = text,
        modifier = Modifier()
            .fontSize("clamp(22px, 3.6vw, 36px)")
            .fontWeight(700)
    )
}

@Composable
private fun Lead(text: String) {
    Paragraph(
        text = text,
        modifier = Modifier()
            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            .lineHeight(1.6)
    )
}

@Composable
private fun PrimaryButton(text: String, href: String, modifier: Modifier = Modifier()) {
    ButtonLink(
        label = text,
        href = href,
        dataHref = href,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .style("height", "52px")
            .style("background", PortfolioTheme.Gradients.ACCENT)
            .boxShadow("0 10px 30px rgba(176,18,53,.38), inset 0 1px 0 #ffffff77")
            .color("#ffffff")
            .padding("0", PortfolioTheme.Spacing.lg)
            .borderRadius(PortfolioTheme.Radii.md)
            .fontWeight(800)
            .letterSpacing("0.3px"),
        dataAttributes = mapOf("cta" to "hero-primary")
    )
}

@Composable
private fun GhostButton(text: String, href: String, modifier: Modifier = Modifier()) {
    ButtonLink(
        label = text,
        href = href,
        dataHref = href,
        modifier = modifier
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .style("background", PortfolioTheme.Gradients.GLASS)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .padding("0", PortfolioTheme.Spacing.lg)
            .style("height", "52px")
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .borderRadius(PortfolioTheme.Radii.md),
        dataAttributes = mapOf("cta" to "hero-secondary")
    )
}

@Composable
private fun GhostActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = { onClick() },
        label = text,
        modifier = Modifier()
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .style("background", PortfolioTheme.Gradients.GLASS)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .padding("0", PortfolioTheme.Spacing.lg)
            .style("height", "52px")
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .borderRadius(PortfolioTheme.Radii.md),
        variant = ButtonVariant.SECONDARY,
        disabled = false,
        dataAttributes = mapOf("analytics" to "hero-services")
    )
}

@Composable
private fun CodeBlock(lines: List<String>, showCopyButton: Boolean = false) {
    Column(
        modifier = Modifier()
            .backgroundColor("#0b0d12")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#ffffff14")
            .borderRadius(PortfolioTheme.Radii.md)
            .padding(PortfolioTheme.Spacing.md)
            .style("font", "500 14px/1.6 ${PortfolioTheme.Typography.FONT_MONO}")
            .style("overflow", "auto")
    ) {
        if (showCopyButton) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.FlexEnd)
                    .style("margin-bottom", PortfolioTheme.Spacing.sm)
            ) {
                Button(
                    onClick = null,
                    label = "Copy",
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor(PortfolioTheme.Gradients.GLASS)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.pill),
                    variant = ButtonVariant.SECONDARY,
                    disabled = false,
                    dataAttributes = mapOf("copy" to "code")
                )
            }
        }
        lines.forEach { line ->
            Text(line, modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY))
        }
    }
}
