package code.yousef.portfolio.finops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinOpsConnectorNormalizerTest {
    @Test
    fun `GCP export query bounds ingestion partition and usage dates`() {
        val sql = GcpBillingExportConfig(
            projectId = "felidai-dev",
            dataset = "felidai_billing_export",
            table = "gcp_billing_export_v1_01D152_BA0DA4_4CB0D6",
            maximumBytesBilled = 1_000_000_000L,
        ).partitionBoundedSql("2026-08-01", "2026-08-23")

        assertTrue(sql.contains("_PARTITIONDATE >= DATE_SUB(@fromDate, INTERVAL 30 DAY)"))
        assertTrue(sql.contains("_PARTITIONDATE < @toDateExclusive"))
        assertTrue(sql.contains("DATE(usage_start_time) >= @fromDate"))
        assertTrue(sql.contains("DATE(usage_start_time) < @toDateExclusive"))
    }

    @Test
    fun `cloudflare subscription is accrued until finalized and classified by zone`() {
        val request = FinOpsConnectorNormalizer.cloudflare(
            CloudflareBillingRow(
                sourceRecordId = "subscription-1:2027-01",
                chargeStartMillis = 1_800_000_000_000L,
                productFamily = "Workers",
                metric = "paid-plan",
                zoneName = "samurai.felidai.com",
                billedMinorUnits = 500,
                billedUsdMicros = 5_000_000L,
                finalized = false,
            ),
            environment = "dev",
        )
        assertEquals("samurai", request.entry.project)
        assertEquals("dev", request.entry.environment)
        assertEquals(FinOpsStatus.ACCRUED, request.entry.status)
        assertEquals(5_000_000L, request.entry.usdMicros)
    }

    @Test
    fun `bigquery query is partition bounded and byte capped`() {
        val config = GcpBillingExportConfig("billing-admin-123", "billing", "gcp_billing_export_v1_123", 50_000_000)
        val sql = config.partitionBoundedSql("2027-01-01", "2027-02-01")
        assertEquals(true, sql.contains("DATE(usage_start_time) >= @fromDate"))
        assertEquals(true, sql.contains("DATE(usage_start_time) < @toDateExclusive"))
    }

    @Test
    fun `gcp credit retains invoice currency and usd value`() {
        val rows = FinOpsConnectorNormalizer.gcp(
            GcpBillingRow(
                billingAccountId = "billing",
                sourceRecordId = "line-1",
                usageStartMillis = 1_800_000_000_000L,
                invoiceMonth = "2027-01",
                projectId = "samurai-prod",
                service = "Firestore",
                sku = "reads",
                currency = "USD",
                costMinorUnits = 500,
                costScale = 2,
                creditsMinorUnits = -125,
                creditsUsdMicros = -1_250_000,
                usdMicros = 5_000_000,
                exportTimeMillis = 1_800_000_001_000L,
            ),
            environment = "dev",
        )
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.entry.environment == "dev" })
        assertEquals(1_250_000L, rows.last().entry.usdMicros)
        assertEquals(FinOpsDirection.CREDIT, rows.last().entry.direction)
    }

    @Test
    fun `negative vendor corrections become positive-magnitude credits`() {
        val gcp = FinOpsConnectorNormalizer.gcp(
            GcpBillingRow(
                billingAccountId = "billing",
                sourceRecordId = "correction-1",
                usageStartMillis = 1_800_000_000_000L,
                invoiceMonth = "2027-01",
                projectId = "portfolio-prod",
                service = "Cloud Run",
                sku = "correction",
                currency = "USD",
                costMinorUnits = -250,
                costScale = 2,
                usdMicros = -2_500_000L,
                exportTimeMillis = 1_800_000_001_000L,
            ),
            environment = "dev",
        ).single().entry
        val cloudflare = FinOpsConnectorNormalizer.cloudflare(
            CloudflareBillingRow(
                sourceRecordId = "correction-2",
                chargeStartMillis = 1_800_000_000_000L,
                productFamily = "Workers",
                metric = "correction",
                currency = "USD",
                billedMinorUnits = -300,
                billedUsdMicros = -3_000_000L,
                finalized = true,
            ),
            environment = "dev",
        ).entry

        assertEquals(FinOpsDirection.CREDIT, gcp.direction)
        assertEquals(2_500_000L, gcp.usdMicros)
        assertEquals(FinOpsDirection.CREDIT, cloudflare.direction)
        assertEquals(3_000_000L, cloudflare.usdMicros)
    }

    @Test
    fun `stripe refund and fee reversal reduce revenue and spend`() {
        val entries = FinOpsConnectorNormalizer.stripe(
            StripeBalanceRow(
                sourceRecordId = "txn_refund_1",
                createdMillis = 1_800_000_000_000L,
                type = "refund",
                currency = "USD",
                grossMinorUnits = -2_500,
                feeMinorUnits = -103,
                grossUsdMicros = -25_000_000L,
                feeUsdMicros = -1_030_000L,
            ),
            environment = "dev",
        ).map(FinOpsIngestRequest::entry)

        assertEquals(FinOpsDirection.REFUND, entries[0].direction)
        assertTrue(entries.all { it.environment == "dev" })
        assertEquals(25_000_000L, entries[0].usdMicros)
        assertEquals(FinOpsDirection.CREDIT, entries[1].direction)
        assertEquals(1_030_000L, entries[1].usdMicros)
    }

    @Test
    fun `stripe signed currency values must agree`() {
        assertFailsWith<IllegalArgumentException> {
            FinOpsConnectorNormalizer.stripe(
                StripeBalanceRow(
                    sourceRecordId = "txn_bad_sign",
                    createdMillis = 1_800_000_000_000L,
                    type = "refund",
                    currency = "USD",
                    grossMinorUnits = -100,
                    feeMinorUnits = 0,
                    grossUsdMicros = 1_000_000L,
                    feeUsdMicros = 0,
                ),
                environment = "dev",
            )
        }
    }
}
