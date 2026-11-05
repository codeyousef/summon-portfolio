package code.yousef.firestore.migration

import code.yousef.firestore.PortfolioFirestoreCollections
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortfolioFirestoreDevBackfillTest {
    @Test
    fun `migration collection contract covers business data and excludes asymmetric replication metadata`() {
        assertEquals(
            setOf(
                "projects",
                "services",
                "blog_posts",
                "testimonials",
                "hero",
                "contact_submissions",
                "photography_photos",
            ),
            PortfolioFirestoreMigrationCollections.content,
        )
        assertTrue(PortfolioFirestoreMigrationCollections.all.containsAll(PortfolioFirestoreCollections.all))
        assertEquals(
            setOf("_migration_receipts", "_migration_outbox", "_migration_aggregate_state"),
            PortfolioFirestoreMigrationCollections.migrationMetadata,
        )
        assertTrue(
            PortfolioFirestoreMigrationCollections.all
                .intersect(PortfolioFirestoreMigrationCollections.migrationMetadata)
                .isEmpty(),
        )
        assertFalse(PortfolioFirestoreMigrationCollections.PROOFS in PortfolioFirestoreMigrationCollections.all)
    }

    @Test
    fun `plan writes only source-owned missing and differing documents and never target-only data`() {
        val sameSource = document("same", mapOf("value" to 1L))
        val sameTarget = document("same", mapOf("value" to 1L))
        val source = linkedMapOf(
            "same" to sameSource,
            "missing" to document("missing", mapOf("value" to 2L)),
            "changed" to document("changed", mapOf("value" to 3L)),
        )
        val target = linkedMapOf(
            "same" to sameTarget,
            "changed" to document("changed", mapOf("value" to 4L)),
            "target-only" to document("target-only", mapOf("value" to 5L)),
        )

        val plan = planFirestoreBackfill(source, target)

        assertEquals(listOf("changed", "missing"), plan.writes.map(FirestoreBackfillDocument::id))
        assertEquals(1, plan.sourceOnlyCount)
        assertEquals(1, plan.targetOnlyCount)
        assertEquals(1, plan.differingCount)
        assertFalse(plan.writes.any { it.id == "target-only" })
        assertEquals(
            setOf("missing", "changed", "target-only"),
            firestoreBackfillDiscrepancyIds(source, target),
        )
    }

    @Test
    fun `canonical hashes are deterministic and preserve Firestore numeric types`() {
        val first = mapOf("nested" to linkedMapOf("b" to 2L, "a" to listOf(true, "x")), "number" to 1L)
        val reordered = linkedMapOf("number" to 1L, "nested" to linkedMapOf("a" to listOf(true, "x"), "b" to 2L))

        assertEquals(FirestoreCanonicalHash.document(first), FirestoreCanonicalHash.document(reordered))
        assertFalse(FirestoreCanonicalHash.document(mapOf("number" to 1L)) == FirestoreCanonicalHash.document(mapOf("number" to 1.0)))
    }

    @Test
    fun `pending reconciliation ignores timestamps but requires exact business and revision evidence`() {
        val envelope = MutationEnvelope.create(
            id = "pending-0001",
            aggregateType = "finops_daily_rollups",
            aggregateId = "rollup-1",
            expectedRevision = 4,
            authorityEpoch = AuthorityEpoch.SOURCE,
            occurredAt = Instant.parse("2026-08-23T05:00:00Z"),
            payload = mapOf("usdMicros" to 5L),
            operation = MutationOperation.UPSERT,
        )
        val sourceState = mapOf<String, Any?>(
            "aggregateType" to envelope.aggregateType,
            "aggregateId" to envelope.aggregateId,
            "revision" to 8L,
            "authorityEpoch" to 1L,
            "lastMutationId" to "later-mutation",
            "updatedAt" to "source-time",
        )
        val targetState = sourceState + ("updatedAt" to "target-time")
        val receipt = mapOf<String, Any?>(
            "committedEpoch" to 1L,
            "newRevision" to 5L,
            "firestoreCommitTime" to "2026-08-23T05:00:01Z",
            "mirrorState" to "PENDING",
        )

        assertEquals(
            5L,
            verifiedPendingTargetReceipt(
                envelope,
                mapOf("usdMicros" to 8L),
                mapOf("usdMicros" to 8L),
                sourceState,
                targetState,
                receipt,
            )?.newRevision,
        )
        assertEquals(
            null,
            verifiedPendingTargetReceipt(
                envelope,
                mapOf("usdMicros" to 8L),
                mapOf("usdMicros" to 7L),
                sourceState,
                targetState,
                receipt,
            ),
        )
        assertEquals(
            null,
            verifiedPendingTargetReceipt(
                envelope,
                mapOf("usdMicros" to 8L),
                mapOf("usdMicros" to 8L),
                sourceState,
                targetState + ("revision" to 7L),
                receipt,
            ),
        )
    }

    @Test
    fun `replication health enforces pending p99 truncation and future timestamps`() {
        val now = Instant.parse("2026-08-25T09:00:00Z")
        fun envelope(id: String, occurredAt: Instant) = MutationEnvelope.create(
            id = id,
            aggregateType = "finops_daily_rollups",
            aggregateId = id,
            expectedRevision = 0,
            authorityEpoch = AuthorityEpoch.SOURCE,
            occurredAt = occurredAt,
            payload = mapOf("usdMicros" to 1L),
            operation = MutationOperation.UPSERT,
        )

        assertTrue(firestoreReplicationHealth(emptyList(), now, truncated = false).ready)
        assertTrue(
            firestoreReplicationHealth(
                listOf(envelope("recent", now.minusSeconds(29))),
                now,
                truncated = false,
            ).ready,
        )
        assertFalse(
            firestoreReplicationHealth(
                listOf(envelope("stale", now.minusSeconds(31))),
                now,
                truncated = false,
            ).ready,
        )
        assertFalse(firestoreReplicationHealth(emptyList(), now, truncated = true).ready)
        assertFalse(
            firestoreReplicationHealth(
                listOf(envelope("future", now.plusSeconds(1))),
                now,
                truncated = false,
            ).ready,
        )
    }

    @Test
    fun `collection hash changes for document ids additions and values`() {
        val original = linkedMapOf("a" to document("a", mapOf("value" to "one")))
        val same = linkedMapOf("a" to document("a", mapOf("value" to "one")))
        val changed = linkedMapOf("a" to document("a", mapOf("value" to "two")))
        val added = linkedMapOf(
            "a" to document("a", mapOf("value" to "one")),
            "b" to document("b", mapOf("value" to "one")),
        )

        assertEquals(FirestoreCanonicalHash.collection(original), FirestoreCanonicalHash.collection(same))
        assertFalse(FirestoreCanonicalHash.collection(original) == FirestoreCanonicalHash.collection(changed))
        assertFalse(FirestoreCanonicalHash.collection(original) == FirestoreCanonicalHash.collection(added))
    }

    @Test
    fun `collection contract and parity hashes are deterministic`() {
        val parity = FirestoreBackfillParity(
            listOf(
                FirestoreBackfillCollectionParity("b", 2, 2, 0, 0, 0, "b".repeat(64), "b".repeat(64)),
                FirestoreBackfillCollectionParity("a", 1, 1, 0, 0, 0, "a".repeat(64), "a".repeat(64)),
            ),
        )
        val reordered = FirestoreBackfillParity(parity.collections.reversed())

        assertEquals(FirestoreCanonicalHash.parity(parity), FirestoreCanonicalHash.parity(reordered))
        assertEquals(64, FirestoreCanonicalHash.collectionContract().length)
    }

    @Test
    fun `operator access token stays ephemeral and receives a bounded expiry`() {
        val now = Instant.parse("2026-08-25T08:00:00Z")
        val token = "ya29.${"a".repeat(80)}"

        val credentials = PortfolioFirestoreDevBackfillCli.credentials(token, now)

        assertEquals(token, credentials.accessToken.tokenValue)
        assertEquals(now.plusSeconds(50 * 60L), credentials.accessToken.expirationTime.toInstant())
    }

    @Test
    fun `operator access token rejects blank short and whitespace-bearing values`() {
        assertFailsWith<IllegalArgumentException> {
            PortfolioFirestoreDevBackfillCli.credentials("short", Instant.EPOCH)
        }
        assertFailsWith<IllegalArgumentException> {
            PortfolioFirestoreDevBackfillCli.credentials("ya29.${"a".repeat(40)}\nforged", Instant.EPOCH)
        }
    }

    private fun document(id: String, data: Map<String, Any?>) = FirestoreBackfillDocument(id, data)
}
