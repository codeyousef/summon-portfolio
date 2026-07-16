package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CatalogTest {
    private val pkg = PackageRecord(
        identity = "seen/demo",
        description = "A source-only Seen package",
        licenseSpdx = "MIT",
        latestActiveVersion = "1.2.3",
        createdAt = "2026-07-16T12:00:00Z",
        updatedAt = "2026-07-16T12:00:00Z",
        links = PackageLinks(
            self = "/packages/api/v1/packages/seen/demo",
            releases = "/packages/api/v1/packages/seen/demo/releases",
        ),
    )

    @Test
    fun `renders navigable package and active release detail without internal state`() {
        val release = release("available")
        val catalog = CatalogRenderer.render(listOf(pkg))
        val packagePage = CatalogRenderer.renderPackage(pkg, listOf(release))
        val releasePage = CatalogRenderer.renderRelease(pkg, release)

        assertContains(catalog, "/packages/seen/demo")
        assertContains(catalog, "complete registry review")
        assertFalse(catalog.contains("safe", ignoreCase = true))
        assertFalse(catalog.contains("verified source packages", ignoreCase = true))
        assertContains(packagePage, "/packages/seen/demo/1.2.3")
        assertContains(packagePage, "A source-only Seen package")
        assertContains(releasePage, release.archive.sha256)
        assertContains(releasePage, "available to the Seen resolver")
        assertFalse(releasePage.contains("uploadId", ignoreCase = true))
        assertFalse(releasePage.contains("ownerPrincipal", ignoreCase = true))
    }

    @Test
    fun `renders concealed and security states with generic public language`() {
        val securityPage = CatalogRenderer.renderRelease(pkg, release("security-quarantined"))
        val missingPage = CatalogRenderer.renderUnavailable(true)
        val failedPage = CatalogRenderer.renderUnavailable(false)

        assertContains(securityPage, "Unavailable during security review")
        assertContains(missingPage, "not public")
        assertContains(failedPage, "Try again shortly")
        assertFalse(failedPage.contains("exception", ignoreCase = true))
    }

    private fun release(availability: String) = ReleaseRecord(
        `package` = pkg.identity,
        version = "1.2.3",
        archive = ArchiveStats(sha256 = "a".repeat(64), compressedBytes = 412),
        manifestSha256 = "b".repeat(64),
        state = ReleaseState(lifecycle = "active", visibility = "public", availability = availability),
        timestamps = ReleaseTimestamps(
            reservedAt = "2026-07-16T12:00:00Z",
            activatedAt = "2026-07-16T12:00:00Z",
            updatedAt = "2026-07-16T12:00:00Z",
        ),
        links = ReleaseLinks(
            self = "/packages/api/v1/packages/seen/demo/releases/1.2.3",
            `package` = "/packages/api/v1/packages/seen/demo",
            download = if (availability == "available") "/packages/api/v1/blobs/sha256/${"a".repeat(64)}" else null,
        ),
    )
}
