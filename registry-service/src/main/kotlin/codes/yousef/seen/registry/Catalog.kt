package codes.yousef.seen.registry

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.action.UiAction
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormTextField
import codes.yousef.summon.components.forms.FormTextFieldType
import codes.yousef.summon.components.html.Footer
import codes.yousef.summon.components.html.H1
import codes.yousef.summon.components.html.H2
import codes.yousef.summon.components.html.H3
import codes.yousef.summon.components.html.Details
import codes.yousef.summon.components.html.Header
import codes.yousef.summon.components.html.Main
import codes.yousef.summon.components.html.Nav
import codes.yousef.summon.components.html.Section
import codes.yousef.summon.components.html.Summary
import codes.yousef.summon.components.input.Button
import codes.yousef.summon.components.input.ButtonVariant
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.components.styles.*
import codes.yousef.summon.modifier.*
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal const val CATALOG_QUERY_MAX_LENGTH = 128

data class CatalogNavigationLinks(
    val portfolio: String,
    val projects: String,
    val photography: String,
    val blog: String,
    val work: String,
    val summon: String,
    val materia: String,
    val sigil: String,
    val aether: String,
    val seen: String,
    val seenPackages: String,
    val seenPlayground: String,
    val seenDocs: String,
    val seenApiReference: String,
) {
    companion object {
        fun development(): CatalogNavigationLinks =
            fromRegistryOrigin("https://seen.dev.yousef.codes/packages")

        fun fromRegistryOrigin(rawOrigin: String): CatalogNavigationLinks {
            val uri = runCatching { URI.create(rawOrigin.trim().trimEnd('/')) }
                .getOrElse { throw IllegalArgumentException("Registry origin must be a valid URL", it) }
            require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null) {
                "Registry origin must be an absolute HTTPS URL without user info"
            }
            require(uri.rawQuery == null && uri.rawFragment == null && uri.path.trimEnd('/') == "/packages") {
                "Registry origin must end at /packages without a query or fragment"
            }

            val host = uri.host.lowercase(Locale.ROOT)
            val development = when (host) {
                "seen.dev.yousef.codes" -> true
                "seen.yousef.codes" -> false
                else -> throw IllegalArgumentException(
                    "Registry navigation supports seen.dev.yousef.codes and seen.yousef.codes origins",
                )
            }
            val seenBase = URI(uri.scheme, null, uri.host, uri.port, null, null, null).toASCIIString()
            val portfolioBase = if (development) "https://dev.yousef.codes" else "https://www.yousef.codes"
            fun productBase(product: String): String = if (development) {
                "https://$product.dev.yousef.codes"
            } else {
                "https://$product.yousef.codes"
            }

            return CatalogNavigationLinks(
                portfolio = portfolioBase,
                projects = "$portfolioBase/projects",
                photography = "$portfolioBase/photography",
                blog = "$portfolioBase/blog",
                work = "$portfolioBase/services",
                summon = productBase("summon"),
                materia = productBase("materia"),
                sigil = productBase("sigil"),
                aether = productBase("aether"),
                seen = seenBase,
                seenPackages = "$seenBase/packages",
                seenPlayground = "$seenBase/playground",
                seenDocs = "$seenBase/docs",
                seenApiReference = "$seenBase/docs/api-reference",
            )
        }
    }
}

object CatalogRenderer {
    private val lock = Any()

    fun render(
        packages: List<PackageRecord>,
        query: String = "",
        navigationLinks: CatalogNavigationLinks = CatalogNavigationLinks.development(),
    ): String = synchronized(lock) {
        renderLocked(
            title = "Packages · Seen",
            description = "Browse public source packages for the Seen programming language.",
        ) { Catalog(packages, normalizeCatalogQuery(query), navigationLinks) }
    }

    fun renderPackage(
        pkg: PackageRecord,
        releases: List<ReleaseRecord>,
        navigationLinks: CatalogNavigationLinks = CatalogNavigationLinks.development(),
    ): String = synchronized(lock) {
        renderLocked(
            title = "${pkg.identity} · Seen packages",
            description = pkg.description ?: "Public releases of ${pkg.identity} for Seen.",
        ) { PackageDetail(pkg, releases, navigationLinks) }
    }

    fun renderRelease(
        pkg: PackageRecord,
        release: ReleaseRecord,
        navigationLinks: CatalogNavigationLinks = CatalogNavigationLinks.development(),
    ): String = synchronized(lock) {
        renderLocked(
            title = "${pkg.identity} ${release.version} · Seen packages",
            description = "Release ${release.version} of ${pkg.identity} for Seen.",
        ) { ReleaseDetail(pkg, release, navigationLinks) }
    }

    fun renderUnavailable(
        notFound: Boolean,
        navigationLinks: CatalogNavigationLinks = CatalogNavigationLinks.development(),
    ): String = synchronized(lock) {
        renderLocked(
            title = if (notFound) "Package not found · Seen" else "Registry unavailable · Seen",
            description = if (notFound) {
                "The requested Seen package or release is not public."
            } else {
                "The Seen package registry is temporarily unavailable."
            },
        ) { RegistryUnavailable(notFound, navigationLinks) }
    }

    private fun renderLocked(
        title: String,
        description: String,
        content: @Composable () -> Unit,
    ): String {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            renderer.renderHeadElements {
                title(title)
                meta(name = "description", content = description)
                meta(property = "og:title", content = title)
            }
            return renderer.renderComposableRootWithHydration("en", "ltr") { content() }
        } finally {
            clearPlatformRenderer()
        }
    }
}

internal fun normalizeCatalogQuery(rawQuery: String?): String =
    rawQuery.orEmpty().trim().take(CATALOG_QUERY_MAX_LENGTH)

internal fun normalizeCatalogQueryParameter(rawQuery: String?): String {
    val decoded = rawQuery?.let { encoded ->
        runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }
            .getOrDefault(encoded)
    }
    return normalizeCatalogQuery(decoded)
}

internal fun filterCatalogPackages(
    packages: List<PackageRecord>,
    rawQuery: String?,
): List<PackageRecord> {
    val query = normalizeCatalogQuery(rawQuery)
    val sorted = packages.sortedBy { it.identity.lowercase(Locale.ROOT) }
    if (query.isEmpty()) return sorted

    val terms = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
    return sorted.filter { pkg ->
        val searchable = listOfNotNull(
            pkg.identity,
            pkg.description,
            pkg.licenseSpdx,
            pkg.latestActiveVersion,
        ).joinToString(" ").lowercase(Locale.ROOT)
        terms.all(searchable::contains)
    }
}

@Composable
private fun Catalog(
    packages: List<PackageRecord>,
    query: String,
    navigationLinks: CatalogNavigationLinks,
) {
    val visiblePackages = filterCatalogPackages(packages, query)
    CatalogScaffold(navigationLinks) {
        H1(modifier = pageTitleModifier()) {
            Text("Seen packages")
        }
        Text(
            "Discover source packages that complete registry review and are ready for the Seen resolver.",
            Modifier().fontSize(18).lineHeight(1.7).color(CatalogTheme.TEXT_SECONDARY),
        )

        CatalogSearch(query)
        Text(
            resultSummary(visiblePackages.size, query),
            Modifier()
                .color(CatalogTheme.TEXT_MUTED)
                .fontSize(14)
                .attribute("role", "status")
                .attribute("aria-live", "polite")
                .attribute("aria-atomic", "true"),
        )

        when {
            packages.isEmpty() -> CatalogStatePanel(
                title = "No public packages yet",
                message = "Packages will appear here after their first public release completes registry review.",
            )
            visiblePackages.isEmpty() -> CatalogStatePanel(
                title = "No packages match “$query”",
                message = "Try a package identity, description, license, or version.",
                action = { CatalogTextLink("Clear search", "/packages") },
            )
            else -> PackageGrid(visiblePackages)
        }
    }
}

@Composable
private fun CatalogSearch(query: String) {
    Form(
        action = "/packages",
        method = FormMethod.Get,
        modifier = Modifier()
            .className("seen-catalog-search")
            .display(Display.Flex)
            .alignItems(AlignItems.FlexEnd)
            .gap("12px")
            .flexWrap(FlexWrap.Wrap)
            .padding(20)
            .backgroundColor(CatalogTheme.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER)
            .borderRadius(18)
            .attribute("role", "search")
            .attribute("aria-label", "Search packages"),
    ) {
        FormTextField(
            name = "q",
            label = "Search packages",
            defaultValue = query,
            placeholder = "Search by name, description, license, or latest version",
            type = FormTextFieldType.Search,
            autoComplete = "off",
            maxLength = CATALOG_QUERY_MAX_LENGTH,
            id = "seen-package-search",
            modifier = Modifier()
                .className("seen-package-search-field")
                .flex(grow = 1, shrink = 1, basis = "320px")
                .minWidth(0)
                .gap("8px")
                .margin(0),
            fieldModifier = Modifier()
                .className("seen-package-search-input")
                .attribute("aria-label", "Search packages")
                .width("100%")
                .padding(12)
                .backgroundColor(CatalogTheme.INPUT)
                .color(CatalogTheme.TEXT_PRIMARY)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(CatalogTheme.BORDER_STRONG)
                .borderRadius(12)
                .fontSize(16),
        )
        FormButton(
            text = "Search",
            modifier = Modifier()
                .className("seen-search-button")
                .backgroundColor(CatalogTheme.ACCENT)
                .color("#001a2c")
                .padding(12, 20)
                .borderRadius(12)
                .fontWeight(800),
            fullWidth = false,
            ariaLabel = "Search packages",
        )
        if (query.isNotEmpty()) {
            CatalogTextLink("Clear", "/packages")
        }
    }
}

@Composable
private fun PackageGrid(packages: List<PackageRecord>) {
    Section(modifier = Modifier().attribute("aria-label", "Package results")) {
        Row(
            Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Stretch)
                .gap("16px")
                .flexWrap(FlexWrap.Wrap),
        ) {
            packages.forEach(::PackageCard)
        }
    }
}

@Composable
private fun PackageCard(pkg: PackageRecord) {
    Link(
        href = "/packages/${pkg.identity}",
        modifier = Modifier()
            .className("seen-package-card")
            .flex(grow = 1, shrink = 1, basis = "300px")
            .minWidth(0)
            .padding(22)
            .backgroundColor(CatalogTheme.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER)
            .borderRadius(18)
            .color(CatalogTheme.TEXT_PRIMARY)
            .textDecoration(TextDecoration.None)
            .overflowWrap(OverflowWrap.Anywhere),
        ariaLabel = "View ${pkg.identity}, latest version ${pkg.latestActiveVersion}",
    ) {
        Column(Modifier().gap("14px")) {
            Row(
                Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.FlexStart)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .gap("12px")
                    .flexWrap(FlexWrap.Wrap),
            ) {
                H2(modifier = Modifier().margin(0).fontSize(22).fontWeight(800)) {
                    Text(pkg.identity)
                }
                PackageChip("v${pkg.latestActiveVersion}", CatalogTheme.ACCENT_SOFT, CatalogTheme.ACCENT)
            }
            Text(
                pkg.description ?: "A public Seen source package.",
                Modifier().color(CatalogTheme.TEXT_SECONDARY).lineHeight(1.6),
            )
            Row(Modifier().display(Display.Flex).gap("8px").flexWrap(FlexWrap.Wrap)) {
                pkg.licenseSpdx?.let { PackageChip(it, CatalogTheme.SURFACE_STRONG, CatalogTheme.TEXT_SECONDARY) }
                PackageChip("Public", CatalogTheme.SUCCESS_SOFT, CatalogTheme.SUCCESS)
            }
        }
    }
}

@Composable
private fun PackageDetail(
    pkg: PackageRecord,
    releases: List<ReleaseRecord>,
    navigationLinks: CatalogNavigationLinks,
) {
    CatalogScaffold(navigationLinks) {
        Breadcrumbs(listOf("Packages" to "/packages"), current = pkg.identity)
        H1(modifier = pageTitleModifier().overflowWrap(OverflowWrap.Anywhere)) {
            Text(pkg.identity)
        }
        pkg.description?.let {
            Text(it, Modifier().fontSize(18).lineHeight(1.7).color(CatalogTheme.TEXT_SECONDARY))
        }
        Row(Modifier().display(Display.Flex).gap("8px").flexWrap(FlexWrap.Wrap)) {
            pkg.licenseSpdx?.let { PackageChip("License $it", CatalogTheme.SURFACE_STRONG, CatalogTheme.TEXT_SECONDARY) }
            pkg.latestActiveVersion?.let { PackageChip("Latest $it", CatalogTheme.ACCENT_SOFT, CatalogTheme.ACCENT) }
        }

        H2(modifier = sectionTitleModifier()) { Text("Releases") }
        if (releases.isEmpty()) {
            CatalogStatePanel(
                title = "No public releases",
                message = "This package does not currently have a public release.",
            )
        } else {
            Column(Modifier().gap("12px")) {
                releases.forEach { release ->
                    ReleaseRow(pkg, release)
                }
            }
        }
    }
}

@Composable
private fun ReleaseRow(pkg: PackageRecord, release: ReleaseRecord) {
    Link(
        href = "/packages/${pkg.identity}/${release.version}",
        modifier = Modifier()
            .className("seen-release-row")
            .padding(18)
            .backgroundColor(CatalogTheme.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER)
            .borderRadius(16)
            .color(CatalogTheme.TEXT_PRIMARY)
            .textDecoration(TextDecoration.None),
        ariaLabel = "View ${pkg.identity} ${release.version}, ${releaseStatus(release)}",
    ) {
        Row(
            Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap("14px")
                .flexWrap(FlexWrap.Wrap),
        ) {
            Column(Modifier().gap("5px")) {
                H3(modifier = Modifier().margin(0).fontSize(20).fontWeight(800)) {
                    Text(release.version)
                }
                Text("Published ${release.timestamps.activatedAt ?: release.timestamps.updatedAt}", Modifier().fontSize(13).color(CatalogTheme.TEXT_MUTED))
            }
            ReleaseStatusBadge(release)
        }
    }
}

@Composable
private fun ReleaseDetail(
    pkg: PackageRecord,
    release: ReleaseRecord,
    navigationLinks: CatalogNavigationLinks,
) {
    CatalogScaffold(navigationLinks) {
        Breadcrumbs(
            ancestors = listOf(
                "Packages" to "/packages",
                pkg.identity to "/packages/${pkg.identity}",
            ),
            current = release.version,
        )
        H1(modifier = pageTitleModifier().overflowWrap(OverflowWrap.Anywhere)) {
            Text("${pkg.identity} ${release.version}")
        }
        ReleaseStatusBadge(release)

        Section(
            modifier = Modifier()
                .padding(22)
                .backgroundColor(CatalogTheme.SURFACE)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(CatalogTheme.BORDER)
                .borderRadius(18),
        ) {
            H2(modifier = sectionTitleModifier()) { Text("Release metadata") }
            Column(Modifier().gap("18px")) {
                MetadataItem("SHA-256", release.archive.sha256, monospace = true)
                MetadataItem("Manifest SHA-256", release.manifestSha256, monospace = true)
                MetadataItem("Compressed size", formatBytes(release.archive.compressedBytes))
                MetadataItem("Archive format", release.archive.format)
            }
        }

        if (release.state.availability == "available" && release.links.download != null) {
            Text(
                "This release is active and available to the Seen resolver.",
                Modifier().color(CatalogTheme.SUCCESS).lineHeight(1.6),
            )
            Link(
                href = release.links.download,
                modifier = Modifier()
                    .className("seen-download-link")
                    .backgroundColor(CatalogTheme.ACCENT)
                    .color("#001a2c")
                    .padding(12, 18)
                    .borderRadius(12)
                    .fontWeight(800)
                    .textDecoration(TextDecoration.None),
                ariaLabel = "Download ${pkg.identity} ${release.version} source archive",
            ) {
                Text("Download source archive")
            }
        } else {
            Text(
                "Archive download is unavailable for this release state.",
                Modifier().color(CatalogTheme.WARNING).lineHeight(1.6),
            )
        }
    }
}

@Composable
private fun RegistryUnavailable(
    notFound: Boolean,
    navigationLinks: CatalogNavigationLinks,
) {
    CatalogScaffold(navigationLinks) {
        Column(
            Modifier()
                .gap("18px")
                .padding(24)
                .backgroundColor(CatalogTheme.SURFACE)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(CatalogTheme.BORDER)
                .borderRadius(18)
                .attribute("role", if (notFound) "status" else "alert"),
        ) {
            H1(modifier = pageTitleModifier()) {
                Text(if (notFound) "Package not found" else "Registry unavailable")
            }
            Text(
                if (notFound) {
                    "The package or release is not public."
                } else {
                    "The registry could not load this page. Try again shortly."
                },
                Modifier().fontSize(18).lineHeight(1.7).color(CatalogTheme.TEXT_SECONDARY),
            )
            Row(Modifier().display(Display.Flex).gap("16px").flexWrap(FlexWrap.Wrap)) {
                if (!notFound) CatalogTextLink("Retry", "")
                CatalogTextLink("Return to packages", "/packages")
                CatalogTextLink("Back to Seen", navigationLinks.seen)
            }
        }
    }
}

@Composable
private fun CatalogScaffold(
    navigationLinks: CatalogNavigationLinks,
    content: @Composable () -> Unit,
) {
    CatalogStyles()
    Column(
        Modifier()
            .className("seen-catalog")
            .minHeight("100vh")
            .color(CatalogTheme.TEXT_PRIMARY),
    ) {
        CatalogHeader(navigationLinks)
        Main(
            modifier = Modifier()
                .className("seen-catalog-main")
                .width("100%")
                .maxWidth(1040)
                .marginAuto()
                .padding(56, 24),
        ) {
            Column(Modifier().gap("22px"), content = content)
        }
        CatalogFooter()
    }
}

@Composable
private fun CatalogHeader(navigationLinks: CatalogNavigationLinks) {
    val menuId = "seen-primary-navigation-menu"
    Header(
        modifier = Modifier()
            .className("seen-site-navigation")
            .backgroundColor(CatalogTheme.HEADER)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER),
    ) {
        Nav(
            modifier = Modifier()
                .attribute("aria-label", "Primary")
                .attribute("data-navigation-layer", "global"),
        ) {
            Row(
                Modifier()
                    .className("seen-global-nav")
                    .width("100%")
                    .maxWidth(1120)
                    .marginAuto()
                    .padding(12, 24)
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .gap("16px"),
            ) {
                CatalogGlobalBrand(navigationLinks.portfolio)
                Box(
                    modifier = Modifier()
                        .className("seen-primary-navigation")
                        .position(Position.Relative)
                        .flex(grow = 1, shrink = 1, basis = "auto"),
                ) {
                    Button(
                        onClick = {},
                        label = "Menu",
                        modifier = Modifier()
                            .className("seen-primary-navigation-summary")
                            .attribute("aria-label", "Open primary navigation")
                            .attribute("aria-controls", menuId)
                            .attribute("aria-expanded", "false")
                            .padding(10, 12)
                            .borderRadius(10)
                            .color(CatalogTheme.TEXT_PRIMARY)
                            .fontWeight(800),
                        variant = ButtonVariant.GHOST,
                        disabled = false,
                        action = UiAction.ToggleVisibility(menuId),
                    )
                    Box(
                        Modifier()
                            .className("seen-primary-navigation-panel")
                            .id(menuId),
                    ) {
                        Row(
                            Modifier()
                                .className("seen-primary-navigation-links")
                                .display(Display.Flex)
                                .alignItems(AlignItems.Center)
                                .justifyContent(JustifyContent.Center)
                                .gap("4px"),
                        ) {
                            CatalogGlobalLink("Projects", navigationLinks.projects, "projects")
                            CatalogGlobalLink("Photography", navigationLinks.photography, "photography")
                            CatalogGlobalLink("Blog", navigationLinks.blog, "blog")
                            CatalogEcosystemDisclosure(navigationLinks)
                        }
                        CatalogGlobalLink(
                            label = "Work With Me",
                            href = navigationLinks.work,
                            id = "work",
                            emphasized = true,
                        )
                    }
                }
            }
        }

        SeenContextNavigation(navigationLinks)
    }
}

private data class CatalogNavigationItem(
    val id: String,
    val label: String,
    val href: String,
)

private fun catalogEcosystemItems(links: CatalogNavigationLinks): List<CatalogNavigationItem> = listOf(
    CatalogNavigationItem("summon", "Summon", links.summon),
    CatalogNavigationItem("materia", "Materia", links.materia),
    CatalogNavigationItem("sigil", "Sigil", links.sigil),
    CatalogNavigationItem("aether", "Aether", links.aether),
    CatalogNavigationItem("seen", "Seen", links.seen),
)

@Composable
private fun CatalogGlobalBrand(href: String) {
    Link(
        href = href,
        modifier = Modifier()
            .className("seen-global-brand")
            .padding(8, 10)
            .borderRadius(10)
            .color(CatalogTheme.TEXT_PRIMARY)
            .fontSize(14)
            .fontWeight(900)
            .textDecoration(TextDecoration.None)
            .letterSpacing("0.16em")
            .attribute("data-nav-id", "home"),
        ariaLabel = "Yousef home",
    ) {
        Text("YOUSEF")
    }
}

@Composable
private fun CatalogGlobalLink(
    label: String,
    href: String,
    id: String,
    active: Boolean = false,
    emphasized: Boolean = false,
) {
    val base = Modifier()
        .className(
            when {
                emphasized -> "seen-global-link seen-global-work"
                else -> "seen-global-link"
            },
        )
        .padding(9, 11)
        .borderRadius(10)
        .color(if (emphasized) "#ffffff" else CatalogTheme.TEXT_SECONDARY)
        .backgroundColor(if (emphasized) "#ff4668" else "transparent")
        .fontSize(13)
        .fontWeight(if (emphasized) 800 else 700)
        .textDecoration(TextDecoration.None)
        .attribute("data-nav-id", id)
        .let { if (active) it.attribute("data-active", "true") else it }
    Link(href = href, modifier = base) { Text(label) }
}

@Composable
private fun CatalogEcosystemDisclosure(links: CatalogNavigationLinks) {
    Details(
        modifier = Modifier()
            .className("seen-ecosystem-navigation")
            .position(Position.Relative)
            .attribute("data-nav-id", "ecosystem")
            .attribute("data-active", "true"),
    ) {
        Summary(
            modifier = Modifier()
                .className("seen-ecosystem-summary")
                .padding(9, 11)
                .borderRadius(10)
                .color(CatalogTheme.TEXT_PRIMARY)
                .fontSize(13)
                .fontWeight(700),
        ) {
            Text("Ecosystem ⌄")
        }
        Column(
            Modifier()
                .className("seen-ecosystem-panel")
                .gap("4px")
                .padding(8)
                .backgroundColor("#071b2e")
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(CatalogTheme.BORDER_STRONG)
                .borderRadius(16),
        ) {
            catalogEcosystemItems(links).forEach { item ->
                CatalogGlobalLink(
                    label = item.label,
                    href = item.href,
                    id = item.id,
                    active = item.id == "seen",
                )
            }
        }
    }
}

@Composable
private fun SeenContextNavigation(links: CatalogNavigationLinks) {
    val items = listOf(
        CatalogNavigationItem("overview", "Overview", links.seen),
        CatalogNavigationItem("packages", "Packages", links.seenPackages),
        CatalogNavigationItem("playground", "Playground", links.seenPlayground),
        CatalogNavigationItem("documentation", "Documentation", links.seenDocs),
        CatalogNavigationItem("api-reference", "API Reference", links.seenApiReference),
    )
    Nav(
        modifier = Modifier()
            .className("seen-context-navigation")
            .attribute("aria-label", "Seen navigation")
            .attribute("data-navigation-layer", "context"),
    ) {
        Row(
            Modifier()
                .className("seen-context-navigation-rail")
                .width("100%")
                .maxWidth(1120)
                .marginAuto()
                .padding(9, 24)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap("8px")
                .overflowX(Overflow.Auto)
                .whiteSpace(WhiteSpace.NoWrap),
        ) {
            Row(
                modifier = Modifier()
                    .className("seen-context-brand")
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap("8px")
                    .padding(7, 10)
                    .borderRadius(10)
                    .color(CatalogTheme.ACCENT)
                    .fontWeight(900)
                    .textDecoration(TextDecoration.None)
                    .attribute("data-nav-id", "context-brand"),
            ) {
                Text("S", Modifier().className("seen-brand-mark").color(CatalogTheme.ACCENT))
                Text("Seen")
            }
            items.forEach { item ->
                val active = item.id == "packages"
                Link(
                    href = item.href,
                    modifier = headerLinkModifier()
                        .attribute("data-nav-id", "context-${item.id}")
                        .let { if (active) it.attribute("aria-current", "page").attribute("data-active", "true") else it },
                ) { Text(item.label) }
            }
        }
    }
}

@Composable
private fun CatalogFooter() {
    Footer(
        modifier = Modifier()
            .width("100%")
            .maxWidth(1040)
            .marginAuto()
            .padding(24)
            .marginTop("auto"),
    ) {
        Row(
            Modifier()
                .display(Display.Flex)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap("14px")
                .flexWrap(FlexWrap.Wrap)
                .color(CatalogTheme.TEXT_MUTED)
                .fontSize(13),
        ) {
            Text("Seen package registry")
            Text("Public package metadata is review-gated and resolver-ready.")
        }
    }
}

@Composable
private fun Breadcrumbs(ancestors: List<Pair<String, String>>, current: String) {
    Nav(modifier = Modifier().attribute("aria-label", "Breadcrumb")) {
        Row(
            Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap("8px")
                .flexWrap(FlexWrap.Wrap)
                .fontSize(14)
                .overflowWrap(OverflowWrap.Anywhere),
        ) {
            ancestors.forEach { (label, href) ->
                CatalogTextLink(label, href)
                Text("/", Modifier().color(CatalogTheme.TEXT_MUTED).attribute("aria-hidden", "true"))
            }
            Text(current, Modifier().color(CatalogTheme.TEXT_SECONDARY).attribute("aria-current", "page"))
        }
    }
}

@Composable
private fun CatalogStatePanel(
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier()
            .className("seen-catalog-state")
            .padding(28)
            .backgroundColor(CatalogTheme.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER)
            .borderRadius(18)
            .gap("10px"),
    ) {
        H2(modifier = Modifier().margin(0).fontSize(22).fontWeight(800)) { Text(title) }
        Text(message, Modifier().color(CatalogTheme.TEXT_SECONDARY).lineHeight(1.6))
        action?.invoke()
    }
}

@Composable
private fun ReleaseStatusBadge(release: ReleaseRecord) {
    val (background, foreground) = when (release.state.availability) {
        "available" -> CatalogTheme.SUCCESS_SOFT to CatalogTheme.SUCCESS
        "yanked" -> CatalogTheme.WARNING_SOFT to CatalogTheme.WARNING
        "security-quarantined" -> CatalogTheme.DANGER_SOFT to CatalogTheme.DANGER
        else -> CatalogTheme.SURFACE_STRONG to CatalogTheme.TEXT_SECONDARY
    }
    PackageChip(releaseStatus(release), background, foreground)
}

@Composable
private fun PackageChip(label: String, background: String, foreground: String) {
    Text(
        label,
        Modifier()
            .padding(6, 10)
            .backgroundColor(background)
            .color(foreground)
            .borderRadius(999)
            .fontSize(12)
            .fontWeight(800)
            .width("fit-content"),
    )
}

@Composable
private fun MetadataItem(label: String, value: String, monospace: Boolean = false) {
    Column(Modifier().gap("6px").minWidth(0)) {
        Text(label, Modifier().fontSize(12).fontWeight(800).color(CatalogTheme.TEXT_MUTED))
        Text(
            value,
            Modifier()
                .color(CatalogTheme.TEXT_SECONDARY)
                .let { if (monospace) it.fontFamily(CatalogTheme.MONO) else it }
                .overflowWrap(OverflowWrap.Anywhere),
        )
    }
}

@Composable
private fun CatalogTextLink(label: String, href: String) {
    Link(
        href = href,
        modifier = Modifier()
            .className("seen-text-link")
            .color(CatalogTheme.ACCENT)
            .fontWeight(700)
            .textDecoration(TextDecoration.None),
    ) { Text(label) }
}

private fun pageTitleModifier(): Modifier = Modifier()
    .margin(0)
    .fontSize(40)
    .lineHeight(1.15)
    .fontWeight(900)
    .color(CatalogTheme.TEXT_PRIMARY)

private fun sectionTitleModifier(): Modifier = Modifier()
    .margin(0)
    .fontSize(24)
    .fontWeight(850)
    .color(CatalogTheme.TEXT_PRIMARY)

private fun headerLinkModifier(): Modifier = Modifier()
    .className("seen-header-link")
    .padding(8, 12)
    .borderRadius(10)
    .color(CatalogTheme.TEXT_SECONDARY)
    .fontWeight(700)
    .textDecoration(TextDecoration.None)

private fun resultSummary(resultCount: Int, query: String): String {
    val count = if (resultCount == 1) "1 package" else "$resultCount packages"
    return if (query.isEmpty()) count else "$count found for “$query”"
}

private fun releaseStatus(release: ReleaseRecord): String = when (release.state.availability) {
    "available" -> "Active and available"
    "yanked" -> "Yanked; retained for existing locks"
    "security-quarantined" -> "Unavailable during security review"
    else -> "Not publicly available"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MiB".format(Locale.ROOT, bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KiB".format(Locale.ROOT, bytes / 1_024.0)
    else -> "$bytes bytes"
}

private object CatalogTheme {
    const val BACKGROUND = "#001a2c"
    const val HEADER = "rgba(0, 26, 44, 0.92)"
    const val SURFACE = "rgba(5, 41, 74, 0.72)"
    const val SURFACE_STRONG = "rgba(255, 255, 255, 0.09)"
    const val INPUT = "rgba(0, 18, 34, 0.84)"
    const val BORDER = "rgba(255, 255, 255, 0.13)"
    const val BORDER_STRONG = "rgba(255, 255, 255, 0.25)"
    const val TEXT_PRIMARY = "#ffffff"
    const val TEXT_SECONDARY = "#d0dae8"
    const val TEXT_MUTED = "#94a8bf"
    const val ACCENT = "#58a6ff"
    const val ACCENT_SOFT = "rgba(88, 166, 255, 0.15)"
    const val SUCCESS = "#73e2a7"
    const val SUCCESS_SOFT = "rgba(50, 205, 129, 0.14)"
    const val WARNING = "#ffc978"
    const val WARNING_SOFT = "rgba(255, 183, 77, 0.15)"
    const val DANGER = "#ff9b9b"
    const val DANGER_SOFT = "rgba(255, 107, 107, 0.15)"
    const val MONO = "'JetBrains Mono', 'SFMono-Regular', Consolas, monospace"
}

@Composable
private fun CatalogStyles() {
    val summary = StyleSelector.element(StyleElement.Summary)
    val globalBrand = StyleSelector.className("seen-global-brand")
    val globalLink = StyleSelector.className("seen-global-link")
    val ecosystemSummary = StyleSelector.className("seen-ecosystem-summary")
    val primarySummary = StyleSelector.className("seen-primary-navigation-summary")
    val contextBrand = StyleSelector.className("seen-context-brand")
    val headerLink = StyleSelector.className("seen-header-link")
    val packageCard = StyleSelector.className("seen-package-card")
    val releaseRow = StyleSelector.className("seen-release-row")
    val searchField = StyleSelector.className("seen-package-search-field")
    val searchButton = StyleSelector.className("seen-search-button")
    val downloadLink = StyleSelector.className("seen-download-link")
    val interactiveNavigationItems = listOf(
        globalBrand,
        globalLink,
        ecosystemSummary,
        primarySummary,
        contextBrand,
        headerLink,
    )
    val interactiveNavigation = StyleSelector.all(interactiveNavigationItems)
    val interactiveCardItems = listOf(packageCard, releaseRow)
    val interactiveCards = StyleSelector.all(interactiveCardItems)
    val primaryPanel = StyleSelector.className("seen-primary-navigation-panel")
    val ecosystemPanel = StyleSelector.className("seen-ecosystem-panel")

    TypedStyleSheet {
        rule(StyleSelector.Root, Modifier().colorScheme(ColorScheme.OnlyDark))
        rule(StyleSelector.Universal, Modifier().boxSizing(BoxSizing.BorderBox))
        rule(
            StyleSelector.element(StyleElement.Html).or(StyleSelector.element(StyleElement.Body)),
            Modifier()
                .margin(0)
                .minHeight("100%")
                .backgroundColor(CatalogTheme.BACKGROUND),
        )
        rule(
            StyleSelector.element(StyleElement.Body),
            catalogBackgroundModifier().fontFamily(
                "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
            ),
        )
        rule(StyleSelector.id("summon-root"), Modifier().minHeight("100vh"))
        rule(StyleSelector.className("seen-catalog"), catalogBackgroundModifier())
        rule(
            StyleSelector.className("seen-site-navigation"),
            Modifier()
                .position(Position.Sticky)
                .top("0")
                .zIndex(50)
                .borderBottomWidth(1)
                .backdropFilter { blur(18) },
            StyleRulePriority.Important,
        )
        rule(StyleSelector.className("seen-primary-navigation"), Modifier().minWidth(0))
        rule(primarySummary, Modifier().display(Display.None), StyleRulePriority.Important)
        rule(
            primaryPanel,
            Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap("16px"),
        )
        rule(
            interactiveNavigation,
            Modifier().transition("color 160ms ease, background-color 160ms ease, border-color 160ms ease"),
        )
        rule(
            StyleSelector.all(interactiveNavigationItems.map { it.pseudoClass(StylePseudoClass.Hover) }),
            Modifier().color(CatalogTheme.TEXT_PRIMARY).backgroundColor(CatalogTheme.SURFACE_STRONG),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-global-work").pseudoClass(StylePseudoClass.Hover),
            Modifier().backgroundColor("#ff5d79"),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-ecosystem-navigation").child(summary),
            Modifier().cursor(Cursor.Pointer).listStyle(ListStyleType.None),
        )
        rule(
            StyleSelector.className("seen-ecosystem-navigation")
                .child(summary)
                .pseudoElement(StylePseudoElement.WebkitDetailsMarker),
            Modifier().display(Display.None),
        )
        rule(
            StyleSelector.className("seen-ecosystem-navigation")
                .attribute(StyleAttribute.data("active"), "true")
                .child(summary),
            Modifier().backgroundColor(CatalogTheme.ACCENT_SOFT),
        )
        rule(
            ecosystemPanel,
            Modifier()
                .position(Position.Absolute)
                .top("calc(100% + 10px)")
                .left("0")
                .zIndex(100)
                .minWidth(230)
                .boxShadow("0 24px 70px rgba(0, 0, 0, 0.5)"),
        )
        rule(
            StyleSelector.className("seen-ecosystem-navigation")
                .not(StyleSelector.Universal.attribute(StyleAttribute.Open))
                .descendant(ecosystemPanel),
            Modifier().display(Display.None),
        )
        rule(
            StyleSelector.className("seen-ecosystem-navigation")
                .attribute(StyleAttribute.Open)
                .child(summary),
            Modifier().color(CatalogTheme.TEXT_PRIMARY).backgroundColor("rgba(255, 255, 255, 0.1)"),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-context-navigation"),
            Modifier()
                .borderTopWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(CatalogTheme.BORDER),
        )
        rule(
            StyleSelector.className("seen-context-navigation-rail"),
            Modifier()
                .scrollbarWidth(ScrollbarWidth.Thin)
                .scrollbarColor("rgba(88, 166, 255, 0.4)", "transparent"),
        )
        rule(
            StyleSelector.className("seen-brand-mark"),
            Modifier()
                .display(Display.InlineFlex)
                .size(32)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor("rgba(88, 166, 255, 0.45)")
                .borderRadius(10)
                .backgroundColor("rgba(88, 166, 255, 0.12)")
                .fontFamily("Georgia, serif"),
        )
        rule(
            headerLink.attribute(StyleAttribute.AriaCurrent, "page")
                .or(globalLink.attribute(StyleAttribute.data("active"), "true")),
            Modifier().color(CatalogTheme.TEXT_PRIMARY).backgroundColor(CatalogTheme.ACCENT_SOFT),
            StyleRulePriority.Important,
        )
        rule(
            interactiveCards,
            Modifier().transition("transform 160ms ease, border-color 160ms ease, background-color 160ms ease"),
        )
        rule(
            StyleSelector.all(interactiveCardItems.map { it.pseudoClass(StylePseudoClass.Hover) }),
            Modifier()
                .transform(TransformFunction.TranslateY to "-2px")
                .borderColor("rgba(88, 166, 255, 0.56)")
                .backgroundColor("rgba(7, 52, 91, 0.9)"),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-text-link").pseudoClass(StylePseudoClass.Hover),
            Modifier().color("#8fc4ff"),
            StyleRulePriority.Important,
        )

        val catalog = StyleSelector.className("seen-catalog")
        val focusable = StyleSelector.all(listOf(
            catalog.descendant(StyleSelector.element(StyleElement.Anchor)),
            catalog.descendant(summary),
            catalog.descendant(StyleSelector.element(StyleElement.Input)),
            catalog.descendant(StyleSelector.element(StyleElement.Button)),
        ).map { it.pseudoClass(StylePseudoClass.FocusVisible) })
        rule(
            focusable,
            Modifier().outline(3, OutlineStyle.Solid, "#8fc4ff").outlineOffset(3),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-package-search-input")
                .pseudoElement(StylePseudoElement.Placeholder),
            Modifier().color("#8198b0").opacity(1f),
        )
        rule(
            searchField.descendant(StyleSelector.element(StyleElement.Label)),
            Modifier().color(CatalogTheme.TEXT_SECONDARY),
            StyleRulePriority.Important,
        )
        rule(
            searchButton.or(downloadLink),
            Modifier().transition("filter 160ms ease, transform 160ms ease"),
        )
        rule(
            StyleSelector.all(
                listOf(searchButton, downloadLink).map { it.pseudoClass(StylePseudoClass.Hover) },
            ),
            Modifier()
                .filter { brightness(1.09) }
                .transform(TransformFunction.TranslateY to "-1px"),
        )

        media(MediaQuery.MaxWidth(1040)) {
            rule(
                StyleSelector.className("seen-global-nav"),
                Modifier().padding(10, 16),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.className("seen-primary-navigation"),
                Modifier().flex(grow = 0, shrink = 0, basis = "auto"),
                StyleRulePriority.Important,
            )
            rule(
                primarySummary,
                Modifier()
                    .display(Display.Block)
                    .backgroundColor("transparent")
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
                    .zIndex(100)
                    .width("min(88vw, 340px)")
                    .maxHeight("calc(100vh - 110px)")
                    .overflowY(Overflow.Auto)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch)
                    .gap("6px")
                    .padding(14)
                    .backgroundColor("#071b2e")
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(CatalogTheme.BORDER_STRONG)
                    .borderRadius(16)
                    .boxShadow("0 24px 70px rgba(0, 0, 0, 0.5)"),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.className("seen-primary-navigation-links"),
                Modifier()
                    .width("100%")
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch)
                    .gap("6px"),
                StyleRulePriority.Important,
            )
            val panelLinks = StyleSelector.all(
                primaryPanel.descendant(globalLink),
                primaryPanel.descendant(ecosystemSummary),
                primaryPanel.descendant(ecosystemPanel).descendant(globalLink),
            )
            rule(
                panelLinks,
                Modifier().display(Display.Flex).width("100%").padding(11, 12).fontSize(15),
                StyleRulePriority.Important,
            )
            rule(ecosystemSummary, Modifier().justifyContent(JustifyContent.SpaceBetween))
            rule(StyleSelector.className("seen-ecosystem-navigation"), Modifier().width("100%"))
            rule(
                ecosystemPanel,
                Modifier()
                    .position(Position.Static)
                    .minWidth(0)
                    .width("100%")
                    .marginTop(6)
                    .boxShadow("none"),
                StyleRulePriority.Important,
            )
        }
        media(MediaQuery.MaxWidth(640)) {
            rule(
                StyleSelector.className("seen-catalog-main"),
                Modifier().padding(36, 16),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.className("seen-context-navigation-rail"),
                Modifier().padding(8, 16),
                StyleRulePriority.Important,
            )
            rule(
                contextBrand,
                Modifier()
                    .position(Position.Sticky)
                    .left("0")
                    .zIndex(2)
                    .backgroundColor("#071b2e"),
            )
            rule(
                StyleSelector.className("seen-catalog-search"),
                Modifier().padding(16),
                StyleRulePriority.Important,
            )
            rule(searchButton, Modifier().width("100%"), StyleRulePriority.Important)
            rule(
                catalog.descendant(StyleSelector.element(StyleElement.Heading1)),
                Modifier().fontSize(32),
                StyleRulePriority.Important,
            )
        }
        media(MediaQuery.PrefersReducedMotion) {
            rule(
                StyleSelector.all(
                    interactiveNavigationItems + interactiveCardItems + listOf(searchButton, downloadLink),
                ),
                Modifier().transitionProperty(TransitionProperty.None),
                StyleRulePriority.Important,
            )
            rule(
                StyleSelector.all(
                    (interactiveCardItems + listOf(searchButton, downloadLink))
                        .map { it.pseudoClass(StylePseudoClass.Hover) },
                ),
                Modifier().transform("none"),
                StyleRulePriority.Important,
            )
        }
    }
}

private fun catalogBackgroundModifier(): Modifier = Modifier()
    .backgroundColor(CatalogTheme.BACKGROUND)
    .backgroundLayers {
        radialGradient {
            position(12, 0)
            colorStop("rgba(88, 166, 255, 0.16)")
            colorStop("transparent", "32rem")
        }
        linearGradient {
            angle(180)
            colorStop(CatalogTheme.BACKGROUND, "0%")
            colorStop("#001522", "100%")
        }
    }
