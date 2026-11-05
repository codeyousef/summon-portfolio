package code.yousef.portfolio.finops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinOpsAggregatePerformanceTest {
    @Test
    fun `ten thousand precomputed rollups keep aggregate p95 below three hundred milliseconds`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val from = 1_798_761_600_000L // 2027-01-01T00:00:00Z
        val dayMillis = 86_400_000L
        val toExclusive = from + (31L * dayMillis)

        repeat(10_000) { index ->
            val sourceRecordId = "load:$index"
            repository.ensureDailyRollups(
                entry = FinOpsEntry(
                    id = deterministicId("performance", sourceRecordId),
                    source = "gcp",
                    sourceRecordId = sourceRecordId,
                    direction = FinOpsDirection.EXPENSE,
                    amount = MoneyAmount("USD", 1L, USD_MICRO_SCALE),
                    usdMicros = 1L,
                    incurredAt = from + ((index % 31) * dayMillis) + 1L,
                    invoiceMonth = "2027-01",
                    project = "samurai",
                    environment = "dev",
                    vendor = "google-cloud",
                    service = "load-service-$index",
                    status = FinOpsStatus.FINALIZED,
                    reconciliationKey = "gcp:2027-01:$index",
                    sourceHash = sha256Hex(sourceRecordId),
                ),
                allocations = emptyList(),
            )
        }

        val query = FinOpsQuery(from = from, toExclusive = toExclusive, limit = 500)
        repeat(10) { service.summary(query) }
        val samples = List(40) {
            val startedAt = System.nanoTime()
            val summary = service.summary(query)
            assertEquals(10_000L, summary.finalizedSpendUsdMicros)
            System.nanoTime() - startedAt
        }.sorted()
        val p95Nanos = samples[((samples.size * 95 + 99) / 100) - 1]
        println("finops.aggregate.rollups=10000 samples=${samples.size} p95_ms=${p95Nanos / 1_000_000.0}")

        assertTrue(
            p95Nanos < 300_000_000L,
            "10,000-rollup aggregate p95 was ${p95Nanos / 1_000_000.0} ms; expected less than 300 ms",
        )
    }
}
