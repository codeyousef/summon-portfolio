package code.yousef.portfolio.finops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FinOpsRollupBackfillServiceTest {
    @Test
    fun `backfill is bounded cursor based and idempotent`() {
        val source = InMemoryFinOpsRepository()
        val target = InMemoryFinOpsRepository()
        val dayStart = 1_799_971_200_000L
        val first = rollupEntry("entry:first", dayStart + 1_000L, 1_000_000L)
        val second = rollupEntry("entry:second", dayStart + 100_000L, 2_000_000L)
        listOf(first, second).forEach { entry ->
            source.putEntry(entry)
            source.ensureDailyRollups(entry, emptyList())
            source.ensureUnallocatedRollup(entry, emptyList())
        }
        val service = FinOpsRollupBackfillService(source, target)
        val request = FinOpsRollupBackfillRequest(
            from = dayStart,
            toExclusive = dayStart + 86_400_000L,
            limit = 1,
        )

        val firstPage = service.backfillPage(request)
        assertEquals(1, firstPage.processed)
        assertEquals(first.id, firstPage.firstEntryId)
        assertFalse(firstPage.complete)
        assertNotNull(firstPage.nextCursor)
        assertFalse(service.reconcile(request.from, request.toExclusive).ready)

        val secondPage = service.backfillPage(request.copy(cursor = firstPage.nextCursor))
        assertEquals(second.id, secondPage.lastEntryId)
        assertTrue(secondPage.complete)
        assertTrue(service.reconcile(request.from, request.toExclusive).ready)

        service.backfillPage(request)
        assertTrue(service.reconcile(request.from, request.toExclusive).ready)
    }

    @Test
    fun `parity reports unexpected and mismatched canonical groups`() {
        val source = InMemoryFinOpsRepository()
        val target = InMemoryFinOpsRepository()
        val dayStart = 1_799_971_200_000L
        val expected = rollupEntry("entry:expected", dayStart + 1_000L, 1_000_000L)
        val mismatched = expected.copy(id = "entry:mismatched", usdMicros = 2_000_000L, sourceHash = sha256Hex("mismatched"))
        val unexpected = expected.copy(
            id = "entry:unexpected",
            incurredAt = expected.incurredAt + 86_400_000L,
            sourceHash = sha256Hex("unexpected"),
        )
        source.ensureDailyRollups(expected, emptyList())
        target.ensureDailyRollups(mismatched, emptyList())
        target.ensureDailyRollups(unexpected, emptyList())
        val report = FinOpsRollupBackfillService(source, target).reconcile(
            dayStart,
            dayStart + 2 * 86_400_000L,
        )

        assertFalse(report.ready)
        assertEquals(0L, report.daily.missingCount)
        assertEquals(1L, report.daily.unexpectedCount)
        assertEquals(1L, report.daily.mismatchedCount)
        assertEquals(1L, report.reconciliation.mismatchedCount)
    }
}

private fun rollupEntry(id: String, incurredAt: Long, usdMicros: Long): FinOpsEntry = FinOpsEntry(
    id = id,
    source = "gcp",
    sourceRecordId = id,
    direction = FinOpsDirection.EXPENSE,
    amount = MoneyAmount("USD", usdMicros, USD_MICRO_SCALE),
    usdMicros = usdMicros,
    incurredAt = incurredAt,
    invoiceMonth = "2027-01",
    project = "portfolio",
    environment = "dev",
    vendor = "google-cloud",
    service = "cloud-run",
    status = FinOpsStatus.FINALIZED,
    reconciliationKey = "gcp:2027-01",
    sourceHash = sha256Hex(id),
    recordedAt = incurredAt,
)
