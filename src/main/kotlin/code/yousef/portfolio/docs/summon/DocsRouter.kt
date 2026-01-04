package code.yousef.portfolio.docs.summon

import code.yousef.portfolio.docs.*
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.HYDRATION_SCRIPT_PATH
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.sigilMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.HamburgerMenu
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.seo.HeadScope
import kotlinx.serialization.json.Json
import java.net.URI

class DocsRouter(
    private val seoExtractor: SeoExtractor,
    private val portfolioOrigin: String,
    private val defaultBranding: DocsBranding = DocsBranding.summon(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun render(
        requestPath: String,
        origin: String,
        html: String,
        meta: MarkdownMeta,
        toc: List<TocEntry>,
        sidebar: DocsNavTree,
        neighbors: NeighborLinks,
        basePath: String = ""
    ): SummonPage {
        val seo = seoExtractor.build(requestPath, meta)
        val navBase = resolveNavBase(origin)
        val branding = resolveBranding(origin)
        return SummonPage(
            head = headBlock(branding, seo.title, seo.description, seo.canonicalUrl),
            content = {
                DocsPageFrame(navBase, origin, branding) {
                    DocsShell(
                        requestPath = requestPath,
                        html = html,
                        toc = toc,
                        sidebar = sidebar,
                        meta = meta,
                        neighbors = neighbors,
                        basePath = basePath
                    )
                }
            }
        )
    }

    fun notFound(requestPath: String, sidebar: DocsNavTree, origin: String): SummonPage {
        val canonical = seoExtractor.canonical(requestPath)
        val navJson = json.encodeToString(sidebar).replace("</", "<\\/")
        val navBase = resolveNavBase(origin)
        val branding = resolveBranding(origin)
        return SummonPage(
            head = headBlock(branding, "Not found", "This page could not be located.", canonical),
            content = {
                DocsPageFrame(navBase, origin, branding) {
                    DocsNotFoundContent(navJson)
                }
            }
        )
    }

    private fun headBlock(branding: DocsBranding, title: String, description: String, canonical: String): (HeadScope) -> Unit = { head ->
        head.title("$title · ${branding.docsTitle}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("description", null, description, null, null)
        head.meta(null, "og:title", title, null, null)
        head.meta(null, "og:description", description, null, null)
        head.meta(null, "og:url", canonical, null, null)
        head.meta(null, "og:type", "article", null, null)
        head.meta("twitter:card", null, "summary_large_image", null, null)
        head.meta("twitter:title", null, title, null, null)
        head.meta("twitter:description", null, description, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, null, "application/javascript", false, true, null)
    }

    private fun resolveBranding(origin: String): DocsBranding {
        val uri = runCatching { URI(origin) }.getOrNull()
        val host = uri?.host
        
        return when {
            host == null -> defaultBranding
            host.startsWith("summon.") -> DocsBranding.summon(::summonMarketingUrl)
            host.startsWith("materia.") -> DocsBranding.materia(::materiaMarketingUrl)
            host.startsWith("sigil.") -> DocsBranding.sigil(::sigilMarketingUrl)
            else -> defaultBranding
        }
    }

    private fun resolveNavBase(origin: String): String {
        val uri = runCatching { URI(origin) }.getOrNull()
        val host = uri?.host
        val isLocalHost = host != null && (host == "localhost" || host.endsWith(".localhost"))
        if (!isLocalHost && portfolioOrigin.isNotBlank()) {
            return portfolioOrigin
        }
        if (uri == null || host == null) {
            return if (portfolioOrigin.isNotBlank()) portfolioOrigin else origin
        }
        val normalizedHost = when {
            host.startsWith("summon.") -> host.removePrefix("summon.")
            host.startsWith("docs.") -> host.removePrefix("docs.")
            else -> host
        }
        val scheme = uri.scheme ?: "http"
        val defaultPort = if (scheme == "https") 443 else 80
        val portPart = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$normalizedHost$portPart"
    }
}

@Composable
private fun DocsNotFoundContent(navJson: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(codes.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
    ) {
        Text(
            text = "Page not found",
            modifier = Modifier()
                .fontWeight(700)
                .fontSize(2.rem)
        )
        Text(
            text = "We couldn’t find that guide. Use the search below or pick another section.",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.sm)
                .padding(PortfolioTheme.Spacing.md)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
        ) {
            Text(
                text = "Search coming soon",
                modifier = Modifier().fontWeight(700)
            )
            Text(
                text = "Browse sections or use your browser search.",
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun DocsPageFrame(navBaseUrl: String, docsBaseUrl: String, branding: DocsBranding, content: @Composable () -> Unit) {
    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .width(100.percent)
        ) {
            DocsNavbar(navBaseUrl, docsBaseUrl, branding)
            SectionWrap(maxWidthPx = 1500) {
                content()
            }
        }
    }
}

@Composable
private fun DocsNavbar(navBaseUrl: String, docsBaseUrl: String, branding: DocsBranding) {
    val docsPath = "/docs"
    val apiReferencePath = "/docs/api-reference"

    // CSS for responsive visibility
    GlobalStyle("""
        .docs-nav-desktop { display: block !important; }
        .docs-nav-mobile { display: none !important; }
        
        @media (max-width: 960px) {
            .docs-nav-desktop { display: none !important; }
            .docs-nav-mobile { display: block !important; }
        }
    """)

    Box(modifier = Modifier().width(100.percent)) {
        // Desktop Version
        Box(modifier = Modifier().className("docs-nav-desktop").width(100.percent)) {
            DesktopDocsNavbar(navBaseUrl, docsBaseUrl, branding, docsPath, apiReferencePath)
        }
        
        // Mobile Version
        Box(modifier = Modifier().className("docs-nav-mobile").width(100.percent)) {
            MobileDocsNavbar(navBaseUrl, docsBaseUrl, branding, docsPath, apiReferencePath)
        }
    }
}

@Composable
private fun DesktopDocsNavbar(navBaseUrl: String, docsBaseUrl: String, branding: DocsBranding, docsPath: String, apiReferencePath: String) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1, BorderSide.Bottom)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            Image(
                src = branding.logoPath,
                alt = branding.name,
                modifier = Modifier()
                    .width(28.px)
                    .height(28.px)
            )
            AnchorLink(
                label = branding.name,
                href = branding.homeUrl,
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.25.rem)
                    .color(branding.accentColor)
                    .textDecoration(TextDecoration.None),
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
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            DocsNavLink(label = "Documentation", href = docsPath, accentColor = branding.accentColor)
            DocsNavLink(label = "API Reference", href = apiReferencePath, accentColor = branding.accentColor)
        }
    }
}

@Composable
private fun MobileDocsNavbar(navBaseUrl: String, docsBaseUrl: String, branding: DocsBranding, docsPath: String, apiReferencePath: String) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1, BorderSide.Bottom)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
    ) {
        // Hamburger Menu (Left)
        HamburgerMenu(
            modifier = Modifier()
                .position(Position.Relative)
                .width(40.px)
                .height(40.px)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .zIndex(100),
            menuContainerModifier = Modifier()
                .position(Position.Absolute)
                .top(100.percent)
                .left(0.px)
                .marginTop(8.px)
                .backgroundColor("#0a1628")
                .borderRadius(8.px)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .zIndex(1000)
                .minWidth(200.px)
                .width("max-content"),
            menuContent = {
                Column(
                    modifier = Modifier()
                        .width(100.percent)
                        .padding(16.px)
                        .gap(12.px)
                        .backgroundColor("#0a1628")
                ) {
                    DocsNavLink(label = "Documentation", href = docsPath, accentColor = branding.accentColor)
                    DocsNavLink(label = "API Reference", href = apiReferencePath, accentColor = branding.accentColor)
                }
            }
        )

        // Logo and name (Right/Center)
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            Image(
                src = branding.logoPath,
                alt = branding.name,
                modifier = Modifier()
                    .width(28.px)
                    .height(28.px)
            )
            AnchorLink(
                label = branding.name,
                href = branding.homeUrl,
                modifier = Modifier()
                    .fontWeight(700)
                    .fontSize(1.1.rem)
                    .color(branding.accentColor)
                    .textDecoration(TextDecoration.None),
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

@Composable
private fun DocsNavLink(label: String, href: String, accentColor: String = PortfolioTheme.Colors.ACCENT_ALT) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
            .hover(
                Modifier()
                    .color(accentColor)
            ),
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
