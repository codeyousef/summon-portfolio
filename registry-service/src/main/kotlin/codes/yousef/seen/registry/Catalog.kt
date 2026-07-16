package codes.yousef.seen.registry

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.modifier.Modifier
import codes.yousef.summon.modifier.backgroundColor
import codes.yousef.summon.modifier.borderRadius
import codes.yousef.summon.modifier.color
import codes.yousef.summon.modifier.fontSize
import codes.yousef.summon.modifier.fontWeight
import codes.yousef.summon.modifier.gap
import codes.yousef.summon.modifier.marginAuto
import codes.yousef.summon.modifier.maxWidth
import codes.yousef.summon.modifier.padding
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer

object CatalogRenderer {
    private val lock = Any()

    fun render(packages: List<PackageRecord>): String = synchronized(lock) {
        renderLocked { Catalog(packages) }
    }

    fun renderPackage(pkg: PackageRecord, releases: List<ReleaseRecord>): String = synchronized(lock) {
        renderLocked { PackageDetail(pkg, releases) }
    }

    fun renderRelease(pkg: PackageRecord, release: ReleaseRecord): String = synchronized(lock) {
        renderLocked { ReleaseDetail(pkg, release) }
    }

    fun renderUnavailable(notFound: Boolean): String = synchronized(lock) {
        renderLocked { RegistryUnavailable(notFound) }
    }

    private fun renderLocked(content: @Composable () -> Unit): String {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            return renderer.renderComposableRootWithHydration("en", "ltr") { content() }
        } finally {
            clearPlatformRenderer()
        }
    }
}

@Composable
private fun Catalog(packages: List<PackageRecord>) {
    Column(
        Modifier()
            .maxWidth(880)
            .marginAuto()
            .padding(32)
            .gap("20px"),
    ) {
        Text("Seen packages", Modifier().fontSize(36).fontWeight(700).color("#14213d"), semantic = "heading")
        Text("Verified source packages published to the Seen development registry.", Modifier().fontSize(18).color("#41516d"), semantic = "paragraph")
        if (packages.isEmpty()) {
            Text("No public packages yet.", Modifier().padding(24).backgroundColor("#f4f7fb").borderRadius(12))
        } else {
            packages.forEach { pkg ->
                Link(href = "/packages/${pkg.identity}", target = null) {
                    Column(Modifier().padding(20).backgroundColor("#f4f7fb").borderRadius(12).gap("8px")) {
                        Text(pkg.identity, Modifier().fontSize(22).fontWeight(650).color("#14213d"), semantic = "heading")
                        pkg.description?.let { Text(it, Modifier().color("#41516d"), semantic = "paragraph") }
                        Text("Latest ${pkg.latestActiveVersion}", Modifier().fontSize(14).color("#65758b"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageDetail(pkg: PackageRecord, releases: List<ReleaseRecord>) {
    CatalogPage {
        Link(href = "/packages", target = null) { Text("← All packages", Modifier().color("#315ea8")) }
        Text(pkg.identity, Modifier().fontSize(36).fontWeight(700).color("#14213d"), semantic = "heading")
        pkg.description?.let { Text(it, Modifier().fontSize(18).color("#41516d"), semantic = "paragraph") }
        pkg.licenseSpdx?.let { Text("License: $it", Modifier().color("#65758b")) }
        Text("Releases", Modifier().fontSize(24).fontWeight(650).color("#14213d"), semantic = "heading")
        releases.forEach { release ->
            Link(href = "/packages/${pkg.identity}/${release.version}", target = null) {
                Column(Modifier().padding(18).backgroundColor("#f4f7fb").borderRadius(12).gap("6px")) {
                    Text(release.version, Modifier().fontSize(20).fontWeight(650).color("#14213d"))
                    Text(releaseStatus(release), Modifier().fontSize(14).color("#65758b"))
                }
            }
        }
    }
}

@Composable
private fun ReleaseDetail(pkg: PackageRecord, release: ReleaseRecord) {
    CatalogPage {
        Link(href = "/packages/${pkg.identity}", target = null) { Text("← ${pkg.identity}", Modifier().color("#315ea8")) }
        Text("${pkg.identity} ${release.version}", Modifier().fontSize(34).fontWeight(700).color("#14213d"), semantic = "heading")
        Text(releaseStatus(release), Modifier().fontSize(17).color("#41516d"), semantic = "paragraph")
        Column(Modifier().padding(18).backgroundColor("#f4f7fb").borderRadius(12).gap("8px")) {
            Text("SHA-256", Modifier().fontWeight(650).color("#14213d"))
            Text(release.archive.sha256, Modifier().fontSize(13).color("#41516d"))
            Text("Compressed bytes: ${release.archive.compressedBytes}", Modifier().color("#65758b"))
            Text("Manifest SHA-256: ${release.manifestSha256}", Modifier().fontSize(13).color("#41516d"))
        }
        if (release.state.availability == "available" && release.links.download != null) {
            Text("This release is available to the Seen resolver.", Modifier().color("#17663a"))
        } else {
            Text("Archive download is unavailable for this release state.", Modifier().color("#8a4b08"))
        }
    }
}

@Composable
private fun RegistryUnavailable(notFound: Boolean) {
    CatalogPage {
        Text(if (notFound) "Package not found" else "Registry unavailable", Modifier().fontSize(34).fontWeight(700).color("#14213d"), semantic = "heading")
        Text(
            if (notFound) "The package or release is not public." else "The registry could not load this page. Try again shortly.",
            Modifier().fontSize(18).color("#41516d"),
            semantic = "paragraph",
        )
        Link(href = "/packages", target = null) { Text("Return to packages", Modifier().color("#315ea8")) }
    }
}

@Composable
private fun CatalogPage(content: @Composable () -> Unit) {
    Column(Modifier().maxWidth(880).marginAuto().padding(32).gap("20px"), content = content)
}

private fun releaseStatus(release: ReleaseRecord): String = when (release.state.availability) {
    "available" -> "Active and available"
    "yanked" -> "Yanked; retained for existing locks"
    "security-quarantined" -> "Unavailable during security review"
    else -> "Not publicly available"
}
