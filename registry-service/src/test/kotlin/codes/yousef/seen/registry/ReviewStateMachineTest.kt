package codes.yousef.seen.registry

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReviewStateMachineTest {
    private val machine = ReviewStateMachine(Duration.ofHours(72))
    private val digest = "a".repeat(64)

    @Test
    fun `public activation requires fresh proof two bound scans and exact delay boundary`() {
        val started = Instant.parse("2026-07-17T00:00:00Z")
        val initial = reviewStoredRelease()
        val quarantined = initial.copy(
            record = initial.record.copy(
                state = ReleaseState(lifecycle = "quarantined", visibility = "public"),
                timestamps = initial.record.timestamps.copy(quarantinedAt = started.utc()),
            ),
        )
        val firstClaim = machine.claimSourceVerification(quarantined, ReviewPhase.FIRST, started)
        val firstProof = machine.recordVerifiedSource(firstClaim, proof(ReviewPhase.FIRST, 1), started)
        assertEquals("first-scanning", firstProof.record.state.lifecycle)
        val firstScanClaim = machine.claimPackageScan(firstProof, ReviewPhase.FIRST, started.plusSeconds(1))
        val delayed = machine.recordPassedScan(firstScanClaim, scan(ReviewPhase.FIRST, 1, firstProof.review), started.plusSeconds(1))
        assertEquals("delayed", delayed.record.state.lifecycle)
        assertEquals("passed", delayed.record.verification.firstScan)

        val deadline = Instant.parse(delayed.record.timestamps.publicDelayEndsAt)
        assertFailsWith<IllegalArgumentException> {
            machine.claimSourceVerification(delayed, ReviewPhase.SECOND, deadline.minusNanos(1))
        }
        val secondClaim = machine.claimSourceVerification(delayed, ReviewPhase.SECOND, deadline)
        val secondProof = machine.recordVerifiedSource(secondClaim, proof(ReviewPhase.SECOND, 2), deadline)
        val secondScanClaim = machine.claimPackageScan(secondProof, ReviewPhase.SECOND, deadline.plusSeconds(1))
        val ready = machine.recordPassedScan(secondScanClaim, scan(ReviewPhase.SECOND, 2, secondProof.review), deadline.plusSeconds(1))
        assertEquals("ready", ready.record.state.lifecycle)
        assertEquals("passed", ready.record.verification.secondScan)

        val claimed = machine.claimPromotion(ready, id("act_", 3), deadline.plusSeconds(2))
        assertNotNull(claimed.review.promotionInputSha256)
        assertFailsWith<IllegalArgumentException> {
            machine.claimPromotion(claimed, id("act_", 4), deadline.plusSeconds(2))
        }
        val active = machine.activate(claimed, id("act_", 3), 42, deadline.plusSeconds(3))
        assertEquals("active", active.record.state.lifecycle)
        assertEquals("available", active.record.state.availability)
        assertEquals(42, active.record.resolverMetadataVersion)
    }

    @Test
    fun `scan cannot substitute archive or source proof evidence`() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val initial = reviewStoredRelease()
        val quarantined = initial.copy(
            record = initial.record.copy(state = ReleaseState(lifecycle = "quarantined", visibility = "public")),
        )
        val firstClaim = machine.claimSourceVerification(quarantined, ReviewPhase.FIRST, now)
        val firstProof = machine.recordVerifiedSource(firstClaim, proof(ReviewPhase.FIRST, 1), now)
        val scanClaim = machine.claimPackageScan(firstProof, ReviewPhase.FIRST, now)
        assertFailsWith<IllegalArgumentException> {
            machine.recordPassedScan(scanClaim, scan(ReviewPhase.FIRST, 1, firstProof.review).copy(archiveSha256 = "b".repeat(64)), now)
        }
        assertFailsWith<IllegalArgumentException> {
            machine.recordPassedScan(scanClaim, scan(ReviewPhase.FIRST, 1, firstProof.review).copy(sourceProofSha256 = "c".repeat(64)), now)
        }
    }

    @Test
    fun `retryable and definitive scanner failures fail closed`() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val initial = reviewStoredRelease()
        val quarantined = initial.copy(
            record = initial.record.copy(state = ReleaseState(lifecycle = "quarantined", visibility = "public")),
        )
        val claim = machine.claimSourceVerification(quarantined, ReviewPhase.FIRST, now)
        val scanning = machine.recordVerifiedSource(claim, proof(ReviewPhase.FIRST, 1), now)
        val scanClaim = machine.claimPackageScan(scanning, ReviewPhase.FIRST, now.plusSeconds(1))
        val retry = machine.recordRetryableReviewFailure(scanClaim, ReviewPhase.FIRST, now.plusSeconds(1))
        assertEquals("quarantined", retry.record.state.lifecycle)
        assertEquals("inconclusive", retry.record.verification.firstScan)

        val claimAgain = machine.claimSourceVerification(retry, ReviewPhase.FIRST, now.plusSeconds(2))
        val scanningAgain = machine.recordVerifiedSource(claimAgain, proof(ReviewPhase.FIRST, 2), now.plusSeconds(2))
        val scanClaimAgain = machine.claimPackageScan(scanningAgain, ReviewPhase.FIRST, now.plusSeconds(3))
        val rejected = machine.reject(scanClaimAgain, ReviewPhase.FIRST, "malware_signature", id("aud_", 4), now.plusSeconds(3))
        assertEquals("rejected", rejected.record.state.lifecycle)
        assertEquals("failed", rejected.record.verification.firstScan)
        assertFailsWith<IllegalArgumentException> {
            machine.claimPromotion(rejected, id("act_", 5), now.plusSeconds(4))
        }
    }

    @Test
    fun `private release becomes ready after one scan without public delay`() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val base = reviewStoredRelease()
        val quarantined = base.copy(record = base.record.copy(state = ReleaseState(lifecycle = "quarantined", visibility = "private")))
        val claim = machine.claimSourceVerification(quarantined, ReviewPhase.FIRST, now)
        val proof = machine.recordVerifiedSource(claim, proof(ReviewPhase.FIRST, 1), now)
        val scanClaim = machine.claimPackageScan(proof, ReviewPhase.FIRST, now.plusSeconds(1))
        val ready = machine.recordPassedScan(scanClaim, scan(ReviewPhase.FIRST, 1, proof.review), now.plusSeconds(1))
        assertEquals("ready", ready.record.state.lifecycle)
        assertEquals("not-required", ready.record.verification.secondScan)
        assertEquals(null, ready.record.timestamps.publicDelayEndsAt)
    }

    @Test
    fun `first source claim is live before its lease and only one recovery wins at the boundary`() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val lease = Duration.ofMinutes(5)
        val leasedMachine = ReviewStateMachine(Duration.ofHours(72), lease)
        val repository = InMemoryRegistryRepository()
        val quarantined = reviewStoredRelease().copy(record = reviewStoredRelease().record.copy(
            state = ReleaseState(lifecycle = "quarantined", visibility = "public"),
            timestamps = reviewStoredRelease().record.timestamps.copy(updatedAt = now.utc()),
        ))
        assertTrue(repository.reserveRelease(quarantined))
        val requested = leasedMachine.claimSourceVerification(quarantined, ReviewPhase.FIRST, now)
        val claimed = (repository.transitionRelease(quarantined.revision, requested) as ReleaseTransitionResult.Applied).value

        assertFalse(leasedMachine.isExpiredReviewClaim(
            claimed, ReviewPhase.FIRST, ReviewClaimKind.SOURCE, now.plus(lease).minusNanos(1),
        ))
        assertFailsWith<IllegalArgumentException> {
            leasedMachine.recoverExpiredReviewClaim(
                claimed, ReviewPhase.FIRST, ReviewClaimKind.SOURCE, now.plus(lease).minusNanos(1),
            )
        }

        val boundary = now.plus(lease)
        val recoveryA = leasedMachine.recoverExpiredReviewClaim(
            claimed, ReviewPhase.FIRST, ReviewClaimKind.SOURCE, boundary,
        )
        val recoveryB = leasedMachine.recoverExpiredReviewClaim(
            claimed, ReviewPhase.FIRST, ReviewClaimKind.SOURCE, boundary,
        )
        assertTrue(repository.transitionRelease(claimed.revision, recoveryA) is ReleaseTransitionResult.Applied)
        assertTrue(repository.transitionRelease(claimed.revision, recoveryB) is ReleaseTransitionResult.Conflict)
        val recovered = repository.getRelease("seen/demo", "1.2.3")!!
        assertEquals("quarantined", recovered.record.state.lifecycle)
        assertEquals(null, recovered.review.activeReviewClaim)
        assertEquals(0, recovered.record.verification.attestationSequence)
    }

    @Test
    fun `second scan claim recovery preserves first review and exact public delay`() {
        val now = Instant.parse("2026-07-17T00:00:00Z")
        val lease = Duration.ofMinutes(5)
        val leasedMachine = ReviewStateMachine(Duration.ofHours(72), lease)
        val quarantined = reviewStoredRelease().copy(record = reviewStoredRelease().record.copy(
            state = ReleaseState(lifecycle = "quarantined", visibility = "public"),
            timestamps = reviewStoredRelease().record.timestamps.copy(updatedAt = now.utc()),
        ))
        val firstSourceClaim = leasedMachine.claimSourceVerification(quarantined, ReviewPhase.FIRST, now)
        val firstProof = leasedMachine.recordVerifiedSource(firstSourceClaim, proof(ReviewPhase.FIRST, 1), now)
        val firstScanClaim = leasedMachine.claimPackageScan(firstProof, ReviewPhase.FIRST, now)
        val delayed = leasedMachine.recordPassedScan(firstScanClaim, scan(ReviewPhase.FIRST, 1, firstProof.review), now)
        val deadline = Instant.parse(delayed.record.timestamps.publicDelayEndsAt)
        val secondSourceClaim = leasedMachine.claimSourceVerification(delayed, ReviewPhase.SECOND, deadline)
        val secondProof = leasedMachine.recordVerifiedSource(secondSourceClaim, proof(ReviewPhase.SECOND, 2), deadline)
        val secondScanClaim = leasedMachine.claimPackageScan(secondProof, ReviewPhase.SECOND, deadline.plusSeconds(1))

        assertFalse(leasedMachine.isExpiredReviewClaim(
            secondScanClaim,
            ReviewPhase.SECOND,
            ReviewClaimKind.SCAN,
            deadline.plusSeconds(1).plus(lease).minusNanos(1),
        ))
        val recovered = leasedMachine.recoverExpiredReviewClaim(
            secondScanClaim,
            ReviewPhase.SECOND,
            ReviewClaimKind.SCAN,
            deadline.plusSeconds(1).plus(lease),
        )
        assertEquals("delayed", recovered.record.state.lifecycle)
        assertEquals(delayed.review.firstSourceProofId, recovered.record.sourceProofId)
        assertEquals(delayed.review.firstScanAttestationId, recovered.review.firstScanAttestationId)
        assertEquals(delayed.record.timestamps.publicDelayStartedAt, recovered.record.timestamps.publicDelayStartedAt)
        assertEquals(delayed.record.timestamps.publicDelayEndsAt, recovered.record.timestamps.publicDelayEndsAt)
        assertEquals(secondProof.record.verification.attestationSequence, recovered.record.verification.attestationSequence)
        assertEquals("inconclusive", recovered.record.verification.secondScan)
        assertEquals(null, recovered.review.activeReviewClaim)
    }

    private fun proof(phase: ReviewPhase, seed: Int) = VerifiedSourceEvidence(
        phase = phase,
        sequence = seed * 2L - 1L,
        proofId = id("prf_", seed),
        proofSha256 = seed.toString(16).padStart(64, '0'),
        archiveSha256 = digest,
    )

    private fun scan(phase: ReviewPhase, seed: Int, review: ReviewEvidenceState): PassedScanEvidence {
        val proofId = if (phase == ReviewPhase.FIRST) review.firstSourceProofId else review.secondSourceProofId
        val proofDigest = if (phase == ReviewPhase.FIRST) review.firstSourceProofSha256 else review.secondSourceProofSha256
        return PassedScanEvidence(
            phase = phase,
            sequence = seed * 2L,
            attestationId = id("scn_", seed),
            attestationSha256 = (seed + 10).toString(16).padStart(64, '0'),
            archiveSha256 = digest,
            sourceProofId = requireNotNull(proofId),
            sourceProofSha256 = requireNotNull(proofDigest),
        )
    }

    private fun id(prefix: String, seed: Int): String = prefix + seed.toString().padStart(16, '0')

    private fun reviewStoredRelease(): StoredRelease {
        val now = "2026-07-17T00:00:00Z"
        return StoredRelease(
            record = ReleaseRecord(
                `package` = "seen/demo",
                version = "1.2.3",
                archive = ArchiveStats(sha256 = digest, compressedBytes = 128),
                manifestSha256 = "b".repeat(64),
                state = ReleaseState(visibility = "public"),
                timestamps = ReleaseTimestamps(reservedAt = now, updatedAt = now),
                links = ReleaseLinks(
                    "/packages/api/v1/packages/seen/demo/releases/1.2.3",
                    "/packages/api/v1/packages/seen/demo",
                ),
            ),
            ownerPrincipal = "publisher",
            uploadId = "upl_0123456789abcdef",
            uploadExpiresAt = "2026-07-18T01:00:00Z",
            manifest = manifestJson(),
            source = SourceDeclaration(
                "github",
                "seen-demo",
                "installation-1",
                "refs/tags/v1.2.3",
                "a".repeat(40),
                "MIT",
            ),
        )
    }
}
