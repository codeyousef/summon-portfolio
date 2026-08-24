package code.yousef.firestore.migration

import code.yousef.firestore.PortfolioFirestoreCollections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortfolioFirestoreDevBackfillTest {
    @Test
    fun `migration collection contract covers content business data and revision metadata`() {
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
    }

    @Test
    fun `canonical hashes are deterministic and preserve Firestore numeric types`() {
        val first = mapOf("nested" to linkedMapOf("b" to 2L, "a" to listOf(true, "x")), "number" to 1L)
        val reordered = linkedMapOf("number" to 1L, "nested" to linkedMapOf("a" to listOf(true, "x"), "b" to 2L))

        assertEquals(FirestoreCanonicalHash.document(first), FirestoreCanonicalHash.document(reordered))
        assertFalse(FirestoreCanonicalHash.document(mapOf("number" to 1L)) == FirestoreCanonicalHash.document(mapOf("number" to 1.0)))
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

    private fun document(id: String, data: Map<String, Any?>) = FirestoreBackfillDocument(id, data)
}
