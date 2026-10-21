package code.yousef.aws.migration

import code.yousef.aws.DynamoDbLocal
import code.yousef.aws.dynamodb.DynamoDbPortfolioDocumentStore
import code.yousef.firestore.migration.FirestoreBackfillDocument
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreToDynamoBackfillTest {
    private val collections = setOf("projects", "finops_cost_entries")

    private val sourceDocuments = mapOf(
        "projects" to mapOf(
            "p1" to mapOf<String, Any?>("title" to mapOf("en" to "One", "ar" to null), "order" to 1L, "tags" to listOf("a")),
            "p2" to mapOf<String, Any?>("title" to "Two", "ratio" to 0.5, "whole" to 3.0),
        ),
        "finops_cost_entries" to mapOf(
            "e1" to mapOf<String, Any?>(
                "incurredAt" to 1_792_000_000_000L,
                "at" to Timestamp.ofTimeSecondsAndNanos(1_792_000_000, 5),
                "blob" to Blob.fromBytes(byteArrayOf(1, 2)),
            ),
        ),
    )

    private fun reader(documents: Map<String, Map<String, Map<String, Any?>>>) = BackfillCollectionReader { collection ->
        documents[collection].orEmpty().toSortedMap().entries
            .associateTo(LinkedHashMap()) { (id, data) -> id to FirestoreBackfillDocument(id, data) }
    }

    @Test
    fun `dry run reports differences and execute converges to exact parity`() {
        val target = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>(
            "projects" to mutableMapOf("p1" to mapOf("title" to "stale")),
        )
        val backfill = FirestoreToDynamoBackfill(
            source = reader(sourceDocuments),
            target = reader(target),
            write = { collection, id, data -> target.getOrPut(collection) { mutableMapOf() }[id] = data },
            collections = collections,
        )
        val dryRun = backfill.inspect()
        assertFalse(dryRun.ready)
        assertEquals(1L, dryRun.parity.differingCount)
        assertEquals(2L, dryRun.parity.sourceOnlyCount)
        assertEquals(0L, dryRun.writtenDocuments)
        assertEquals(mapOf("title" to "stale"), target.getValue("projects").getValue("p1"), "dry run never writes")

        val executed = backfill.execute()
        assertTrue(executed.ready)
        assertEquals(3L, executed.writtenDocuments)
        assertEquals(executed.sourceParityHash, executed.targetParityHash)
    }

    @Test
    fun `execute refuses targets with documents absent from the source`() {
        val target = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>(
            "projects" to mutableMapOf("ghost" to mapOf("title" to "x")),
        )
        val backfill = FirestoreToDynamoBackfill(reader(sourceDocuments), reader(target), { _, _, _ -> error("no writes") }, collections)
        assertFailsWith<IllegalStateException> { backfill.execute() }
    }

    @Test
    fun `Firestore values survive the DynamoDB codec with identical parity hashes`() {
        val client = DynamoDbLocal.client()
        val store = DynamoDbPortfolioDocumentStore(client, DynamoDbLocal.createDocumentsTable(client))
        val backfill = FirestoreToDynamoBackfill(reader(sourceDocuments), DynamoBackfillReader(store), store::replaceForBackfill, collections)
        assertFalse(backfill.inspect().ready)
        val result = backfill.execute()
        assertTrue(result.ready)
        assertTrue(backfill.inspect().ready, "a second dry run proves parity")
        assertEquals(0L, backfill.execute().writtenDocuments)
    }
}
