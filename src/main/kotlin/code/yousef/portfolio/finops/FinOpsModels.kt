package code.yousef.portfolio.finops

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

const val USD_MICRO_SCALE: Int = 6
const val USD_TO_SAR_MICROS: Long = 3_750_000L

internal val FINOPS_CONNECTOR_FAILURE_CODES: Set<String> = setOf(
    "configuration",
    "workload_identity",
    "provider_authorization",
    "provider_api",
    "provider_schema",
    "bigquery_authorization",
    "bigquery_query",
    "bigquery_schema",
    "query_limit",
    "pagination_limit",
    "queue_delivery",
    "record_validation",
    "unsupported_currency",
    "cost_unavailable",
    "restricted_api_unavailable",
    "unknown",
)

@Serializable
data class MoneyAmount(
    val currency: String,
    val minorUnits: Long,
    val scale: Int,
) {
    init {
        require(currency.matches(Regex("^[A-Z]{3}$"))) { "currency must be an ISO 4217 code" }
        require(scale in 0..9) { "scale must be between 0 and 9" }
    }

    fun decimal(): BigDecimal = BigDecimal.valueOf(minorUnits, scale)

    companion object {
        fun parse(currency: String, value: String): MoneyAmount {
            val decimal = value.trim().toBigDecimalOrNull()
                ?: throw IllegalArgumentException("amount must be a decimal number")
            require(decimal.scale() <= 9) { "amount supports at most 9 decimal places" }
            val normalizedScale = decimal.scale().coerceAtLeast(0)
            return MoneyAmount(
                currency = currency.trim().uppercase(),
                minorUnits = decimal.movePointRight(normalizedScale).longValueExact(),
                scale = normalizedScale,
            )
        }
    }
}

@Serializable
enum class FinOpsDirection { EXPENSE, FEE, TAX, REFUND, REVENUE, CREDIT, ADJUSTMENT }

@Serializable
enum class FinOpsStatus { ESTIMATED, ACCRUED, FINALIZED, ADJUSTMENT }

@Serializable
enum class FinOpsEntryOrder { NEWEST, OLDEST }

@Serializable
enum class SamuraiUserOrder { ASCENDING, DESCENDING }

@Serializable
enum class AllocationMethod { DIRECT, METERED, PROPORTIONAL, RESIDUAL }

@Serializable
enum class FinOpsBudgetScope { STUDIO, COST_CLASS, PROJECT, VENDOR, SERVICE, ENVIRONMENT }

@Serializable
data class FinOpsBudget(
    val id: String,
    val scope: FinOpsBudgetScope,
    val scopeValue: String? = null,
    val amountUsdMicros: Long,
    val warningThresholdBasisPoints: Int = 7_500,
    val criticalThresholdBasisPoints: Int = 9_000,
    val hardGate: Boolean = false,
    val effectiveFrom: Long,
    val effectiveToExclusive: Long? = null,
    val enabled: Boolean = true,
    val sourceHash: String,
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid budget id" }
        require(scopeValue == null || scopeValue.isSafeDimension("budget scope value"))
        require(scope == FinOpsBudgetScope.STUDIO || scopeValue != null) { "scoped budgets require a scope value" }
        require(amountUsdMicros > 0L) { "budget amount must be positive" }
        require(warningThresholdBasisPoints in 1..10_000) { "invalid budget warning threshold" }
        require(criticalThresholdBasisPoints in warningThresholdBasisPoints..10_000) { "invalid budget critical threshold" }
        require(effectiveToExclusive == null || effectiveToExclusive > effectiveFrom) { "invalid budget effective range" }
        require(sourceHash.matches(Regex("^[a-f0-9]{64}$"))) { "sourceHash must be SHA-256 hex" }
    }

    fun warningUsdMicros(): Long = thresholdMicros(warningThresholdBasisPoints)
    fun criticalUsdMicros(): Long = thresholdMicros(criticalThresholdBasisPoints)

    private fun thresholdMicros(basisPoints: Int): Long = BigDecimal.valueOf(amountUsdMicros)
        .multiply(BigDecimal.valueOf(basisPoints.toLong()))
        .divide(BigDecimal("10000"), 0, RoundingMode.HALF_EVEN)
        .longValueExact()
}

@Serializable
enum class FinOpsFxProvenance { MANUAL, CENTRAL_BANK, VENDOR, INVOICE }

@Serializable
enum class FinOpsRecurrenceCadence { MONTHLY, YEARLY }

@Serializable
data class FinOpsRecurringExpense(
    val id: String,
    val amount: MoneyAmount,
    val usdMicros: Long,
    val project: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val sku: String? = null,
    val cadence: FinOpsRecurrenceCadence,
    val startDate: String,
    val endDateExclusive: String? = null,
    val enabled: Boolean = true,
    val sourceHash: String,
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid recurring expense id" }
        require(amount.minorUnits > 0L && usdMicros > 0L) { "recurring expense amounts must be positive" }
        require(project.isSafeDimension("project"))
        require(environment.isSafeDimension("environment"))
        require(vendor.isSafeDimension("vendor"))
        require(service.isSafeDimension("service"))
        val start = LocalDate.parse(startDate)
        require(start >= LocalDate.of(1970, 1, 1)) { "recurring expense cannot start before 1970" }
        endDateExclusive?.let { require(LocalDate.parse(it) > start) { "invalid recurring expense date range" } }
        require(sourceHash.matches(Regex("^[a-f0-9]{64}$"))) { "sourceHash must be SHA-256 hex" }
    }
}

@Serializable
data class FinOpsRecurringMaterialization(
    val templates: Int,
    val occurrences: Int,
    val inserted: Int,
    val duplicates: Int,
)

@Serializable
data class FinOpsRecurringMaterializeRequest(
    val fromDate: String,
    val toDateExclusive: String,
) {
    init {
        require(LocalDate.parse(toDateExclusive) > LocalDate.parse(fromDate)) { "invalid recurring materialization range" }
    }
}

@Serializable
data class FinOpsFxRate(
    val id: String,
    val sourceCurrency: String,
    val effectiveDate: String,
    val usdPerMajorUnit: MoneyAmount,
    val provenance: FinOpsFxProvenance,
    val source: String,
    val sourceHash: String,
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid FX rate id" }
        require(sourceCurrency.matches(Regex("^[A-Z]{3}$")) && sourceCurrency != "USD") { "invalid FX source currency" }
        require(effectiveDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) { "effectiveDate must be YYYY-MM-DD" }
        LocalDate.parse(effectiveDate)
        require(usdPerMajorUnit.currency == "USD" && usdPerMajorUnit.minorUnits > 0L) { "FX quote must be a positive USD amount" }
        require(source.isSafeDimension("FX source"))
        require(sourceHash.matches(Regex("^[a-f0-9]{64}$"))) { "sourceHash must be SHA-256 hex" }
    }

    fun convertToUsdMicros(amount: MoneyAmount): Long {
        require(amount.currency == sourceCurrency) { "FX rate currency does not match amount" }
        return amount.decimal()
            .multiply(usdPerMajorUnit.decimal())
            .movePointRight(USD_MICRO_SCALE)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
    }
}

@Serializable
data class FinOpsEntry(
    val id: String,
    val source: String,
    val sourceRecordId: String,
    val direction: FinOpsDirection,
    val amount: MoneyAmount,
    val usdMicros: Long,
    val incurredAt: Long,
    val invoiceMonth: String,
    val project: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val sku: String? = null,
    val status: FinOpsStatus,
    val reconciliationKey: String,
    val sourceHash: String,
    val metadata: Map<String, String> = emptyMap(),
    val recordedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid FinOps entry id" }
        require(sourceRecordId.length in 1..512 && '\n' !in sourceRecordId && '\r' !in sourceRecordId) {
            "invalid sourceRecordId"
        }
        require(source.isSafeDimension("source"))
        require(project.isSafeDimension("project"))
        require(environment.isSafeDimension("environment"))
        require(vendor.isSafeDimension("vendor"))
        require(service.isSafeDimension("service"))
        require(sku == null || sku.isSafeDimension("sku"))
        require(amount.minorUnits >= 0L && usdMicros >= 0L) {
            "financial amounts must be non-negative magnitudes; direction carries the sign"
        }
        require(incurredAt >= 0L && recordedAt >= 0L) { "financial timestamps cannot be negative" }
        require(invoiceMonth.matches(Regex("^\\d{4}-\\d{2}$"))) { "invoiceMonth must be YYYY-MM" }
        require(reconciliationKey.length in 1..240 && '\n' !in reconciliationKey && '\r' !in reconciliationKey) {
            "invalid reconciliationKey"
        }
        require(sourceHash.matches(Regex("^[a-f0-9]{64}$"))) { "sourceHash must be SHA-256 hex" }
        require(metadata.size <= 32) { "metadata supports at most 32 values" }
        require(metadata.all { (key, value) ->
            key.length in 1..64 && value.length <= 512 && '\n' !in key && '\r' !in key && '\n' !in value && '\r' !in value
        }) {
            "invalid metadata key or value"
        }
        listOf("creditsBurned", "inputTokens", "cachedInputTokens", "outputTokens", "toolCostUsdMicros", "toolCallCount")
            .forEach { key ->
                metadata[key]?.let { value ->
                    require(value.toLongOrNull()?.let { it >= 0L } == true) { "$key must be a non-negative integer" }
                }
            }
        metadata["fallback"]?.let { value ->
            require(value.equals("true", true) || value.equals("false", true)) { "fallback must be a boolean" }
        }
        metadata["costClass"]?.let { require(it.isSafeDimension("costClass")) }
        metadata["accountingClass"]?.let { value ->
            require(value == "expense" || value == "revenue") { "invalid accountingClass" }
        }
    }

    val signedSpendMicros: Long
        get() = when (direction) {
            FinOpsDirection.EXPENSE, FinOpsDirection.FEE, FinOpsDirection.TAX, FinOpsDirection.ADJUSTMENT -> usdMicros
            FinOpsDirection.REFUND -> if (metadata["accountingClass"] == "revenue") 0L else -usdMicros
            FinOpsDirection.CREDIT -> -usdMicros
            FinOpsDirection.REVENUE -> 0L
        }

    val signedRevenueMicros: Long
        get() = when (direction) {
            FinOpsDirection.REVENUE -> usdMicros
            FinOpsDirection.REFUND -> if (metadata["accountingClass"] == "revenue") -usdMicros else 0L
            else -> 0L
        }

    /**
     * Signed face value for transaction-level inspection only. Spend and
     * revenue summaries intentionally continue to use their separate
     * accounting projections above so revenue never offsets operational cost.
     */
    val signedLedgerMicros: Long
        get() = when (direction) {
            FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -usdMicros
            FinOpsDirection.EXPENSE,
            FinOpsDirection.FEE,
            FinOpsDirection.TAX,
            FinOpsDirection.REVENUE,
            FinOpsDirection.ADJUSTMENT,
            -> usdMicros
        }

    val signedStripeFeeMicros: Long
        get() {
            if (!vendor.equals("stripe", true) || metadata["accountingClass"] != "expense") return 0L
            return when (direction) {
                FinOpsDirection.FEE -> usdMicros
                FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -usdMicros
                else -> 0L
            }
        }
}

@Serializable
data class CostAllocation(
    val id: String,
    val entryId: String,
    val project: String,
    val userId: String? = null,
    val provider: String? = null,
    val modelId: String? = null,
    val runId: String? = null,
    val allocatedUsdMicros: Long,
    val method: AllocationMethod,
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid allocation id" }
        require(entryId.matches(SAFE_ID)) { "invalid entry id" }
        require(project.isSafeDimension("project"))
        require(allocatedUsdMicros >= 0L) { "allocatedUsdMicros cannot be negative" }
        listOfNotNull(userId, provider, modelId, runId).forEach {
            require(it.length <= 240 && !it.contains('\n') && !it.contains('\r')) { "invalid allocation dimension" }
        }
    }
}

@Serializable
data class FinOpsIngestRequest(
    val entry: FinOpsEntry,
    val allocations: List<CostAllocation> = emptyList(),
)

@Serializable
data class FinOpsIngestReceipt(
    val entryId: String,
    val state: String,
    val allocationCount: Int,
)

@Serializable
data class FinOpsAllocationBackfillResult(
    val processed: Int,
    val residualAllocations: Int,
    val nextCursor: String? = null,
)

@Serializable
data class FinOpsAllocationProjectionReconciliation(
    val expectedCount: Long,
    val actualCount: Long,
    val missingCount: Long,
    val unexpectedCount: Long,
    val mismatchedCount: Long,
    val invalidCount: Long,
    val orphanedAllocationCount: Long,
    val expectedHash: String,
    val actualHash: String,
    val ready: Boolean,
) {
    init {
        listOf(
            expectedCount,
            actualCount,
            missingCount,
            unexpectedCount,
            mismatchedCount,
            invalidCount,
            orphanedAllocationCount,
        ).forEach { require(it >= 0L) { "projection reconciliation counts cannot be negative" } }
        require(expectedHash.matches(Regex("^[a-f0-9]{64}$"))) { "expected projection hash must be SHA-256 hex" }
        require(actualHash.matches(Regex("^[a-f0-9]{64}$"))) { "actual projection hash must be SHA-256 hex" }
        val reconciled = missingCount == 0L && unexpectedCount == 0L && mismatchedCount == 0L &&
            invalidCount == 0L && orphanedAllocationCount == 0L && expectedCount == actualCount &&
            expectedHash == actualHash
        require(ready == reconciled) { "projection readiness must match reconciliation evidence" }
    }
}

@Serializable
data class FinOpsReconcileResult(
    val allocationBackfill: FinOpsAllocationBackfillResult,
    val allocationProjections: FinOpsAllocationProjectionReconciliation,
    val reconciliations: List<FinOpsReconciliation>,
)

@Serializable
data class FinOpsImportRun(
    val id: String,
    val source: String,
    val fromDate: String,
    val toDateExclusive: String,
    val sourceRecords: Long,
    val entries: Long,
    val status: String,
    val completedAt: Long,
    val sourceHash: String,
    val failureCode: String? = null,
) {
    init {
        require(id.matches(SAFE_ID)) { "invalid import run id" }
        require(source.isSafeDimension("source"))
        val from = runCatching { LocalDate.parse(fromDate) }
            .getOrElse { throw IllegalArgumentException("import run dates must be valid YYYY-MM-DD values", it) }
        val to = runCatching { LocalDate.parse(toDateExclusive) }
            .getOrElse { throw IllegalArgumentException("import run dates must be valid YYYY-MM-DD values", it) }
        require(to > from) { "import run date range must be non-empty and ordered" }
        require(sourceRecords >= 0 && entries >= 0) { "import counts cannot be negative" }
        require(completedAt >= 0L) { "import completion timestamp cannot be negative" }
        require(status in setOf("succeeded", "failed", "partial")) { "invalid import run status" }
        require(sourceHash.matches(Regex("^[a-f0-9]{64}$"))) { "sourceHash must be SHA-256 hex" }
        require(failureCode == null || failureCode in FINOPS_CONNECTOR_FAILURE_CODES) {
            "unsupported import failure code"
        }
        require(failureCode == null || status != "succeeded") {
            "successful import runs cannot include a failure code"
        }
    }
}

@Serializable
data class FinOpsConnectorState(
    val status: String,
    val completedAt: Long,
    val failureCode: String? = null,
) {
    init {
        require(status in setOf("succeeded", "failed", "partial")) { "invalid connector status" }
        require(completedAt >= 0L) { "connector completion timestamp cannot be negative" }
        require(failureCode == null || failureCode in FINOPS_CONNECTOR_FAILURE_CODES) {
            "unsupported connector failure code"
        }
        require(failureCode == null || status != "succeeded") {
            "successful connectors cannot include a failure code"
        }
    }
}

@Serializable
data class FinOpsQuery(
    val from: Long,
    val toExclusive: Long,
    val project: String? = null,
    val environment: String? = null,
    val vendor: String? = null,
    val service: String? = null,
    val userId: String? = null,
    val provider: String? = null,
    val modelId: String? = null,
    val runId: String? = null,
    val status: FinOpsStatus? = null,
    val order: FinOpsEntryOrder = FinOpsEntryOrder.NEWEST,
    val limit: Int = 100,
    val cursor: String? = null,
    val samuraiUserOrder: SamuraiUserOrder = SamuraiUserOrder.ASCENDING,
    val samuraiUserLimit: Int = 50,
    val samuraiUserCursor: String? = null,
) {
    init {
        require(from >= 0L && toExclusive > from) { "invalid time range" }
        require(limit in 1..500) { "limit must be between 1 and 500" }
        require(samuraiUserLimit in 1..100) { "Samurai user limit must be between 1 and 100" }
        listOfNotNull(project, environment, vendor, service).forEach {
            require(it.isSafeDimension("query dimension"))
        }
        listOfNotNull(userId, provider, modelId, runId).forEach {
            require(it.length in 1..240 && '\n' !in it && '\r' !in it) {
                "invalid allocation filter"
            }
        }
        require(cursor == null || (cursor.length in 1..500 && '\n' !in cursor && '\r' !in cursor)) {
            "invalid cursor"
        }
        require(
            samuraiUserCursor == null ||
                (samuraiUserCursor.length in 1..1_024 && '\n' !in samuraiUserCursor && '\r' !in samuraiUserCursor),
        ) { "invalid Samurai user cursor" }
    }
}

@Serializable
data class FinOpsEntryPage(
    val entries: List<FinOpsEntry>,
    val nextCursor: String? = null,
)

data class FinOpsCsvExport(
    val csv: String,
    val nextCursor: String?,
    val rowCount: Int,
)

@Serializable
data class FinOpsBreakdown(val key: String, val usdMicros: Long)

data class FinOpsDailyRollup(
    val id: String,
    val dayStart: Long,
    val source: String,
    val project: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val costClass: String,
    val status: FinOpsStatus,
    val finalizedSpendUsdMicros: Long,
    val accruedSpendUsdMicros: Long,
    val estimatedSpendUsdMicros: Long,
    val revenueUsdMicros: Long,
    val stripeFeesUsdMicros: Long,
    val directAiCostUsdMicros: Long,
    val contributionCostUsdMicros: Long,
    val unallocatedUsdMicros: Long,
    val entryCount: Long,
)

data class FinOpsUserDailyRollup(
    val id: String,
    val dayStart: Long,
    val reconciliationKey: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val userId: String,
    val provider: String,
    val modelId: String,
    val method: AllocationMethod,
    val providerCostUsdMicros: Long,
    val allocatedInvoiceUsdMicros: Long,
    val creditsBurned: Long,
    val requests: Long,
    val revenueUsdMicros: Long,
    val fallbacks: Long = 0L,
    val toolCalls: Long = 0L,
)

data class FinOpsReconciliationRollup(
    val id: String,
    val dayStart: Long,
    val reconciliationKey: String,
    val project: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val costClass: String,
    val accruedUsdMicros: Long,
    val estimatedUsdMicros: Long,
    val finalizedUsdMicros: Long,
    val finalizedEntryCount: Long,
)

@Serializable
data class FinOpsSummary(
    val from: Long,
    val toExclusive: Long,
    val finalizedSpendUsdMicros: Long,
    val accruedSpendUsdMicros: Long,
    val estimatedSpendUsdMicros: Long,
    val revenueUsdMicros: Long,
    val stripeFeesUsdMicros: Long,
    val directAiCostUsdMicros: Long,
    val grossMarginUsdMicros: Long,
    val contributionMarginUsdMicros: Long,
    val unreconciledUsdMicros: Long,
    val unallocatedUsdMicros: Long = 0L,
    val cloudPlatformBudgetUsdMicros: Long = 100_000_000L,
    val budgetWarningUsdMicros: Long = 75_000_000L,
    val budgetCriticalUsdMicros: Long = 90_000_000L,
    val cloudPlatformSpendUsdMicros: Long = 0L,
    val byProject: List<FinOpsBreakdown> = emptyList(),
    val byEnvironment: List<FinOpsBreakdown> = emptyList(),
    val byCategory: List<FinOpsBreakdown> = emptyList(),
    val byVendor: List<FinOpsBreakdown> = emptyList(),
    val byService: List<FinOpsBreakdown> = emptyList(),
    val dailyFinalizedSpend: List<FinOpsBreakdown> = emptyList(),
    val coverageGaps: List<String> = emptyList(),
    val dataFreshness: Map<String, Long> = emptyMap(),
    val connectorStates: Map<String, FinOpsConnectorState> = emptyMap(),
    val forecastSpendUsdMicros: Long = 0L,
    val previousPeriodSpendUsdMicros: Long = 0L,
    val monthOverMonthBasisPoints: Long? = null,
    val generatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class SamuraiUserSpend(
    val userId: String,
    val displayName: String? = null,
    val email: String? = null,
    val providerCostUsdMicros: Long,
    val allocatedInvoiceUsdMicros: Long,
    val creditsBurned: Long,
    val requests: Long,
    val revenueUsdMicros: Long,
    val grossMarginUsdMicros: Long,
    val fallbacks: Long = 0L,
    val toolCalls: Long = 0L,
    val byProvider: List<FinOpsBreakdown> = emptyList(),
    val byModel: List<FinOpsBreakdown> = emptyList(),
)

@Serializable
data class SamuraiUserSpendPage(
    val users: List<SamuraiUserSpend>,
    val nextCursor: String? = null,
)

data class SamuraiUserIdPage(
    val userIds: List<String>,
    val nextCursor: String? = null,
)

@Serializable
data class FinOpsDimensions(
    val projects: List<String>,
    val environments: List<String>,
    val vendors: List<String>,
    val services: List<String>,
    val providers: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val statuses: List<String> = FinOpsStatus.entries.map { it.name.lowercase() },
)

@Serializable
data class FinOpsReconciliation(
    val key: String,
    val estimatedUsdMicros: Long,
    val finalizedUsdMicros: Long,
    val differenceUsdMicros: Long,
    val state: String,
)

@Serializable
data class ManualFinOpsEntryRequest(
    val sourceRecordId: String,
    val direction: FinOpsDirection,
    val amount: MoneyAmount,
    val usdMicros: Long,
    val incurredAt: Long,
    val project: String,
    val environment: String,
    val vendor: String,
    val service: String,
    val sku: String? = null,
    val status: FinOpsStatus = FinOpsStatus.FINALIZED,
    val reconciliationKey: String,
    val sourceHash: String,
    val metadata: Map<String, String> = emptyMap(),
    val receipt: FinOpsReceiptReference? = null,
)

@Serializable
data class FinOpsReceiptReference(
    val storageKey: String,
    val sha256: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val uploadId: String? = null,
) {
    init {
        require(uploadId == null || uploadId.matches(Regex("^[a-f0-9]{32}$"))) { "invalid receipt upload id" }
        val expectedStorageKey = uploadId?.let { "sha256/$sha256/uploads/$it/$filename" }
            ?: "sha256/$sha256/$filename"
        require(storageKey == expectedStorageKey) { "receipt storage key must match its digest, upload id, and filename" }
        require(sha256.matches(Regex("^[a-f0-9]{64}$"))) { "receipt digest must be SHA-256 hex" }
        require(filename.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,191}$"))) { "invalid receipt filename" }
        require(contentType in FINOPS_RECEIPT_CONTENT_TYPES) { "unsupported receipt content type" }
        require(size in 1..26_214_400L) { "invalid receipt size" }
    }
}

@Serializable
data class FinOpsReceiptAttachment(
    val uploadId: String,
    val entryId: String,
    val storageKey: String,
    val sha256: String,
) {
    init {
        require(uploadId.matches(Regex("^[a-f0-9]{32}$"))) { "invalid receipt upload id" }
        require(entryId.matches(SAFE_ID)) { "invalid receipt attachment entry id" }
        require(sha256.matches(Regex("^[a-f0-9]{64}$"))) { "receipt digest must be SHA-256 hex" }
        require(storageKey.startsWith("sha256/$sha256/uploads/$uploadId/")) {
            "receipt attachment key must match its digest and upload id"
        }
    }
}

val FINOPS_RECEIPT_CONTENT_TYPES: Set<String> = setOf(
    "application/json",
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/webp",
    "text/csv",
    "text/plain",
)

fun defaultMonthRange(now: Instant = Instant.now()): Pair<Long, Long> {
    val month = YearMonth.from(now.atZone(ZoneOffset.UTC))
    return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to
        month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

fun invoiceMonth(epochMillis: Long): String = YearMonth.from(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
).toString()

fun utcDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

fun formatUsdMicros(micros: Long): String = BigDecimal.valueOf(micros, USD_MICRO_SCALE)
    .setScale(2, RoundingMode.HALF_EVEN)
    .toPlainString()

fun formatSarFromUsdMicros(micros: Long): String = BigDecimal.valueOf(micros, USD_MICRO_SCALE)
    .multiply(BigDecimal("3.75"))
    .setScale(2, RoundingMode.HALF_EVEN)
    .toPlainString()

private fun String.isSafeDimension(name: String): Boolean {
    require(length in 1..120 && !contains('\n') && !contains('\r')) { "invalid $name" }
    return true
}

private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,239}$")
