package code.yousef.portfolio.finops

import kotlinx.serialization.Serializable

private const val MAX_ROLLUP_BACKFILL_PAGE_SIZE = 500
private const val MILLIS_PER_UTC_DAY = 86_400_000L

@Serializable
data class FinOpsRollupBackfillRequest(
    val from: Long,
    val toExclusive: Long,
    val limit: Int = 100,
    val cursor: String? = null,
) {
    init {
        require(from >= 0L && toExclusive > from) { "invalid rollup backfill range" }
        require(from % MILLIS_PER_UTC_DAY == 0L && toExclusive % MILLIS_PER_UTC_DAY == 0L) {
            "rollup backfill range must use UTC day boundaries"
        }
        require(limit in 1..MAX_ROLLUP_BACKFILL_PAGE_SIZE) {
            "rollup backfill limit must be between 1 and $MAX_ROLLUP_BACKFILL_PAGE_SIZE"
        }
        require(cursor == null || cursor.length in 1..500) { "invalid rollup backfill cursor" }
    }
}

@Serializable
data class FinOpsRollupReconcileRequest(
    val from: Long,
    val toExclusive: Long,
) {
    init {
        require(from >= 0L && toExclusive > from) { "invalid rollup reconciliation range" }
        require(from % MILLIS_PER_UTC_DAY == 0L && toExclusive % MILLIS_PER_UTC_DAY == 0L) {
            "rollup reconciliation range must use UTC day boundaries"
        }
    }
}

@Serializable
data class FinOpsRollupBackfillResult(
    val processed: Int,
    val firstEntryId: String? = null,
    val lastEntryId: String? = null,
    val pageHash: String,
    val nextCursor: String? = null,
    val complete: Boolean,
)

@Serializable
data class FinOpsRollupParitySection(
    val expectedCount: Long,
    val actualCount: Long,
    val missingCount: Long,
    val unexpectedCount: Long,
    val mismatchedCount: Long,
    val expectedHash: String,
    val actualHash: String,
    val ready: Boolean,
)

@Serializable
data class FinOpsRollupParityReport(
    val from: Long,
    val toExclusive: Long,
    val daily: FinOpsRollupParitySection,
    val reconciliation: FinOpsRollupParitySection,
    val ready: Boolean,
)

/**
 * Rebuilds the versioned, sharded global rollups without changing the
 * repository used by live dashboard reads. The caller must keep execution
 * behind a separate, fail-closed operational gate until parity is proven.
 */
class FinOpsRollupBackfillService(
    private val source: FinOpsRepository,
    private val target: FinOpsRepository,
) {
    fun backfillPage(request: FinOpsRollupBackfillRequest): FinOpsRollupBackfillResult {
        val page = source.listEntries(
            FinOpsQuery(
                from = request.from,
                toExclusive = request.toExclusive,
                order = FinOpsEntryOrder.OLDEST,
                limit = request.limit,
                cursor = request.cursor,
            ),
        )
        val allocations = source.listAllocations(page.entries.mapTo(linkedSetOf(), FinOpsEntry::id))
            .groupBy(CostAllocation::entryId)
        page.entries.forEach { entry ->
            val entryAllocations = allocations[entry.id].orEmpty()
            target.ensureDailyRollups(entry, entryAllocations)
            target.ensureUnallocatedRollup(entry, entryAllocations)
        }
        return FinOpsRollupBackfillResult(
            processed = page.entries.size,
            firstEntryId = page.entries.firstOrNull()?.id,
            lastEntryId = page.entries.lastOrNull()?.id,
            pageHash = sha256Hex(page.entries.joinToString("\n", transform = FinOpsEntry::canonicalBackfillIdentity)),
            nextCursor = page.nextCursor,
            complete = page.nextCursor == null,
        )
    }

    fun reconcile(from: Long, toExclusive: Long): FinOpsRollupParityReport {
        FinOpsRollupReconcileRequest(from, toExclusive)
        val query = FinOpsQuery(from = from, toExclusive = toExclusive, limit = 500)
        val daily = compareCanonical(
            source.listDailyRollups(query).canonicalDailyRollups(),
            target.listDailyRollups(query).canonicalDailyRollups(),
        )
        val reconciliation = compareCanonical(
            source.listReconciliationRollups(query).canonicalReconciliationRollups(),
            target.listReconciliationRollups(query).canonicalReconciliationRollups(),
        )
        return FinOpsRollupParityReport(
            from = from,
            toExclusive = toExclusive,
            daily = daily,
            reconciliation = reconciliation,
            ready = daily.ready && reconciliation.ready,
        )
    }
}

private fun FinOpsEntry.canonicalBackfillIdentity(): String = listOf(
    id,
    sourceHash,
    incurredAt.toString(),
    usdMicros.toString(),
).joinToString("|")

private fun List<FinOpsDailyRollup>.canonicalDailyRollups(): Map<String, String> =
    groupBy(FinOpsDailyRollup::canonicalKey).mapValues { (_, values) ->
        values.fold(values.first().zeroed()) { total, value -> total.plusExact(value) }.canonicalValue()
    }

private fun FinOpsDailyRollup.canonicalKey(): String = listOf(
    dayStart,
    source,
    project,
    environment,
    vendor,
    service,
    costClass,
    status.name,
).joinToString("|")

private fun FinOpsDailyRollup.zeroed(): FinOpsDailyRollup = copy(
    id = canonicalKey(),
    finalizedSpendUsdMicros = 0,
    accruedSpendUsdMicros = 0,
    estimatedSpendUsdMicros = 0,
    revenueUsdMicros = 0,
    stripeFeesUsdMicros = 0,
    directAiCostUsdMicros = 0,
    contributionCostUsdMicros = 0,
    unallocatedUsdMicros = 0,
    entryCount = 0,
)

private fun FinOpsDailyRollup.plusExact(other: FinOpsDailyRollup): FinOpsDailyRollup = copy(
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

private fun FinOpsDailyRollup.canonicalValue(): String = listOf(
    finalizedSpendUsdMicros,
    accruedSpendUsdMicros,
    estimatedSpendUsdMicros,
    revenueUsdMicros,
    stripeFeesUsdMicros,
    directAiCostUsdMicros,
    contributionCostUsdMicros,
    unallocatedUsdMicros,
    entryCount,
).joinToString("|")

private fun List<FinOpsReconciliationRollup>.canonicalReconciliationRollups(): Map<String, String> =
    groupBy(FinOpsReconciliationRollup::canonicalKey).mapValues { (_, values) ->
        values.fold(values.first().zeroed()) { total, value -> total.plusExact(value) }.canonicalValue()
    }

private fun FinOpsReconciliationRollup.canonicalKey(): String = listOf(
    dayStart,
    reconciliationKey,
    project,
    environment,
    vendor,
    service,
    costClass,
).joinToString("|")

private fun FinOpsReconciliationRollup.zeroed(): FinOpsReconciliationRollup = copy(
    id = canonicalKey(),
    accruedUsdMicros = 0,
    estimatedUsdMicros = 0,
    finalizedUsdMicros = 0,
    finalizedEntryCount = 0,
)

private fun FinOpsReconciliationRollup.plusExact(
    other: FinOpsReconciliationRollup,
): FinOpsReconciliationRollup = copy(
    accruedUsdMicros = Math.addExact(accruedUsdMicros, other.accruedUsdMicros),
    estimatedUsdMicros = Math.addExact(estimatedUsdMicros, other.estimatedUsdMicros),
    finalizedUsdMicros = Math.addExact(finalizedUsdMicros, other.finalizedUsdMicros),
    finalizedEntryCount = Math.addExact(finalizedEntryCount, other.finalizedEntryCount),
)

private fun FinOpsReconciliationRollup.canonicalValue(): String = listOf(
    accruedUsdMicros,
    estimatedUsdMicros,
    finalizedUsdMicros,
    finalizedEntryCount,
).joinToString("|")

private fun compareCanonical(expected: Map<String, String>, actual: Map<String, String>): FinOpsRollupParitySection {
    val missing = expected.keys - actual.keys
    val unexpected = actual.keys - expected.keys
    val mismatched = expected.keys.intersect(actual.keys).count { expected[it] != actual[it] }.toLong()
    val expectedHash = canonicalMapHash(expected)
    val actualHash = canonicalMapHash(actual)
    val ready = missing.isEmpty() && unexpected.isEmpty() && mismatched == 0L && expectedHash == actualHash
    return FinOpsRollupParitySection(
        expectedCount = expected.size.toLong(),
        actualCount = actual.size.toLong(),
        missingCount = missing.size.toLong(),
        unexpectedCount = unexpected.size.toLong(),
        mismatchedCount = mismatched,
        expectedHash = expectedHash,
        actualHash = actualHash,
        ready = ready,
    )
}

private fun canonicalMapHash(values: Map<String, String>): String = sha256Hex(
    values.toSortedMap().entries.joinToString("\n") { (key, value) -> "$key=$value" },
)
