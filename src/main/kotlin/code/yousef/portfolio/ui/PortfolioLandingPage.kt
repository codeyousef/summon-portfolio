package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import code.yousef.summon.modifier.WhiteSpace
import code.yousef.summon.modifier.cssClamp
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.extensions.vw

private const val SUMMON_URL = "https://summon.yousef.codes"

@Composable
fun PortfolioLandingPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    if (servicesModalOpen) {
        // Legacy query param now simply highlights the contact section by ensuring it stays rendered below.
    }
    val summonProjectTitle = content.projects.firstOrNull { it.slug == "summon-framework" }
        ?.title
        ?.resolve(locale)
        ?: "Summon"

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        HeroBand()
        WhatIBuildSection()
        WhyWorkWithMeSection()
        FeaturedProjectSection(projectName = summonProjectTitle)
        ProcessSection()
        TestimonialSection()
        ContactCtaSection(locale)
        PortfolioFooter(locale = locale)
    }
}

@Composable
private fun HeroBand() {
    SectionWrap(modifier = Modifier().id("hero")) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "I design & build high-performance websites and mobile apps.",
                modifier = Modifier()
                    .fontSize(cssClamp(42.px, 6.vw, 76.px))
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
            Paragraph(
                text = "I’m Yousef — a developer who creates fast, modern digital products that look great, feel smooth, and work everywhere: web, iOS, Android, and desktop.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.25.rem)
                    .lineHeight(1.6)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                PrimaryCtaButton(
                    text = "Start your project",
                    href = "/contact",
                    modifier = Modifier()
                        .minWidth("200px")
                        .whiteSpace(WhiteSpace.NoWrap)
                )
                SecondaryCtaButton(
                    text = "See my framework",
                    href = SUMMON_URL,
                    modifier = Modifier()
                        .minWidth("220px")
                        .whiteSpace(WhiteSpace.NoWrap)
                )
            }
            Paragraph(
                text = "Trusted by developers and creatives — I built Summon, a custom UI framework used to power fast, responsive apps.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun WhatIBuildSection() {
    SectionWrap(modifier = Modifier().id("build")) {
        SectionHeading(
            eyebrow = "What I build",
            title = "Web, mobile, desktop — one cohesive experience."
        )
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            buildCapabilities.forEach { item ->
                Column(
                    modifier = Modifier()
                        .flex(grow = 1, shrink = 1, basis = "280px")
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .background(PortfolioTheme.Gradients.CARD)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier()
                            .fontWeight(700)
                            .fontSize(1.1.rem)
                    )
                    Paragraph(
                        text = item.description,
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}

@Composable
private fun WhyWorkWithMeSection() {
    SectionWrap(modifier = Modifier().id("why")) {
        SectionHeading(
            eyebrow = "Why work with me",
            title = "One developer. Every platform. Same quality."
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            reasonsToWorkWithMe.forEach { item ->
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.sm)
                        .alignItems(AlignItems.FlexStart)
                ) {
                    Text(text = item.emoji, modifier = Modifier().fontSize(1.5.rem))
                    Column(
                        modifier = Modifier()
                            .gap(PortfolioTheme.Spacing.xs)
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier().fontWeight(700)
                        )
                        Paragraph(
                            text = item.description,
                            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }
            Paragraph(
                text = "I build with React/Next.js and Kotlin Multiplatform — the same tools used by companies like Netflix, JetBrains, and Google.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .style("font-style", "italic")
            )
        }
    }
}

@Composable
private fun FeaturedProjectSection(projectName: String) {
    SectionWrap(modifier = Modifier().id("featured")) {
        Box(
            modifier = Modifier()
                .borderRadius(PortfolioTheme.Radii.lg)
                .backgroundLayers {
                    linearGradient {
                        direction("135deg")
                        colorStop("#ff5b8d", "0%")
                        colorStop("#ff784c", "100%")
                    }
                }
                .padding(PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = "Built the tools I use.",
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 4.vw, 48.px))
                        .fontWeight(800)
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                )
                Paragraph(
                    text = "I created Summon, a modern UI framework that makes websites load faster, perform better, and scale cleanly across devices. It’s the same engineering mindset I bring to client projects.",
                    modifier = Modifier()
                        .color("#1c0d11")
                        .fontWeight(600)
                )
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexWrap(FlexWrap.Wrap)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    PrimaryCtaButton(
                        text = "Explore $projectName",
                        href = SUMMON_URL
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessSection() {
    SectionWrap(modifier = Modifier().id("process")) {
        SectionHeading(
            eyebrow = "Process",
            title = "From idea to launch, I make it simple."
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            processSteps.forEach { step ->
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .gap(PortfolioTheme.Spacing.md)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .padding(PortfolioTheme.Spacing.md)
                ) {
                    Text(
                        text = step.number.toString(),
                        modifier = Modifier()
                            .fontWeight(800)
                            .fontSize(1.5.rem)
                    )
                    Column(
                        modifier = Modifier()
                            .gap(PortfolioTheme.Spacing.xs)
                    ) {
                        Text(
                            text = step.title,
                            modifier = Modifier().fontWeight(700)
                        )
                        Paragraph(
                            text = step.description,
                            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestimonialSection() {
    SectionWrap(modifier = Modifier().id("testimonial")) {
        Column(
            modifier = Modifier()
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .background(PortfolioTheme.Gradients.GLASS)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = "Testimonials",
                modifier = Modifier().fontWeight(700)
            )
            Paragraph(
                text = "“Yousef built our website from scratch. It’s fast, clean, and exactly what we wanted.”",
                modifier = Modifier()
                    .fontSize(1.3.rem)
                    .lineHeight(1.5)
            )
            Paragraph(
                text = "— Client Name, Company Name",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun ContactCtaSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("contact")) {
        Column(
            modifier = Modifier()
                .borderRadius(PortfolioTheme.Radii.lg)
                .background(PortfolioTheme.Gradients.CARD)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = "Let’s build something great.",
                modifier = Modifier()
                    .fontSize(cssClamp(32.px, 4.vw, 48.px))
                    .fontWeight(800)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = "Have an idea or project in mind? I’ll help you bring it to life — fast, clean, and cross-platform from day one.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Paragraph(
                text = "No agencies, no outsourcing — you’ll work directly with me.",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
    ContactSection(locale = locale)
}

@Composable
private fun SectionHeading(
    eyebrow: String,
    title: String
) {
    Column(
        modifier = Modifier()
            .gap(PortfolioTheme.Spacing.xs)
    ) {
        Text(
            text = eyebrow.uppercase(),
            modifier = Modifier()
                .fontSize(0.85.rem)
                .letterSpacing("0.3em")
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Text(
            text = title,
            modifier = Modifier()
                .fontSize(cssClamp(32.px, 4.vw, 48.px))
                .fontWeight(800)
                .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
        )
    }
}

@Composable
private fun PrimaryCtaButton(text: String, href: String, modifier: Modifier = Modifier()) {
    ButtonLink(
        label = text,
        href = href,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .height(56.px)
            .padding("0", PortfolioTheme.Spacing.lg)
            .borderRadius(PortfolioTheme.Radii.lg)
            .background(PortfolioTheme.Gradients.ACCENT)
            .color("#ffffff")
            .textDecoration("none")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.ACCENT_ALT)
            .boxShadow("0 18px 40px rgba(255,70,104,0.45)")
            .fontWeight(800)
            .letterSpacing("-0.01em"),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("cta" to text.lowercase()),
        navigationMode = LinkNavigationMode.Native
    )
}

@Composable
private fun SecondaryCtaButton(
    text: String,
    href: String,
    modifier: Modifier = Modifier(),
    openInNewTab: Boolean = true
) {
    val targetAttr = if (openInNewTab) "_blank" else null
    val relAttr = if (openInNewTab) "noopener" else null
    ButtonLink(
        label = text,
        href = href,
        modifier = modifier
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.TEXT_SECONDARY)
            .borderRadius(PortfolioTheme.Radii.lg)
            .height(56.px)
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .padding("0", PortfolioTheme.Spacing.lg)
            .textDecoration("none")
            .backgroundColor("rgba(255,255,255,0.03)")
            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            .boxShadow("0 10px 30px rgba(0,0,0,0.25)"),
        target = targetAttr,
        rel = relAttr,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("cta" to text.lowercase()),
        navigationMode = LinkNavigationMode.Native
    )
}

private data class BuildCapability(val title: String, val description: String)

private val buildCapabilities = listOf(
    BuildCapability(
        title = "Websites & Web Apps",
        description = "Fast-loading, responsive websites that feel as smooth as apps — perfect for businesses, startups, and creators."
    ),
    BuildCapability(
        title = "Mobile Apps (iOS & Android)",
        description = "One app that runs beautifully on both platforms — no need for two codebases."
    ),
    BuildCapability(
        title = "Desktop & Cross-Platform Tools",
        description = "Powerful desktop or internal tools that share code across web, mobile, and desktop — consistent and efficient."
    ),
    BuildCapability(
        title = "Custom Systems & Dashboards",
        description = "Admin panels, analytics tools, or full product dashboards tailored to your workflow."
    )
)

private data class Reason(val emoji: String, val title: String, val description: String)

private val reasonsToWorkWithMe = listOf(
    Reason("⚡", "Fast & Reliable", "Your app feels instant, loads fast, and runs smoothly."),
    Reason("🧩", "Consistent Experience", "Looks and feels right on every device — web, mobile, or desktop."),
    Reason("🛠️", "Built for Growth", "Clean code, scalable design systems, and easy maintenance."),
    Reason("🎯", "End-to-End", "I handle design, development, deployment, and support — start to finish.")
)

private data class ProcessStep(val number: Int, val title: String, val description: String)

private val processSteps = listOf(
    ProcessStep(1, "Discovery Call", "We talk about your goals and map out what you actually need."),
    ProcessStep(2, "Proposal & Plan", "You’ll get a clear scope, timeline, and fixed quote."),
    ProcessStep(3, "Design & Build", "You’ll see progress weekly — no mystery."),
    ProcessStep(4, "Launch & Support", "Once live, I stay available for updates or scaling.")
)
