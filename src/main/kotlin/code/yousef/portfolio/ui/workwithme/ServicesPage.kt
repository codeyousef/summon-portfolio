package code.yousef.portfolio.ui.workwithme

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactFooterSection
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
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.color
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

/**
 * Consulting & Services page.
 * Target Audience: Startups, Agencies, Project Managers.
 * Goal: Define offerings and streamline the scoping process.
 */
@Composable
fun ServicesPage(locale: PortfolioLocale) {
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        
        // Hero Section
        ServicesHeroSection()
        
        // Specialized Services
        SpecializedServicesSection()
        
        // Engagement Models
        EngagementModelsSection()
        
        // Why Work With Me Section
        WhyWorkWithMeSection()
        
        // Contact CTA Section
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        
        PortfolioFooter(locale = locale)
    }
}

@Composable
private fun ServicesHeroSection() {
    SectionWrap(modifier = Modifier().paddingTop(80.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.lg)) {
            // Availability Status
            Row(
                modifier = Modifier()
                    .alignItems(AlignItems.Center)
                    .gap(8.px)
                    .marginBottom(PortfolioTheme.Spacing.sm)
            ) {
                Box(
                    modifier = Modifier()
                        .width(10.px)
                        .height(10.px)
                        .borderRadius(5.px)
                        .backgroundColor("#22c55e") // green
                        .style("animation", "pulse 2s infinite")
                ) {}
                Text(
                    text = "Currently accepting new projects",
                    modifier = Modifier()
                        .fontSize(0.9.rem)
                        .fontWeight(600)
                        .color("#22c55e")
                )
            }
            
            // Headline
            Text(
                text = "Engineering Partner for High-Impact Projects",
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(900)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .lineHeight(1.2)
                    .style("text-shadow", "0 0 30px rgba(0,0,0,0.8)")
            )
            
            // Sub-headline
            Paragraph(
                text = "From complex shader effects to complete cross-platform applications, I bring deep systems expertise to your most challenging engineering problems.",
                modifier = Modifier()
                    .fontSize(1.25.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
                    .maxWidth(800.px)
            )
            
            // CTA Buttons
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.md)
                    .marginTop(PortfolioTheme.Spacing.lg)
            ) {
                ButtonLink(
                    label = "Book a Discovery Call",
                    href = "https://app.usemotion.com/meet/motion.duckling867/meeting",
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding("14px", "28px")
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .fontSize(1.rem)
                        .textDecoration(TextDecoration.None),
                    target = "_blank",
                    rel = "noopener noreferrer",
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap(),
                    navigationMode = LinkNavigationMode.Native
                )
                ButtonLink(
                    label = "View Work",
                    href = "/#projects",
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding("14px", "28px")
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(600)
                        .fontSize(1.rem)
                        .textDecoration(TextDecoration.None)
                        .border("1px", "solid", PortfolioTheme.Colors.BORDER),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap(),
                    navigationMode = LinkNavigationMode.Native
                )
            }
        }
    }
}

@Composable
private fun SpecializedServicesSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.xl)) {
            Text(
                text = "Specialized Services",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .style("grid-template-columns", "repeat(auto-fit, minmax(300px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                ServiceCard(
                    icon = "🎨",
                    title = "3D & Graphics Engineering",
                    description = "Custom WebGL/WebGPU effects, shader development, and real-time visualization. From particle systems to full 3D scenes with declarative Kotlin APIs.",
                    deliverables = listOf(
                        "Shader development (GLSL/WGSL)",
                        "Interactive 3D web experiences",
                        "Performance optimization",
                        "Cross-platform rendering pipelines"
                    )
                )
                ServiceCard(
                    icon = "📱",
                    title = "Cross-Platform Development",
                    description = "Kotlin Multiplatform applications targeting JVM, Web (Wasm), iOS, and Desktop from a single codebase with shared business logic.",
                    deliverables = listOf(
                        "KMP architecture & setup",
                        "Shared UI with Compose Multiplatform",
                        "Native platform integration",
                        "Migration from React Native/Flutter"
                    )
                )
                ServiceCard(
                    icon = "⚙️",
                    title = "Tooling & Automation",
                    description = "Developer experience improvements, CI/CD pipelines, build system optimization, and custom Gradle plugins.",
                    deliverables = listOf(
                        "Custom Gradle plugins",
                        "Build performance optimization",
                        "API design & documentation",
                        "Testing infrastructure"
                    )
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(
    icon: String,
    title: String,
    description: String,
    deliverables: List<String>
) {
    Box(
        modifier = Modifier()
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .border("1px", "solid", PortfolioTheme.Colors.BORDER)
            .height("100%")
    ) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.md)) {
            Text(
                text = icon,
                modifier = Modifier().fontSize(2.rem)
            )
            Text(
                text = title,
                modifier = Modifier()
                    .fontSize(1.2.rem)
                    .fontWeight(700)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            Paragraph(
                text = description,
                modifier = Modifier()
                    .fontSize(0.95.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            
            Column(modifier = Modifier().gap(6.px).marginTop(PortfolioTheme.Spacing.sm)) {
                Text(
                    text = "Deliverables:",
                    modifier = Modifier()
                        .fontSize(0.8.rem)
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.ACCENT)
                        .textTransform(TextTransform.Uppercase)
                        .letterSpacing("0.05em")
                )
                deliverables.forEach { item ->
                    Row(modifier = Modifier().alignItems(AlignItems.FlexStart).gap(8.px)) {
                        Text(text = "✓", modifier = Modifier().color(PortfolioTheme.Colors.ACCENT).fontWeight(700))
                        Text(
                            text = item,
                            modifier = Modifier()
                                .fontSize(0.9.rem)
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngagementModelsSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.xl)) {
            Text(
                text = "Engagement Models",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .style("grid-template-columns", "repeat(auto-fit, minmax(300px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                EngagementCard(
                    title = "Project-Based",
                    description = "Fixed-scope engagements with clear deliverables and timelines. Ideal for discrete features, prototypes, or technical spikes.",
                    features = listOf(
                        "Defined scope & milestones",
                        "Fixed price or capped hours",
                        "Regular progress updates",
                        "Documentation included"
                    ),
                    highlighted = false
                )
                EngagementCard(
                    title = "Staff Augmentation",
                    description = "Embedded as part of your team for ongoing work. Best for longer engagements where deep context matters.",
                    features = listOf(
                        "Part of your daily standups",
                        "Weekly/monthly commitment",
                        "Code reviews & mentoring",
                        "Architecture guidance"
                    ),
                    highlighted = true
                )
            }
        }
    }
}

@Composable
private fun EngagementCard(
    title: String,
    description: String,
    features: List<String>,
    highlighted: Boolean
) {
    Box(
        modifier = Modifier()
            .backgroundColor(if (highlighted) PortfolioTheme.Colors.SURFACE_STRONG else PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .border(
                "2px",
                "solid",
                if (highlighted) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.BORDER
            )
    ) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.md)) {
            if (highlighted) {
                Text(
                    text = "RECOMMENDED",
                    modifier = Modifier()
                        .fontSize(0.7.rem)
                        .fontWeight(700)
                        .color(PortfolioTheme.Colors.ACCENT)
                        .letterSpacing("0.1em")
                )
            }
            Text(
                text = title,
                modifier = Modifier()
                    .fontSize(1.3.rem)
                    .fontWeight(700)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            Paragraph(
                text = description,
                modifier = Modifier()
                    .fontSize(0.95.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            
            Column(modifier = Modifier().gap(8.px).marginTop(PortfolioTheme.Spacing.sm)) {
                features.forEach { feature ->
                    Row(modifier = Modifier().alignItems(AlignItems.Center).gap(8.px)) {
                        Text(text = "→", modifier = Modifier().color(PortfolioTheme.Colors.ACCENT).fontWeight(700))
                        Text(
                            text = feature,
                            modifier = Modifier()
                                .fontSize(0.9.rem)
                                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhyWorkWithMeSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.xl)) {
            Text(
                text = "Why Work With Me",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .style("grid-template-columns", "repeat(auto-fit, minmax(250px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                WhyCard(
                    title = "Proven Track Record",
                    description = "Author of 3 production-grade open-source libraries. I ship code that others depend on."
                )
                WhyCard(
                    title = "Clear Communication",
                    description = "Regular updates, detailed documentation, and no surprises. You'll always know project status."
                )
                WhyCard(
                    title = "Quality Focus",
                    description = "Production-ready code with tests, CI/CD, and documentation. Not just \"working\" — maintainable."
                )
                WhyCard(
                    title = "Full Context",
                    description = "I understand the full stack from GPU shaders to REST APIs. No black boxes."
                )
            }
        }
    }
}

@Composable
private fun WhyCard(title: String, description: String) {
    Column(modifier = Modifier().gap(8.px)) {
        Text(
            text = title,
            modifier = Modifier()
                .fontSize(1.05.rem)
                .fontWeight(700)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        Paragraph(
            text = description,
            modifier = Modifier()
                .fontSize(0.9.rem)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.5)
        )
    }
}
