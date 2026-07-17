package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewEvidencePersistenceTest {
    @Test
    fun `review artifacts are append-only ordered and subject scoped`() {
        val repository = InMemoryRegistryRepository()
        val first = artifact("prf_0000000000000001", "source-proof", 1)
        val second = artifact("scn_0000000000000002", "scan-attestation", 2)
        val other = artifact("aud_0000000000000003", "audit-event", 3, identity = "seen/other")

        assertTrue(repository.appendReviewArtifact(first))
        assertFalse(repository.appendReviewArtifact(first.copy(payload = buildJsonObject { put("tampered", true) })))
        assertTrue(repository.appendReviewArtifact(second))
        assertTrue(repository.appendReviewArtifact(other))
        assertEquals(listOf(second.artifactId, first.artifactId), repository.listReviewArtifacts("seen/demo").map { it.artifactId })
        assertEquals(listOf(first.artifactId), repository.listReviewArtifacts("seen/demo", "1.2.3", "source-proof").map { it.artifactId })
        assertEquals(first, repository.getReviewArtifact(first.artifactId))
    }

    @Test
    fun `reviewed evidence cannot be replaced by a later transition`() {
        val repository = InMemoryRegistryRepository()
        val initial = reviewRelease().copy(
            review = ReviewEvidenceState(
                validatedArchiveSha256 = "a".repeat(64),
                firstSourceProofId = "prf_0000000000000001",
                firstSourceProofSha256 = "b".repeat(64),
                firstScanAttestationId = "scn_0000000000000001",
                firstScanAttestationSha256 = "c".repeat(64),
            ),
        )
        assertTrue(repository.reserveRelease(initial))
        val tampered = initial.copy(
            review = initial.review.copy(firstSourceProofSha256 = "d".repeat(64)),
            revision = 1,
        )
        assertFailsWith<IllegalArgumentException> { repository.transitionRelease(0, tampered) }
        assertEquals("b".repeat(64), repository.getRelease("seen/demo", "1.2.3")!!.review.firstSourceProofSha256)
    }

    @Test
    fun `compare and set permits one evidence binding winner`() {
        val repository = InMemoryRegistryRepository()
        val initial = reviewRelease()
        assertTrue(repository.reserveRelease(initial))
        val left = initial.copy(review = initial.review.copy(firstSourceProofId = "prf_0000000000000001"), revision = 1)
        val right = initial.copy(review = initial.review.copy(firstSourceProofId = "prf_0000000000000002"), revision = 1)
        assertIs<ReleaseTransitionResult.Applied>(repository.transitionRelease(0, left))
        val conflict = assertIs<ReleaseTransitionResult.Conflict>(repository.transitionRelease(0, right))
        assertEquals(left.review.firstSourceProofId, conflict.current.review.firstSourceProofId)
    }

    @Test
    fun `promotion activation commits release audit and catalog as one transaction`() {
        val repository = InMemoryRegistryRepository()
        val claimed = claimedPromotionRelease()
        val activated = activatedPromotionRelease(claimed)
        val audit = activationAudit(activated)
        assertTrue(repository.createPackage(reviewPackage()))
        assertTrue(repository.reserveRelease(claimed))

        val result = repository.commitPromotionActivation(claimed.revision, activated, audit)

        assertIs<PromotionActivationResult.Applied>(result)
        assertEquals("active", repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
        assertEquals("1.2.3", repository.getPackage("seen/demo")!!.record.latestActiveVersion)
        assertEquals(audit, repository.getReviewArtifact(audit.artifactId))
    }

    @Test
    fun `activation audit collision leaves release and catalog uncommitted`() {
        val repository = InMemoryRegistryRepository()
        val claimed = claimedPromotionRelease()
        val activated = activatedPromotionRelease(claimed)
        val audit = activationAudit(activated)
        assertTrue(repository.createPackage(reviewPackage()))
        assertTrue(repository.reserveRelease(claimed))
        assertTrue(repository.appendReviewArtifact(audit.copy(kind = "collision-sentinel")))

        val result = repository.commitPromotionActivation(claimed.revision, activated, audit)

        assertIs<PromotionActivationResult.AuditCollision>(result)
        assertEquals("ready", repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
        assertNull(repository.getPackage("seen/demo")!!.record.latestActiveVersion)
        assertEquals("collision-sentinel", repository.getReviewArtifact(audit.artifactId)!!.kind)
    }

    @Test
    fun `missing catalog package leaves release and activation audit uncommitted`() {
        val repository = InMemoryRegistryRepository()
        val claimed = claimedPromotionRelease()
        val activated = activatedPromotionRelease(claimed)
        val audit = activationAudit(activated)
        assertTrue(repository.reserveRelease(claimed))

        val result = repository.commitPromotionActivation(claimed.revision, activated, audit)

        assertIs<PromotionActivationResult.MissingPackage>(result)
        assertEquals("ready", repository.getRelease("seen/demo", "1.2.3")!!.record.state.lifecycle)
        assertNull(repository.getReviewArtifact(audit.artifactId))
        assertNull(repository.getPackage("seen/demo"))
    }

    private fun artifact(
        id: String,
        kind: String,
        sequence: Long,
        identity: String = "seen/demo",
    ) = ReviewArtifact(
        artifactId = id,
        kind = kind,
        packageIdentity = identity,
        version = "1.2.3",
        archiveSha256 = "a".repeat(64),
        sequence = sequence,
        createdAt = "2026-07-17T00:00:0${sequence}Z",
        payload = buildJsonObject { put("id", id) },
    )

    private fun reviewRelease(): StoredRelease {
        val now = "2026-07-17T00:00:00Z"
        return StoredRelease(
            record = ReleaseRecord(
                `package` = "seen/demo",
                version = "1.2.3",
                archive = ArchiveStats(sha256 = "a".repeat(64), compressedBytes = 128),
                manifestSha256 = "b".repeat(64),
                state = ReleaseState(visibility = "public"),
                timestamps = ReleaseTimestamps(reservedAt = now, updatedAt = now),
                links = ReleaseLinks("/self", "/package"),
            ),
            ownerPrincipal = "publisher",
            uploadId = "upl_0123456789abcdef",
            uploadExpiresAt = "2026-07-18T00:00:00Z",
            manifest = manifestJson(),
            source = SourceDeclaration("github", "1", "2", "refs/heads/main", "a".repeat(40), "MIT"),
        )
    }

    private fun reviewPackage() = StoredPackage(
        record = PackageRecord(
            identity = "seen/demo",
            repository = "https://github.com/seen/demo",
            licenseSpdx = "MIT",
            createdAt = "2026-07-17T00:00:00Z",
            updatedAt = "2026-07-17T00:00:00Z",
            links = PackageLinks("/self", "/releases"),
        ),
        ownerPrincipal = "publisher",
    )

    private fun claimedPromotionRelease() = reviewRelease().copy(
        record = reviewRelease().record.copy(
            state = ReleaseState(lifecycle = "ready", visibility = "public", availability = "unavailable"),
            timestamps = reviewRelease().record.timestamps.copy(
                readyAt = "2026-07-20T00:00:00Z",
                updatedAt = "2026-07-20T00:00:00Z",
            ),
        ),
        review = ReviewEvidenceState(promotionAttemptId = "act_0000000000000001"),
    )

    private fun activatedPromotionRelease(claimed: StoredRelease) = claimed.copy(
        record = claimed.record.copy(
            state = claimed.record.state.copy(lifecycle = "active", availability = "available"),
            resolverMetadataVersion = 7,
            timestamps = claimed.record.timestamps.copy(
                activatedAt = "2026-07-20T00:00:01Z",
                updatedAt = "2026-07-20T00:00:01Z",
            ),
        ),
        revision = claimed.revision + 1,
    )

    private fun activationAudit(activated: StoredRelease): ReviewArtifact {
        val event = AuditEventRecord(
            eventId = "aud_0000000000000001",
            sequence = 1,
            action = "promotion",
            outcome = "activated",
            actor = AuditActor("worker", "release-promoter"),
            subject = AuditSubject(
                activated.record.`package`,
                activated.record.version,
                activated.record.archive.sha256,
            ),
            evidenceIds = listOf(assertNotNull(activated.review.promotionAttemptId)),
            occurredAt = assertNotNull(activated.record.timestamps.activatedAt),
        )
        return event.toArtifact()
    }
}
