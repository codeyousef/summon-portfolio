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

/**
 * Full-Time Opportunities page.
 * Target Audience: CTOs, Staff/Principal Engineers, Technical Recruiters.
 * Goal: Prove you are an "Asset," not just a "Resource."
 */
@Composable
fun FullTimePage(locale: PortfolioLocale) {
    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        
        // Hero Section
        FullTimeHeroSection()
        
        // The "Unfair Advantage" Section
        UnfairAdvantageSection()
        
        // Technical Proficiency Section
        TechnicalProficiencySection()
        
        // Ideal Role Criteria Section
        IdealRoleCriteriaSection()
        
        // Deployment & Docs Section
        DeploymentDocsSection()
        
        // Contact CTA Section
        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        
        PortfolioFooter(locale = locale)
    }
}

@Composable
private fun FullTimeHeroSection() {
    SectionWrap(modifier = Modifier().paddingTop(80.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.lg)) {
            // Headline
            Text(
                text = "I don't just use libraries. I build the ecosystem.",
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(900)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .lineHeight(1.2)
                    .style("text-shadow", "0 0 30px rgba(0,0,0,0.8)")
            )
            
            // Sub-headline
            Paragraph(
                text = "Architect of Summon, Materia, and Sigil. I specialize in building high-performance graphics runtimes and developer tools that bridge the gap between JVM backends and modern UI.",
                modifier = Modifier()
                    .fontSize(1.25.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
                    .maxWidth(800.px)
            )
            
            // Quick Stats
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.xl)
                    .marginTop(PortfolioTheme.Spacing.lg)
            ) {
                QuickStat(label = "Role Level", value = "Senior / Staff Engineer")
                QuickStat(label = "Focus", value = "Graphics Engineering, KMP Architecture, Developer Experience")
                QuickStat(label = "Location", value = "Open to Relocation (Global) or Remote")
            }
        }
    }
}

@Composable
private fun QuickStat(label: String, value: String) {
    Column(modifier = Modifier().gap(4.px)) {
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(0.75.rem)
                .fontWeight(600)
                .color(PortfolioTheme.Colors.ACCENT)
                .textTransform(TextTransform.Uppercase)
                .letterSpacing("0.1em")
        )
        Text(
            text = value,
            modifier = Modifier()
                .fontSize(1.rem)
                .fontWeight(500)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
    }
}

@Composable
private fun UnfairAdvantageSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.xl)) {
            Text(
                text = "The \"Unfair Advantage\"",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .style("grid-template-columns", "repeat(auto-fit, minmax(280px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                AdvantageCard(
                    title = "Systems Architecture",
                    description = "Most candidates can build a feature. I build the framework. I understand API surface area, backward compatibility, and the complexities of maintaining a full stack (UI + Engine + Shaders) used by others."
                )
                AdvantageCard(
                    title = "The Graphics/UI Bridge",
                    description = "I solved the \"Hard Problem\" of declaratively managing 3D state. By building Sigil (a React-Three-Fiber equivalent for Kotlin), I proved I can translate complex imperative graphics pipelines into ergonomic, reactive APIs."
                )
                AdvantageCard(
                    title = "True Multiplatform",
                    description = "I don't rely on wrappers. I write logic that compiles to Wasm, Native (Metal/Vulkan), and JVM. I understand the memory models and concurrency challenges of every target platform."
                )
            }
        }
    }
}

@Composable
private fun AdvantageCard(title: String, description: String) {
    Box(
        modifier = Modifier()
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .border("1px", "solid", PortfolioTheme.Colors.BORDER)
    ) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.sm)) {
            Text(
                text = title,
                modifier = Modifier()
                    .fontSize(1.1.rem)
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
        }
    }
}

@Composable
private fun TechnicalProficiencySection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.xl)) {
            Text(
                text = "Technical Proficiency",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .style("grid-template-columns", "repeat(auto-fit, minmax(280px, 1fr))")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                ProficiencyCard(
                    level = "Expert",
                    skills = listOf(
                        "Kotlin (JVM, Native, Wasm)",
                        "Compose Multiplatform / Jetpack Compose",
                        "OpenGL / WebGL / Shaders (GLSL)",
                        "API Design & Library Architecture"
                    )
                )
                ProficiencyCard(
                    level = "Proficient",
                    skills = listOf(
                        "C++ (Interop & Native bindings)",
                        "Vulkan / Metal (via Materia)",
                        "React / TypeScript (Legacy stack knowledge)"
                    )
                )
            }
        }
    }
}

@Composable
private fun ProficiencyCard(level: String, skills: List<String>) {
    Box(
        modifier = Modifier()
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .border("1px", "solid", PortfolioTheme.Colors.BORDER)
    ) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.md)) {
            Text(
                text = level,
                modifier = Modifier()
                    .fontSize(0.8.rem)
                    .fontWeight(700)
                    .color(if (level == "Expert") PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.TEXT_SECONDARY)
                    .textTransform(TextTransform.Uppercase)
                    .letterSpacing("0.1em")
            )
            Column(modifier = Modifier().gap(8.px)) {
                skills.forEach { skill ->
                    Row(modifier = Modifier().alignItems(AlignItems.Center).gap(8.px)) {
                        Text(text = "•", modifier = Modifier().color(PortfolioTheme.Colors.ACCENT))
                        Text(
                            text = skill,
                            modifier = Modifier()
                                .fontSize(0.95.rem)
                                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdealRoleCriteriaSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.lg)) {
            Text(
                text = "Ideal Role Criteria",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Paragraph(
                text = "I am looking for:",
                modifier = Modifier()
                    .fontSize(1.1.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .marginBottom(PortfolioTheme.Spacing.md)
            )
            
            Column(modifier = Modifier().gap(PortfolioTheme.Spacing.md)) {
                CriteriaItem(
                    title = "Engineering Challenges",
                    description = "Roles involving Graphics Runtimes, Tooling Infrastructure, or Core Product Architecture."
                )
                CriteriaItem(
                    title = "Culture",
                    description = "Teams that value \"Deep Work,\" open-source contribution, and high-quality documentation."
                )
                CriteriaItem(
                    title = "Location",
                    description = "Actively seeking relocation to London, Berlin, Amsterdam, or US Tech Hubs. Open to remote."
                )
            }
        }
    }
}

@Composable
private fun CriteriaItem(title: String, description: String) {
    Row(modifier = Modifier().gap(PortfolioTheme.Spacing.sm)) {
        Text(text = "→", modifier = Modifier().color(PortfolioTheme.Colors.ACCENT).fontWeight(700))
        Column(modifier = Modifier().gap(4.px)) {
            Text(
                text = title,
                modifier = Modifier()
                    .fontSize(1.rem)
                    .fontWeight(600)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            Paragraph(
                text = description,
                modifier = Modifier()
                    .fontSize(0.95.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun DeploymentDocsSection() {
    SectionWrap(modifier = Modifier().paddingTop(40.px).paddingBottom(60.px)) {
        Column(modifier = Modifier().gap(PortfolioTheme.Spacing.lg)) {
            Text(
                text = "Proof of Work",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(800)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
            
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                ButtonLink(
                    label = "Read the Summon Documentation",
                    href = "/summon/docs",
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .padding("12px", "24px")
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(600)
                        .textDecoration(TextDecoration.None),
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
                ButtonLink(
                    label = "View GitHub Activity",
                    href = "https://github.com/yehousef",
                    modifier = Modifier()
                        .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding("12px", "24px")
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(600)
                        .textDecoration(TextDecoration.None)
                        .border("1px", "solid", PortfolioTheme.Colors.BORDER),
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

            }
        }
    }
}
