package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReviewWorkersTest {
    private val clock = MutableClock(Instant.parse("2026-07-17T00:00:00Z"))
    private val ids = object : ReviewIdGenerator {
        private var sequence = 0
        override fun next(prefix: String): String = prefix + (++sequence).toString().padStart(16, '0')
    }

    @Test
    fun `source and scan workers bind exact evidence before starting delay`() {
        val fixture = fixture()
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        val scanning = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("first-scanning", scanning.record.state.lifecycle)
        assertNotNull(scanning.review.firstSourceProofId)

        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.scan.runOnce())
        val delayed = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("delayed", delayed.record.state.lifecycle)
        assertEquals("passed", delayed.record.verification.firstScan)
        assertNotNull(delayed.record.timestamps.publicDelayEndsAt)
        assertEquals(2, fixture.repository.listReviewArtifacts("seen/demo", "1.2.3").size)
    }

    @Test
    fun `missing crashed and mismatched scanner evidence never advances`() {
        listOf<PackageScanEngine>(
            PackageScanEngine { null },
            PackageScanEngine { error("scanner crashed") },
            PackageScanEngine { input ->
                validAttestation(input).copy(subject = validAttestation(input).subject.copy(archiveSha256 = "f".repeat(64)))
            },
        ).forEachIndexed { index, engine ->
            val fixture = fixture(scanner = engine)
            assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
            val outcome = fixture.scan.runOnce()
            val release = fixture.repository.getRelease("seen/demo", "1.2.3")!!
            if (index < 2) {
                assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, outcome)
                assertEquals("quarantined", release.record.state.lifecycle)
            } else {
                assertEquals(ReviewWorkerOutcome.REJECTED, outcome)
                assertEquals("rejected", release.record.state.lifecycle)
            }
            assertTrue(fixture.repository.listReviewArtifacts(kind = AUDIT_EVENT_ARTIFACT).isNotEmpty())
            assertEquals("unavailable", release.record.state.availability)
        }
    }

    @Test
    fun `scanner timeout fails closed with durable audit evidence`() {
        val fixture = fixture(scanner = PackageScanEngine { throw PackageScanTimeoutException() })
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())

        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.scan.runOnce())

        val release = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("quarantined", release.record.state.lifecycle)
        assertEquals("unavailable", release.record.state.availability)
        val audit = fixture.repository.listReviewArtifacts(kind = AUDIT_EVENT_ARTIFACT)
            .map { RegistryJson.decodeFromJsonElement<AuditEventRecord>(it.payload) }
            .single()
        assertEquals("scanner_timeout", audit.internalReason)
    }

    @Test
    fun `second review begins only at exact public deadline and produces a fresh proof`() {
        val fixture = fixture()
        fixture.source.runOnce()
        fixture.scan.runOnce()
        val delayed = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val firstProof = delayed.review.firstSourceProofId
        clock.advance(Duration.ofHours(72).minusNanos(1))
        assertEquals(ReviewWorkerOutcome.NO_WORK, fixture.source.runOnce())
        clock.advance(Duration.ofNanos(1))
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        val secondScanning = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertTrue(secondScanning.review.secondSourceProofId != firstProof)
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.scan.runOnce())
        assertEquals("ready", fixture.repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
    }

    @Test
    fun `source worker rejects a verifier result substituted for another repository`() {
        val fixture = fixture(sourceTransform = { it.copy(repositoryId = "999") })

        assertEquals(ReviewWorkerOutcome.REJECTED, fixture.source.runOnce())
        val rejected = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("rejected", rejected.record.state.lifecycle)
        assertTrue(fixture.repository.listReviewArtifacts(kind = SOURCE_PROOF_ARTIFACT).isEmpty())
        assertTrue(fixture.repository.listReviewArtifacts(kind = AUDIT_EVENT_ARTIFACT).isNotEmpty())
    }

    @Test
    fun `successful second scan anchors directly to first passed scan after retryable evidence`() {
        var emittedSecondTimeout = false
        val fixture = fixture(scanner = PackageScanEngine { input ->
            val valid = validAttestation(input)
            if (input.phase == ReviewPhase.SECOND && !emittedSecondTimeout) {
                emittedSecondTimeout = true
                valid.copy(result = valid.result.copy(
                    status = "timeout",
                    reason = "scanner-timeout",
                    disposition = "retry-unavailable",
                    reportSha256 = null,
                ))
            } else {
                valid
            }
        })
        fixture.source.runOnce()
        fixture.scan.runOnce()
        val firstScanId = fixture.repository.getRelease("seen/demo", "1.2.3")!!.review.firstScanAttestationId
        clock.advance(Duration.ofHours(72))
        fixture.source.runOnce()
        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.scan.runOnce())
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.scan.runOnce())

        val ready = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val second = RegistryJson.decodeFromJsonElement<ScanAttestationRecord>(
            fixture.repository.getReviewArtifact(requireNotNull(ready.review.secondScanAttestationId))!!.payload,
        )
        assertEquals(firstScanId, second.previousAttestationId)
    }

    @Test
    fun `live source claim is not stolen and expiry recovery is audited`() {
        val fixture = fixture()
        val current = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val claim = fixture.stateMachine.claimSourceVerification(current, ReviewPhase.FIRST, clock.instant())
        assertTrue(fixture.repository.transitionRelease(current.revision, claim) is ReleaseTransitionResult.Applied)

        clock.advance(Duration.ofMinutes(10).minusNanos(1))
        assertEquals(ReviewWorkerOutcome.NO_WORK, fixture.source.runOnce())
        val live = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("first-source", live.review.activeReviewClaim)

        clock.advance(Duration.ofNanos(1))
        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.source.runOnce())
        val recovered = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("quarantined", recovered.record.state.lifecycle)
        assertEquals(null, recovered.review.activeReviewClaim)
        val audit = fixture.repository.listReviewArtifacts(kind = AUDIT_EVENT_ARTIFACT)
            .map { RegistryJson.decodeFromJsonElement<AuditEventRecord>(it.payload) }
            .single()
        assertEquals("review_claim_lease_expired", audit.internalReason)
    }

    @Test
    fun `crashed scan claim recovers only after its own updated-at lease`() {
        val fixture = fixture()
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        val sourceReady = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val requested = fixture.stateMachine.claimPackageScan(sourceReady, ReviewPhase.FIRST, clock.instant())
        val claimed = (fixture.repository.transitionRelease(sourceReady.revision, requested) as ReleaseTransitionResult.Applied).value
        val acceptedSequence = claimed.record.verification.attestationSequence

        clock.advance(Duration.ofMinutes(10).minusNanos(1))
        assertEquals(ReviewWorkerOutcome.NO_WORK, fixture.scan.runOnce())
        assertEquals("first-scan", fixture.repository.getRelease("seen/demo", "1.2.3")!!.review.activeReviewClaim)

        clock.advance(Duration.ofNanos(1))
        assertEquals(ReviewWorkerOutcome.RETRYABLE_FAILURE, fixture.scan.runOnce())
        val recovered = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("quarantined", recovered.record.state.lifecycle)
        assertEquals(acceptedSequence, recovered.record.verification.attestationSequence)
        assertEquals("review_claim_lease_expired", RegistryJson.decodeFromJsonElement<AuditEventRecord>(
            fixture.repository.listReviewArtifacts(kind = AUDIT_EVENT_ARTIFACT).single().payload,
        ).internalReason)
    }

    @Test
    fun `orphaned proof and scan appends cannot poison canonical TUF review chains`() {
        val fixture = fixture()
        appendOrphan(fixture.repository, "prf_orphanedproof0001", SOURCE_PROOF_ARTIFACT, 1)
        appendOrphan(fixture.repository, "scn_orphanedscan00001", SCAN_ATTESTATION_ARTIFACT, 2)

        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.scan.runOnce())
        val delayed = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val firstProofId = requireNotNull(delayed.review.firstSourceProofId)
        val firstScanId = requireNotNull(delayed.review.firstScanAttestationId)

        appendOrphan(fixture.repository, "prf_orphanedproof0002", SOURCE_PROOF_ARTIFACT, 5)
        appendOrphan(fixture.repository, "scn_orphanedscan00002", SCAN_ATTESTATION_ARTIFACT, 6)
        clock.advance(Duration.ofHours(72))
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.source.runOnce())
        assertEquals(ReviewWorkerOutcome.APPLIED, fixture.scan.runOnce())

        val ready = fixture.repository.getRelease("seen/demo", "1.2.3")!!
        val firstProof = RegistryJson.decodeFromJsonElement<SourceProofRecord>(
            fixture.repository.getReviewArtifact(firstProofId)!!.payload,
        )
        val firstScan = RegistryJson.decodeFromJsonElement<ScanAttestationRecord>(
            fixture.repository.getReviewArtifact(firstScanId)!!.payload,
        )
        val secondProof = RegistryJson.decodeFromJsonElement<SourceProofRecord>(
            fixture.repository.getReviewArtifact(requireNotNull(ready.review.secondSourceProofId))!!.payload,
        )
        val secondScan = RegistryJson.decodeFromJsonElement<ScanAttestationRecord>(
            fixture.repository.getReviewArtifact(requireNotNull(ready.review.secondScanAttestationId))!!.payload,
        )
        assertEquals(null, firstProof.previousProofId)
        assertEquals(firstProof.proofId, secondProof.previousProofId)
        assertEquals(null, firstScan.previousAttestationId)
        assertEquals(firstScan.attestationId, secondScan.previousAttestationId)
        assertTrue(firstProof.sequence < firstScan.sequence)
        assertTrue(firstScan.sequence < secondProof.sequence)
        assertTrue(secondProof.sequence < secondScan.sequence)
        assertEquals(secondScan.sequence, ready.record.verification.attestationSequence)
    }

    private fun fixture(
        scanner: PackageScanEngine = PackageScanEngine(::validAttestation),
        sourceTransform: (SourceVerificationResult) -> SourceVerificationResult = { it },
    ): WorkerFixture {
        val repository = InMemoryRegistryRepository()
        val archive = "a".repeat(64)
        val now = clock.instant().utc()
        repository.createPackage(StoredPackage(
            PackageRecord(
                identity = "seen/demo",
                repository = "https://github.com/seen/demo",
                licenseSpdx = "MIT",
                createdAt = now,
                updatedAt = now,
                links = PackageLinks("/self", "/releases"),
            ),
            "publisher",
        ))
        repository.reserveRelease(StoredRelease(
            record = ReleaseRecord(
                `package` = "seen/demo",
                version = "1.2.3",
                archive = ArchiveStats(sha256 = archive, compressedBytes = 128),
                manifestSha256 = "b".repeat(64),
                state = ReleaseState(lifecycle = "quarantined", visibility = "public"),
                timestamps = ReleaseTimestamps(reservedAt = now, quarantinedAt = now, updatedAt = now),
                links = ReleaseLinks("/self", "/package"),
            ),
            ownerPrincipal = "publisher",
            uploadId = "upl_0123456789abcdef",
            uploadExpiresAt = "2026-07-18T00:00:00Z",
            manifest = buildJsonObject {},
            source = SourceDeclaration("github", "123", "456", "refs/tags/v1.2.3", "d".repeat(40), "MIT"),
        ))
        val inspection = ReviewArchiveInspection(
            archive,
            "manifest-version = 1".encodeToByteArray(),
            listOf(
                SourceArchiveFile("Seen.toml", "manifest-version = 1".encodeToByteArray()),
                SourceArchiveFile("src/main.seen", "fun main() {}".encodeToByteArray()),
            ),
        )
        val stateMachine = ReviewStateMachine(Duration.ofHours(72))
        val sourceResult = SourceVerificationResult(
            forge = SourceForge.GITHUB,
            repositoryId = "123",
            canonicalRepositoryUrl = "https://github.com/seen/demo",
            installationIdentity = "456",
            requestedRef = "refs/tags/v1.2.3",
            resolvedCommit = "d".repeat(40),
            packageDirectory = "",
            treeObjectId = "e".repeat(40),
            treeEvidenceSha256 = "f".repeat(64),
            archiveSha256 = archive,
            archiveFileSetSha256 = "1".repeat(64),
            licenseSpdx = "MIT",
            licensePath = "LICENSE",
            licenseEvidenceSha256 = "2".repeat(64),
            verifiedAt = clock.instant(),
            proofSha256 = "3".repeat(64),
        )
        return WorkerFixture(
            repository,
            SourceReviewWorker(
                repository,
                { inspection },
                { sourceTransform(sourceResult.copy(verifiedAt = clock.instant())) },
                stateMachine,
                clock,
                ids,
            ),
            ScanReviewWorker(repository, { inspection }, scanner, stateMachine, clock, ids),
            stateMachine,
        )
    }

    private fun appendOrphan(repository: RegistryRepository, artifactId: String, kind: String, sequence: Long) {
        assertTrue(repository.appendReviewArtifact(ReviewArtifact(
            artifactId = artifactId,
            kind = kind,
            packageIdentity = "seen/demo",
            version = "1.2.3",
            archiveSha256 = "a".repeat(64),
            sequence = sequence,
            createdAt = clock.instant().utc(),
            payload = buildJsonObject {},
        )))
    }

    private fun validAttestation(input: PackageScanInput) = ScanAttestationRecord(
        attestationId = input.attestationId,
        sequence = input.sequence,
        previousAttestationId = input.previousAttestationId,
        subject = ScanSubject(
            input.packageIdentity,
            input.version,
            input.archiveSha256,
            input.sourceProof.proofId,
            input.sourceProofSha256,
        ),
        scan = ScanDescriptor(input.phase.name.lowercase(), 1, "package-scan-v1.0.0", "4".repeat(64)),
        scanner = ScannerIdentity("scanner", "1.0.0", true, "none", "none", "read-only"),
        input = ScanInputBinding(input.archiveSha256, input.sourceProofSha256, true),
        sandbox = ScanSandbox(true, true, "none", 2_000, 536_870_912, 64),
        invocation = ScanInvocation(
            "scan-run-worker-00000001", clock.instant().utc(), clock.instant().utc(), 300,
        ),
        result = ScanResult(
            "passed",
            disposition = "promotion-eligible",
            observedArchiveSha256 = input.archiveSha256,
            observedSourceProofSha256 = input.sourceProofSha256,
            findings = emptyList(),
            reportSha256 = "6".repeat(64),
            evidenceSha256 = "5".repeat(64),
        ),
        generatedAt = clock.instant().utc(),
    )

    private data class WorkerFixture(
        val repository: InMemoryRegistryRepository,
        val source: SourceReviewWorker,
        val scan: ScanReviewWorker,
        val stateMachine: ReviewStateMachine,
    )
}
