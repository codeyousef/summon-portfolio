package code.yousef.portfolio.ui

import code.yousef.portfolio.content.PortfolioContent
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.ServicesOverlay
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.AboutMeSection
import code.yousef.portfolio.ui.sections.CaseStudySection
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import code.yousef.portfolio.ui.sections.SelectedWorksSection
import code.yousef.portfolio.ui.sections.ServicesSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
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


private object LandingCopy {
    val heroTitle = LocalizedText(
        en = "Senior Full-Stack Engineer & System Architect.",
        ar = "مهندس برمجيات كامل ومعماري أنظمة."
    )
    val heroBody = LocalizedText(
        en = "Specializing in Kotlin Multiplatform and Custom Framework Development. I build scalable, cross-platform applications from a single codebase.",
        ar = "متخصص في Kotlin Multiplatform وتطوير أطر العمل المخصصة. أبني تطبيقات قابلة للتوسع ومتعددة المنصات من قاعدة كود واحدة."
    )
    val heroPrimaryCta = LocalizedText("View Selected Work", "عرض الأعمال المختارة")
    val heroSecondaryCta = LocalizedText("Book a Discovery Call", "احجز جلسة تعريف")
}

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
        Box(
            modifier = Modifier()
                .height(PortfolioTheme.Spacing.xxl)
        ) {}
        HeroBand(locale)
        CaseStudySection(locale = locale)
        AboutMeSection(locale = locale)
        if (content.services.isNotEmpty()) {
            ServicesSection(
                services = content.services,
                locale = locale,
                onRequestServices = openServicesModal,
                modifier = Modifier().id("services")
            )
        }
        if (content.projects.isNotEmpty()) {
            SelectedWorksSection(
                projects = content.projects,
                locale = locale,
                modifier = Modifier().id("projects")
            )
        }
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
            Text(
                text = LandingCopy.heroBody.resolve(locale),
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
                val projectsHref = "$home#projects"
                PrimaryCtaButton(
                    text = LandingCopy.heroPrimaryCta.resolve(locale),
                    href = projectsHref,
                    modifier = Modifier()
                        .minWidth(200.px)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Client
                )
                SecondaryCtaButton(
                    text = LandingCopy.heroSecondaryCta.resolve(locale),
                    href = "https://app.usemotion.com/meet/motion.duckling867/meeting",
                    modifier = Modifier()
                        .minWidth(220.px)
                        .whiteSpace(WhiteSpace.NoWrap)
                )
            }
        }
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
        // Correct argument order: src, id, type, async, defer, text
        script(
            null,
            "portfolio-structured-data",
            "application/ld+json",
            false,
            false,
            schema
        )
    }
}
