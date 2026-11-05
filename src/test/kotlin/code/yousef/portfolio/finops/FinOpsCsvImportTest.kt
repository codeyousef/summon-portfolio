package code.yousef.portfolio.finops

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinOpsCsvImportTest {
    @Test
    fun `CSV import validates all rows then ingests idempotently`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val csv = """
            source_record_id,direction,amount,currency,usd_micros,incurred_at,project,environment,vendor,service,sku,status,reconciliation_key
            domain-2027,expense,12.34,USD,12340000,2027-01-10T00:00:00Z,portfolio,prod,registrar,"domain, renewal",annual,finalized,registrar:2027-01
            software-2027,fee,5,USD,5000000,1800000000000,shared,prod,vendor,software,,accrued,vendor:2027-01
        """.trimIndent()

        assertEquals(FinOpsCsvImportResult(2, 2, 0), service.importManualCsv(csv, "owner"))
        assertEquals(FinOpsCsvImportResult(2, 0, 2), service.importManualCsv(csv, "owner"))
        assertEquals(17_340_000L, service.summary(FinOpsQuery(1_700_000_000_000L, 1_900_000_000_000L)).finalizedSpendUsdMicros +
            service.summary(FinOpsQuery(1_700_000_000_000L, 1_900_000_000_000L)).accruedSpendUsdMicros)
    }

    @Test
    fun `CSV parser handles escaped quotes and rejects malformed or partial rows before writes`() {
        val header = "source_record_id,direction,amount,currency,usd_micros,incurred_at,project,environment,vendor,service,status,reconciliation_key"
        val parsed = parseManualFinOpsCsv(
            "$header\nrow-1,expense,1.00,USD,1000000,2027-01-01T00:00:00Z,portfolio,dev,vendor,\"support \"\"plus\"\" care\",finalized,vendor:2027-01",
        )
        assertEquals("support \"plus\" care", parsed.single().service)

        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val malformed = "$header\ngood,expense,1,USD,1000000,2027-01-01T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01\nbad,expense"
        assertFailsWith<IllegalArgumentException> { service.importManualCsv(malformed, "owner") }
        assertEquals(0, service.entries(FinOpsQuery(1_700_000_000_000L, 1_900_000_000_000L)).entries.size)
        assertFailsWith<IllegalArgumentException> { parseManualFinOpsCsv("$header\n\"unterminated") }
    }

    @Test
    fun `CSV validates financial magnitudes for every row before writing any entry`() {
        val header = "source_record_id,direction,amount,currency,usd_micros,incurred_at,project,environment,vendor,service,status,reconciliation_key"
        val csv = "$header\ngood,expense,1,USD,1000000,2027-01-01T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01\n" +
            "negative,credit,-1,USD,-1000000,2027-01-02T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01"
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())

        assertFailsWith<IllegalArgumentException> { service.importManualCsv(csv, "owner") }
        assertEquals(0, service.entries(FinOpsQuery(1_700_000_000_000L, 1_900_000_000_000L)).entries.size)
    }

    @Test
    fun `CSV validates future-only coverage for every row before writing any entry`() {
        val cutoff = java.time.Instant.parse("2026-08-24T00:00:00Z").toEpochMilli()
        val header = "source_record_id,direction,amount,currency,usd_micros,incurred_at,project,environment,vendor,service,status,reconciliation_key"
        val csv = "$header\nfuture,expense,1,USD,1000000,2026-08-24T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:future\n" +
            "historical,expense,2,USD,2000000,2026-08-23T23:59:59Z,portfolio,dev,vendor,service,finalized,vendor:historical"
        val service = FinOpsService(
            InMemoryFinOpsRepository(),
            coverageSources = emptySet(),
            coverageStartAt = cutoff,
        )

        assertFailsWith<IllegalArgumentException> { service.importManualCsv(csv, "owner") }
        assertEquals(0, service.entries(FinOpsQuery(cutoff, cutoff + 86_400_000L)).entries.size)
    }

    @Test
    fun `CSV replay completes after a transient partial import without duplicate spend`() {
        val backing = InMemoryFinOpsRepository()
        val failSecondRowOnce = AtomicBoolean(true)
        val repository = object : FinOpsRepository by backing {
            override fun putEntry(entry: FinOpsEntry): FinOpsWriteResult {
                if (entry.sourceRecordId == "row-2" && failSecondRowOnce.compareAndSet(true, false)) {
                    throw IllegalStateException("simulated transient datastore failure")
                }
                return backing.putEntry(entry)
            }
        }
        val service = FinOpsService(repository, coverageSources = emptySet())
        val csv = """
            source_record_id,direction,amount,currency,usd_micros,incurred_at,project,environment,vendor,service,status,reconciliation_key
            row-1,expense,1,USD,1000000,2027-01-01T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01
            row-2,expense,2,USD,2000000,2027-01-02T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01
            row-3,expense,3,USD,3000000,2027-01-03T00:00:00Z,portfolio,dev,vendor,service,finalized,vendor:2027-01
        """.trimIndent()
        val query = FinOpsQuery(1_700_000_000_000L, 1_900_000_000_000L, limit = 500)

        assertFailsWith<IllegalStateException> { service.importManualCsv(csv, "owner") }
        assertEquals(1, service.entries(query).entries.size)

        assertEquals(FinOpsCsvImportResult(3, 2, 1), service.importManualCsv(csv, "owner"))
        assertEquals(3, service.entries(query).entries.size)
        assertEquals(6_000_000L, service.summary(query).finalizedSpendUsdMicros)
    }
}
