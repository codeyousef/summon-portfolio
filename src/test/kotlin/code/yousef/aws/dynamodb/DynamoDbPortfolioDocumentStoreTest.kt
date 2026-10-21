package code.yousef.aws.dynamodb

import code.yousef.aws.DynamoDbLocal
import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreDocument
import code.yousef.firestore.PortfolioFirestorePage
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.migration.IdempotencyConflictException
import com.google.cloud.Timestamp
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDbPortfolioDocumentStoreTest {
    private lateinit var store: DynamoDbPortfolioDocumentStore

    @BeforeTest
    fun setUp() {
        val client = DynamoDbLocal.client()
        store = DynamoDbPortfolioDocumentStore(client, DynamoDbLocal.createDocumentsTable(client))
    }

    @Test
    fun `documents round trip through upsert get getMany and list`() {
        val data = mapOf(
            "title" to "Hello",
            "order" to 3,
            "ratio" to 0.25,
            "whole" to 2.0,
            "tags" to listOf("a", "b"),
            "nested" to mapOf("en" to "x", "ar" to null),
            "at" to Timestamp.ofTimeSecondsAndNanos(10, 20),
        )
        store.upsert("projects", "p1", data)
        store.upsert("projects", "p2", mapOf("title" to "Two"))
        val expected = data + ("order" to 3L)
        assertEquals(expected, store.get("projects", "p1")?.data)
        assertEquals(listOf("p2", "p1"), store.getMany("projects", listOf("p2", "missing", "p1")).map { it.id })
        assertEquals(setOf("p1", "p2"), store.list("projects").map { it.id }.toSet())
        assertNull(store.get("projects", "missing"))
        store.delete("projects", "p1")
        assertNull(store.get("projects", "p1"))
    }

    @Test
    fun `range and string pages match the reference implementation for random queries`() {
        val reference = ReferenceStore()
        val random = Random(42)
        val collection = PortfolioFirestoreCollections.FINOPS_COST_ENTRIES
        repeat(120) { index ->
            val data = mapOf(
                // Many duplicates exercise the (value, id) cursor tie-break.
                "incurredAt" to random.nextLong(0, 12) * 1_000L,
                "project" to listOf("samurai", "seen", "portfolio").random(random),
                "vendor" to listOf("stripe", "gcp").random(random),
            )
            val id = "entry:${random.nextInt(10_000)}:$index"
            store.upsert(collection, id, data)
            reference.upsert(collection, id, data)
        }
        // One document without the order field must be excluded, as in Firestore.
        store.upsert(collection, "no-order", mapOf("project" to "seen"))
        reference.upsert(collection, "no-order", mapOf("project" to "seen"))
        repeat(40) {
            val from = random.nextLong(0, 6) * 1_000L
            val to = from + random.nextLong(1, 8) * 1_000L
            val filters = if (random.nextBoolean()) mapOf("project" to "seen") else emptyMap()
            val limit = random.nextInt(1, 9)
            val descending = random.nextBoolean()
            assertEquals(
                drainRange(reference, collection, from, to, filters, limit, descending),
                drainRange(store, collection, from, to, filters, limit, descending),
            )
        }

        val users = PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX
        listOf("user-b", "user-a", "user-ab", "user a", "z", "é").forEachIndexed { index, user ->
            store.upsert(users, "u$index", mapOf("userId" to user))
            reference.upsert(users, "u$index", mapOf("userId" to user))
        }
        listOf(false, true).forEach { descending ->
            assertEquals(drainString(reference, users, 2, descending), drainString(store, users, 2, descending))
        }
    }

    @Test
    fun `equality lookups use the lookup index and keep Firestore filter semantics`() {
        val collection = PortfolioFirestoreCollections.FINOPS_ALLOCATIONS
        store.upsert(collection, "a1", mapOf("entryId" to "e1", "userId" to "u1"))
        store.upsert(collection, "a2", mapOf("entryId" to "e1", "userId" to "u2"))
        store.upsert(collection, "a3", mapOf("entryId" to "e2", "userId" to "u1"))
        assertEquals(setOf("a1", "a2"), store.whereEqualTo(collection, mapOf("entryId" to "e1")).map { it.id }.toSet())
        assertEquals(listOf("a1"), store.whereEqualTo(collection, mapOf("entryId" to "e1", "userId" to "u1")).map { it.id })
        assertEquals(setOf("a1", "a3"), store.whereEqualTo(collection, mapOf("userId" to "u1")).map { it.id }.toSet())
        assertEquals(setOf("a1", "a2", "a3"), store.whereIn(collection, "entryId", listOf("e1", "e2")).map { it.id }.toSet())
        // Moving a document between lookup values updates the sparse index.
        store.upsert(collection, "a3", mapOf("entryId" to "e1", "userId" to "u1"))
        assertEquals(setOf("a1", "a2", "a3"), store.whereEqualTo(collection, mapOf("entryId" to "e1")).map { it.id }.toSet())

        val tokens = PortfolioFirestoreCollections.BUILDING_PASSWORD_RESET_TOKENS
        store.upsert(tokens, "t1", mapOf("username" to "x", "used" to false, "expireTime" to 10L))
        store.upsert(tokens, "t2", mapOf("username" to "x", "used" to true, "expireTime" to 30L))
        assertEquals(listOf("t1"), store.whereEqualTo(tokens, mapOf("username" to "x", "used" to false)).map { it.id })
        assertEquals(listOf("t1"), store.whereLessThan(tokens, "expireTime", 20L).map { it.id })
    }

    @Test
    fun `insertIfAbsent creates once and never replaces`() {
        assertTrue(store.insertIfAbsent("finops_cost_entries", "e1", mapOf("incurredAt" to 1L), "m1"))
        assertFalse(store.insertIfAbsent("finops_cost_entries", "e1", mapOf("incurredAt" to 1L), "m1"), "replay")
        assertFalse(store.insertIfAbsent("finops_cost_entries", "e1", mapOf("incurredAt" to 2L), "m2"))
        assertEquals(1L, store.get("finops_cost_entries", "e1")?.data?.get("incurredAt"))
        // The created document is visible to the order index immediately after creation.
        assertEquals(
            listOf("e1"),
            store.rangePage("finops_cost_entries", "incurredAt", 0, 10, emptyMap(), 5).documents.map { it.id },
        )
        // Concurrent creates of one id: exactly one wins.
        val pool = Executors.newFixedThreadPool(8)
        val wins = (1..16).map { index ->
            pool.submit<Boolean> { store.insertIfAbsent("finops_audit", "a1", mapOf("n" to index.toLong()), "m$index") }
        }.count { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, wins)
    }

    @Test
    fun `accumulate is exactly-once per mutation id under concurrency`() {
        val collection = PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS
        val dimensions = mapOf<String, Any>("dayStart" to 86_400_000L, "project" to "seen")
        val pool = Executors.newFixedThreadPool(8)
        val futures = (1..40).map { index ->
            pool.submit {
                store.accumulate(collection, "r1", dimensions, mapOf("usdMicros" to index.toLong(), "count" to 1L), "m$index")
                // Replays of the same mutation never double count.
                store.accumulate(collection, "r1", dimensions, mapOf("usdMicros" to index.toLong(), "count" to 1L), "m$index")
            }
        }
        futures.forEach { it.get(120, TimeUnit.SECONDS) }
        pool.shutdown()
        val data = requireNotNull(store.get(collection, "r1")).data
        assertEquals((1..40).sum().toLong(), data["usdMicros"])
        assertEquals(40L, data["count"])
        assertEquals("seen", data["project"])
        assertFailsWith<IdempotencyConflictException> {
            store.accumulate(collection, "r1", dimensions, mapOf("usdMicros" to 999L), "m1")
        }
        assertFailsWith<IllegalArgumentException> {
            store.accumulate(collection, "r1", dimensions + ("project" to "other"), mapOf("usdMicros" to 1L), "m-new")
        }
        // The rollup stays in the order index.
        assertEquals(listOf("r1"), store.rangePage(collection, "dayStart", 0, Long.MAX_VALUE, emptyMap(), 10).documents.map { it.id })
    }

    @Test
    fun `merge follows Firestore set-with-merge semantics and update is atomic`() {
        store.upsert("ai_curriculum", "progress", mapOf("a" to true, "nested" to mapOf("x" to 1L, "y" to 2L)))
        store.merge("ai_curriculum", "progress", mapOf("b" to false, "nested" to mapOf("y" to 3L)))
        assertEquals(
            mapOf("a" to true, "b" to false, "nested" to mapOf("x" to 1L, "y" to 3L)),
            store.get("ai_curriculum", "progress")?.data,
        )
        store.merge("ai_curriculum", "new-doc", mapOf("c" to true))
        assertEquals(mapOf("c" to true), store.get("ai_curriculum", "new-doc")?.data)

        val pool = Executors.newFixedThreadPool(6)
        (1..30).map { pool.submit { store.update("fifth_wall_telemetry", "s1") { current -> mapOf("n" to ((current?.get("n") as? Long) ?: 0L) + 1L) } } }
            .forEach { it.get(120, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(30L, store.get("fifth_wall_telemetry", "s1")?.data?.get("n"))
        store.update("fifth_wall_telemetry", "s1") { null }
        assertNull(store.get("fifth_wall_telemetry", "s1"))
        store.probe()
    }

    private fun drainRange(
        source: PortfolioFirestoreStore,
        collection: String,
        from: Long,
        to: Long,
        filters: Map<String, Any>,
        limit: Int,
        descending: Boolean,
    ): List<String> {
        val ids = mutableListOf<String>()
        var afterValue: Long? = null
        var afterId: String? = null
        do {
            val page: PortfolioFirestorePage = source.rangePage(collection, "incurredAt", from, to, filters, limit, afterValue, afterId, descending)
            ids += page.documents.map { it.id }
            val last = page.documents.lastOrNull()
            afterValue = (last?.data?.get("incurredAt") as? Number)?.toLong()
            afterId = last?.id
        } while (page.hasMore)
        return ids
    }

    private fun drainString(source: PortfolioFirestoreStore, collection: String, limit: Int, descending: Boolean): List<String> {
        val ids = mutableListOf<String>()
        var afterValue: String? = null
        var afterId: String? = null
        do {
            val page = source.stringPage(collection, "userId", limit, afterValue, afterId, descending)
            ids += page.documents.map { it.id }
            afterValue = page.documents.lastOrNull()?.data?.get("userId") as? String
            afterId = page.documents.lastOrNull()?.id
        } while (page.hasMore)
        return ids
    }
}

/** In-memory store that relies on the interface's reference query implementations. */
internal class ReferenceStore : PortfolioFirestoreStore {
    private val documents = linkedMapOf<Pair<String, String>, PortfolioFirestoreDocument>()
    override val writeMode: FirestoreWriteMode = FirestoreWriteMode.SOURCE
    override fun get(collection: String, documentId: String) = documents[collection to documentId]
    override fun list(collection: String) = documents.filterKeys { it.first == collection }.values.toList()
    override fun whereEqualTo(collection: String, filters: Map<String, Any>) =
        list(collection).filter { document -> filters.all { (key, value) -> document.data[key] == value } }
    override fun whereLessThan(collection: String, field: String, value: Any) = emptyList<PortfolioFirestoreDocument>()
    override fun upsert(collection: String, documentId: String, data: Map<String, Any?>) {
        documents[collection to documentId] = PortfolioFirestoreDocument(documentId, data)
    }
    override fun merge(collection: String, documentId: String, data: Map<String, Any?>) {
        upsert(collection, documentId, get(collection, documentId)?.data.orEmpty() + data)
    }
    override fun delete(collection: String, documentId: String) {
        documents.remove(collection to documentId)
    }
}
