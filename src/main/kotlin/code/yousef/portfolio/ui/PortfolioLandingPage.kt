package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.ContactSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.extensions.vw
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight


private object LandingCopy {
    val heroTitle = LocalizedText(
        en = "I design & build high-performance websites and mobile apps.",
        ar = "أصمم وأبني مواقع وتطبيقات عالية الأداء."
    )
    val heroBody = LocalizedText(
        en = "I’m Yousef — a developer who creates fast, modern digital products that look great, feel smooth, and work everywhere: web, iOS, Android, and desktop. %SUMMON% powers the same work I ship for clients.",
        ar = "أنا يوسف — مطور يبني منتجات رقمية سريعة وعصرية تعمل بسلاسة على الويب وiOS وAndroid وسطح المكتب. %SUMMON% هي التقنية نفسها التي أستخدمها لعملائي."
    )
    val heroTrust = LocalizedText(
        en = "Trusted by developers and creatives — I built %SUMMON%, a custom UI framework used to power fast, responsive apps.",
        ar = "يثق بي المطورون والمبدعون — أنشأت %SUMMON%، إطار واجهات مخصص يشغّل تطبيقات سريعة ومتجاوبة."
    )
    val heroStack = LocalizedText(
        en = "I build with React/Next.js and Kotlin Multiplatform — the same tools used by companies like Netflix, JetBrains, and Google.",
        ar = "أبني باستخدام React/Next.js وKotlin Multiplatform — نفس الأدوات التي تستخدمها شركات مثل نتفلكس وجيت براينز وجوجل."
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
    val heroSecondaryCta = LocalizedText("Explore Summon", "استكشف سُمّون")
}

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
        HeroBand(locale)
        WhatIBuildSection(locale)
        WhyWorkWithMeSection(locale)
        FeaturedProjectSection(locale, projectName = summonProjectTitle)
        CaseStudySection(locale)
        ProcessSection(locale)
        TestimonialSection(locale)
        ContactCtaSection(locale)
        PortfolioFooter(locale = locale)
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
            RawHtml(
                """
                <p style="color:rgba(255,255,255,0.88);font-size:1.25rem;line-height:1.6;font-weight:500;">
                  ${LandingCopy.heroBody.resolveWithSummonLink(locale)}
                </p>
                """.trimIndent()
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
                        .whiteSpace(WhiteSpace.NoWrap)
                )
                SecondaryCtaButton(
                    text = LandingCopy.heroSecondaryCta.resolve(locale),
                    href = summonMarketingUrl(),
                    modifier = Modifier()
                        .minWidth("220px")
                        .whiteSpace(WhiteSpace.NoWrap)
                )
            }
            RawHtml(
                """
                <p style="color:rgba(255,255,255,0.78);font-weight:500;">
                  ${LandingCopy.heroTrust.resolveWithSummonLink(locale)}
                </p>
                """.trimIndent()
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
        RawHtml(
            """
            <p style="color:rgba(255,255,255,0.8);font-style:italic;">
              ${LandingCopy.heroStack.resolve(locale)}
            </p>
            """.trimIndent()
        )
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
                RawHtml(
                    """
                    <p style=\"color:#1c0d11;font-weight:600;\">
                      ${LandingCopy.featuredBody.resolveWithSummonLink(locale)}
                    </p>
                    """.trimIndent()
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
private fun CaseStudySection(locale: PortfolioLocale) {
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
            caseStudies.forEach { study ->
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
                    RawHtml(
                        """
                        <div style=\"width:100%;height:180px;border-radius:20px;background:linear-gradient(135deg,#4f46e5,#ec4899);display:flex;align-items:center;justify-content:center;font-weight:700;color:#ffffff;letter-spacing:0.08em;\">
                          ${study.client.take(16)}
                        </div>
                        """.trimIndent()
                    )
                    Text(
                        text = "${study.client} · ${study.industry.resolve(locale)}",
                        modifier = Modifier().fontWeight(700)
                    )
                    Paragraph(
                        text = study.summary.resolve(locale),
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                    RawHtml(
                        """
                        <p style=\"color:${PortfolioTheme.Colors.TEXT_PRIMARY};font-weight:600;\">
                          ${study.highlight.resolveWithSummonLink(locale)}
                        </p>
                        """.trimIndent()
                    )
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.sm)
                    ) {
                        Column {
                            Text(
                                text = study.statLabel.resolve(locale),
                                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            )
                            Text(text = study.statValue, modifier = Modifier().fontWeight(700))
                        }
                        ButtonLink(
                            label = LocalizedText("View details", "عرض التفاصيل").resolve(locale),
                            href = "#contact",
                            modifier = Modifier()
                                .textDecoration("none")
                                .color(PortfolioTheme.Colors.ACCENT_ALT),
                            navigationMode = LinkNavigationMode.Client,
                            dataAttributes = mapOf("case-study" to study.client.lowercase()),
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
                    RawHtml(
                        """
                        <div style=\"width:48px;height:48px;border-radius:16px;background:linear-gradient(135deg,#22d3ee,#3b82f6);display:flex;align-items:center;justify-content:center;font-weight:700;color:#001a2c;\">
                          ★
                        </div>
                        """.trimIndent()
                    )
                    Paragraph(
                        text = testimonial.quote.resolveWithSummonLink(locale),
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

private data class BuildCapability(val title: LocalizedText, val description: LocalizedText)

private val buildCapabilities = listOf(
    BuildCapability(
        title = LocalizedText("Websites & Web Apps", "مواقع وتطبيقات ويب"),
        description = LocalizedText(
            en = "Fast-loading, responsive websites that feel as smooth as apps — perfect for businesses, startups, and creators.",
            ar = "مواقع سريعة ومتجاوبة تشبه التطبيقات في سلاستها — مثالية للأعمال والشركات الناشئة وصنّاع المحتوى."
        )
    ),
    BuildCapability(
        title = LocalizedText("Mobile Apps (iOS & Android)", "تطبيقات جوال (iOS وAndroid)"),
        description = LocalizedText(
            en = "One app that runs beautifully on both platforms — no need for two codebases.",
            ar = "تطبيق واحد يعمل بكفاءة على كلا النظامين دون الحاجة لقاعدتي كود منفصلتين."
        )
    ),
    BuildCapability(
        title = LocalizedText("Desktop & Cross-Platform Tools", "تطبيقات سطح المكتب والمتعددة المنصات"),
        description = LocalizedText(
            en = "Powerful desktop or internal tools that share code across web, mobile, and desktop — consistent and efficient.",
            ar = "أدوات سطح مكتب أو حلول داخلية تشارك الكود بين الويب والجوال وسطح المكتب — تجربة متناسقة وفعالة."
        )
    ),
    BuildCapability(
        title = LocalizedText("Custom Systems & Dashboards", "أنظمة مخصصة ولوحات تحكم"),
        description = LocalizedText(
            en = "Admin panels, analytics tools, or full product dashboards tailored to your workflow.",
            ar = "لوحات تحكم وأدوات تحليل أو أنظمة إدارية مصممة خصيصًا لتدفق عملك."
        )
    )
)

private data class CaseStudy(
    val client: String,
    val industry: LocalizedText,
    val summary: LocalizedText,
    val highlight: LocalizedText,
    val statLabel: LocalizedText,
    val statValue: String
)

private val caseStudies = listOf(
    CaseStudy(
        client = "Futura Labs",
        industry = LocalizedText("AI SaaS", "حلول ذكاء اصطناعي"),
        summary = LocalizedText(
            en = "Designed a multilingual marketing site and onboarding flow that loads in under a second worldwide.",
            ar = "صممت موقعًا تسويقيًا متعدد اللغات ومسار ترحيب يقل زمن تحميله عن ثانية في كل مكان."
        ),
        highlight = LocalizedText(
            en = "%SUMMON% SSR + edge caching",
            ar = "%SUMMON% مع SSR وتخزين عند الحافة"
        ),
        statLabel = LocalizedText("Faster load", "تحمّل أسرع"),
        statValue = "-42%"
    ),
    CaseStudy(
        client = "Redline Mobility",
        industry = LocalizedText("Transportation", "النقل"),
        summary = LocalizedText(
            en = "Unified their booking dashboard across desktop, tablet, and in-vehicle displays using one Kotlin codebase.",
            ar = "وحّدت لوحة الحجز عبر سطح المكتب والأجهزة اللوحية وشاشات المركبات باستخدام قاعدة كود Kotlin واحدة."
        ),
        highlight = LocalizedText(
            en = "Compose + %SUMMON% UI kit",
            ar = "واجهة Compose مع حزمة واجهات %SUMMON%"
        ),
        statLabel = LocalizedText("Ops saved", "ساعات موفَّرة"),
        statValue = "60 hrs/mo"
    ),
    CaseStudy(
        client = "Northwind Commerce",
        industry = LocalizedText("Retail", "التجزئة"),
        summary = LocalizedText(
            en = "Built a secure admin portal with live metrics, dark mode, and localized Arabic content for GCC teams.",
            ar = "بنيت بوابة إدارية آمنة ببيانات مباشرة ووضع داكن ومحتوى عربي لفِرق الخليج."
        ),
        highlight = LocalizedText(
            en = "%SUMMON% modifiers + hydration",
            ar = "معدلّات %SUMMON% مع Hydration"
        ),
        statLabel = LocalizedText("Bug rate", "نسبة الأخطاء"),
        statValue = "-35%"
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
            ar = "\"تولى كل شيء — الهيكلة ومكوّنات %SUMMON% والنشر. كان يوم الإطلاق الأكثر هدوءًا لنا.\""
        ),
        author = "Marcus R.",
        role = LocalizedText("COO", "المدير التشغيلي"),
        company = LocalizedText("Atlas Billing", "Atlas Billing")
    )
)

@Composable
private fun StructuredDataSnippet() {
    RawHtml(
        """
        <script type="application/ld+json">
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
        </script>
        """.trimIndent()
    )
}
