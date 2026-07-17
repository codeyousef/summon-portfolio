package codes.yousef.seen.registry

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Label
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.html.Footer
import codes.yousef.summon.components.html.H1
import codes.yousef.summon.components.html.H2
import codes.yousef.summon.components.html.H3
import codes.yousef.summon.components.html.Header
import codes.yousef.summon.components.html.Main
import codes.yousef.summon.components.html.Nav
import codes.yousef.summon.components.html.Section
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.modifier.AlignItems
import codes.yousef.summon.modifier.BorderStyle
import codes.yousef.summon.modifier.Display
import codes.yousef.summon.modifier.FlexWrap
import codes.yousef.summon.modifier.JustifyContent
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.TextDecoration
import codes.yousef.summon.modifier.alignItems
import codes.yousef.summon.modifier.attribute
import codes.yousef.summon.modifier.backgroundColor
import codes.yousef.summon.modifier.borderColor
import codes.yousef.summon.modifier.borderRadius
import codes.yousef.summon.modifier.borderStyle
import codes.yousef.summon.modifier.borderWidth
import codes.yousef.summon.modifier.className
import codes.yousef.summon.modifier.color
import codes.yousef.summon.modifier.display
import codes.yousef.summon.modifier.flex
import codes.yousef.summon.modifier.flexDirection
import codes.yousef.summon.modifier.flexWrap
import codes.yousef.summon.modifier.fontFamily
import codes.yousef.summon.modifier.fontSize
import codes.yousef.summon.modifier.fontWeight
import codes.yousef.summon.modifier.gap
import codes.yousef.summon.modifier.id
import codes.yousef.summon.modifier.justifyContent
import codes.yousef.summon.modifier.lineHeight
import codes.yousef.summon.modifier.marginAuto
import codes.yousef.summon.modifier.maxWidth
import codes.yousef.summon.modifier.minHeight
import codes.yousef.summon.modifier.minWidth
import codes.yousef.summon.modifier.padding
import codes.yousef.summon.modifier.style
import codes.yousef.summon.modifier.textDecoration
import codes.yousef.summon.modifier.width
import codes.yousef.summon.runtime.LocalPlatformRenderer
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import java.util.Locale

internal const val CATALOG_QUERY_MAX_LENGTH = 128

object CatalogRenderer {
    private val lock = Any()

    fun render(packages: List<PackageRecord>, query: String = ""): String = synchronized(lock) {
        renderLocked(
            title = "Packages · Seen",
            description = "Browse public source packages for the Seen programming language.",
        ) { Catalog(packages, normalizeCatalogQuery(query)) }
    }

    fun renderPackage(pkg: PackageRecord, releases: List<ReleaseRecord>): String = synchronized(lock) {
        renderLocked(
            title = "${pkg.identity} · Seen packages",
            description = pkg.description ?: "Public releases of ${pkg.identity} for Seen.",
        ) { PackageDetail(pkg, releases) }
    }

    fun renderRelease(pkg: PackageRecord, release: ReleaseRecord): String = synchronized(lock) {
        renderLocked(
            title = "${pkg.identity} ${release.version} · Seen packages",
            description = "Release ${release.version} of ${pkg.identity} for Seen.",
        ) { ReleaseDetail(pkg, release) }
    }

    fun renderUnavailable(notFound: Boolean): String = synchronized(lock) {
        renderLocked(
            title = if (notFound) "Package not found · Seen" else "Registry unavailable · Seen",
            description = if (notFound) {
                "The requested Seen package or release is not public."
            } else {
                "The Seen package registry is temporarily unavailable."
            },
        ) { RegistryUnavailable(notFound) }
    }

    private fun renderLocked(
        title: String,
        description: String,
        content: @Composable () -> Unit,
    ): String {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            val html = renderer.renderComposableRootWithHydration("en", "ltr") { content() }
            return html
                .replace("<title>Summon App</title>", "<title>${headText(title)}</title>")
                .replace(
                    "<meta name=\"description\" content=\"Summon Framework Application\">",
                    "<meta name=\"description\" content=\"${headText(description)}\">",
                )
                .replace(
                    "<meta property=\"og:title\" content=\"Summon App\">",
                    "<meta property=\"og:title\" content=\"${headText(title)}\">",
                )
        } finally {
            clearPlatformRenderer()
        }
    }
}

internal fun normalizeCatalogQuery(rawQuery: String?): String =
    rawQuery.orEmpty().trim().take(CATALOG_QUERY_MAX_LENGTH)

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
private fun Catalog(packages: List<PackageRecord>, query: String) {
    val visiblePackages = filterCatalogPackages(packages, query)
    CatalogScaffold {
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
        Column(
            Modifier()
                .flex(grow = 1, shrink = 1, basis = "320px")
                .minWidth(0)
                .gap("8px"),
        ) {
            Label(
                text = "Search packages",
                modifier = Modifier().fontSize(14).fontWeight(700).color(CatalogTheme.TEXT_PRIMARY),
                forElement = "seen-package-search",
            )
            LocalPlatformRenderer.current.renderNativeInput(
                type = "search",
                modifier = Modifier()
                    .id("seen-package-search")
                    .className("seen-package-search-input")
                    .attribute("name", "q")
                    .attribute("maxlength", CATALOG_QUERY_MAX_LENGTH.toString())
                    .attribute("autocomplete", "off")
                    .attribute("placeholder", "Search by name, description, license, or latest version")
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
                value = query,
            )
        }
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
            .style("overflow-wrap", "anywhere"),
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
                H2(modifier = Modifier().style("margin", "0").fontSize(22).fontWeight(800)) {
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
private fun PackageDetail(pkg: PackageRecord, releases: List<ReleaseRecord>) {
    CatalogScaffold {
        Breadcrumbs(listOf("Packages" to "/packages"), current = pkg.identity)
        H1(modifier = pageTitleModifier().style("overflow-wrap", "anywhere")) {
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
                H3(modifier = Modifier().style("margin", "0").fontSize(20).fontWeight(800)) {
                    Text(release.version)
                }
                Text("Published ${release.timestamps.activatedAt ?: release.timestamps.updatedAt}", Modifier().fontSize(13).color(CatalogTheme.TEXT_MUTED))
            }
            ReleaseStatusBadge(release)
        }
    }
}

@Composable
private fun ReleaseDetail(pkg: PackageRecord, release: ReleaseRecord) {
    CatalogScaffold {
        Breadcrumbs(
            ancestors = listOf(
                "Packages" to "/packages",
                pkg.identity to "/packages/${pkg.identity}",
            ),
            current = release.version,
        )
        H1(modifier = pageTitleModifier().style("overflow-wrap", "anywhere")) {
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
private fun RegistryUnavailable(notFound: Boolean) {
    CatalogScaffold {
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
                CatalogTextLink("Back to Seen", "/")
            }
        }
    }
}

@Composable
private fun CatalogScaffold(content: @Composable () -> Unit) {
    GlobalStyle(CATALOG_CSS)
    Column(
        Modifier()
            .className("seen-catalog")
            .minHeight("100vh")
            .color(CatalogTheme.TEXT_PRIMARY),
    ) {
        CatalogHeader()
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
private fun CatalogHeader() {
    Header(
        modifier = Modifier()
            .className("seen-catalog-header")
            .backgroundColor(CatalogTheme.HEADER)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(CatalogTheme.BORDER),
    ) {
        Row(
            Modifier()
                .className("seen-catalog-header-inner")
                .width("100%")
                .maxWidth(1120)
                .marginAuto()
                .padding(16, 24)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap("18px")
                .flexWrap(FlexWrap.Wrap),
        ) {
            Link(
                href = "/",
                modifier = Modifier()
                    .className("seen-brand-link")
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap("10px")
                    .color(CatalogTheme.TEXT_PRIMARY)
                    .fontSize(20)
                    .fontWeight(900)
                    .textDecoration(TextDecoration.None),
                ariaLabel = "Seen home",
            ) {
                Text("S", Modifier().className("seen-brand-mark").color(CatalogTheme.ACCENT))
                Text("Seen")
            }
            Nav(modifier = Modifier().attribute("aria-label", "Seen package navigation")) {
                Row(Modifier().display(Display.Flex).alignItems(AlignItems.Center).gap("8px").flexWrap(FlexWrap.Wrap)) {
                    Link(
                        href = "/packages",
                        modifier = headerLinkModifier().attribute("aria-current", "page"),
                    ) { Text("Packages") }
                    Link(href = "/docs", modifier = headerLinkModifier()) { Text("Docs") }
                }
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
            .style("margin-top", "auto"),
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
                .style("overflow-wrap", "anywhere"),
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
        H2(modifier = Modifier().style("margin", "0").fontSize(22).fontWeight(800)) { Text(title) }
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
            .style("width", "fit-content"),
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
                .style("overflow-wrap", "anywhere"),
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
    .style("margin", "0")
    .fontSize(40)
    .lineHeight(1.15)
    .fontWeight(900)
    .color(CatalogTheme.TEXT_PRIMARY)

private fun sectionTitleModifier(): Modifier = Modifier()
    .style("margin", "0")
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

private fun headText(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

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

private val CATALOG_CSS = """
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    html, body { margin: 0; min-height: 100%; background: #001a2c; }
    body {
        font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        background:
            radial-gradient(circle at 12% 0%, rgba(88, 166, 255, 0.16), transparent 32rem),
            linear-gradient(180deg, #001a2c 0%, #001522 100%);
    }
    #summon-root { min-height: 100vh; }
    .seen-catalog {
        background:
            radial-gradient(circle at 12% 0%, rgba(88, 166, 255, 0.16), transparent 32rem),
            linear-gradient(180deg, #001a2c 0%, #001522 100%);
    }
    .seen-catalog-header { border-width: 0 0 1px !important; backdrop-filter: blur(18px); }
    .seen-brand-mark {
        display: inline-flex;
        width: 32px;
        height: 32px;
        align-items: center;
        justify-content: center;
        border: 1px solid rgba(88, 166, 255, 0.45);
        border-radius: 10px;
        background: rgba(88, 166, 255, 0.12);
        font-family: Georgia, serif;
    }
    .seen-header-link[aria-current="page"] { color: #ffffff !important; background: rgba(88, 166, 255, 0.14); }
    .seen-package-card, .seen-release-row { transition: transform 160ms ease, border-color 160ms ease, background-color 160ms ease; }
    .seen-package-card:hover, .seen-release-row:hover {
        transform: translateY(-2px);
        border-color: rgba(88, 166, 255, 0.56) !important;
        background: rgba(7, 52, 91, 0.9) !important;
    }
    .seen-brand-link:hover, .seen-header-link:hover, .seen-text-link:hover { color: #8fc4ff !important; }
    .seen-catalog a:focus-visible,
    .seen-catalog input:focus-visible,
    .seen-catalog button:focus-visible {
        outline: 3px solid #8fc4ff !important;
        outline-offset: 3px;
    }
    .seen-package-search-input::placeholder { color: #8198b0; opacity: 1; }
    .seen-search-button, .seen-download-link { transition: filter 160ms ease, transform 160ms ease; }
    .seen-search-button:hover, .seen-download-link:hover { filter: brightness(1.09); transform: translateY(-1px); }
    @media (max-width: 640px) {
        .seen-catalog-main { padding: 36px 16px !important; }
        .seen-catalog-header-inner { padding: 14px 16px !important; }
        .seen-catalog-search { padding: 16px !important; }
        .seen-search-button { width: 100% !important; }
        .seen-catalog h1 { font-size: 32px !important; }
    }
    @media (prefers-reduced-motion: reduce) {
        .seen-package-card, .seen-release-row, .seen-search-button, .seen-download-link { transition: none !important; }
        .seen-package-card:hover, .seen-release-row:hover, .seen-search-button:hover, .seen-download-link:hover { transform: none !important; }
    }
""".trimIndent()
