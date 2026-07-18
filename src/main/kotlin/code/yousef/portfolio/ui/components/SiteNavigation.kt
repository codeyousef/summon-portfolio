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
import codes.yousef.summon.components.styles.*
import codes.yousef.summon.core.style.Color
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
    SiteNavigationStyleSheet()

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
            modifier = Modifier().width(22.px).height(22.px).objectFit(ObjectFit.Contain),
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
            .cssVar("context-accent", context.accentColor)
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
                        modifier = Modifier().width(24.px).height(24.px).objectFit(ObjectFit.Contain),
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

@Composable
private fun SiteNavigationStyleSheet() {
    TypedStyleSheet {
        val shell = StyleSelector.className("site-navigation-shell")
        val compact = StyleSelector.className("site-navigation-compact")
        val primaryMenu = StyleSelector.className("site-nav-primary-menu")
        val primaryToggle = StyleSelector.className("site-nav-primary-toggle")
        val primaryPanel = StyleSelector.className("site-nav-primary-panel")
        val mainLinks = StyleSelector.className("site-nav-main-links")
        val actions = StyleSelector.className("site-nav-actions")
        val navLink = StyleSelector.className("site-nav-link")
        val workLink = StyleSelector.className("site-nav-work-link")
        val disclosure = StyleSelector.className("site-nav-ecosystem-disclosure")
        val disclosureSummary = StyleSelector.className("site-nav-ecosystem-summary")
        val ecosystemLink = StyleSelector.className("site-nav-ecosystem-link")
        val ecosystemPanel = StyleSelector.className("site-nav-ecosystem-panel")
        val contextRail = StyleSelector.className("site-context-nav-rail")
        val contextBrand = StyleSelector.className("site-context-brand")
        val shellAnchor = shell.descendant(StyleSelector.element(StyleElement.Anchor))
        val shellSummary = shell.descendant(StyleSelector.element(StyleElement.Summary))
        val summaryElement = StyleSelector.element(StyleElement.Summary)

        rule(
            shell,
            Modifier()
                .position(Position.Sticky)
                .top(16.px)
                .overflow(Overflow.Visible)
                .backdropBlur(22),
        )
        rule(compact, Modifier().top(0.px))
        rule(primaryMenu, Modifier().minWidth(0))
        rule(primaryToggle, Modifier().display(Display.None), StyleRulePriority.Important)
        rule(
            primaryPanel,
            Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap(16.px),
        )

        val interactive = StyleSelector.all(shellAnchor, shellSummary, primaryToggle)
        rule(
            interactive,
            Modifier().transition(
                property = TransitionProperty.All,
                duration = 160,
                timingFunction = TransitionTimingFunction.Ease,
            ),
        )
        rule(
            StyleSelector.all(
                shellAnchor.pseudoClass(StylePseudoClass.Hover),
                shellSummary.pseudoClass(StylePseudoClass.Hover),
                primaryToggle.pseudoClass(StylePseudoClass.Hover),
            ),
            Modifier()
                .color(Color.WHITE)
                .backgroundColor(Color.rgba(255, 255, 255, 0.09f)),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.all(
                shellAnchor.pseudoClass(StylePseudoClass.FocusVisible),
                shellSummary.pseudoClass(StylePseudoClass.FocusVisible),
                primaryToggle.pseudoClass(StylePseudoClass.FocusVisible),
            ),
            Modifier()
                .outline(3, OutlineStyle.Solid, "#8fe3ff")
                .outlineOffset(3),
            StyleRulePriority.Important,
        )

        rule(
            StyleSelector.all(
                shellAnchor.attribute(StyleAttribute.AriaCurrent, "page"),
                ecosystemLink.attribute(StyleAttribute.data("active"), "true"),
                disclosure.attribute(StyleAttribute.data("active"), "true").child(summaryElement),
            ),
            Modifier()
                .color(Color.WHITE)
                .backgroundColor(Color.rgba(106, 215, 255, 0.14f)),
            StyleRulePriority.Important,
        )
        rule(
            workLink.attribute(StyleAttribute.AriaCurrent, "page"),
            Modifier().boxShadow("0 0 0 3px rgba(255, 137, 176, 0.3)"),
        )
        rule(disclosure.child(summaryElement), Modifier().listStyle(ListStyleType.None))
        rule(
            disclosure.child(summaryElement.pseudoElement(StylePseudoElement.WebkitDetailsMarker)),
            Modifier().display(Display.None),
        )
        rule(
            disclosure
                .not(StyleSelector.Universal.attribute(StyleAttribute.Open))
                .descendant(ecosystemPanel),
            Modifier().display(Display.None),
        )
        rule(ecosystemPanel, Modifier().zIndex(1000))
        rule(
            disclosure.attribute(StyleAttribute.Open).child(summaryElement),
            Modifier()
                .color(Color.WHITE)
                .backgroundColor(Color.rgba(255, 255, 255, 0.1f)),
            StyleRulePriority.Important,
        )
        rule(
            contextRail,
            Modifier()
                .scrollbarWidth(ScrollbarWidth.Thin)
                .scrollbarColor(Color.rgba(143, 227, 255, 0.35f), Color.TRANSPARENT),
        )
        rule(
            StyleSelector.className("site-context-link")
                .attribute(StyleAttribute.AriaCurrent, "page"),
            Modifier()
                .color(Color.WHITE)
                .backgroundColor("color-mix(in srgb, ${cssVar("context-accent")} 18%, transparent)")
                .boxShadow("inset 0 -2px 0 ${cssVar("context-accent")}"),
            StyleRulePriority.Important,
        )

        media(MediaQuery.MaxWidth(1040)) {
            rule(
                StyleSelector.className("site-nav-primary-row"),
                Modifier().padding(10.px, 12.px),
                StyleRulePriority.Important,
            )
            rule(
                primaryMenu,
                Modifier().flex(grow = 0, shrink = 0, basis = "auto"),
                StyleRulePriority.Important,
            )
            rule(
                primaryToggle,
                Modifier()
                    .display(Display.Flex)
                    .backgroundColor(Color.TRANSPARENT)
                    .borderWidth(0),
                StyleRulePriority.Important,
            )
            rule(
                primaryPanel,
                Modifier()
                    .display(Display.None)
                    .position(Position.Absolute)
                    .insetInlineEnd(0)
                    .top("calc(100% + 10px)")
                    .zIndex(1000)
                    .width("min(88vw, 340px)")
                    .maxHeight("calc(100vh - 110px)")
                    .overflowY(Overflow.Auto)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch)
                    .padding(14.px)
                    .backgroundColor("#071b2e")
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(Color.rgba(255, 255, 255, 0.25f))
                    .borderRadius(16.px)
                    .boxShadow("0 24px 70px rgba(0, 0, 0, 0.5)"),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.all(mainLinks, actions),
                Modifier()
                    .width(100.percent)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch)
                    .gap(6.px),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.all(navLink, workLink, disclosureSummary, ecosystemLink),
                Modifier().display(Display.Flex).width(100.percent),
                StyleRulePriority.Important,
            )
            rule(disclosureSummary, Modifier().justifyContent(JustifyContent.SpaceBetween))
            rule(disclosure, Modifier().width(100.percent))
            rule(
                ecosystemPanel,
                Modifier()
                    .position(Position.Static)
                    .minWidth(0)
                    .width(100.percent)
                    .marginTop(6.px)
                    .boxShadow("none"),
                StyleRulePriority.Important,
            )
        }

        media(MediaQuery.MaxWidth(640)) {
            rule(
                shell,
                Modifier().top(8.px).borderRadius(16.px),
                StyleRulePriority.Important,
            )
            rule(compact, Modifier().top(0.px), StyleRulePriority.Important)
            rule(
                contextRail,
                Modifier().padding(8.px, 10.px),
                StyleRulePriority.Important,
            )
            rule(
                contextBrand,
                Modifier()
                    .position(Position.Sticky)
                    .insetInlineStart(0)
                    .backgroundColor("#071b2e")
                    .zIndex(2),
            )
        }

        media(MediaQuery.PrefersReducedMotion) {
            rule(interactive, Modifier().transitionProperty(TransitionProperty.None), StyleRulePriority.Important)
        }
    }
}
