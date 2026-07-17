package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.pathPrefix
import code.yousef.portfolio.i18n.strings.NavigationStrings
import code.yousef.portfolio.ssr.aetherMarketingUrl
import code.yousef.portfolio.ssr.blogUrl
import code.yousef.portfolio.ssr.materiaMarketingUrl
import code.yousef.portfolio.ssr.portfolioBaseUrl
import code.yousef.portfolio.ssr.seenMarketingUrl
import code.yousef.portfolio.ssr.sigilMarketingUrl
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.action.UiAction
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.html.Details
import codes.yousef.summon.components.html.Header
import codes.yousef.summon.components.html.Nav
import codes.yousef.summon.components.html.Summary
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

enum class GlobalNavigationDestination {
    HOME,
    PROJECTS,
    PHOTOGRAPHY,
    BLOG,
    ECOSYSTEM,
    WORK,
}

data class ContextNavigationItem(
    val id: String,
    val label: String,
    val href: String,
)

data class PageNavigationContext(
    val name: String,
    val homeHref: String,
    val accentColor: String,
    val items: List<ContextNavigationItem>,
    val activeItemId: String? = null,
    val logoPath: String? = null,
)

object ContextNavigationIds {
    const val OVERVIEW = "overview"
    const val DOCUMENTATION = "documentation"
    const val API_REFERENCE = "api-reference"
    const val PLAYGROUND = "playground"
    const val PACKAGES = "packages"
    const val CONSULTING = "consulting"
    const val FULL_TIME = "full-time"
    const val CONTACT = "contact"
}

private data class PrimaryNavigationItem(
    val destination: GlobalNavigationDestination,
    val label: String,
    val href: String,
)

private data class EcosystemNavigationItem(
    val id: String,
    val label: String,
    val href: String,
    val logoPath: String,
)

@Composable
fun SiteNavigation(
    locale: PortfolioLocale = PortfolioLocale.EN,
    activeDestination: GlobalNavigationDestination? = null,
    context: PageNavigationContext? = null,
    modifier: Modifier = Modifier(),
    compact: Boolean = false,
    showLocale: Boolean = true,
) {
    GlobalStyle(SITE_NAVIGATION_CSS)

    Header(
        modifier = modifier
            .className(if (compact) "site-navigation-shell site-navigation-compact" else "site-navigation-shell")
            .width(100.percent)
            .backgroundColor("rgba(0, 18, 34, 0.94)")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER_STRONG)
            .borderRadius(if (compact) 12.px else PortfolioTheme.Radii.lg)
            .boxShadow("0 18px 50px rgba(0, 0, 0, 0.28)")
            .zIndex(100),
    ) {
        Nav(
            modifier = Modifier()
                .attribute("aria-label", "Primary")
                .dataAttribute("navigation-layer", "global"),
        ) {
            PrimaryNavigation(locale, activeDestination, context, showLocale)
        }

        context?.let { ContextNavigation(it) }
    }
}

@Composable
private fun PrimaryNavigation(
    locale: PortfolioLocale,
    activeDestination: GlobalNavigationDestination?,
    context: PageNavigationContext?,
    showLocale: Boolean,
) {
    val menuId = "site-primary-menu-${locale.code}"
    Row(
        Modifier()
            .className("site-nav-primary-row")
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(PortfolioTheme.Spacing.md)
            .padding(12.px, 16.px),
    ) {
        GlobalBrandLink(locale, activeDestination == GlobalNavigationDestination.HOME)

        Box(
            modifier = Modifier()
                .className("site-nav-primary-menu")
                .position(Position.Relative)
                .flex(grow = 1, shrink = 1, basis = "auto"),
        ) {
            Button(
                onClick = {},
                label = if (locale == PortfolioLocale.AR) "القائمة" else "Menu",
                modifier = Modifier()
                    .className("site-nav-primary-toggle")
                    .attribute("aria-label", "Open primary navigation")
                    .attribute("aria-controls", menuId)
                    .attribute("aria-expanded", "false")
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .height(44.px)
                    .padding(10.px, 14.px)
                    .borderRadius(12.px)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY),
                variant = ButtonVariant.GHOST,
                disabled = false,
                action = UiAction.ToggleVisibility(menuId),
            )
            Box(
                modifier = Modifier()
                    .className("site-nav-primary-panel")
                    .id(menuId),
            ) {
                Row(
                    Modifier()
                        .className("site-nav-main-links")
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.Center)
                        .gap(4.px),
                ) {
                    primaryNavigationItems(locale).forEach { item ->
                        PrimaryNavigationLink(item, activeDestination == item.destination)
                    }
                    EcosystemDisclosure(
                        label = NavigationStrings.ecosystem.resolve(locale),
                        active = activeDestination == GlobalNavigationDestination.ECOSYSTEM,
                        currentProduct = context?.name,
                    )
                }
                Row(
                    Modifier()
                        .className("site-nav-actions")
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.FlexEnd)
                        .gap(8.px),
                ) {
                    WorkWithMeLink(locale, activeDestination == GlobalNavigationDestination.WORK)
                    if (showLocale) {
                        LocaleToggle(current = locale, path = localizedTogglePath(activeDestination, context))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalBrandLink(locale: PortfolioLocale, active: Boolean) {
    Link(
        href = localizedPortfolioHref(locale, ""),
        modifier = currentPageModifier(
            Modifier()
                .className("site-nav-brand")
                .display(Display.InlineFlex)
                .alignItems(AlignItems.Center)
                .gap(10.px)
                .padding(8.px, 10.px)
                .borderRadius(12.px)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .textDecoration(TextDecoration.None)
                .fontSize(0.86.rem)
                .fontWeight(800)
                .letterSpacing("0.16em")
                .whiteSpace(WhiteSpace.NoWrap)
                .dataAttribute("nav-id", "home"),
            active,
        ),
        ariaLabel = "Yousef home",
    ) {
        Text("YOUSEF")
    }
}

@Composable
private fun PrimaryNavigationLink(item: PrimaryNavigationItem, active: Boolean) {
    Link(
        href = item.href,
        modifier = currentPageModifier(primaryLinkModifier().dataAttribute("nav-id", item.destination.name.lowercase()), active),
    ) {
        Text(item.label)
    }
}

@Composable
private fun EcosystemDisclosure(label: String, active: Boolean, currentProduct: String?) {
    Details(
        modifier = Modifier()
            .className("site-nav-ecosystem-disclosure")
            .position(Position.Relative)
            .dataAttribute("nav-id", "ecosystem")
            .dataAttribute("active", active.toString()),
    ) {
        Summary(
            modifier = primaryLinkModifier()
                .className("site-nav-ecosystem-summary")
                .cursor(Cursor.Pointer)
                .display(Display.InlineFlex)
                .alignItems(AlignItems.Center)
                .gap(6.px),
        ) {
            Text(label)
            Text("⌄", Modifier().fontSize(0.75.rem).attribute("aria-hidden", "true"))
        }
        Column(
            Modifier()
                .className("site-nav-ecosystem-panel")
                .position(Position.Absolute)
                .top("calc(100% + 10px)")
                .left(0.px)
                .minWidth(230.px)
                .padding(8.px)
                .gap(4.px)
                .backgroundColor("#071b2e")
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER_STRONG)
                .borderRadius(16.px)
                .boxShadow("0 24px 70px rgba(0, 0, 0, 0.5)"),
        ) {
            ecosystemNavigationItems().forEach { item ->
                EcosystemNavigationLink(item, currentProduct.equals(item.label, ignoreCase = true))
            }
        }
    }
}

@Composable
private fun EcosystemNavigationLink(
    item: EcosystemNavigationItem,
    active: Boolean,
) {
    Link(
        href = item.href,
        modifier = activeVisualModifier(
            Modifier()
                .className("site-nav-ecosystem-link")
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(10.px)
                .padding(10.px, 12.px)
                .borderRadius(10.px)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .textDecoration(TextDecoration.None)
                .fontWeight(650)
                .dataAttribute("nav-id", item.id),
            active,
        ),
    ) {
        Image(
            src = item.logoPath,
            alt = "",
            modifier = Modifier().width(22.px).height(22.px).style("object-fit", "contain"),
        )
        Text(item.label)
    }
}

@Composable
private fun WorkWithMeLink(
    locale: PortfolioLocale,
    active: Boolean,
) {
    Link(
        href = localizedPortfolioHref(locale, "/services"),
        modifier = currentPageModifier(
            Modifier()
                .className("site-nav-work-link")
                .display(Display.InlineFlex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .padding(10.px, 16.px)
                .borderRadius(999.px)
                .backgroundColor(PortfolioTheme.Colors.ACCENT)
                .color("#ffffff")
                .textDecoration(TextDecoration.None)
                .fontSize(0.82.rem)
                .fontWeight(750)
                .whiteSpace(WhiteSpace.NoWrap)
                .dataAttribute("nav-id", "work"),
            active,
        ),
    ) {
        Text(NavigationStrings.workWithMe.resolve(locale))
    }
}

@Composable
private fun ContextNavigation(context: PageNavigationContext) {
    Nav(
        modifier = Modifier()
            .className("site-context-nav")
            .attribute("aria-label", "${context.name} navigation")
            .dataAttribute("navigation-layer", "context")
            .style("--context-accent", context.accentColor)
            .borderWidth(1, BorderSide.Top)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER),
    ) {
        Row(
            Modifier()
                .className("site-context-nav-rail")
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(8.px)
                .padding(9.px, 14.px)
                .overflowX(Overflow.Auto)
                .whiteSpace(WhiteSpace.NoWrap),
        ) {
            Row(
                modifier = Modifier()
                    .className("site-context-brand")
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .gap(8.px)
                    .padding(7.px, 10.px)
                    .borderRadius(10.px)
                    .color(context.accentColor)
                    .textDecoration(TextDecoration.None)
                    .fontWeight(850)
                    .dataAttribute("nav-id", "context-brand"),
            ) {
                context.logoPath?.let { logoPath ->
                    Image(
                        src = logoPath,
                        alt = "",
                        modifier = Modifier().width(24.px).height(24.px).style("object-fit", "contain"),
                    )
                }
                Text(context.name)
            }

            Box(
                Modifier()
                    .width(1.px)
                    .height(24.px)
                    .backgroundColor(PortfolioTheme.Colors.BORDER_STRONG)
                    .marginRight(2.px)
                    .attribute("aria-hidden", "true"),
            ) {}

            context.items.forEach { item ->
                Link(
                    href = item.href,
                    modifier = currentPageModifier(
                        Modifier()
                            .className("site-context-link")
                            .padding(8.px, 12.px)
                            .borderRadius(10.px)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .textDecoration(TextDecoration.None)
                            .fontSize(0.83.rem)
                            .fontWeight(700)
                            .dataAttribute("nav-id", "context-${item.id}"),
                        context.activeItemId == item.id,
                    ),
                ) {
                    Text(item.label)
                }
            }
        }
    }
}

fun workWithMeNavigationContext(
    locale: PortfolioLocale,
    activeItemId: String,
): PageNavigationContext {
    val base = portfolioBaseUrl().trimEnd('/')
    val prefix = locale.pathPrefix()
    return PageNavigationContext(
        name = NavigationStrings.workWithMe.resolve(locale),
        homeHref = "$base$prefix/services",
        accentColor = PortfolioTheme.Colors.ACCENT,
        activeItemId = activeItemId,
        items = listOf(
            ContextNavigationItem(
                ContextNavigationIds.CONSULTING,
                NavigationStrings.consulting.resolve(locale),
                "$base$prefix/services",
            ),
            ContextNavigationItem(
                ContextNavigationIds.FULL_TIME,
                NavigationStrings.fullTime.resolve(locale),
                "$base$prefix/full-time",
            ),
            ContextNavigationItem(
                ContextNavigationIds.CONTACT,
                NavigationStrings.contact.resolve(locale),
                "#contact",
            ),
        ),
    )
}

private fun primaryNavigationItems(locale: PortfolioLocale): List<PrimaryNavigationItem> = listOf(
    PrimaryNavigationItem(
        GlobalNavigationDestination.PROJECTS,
        NavigationStrings.projects.resolve(locale),
        localizedPortfolioHref(locale, "/projects"),
    ),
    PrimaryNavigationItem(
        GlobalNavigationDestination.PHOTOGRAPHY,
        NavigationStrings.photography.resolve(locale),
        "${portfolioBaseUrl().trimEnd('/')}/photography",
    ),
    PrimaryNavigationItem(
        GlobalNavigationDestination.BLOG,
        NavigationStrings.blog.resolve(locale),
        blogUrl(),
    ),
)

private fun ecosystemNavigationItems(): List<EcosystemNavigationItem> = listOf(
    EcosystemNavigationItem("summon", "Summon", summonMarketingUrl(), "/static/summon-logo.png"),
    EcosystemNavigationItem("materia", "Materia", materiaMarketingUrl(), "/static/materia-logo.png"),
    EcosystemNavigationItem("sigil", "Sigil", sigilMarketingUrl(), "/static/sigil-logo.png"),
    EcosystemNavigationItem("aether", "Aether", aetherMarketingUrl(), "/static/aether-logo.png"),
    EcosystemNavigationItem("seen", "Seen", seenMarketingUrl(), "/static/seen-logo.png"),
)

private fun localizedPortfolioHref(locale: PortfolioLocale, path: String): String =
    "${portfolioBaseUrl().trimEnd('/')}${locale.pathPrefix()}$path"

private fun localizedTogglePath(
    activeDestination: GlobalNavigationDestination?,
    context: PageNavigationContext?,
): String = when (activeDestination) {
    GlobalNavigationDestination.PROJECTS -> "/projects"
    GlobalNavigationDestination.WORK -> if (context?.activeItemId == ContextNavigationIds.FULL_TIME) "/full-time" else "/services"
    else -> ""
}

private fun primaryLinkModifier(): Modifier = Modifier()
    .className("site-nav-link")
    .padding(9.px, 11.px)
    .borderRadius(10.px)
    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
    .textDecoration(TextDecoration.None)
    .fontSize(0.8.rem)
    .fontWeight(680)
    .whiteSpace(WhiteSpace.NoWrap)

private fun currentPageModifier(modifier: Modifier, active: Boolean): Modifier =
    if (active) modifier.attribute("aria-current", "page").dataAttribute("active", "true") else modifier

private fun activeVisualModifier(modifier: Modifier, active: Boolean): Modifier =
    if (active) modifier.dataAttribute("active", "true") else modifier

private val SITE_NAVIGATION_CSS = """
    .site-navigation-shell {
        position: sticky;
        top: 16px;
        overflow: visible;
        backdrop-filter: blur(22px);
        -webkit-backdrop-filter: blur(22px);
    }
    .site-navigation-compact { top: 0; }
    .site-nav-primary-menu { min-width: 0; }
    .site-nav-primary-toggle { display: none !important; }
    .site-nav-primary-panel {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
    }
    .site-navigation-shell a,
    .site-navigation-shell summary,
    .site-nav-primary-toggle { transition: color 160ms ease, background-color 160ms ease, border-color 160ms ease, transform 160ms ease; }
    .site-navigation-shell a:hover,
    .site-navigation-shell summary:hover,
    .site-nav-primary-toggle:hover { color: #ffffff !important; background: rgba(255, 255, 255, 0.09) !important; }
    .site-navigation-shell a:focus-visible,
    .site-navigation-shell summary:focus-visible,
    .site-nav-primary-toggle:focus-visible {
        outline: 3px solid #8fe3ff !important;
        outline-offset: 3px;
    }
    .site-navigation-shell a[aria-current="page"],
    .site-nav-ecosystem-link[data-active="true"],
    .site-nav-ecosystem-disclosure[data-active="true"] > summary {
        color: #ffffff !important;
        background: rgba(106, 215, 255, 0.14);
    }
    .site-nav-work-link[aria-current="page"] { box-shadow: 0 0 0 3px rgba(255, 137, 176, 0.3); }
    .site-nav-ecosystem-disclosure > summary { list-style: none; }
    .site-nav-ecosystem-disclosure > summary::-webkit-details-marker { display: none; }
    .site-nav-ecosystem-disclosure:not([open]) .site-nav-ecosystem-panel { display: none; }
    .site-nav-ecosystem-panel { z-index: 1000; }
    .site-nav-ecosystem-disclosure[open] > summary { color: #ffffff !important; background: rgba(255, 255, 255, 0.1); }
    .site-context-nav-rail { scrollbar-width: thin; scrollbar-color: rgba(143, 227, 255, 0.35) transparent; }
    .site-context-link[aria-current="page"] {
        color: #ffffff !important;
        background: color-mix(in srgb, var(--context-accent) 18%, transparent);
        box-shadow: inset 0 -2px 0 var(--context-accent);
    }
    @media (max-width: 1040px) {
        .site-nav-primary-row { padding: 10px 12px !important; }
        .site-nav-primary-menu { flex: 0 0 auto !important; }
        .site-nav-primary-toggle {
            display: flex !important;
            background: transparent !important;
            border: 0 !important;
        }
        .site-nav-primary-panel {
            display: none;
            position: absolute !important;
            inset-inline-end: 0;
            top: calc(100% + 10px);
            z-index: 1000;
            width: min(88vw, 340px);
            max-height: calc(100vh - 110px);
            overflow-y: auto;
            flex-direction: column !important;
            align-items: stretch !important;
            padding: 14px;
            background: #071b2e;
            border: 1px solid rgba(255,255,255,0.25);
            border-radius: 16px;
            box-shadow: 0 24px 70px rgba(0, 0, 0, 0.5);
        }
        .site-nav-main-links,
        .site-nav-actions {
            width: 100%;
            flex-direction: column !important;
            align-items: stretch !important;
            gap: 6px !important;
        }
        .site-nav-link,
        .site-nav-work-link,
        .site-nav-ecosystem-summary,
        .site-nav-ecosystem-link { display: flex !important; width: 100%; }
        .site-nav-ecosystem-summary { justify-content: space-between; }
        .site-nav-ecosystem-disclosure { width: 100%; }
        .site-nav-ecosystem-panel {
            position: static !important;
            min-width: 0 !important;
            width: 100%;
            margin-top: 6px;
            box-shadow: none !important;
        }
    }
    @media (max-width: 640px) {
        .site-navigation-shell { top: 8px; border-radius: 16px !important; }
        .site-navigation-compact { top: 0; }
        .site-context-nav-rail { padding: 8px 10px !important; }
        .site-context-brand { position: sticky; inset-inline-start: 0; background: #071b2e; z-index: 2; }
    }
    @media (prefers-reduced-motion: reduce) {
        .site-navigation-shell a,
        .site-navigation-shell summary,
        .site-nav-primary-toggle { transition: none !important; }
    }
""".trimIndent()
