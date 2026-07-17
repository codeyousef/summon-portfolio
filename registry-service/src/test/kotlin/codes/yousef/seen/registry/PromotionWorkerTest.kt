package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromotionWorkerTest {
    private val now = Instant.parse("2026-07-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `promotes only the exact two-scan evidence chain`() {
        val fixture = fixture()
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.worker.runOnce())

        val active = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("active", active.record.state.lifecycle)
        assertEquals("available", active.record.state.availability)
        assertEquals(7, active.record.resolverMetadataVersion)
        assertNotNull(fixture.storage.getPublicBlob(active.record.archive.sha256))
        assertNull(fixture.storage.getQuarantine(active.uploadId))
        assertEquals("1.2.3", fixture.repository.getPackage("seen/demo")!!.record.latestActiveVersion)
        assertEquals(1, fixture.publications)
    }

    @Test
    fun `missing or substituted evidence is rejected without publication`() {
        val fixture = fixture(omitSecondScan = true)
        assertEquals(ReviewWorkerOutcome.REJECTED, fixture.worker.runOnce())
        val rejected = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("rejected", rejected.record.state.lifecycle)
        assertEquals("unavailable", rejected.record.state.availability)
        assertNull(fixture.storage.getPublicBlob(rejected.record.archive.sha256))
        assertEquals(0, fixture.publications)
    }

    @Test
    fun `release size mismatch fails closed before metadata publication`() {
        val fixture = fixture(declaredCompressedBytes = "reviewed archive".encodeToByteArray().size.toLong() + 1)

        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.worker.runOnce())

        val release = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("ready", release.record.state.lifecycle)
        assertEquals("unavailable", release.record.state.availability)
        assertNull(fixture.storage.getPublicBlob(release.record.archive.sha256))
        assertNotNull(fixture.storage.getQuarantine(release.uploadId))
        assertEquals(0, fixture.publications)
    }

    @Test
    fun `activation audit is durable before best effort quarantine cleanup`() {
        val fixture = fixture(failQuarantineDelete = true)

        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.worker.runOnce())

        val release = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("active", release.record.state.lifecycle)
        assertNotNull(fixture.storage.getPublicBlob(release.record.archive.sha256))
        assertNotNull(fixture.storage.getQuarantine(release.uploadId))
        assertTrue(fixture.activationAuditPresentAtDelete)
        val activationAudits = fixture.repository
            .listReviewArtifacts("seen/demo", "1.2.3", AUDIT_EVENT_ARTIFACT)
            .map { RegistryJson.decodeFromJsonElement<AuditEventRecord>(it.payload) }
            .filter { it.action == "promotion" && it.outcome == "activated" }
        assertEquals(1, activationAudits.size)
    }

    @Test
    fun `source proof for a different immutable repository is rejected`() {
        val fixture = fixture(secondProofRepositoryId = "999")

        assertEquals(ReviewWorkerOutcome.REJECTED, fixture.worker.runOnce())
        assertEquals("rejected", fixture.repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
        assertEquals(0, fixture.publications)
    }

    @Test
    fun `malformed evidence chain is rejected before publication`() {
        val malformed = listOf(
            fixture(firstProofPrevious = "prf_0000000000000099"),
            fixture(secondProofPrevious = null),
            fixture(firstScanPrevious = "scn_0000000000000099"),
            fixture(secondProofSequence = 2),
            fixture(releaseAttestationSequence = 3),
        )

        malformed.forEach { fixture ->
            assertEquals(ReviewWorkerOutcome.REJECTED, fixture.worker.runOnce())
            assertEquals("rejected", fixture.repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
            assertEquals(0, fixture.publications)
            assertNull(fixture.storage.getPublicBlob(fixture.archiveSha256))
        }
    }

    @Test
    fun `activation audit collision leaves release and catalog inactive and reconciles metadata`() {
        val fixture = fixture(activationAuditCollision = true)

        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.worker.runOnce())

        val release = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("ready", release.record.state.lifecycle)
        assertEquals("unavailable", release.record.state.availability)
        assertNull(fixture.repository.getPackage("seen/demo")!!.record.latestActiveVersion)
        assertEquals(listOf(1, 0), fixture.publishedSelections)
        assertNotNull(fixture.storage.getQuarantine(release.uploadId))
    }

    private fun fixture(
        omitSecondScan: Boolean = false,
        declaredCompressedBytes: Long? = null,
        failQuarantineDelete: Boolean = false,
        secondProofRepositoryId: String = "123",
        activationAuditCollision: Boolean = false,
        firstProofPrevious: String? = null,
        secondProofPrevious: String? = "prf_0000000000000001",
        firstScanPrevious: String? = null,
        secondProofSequence: Long = 3,
        releaseAttestationSequence: Long = 4,
    ): Fixture {
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val bytes = "reviewed archive".encodeToByteArray()
        val archive = sha256(bytes)
        val proof1 = proof(
            "prf_0000000000000001",
            1,
            archive,
            "2026-07-17T00:00:00Z",
            previousProofId = firstProofPrevious,
        )
        val scan1 = scan("scn_0000000000000001", 2, firstScanPrevious, ReviewPhase.FIRST, archive, proof1)
        val proof2 = proof(
            "prf_0000000000000002",
            secondProofSequence,
            archive,
            "2026-07-20T00:00:00Z",
            secondProofRepositoryId,
            secondProofPrevious,
        )
        val scan2 = scan("scn_0000000000000002", 4, scan1.attestationId, ReviewPhase.SECOND, archive, proof2)
        val timestamp = now.utc()
        repository.createPackage(StoredPackage(
            PackageRecord(
                identity = "seen/demo",
                repository = "https://github.com/seen/demo",
                licenseSpdx = "MIT",
                createdAt = timestamp,
                updatedAt = timestamp,
                links = PackageLinks("/self", "/releases"),
            ),
            "publisher",
        ))
        repository.reserveRelease(StoredRelease(
            record = ReleaseRecord(
                `package` = "seen/demo",
                version = "1.2.3",
                archive = ArchiveStats(sha256 = archive, compressedBytes = declaredCompressedBytes ?: bytes.size.toLong()),
                manifestSha256 = "b".repeat(64),
                state = ReleaseState(lifecycle = "ready", visibility = "public", availability = "unavailable"),
                sourceProofId = proof2.proofId,
                verification = ReleaseVerification(
                    origin = "passed", integrity = "passed", source = "passed",
                    firstScan = "passed", secondScan = "passed", attestationSequence = releaseAttestationSequence,
                ),
                timestamps = ReleaseTimestamps(
                    reservedAt = "2026-07-16T00:00:00Z",
                    quarantinedAt = "2026-07-16T00:01:00Z",
                    publicDelayStartedAt = "2026-07-17T00:00:00Z",
                    publicDelayEndsAt = "2026-07-20T00:00:00Z",
                    readyAt = timestamp,
                    updatedAt = timestamp,
                ),
                links = ReleaseLinks("/self", "/package", sourceProof = "/proof/${proof2.proofId}"),
            ),
            ownerPrincipal = "publisher",
            uploadId = "upl_0000000000000001",
            uploadExpiresAt = "2026-07-21T00:00:00Z",
            manifest = buildJsonObject {},
            source = SourceDeclaration("github", "123", "456", "refs/tags/v1.2.3", "d".repeat(40), "MIT"),
            review = ReviewEvidenceState(
                validatedArchiveSha256 = archive,
                firstSourceProofId = proof1.proofId,
                firstSourceProofSha256 = proof1.sha256(),
                firstScanAttestationId = scan1.attestationId,
                firstScanAttestationSha256 = scan1.sha256(),
                secondSourceProofId = proof2.proofId,
                secondSourceProofSha256 = proof2.sha256(),
                secondScanAttestationId = scan2.attestationId,
                secondScanAttestationSha256 = scan2.sha256(),
            ),
        ))
        listOf(proof1.toArtifact(), scan1.toArtifact(), proof2.toArtifact()).forEach {
            check(repository.appendReviewArtifact(it))
        }
        if (!omitSecondScan) check(repository.appendReviewArtifact(scan2.toArtifact()))
        storage.putQuarantine("upl_0000000000000001", bytes)
        var publications = 0
        val idGenerator = object : ReviewIdGenerator {
            private var value = 0
            override fun next(prefix: String): String = prefix + (++value).toString().padStart(16, '0')
        }
        if (activationAuditCollision) {
            check(repository.appendReviewArtifact(ReviewArtifact(
                artifactId = "aud_0000000000000002",
                kind = "collision-sentinel",
                packageIdentity = "seen/demo",
                version = "1.2.3",
                archiveSha256 = archive,
                sequence = 99,
                createdAt = now.utc(),
                payload = buildJsonObject {},
            )))
        }
        val inspector = ReviewArchiveInspector {
            ReviewArchiveInspection(archive, ByteArray(0), emptyList(), emptySet())
        }
        var activationAuditPresentAtDelete = false
        val workerStorage: RegistryObjectStorage = if (failQuarantineDelete) {
            object : RegistryObjectStorage by storage {
                override fun deleteQuarantine(uploadId: String) {
                    activationAuditPresentAtDelete = repository
                        .listReviewArtifacts("seen/demo", "1.2.3", AUDIT_EVENT_ARTIFACT)
                        .map { RegistryJson.decodeFromJsonElement<AuditEventRecord>(it.payload) }
                        .any { it.action == "promotion" && it.outcome == "activated" }
                    throw IllegalStateException("simulated quarantine cleanup failure")
                }
            }
        } else storage
        val publishedSelections = mutableListOf<Int>()
        val worker = PromotionReviewWorker(
            repository,
            workerStorage,
            inspector,
            ReleaseMetadataPublisher { releases ->
                publications++
                publishedSelections += releases.count {
                    it.record.`package` == "seen/demo" && it.record.state.availability == "available"
                }
                6L + publications
            },
            ReviewStateMachine(Duration.ofHours(72)),
            clock,
            idGenerator,
        )
        return Fixture(
            repository,
            storage,
            worker,
            { publications },
            { activationAuditPresentAtDelete },
            { publishedSelections.toList() },
            archive,
        )
    }

    private fun proof(
        id: String,
        sequence: Long,
        archive: String,
        at: String,
        repositoryId: String = "123",
        previousProofId: String? = null,
    ) = SourceProofRecord(
        proofId = id,
        sequence = sequence,
        previousProofId = previousProofId,
        packageIdentity = "seen/demo",
        version = "1.2.3",
        repository = SourceProofRepository("github", repositoryId, "https://github.com/seen/demo", "456"),
        requestedRef = "refs/tags/v1.2.3",
        resolvedRef = "refs/tags/v1.2.3",
        commit = SourceProofCommit("sha1", "d".repeat(40)),
        archive = SourceProofArchive("e".repeat(64), archive),
        license = SourceProofLicense("MIT", "f".repeat(64), true),
        status = "verified",
        checks = listOf(
            "repository-identity",
            "installation-identity",
            "commit-resolution",
            "archive-digest",
            "license",
        ).map { SourceProofCheck(it, "passed", at, "1".repeat(64)) },
        verifiedAt = at,
        verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
    )

    private fun scan(
        id: String,
        sequence: Long,
        previous: String?,
        phase: ReviewPhase,
        archive: String,
        proof: SourceProofRecord,
    ) = ScanAttestationRecord(
        attestationId = id,
        sequence = sequence,
        previousAttestationId = previous,
        subject = ScanSubject("seen/demo", "1.2.3", archive, proof.proofId, proof.sha256()),
        scan = ScanDescriptor(phase.name.lowercase(), 1, "package-scan-v1.0.0", "2".repeat(64)),
        scanner = ScannerIdentity("seen-package-scanner", "1.0.0", true, "none", "none", "read-only"),
        input = ScanInputBinding(archive, proof.sha256(), true),
        sandbox = ScanSandbox(true, true, "none", 2_000, 536_870_912, 64),
        invocation = ScanInvocation("scan-run-${phase.name.lowercase()}-00000001", now.utc(), now.utc(), 300),
        result = ScanResult(
            status = "passed",
            disposition = "promotion-eligible",
            observedArchiveSha256 = archive,
            observedSourceProofSha256 = proof.sha256(),
            findings = emptyList(),
            reportSha256 = "3".repeat(64),
            evidenceSha256 = "4".repeat(64),
        ),
        generatedAt = now.utc(),
    )

    private class Fixture(
        val repository: InMemoryRegistryRepository,
        val storage: InMemoryRegistryObjectStorage,
        val worker: PromotionReviewWorker,
        publications: () -> Int,
        activationAuditPresentAtDelete: () -> Boolean,
        publishedSelections: () -> List<Int>,
        val archiveSha256: String,
    ) {
        val publications: Int get() = publicationsCount()
        val activationAuditPresentAtDelete: Boolean get() = activationAuditPresentAtDeleteValue()
        val publishedSelections: List<Int> get() = publishedSelectionsValue()
        private val publicationsCount = publications
        private val activationAuditPresentAtDeleteValue = activationAuditPresentAtDelete
        private val publishedSelectionsValue = publishedSelections
    }
}
