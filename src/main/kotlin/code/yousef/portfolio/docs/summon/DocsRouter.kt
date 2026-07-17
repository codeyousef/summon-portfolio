package code.yousef.portfolio.docs.summon

import code.yousef.portfolio.docs.*
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.*
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.ContextNavigationIds
import code.yousef.portfolio.ui.components.ContextNavigationItem
import code.yousef.portfolio.ui.components.GlobalNavigationDestination
import code.yousef.portfolio.ui.components.PageNavigationContext
import code.yousef.portfolio.ui.components.SiteNavigation
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.seo.HeadScope
import kotlinx.serialization.json.Json
import java.net.URI

class DocsRouter(
    private val seoExtractor: SeoExtractor,
    private val defaultBranding: DocsBranding = DocsBranding.summon(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val seenPackagesEnabled: Boolean = false,
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
        val branding = resolveBranding(origin)
        return SummonPage(
            head = headBlock(branding, seo.title, seo.description, seo.canonicalUrl),
            content = {
                DocsPageFrame(branding, requestPath, basePath, seenPackagesEnabled) {
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

    fun notFound(requestPath: String, sidebar: DocsNavTree, origin: String, basePath: String = ""): SummonPage {
        val canonical = seoExtractor.canonical(requestPath)
        val navJson = json.encodeToString(sidebar).replace("</", "<\\/")
        val branding = resolveBranding(origin)
        return SummonPage(
            head = headBlock(branding, "Not found", "This page could not be located.", canonical),
            content = {
                DocsPageFrame(branding, requestPath, basePath, seenPackagesEnabled) {
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
            host.startsWith("seen.") -> DocsBranding.seen(::seenMarketingUrl)
            host.startsWith("aether.") -> DocsBranding.aether(::aetherMarketingUrl)
            else -> defaultBranding
        }
    }
}

@Composable
private fun DocsNotFoundContent(@Suppress("UNUSED_PARAMETER") navJson: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
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
private fun DocsPageFrame(
    branding: DocsBranding,
    requestPath: String,
    basePath: String = "",
    seenPackagesEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val docsPath = basePath.ifBlank { "/" }
    val apiReferencePath = "$basePath/api-reference"
    val activeItemId = if (
        requestPath.isWithinNavigationPath("/api-reference") ||
        requestPath.isWithinNavigationPath(apiReferencePath)
    ) {
        ContextNavigationIds.API_REFERENCE
    } else {
        ContextNavigationIds.DOCUMENTATION
    }
    val navigationContext = PageNavigationContext(
        name = branding.name,
        homeHref = branding.homeUrl,
        accentColor = branding.accentColor,
        logoPath = branding.logoPath,
        activeItemId = activeItemId,
        items = buildList {
            add(ContextNavigationItem(
                id = ContextNavigationIds.OVERVIEW,
                label = "Overview",
                href = branding.homeUrl,
            ))
            if (branding.name == "Seen") {
                if (seenPackagesEnabled) {
                    add(ContextNavigationItem(
                        id = ContextNavigationIds.PACKAGES,
                        label = "Packages",
                        href = "${branding.homeUrl.trimEnd('/')}/packages",
                    ))
                }
                add(ContextNavigationItem(
                    id = ContextNavigationIds.PLAYGROUND,
                    label = "Playground",
                    href = "${branding.homeUrl.trimEnd('/')}/playground",
                ))
            }
            add(ContextNavigationItem(
                id = ContextNavigationIds.DOCUMENTATION,
                label = "Documentation",
                href = docsPath,
            ))
            add(ContextNavigationItem(
                id = ContextNavigationIds.API_REFERENCE,
                label = "API Reference",
                href = apiReferencePath,
            ))
        },
    )

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .width(100.percent)
        ) {
            SiteNavigation(
                locale = PortfolioLocale.EN,
                activeDestination = GlobalNavigationDestination.ECOSYSTEM,
                context = navigationContext,
                showLocale = false,
            )
            SectionWrap(maxWidthPx = 1500) {
                content()
            }
        }
    }
}

private fun String.isWithinNavigationPath(root: String): Boolean {
    val normalizedPath = "/${trim().substringBefore('?').substringBefore('#').trim('/')}"
    val normalizedRoot = "/${root.trim().trim('/')}"
    return normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
}
