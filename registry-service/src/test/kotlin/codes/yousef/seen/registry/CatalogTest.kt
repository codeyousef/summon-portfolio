package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        assertContains(catalog, "<main")
        assertContains(catalog, "<h1")
        assertContains(catalog, "href=\"/\"")
        assertContains(catalog, "href=\"/docs\"")
        assertContains(catalog, "aria-current=\"page\"")
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

    @Test
    fun `renders accessible server side search and distinct empty states`() {
        val searchPage = CatalogRenderer.render(listOf(pkg), "  DEMO  ")
        val noMatches = CatalogRenderer.render(listOf(pkg), "missing")
        val emptyRegistry = CatalogRenderer.render(emptyList())

        assertContains(searchPage, "role=\"search\"")
        assertContains(searchPage, "type=\"search\"")
        assertContains(searchPage, "name=\"q\"")
        assertContains(searchPage, "maxlength=\"128\"")
        assertContains(searchPage, "latest version")
        assertContains(searchPage, "value=\"DEMO\"")
        assertContains(searchPage, "1 package found")
        assertContains(noMatches, "No packages match")
        assertContains(noMatches, "Clear search")
        assertContains(emptyRegistry, "No public packages yet")
        assertContains(emptyRegistry, "role=\"search\"")
        assertContains(emptyRegistry, "0 packages")
    }

    @Test
    fun `filters public catalog deterministically across identity description license and version`() {
        val other = pkg.copy(
            identity = "alpha/math",
            description = "Linear algebra primitives",
            licenseSpdx = "Apache-2.0",
            latestActiveVersion = "2.0.0",
        )
        val packages = listOf(pkg, other)

        assertEquals(listOf("alpha/math", "seen/demo"), filterCatalogPackages(packages, "").map { it.identity })
        assertEquals(listOf("seen/demo"), filterCatalogPackages(packages, "SEEN source-only").map { it.identity })
        assertEquals(listOf("alpha/math"), filterCatalogPackages(packages, "apache 2.0.0").map { it.identity })
        assertEquals(emptyList(), filterCatalogPackages(packages, "not-present"))
        assertEquals("x".repeat(CATALOG_QUERY_MAX_LENGTH), normalizeCatalogQuery("  ${"x".repeat(200)}  "))
    }

    @Test
    fun `decodes form query components before normalizing search`() {
        assertEquals("linear algebra", normalizeCatalogQueryParameter("linear+algebra"))
        assertEquals(
            "\"><script>alert(1)</script>",
            normalizeCatalogQueryParameter("%22%3E%3Cscript%3Ealert%281%29%3C%2Fscript%3E"),
        )
        assertEquals("invalid%escape", normalizeCatalogQueryParameter("invalid%escape"))
    }

    @Test
    fun `escapes hostile search text in content and input value`() {
        val hostile = "<script>alert(1)</script>\" autofocus"
        val page = CatalogRenderer.render(listOf(pkg), hostile)

        assertFalse(page.contains("<script>alert(1)</script>"))
        assertFalse(page.contains("value=\"$hostile\""))
        assertContains(page, "&lt;script&gt;alert(1)&lt;/script&gt;")
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
