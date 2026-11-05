package code.yousef.portfolio.finops

import kotlinx.serialization.Serializable

@Serializable
data class GcpBillingRow(
    val billingAccountId: String,
    val sourceRecordId: String,
    val usageStartMillis: Long,
    val invoiceMonth: String,
    val projectId: String,
    val service: String,
    val sku: String,
    val currency: String,
    val costMinorUnits: Long,
    val costScale: Int,
    val creditsMinorUnits: Long = 0L,
    val creditsUsdMicros: Long = 0L,
    val usdMicros: Long,
    val exportTimeMillis: Long,
)

@Serializable
data class CloudflareBillingRow(
    val sourceRecordId: String,
    val chargeStartMillis: Long,
    val productFamily: String,
    val metric: String,
    val zoneName: String? = null,
    val currency: String = "USD",
    val billedMinorUnits: Long,
    val billedScale: Int = 2,
    val billedUsdMicros: Long,
    val finalized: Boolean,
)

@Serializable
data class StripeBalanceRow(
    val sourceRecordId: String,
    val createdMillis: Long,
    val type: String,
    val currency: String,
    val grossMinorUnits: Long,
    val feeMinorUnits: Long,
    val scale: Int = 2,
    val grossUsdMicros: Long,
    val feeUsdMicros: Long,
)

object FinOpsConnectorNormalizer {
    fun gcp(row: GcpBillingRow, environment: String): List<FinOpsIngestRequest> {
        val project = classifyGcpProject(row.projectId)
        val baseKey = "gcp:${row.billingAccountId}:${row.invoiceMonth}:${row.projectId}:${row.service}"
        requireCompatibleSigns(row.costMinorUnits, row.usdMicros, "GCP cost")
        val costIsCredit = row.costMinorUnits < 0L || row.usdMicros < 0L
        val cost = entry(
            id = deterministicId("gcp", row.sourceRecordId),
            source = "gcp",
            sourceRecordId = row.sourceRecordId,
            direction = if (costIsCredit) FinOpsDirection.CREDIT else FinOpsDirection.EXPENSE,
            amount = MoneyAmount(row.currency.uppercase(), magnitude(row.costMinorUnits), row.costScale),
            usdMicros = magnitude(row.usdMicros),
            incurredAt = row.usageStartMillis,
            invoiceMonth = row.invoiceMonth,
            project = project,
            environment = environment,
            vendor = "google-cloud",
            service = row.service,
            sku = row.sku,
            status = FinOpsStatus.FINALIZED,
            reconciliationKey = baseKey,
            rawFingerprint = row.toString(),
            metadata = mapOf("exportTime" to row.exportTimeMillis.toString()),
        )
        if (row.creditsMinorUnits == 0L) return listOf(FinOpsIngestRequest(cost))
        val credit = cost.copy(
            id = deterministicId("gcp-credit", row.sourceRecordId),
            sourceRecordId = "${row.sourceRecordId}:credit",
            direction = FinOpsDirection.CREDIT,
            amount = MoneyAmount(row.currency.uppercase(), magnitude(row.creditsMinorUnits), row.costScale),
            usdMicros = magnitude(row.creditsUsdMicros),
            sourceHash = sha256Hex("${row}:credit"),
        )
        return listOf(FinOpsIngestRequest(cost), FinOpsIngestRequest(credit))
    }

    fun cloudflare(row: CloudflareBillingRow, environment: String): FinOpsIngestRequest {
        val project = classifyCloudflare(row.zoneName, row.productFamily)
        requireCompatibleSigns(row.billedMinorUnits, row.billedUsdMicros, "Cloudflare charge")
        val isCredit = row.billedMinorUnits < 0L || row.billedUsdMicros < 0L
        return FinOpsIngestRequest(
            entry(
                id = deterministicId("cloudflare", row.sourceRecordId),
                source = "cloudflare",
                sourceRecordId = row.sourceRecordId,
                direction = if (isCredit) FinOpsDirection.CREDIT else FinOpsDirection.EXPENSE,
                amount = MoneyAmount(row.currency.uppercase(), magnitude(row.billedMinorUnits), row.billedScale),
                usdMicros = magnitude(row.billedUsdMicros),
                incurredAt = row.chargeStartMillis,
                invoiceMonth = invoiceMonth(row.chargeStartMillis),
                project = project,
                environment = environment,
                vendor = "cloudflare",
                service = row.productFamily,
                sku = row.metric,
                status = if (row.finalized) FinOpsStatus.FINALIZED else FinOpsStatus.ACCRUED,
                reconciliationKey = "cloudflare:${invoiceMonth(row.chargeStartMillis)}:${row.productFamily}",
                rawFingerprint = row.toString(),
            )
        )
    }

    fun stripe(row: StripeBalanceRow, environment: String, project: String = "samurai"): List<FinOpsIngestRequest> {
        requireCompatibleSigns(row.grossMinorUnits, row.grossUsdMicros, "Stripe gross amount")
        requireCompatibleSigns(row.feeMinorUnits, row.feeUsdMicros, "Stripe fee")
        val revenue = entry(
            id = deterministicId("stripe", row.sourceRecordId),
            source = "stripe",
            sourceRecordId = row.sourceRecordId,
            direction = if (
                row.grossMinorUnits < 0L || row.grossUsdMicros < 0L ||
                row.type.equals("refund", true) || row.type.equals("dispute", true)
            ) FinOpsDirection.REFUND else FinOpsDirection.REVENUE,
            amount = MoneyAmount(row.currency.uppercase(), magnitude(row.grossMinorUnits), row.scale),
            usdMicros = magnitude(row.grossUsdMicros),
            incurredAt = row.createdMillis,
            invoiceMonth = invoiceMonth(row.createdMillis),
            project = project,
            environment = environment,
            vendor = "stripe",
            service = "payments",
            status = FinOpsStatus.FINALIZED,
            reconciliationKey = "stripe:${invoiceMonth(row.createdMillis)}",
            rawFingerprint = row.toString(),
            metadata = mapOf("accountingClass" to "revenue"),
        )
        val results = mutableListOf(FinOpsIngestRequest(revenue))
        if (row.feeMinorUnits != 0L) {
            results += FinOpsIngestRequest(
                revenue.copy(
                    id = deterministicId("stripe-fee", row.sourceRecordId),
                    sourceRecordId = "${row.sourceRecordId}:fee",
                    direction = if (row.feeMinorUnits < 0L || row.feeUsdMicros < 0L) {
                        FinOpsDirection.CREDIT
                    } else {
                        FinOpsDirection.FEE
                    },
                    amount = MoneyAmount(row.currency.uppercase(), magnitude(row.feeMinorUnits), row.scale),
                    usdMicros = magnitude(row.feeUsdMicros),
                    sourceHash = sha256Hex("${row}:fee"),
                    metadata = mapOf("accountingClass" to "expense", "stripeParentId" to row.sourceRecordId),
                )
            )
        }
        return results
    }

    private fun entry(
        id: String,
        source: String,
        sourceRecordId: String,
        direction: FinOpsDirection,
        amount: MoneyAmount,
        usdMicros: Long,
        incurredAt: Long,
        invoiceMonth: String,
        project: String,
        environment: String,
        vendor: String,
        service: String,
        sku: String? = null,
        status: FinOpsStatus,
        reconciliationKey: String,
        rawFingerprint: String,
        metadata: Map<String, String> = emptyMap(),
    ) = FinOpsEntry(
        id = id,
        source = source,
        sourceRecordId = sourceRecordId,
        direction = direction,
        amount = amount,
        usdMicros = usdMicros,
        incurredAt = incurredAt,
        invoiceMonth = invoiceMonth,
        project = project,
        environment = environment,
        vendor = vendor,
        service = service,
        sku = sku,
        status = status,
        reconciliationKey = reconciliationKey,
        sourceHash = sha256Hex(rawFingerprint),
        metadata = metadata,
    )

    private fun classifyGcpProject(projectId: String): String = when {
        projectId.contains("samurai", true) || projectId.contains("teddy", true) -> "samurai"
        projectId.contains("seen", true) -> "seen"
        projectId.contains("portfolio", true) -> "portfolio"
        else -> "shared"
    }

    private fun classifyCloudflare(zoneName: String?, family: String): String = when {
        zoneName?.contains("felidai.com", true) == true -> "samurai"
        zoneName?.contains("yousef.codes", true) == true -> "portfolio"
        family.contains("AI", true) -> "samurai"
        else -> "shared"
    }
}

private fun requireCompatibleSigns(originalMinorUnits: Long, usdMicros: Long, label: String) {
    require(!((originalMinorUnits < 0L && usdMicros > 0L) || (originalMinorUnits > 0L && usdMicros < 0L))) {
        "$label has inconsistent original-currency and USD signs"
    }
}

private fun magnitude(value: Long): Long {
    require(value != Long.MIN_VALUE) { "financial amount magnitude exceeds the supported range" }
    return kotlin.math.abs(value)
}

data class GcpBillingExportConfig(
    val projectId: String,
    val dataset: String,
    val table: String,
    val maximumBytesBilled: Long,
) {
    init {
        require(projectId.matches(Regex("^[a-z][a-z0-9-]{4,61}[a-z0-9]$"))) { "invalid BigQuery project" }
        require(dataset.matches(Regex("^[A-Za-z0-9_]{1,1024}$"))) { "invalid BigQuery dataset" }
        require(table.matches(Regex("^[A-Za-z0-9_]{1,1024}$"))) { "invalid BigQuery table" }
        require(maximumBytesBilled in 1..1_000_000_000L) { "maximumBytesBilled must be positive and capped at 1 GB" }
    }

    fun partitionBoundedSql(fromDate: String, toDateExclusive: String): String =
        """SELECT billing_account_id, invoice.month, project.id, service.description, sku.description, usage_start_time, currency, cost, credits, export_time FROM `$projectId.$dataset.$table` WHERE _PARTITIONDATE >= DATE_SUB(@fromDate, INTERVAL 30 DAY) AND _PARTITIONDATE < @toDateExclusive AND DATE(usage_start_time) >= @fromDate AND DATE(usage_start_time) < @toDateExclusive"""
}
