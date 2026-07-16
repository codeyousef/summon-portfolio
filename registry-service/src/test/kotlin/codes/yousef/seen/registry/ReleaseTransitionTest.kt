package codes.yousef.seen.registry

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseTransitionTest {
    @Test
    fun `only one concurrent transition can commit a release revision`() {
        val repository = InMemoryRegistryRepository()
        val reserved = storedRelease()
        assertTrue(repository.reserveRelease(reserved))
        val staleCopies = List(8) { repository.getRelease("seen/demo", "1.2.3")!! }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(staleCopies.size)

        try {
            val results = staleCopies.map { stale ->
                executor.submit<ReleaseTransitionResult> {
                    start.await()
                    repository.transitionRelease(
                        stale.revision,
                        stale.copy(
                            record = stale.record.copy(state = stale.record.state.copy(lifecycle = "quarantined")),
                            revision = stale.revision + 1,
                        ),
                    )
                }
            }
            start.countDown()
            val completed = results.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, completed.count { it is ReleaseTransitionResult.Applied })
            assertEquals(7, completed.count { it is ReleaseTransitionResult.Conflict })
            assertEquals("quarantined", repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
            assertEquals(1, repository.getRelease("seen/demo", "1.2.3")!!.revision)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `stale upload cannot regress a concurrently completed release`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = BlockingFirstQuarantineStorage()
        val config = testConfig()
        val tuf = TufPublisher(
            repository,
            storage,
            testOnlineSigners(),
            config.environment,
            config.repositoryId,
            config.registryOrigin,
            clock,
        )
        val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
        val principal = WriterPrincipal("publisher")
        val manifestBytes = manifestToml()
        val archive = archiveOf(
            "Seen.toml" to manifestBytes,
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
        )
        service.createPackage(CreatePackageRequest("seen/demo"), principal)
        val reservation = service.reserveRelease(
            "seen/demo",
            ReserveReleaseRequest(
                version = "1.2.3",
                visibility = "public",
                archive = ArchiveReservation("tar+gzip", sha256(archive), archive.size.toLong()),
                manifestSha256 = sha256(manifestBytes),
                manifest = manifestJson(),
                source = SourceDeclaration("github", "seen-demo", "installation-1", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
            ),
            principal,
        )
        val executor = Executors.newSingleThreadExecutor()

        try {
            val staleUpload = executor.submit {
                service.uploadArchive(reservation.upload.uploadId, sha256(archive), archive, principal)
            }
            assertTrue(storage.firstPutEntered.await(5, TimeUnit.SECONDS))

            service.uploadArchive(reservation.upload.uploadId, sha256(archive), archive, principal)
            val completed = service.completeUpload(
                reservation.upload.uploadId,
                CompleteUploadRequest(sha256(archive), archive.size.toLong()),
                principal,
            )
            storage.releaseFirstPut.countDown()
            staleUpload.get(5, TimeUnit.SECONDS)

            val persisted = repository.getRelease("seen/demo", "1.2.3")!!
            assertEquals("delayed", persisted.record.state.lifecycle)
            assertEquals(2, persisted.revision)
            assertEquals(completed.timestamps.publicDelayStartedAt, persisted.record.timestamps.publicDelayStartedAt)
            assertEquals(completed.timestamps.publicDelayEndsAt, persisted.record.timestamps.publicDelayEndsAt)
        } finally {
            storage.releaseFirstPut.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `stale completion snapshot is rejected after activation`() {
        val repository = InMemoryRegistryRepository()
        val reserved = storedRelease()
        assertTrue(repository.reserveRelease(reserved))
        val quarantined = reserved.copy(
            record = reserved.record.copy(state = reserved.record.state.copy(lifecycle = "quarantined")),
            revision = 1,
        )
        assertIs<ReleaseTransitionResult.Applied>(repository.transitionRelease(0, quarantined))
        val staleQuarantined = repository.getRelease("seen/demo", "1.2.3")!!
        val delayed = staleQuarantined.copy(
            record = staleQuarantined.record.copy(state = staleQuarantined.record.state.copy(lifecycle = "delayed")),
            revision = 2,
        )
        assertIs<ReleaseTransitionResult.Applied>(repository.transitionRelease(1, delayed))
        val active = delayed.copy(
            record = delayed.record.copy(state = delayed.record.state.copy(lifecycle = "active", availability = "available")),
            revision = 3,
        )
        assertIs<ReleaseTransitionResult.Applied>(repository.transitionRelease(2, active))

        val staleCompletion = staleQuarantined.copy(
            record = staleQuarantined.record.copy(state = staleQuarantined.record.state.copy(lifecycle = "delayed")),
            revision = 2,
        )
        val conflict = assertIs<ReleaseTransitionResult.Conflict>(
            repository.transitionRelease(staleQuarantined.revision, staleCompletion),
        )
        assertEquals("active", conflict.current.record.state.lifecycle)
        assertEquals(3, conflict.current.revision)
        assertEquals("active", repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
    }
}

private fun storedRelease(): StoredRelease {
    val now = "2026-07-16T00:00:00Z"
    return StoredRelease(
        record = ReleaseRecord(
            `package` = "seen/demo",
            version = "1.2.3",
            archive = ArchiveStats(sha256 = "a".repeat(64), compressedBytes = 128),
            manifestSha256 = "b".repeat(64),
            state = ReleaseState(visibility = "public"),
            timestamps = ReleaseTimestamps(reservedAt = now, updatedAt = now),
            links = ReleaseLinks("/packages/api/v1/packages/seen/demo/releases/1.2.3", "/packages/api/v1/packages/seen/demo"),
        ),
        ownerPrincipal = "publisher",
        uploadId = "upl_0123456789abcdef",
        uploadExpiresAt = "2026-07-16T01:00:00Z",
        manifest = manifestJson(),
        source = SourceDeclaration("github", "seen-demo", "installation-1", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
    )
}

private class BlockingFirstQuarantineStorage : RegistryObjectStorage {
    private val delegate = InMemoryRegistryObjectStorage()
    private val blockNextPut = AtomicBoolean(true)
    val firstPutEntered = CountDownLatch(1)
    val releaseFirstPut = CountDownLatch(1)

    override fun putQuarantine(uploadId: String, bytes: ByteArray) {
        delegate.putQuarantine(uploadId, bytes)
        if (blockNextPut.compareAndSet(true, false)) {
            firstPutEntered.countDown()
            check(releaseFirstPut.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release the first quarantine write" }
        }
    }

    override fun getQuarantine(uploadId: String): ByteArray? = delegate.getQuarantine(uploadId)
    override fun deleteQuarantine(uploadId: String) = delegate.deleteQuarantine(uploadId)
    override fun putPublicBlob(digest: String, bytes: ByteArray) = delegate.putPublicBlob(digest, bytes)
    override fun getPublicBlob(digest: String): ByteArray? = delegate.getPublicBlob(digest)
    override fun putMetadata(filename: String, bytes: ByteArray) = delegate.putMetadata(filename, bytes)
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean =
        delegate.replaceMetadataIfUnchanged(filename, expected, bytes)
    override fun getMetadata(filename: String): ByteArray? = delegate.getMetadata(filename)
}
