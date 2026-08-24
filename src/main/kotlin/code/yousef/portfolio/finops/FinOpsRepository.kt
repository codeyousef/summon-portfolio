package code.yousef.portfolio.finops

import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreDocument
import code.yousef.firestore.PortfolioFirestoreStore
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

sealed interface FinOpsWriteResult {
    data object Inserted : FinOpsWriteResult
    data object Duplicate : FinOpsWriteResult
    data object Conflict : FinOpsWriteResult
}

interface FinOpsRepository {
    fun putEntry(entry: FinOpsEntry): FinOpsWriteResult
    fun getEntry(id: String): FinOpsEntry?
    fun listEntries(query: FinOpsQuery): FinOpsEntryPage
    fun putAllocations(allocations: List<CostAllocation>)
    fun listAllocations(entryIds: Set<String>): List<CostAllocation>
    fun reconcileAllocationEntryProjections(): FinOpsAllocationProjectionReconciliation
    fun ensureDailyRollups(entry: FinOpsEntry, allocations: List<CostAllocation>)
    fun ensureUnallocatedRollup(entry: FinOpsEntry, allocations: List<CostAllocation>)
    fun listDailyRollups(query: FinOpsQuery): List<FinOpsDailyRollup>
    fun listUserDailyRollups(query: FinOpsQuery): List<FinOpsUserDailyRollup>
    fun listSamuraiUserIdsPage(query: FinOpsQuery): SamuraiUserIdPage
    fun listReconciliationRollups(query: FinOpsQuery): List<FinOpsReconciliationRollup>
    fun putImportRun(run: FinOpsImportRun): FinOpsWriteResult
    fun listImportRuns(): List<FinOpsImportRun>
    fun putBudget(budget: FinOpsBudget): FinOpsWriteResult
    fun listBudgets(): List<FinOpsBudget>
    fun putFxRate(rate: FinOpsFxRate): FinOpsWriteResult
    fun listFxRates(sourceCurrency: String? = null): List<FinOpsFxRate>
    fun putRecurringExpense(expense: FinOpsRecurringExpense): FinOpsWriteResult
    fun listRecurringExpenses(): List<FinOpsRecurringExpense>
    fun putReceiptAttachment(attachment: FinOpsReceiptAttachment): FinOpsWriteResult
    fun getReceiptAttachment(uploadId: String): FinOpsReceiptAttachment?
    fun appendAudit(event: FinOpsAuditEvent)
}

data class FinOpsAuditEvent(
    val id: String,
    val actor: String,
    val action: String,
    val target: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detail: Map<String, String> = emptyMap(),
)

class InMemoryFinOpsRepository : FinOpsRepository {
    private val entries = ConcurrentHashMap<String, FinOpsEntry>()
    private val allocations = ConcurrentHashMap<String, CostAllocation>()
    private val audit = ConcurrentHashMap<String, FinOpsAuditEvent>()
    private val rollups = ConcurrentHashMap<String, FinOpsDailyRollup>()
    private val rolledUpEntries = ConcurrentHashMap.newKeySet<String>()
    private val unallocatedRolledUpEntries = ConcurrentHashMap.newKeySet<String>()
    private val userRollups = ConcurrentHashMap<String, FinOpsUserDailyRollup>()
    private val samuraiUserIds = ConcurrentHashMap.newKeySet<String>()
    private val rolledUpAllocations = ConcurrentHashMap.newKeySet<String>()
    private val reconciliationRollups = ConcurrentHashMap<String, FinOpsReconciliationRollup>()
    private val reconciledEntries = ConcurrentHashMap.newKeySet<String>()
    private val importRuns = ConcurrentHashMap<String, FinOpsImportRun>()
    private val budgets = ConcurrentHashMap<String, FinOpsBudget>()
    private val fxRates = ConcurrentHashMap<String, FinOpsFxRate>()
    private val recurringExpenses = ConcurrentHashMap<String, FinOpsRecurringExpense>()
    private val receiptAttachments = ConcurrentHashMap<String, FinOpsReceiptAttachment>()

    override fun putEntry(entry: FinOpsEntry): FinOpsWriteResult {
        val previous = entries.putIfAbsent(entry.id, entry) ?: return FinOpsWriteResult.Inserted
        return if (previous.sameImmutablePayload(entry)) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun getEntry(id: String): FinOpsEntry? = entries[id]

    override fun listEntries(query: FinOpsQuery): FinOpsEntryPage {
        val allocationEntryIds = if (query.hasAllocationFilter()) {
            allocations.values.filter(query::matches).mapTo(mutableSetOf(), CostAllocation::entryId)
        } else {
            null
        }
        return paginate(
            entries.values.asSequence()
                .filter(query::matches)
                .filter { allocationEntryIds == null || it.id in allocationEntryIds }
                .toList(),
            query,
        )
    }

    override fun putAllocations(allocations: List<CostAllocation>) {
        allocations.forEach { allocation ->
            val previous = this.allocations.putIfAbsent(allocation.id, allocation)
            require(previous == null || previous == allocation) { "allocation id conflicts with an existing record" }
        }
    }

    override fun listAllocations(entryIds: Set<String>): List<CostAllocation> = allocations.values
        .filter { it.entryId in entryIds }
        .sortedWith(compareBy(CostAllocation::entryId, CostAllocation::id))

    override fun reconcileAllocationEntryProjections(): FinOpsAllocationProjectionReconciliation {
        val expected = allocations.values.flatMap { allocation ->
            val entry = entries[allocation.entryId] ?: return@flatMap emptyList()
            allocation.dimensions().map { (dimensionType, dimensionValue) ->
                allocationEntryProjection(entry, dimensionType, dimensionValue)
            }
        }.associateBy(AllocationEntryProjection::id)
        val orphaned = allocations.values.count { !entries.containsKey(it.entryId) }.toLong()
        return reconcileAllocationEntryProjections(expected, expected, orphanedAllocationCount = orphaned)
    }

    override fun ensureDailyRollups(entry: FinOpsEntry, allocations: List<CostAllocation>) {
        if (rolledUpEntries.add(entry.id)) {
            val delta = entry.toDailyRollup(allocations)
            rollups.compute(delta.id) { _, current -> current?.plus(delta) ?: delta }
        }
        allocations.filter { it.userId != null }.forEach { allocation ->
            if (rolledUpAllocations.add(allocation.id)) {
                val delta = entry.toUserDailyRollup(allocation)
                userRollups.compute(delta.id) { _, current -> current?.plus(delta) ?: delta }
            }
            samuraiUserIds += requireNotNull(allocation.userId)
        }
        if (reconciledEntries.add(entry.id)) {
            val delta = entry.toReconciliationRollup()
            reconciliationRollups.compute(delta.id) { _, current -> current?.plus(delta) ?: delta }
        }
    }

    override fun ensureUnallocatedRollup(entry: FinOpsEntry, allocations: List<CostAllocation>) {
        if (!unallocatedRolledUpEntries.add(entry.id)) return
        val delta = entry.unallocatedUsdMicros(allocations)
        val rollupId = entry.toDailyRollup(emptyList()).id
        rollups.compute(rollupId) { _, current ->
            requireNotNull(current) { "daily rollup must exist before unallocated repair" }
            current.copy(unallocatedUsdMicros = Math.addExact(current.unallocatedUsdMicros, delta))
        }
    }

    override fun listDailyRollups(query: FinOpsQuery): List<FinOpsDailyRollup> = rollups.values
        .filter(query::matches)
        .sortedWith(compareByDescending(FinOpsDailyRollup::dayStart).thenByDescending(FinOpsDailyRollup::id))

    override fun listUserDailyRollups(query: FinOpsQuery): List<FinOpsUserDailyRollup> = userRollups.values
        .filter(query::matches)
        .sortedWith(compareByDescending(FinOpsUserDailyRollup::dayStart).thenByDescending(FinOpsUserDailyRollup::id))

    override fun listSamuraiUserIdsPage(query: FinOpsQuery): SamuraiUserIdPage =
        paginateSamuraiUserIds(samuraiUserIds, query)

    override fun listReconciliationRollups(query: FinOpsQuery): List<FinOpsReconciliationRollup> = reconciliationRollups.values
        .filter(query::matches)
        .sortedWith(compareByDescending(FinOpsReconciliationRollup::dayStart).thenByDescending(FinOpsReconciliationRollup::id))

    override fun putImportRun(run: FinOpsImportRun): FinOpsWriteResult {
        val previous = importRuns.putIfAbsent(run.id, run) ?: return FinOpsWriteResult.Inserted
        return if (previous == run) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun listImportRuns(): List<FinOpsImportRun> = importRuns.values
        .sortedWith(compareByDescending(FinOpsImportRun::completedAt).thenByDescending(FinOpsImportRun::id))

    override fun putBudget(budget: FinOpsBudget): FinOpsWriteResult {
        val previous = budgets.putIfAbsent(budget.id, budget) ?: return FinOpsWriteResult.Inserted
        return if (previous == budget) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun listBudgets(): List<FinOpsBudget> = budgets.values
        .sortedWith(compareByDescending(FinOpsBudget::effectiveFrom).thenBy(FinOpsBudget::id))

    override fun putFxRate(rate: FinOpsFxRate): FinOpsWriteResult {
        val previous = fxRates.putIfAbsent(rate.id, rate) ?: return FinOpsWriteResult.Inserted
        return if (previous == rate) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun listFxRates(sourceCurrency: String?): List<FinOpsFxRate> = fxRates.values
        .filter { sourceCurrency == null || it.sourceCurrency == sourceCurrency }
        .sortedWith(compareByDescending(FinOpsFxRate::effectiveDate)
            .thenByDescending { it.provenance.ordinal }
            .thenBy(FinOpsFxRate::id))

    override fun putRecurringExpense(expense: FinOpsRecurringExpense): FinOpsWriteResult {
        val previous = recurringExpenses.putIfAbsent(expense.id, expense) ?: return FinOpsWriteResult.Inserted
        return if (previous == expense) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun listRecurringExpenses(): List<FinOpsRecurringExpense> = recurringExpenses.values
        .sortedWith(compareBy(FinOpsRecurringExpense::startDate).thenBy(FinOpsRecurringExpense::id))

    override fun putReceiptAttachment(attachment: FinOpsReceiptAttachment): FinOpsWriteResult {
        val previous = receiptAttachments.putIfAbsent(attachment.uploadId, attachment)
            ?: return FinOpsWriteResult.Inserted
        return if (previous == attachment) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun getReceiptAttachment(uploadId: String): FinOpsReceiptAttachment? = receiptAttachments[uploadId]

    override fun appendAudit(event: FinOpsAuditEvent) {
        val existing = audit.putIfAbsent(event.id, event)
        require(existing == null || existing.sameAuditPayload(event)) {
            "audit event id conflicts with an existing payload"
        }
    }

    internal fun auditEvents(): List<FinOpsAuditEvent> = audit.values
        .sortedWith(compareBy(FinOpsAuditEvent::timestamp).thenBy(FinOpsAuditEvent::id))
}

class FirestoreFinOpsRepository(
    private val store: PortfolioFirestoreStore,
    private val allocationEntryProjectionsReady: Boolean = false,
    private val shardedRollupWritesEnabled: Boolean = false,
    private val shardedRollupsReady: Boolean = false,
) : FinOpsRepository {
    init {
        require(!shardedRollupsReady || shardedRollupWritesEnabled) {
            "sharded rollup reads require sharded rollup writes"
        }
    }
    override fun putEntry(entry: FinOpsEntry): FinOpsWriteResult {
        val existing = store.get(PortfolioFirestoreCollections.FINOPS_COST_ENTRIES, entry.id)
        if (existing != null) {
            return if (existing.toEntry().sameImmutablePayload(entry)) {
                FinOpsWriteResult.Duplicate
            } else {
                FinOpsWriteResult.Conflict
            }
        }
        val inserted = store.insertIfAbsent(
            PortfolioFirestoreCollections.FINOPS_COST_ENTRIES,
            entry.id,
            entry.toDocument(),
            mutationId = "finops-entry:${entry.id}:${entry.sourceHash.take(32)}",
        )
        if (inserted) return FinOpsWriteResult.Inserted
        val raced = store.get(PortfolioFirestoreCollections.FINOPS_COST_ENTRIES, entry.id)
            ?: error("immutable FinOps entry disappeared after a concurrent create")
        return if (raced.toEntry().sameImmutablePayload(entry)) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun getEntry(id: String): FinOpsEntry? = store
        .get(PortfolioFirestoreCollections.FINOPS_COST_ENTRIES, id)
        ?.toEntry()

    override fun listEntries(query: FinOpsQuery): FinOpsEntryPage {
        if (query.hasAllocationFilter()) {
            if (!allocationEntryProjectionsReady) {
                val primaryFilter = query.primaryAllocationDimension()
                val entryIds = store.whereEqualTo(
                    PortfolioFirestoreCollections.FINOPS_ALLOCATIONS,
                    mapOf(primaryFilter),
                ).map(PortfolioFirestoreDocument::toAllocation)
                    .filter(query::matches)
                    .mapTo(mutableSetOf(), CostAllocation::entryId)
                return paginate(entryIds.mapNotNull(::getEntry).filter(query::matches), query)
            }
            val (dimensionType, dimensionValue) = query.primaryAllocationDimension()
            val cursor = query.cursor?.let { decodeCursor(it, query, ALLOCATION_CURSOR_NAMESPACE) }
            val page = store.rangePage(
                collection = PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS,
                rangeField = "incurredAt",
                fromInclusive = query.from,
                toExclusive = query.toExclusive,
                equalityFilters = mapOf("dimensionType" to dimensionType, "dimensionValue" to dimensionValue),
                limit = query.limit,
                afterValue = cursor?.first,
                afterDocumentId = cursor?.second,
                descending = query.order == FinOpsEntryOrder.NEWEST,
            )
            val entryIds = page.documents.mapTo(linkedSetOf()) { it.data.string("entryId") }
            val matchingEntryIds = listAllocations(entryIds)
                .filter(query::matches)
                .mapTo(mutableSetOf(), CostAllocation::entryId)
            val entriesById = store.getMany(PortfolioFirestoreCollections.FINOPS_COST_ENTRIES, entryIds)
                .map(PortfolioFirestoreDocument::toEntry)
                .associateBy(FinOpsEntry::id)
            return FinOpsEntryPage(
                entries = page.documents.mapNotNull { projection ->
                    entriesById[projection.data.string("entryId")]?.takeIf { it.id in matchingEntryIds && query.matches(it) }
                },
                nextCursor = page.documents.lastOrNull()?.takeIf { page.hasMore }?.let { projection ->
                    encodeCursor(query, ALLOCATION_CURSOR_NAMESPACE, projection.data.long("incurredAt"), projection.id)
                },
            )
        }
        val cursor = query.cursor?.let { decodeCursor(it, query, ENTRY_CURSOR_NAMESPACE) }
        val filters = buildMap<String, Any> {
            query.project?.let { put("project", it) }
            query.environment?.let { put("environment", it) }
            query.vendor?.let { put("vendor", it) }
            query.service?.let { put("service", it) }
            query.status?.let { put("status", it.name) }
        }
        val page = store.rangePage(
            collection = PortfolioFirestoreCollections.FINOPS_COST_ENTRIES,
            rangeField = "incurredAt",
            fromInclusive = query.from,
            toExclusive = query.toExclusive,
            equalityFilters = filters,
            limit = query.limit,
            afterValue = cursor?.first,
            afterDocumentId = cursor?.second,
            descending = query.order == FinOpsEntryOrder.NEWEST,
        )
        val entries = page.documents.map(PortfolioFirestoreDocument::toEntry)
        return FinOpsEntryPage(
            entries = entries,
            nextCursor = entries.lastOrNull()?.takeIf { page.hasMore }?.let { entry ->
                encodeCursor(query, ENTRY_CURSOR_NAMESPACE, entry.incurredAt, entry.id)
            },
        )
    }

    override fun putAllocations(allocations: List<CostAllocation>) {
        allocations.forEach { allocation ->
            val existing = store.get(PortfolioFirestoreCollections.FINOPS_ALLOCATIONS, allocation.id)
            require(existing == null || existing.toAllocation() == allocation) {
                "allocation id conflicts with an existing record"
            }
            if (existing == null) {
                val inserted = store.insertIfAbsent(
                    PortfolioFirestoreCollections.FINOPS_ALLOCATIONS,
                    allocation.id,
                    allocation.toDocument(),
                    mutationId = "finops-allocation:${allocation.id}:${sha256Hex(allocation.toString()).take(32)}",
                )
                if (!inserted) {
                    val raced = store.get(PortfolioFirestoreCollections.FINOPS_ALLOCATIONS, allocation.id)
                    require(raced?.toAllocation() == allocation) {
                        "allocation id conflicts with an existing record"
                    }
                }
            }
            val entry = requireNotNull(getEntry(allocation.entryId)) {
                "allocation entry does not exist"
            }
            allocation.dimensions().forEach { (dimensionType, dimensionValue) ->
                val expectedProjection = allocationEntryProjection(entry, dimensionType, dimensionValue)
                val projectionId = expectedProjection.id
                val projection = mapOf(
                    "dimensionType" to expectedProjection.dimensionType,
                    "dimensionValue" to expectedProjection.dimensionValue,
                    "entryId" to expectedProjection.entryId,
                    "incurredAt" to expectedProjection.incurredAt,
                )
                val inserted = store.insertIfAbsent(
                    PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS,
                    projectionId,
                    projection,
                    mutationId = "finops-allocation-entry:$projectionId",
                )
                if (!inserted) {
                    val existingProjection = store.get(
                        PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS,
                        projectionId,
                    )
                    require(existingProjection != null && projection.all { (key, value) -> existingProjection.data[key] == value }) {
                        "allocation entry projection conflicts with an existing record"
                    }
                }
            }
        }
    }

    override fun listAllocations(entryIds: Set<String>): List<CostAllocation> {
        if (entryIds.isEmpty()) return emptyList()
        return entryIds.chunked(30).flatMap { chunk ->
            store.whereIn(
                PortfolioFirestoreCollections.FINOPS_ALLOCATIONS,
                "entryId",
                chunk,
            )
        }
            .map(PortfolioFirestoreDocument::toAllocation)
            .filter { it.entryId in entryIds }
            .sortedWith(compareBy(CostAllocation::entryId, CostAllocation::id))
    }

    override fun reconcileAllocationEntryProjections(): FinOpsAllocationProjectionReconciliation {
        val allocations = store.list(PortfolioFirestoreCollections.FINOPS_ALLOCATIONS)
            .map(PortfolioFirestoreDocument::toAllocation)
        val entries = allocations.map(CostAllocation::entryId).distinct().chunked(100)
            .flatMap { entryIds -> store.getMany(PortfolioFirestoreCollections.FINOPS_COST_ENTRIES, entryIds) }
            .map(PortfolioFirestoreDocument::toEntry)
            .associateBy(FinOpsEntry::id)
        val orphaned = allocations.count { !entries.containsKey(it.entryId) }.toLong()
        val expected = allocations.flatMap { allocation ->
            val entry = entries[allocation.entryId] ?: return@flatMap emptyList()
            allocation.dimensions().map { (dimensionType, dimensionValue) ->
                allocationEntryProjection(entry, dimensionType, dimensionValue)
            }
        }.associateBy(AllocationEntryProjection::id)
        val actualDocuments = store.list(PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS)
        val actual = actualDocuments.mapNotNull(PortfolioFirestoreDocument::toAllocationEntryProjectionOrNull)
            .associateBy(AllocationEntryProjection::id)
        return reconcileAllocationEntryProjections(
            expected = expected,
            actual = actual,
            invalidCount = (actualDocuments.size - actual.size).toLong(),
            orphanedAllocationCount = orphaned,
        )
    }

    private fun dailyRollupWriteCollection(): String = if (shardedRollupWritesEnabled) {
        PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS_V2
    } else {
        PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS
    }

    private fun dailyRollupReadCollection(): String = if (shardedRollupsReady) {
        PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS_V2
    } else {
        PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS
    }

    private fun reconciliationRollupWriteCollection(): String = if (shardedRollupWritesEnabled) {
        PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS_V2
    } else {
        PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS
    }

    private fun reconciliationRollupReadCollection(): String = if (shardedRollupsReady) {
        PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS_V2
    } else {
        PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS
    }

    private fun versionedDailyRollup(rollup: FinOpsDailyRollup, entryId: String): FinOpsDailyRollup =
        if (shardedRollupWritesEnabled) rollup.copy(id = "${rollup.id}:s${finOpsRollupShard(entryId)}") else rollup

    private fun versionedReconciliationRollup(
        rollup: FinOpsReconciliationRollup,
        entryId: String,
    ): FinOpsReconciliationRollup = if (shardedRollupWritesEnabled) {
        rollup.copy(id = "${rollup.id}:s${finOpsRollupShard(entryId)}")
    } else {
        rollup
    }

    private fun versionedRollupDimensions(entryId: String, dimensions: Map<String, Any>): Map<String, Any> =
        if (shardedRollupWritesEnabled) dimensions + ("shard" to finOpsRollupShard(entryId)) else dimensions

    override fun ensureDailyRollups(entry: FinOpsEntry, allocations: List<CostAllocation>) {
        val rollup = versionedDailyRollup(entry.toDailyRollup(allocations), entry.id)
        store.accumulate(
            collection = dailyRollupWriteCollection(),
            documentId = rollup.id,
            dimensions = versionedRollupDimensions(entry.id, mapOf(
                "dayStart" to rollup.dayStart,
                "source" to rollup.source,
                "project" to rollup.project,
                "environment" to rollup.environment,
                "vendor" to rollup.vendor,
                "service" to rollup.service,
                "costClass" to rollup.costClass,
                "status" to rollup.status.name,
            )),
            increments = rollup.incrementFields(),
            mutationId = if (shardedRollupWritesEnabled) "finops-rollup-v2:${entry.id}" else "finops-rollup:${entry.id}",
        )
        allocations.filter { it.userId != null }.forEach { allocation ->
            val userRollup = entry.toUserDailyRollup(allocation)
            store.accumulate(
                // v2 includes reconciliation identity in its dimensions and
                // document ID. Keep v1 immutable for audit/backfill rather
                // than reusing its deterministic mutation receipts.
                collection = PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS_V2,
                documentId = userRollup.id,
                dimensions = mapOf(
                    "dayStart" to userRollup.dayStart,
                    "reconciliationKey" to userRollup.reconciliationKey,
                    "environment" to userRollup.environment,
                    "vendor" to userRollup.vendor,
                    "service" to userRollup.service,
                    "userId" to userRollup.userId,
                    "provider" to userRollup.provider,
                    "modelId" to userRollup.modelId,
                    "method" to userRollup.method.name,
                ),
                increments = userRollup.incrementFields(),
                mutationId = "finops-user-rollup-v2:${allocation.id}",
            )
            ensureSamuraiUserIndex(userRollup.userId)
        }
        val reconciliation = versionedReconciliationRollup(entry.toReconciliationRollup(), entry.id)
        store.accumulate(
            collection = reconciliationRollupWriteCollection(),
            documentId = reconciliation.id,
            dimensions = versionedRollupDimensions(entry.id, mapOf(
                "dayStart" to reconciliation.dayStart,
                "reconciliationKey" to reconciliation.reconciliationKey,
                "project" to reconciliation.project,
                "environment" to reconciliation.environment,
                "vendor" to reconciliation.vendor,
                "service" to reconciliation.service,
                "costClass" to reconciliation.costClass,
            )),
            increments = mapOf(
                "accruedUsdMicros" to reconciliation.accruedUsdMicros,
                "estimatedUsdMicros" to reconciliation.estimatedUsdMicros,
                "finalizedUsdMicros" to reconciliation.finalizedUsdMicros,
                "finalizedEntryCount" to reconciliation.finalizedEntryCount,
            ),
            mutationId = if (shardedRollupWritesEnabled) {
                "finops-reconciliation-v2:${entry.id}"
            } else {
                "finops-reconciliation:${entry.id}"
            },
        )
    }

    override fun ensureUnallocatedRollup(entry: FinOpsEntry, allocations: List<CostAllocation>) {
        val rollup = versionedDailyRollup(entry.toDailyRollup(emptyList()), entry.id)
        store.accumulate(
            collection = dailyRollupWriteCollection(),
            documentId = rollup.id,
            dimensions = versionedRollupDimensions(entry.id, mapOf(
                "dayStart" to rollup.dayStart,
                "source" to rollup.source,
                "project" to rollup.project,
                "environment" to rollup.environment,
                "vendor" to rollup.vendor,
                "service" to rollup.service,
                "costClass" to rollup.costClass,
                "status" to rollup.status.name,
            )),
            increments = mapOf("unallocatedUsdMicros" to entry.unallocatedUsdMicros(allocations)),
            mutationId = if (shardedRollupWritesEnabled) {
                "finops-unallocated-rollup-v2:${entry.id}"
            } else {
                "finops-unallocated-rollup-v1:${entry.id}"
            },
        )
    }

    override fun listDailyRollups(query: FinOpsQuery): List<FinOpsDailyRollup> {
        val result = mutableListOf<FinOpsDailyRollup>()
        var afterValue: Long? = null
        var afterId: String? = null
        do {
            val page = store.rangePage(
                collection = dailyRollupReadCollection(),
                rangeField = "dayStart",
                fromInclusive = query.from,
                toExclusive = query.toExclusive,
                // Daily rollups are a bounded dataset. Filtering them after the
                // range query avoids an exponential set of composite indexes for
                // every dashboard filter combination.
                equalityFilters = emptyMap(),
                limit = 500,
                afterValue = afterValue,
                afterDocumentId = afterId,
            )
            result += page.documents.map(PortfolioFirestoreDocument::toDailyRollup).filter(query::matches)
            val last = page.documents.lastOrNull()
            afterValue = last?.data?.get("dayStart")?.let { (it as Number).toLong() }
            afterId = last?.id
        } while (page.hasMore && afterValue != null && afterId != null)
        return result
    }

    override fun listUserDailyRollups(query: FinOpsQuery): List<FinOpsUserDailyRollup> {
        if (query.project != null && !query.project.equals("samurai", true)) return emptyList()
        val result = mutableListOf<FinOpsUserDailyRollup>()
        var afterValue: Long? = null
        var afterId: String? = null
        do {
            val page = store.rangePage(
                collection = PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS_V2,
                rangeField = "dayStart",
                fromInclusive = query.from,
                toExclusive = query.toExclusive,
                equalityFilters = query.userId?.let { mapOf("userId" to it) }.orEmpty(),
                limit = 500,
                afterValue = afterValue,
                afterDocumentId = afterId,
            )
            result += page.documents.map(PortfolioFirestoreDocument::toUserDailyRollup).filter(query::matches)
            val last = page.documents.lastOrNull()
            afterValue = last?.data?.get("dayStart")?.let { (it as Number).toLong() }
            afterId = last?.id
        } while (page.hasMore && afterValue != null && afterId != null)
        return result
    }

    override fun listSamuraiUserIdsPage(query: FinOpsQuery): SamuraiUserIdPage {
        query.userId?.let { return SamuraiUserIdPage(listOf(it)) }
        val decoded = query.samuraiUserCursor?.let { decodeSamuraiUserCursor(it, query) }
        val page = store.stringPage(
            collection = PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX,
            orderField = "userId",
            limit = query.samuraiUserLimit,
            afterValue = decoded?.first,
            afterDocumentId = decoded?.second,
            descending = query.samuraiUserOrder == SamuraiUserOrder.DESCENDING,
        )
        val users = page.documents.map { document ->
            document.data.string("userId").also(::requireSamuraiUserIndexId)
        }
        return SamuraiUserIdPage(
            userIds = users,
            nextCursor = page.documents.lastOrNull()?.takeIf { page.hasMore }?.let { document ->
                encodeSamuraiUserCursor(query, document.data.string("userId"), document.id)
            },
        )
    }

    private fun ensureSamuraiUserIndex(userId: String) {
        requireSamuraiUserIndexId(userId)
        val documentId = samuraiUserIndexDocumentId(userId)
        val data = mapOf("userId" to userId)
        val inserted = store.insertIfAbsent(
            PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX,
            documentId,
            data,
            mutationId = "finops-samurai-user-index:$documentId",
        )
        if (!inserted) {
            val existing = store.get(PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX, documentId)
            require(existing?.data?.get("userId") == userId) {
                "Samurai user index document conflicts with an existing principal"
            }
        }
    }

    override fun listReconciliationRollups(query: FinOpsQuery): List<FinOpsReconciliationRollup> {
        val result = mutableListOf<FinOpsReconciliationRollup>()
        var afterValue: Long? = null
        var afterId: String? = null
        do {
            val page = store.rangePage(
                collection = reconciliationRollupReadCollection(),
                rangeField = "dayStart",
                fromInclusive = query.from,
                toExclusive = query.toExclusive,
                equalityFilters = emptyMap(),
                limit = 500,
                afterValue = afterValue,
                afterDocumentId = afterId,
            )
            result += page.documents.map(PortfolioFirestoreDocument::toReconciliationRollup).filter(query::matches)
            val last = page.documents.lastOrNull()
            afterValue = last?.data?.get("dayStart")?.let { (it as Number).toLong() }
            afterId = last?.id
        } while (page.hasMore && afterValue != null && afterId != null)
        return result
    }

    override fun putImportRun(run: FinOpsImportRun): FinOpsWriteResult {
        val existing = store.get(PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS, run.id)
        if (existing != null) {
            return if (existing.toImportRun() == run) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
        }
        val data = mutableMapOf<String, Any>(
            "source" to run.source,
            "fromDate" to run.fromDate,
            "toDateExclusive" to run.toDateExclusive,
            "sourceRecords" to run.sourceRecords,
            "entries" to run.entries,
            "status" to run.status,
            "completedAt" to run.completedAt,
            "sourceHash" to run.sourceHash,
        )
        run.failureCode?.let { data["failureCode"] = it }
        val inserted = store.insertIfAbsent(
            PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS,
            run.id,
            data,
            mutationId = "finops-import-run:${run.id}:${run.sourceHash.take(32)}",
        )
        if (inserted) return FinOpsWriteResult.Inserted
        val raced = store.get(PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS, run.id)
            ?: error("FinOps import run disappeared after a concurrent create")
        return if (raced.toImportRun() == run) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun listImportRuns(): List<FinOpsImportRun> {
        val result = mutableListOf<FinOpsImportRun>()
        var afterValue: Long? = null
        var afterId: String? = null
        do {
            val page = store.rangePage(
                collection = PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS,
                rangeField = "completedAt",
                fromInclusive = 0L,
                toExclusive = Long.MAX_VALUE,
                equalityFilters = emptyMap(),
                limit = 500,
                afterValue = afterValue,
                afterDocumentId = afterId,
            )
            result += page.documents.map(PortfolioFirestoreDocument::toImportRun)
            val last = page.documents.lastOrNull()
            afterValue = last?.data?.get("completedAt")?.let { (it as Number).toLong() }
            afterId = last?.id
        } while (page.hasMore && afterValue != null && afterId != null)
        return result
    }

    override fun putBudget(budget: FinOpsBudget): FinOpsWriteResult = putImmutableConfig(
        collection = PortfolioFirestoreCollections.FINOPS_BUDGETS,
        id = budget.id,
        sourceHash = budget.sourceHash,
        mutationPrefix = "finops-budget",
        document = budget.toDocument(),
        matches = { it.toBudget() == budget },
    )

    override fun listBudgets(): List<FinOpsBudget> = store.list(PortfolioFirestoreCollections.FINOPS_BUDGETS)
        .map(PortfolioFirestoreDocument::toBudget)
        .sortedWith(compareByDescending(FinOpsBudget::effectiveFrom).thenBy(FinOpsBudget::id))

    override fun putFxRate(rate: FinOpsFxRate): FinOpsWriteResult = putImmutableConfig(
        collection = PortfolioFirestoreCollections.FINOPS_FX_RATES,
        id = rate.id,
        sourceHash = rate.sourceHash,
        mutationPrefix = "finops-fx-rate",
        document = rate.toDocument(),
        matches = { it.toFxRate() == rate },
    )

    override fun listFxRates(sourceCurrency: String?): List<FinOpsFxRate> = store
        .list(PortfolioFirestoreCollections.FINOPS_FX_RATES)
        .map(PortfolioFirestoreDocument::toFxRate)
        .filter { sourceCurrency == null || it.sourceCurrency == sourceCurrency }
        .sortedWith(compareByDescending(FinOpsFxRate::effectiveDate)
            .thenByDescending { it.provenance.ordinal }
            .thenBy(FinOpsFxRate::id))

    override fun putRecurringExpense(expense: FinOpsRecurringExpense): FinOpsWriteResult = putImmutableConfig(
        collection = PortfolioFirestoreCollections.FINOPS_RECURRING_EXPENSES,
        id = expense.id,
        sourceHash = expense.sourceHash,
        mutationPrefix = "finops-recurring",
        document = expense.toDocument(),
        matches = { it.toRecurringExpense() == expense },
    )

    override fun listRecurringExpenses(): List<FinOpsRecurringExpense> = store
        .list(PortfolioFirestoreCollections.FINOPS_RECURRING_EXPENSES)
        .map(PortfolioFirestoreDocument::toRecurringExpense)
        .sortedWith(compareBy(FinOpsRecurringExpense::startDate).thenBy(FinOpsRecurringExpense::id))

    override fun putReceiptAttachment(attachment: FinOpsReceiptAttachment): FinOpsWriteResult {
        val existing = getReceiptAttachment(attachment.uploadId)
        if (existing != null) return if (existing == attachment) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
        val inserted = store.insertIfAbsent(
            PortfolioFirestoreCollections.FINOPS_RECEIPT_ATTACHMENTS,
            attachment.uploadId,
            mapOf(
                "entryId" to attachment.entryId,
                "storageKey" to attachment.storageKey,
                "sha256" to attachment.sha256,
            ),
            mutationId = "finops-receipt-attachment:${attachment.uploadId}",
        )
        if (inserted) return FinOpsWriteResult.Inserted
        val raced = getReceiptAttachment(attachment.uploadId)
            ?: error("receipt attachment disappeared after a concurrent create")
        return if (raced == attachment) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun getReceiptAttachment(uploadId: String): FinOpsReceiptAttachment? {
        require(uploadId.matches(Regex("^[a-f0-9]{32}$"))) { "invalid receipt upload id" }
        return store.get(PortfolioFirestoreCollections.FINOPS_RECEIPT_ATTACHMENTS, uploadId)?.let { document ->
            FinOpsReceiptAttachment(
                uploadId = document.id,
                entryId = document.data.string("entryId"),
                storageKey = document.data.string("storageKey"),
                sha256 = document.data.string("sha256"),
            )
        }
    }

    private fun putImmutableConfig(
        collection: String,
        id: String,
        sourceHash: String,
        mutationPrefix: String,
        document: Map<String, Any?>,
        matches: (PortfolioFirestoreDocument) -> Boolean,
    ): FinOpsWriteResult {
        val existing = store.get(collection, id)
        if (existing != null) {
            return if (matches(existing)) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
        }
        val inserted = store.insertIfAbsent(
            collection = collection,
            documentId = id,
            data = document,
            mutationId = "$mutationPrefix:$id:${sourceHash.take(32)}",
        )
        if (inserted) return FinOpsWriteResult.Inserted
        val raced = store.get(collection, id) ?: error("immutable FinOps configuration disappeared after a concurrent create")
        return if (matches(raced)) FinOpsWriteResult.Duplicate else FinOpsWriteResult.Conflict
    }

    override fun appendAudit(event: FinOpsAuditEvent) {
        val inserted = store.insertIfAbsent(
            PortfolioFirestoreCollections.FINOPS_AUDIT,
            event.id,
            mapOf(
                "actor" to event.actor,
                "action" to event.action,
                "target" to event.target,
                "timestamp" to event.timestamp,
                "detail" to event.detail,
            ),
            mutationId = "finops-audit:${event.id}",
        )
        if (!inserted) {
            val existing = requireNotNull(store.get(PortfolioFirestoreCollections.FINOPS_AUDIT, event.id)) {
                "audit event disappeared after a concurrent create"
            }
            require(existing.toAuditEvent().sameAuditPayload(event)) {
                "audit event id conflicts with an existing payload"
            }
        }
    }
}

private fun FinOpsAuditEvent.sameAuditPayload(other: FinOpsAuditEvent): Boolean =
    actor == other.actor && action == other.action && target == other.target && detail == other.detail

private fun PortfolioFirestoreDocument.toAuditEvent(): FinOpsAuditEvent = FinOpsAuditEvent(
    id = id,
    actor = data.string("actor"),
    action = data.string("action"),
    target = data.string("target"),
    timestamp = data.long("timestamp"),
    detail = (data["detail"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty(),
)

private fun paginate(entries: List<FinOpsEntry>, query: FinOpsQuery): FinOpsEntryPage {
    val newestFirst = query.order == FinOpsEntryOrder.NEWEST
    val ordered = entries.sortedWith(if (newestFirst) {
        compareByDescending(FinOpsEntry::incurredAt).thenByDescending(FinOpsEntry::id)
    } else {
        compareBy(FinOpsEntry::incurredAt).thenBy(FinOpsEntry::id)
    })
    val namespace = if (query.hasAllocationFilter()) ALLOCATION_CURSOR_NAMESPACE else ENTRY_CURSOR_NAMESPACE
    val decoded = query.cursor?.let { decodeCursor(it, query, namespace) }
    val afterCursor = decoded?.let { (timestamp, id) ->
        ordered.dropWhile {
            if (newestFirst) {
                it.incurredAt > timestamp || (it.incurredAt == timestamp && it.id >= id)
            } else {
                it.incurredAt < timestamp || (it.incurredAt == timestamp && it.id <= id)
            }
        }
    } ?: ordered
    val page = afterCursor.take(query.limit)
    return FinOpsEntryPage(
        entries = page,
        nextCursor = page.lastOrNull()?.takeIf { afterCursor.size > page.size }?.let { entry ->
            encodeCursor(query, namespace, entry.incurredAt, entry.id)
        },
    )
}

private fun paginateSamuraiUserIds(userIds: Collection<String>, query: FinOpsQuery): SamuraiUserIdPage {
    query.userId?.let { return SamuraiUserIdPage(listOf(it)) }
    val descending = query.samuraiUserOrder == SamuraiUserOrder.DESCENDING
    val ordered = userIds.onEach(::requireSamuraiUserIndexId).sorted().let { values ->
        if (descending) values.asReversed() else values
    }
    val decoded = query.samuraiUserCursor?.let { decodeSamuraiUserCursor(it, query) }
    val remaining = decoded?.let { (afterUserId, _) ->
        ordered.dropWhile { if (descending) it >= afterUserId else it <= afterUserId }
    } ?: ordered
    val users = remaining.take(query.samuraiUserLimit)
    return SamuraiUserIdPage(
        userIds = users,
        nextCursor = users.lastOrNull()?.takeIf { remaining.size > users.size }?.let { userId ->
            encodeSamuraiUserCursor(query, userId, samuraiUserIndexDocumentId(userId))
        },
    )
}

private fun samuraiUserIndexDocumentId(userId: String): String = "samurai-user:${sha256Hex(userId).take(40)}"

private fun requireSamuraiUserIndexId(userId: String) {
    require(userId.length in 1..240 && userId.none(Char::isISOControl)) { "invalid Samurai user index principal" }
}

private fun encodeSamuraiUserCursor(query: FinOpsQuery, userId: String, documentId: String): String {
    requireSamuraiUserIndexId(userId)
    require(documentId == samuraiUserIndexDocumentId(userId)) { "invalid Samurai user index document" }
    val payload = listOf("v1", query.samuraiUserCursorFingerprint(), query.samuraiUserOrder.name, userId, documentId)
        .joinToString("\n")
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeSamuraiUserCursor(cursor: String, query: FinOpsQuery): Pair<String, String> {
    val decoded = runCatching {
        Base64.getUrlDecoder().decode(cursor).toString(StandardCharsets.UTF_8).split('\n')
    }.getOrElse { throw IllegalArgumentException("invalid Samurai user cursor", it) }
    require(
        decoded.size == 5 && decoded[0] == "v1" && decoded[1] == query.samuraiUserCursorFingerprint() &&
            decoded[2] == query.samuraiUserOrder.name,
    ) { "Samurai user cursor does not belong to this query" }
    val userId = decoded[3]
    val documentId = decoded[4]
    requireSamuraiUserIndexId(userId)
    require(documentId == samuraiUserIndexDocumentId(userId)) { "invalid Samurai user cursor" }
    return userId to documentId
}

private fun FinOpsQuery.samuraiUserCursorFingerprint(): String = sha256Hex(
    listOf(
        from.toString(), toExclusive.toString(), project.orEmpty(), environment.orEmpty(), vendor.orEmpty(),
        service.orEmpty(), provider.orEmpty(), modelId.orEmpty(), runId.orEmpty(), status?.name.orEmpty(),
        samuraiUserOrder.name,
    ).joinToString(separator = "") { value -> "${value.length}:$value" },
).take(24)

private const val ENTRY_CURSOR_NAMESPACE = "entry"
private const val ALLOCATION_CURSOR_NAMESPACE = "allocation"

private fun encodeCursor(query: FinOpsQuery, namespace: String, timestamp: Long, id: String): String =
    "v1:$namespace:${query.cursorFingerprint()}:$timestamp:$id"

private fun decodeCursor(cursor: String, query: FinOpsQuery, namespace: String): Pair<Long, String> {
    val parts = cursor.split(':', limit = 5)
    require(parts.size == 5 && parts[0] == "v1" && parts[1] == namespace && parts[2] == query.cursorFingerprint()) {
        "cursor does not belong to this query"
    }
    val timestamp = parts[3].toLongOrNull() ?: throw IllegalArgumentException("invalid cursor")
    val id = parts[4]
    require(id.length <= 240 && !id.contains('\n') && !id.contains('\r')) { "invalid cursor" }
    return timestamp to id
}

private fun FinOpsQuery.cursorFingerprint(): String = sha256Hex(
    listOf(
        from.toString(), toExclusive.toString(), project.orEmpty(), environment.orEmpty(), vendor.orEmpty(),
        service.orEmpty(), userId.orEmpty(), provider.orEmpty(), modelId.orEmpty(), runId.orEmpty(),
        status?.name.orEmpty(), order.name,
    ).joinToString(separator = "") { value -> "${value.length}:$value" },
).take(24)

private fun FinOpsQuery.matches(entry: FinOpsEntry): Boolean =
    entry.incurredAt in from until toExclusive &&
        (project == null || entry.project == project) &&
        (environment == null || entry.environment == environment) &&
        (vendor == null || entry.vendor == vendor) &&
        (service == null || entry.service == service) &&
        (status == null || entry.status == status)

private fun FinOpsEntry.toDocument(): Map<String, Any?> = mapOf(
    "source" to source,
    "sourceRecordId" to sourceRecordId,
    "direction" to direction.name,
    "amountCurrency" to amount.currency,
    "amountMinorUnits" to amount.minorUnits,
    "amountScale" to amount.scale,
    "usdMicros" to usdMicros,
    "incurredAt" to incurredAt,
    "invoiceMonth" to invoiceMonth,
    "project" to project,
    "environment" to environment,
    "vendor" to vendor,
    "service" to service,
    "sku" to sku,
    "status" to status.name,
    "reconciliationKey" to reconciliationKey,
    "sourceHash" to sourceHash,
    "metadata" to metadata,
    "recordedAt" to recordedAt,
)

private fun FinOpsBudget.toDocument(): Map<String, Any?> = mapOf(
    "scope" to scope.name,
    "scopeValue" to scopeValue,
    "amountUsdMicros" to amountUsdMicros,
    "warningThresholdBasisPoints" to warningThresholdBasisPoints,
    "criticalThresholdBasisPoints" to criticalThresholdBasisPoints,
    "hardGate" to hardGate,
    "effectiveFrom" to effectiveFrom,
    "effectiveToExclusive" to effectiveToExclusive,
    "enabled" to enabled,
    "sourceHash" to sourceHash,
)

private fun PortfolioFirestoreDocument.toBudget(): FinOpsBudget = FinOpsBudget(
    id = id,
    scope = enumValueOf(data.string("scope")),
    scopeValue = data["scopeValue"] as? String,
    amountUsdMicros = data.long("amountUsdMicros"),
    warningThresholdBasisPoints = data.int("warningThresholdBasisPoints"),
    criticalThresholdBasisPoints = data.int("criticalThresholdBasisPoints"),
    hardGate = data.boolean("hardGate"),
    effectiveFrom = data.long("effectiveFrom"),
    effectiveToExclusive = (data["effectiveToExclusive"] as? Number)?.toLong(),
    enabled = data.boolean("enabled"),
    sourceHash = data.string("sourceHash"),
)

private fun FinOpsFxRate.toDocument(): Map<String, Any?> = mapOf(
    "sourceCurrency" to sourceCurrency,
    "effectiveDate" to effectiveDate,
    "usdMinorUnits" to usdPerMajorUnit.minorUnits,
    "usdScale" to usdPerMajorUnit.scale,
    "provenance" to provenance.name,
    "source" to source,
    "sourceHash" to sourceHash,
)

private fun FinOpsRecurringExpense.toDocument(): Map<String, Any?> = mapOf(
    "amountCurrency" to amount.currency,
    "amountMinorUnits" to amount.minorUnits,
    "amountScale" to amount.scale,
    "usdMicros" to usdMicros,
    "project" to project,
    "environment" to environment,
    "vendor" to vendor,
    "service" to service,
    "sku" to sku,
    "cadence" to cadence.name,
    "startDate" to startDate,
    "endDateExclusive" to endDateExclusive,
    "enabled" to enabled,
    "sourceHash" to sourceHash,
)

private fun PortfolioFirestoreDocument.toRecurringExpense(): FinOpsRecurringExpense = FinOpsRecurringExpense(
    id = id,
    amount = MoneyAmount(data.string("amountCurrency"), data.long("amountMinorUnits"), data.int("amountScale")),
    usdMicros = data.long("usdMicros"),
    project = data.string("project"),
    environment = data.string("environment"),
    vendor = data.string("vendor"),
    service = data.string("service"),
    sku = data["sku"] as? String,
    cadence = enumValueOf(data.string("cadence")),
    startDate = data.string("startDate"),
    endDateExclusive = data["endDateExclusive"] as? String,
    enabled = data.boolean("enabled"),
    sourceHash = data.string("sourceHash"),
)

private fun PortfolioFirestoreDocument.toFxRate(): FinOpsFxRate = FinOpsFxRate(
    id = id,
    sourceCurrency = data.string("sourceCurrency"),
    effectiveDate = data.string("effectiveDate"),
    usdPerMajorUnit = MoneyAmount("USD", data.long("usdMinorUnits"), data.int("usdScale")),
    provenance = enumValueOf(data.string("provenance")),
    source = data.string("source"),
    sourceHash = data.string("sourceHash"),
)

private const val FINOPS_GLOBAL_ROLLUP_SHARDS = 16

internal fun finOpsRollupShard(entryId: String): String =
    (sha256Hex(entryId).take(2).toInt(16) % FINOPS_GLOBAL_ROLLUP_SHARDS).toString(16).padStart(2, '0')

private fun FinOpsEntry.toDailyRollup(allocations: List<CostAllocation>): FinOpsDailyRollup {
    val dayStart = utcDate(incurredAt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val costClass = metadata["costClass"] ?: inferredCostClass(source, vendor)
    val dimensions = "$dayStart|$source|$project|$environment|$vendor|$service|$costClass|${status.name}"
    val finalized = status == FinOpsStatus.FINALIZED || status == FinOpsStatus.ADJUSTMENT
    return FinOpsDailyRollup(
        id = "day:${sha256Hex(dimensions).take(40)}",
        dayStart = dayStart,
        source = source,
        project = project,
        environment = environment,
        vendor = vendor,
        service = service,
        costClass = costClass,
        status = status,
        finalizedSpendUsdMicros = if (finalized) signedSpendMicros else 0L,
        accruedSpendUsdMicros = if (status == FinOpsStatus.ACCRUED) signedSpendMicros else 0L,
        estimatedSpendUsdMicros = if (status == FinOpsStatus.ESTIMATED) signedSpendMicros else 0L,
        revenueUsdMicros = if (finalized) signedRevenueMicros else 0L,
        stripeFeesUsdMicros = if (finalized) signedStripeFeeMicros else 0L,
        directAiCostUsdMicros = if (project.equals("samurai", true) && metadata["costClass"] == "direct_ai") signedSpendMicros else 0L,
        contributionCostUsdMicros = if (project.equals("samurai", true)) signedSpendMicros else 0L,
        unallocatedUsdMicros = 0L,
        entryCount = 1L,
    )
}

private fun FinOpsEntry.signedCostAllocation(allocation: CostAllocation): Long {
    if (direction == FinOpsDirection.REVENUE || metadata["accountingClass"] == "revenue") return 0L
    return when (direction) {
        FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -allocation.allocatedUsdMicros
        FinOpsDirection.REVENUE -> 0L
        else -> allocation.allocatedUsdMicros
    }
}

private fun FinOpsEntry.unallocatedUsdMicros(allocations: List<CostAllocation>): Long = allocations
    .filter { it.method == AllocationMethod.RESIDUAL && it.userId == null }
    .sumOf(::signedCostAllocation)

private fun FinOpsDailyRollup.incrementFields(): Map<String, Long> = mapOf(
    "finalizedSpendUsdMicros" to finalizedSpendUsdMicros,
    "accruedSpendUsdMicros" to accruedSpendUsdMicros,
    "estimatedSpendUsdMicros" to estimatedSpendUsdMicros,
    "revenueUsdMicros" to revenueUsdMicros,
    "stripeFeesUsdMicros" to stripeFeesUsdMicros,
    "directAiCostUsdMicros" to directAiCostUsdMicros,
    "contributionCostUsdMicros" to contributionCostUsdMicros,
    "unallocatedUsdMicros" to unallocatedUsdMicros,
    "entryCount" to entryCount,
)

private fun FinOpsEntry.toUserDailyRollup(allocation: CostAllocation): FinOpsUserDailyRollup {
    val userId = requireNotNull(allocation.userId)
    val dayStart = utcDate(incurredAt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val provider = allocation.provider ?: vendor
    val modelId = allocation.modelId ?: sku ?: "unattributed"
    val dimensions = "$dayStart|$reconciliationKey|$environment|$vendor|$service|$userId|$provider|$modelId|${allocation.method.name}"
    val isMeteredRequest = allocation.method == AllocationMethod.METERED && metadata["costClass"] == "direct_ai"
    val isRevenue = direction == FinOpsDirection.REVENUE || metadata["accountingClass"] == "revenue"
    val signedAllocation = when (direction) {
        FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -allocation.allocatedUsdMicros
        else -> allocation.allocatedUsdMicros
    }
    val cost = if (isRevenue) 0L else signedAllocation
    val providerCost = if (allocation.method == AllocationMethod.METERED) cost else 0L
    val revenue = if (isRevenue) signedAllocation else 0L
    return FinOpsUserDailyRollup(
        id = "user-day:${sha256Hex(dimensions).take(40)}",
        dayStart = dayStart,
        reconciliationKey = reconciliationKey,
        environment = environment,
        vendor = vendor,
        service = service,
        userId = userId,
        provider = provider,
        modelId = modelId,
        method = allocation.method,
        providerCostUsdMicros = providerCost,
        allocatedInvoiceUsdMicros = if (allocation.method == AllocationMethod.METERED) 0L else cost,
        creditsBurned = if (isMeteredRequest) metadata["creditsBurned"]?.toLongOrNull() ?: 0L else 0L,
        requests = if (isMeteredRequest) 1L else 0L,
        revenueUsdMicros = revenue,
        fallbacks = if (isMeteredRequest && metadata["fallback"].equals("true", true)) 1L else 0L,
        toolCalls = if (isMeteredRequest) metadata["toolCallCount"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L else 0L,
    )
}

private fun FinOpsUserDailyRollup.incrementFields(): Map<String, Long> = mapOf(
    "providerCostUsdMicros" to providerCostUsdMicros,
    "allocatedInvoiceUsdMicros" to allocatedInvoiceUsdMicros,
    "creditsBurned" to creditsBurned,
    "requests" to requests,
    "revenueUsdMicros" to revenueUsdMicros,
    "fallbacks" to fallbacks,
    "toolCalls" to toolCalls,
)

private fun FinOpsEntry.toReconciliationRollup(): FinOpsReconciliationRollup {
    val dayStart = utcDate(incurredAt).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val costClass = metadata["costClass"] ?: inferredCostClass(source, vendor)
    val dimensions = "$dayStart|$reconciliationKey|$project|$environment|$vendor|$service|$costClass"
    val estimated = status == FinOpsStatus.ESTIMATED
    val accrued = status == FinOpsStatus.ACCRUED
    val finalized = status == FinOpsStatus.FINALIZED || status == FinOpsStatus.ADJUSTMENT
    return FinOpsReconciliationRollup(
        id = "reconciliation:${sha256Hex(dimensions).take(40)}",
        dayStart = dayStart,
        reconciliationKey = reconciliationKey,
        project = project,
        environment = environment,
        vendor = vendor,
        service = service,
        costClass = costClass,
        accruedUsdMicros = if (accrued) signedSpendMicros else 0L,
        estimatedUsdMicros = if (estimated) signedSpendMicros else 0L,
        finalizedUsdMicros = if (finalized) signedSpendMicros else 0L,
        finalizedEntryCount = if (finalized) 1L else 0L,
    )
}

private fun FinOpsDailyRollup.plus(other: FinOpsDailyRollup): FinOpsDailyRollup {
    require(id == other.id)
    return copy(
        finalizedSpendUsdMicros = Math.addExact(finalizedSpendUsdMicros, other.finalizedSpendUsdMicros),
        accruedSpendUsdMicros = Math.addExact(accruedSpendUsdMicros, other.accruedSpendUsdMicros),
        estimatedSpendUsdMicros = Math.addExact(estimatedSpendUsdMicros, other.estimatedSpendUsdMicros),
        revenueUsdMicros = Math.addExact(revenueUsdMicros, other.revenueUsdMicros),
        stripeFeesUsdMicros = Math.addExact(stripeFeesUsdMicros, other.stripeFeesUsdMicros),
        directAiCostUsdMicros = Math.addExact(directAiCostUsdMicros, other.directAiCostUsdMicros),
        contributionCostUsdMicros = Math.addExact(contributionCostUsdMicros, other.contributionCostUsdMicros),
        unallocatedUsdMicros = Math.addExact(unallocatedUsdMicros, other.unallocatedUsdMicros),
        entryCount = Math.addExact(entryCount, other.entryCount),
    )
}

private fun FinOpsUserDailyRollup.plus(other: FinOpsUserDailyRollup): FinOpsUserDailyRollup {
    require(id == other.id)
    return copy(
        providerCostUsdMicros = Math.addExact(providerCostUsdMicros, other.providerCostUsdMicros),
        allocatedInvoiceUsdMicros = Math.addExact(allocatedInvoiceUsdMicros, other.allocatedInvoiceUsdMicros),
        creditsBurned = Math.addExact(creditsBurned, other.creditsBurned),
        requests = Math.addExact(requests, other.requests),
        revenueUsdMicros = Math.addExact(revenueUsdMicros, other.revenueUsdMicros),
        fallbacks = Math.addExact(fallbacks, other.fallbacks),
        toolCalls = Math.addExact(toolCalls, other.toolCalls),
    )
}

private fun FinOpsReconciliationRollup.plus(other: FinOpsReconciliationRollup): FinOpsReconciliationRollup {
    require(id == other.id)
    return copy(
        accruedUsdMicros = Math.addExact(accruedUsdMicros, other.accruedUsdMicros),
        estimatedUsdMicros = Math.addExact(estimatedUsdMicros, other.estimatedUsdMicros),
        finalizedUsdMicros = Math.addExact(finalizedUsdMicros, other.finalizedUsdMicros),
        finalizedEntryCount = Math.addExact(finalizedEntryCount, other.finalizedEntryCount),
    )
}

private fun FinOpsQuery.matches(rollup: FinOpsDailyRollup): Boolean =
    rollup.dayStart in from until toExclusive &&
        (project == null || rollup.project == project) &&
        (environment == null || rollup.environment == environment) &&
        (vendor == null || rollup.vendor == vendor) &&
        (service == null || rollup.service == service) &&
        (status == null || rollup.status == status)

private fun FinOpsQuery.matches(rollup: FinOpsUserDailyRollup): Boolean =
    rollup.dayStart in from until toExclusive &&
        (project == null || project.equals("samurai", true)) &&
        (environment == null || rollup.environment == environment) &&
        (vendor == null || rollup.vendor == vendor) &&
        (service == null || rollup.service == service) &&
        (userId == null || rollup.userId == userId) &&
        (provider == null || rollup.provider == provider) &&
        (modelId == null || rollup.modelId == modelId)

private fun FinOpsQuery.hasAllocationFilter(): Boolean =
    userId != null || provider != null || modelId != null || runId != null

private fun FinOpsQuery.primaryAllocationDimension(): Pair<String, String> = when {
    userId != null -> "userId" to userId
    provider != null -> "provider" to provider
    modelId != null -> "modelId" to modelId
    else -> "runId" to requireNotNull(runId)
}

private fun CostAllocation.dimensions(): List<Pair<String, String>> = listOfNotNull(
    userId?.let { "userId" to it },
    provider?.let { "provider" to it },
    modelId?.let { "modelId" to it },
    runId?.let { "runId" to it },
)

private data class AllocationEntryProjection(
    val id: String,
    val dimensionType: String,
    val dimensionValue: String,
    val entryId: String,
    val incurredAt: Long,
) {
    fun canonical(): String = listOf(id, dimensionType, dimensionValue, entryId, incurredAt.toString())
        .joinToString(separator = "") { value -> "${value.length}:$value" }
}

private fun allocationEntryProjection(
    entry: FinOpsEntry,
    dimensionType: String,
    dimensionValue: String,
): AllocationEntryProjection = AllocationEntryProjection(
    id = "allocation-entry:${sha256Hex("$dimensionType\u0000$dimensionValue\u0000${entry.id}").take(40)}",
    dimensionType = dimensionType,
    dimensionValue = dimensionValue,
    entryId = entry.id,
    incurredAt = entry.incurredAt,
)

private fun PortfolioFirestoreDocument.toAllocationEntryProjectionOrNull(): AllocationEntryProjection? {
    val dimensionType = data["dimensionType"] as? String ?: return null
    val dimensionValue = data["dimensionValue"] as? String ?: return null
    val entryId = data["entryId"] as? String ?: return null
    val incurredAt = (data["incurredAt"] as? Number)?.toLong() ?: return null
    if (dimensionType !in setOf("userId", "provider", "modelId")) return null
    if (dimensionValue.isEmpty() || dimensionValue.length > 240 || '\n' in dimensionValue || '\r' in dimensionValue) return null
    if (incurredAt < 0L) return null
    val expectedId = "allocation-entry:${sha256Hex("$dimensionType\u0000$dimensionValue\u0000$entryId").take(40)}"
    if (id != expectedId) return null
    return AllocationEntryProjection(id, dimensionType, dimensionValue, entryId, incurredAt)
}

private fun reconcileAllocationEntryProjections(
    expected: Map<String, AllocationEntryProjection>,
    actual: Map<String, AllocationEntryProjection>,
    invalidCount: Long = 0L,
    orphanedAllocationCount: Long = 0L,
): FinOpsAllocationProjectionReconciliation {
    val missing = expected.keys - actual.keys
    val unexpected = actual.keys - expected.keys
    val mismatched = (expected.keys intersect actual.keys).count { id -> expected[id] != actual[id] }.toLong()
    val expectedHash = sha256Hex(expected.values.sortedBy(AllocationEntryProjection::id).joinToString("\n", transform = AllocationEntryProjection::canonical))
    val actualHash = sha256Hex(actual.values.sortedBy(AllocationEntryProjection::id).joinToString("\n", transform = AllocationEntryProjection::canonical))
    val ready = missing.isEmpty() && unexpected.isEmpty() && mismatched == 0L && invalidCount == 0L &&
        orphanedAllocationCount == 0L && expected.size == actual.size && expectedHash == actualHash
    return FinOpsAllocationProjectionReconciliation(
        expectedCount = expected.size.toLong(),
        actualCount = actual.size.toLong() + invalidCount,
        missingCount = missing.size.toLong(),
        unexpectedCount = unexpected.size.toLong(),
        mismatchedCount = mismatched,
        invalidCount = invalidCount,
        orphanedAllocationCount = orphanedAllocationCount,
        expectedHash = expectedHash,
        actualHash = actualHash,
        ready = ready,
    )
}

private fun FinOpsQuery.matches(allocation: CostAllocation): Boolean =
    (userId == null || allocation.userId == userId) &&
        (provider == null || allocation.provider == provider) &&
        (modelId == null || allocation.modelId == modelId) &&
        (runId == null || allocation.runId == runId)

private fun FinOpsQuery.matches(rollup: FinOpsReconciliationRollup): Boolean =
    rollup.dayStart in from until toExclusive &&
        (project == null || rollup.project == project) &&
        (environment == null || rollup.environment == environment) &&
        (vendor == null || rollup.vendor == vendor) &&
        (service == null || rollup.service == service)

private fun PortfolioFirestoreDocument.toDailyRollup(): FinOpsDailyRollup = FinOpsDailyRollup(
    id = id,
    dayStart = data.long("dayStart"),
    source = data.string("source"),
    project = data.string("project"),
    environment = data.string("environment"),
    vendor = data.string("vendor"),
    service = data.string("service"),
    costClass = (data["costClass"] as? String) ?: inferredCostClass(data.string("source"), data.string("vendor")),
    status = enumValueOf(data.string("status")),
    finalizedSpendUsdMicros = data.long("finalizedSpendUsdMicros"),
    accruedSpendUsdMicros = data.long("accruedSpendUsdMicros"),
    estimatedSpendUsdMicros = data.long("estimatedSpendUsdMicros"),
    revenueUsdMicros = data.long("revenueUsdMicros"),
    stripeFeesUsdMicros = data.long("stripeFeesUsdMicros"),
    directAiCostUsdMicros = data.long("directAiCostUsdMicros"),
    contributionCostUsdMicros = data.long("contributionCostUsdMicros"),
    unallocatedUsdMicros = data.longOrZero("unallocatedUsdMicros"),
    entryCount = data.long("entryCount"),
)

private fun PortfolioFirestoreDocument.toUserDailyRollup(): FinOpsUserDailyRollup = FinOpsUserDailyRollup(
    id = id,
    dayStart = data.long("dayStart"),
    reconciliationKey = data["reconciliationKey"] as? String ?: "legacy:$id",
    environment = data.string("environment"),
    vendor = data.string("vendor"),
    service = data.string("service"),
    userId = data.string("userId"),
    provider = data.string("provider"),
    modelId = data.string("modelId"),
    method = enumValueOf(data.string("method")),
    providerCostUsdMicros = data.long("providerCostUsdMicros"),
    allocatedInvoiceUsdMicros = data.long("allocatedInvoiceUsdMicros"),
    creditsBurned = data.long("creditsBurned"),
    requests = data.long("requests"),
    revenueUsdMicros = data.long("revenueUsdMicros"),
    fallbacks = data.longOrZero("fallbacks"),
    toolCalls = data.longOrZero("toolCalls"),
)

private fun PortfolioFirestoreDocument.toReconciliationRollup(): FinOpsReconciliationRollup = FinOpsReconciliationRollup(
    id = id,
    dayStart = data.long("dayStart"),
    reconciliationKey = data.string("reconciliationKey"),
    project = data.string("project"),
    environment = data.string("environment"),
    vendor = data.string("vendor"),
    service = data.string("service"),
    costClass = (data["costClass"] as? String) ?: inferredCostClass(null, data.string("vendor")),
    accruedUsdMicros = data.longOrZero("accruedUsdMicros"),
    estimatedUsdMicros = data.long("estimatedUsdMicros"),
    finalizedUsdMicros = data.long("finalizedUsdMicros"),
    finalizedEntryCount = data.longOrZero("finalizedEntryCount"),
)

private fun inferredCostClass(source: String?, vendor: String): String = when {
    source.equals("gcp", ignoreCase = true) ||
        source.equals("cloudflare", ignoreCase = true) ||
        vendor.equals("google-cloud", ignoreCase = true) ||
        vendor.equals("cloudflare", ignoreCase = true) -> "cloud_platform"
    else -> "unclassified"
}

private fun PortfolioFirestoreDocument.toImportRun(): FinOpsImportRun = FinOpsImportRun(
    id = id,
    source = data.string("source"),
    fromDate = data.string("fromDate"),
    toDateExclusive = data.string("toDateExclusive"),
    sourceRecords = data.long("sourceRecords"),
    entries = data.long("entries"),
    status = data.string("status"),
    completedAt = data.long("completedAt"),
    sourceHash = data.string("sourceHash"),
    failureCode = data["failureCode"] as? String,
)

private fun PortfolioFirestoreDocument.toEntry(): FinOpsEntry = FinOpsEntry(
    id = id,
    source = data.string("source"),
    sourceRecordId = data.string("sourceRecordId"),
    direction = enumValueOf(data.string("direction")),
    amount = MoneyAmount(data.string("amountCurrency"), data.long("amountMinorUnits"), data.int("amountScale")),
    usdMicros = data.long("usdMicros"),
    incurredAt = data.long("incurredAt"),
    invoiceMonth = data.string("invoiceMonth"),
    project = data.string("project"),
    environment = data.string("environment"),
    vendor = data.string("vendor"),
    service = data.string("service"),
    sku = data["sku"] as? String,
    status = enumValueOf(data.string("status")),
    reconciliationKey = data.string("reconciliationKey"),
    sourceHash = data.string("sourceHash"),
    metadata = (data["metadata"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty(),
    recordedAt = data.long("recordedAt"),
)

private fun FinOpsEntry.sameImmutablePayload(other: FinOpsEntry): Boolean =
    copy(recordedAt = 0L) == other.copy(recordedAt = 0L)

private fun CostAllocation.toDocument(): Map<String, Any?> = mapOf(
    "entryId" to entryId,
    "project" to project,
    "userId" to userId,
    "provider" to provider,
    "modelId" to modelId,
    "runId" to runId,
    "allocatedUsdMicros" to allocatedUsdMicros,
    "method" to method.name,
)

private fun PortfolioFirestoreDocument.toAllocation(): CostAllocation = CostAllocation(
    id = id,
    entryId = data.string("entryId"),
    project = data.string("project"),
    userId = data["userId"] as? String,
    provider = data["provider"] as? String,
    modelId = data["modelId"] as? String,
    runId = data["runId"] as? String,
    allocatedUsdMicros = data.long("allocatedUsdMicros"),
    method = enumValueOf(data.string("method")),
)

private fun Map<String, Any?>.string(key: String): String = this[key]?.toString()
    ?: error("FinOps document is missing $key")
private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong()
    ?: error("FinOps document is missing numeric $key")
private fun Map<String, Any?>.longOrZero(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.int(key: String): Int = long(key).toInt()
private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean
    ?: error("FinOps document is missing boolean $key")
