package code.yousef.portfolio.docs.summon

import code.yousef.portfolio.docs.*
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.HYDRATION_SCRIPT_PATH
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text

import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.seo.HeadScope
import kotlinx.serialization.json.Json
import java.net.URI

class DocsRouter(
    private val seoExtractor: SeoExtractor,
    private val portfolioOrigin: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun render(
        requestPath: String,
        origin: String,
        html: String,
        meta: MarkdownMeta,
        toc: List<TocEntry>,
        sidebar: DocsNavTree,
        neighbors: NeighborLinks
    ): SummonPage {
        val seo = seoExtractor.build(requestPath, meta)
        val navBase = resolveNavBase(origin)
        return SummonPage(
            head = headBlock(seo.title, seo.description, seo.canonicalUrl),
            content = {
                DocsPageFrame(navBase, origin) {
                    DocsShell(
                        requestPath = requestPath,
                        html = html,
                        toc = toc,
                        sidebar = sidebar,
                        meta = meta,
                        neighbors = neighbors
                    )
                }
            }
        )
    }

    fun notFound(requestPath: String, sidebar: DocsNavTree, origin: String): SummonPage {
        val canonical = seoExtractor.canonical(requestPath)
        val navJson = json.encodeToString(sidebar).replace("</", "<\\/")
        val navBase = resolveNavBase(origin)
        return SummonPage(
            head = headBlock("Not found", "This page could not be located.", canonical),
            content = {
                DocsPageFrame(navBase, origin) {
                    DocsNotFoundContent(navJson)
                }
            }
        )
    }

    private fun headBlock(title: String, description: String, canonical: String): (HeadScope) -> Unit = { head ->
        head.title("$title · Summon Docs")
        head.meta("description", description, null, null, null)
        head.meta(null, title, "og:title", null, null)
        head.meta(null, description, "og:description", null, null)
        head.meta(null, canonical, "og:url", null, null)
        head.meta(null, "article", "og:type", null, null)
        head.meta("twitter:card", "summary_large_image", null, null, null)
        head.meta("twitter:title", title, null, null, null)
        head.meta("twitter:description", description, null, null, null)
        head.link("canonical", canonical, null, null, null, null)
        head.script(HYDRATION_SCRIPT_PATH, "application/javascript", "summon-hydration-runtime", false, true, null)
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
private fun DocsPageFrame(navBaseUrl: String, docsBaseUrl: String, content: @Composable () -> Unit) {
    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(
            locale = PortfolioLocale.EN,
            forceNativeLinks = true,
            nativeBaseUrl = navBaseUrl,
            docsBaseUrl = docsBaseUrl
        )
        Box(
            modifier = Modifier()
                .height(PortfolioTheme.Spacing.xxl)
        ) {}
        SectionWrap {
            content()
        }
    }
}
