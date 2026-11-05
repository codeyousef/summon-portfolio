package code.yousef.portfolio.finops

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreDocument
import code.yousef.firestore.PortfolioFirestoreStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirestoreFinOpsRepositoryTest {
    @Test
    fun `immutable Firestore entries ignore retry timestamp but compare every source field`() {
        val repository = FirestoreFinOpsRepository(MapPortfolioFirestoreStore())
        val original = entry(recordedAt = 1_000L)

        assertEquals(FinOpsWriteResult.Inserted, repository.putEntry(original))
        assertEquals(FinOpsWriteResult.Duplicate, repository.putEntry(original.copy(recordedAt = 2_000L)))
        assertEquals(
            FinOpsWriteResult.Conflict,
            repository.putEntry(original.copy(usdMicros = 2_000_000L, recordedAt = 3_000L)),
        )
    }

    @Test
    fun `immutable Firestore budgets cannot change fields behind the same source hash`() {
        val repository = FirestoreFinOpsRepository(MapPortfolioFirestoreStore())
        val original = FinOpsBudget(
            id = "budget:immutable",
            scope = FinOpsBudgetScope.COST_CLASS,
            scopeValue = "cloud_platform",
            amountUsdMicros = 100_000_000L,
            effectiveFrom = 1_800_000_000_000L,
            sourceHash = sha256Hex("budget:immutable"),
        )

        assertEquals(FinOpsWriteResult.Inserted, repository.putBudget(original))
        assertEquals(FinOpsWriteResult.Duplicate, repository.putBudget(original))
        assertEquals(
            FinOpsWriteResult.Conflict,
            repository.putBudget(original.copy(amountUsdMicros = 200_000_000L)),
        )
    }

    @Test
    fun `failed import run round trips only its redacted failure code`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store)
        val run = FinOpsImportRun(
            id = "import:gcp:2027-01-03:authorization",
            source = "gcp",
            fromDate = "2027-01-01",
            toDateExclusive = "2027-02-01",
            sourceRecords = 0,
            entries = 0,
            status = "failed",
            completedAt = 1_800_000_000_000L,
            sourceHash = sha256Hex("gcp-authorization-failure"),
            failureCode = "bigquery_authorization",
        )

        assertEquals(FinOpsWriteResult.Inserted, repository.putImportRun(run))
        assertEquals(listOf(run), repository.listImportRuns())
        assertEquals(
            "bigquery_authorization",
            store.get(PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS, run.id)?.data?.get("failureCode"),
        )
    }

    @Test
    fun `user rollups use the v2 collection and mutation namespace`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store)
        val source = entry(recordedAt = 1_000L)
        val allocation = CostAllocation(
            id = "allocation:v2-contract",
            entryId = source.id,
            project = "samurai",
            userId = "user-1",
            provider = "workers-ai",
            modelId = "model-1",
            runId = "run-42",
            allocatedUsdMicros = source.usdMicros,
            method = AllocationMethod.METERED,
        )

        repository.ensureDailyRollups(source, listOf(allocation))

        assertTrue(store.accumulations.any { accumulation ->
            accumulation.collection == PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS_V2 &&
                accumulation.mutationId == "finops-user-rollup-v2:${allocation.id}" &&
                accumulation.dimensions["reconciliationKey"] == source.reconciliationKey
        })
        assertTrue(store.accumulations.none { accumulation ->
            accumulation.collection == PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS
        })
        val index = store.list(PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX).single()
        assertEquals("user-1", index.data["userId"])
        assertEquals(
            listOf("user-1"),
            repository.listSamuraiUserIdsPage(
                FinOpsQuery(source.incurredAt - 1, source.incurredAt + 1),
            ).userIds,
        )

        repository.ensureDailyRollups(source, listOf(allocation))
        assertEquals(1, store.list(PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX).size)
    }

    @Test
    fun `global sharded rollups use versioned collections ids and mutation namespaces only after readiness`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store, shardedRollupWritesEnabled = true)
        val source = entry(recordedAt = 1_000L)
        val expectedShard = finOpsRollupShard(source.id)

        repository.ensureDailyRollups(source, emptyList())
        repository.ensureUnallocatedRollup(source, emptyList())

        val daily = store.accumulations.filter {
            it.collection == PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS_V2
        }
        assertEquals(2, daily.size)
        assertTrue(daily.all { it.documentId.endsWith(":s$expectedShard") })
        assertTrue(daily.all { it.dimensions["shard"] == expectedShard })
        assertEquals(
            setOf("finops-rollup-v2:${source.id}", "finops-unallocated-rollup-v2:${source.id}"),
            daily.mapTo(linkedSetOf(), MapPortfolioFirestoreStore.Accumulation::mutationId),
        )
        val reconciliation = store.accumulations.single {
            it.collection == PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS_V2
        }
        assertTrue(reconciliation.documentId.endsWith(":s$expectedShard"))
        assertEquals(expectedShard, reconciliation.dimensions["shard"])
        assertEquals("finops-reconciliation-v2:${source.id}", reconciliation.mutationId)
        assertTrue(store.accumulations.none {
            it.collection == PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS ||
                it.collection == PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS
        })
        val dayStart = source.incurredAt - (source.incurredAt % 86_400_000L)
        val query = FinOpsQuery(dayStart, dayStart + 86_400_000L)
        assertTrue(repository.listDailyRollups(query).isEmpty())
        assertEquals(
            1,
            FirestoreFinOpsRepository(
                store,
                shardedRollupWritesEnabled = true,
                shardedRollupsReady = true,
            ).listDailyRollups(query).size,
        )
    }

    @Test
    fun `audit replay preserves the first event and rejects a conflicting payload`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store)
        val first = FinOpsAuditEvent(
            id = "audit:mutation:stable",
            actor = "owner",
            action = "ingest",
            target = "manual:one",
            timestamp = 1_000L,
            detail = mapOf("source" to "manual"),
        )

        repository.appendAudit(first)
        repository.appendAudit(first.copy(timestamp = 2_000L))

        val stored = requireNotNull(store.get(PortfolioFirestoreCollections.FINOPS_AUDIT, first.id))
        assertEquals(1_000L, stored.data["timestamp"])
        assertFailsWith<IllegalArgumentException> {
            repository.appendAudit(first.copy(action = "different", timestamp = 3_000L))
        }
    }

    @Test
    fun `allocation drill-down uses a bounded time projection`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store, allocationEntryProjectionsReady = true)
        val source = entry(recordedAt = 1_000L)
        val allocation = CostAllocation(
            id = "allocation:bounded-projection",
            entryId = source.id,
            project = source.project,
            userId = "user-1",
            provider = "workers-ai",
            modelId = "model-1",
            runId = "run-42",
            allocatedUsdMicros = source.usdMicros,
            method = AllocationMethod.METERED,
        )

        repository.putEntry(source)
        repository.putAllocations(listOf(allocation))

        assertEquals(4, store.list(PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS).size)
        val page = repository.listEntries(
            FinOpsQuery(
                from = source.incurredAt - 1,
                toExclusive = source.incurredAt + 1,
                userId = "user-1",
                provider = "workers-ai",
                modelId = "model-1",
                runId = "run-42",
            ),
        )
        assertEquals(listOf(source.id), page.entries.map(FinOpsEntry::id))
        assertEquals(1, store.getManyCalls)
    }

    @Test
    fun `projection reconciliation proves exact counts and canonical hashes after repair`() {
        val store = MapPortfolioFirestoreStore()
        val repository = FirestoreFinOpsRepository(store)
        val source = entry(recordedAt = 1_000L)
        val allocation = CostAllocation(
            id = "allocation:projection-reconciliation",
            entryId = source.id,
            project = source.project,
            userId = "user-1",
            provider = "workers-ai",
            modelId = "model-1",
            allocatedUsdMicros = source.usdMicros,
            method = AllocationMethod.METERED,
        )
        repository.putEntry(source)
        repository.putAllocations(listOf(allocation))

        val projectionToRemove = store.list(PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS).first()
        store.delete(PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS, projectionToRemove.id)
        val incomplete = repository.reconcileAllocationEntryProjections()
        assertEquals(3L, incomplete.expectedCount)
        assertEquals(2L, incomplete.actualCount)
        assertEquals(1L, incomplete.missingCount)
        assertTrue(!incomplete.ready)

        repository.putAllocations(listOf(allocation))
        val repaired = repository.reconcileAllocationEntryProjections()
        assertEquals(3L, repaired.expectedCount)
        assertEquals(3L, repaired.actualCount)
        assertEquals(repaired.expectedHash, repaired.actualHash)
        assertTrue(repaired.ready)
    }

    @Test
    fun `receipt attachment projection is immutable and exactly addressable by upload id`() {
        val repository = FirestoreFinOpsRepository(MapPortfolioFirestoreStore())
        val uploadId = "a".repeat(32)
        val digest = "b".repeat(64)
        val attachment = FinOpsReceiptAttachment(
            uploadId = uploadId,
            entryId = "manual:receipt-entry",
            storageKey = "sha256/$digest/uploads/$uploadId/invoice.pdf",
            sha256 = digest,
        )

        assertEquals(FinOpsWriteResult.Inserted, repository.putReceiptAttachment(attachment))
        assertEquals(FinOpsWriteResult.Duplicate, repository.putReceiptAttachment(attachment))
        assertEquals(attachment, repository.getReceiptAttachment(uploadId))
        assertEquals(
            FinOpsWriteResult.Conflict,
            repository.putReceiptAttachment(attachment.copy(entryId = "manual:different-entry")),
        )
    }

    private fun entry(recordedAt: Long) = FinOpsEntry(
        id = "manual:firestore-immutable",
        source = "manual",
        sourceRecordId = "firestore-immutable",
        direction = FinOpsDirection.EXPENSE,
        amount = MoneyAmount("USD", 100, 2),
        usdMicros = 1_000_000L,
        incurredAt = 1_800_000_000_000L,
        invoiceMonth = "2027-01",
        project = "portfolio",
        environment = "dev",
        vendor = "vendor",
        service = "service",
        status = FinOpsStatus.FINALIZED,
        reconciliationKey = "vendor:2027-01",
        sourceHash = sha256Hex("firestore-immutable"),
        recordedAt = recordedAt,
    )
}

private class MapPortfolioFirestoreStore : PortfolioFirestoreStore {
    data class Accumulation(
        val collection: String,
        val documentId: String,
        val mutationId: String,
        val dimensions: Map<String, Any>,
    )

    private val documents = mutableMapOf<Pair<String, String>, PortfolioFirestoreDocument>()
    private val appliedMutations = mutableSetOf<String>()
    val accumulations = mutableListOf<Accumulation>()
    var getManyCalls: Int = 0
    override val writeMode: FirestoreWriteMode = FirestoreWriteMode.SOURCE

    override fun get(collection: String, documentId: String): PortfolioFirestoreDocument? =
        documents[collection to documentId]

    override fun getMany(collection: String, documentIds: Collection<String>): List<PortfolioFirestoreDocument> {
        getManyCalls += 1
        return documentIds.distinct().mapNotNull { documentId -> get(collection, documentId) }
    }

    override fun list(collection: String): List<PortfolioFirestoreDocument> = documents
        .filterKeys { it.first == collection }
        .values
        .toList()

    override fun whereEqualTo(collection: String, filters: Map<String, Any>): List<PortfolioFirestoreDocument> =
        list(collection).filter { document -> filters.all { (key, value) -> document.data[key] == value } }

    override fun whereLessThan(collection: String, field: String, value: Any): List<PortfolioFirestoreDocument> = emptyList()

    override fun upsert(collection: String, documentId: String, data: Map<String, Any?>) {
        documents[collection to documentId] = PortfolioFirestoreDocument(documentId, data)
    }

    override fun merge(collection: String, documentId: String, data: Map<String, Any?>) {
        val current = get(collection, documentId)?.data.orEmpty()
        upsert(collection, documentId, current + data)
    }

    override fun accumulate(
        collection: String,
        documentId: String,
        dimensions: Map<String, Any>,
        increments: Map<String, Long>,
        mutationId: String,
    ) {
        accumulations += Accumulation(collection, documentId, mutationId, dimensions)
        if (!appliedMutations.add(mutationId)) return
        val current = get(collection, documentId)?.data.orEmpty()
        val values = dimensions.toMutableMap<String, Any?>()
        increments.forEach { (field, increment) ->
            values[field] = Math.addExact((current[field] as? Number)?.toLong() ?: 0L, increment)
        }
        upsert(collection, documentId, current + values)
    }

    override fun delete(collection: String, documentId: String) {
        documents.remove(collection to documentId)
    }
}
