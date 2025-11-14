package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.BlogTeaserSection
import code.yousef.portfolio.ui.sections.ContactSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.portfolio.ui.sections.ServicesSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.RichText
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight
import codes.yousef.summon.runtime.LocalPlatformRenderer
import codes.yousef.summon.runtime.rememberMutableStateOf


private object LandingCopy {
    val heroTitle = LocalizedText(
        en = "I design & build high-performance websites and mobile apps.",
        ar = "أصمم وأبني مواقع وتطبيقات عالية الأداء."
    )
    val heroBody = LocalizedText(
        en = "I’m Yousef — a full-stack engineer who ships web, mobile, and desktop products using the right tool for the job: React/Next.js, Kotlin Multiplatform, Spring, Ktor, Quarkus, Django, and %SUMMON% when custom UI speed matters.",
        ar = "أنا يوسف — مهندس برمجيات كامل يبني منتجات للويب والجوال وسطح المكتب باستخدام الأداة المناسبة: React/Next.js وKotlin Multiplatform وSpring وKtor وQuarkus وDjango و%SUMMON% حين نحتاج واجهات فائقة الأداء."
    )
    val heroTrust = LocalizedText(
        en = "Trusted by developers and creatives — I built %SUMMON%, a custom UI framework used to power fast, responsive apps.",
        ar = "يثق بي المطورون والمبدعون — أنشأت %SUMMON%، إطار واجهات مخصص يشغّل تطبيقات سريعة ومتجاوبة."
    )
    val whatEyebrow = LocalizedText("What I build", "ما الذي أبنيه")
    val whatTitle =
        LocalizedText("Web, mobile, desktop — one cohesive experience.", "ويب، جوال، سطح مكتب — تجربة واحدة متماسكة.")
    val featuredHeading = LocalizedText("Built the tools I use.", "بنيت الأدوات التي أستخدمها.")
    val featuredBody = LocalizedText(
        en = "I created %SUMMON%, a modern UI framework that makes websites load faster, perform better, and scale cleanly across devices. It’s the same engineering mindset I bring to client projects.",
        ar = "أنشأت %SUMMON%، إطار واجهات حديث يجعل المواقع أسرع وأفضل أداءً وأسهل في التوسّع على أي جهاز. هذا هو نفس التفكير الهندسي الذي أقدّمه لمشاريع العملاء."
    )
    val caseEyebrow = LocalizedText("Case studies", "دراسات حالة")
    val caseTitle = LocalizedText("Recent builds and experiments.", "أحدث المشاريع والتجارب.")
    val whyEyebrow = LocalizedText("Why work with me", "لماذا تعمل معي")
    val whyTitle = LocalizedText("One developer. Every platform. Same quality.", "مطور واحد. جميع المنصات. نفس الجودة.")
    val processEyebrow = LocalizedText("Process", "المنهجية")
    val processTitle = LocalizedText("From idea to launch, I make it simple.", "من الفكرة إلى الإطلاق — أجعلها بسيطة.")
    val testimonialEyebrow = LocalizedText("Social proof", "آراء العملاء")
    val testimonialTitle = LocalizedText("Teams keep coming back.", "العملاء يعودون مجددًا.")
    val contactHeadline = LocalizedText("Let’s build something great.", "فلنبنِ شيئًا رائعًا.")
    val contactBodyPrimary = LocalizedText(
        en = "Have an idea or project in mind? I’ll help you bring it to life — fast, clean, and cross-platform from day one.",
        ar = "هل لديك فكرة أو مشروع في ذهنك؟ سأساعدك على تنفيذه بسرعة وجودة وبشكل متعدد المنصات منذ اليوم الأول."
    )
    val contactBodySecondary = LocalizedText(
        en = "No agencies, no outsourcing — you’ll work directly with me.",
        ar = "لا وكالات ولا تعهيد — ستعمل معي مباشرة."
    )
    val heroPrimaryCta = LocalizedText("Start your project", "ابدأ مشروعك")
    val heroSecondaryCta = LocalizedText("Explore Summon", "استكشف Summon")
}

@Composable
fun PortfolioLandingPage(
    content: PortfolioContent,
    locale: PortfolioLocale,
    servicesModalOpen: Boolean = false
) {
    val summonProjectTitle = content.projects.firstOrNull { it.slug == "summon-framework" }
        ?.title
        ?.resolve(locale)
        ?: "Summon"
    val servicesModalState = rememberMutableStateOf(servicesModalOpen)
    val openServicesModal = { servicesModalState.value = true }
    val closeServicesModal = { servicesModalState.value = false }

    PageScaffold(locale = locale) {
        AppHeader(locale = locale)
        Box(
            modifier = Modifier()
                .height(PortfolioTheme.Spacing.xxl)
        ) {}
        HeroBand(locale)
        WhatIBuildSection(locale)
        ServicesSection(
            services = content.services,
            locale = locale,
            onRequestServices = openServicesModal,
            modifier = Modifier().id("services")
        )
        WhyWorkWithMeSection(locale)
        FeaturedProjectSection(locale, projectName = summonProjectTitle)
        BlogTeaserSection(
            posts = content.blogPosts,
            locale = locale,
            modifier = Modifier().id("blog")
        )
        CaseStudySection(projects = content.projects, locale = locale)
        ProcessSection(locale)
        TestimonialSection(locale)
        ContactCtaSection(locale)
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

@Composable
private fun HeroBand(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("hero")) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = LandingCopy.heroTitle.resolve(locale),
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
            RichText(
                "<p>${LandingCopy.heroBody.resolveWithSummonLink(locale)}</p>",
                modifier = Modifier()
                    .color("rgba(255,255,255,0.88)")
                    .fontSize(1.25.rem)
                    .lineHeight(1.6)
                    .fontWeight(500)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                val prefix = locale.pathPrefix()
                val home = if (prefix.isEmpty()) "/" else prefix
                val contactHref = "$home#contact"
                PrimaryCtaButton(
                    text = LandingCopy.heroPrimaryCta.resolve(locale),
                    href = contactHref,
                    modifier = Modifier()
                        .minWidth("200px")
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Client
                )
                SecondaryCtaButton(
                    text = LandingCopy.heroSecondaryCta.resolve(locale),
                    href = summonMarketingUrl(),
                    modifier = Modifier()
                        .minWidth("220px")
                        .whiteSpace(WhiteSpace.NoWrap)
                )
            }
            RichText(
                "<p>${LandingCopy.heroTrust.resolveWithSummonLink(locale)}</p>",
                modifier = Modifier()
                    .color("rgba(255,255,255,0.78)")
                    .fontWeight(500)
            )
        }
    }
}

@Composable
private fun WhatIBuildSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("build")) {
        SectionHeading(
            locale = locale,
            eyebrow = LandingCopy.whatEyebrow,
            title = LandingCopy.whatTitle
        )
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(240px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            buildCapabilities.forEach { item ->
                Column(
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .background(PortfolioTheme.Gradients.CARD)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = item.title.resolve(locale),
                        modifier = Modifier()
                            .fontWeight(700)
                            .fontSize(1.1.rem)
                    )
                    Paragraph(
                        text = item.description.resolve(locale),
                        modifier = Modifier()
                            .color("rgba(255,255,255,0.82)")
                    )
                }
            }
        }
    }
}

@Composable
private fun WhyWorkWithMeSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("why")) {
        SectionHeading(
            locale = locale,
            eyebrow = LandingCopy.whyEyebrow,
            title = LandingCopy.whyTitle
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
                            text = item.title.resolve(locale),
                            modifier = Modifier().fontWeight(700)
                        )
                        Paragraph(
                            text = item.description.resolve(locale),
                            modifier = Modifier().color("rgba(255,255,255,0.82)")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedProjectSection(locale: PortfolioLocale, projectName: String) {
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
                    text = LandingCopy.featuredHeading.resolve(locale),
                    modifier = Modifier()
                        .fontSize(cssClamp(32.px, 4.vw, 48.px))
                        .fontWeight(800)
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                )
                RichText(
                    "<p>${LandingCopy.featuredBody.resolveWithSummonLink(locale)}</p>",
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
                        text = LocalizedText("Explore", "استكشف").resolve(locale) + " $projectName",
                        href = summonMarketingUrl()
                    )
                }
            }
        }
    }
}

@Composable
private fun CaseStudySection(projects: List<code.yousef.portfolio.content.model.Project>, locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("projects")) {
        SectionHeading(
            locale = locale,
            eyebrow = LandingCopy.caseEyebrow,
            title = LandingCopy.caseTitle
        )
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(260px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            projects.sortedBy { it.order }.forEach { project ->
                Column(
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .background(PortfolioTheme.Gradients.GLASS)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier()
                            .width(100.percent)
                            .height(180.px)
                            .borderRadius(20.px)
                            .backgroundLayers {
                                linearGradient {
                                    direction("135deg")
                                    colorStop("#4f46e5", "0%")
                                    colorStop("#ec4899", "100%")
                                }
                            }
                            .display(Display.Flex)
                            .alignItems(AlignItems.Center)
                            .justifyContent(JustifyContent.Center)
                    ) {
                        Text(
                            text = project.layerLabel.resolve(locale),
                            modifier = Modifier()
                                .fontWeight(700)
                                .color("#ffffff")
                                .letterSpacing("0.08em")
                        )
                    }
                    Text(
                        text = "${project.title.resolve(locale)} · ${project.category.label.resolve(locale)}",
                        modifier = Modifier().fontWeight(700)
                    )
                    Paragraph(
                        text = project.description.resolve(locale),
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                    RichText(
                        "<p>${project.layerName.resolveWithSummonLink(locale)}</p>",
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                            .fontWeight(600)
                    )
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.sm)
                            .flexWrap(FlexWrap.Wrap)
                    ) {
                        project.technologies.forEach { tech ->
                            Text(
                                text = tech,
                                modifier = Modifier()
                                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                                    .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                                    .borderRadius(PortfolioTheme.Radii.md)
                                    .fontSize(0.85.rem)
                                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("process")) {
        SectionHeading(
            locale = locale,
            eyebrow = LandingCopy.processEyebrow,
            title = LandingCopy.processTitle
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            processSteps.forEach { step ->
                Column(
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .padding(PortfolioTheme.Spacing.md)
                        .gap(PortfolioTheme.Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.sm)
                            .alignItems(AlignItems.Center)
                    ) {
                        Text(
                            text = step.number.toString().padStart(2, '0'),
                            modifier = Modifier()
                                .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                                .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                                .borderRadius(PortfolioTheme.Radii.md)
                                .fontWeight(700)
                        )
                        Text(
                            text = step.title.resolve(locale),
                            modifier = Modifier().fontWeight(700)
                        )
                    }
                    Paragraph(
                        text = step.description.resolve(locale),
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}

@Composable
private fun TestimonialSection(locale: PortfolioLocale) {
    SectionWrap(modifier = Modifier().id("testimonial")) {
        SectionHeading(
            locale = locale,
            eyebrow = LandingCopy.testimonialEyebrow,
            title = LandingCopy.testimonialTitle
        )
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(260px, 1fr))")
                .gap(PortfolioTheme.Spacing.md)
        ) {
            testimonials.forEach { testimonial ->
                Column(
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .background(PortfolioTheme.Gradients.GLASS)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier()
                            .width(48.px)
                            .height(48.px)
                            .borderRadius(16.px)
                            .backgroundLayers {
                                linearGradient {
                                    direction("135deg")
                                    colorStop("#22d3ee", "0%")
                                    colorStop("#3b82f6", "100%")
                                }
                            }
                            .display(Display.Flex)
                            .alignItems(AlignItems.Center)
                            .justifyContent(JustifyContent.Center)
                    ) {
                        Text(
                            text = "★",
                            modifier = Modifier()
                                .fontWeight(700)
                                .color("#001a2c")
                        )
                    }
                    RichText(
                        "<p>${testimonial.quote.resolveWithSummonLink(locale)}</p>",
                        modifier = Modifier()
                            .fontSize(1.1.rem)
                            .lineHeight(1.5)
                    )
                    Paragraph(
                        text = "${testimonial.author} — ${testimonial.role.resolve(locale)}, ${
                            testimonial.company.resolve(
                                locale
                            )
                        }",
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
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
                text = LandingCopy.contactHeadline.resolve(locale),
                modifier = Modifier()
                    .fontSize(cssClamp(32.px, 4.vw, 48.px))
                    .fontWeight(800)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = LandingCopy.contactBodyPrimary.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
            Paragraph(
                text = LandingCopy.contactBodySecondary.resolve(locale),
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
    ContactSection(locale = locale)
}

@Composable
private fun SectionHeading(
    locale: PortfolioLocale,
    eyebrow: LocalizedText,
    title: LocalizedText
) {
    Column(
        modifier = Modifier()
            .gap(PortfolioTheme.Spacing.xs)
    ) {
        Text(
            text = eyebrow.resolve(locale).uppercase(),
            modifier = Modifier()
                .fontSize(0.85.rem)
                .letterSpacing("0.3em")
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Text(
            text = title.resolve(locale),
            modifier = Modifier()
                .fontSize(cssClamp(32.px, 4.vw, 48.px))
                .fontWeight(800)
                .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
        )
    }
}

@Composable
private fun PrimaryCtaButton(
    text: String,
    href: String,
    modifier: Modifier = Modifier(),
    navigationMode: LinkNavigationMode = LinkNavigationMode.Native
) {
    ButtonLink(
        label = text,
        href = href,
        modifier = modifier
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
            .borderRadius(PortfolioTheme.Radii.pill)
            .backgroundColor(PortfolioTheme.Colors.ACCENT)
            .color("#ffffff")
            .textDecoration(TextDecoration.None)
            .fontWeight(600)
            .letterSpacing("-0.01em")
            .whiteSpace(WhiteSpace.NoWrap),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = mapOf("cta" to text.lowercase()),
        navigationMode = LinkNavigationMode.Client
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
            .textDecoration(TextDecoration.None)
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

private data class BuildCapability(val title: LocalizedText, val description: LocalizedText)

private val buildCapabilities = listOf(
    BuildCapability(
        title = LocalizedText("Websites & Web Apps", "مواقع وتطبيقات ويب"),
        description = LocalizedText(
            en = "React, Next.js, %SUMMON%, or classic SSR stacks (Spring/Ktor/Django) for marketing sites, product dashboards, and commerce flows that stay fast worldwide.",
            ar = "أبني مواقع وتطبيقات ويب باستخدام React وNext.js و%SUMMON% أو أطر SSR مثل Spring وKtor وDjango لضمان السرعة عالميًا."
        )
    ),
    BuildCapability(
        title = LocalizedText("APIs & Backends", "واجهات برمجية وأنظمة خلفية"),
        description = LocalizedText(
            en = "Modern services built with Spring Boot, Ktor, Quarkus, or Django REST — complete with auth, observability, and CI/CD pipelines.",
            ar = "أنظمة خلفية حديثة بـ Spring Boot وKtor وQuarkus وDjango REST مع المصادقة والمراقبة وخطوط CI/CD."
        )
    ),
    BuildCapability(
        title = LocalizedText("Mobile Apps (Kotlin Multiplatform)", "تطبيقات جوال (Kotlin Multiplatform)"),
        description = LocalizedText(
            en = "One Kotlin Multiplatform codebase for iOS + Android with Compose and native integrations — including secure offline modes and analytics hooks.",
            ar = "قاعدة كود Kotlin Multiplatform واحدة لـ iOS وAndroid مع Compose وتكاملات محلية، وتشمل أوضاع عدم الاتصال والتحليلات."
        )
    ),
    BuildCapability(
        title = LocalizedText("Desktop & Internal Tools", "تطبيقات سطح المكتب والأدوات الداخلية"),
        description = LocalizedText(
            en = "Compose Desktop and web hybrids for mission-critical tooling, installers, or kiosk experiences that sync with your backend in real time.",
            ar = "تطبيقات Compose Desktop أو هجينة لأدوات حيوية أو أنظمة نقاط عرض تتزامن مع الخلفية في الوقت الفعلي."
        )
    )
)


private data class Reason(val emoji: String, val title: LocalizedText, val description: LocalizedText)

private val reasonsToWorkWithMe = listOf(
    Reason(
        "⚡",
        LocalizedText("Fast & Reliable", "سريع وموثوق"),
        LocalizedText(
            en = "Your app feels instant, loads fast, and runs smoothly.",
            ar = "تطبيقك يستجيب فورًا ويحمّل بسرعة ويعمل بسلاسة."
        )
    ),
    Reason(
        "🧩",
        LocalizedText("Consistent Experience", "تجربة متناسقة"),
        LocalizedText(
            en = "Looks and feels right on every device — web, mobile, or desktop.",
            ar = "مظهر وسلوك متناسق على كل جهاز — ويب أو جوال أو سطح مكتب."
        )
    ),
    Reason(
        "🛠️",
        LocalizedText("Built for Growth", "جاهز للنمو"),
        LocalizedText(
            en = "Clean code, scalable design systems, and easy maintenance.",
            ar = "كود نظيف وأنظمة تصميم قابلة للتوسّع وصيانة سهلة."
        )
    ),
    Reason(
        "🎯",
        LocalizedText("End-to-End", "حل متكامل"),
        LocalizedText(
            en = "I handle design, development, deployment, and support — start to finish.",
            ar = "أتولّى التصميم والتطوير والنشر والدعم — من البداية حتى النهاية."
        )
    )
)

private data class ProcessStep(val number: Int, val title: LocalizedText, val description: LocalizedText)

private val processSteps = listOf(
    ProcessStep(
        1,
        LocalizedText("Discovery Call", "جلسة تعريف"),
        LocalizedText(
            en = "We talk about your goals and map out what you actually need.",
            ar = "نتحدث عن أهدافك ونحدد ما تحتاجه فعليًا."
        )
    ),
    ProcessStep(
        2,
        LocalizedText("Proposal & Plan", "عرض وخطة"),
        LocalizedText(
            en = "You’ll get a clear scope, timeline, and fixed quote.",
            ar = "تحصل على نطاق عمل واضح وجدول زمني وتسعيرة ثابتة."
        )
    ),
    ProcessStep(
        3,
        LocalizedText("Design & Build", "التصميم والتنفيذ"),
        LocalizedText(
            en = "You’ll see progress weekly — no mystery.",
            ar = "تشاهد التقدم أسبوعيًا — بلا مفاجآت."
        )
    ),
    ProcessStep(
        4,
        LocalizedText("Launch & Support", "الإطلاق والدعم"),
        LocalizedText(
            en = "Once live, I stay available for updates or scaling.",
            ar = "بعد الإطلاق أظل متاحًا للتحديثات أو التوسع."
        )
    )
)

private data class Testimonial(
    val quote: LocalizedText,
    val author: String,
    val role: LocalizedText,
    val company: LocalizedText
)

private val testimonials = listOf(
    Testimonial(
        quote = LocalizedText(
            en = "“Yousef rebuilt our marketing site and internal dashboard in six weeks. Page speed doubled and the UI finally matches our brand.”",
            ar = "\"أعاد يوسف بناء موقعنا ولوحة التحكم الداخلية خلال ستة أسابيع. تضاعفت سرعة التصفح وأصبحت الواجهة تعكس هويتنا.\""
        ),
        author = "Laila A.",
        role = LocalizedText("Head of Product", "رئيسة المنتج"),
        company = LocalizedText("Verve Studio", "Verve Studio")
    ),
    Testimonial(
        quote = LocalizedText(
            en = "“He handled everything — architecture, %SUMMON% components, deployment. Launch day was the calmest we’ve had.”",
            ar = "تولى كل شيء — الهيكلة ومكوّنات Summon والنشر. كان يوم الإطلاق الأكثر هدوءًا لنا."
        ),
        author = "Marcus R.",
        role = LocalizedText("COO", "المدير التشغيلي"),
        company = LocalizedText("Atlas Billing", "Atlas Billing")
    )
)

@Composable
private fun StructuredDataSnippet() {
    val renderer = runCatching { LocalPlatformRenderer.current }.getOrNull() ?: return
    val schema = """
        {
          "@context": "https://schema.org",
          "@type": "Person",
          "name": "Yousef Baitalmal",
          "url": "https://dev.yousef.codes",
          "sameAs": [
            "https://www.linkedin.com/in/yousefbaitalmal",
            "https://github.com/yousefb"
          ],
          "knowsAbout": ["Kotlin", "Compose Multiplatform", "Summon UI", "SSR"],
          "hasOfferCatalog": {
            "@type": "OfferCatalog",
            "name": "Summon Services",
            "itemListElement": [
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Web apps"}},
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Mobile apps"}},
              {"@type": "Offer", "itemOffered": {"@type": "Service", "name": "Dashboards"}}
            ]
          }
        }
    """.trimIndent()
    renderer.renderHeadElements {
        script(
            null,
            "application/ld+json",
            "portfolio-structured-data",
            false,
            false,
            schema
        )
    }
}
