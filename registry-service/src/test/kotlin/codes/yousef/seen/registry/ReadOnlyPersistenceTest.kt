package codes.yousef.seen.registry

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadOnlyPersistenceTest {
    @Test
    fun `public read service conceals an available private release`() {
        val mutableRepository = InMemoryRegistryRepository()
        val storedPackage = StoredPackage(
            record = PackageRecord(
                identity = "seen/private-demo",
                latestActiveVersion = "1.0.0",
                createdAt = "2026-07-19T00:00:00Z",
                updatedAt = "2026-07-19T00:00:00Z",
                links = PackageLinks(
                    self = "/packages/api/v1/packages/seen/private-demo",
                    releases = "/packages/api/v1/packages/seen/private-demo/releases",
                ),
            ),
            ownerPrincipal = "internal-publisher",
        )
        mutableRepository.createPackage(storedPackage)
        mutableRepository.reserveRelease(StoredRelease(
            record = ReleaseRecord(
                `package` = "seen/private-demo",
                version = "1.0.0",
                archive = ArchiveStats(sha256 = "b".repeat(64), compressedBytes = 1),
                manifestSha256 = "c".repeat(64),
                state = ReleaseState(lifecycle = "active", visibility = "private", availability = "available"),
                timestamps = ReleaseTimestamps(
                    reservedAt = "2026-07-19T00:00:00Z",
                    activatedAt = "2026-07-19T00:00:00Z",
                    updatedAt = "2026-07-19T00:00:00Z",
                ),
                links = ReleaseLinks(
                    self = "/packages/api/v1/packages/seen/private-demo/releases/1.0.0",
                    `package` = "/packages/api/v1/packages/seen/private-demo",
                ),
            ),
            ownerPrincipal = "internal-publisher",
            uploadId = "upl_private_demo_0001",
            uploadExpiresAt = "2026-07-20T00:00:00Z",
            manifest = JsonObject(emptyMap()),
            source = SourceDeclaration(
                forge = "github",
                repositoryId = "1",
                installationId = "1",
                requestedRef = "refs/tags/v1.0.0",
                expectedCommit = "d".repeat(40),
                licenseSpdx = "MIT",
            ),
        ))
        val repository = ReadOnlyRegistryRepository(mutableRepository)
        assertEquals(null, repository.getPackage("seen/private-demo"))
        assertEquals(null, repository.getRelease("seen/private-demo", "1.0.0"))
        assertEquals(emptyList(), repository.listPackages())
        assertEquals(emptyList(), repository.listReleases("seen/private-demo"))
        val storage = ReadOnlyRegistryObjectStorage(InMemoryRegistryObjectStorage())
        val config = testConfig().copy(
            environment = "production",
            repositoryId = "seen-prod-registry-v1",
            registryOrigin = "https://seen.yousef.codes/packages",
            serverMode = RegistryServerMode.READ_ONLY_PUBLIC_API,
            writerMode = "",
            writerToken = "",
            writerPrincipal = "",
            ownerAllowlist = emptySet(),
            writersEnabled = false,
            publicDelay = Duration.ZERO,
            trustAndSafetyToken = null,
            trustAndSafetyPrincipal = "",
        )
        val service = RegistryService(
            config,
            repository,
            storage,
            ArchiveValidator(),
            TufPublisher(
                repository,
                storage,
                testOnlineSigners(),
                config.environment,
                config.repositoryId,
                config.registryOrigin,
                Clock.systemUTC(),
            ),
            Clock.systemUTC(),
        )

        val error = assertFailsWith<RegistryException> {
            service.getRelease("seen/private-demo", "1.0.0")
        }
        assertEquals("not_found", error.code)
        assertEquals(emptyList(), service.listPublicPackages().items)
        listOf(
            { service.getPackage("seen/private-demo") },
            { service.listReleases("seen/private-demo") },
        ).forEach { read ->
            val hidden = assertFailsWith<RegistryException> { read() }
            assertEquals("not_found", hidden.code)
        }
    }

    @Test
    fun `repository capability delegates public reads and rejects every mutation path`() {
        val mutable = InMemoryRegistryRepository()
        val stored = StoredPackage(
            record = PackageRecord(
                identity = "seen/demo",
                latestActiveVersion = "1.2.4",
                createdAt = "2026-07-19T00:00:00Z",
                updatedAt = "2026-07-19T00:00:00Z",
                links = PackageLinks(
                    self = "/packages/api/v1/packages/seen/demo",
                    releases = "/packages/api/v1/packages/seen/demo/releases",
                ),
            ),
            ownerPrincipal = "internal-publisher",
        )
        mutable.createPackage(stored)
        mutable.reserveRelease(readOnlyFixtureRelease(
            identity = "seen/demo",
            version = "1.2.3",
            visibility = "public",
            availability = "available",
        ))
        mutable.reserveRelease(readOnlyFixtureRelease(
            identity = "seen/demo",
            version = "1.2.4",
            visibility = "public",
            availability = "security-quarantined",
        ))
        val readOnly = ReadOnlyRegistryRepository(mutable)

        assertEquals("seen/demo", readOnly.getPackage("seen/demo")?.record?.identity)
        assertEquals("1.2.3", readOnly.getPackage("seen/demo")?.record?.latestActiveVersion)
        assertEquals(listOf("seen/demo"), readOnly.listPackages().map { it.record.identity })
        assertEquals("1.2.3", readOnly.listPackages().single().record.latestActiveVersion)
        assertEquals(listOf("1.2.3"), readOnly.listReleases("seen/demo").map { it.record.version })
        assertEquals(null, readOnly.getRelease("seen/demo", "1.2.4"))
        assertFailsWith<IllegalStateException> { readOnly.createPackage(stored) }
        assertFailsWith<IllegalStateException> { readOnly.savePackage(stored) }
        assertFailsWith<IllegalStateException> { readOnly.findReleaseByUpload("upl_private") }
        assertFailsWith<IllegalStateException> { readOnly.nextMetadataVersion() }
        assertFailsWith<IllegalStateException> {
            readOnly.beginIdempotency(
                StoredIdempotency(
                    scope = "scope",
                    fingerprint = "fingerprint",
                    attemptId = "attempt",
                    createdAt = "2026-07-19T00:00:00Z",
                    processingExpiresAt = "2026-07-19T00:01:00Z",
                    expiresAt = "2026-07-20T00:00:00Z",
                ),
                Instant.parse("2026-07-19T00:00:00Z"),
            )
        }
    }

    @Test
    fun `object capability reads only public blobs and metadata`() {
        val digest = "a".repeat(64)
        val blob = "public package".encodeToByteArray()
        val root = "signed root".encodeToByteArray()
        val mutable = InMemoryRegistryObjectStorage().apply {
            putPublicBlob(digest, blob)
            putMetadata("root.json", root)
        }
        val readOnly = ReadOnlyRegistryObjectStorage(mutable)

        assertContentEquals(blob, readOnly.getPublicBlob(digest))
        assertContentEquals(root, readOnly.getMetadata("root.json"))
        assertFailsWith<IllegalStateException> { readOnly.getQuarantine("upl_private") }
        assertFailsWith<IllegalStateException> { readOnly.putPublicBlob(digest, blob) }
        assertFailsWith<IllegalStateException> { readOnly.putMetadata("root.json", root) }
        assertFailsWith<IllegalStateException> {
            readOnly.replaceMetadataIfUnchanged("root.json", root, root)
        }
    }
}

private fun readOnlyFixtureRelease(
    identity: String,
    version: String,
    visibility: String,
    availability: String,
): StoredRelease = StoredRelease(
    record = ReleaseRecord(
        `package` = identity,
        version = version,
        archive = ArchiveStats(sha256 = "e".repeat(64), compressedBytes = 1),
        manifestSha256 = "f".repeat(64),
        state = ReleaseState(lifecycle = "active", visibility = visibility, availability = availability),
        timestamps = ReleaseTimestamps(
            reservedAt = "2026-07-19T00:00:00Z",
            activatedAt = "2026-07-19T00:00:00Z",
            updatedAt = "2026-07-19T00:00:00Z",
        ),
        links = ReleaseLinks(
            self = "/packages/api/v1/packages/$identity/releases/$version",
            `package` = "/packages/api/v1/packages/$identity",
        ),
    ),
    ownerPrincipal = "internal-publisher",
    uploadId = "upl_fixture_${version.replace('.', '_')}",
    uploadExpiresAt = "2026-07-20T00:00:00Z",
    manifest = JsonObject(emptyMap()),
    source = SourceDeclaration(
        forge = "github",
        repositoryId = "1",
        installationId = "1",
        requestedRef = "refs/tags/v$version",
        expectedCommit = "1".repeat(40),
        licenseSpdx = "MIT",
    ),
)
