package code.yousef.aws.jobs

import code.yousef.portfolio.finops.FinOpsQuery
import code.yousef.portfolio.finops.FinOpsRecurrenceCadence
import code.yousef.portfolio.finops.FinOpsRecurringExpense
import code.yousef.portfolio.finops.FinOpsRollupBackfillService
import code.yousef.portfolio.finops.FinOpsService
import code.yousef.portfolio.finops.InMemoryFinOpsRepository
import code.yousef.portfolio.finops.MoneyAmount
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortfolioJobRunnerTest {
    private val repository = InMemoryFinOpsRepository()
    private val coverage = LocalDate.parse("2026-09-01").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val service = FinOpsService(repository, coverageStartAt = coverage)
    private val environment = mutableMapOf("PORTFOLIO_ENV" to "dev", "FINOPS_COVERAGE_START_DATE" to "2026-09-01")
    private val runner = PortfolioJobRunner(
        finOpsService = service,
        rollupBackfillService = FinOpsRollupBackfillService(repository, InMemoryFinOpsRepository()),
        environment = environment,
        authoritativeBackend = true,
        http = { _, url, _, _, _ -> error("unexpected network call to $url") },
        gcpAccessTokenProvider = null,
    )

    private fun run(body: String) = runner.run(PortfolioJobMessage.parse(body))

    @Test
    fun `recurring expenses materialize the trailing window idempotently`() {
        service.recordRecurringExpense(
            FinOpsRecurringExpense(
                id = "recurring:domain",
                amount = MoneyAmount("USD", 1_200, 2),
                usdMicros = 12_000_000,
                project = "portfolio",
                environment = "prod",
                vendor = "registrar",
                service = "domains",
                cadence = FinOpsRecurrenceCadence.MONTHLY,
                startDate = "2026-01-15",
                sourceHash = sha("recurring:domain"),
            ),
            "test",
        )
        run("""{"job":"finops.recurring-expenses","scheduledAt":"2026-10-21T05:31:00Z"}""")
        run("""{"job":"finops.recurring-expenses","scheduledAt":"2026-10-21T05:31:00Z"}""")
        val entries = service.entries(FinOpsQuery(from = coverage, toExclusive = coverage + 90L * 86_400_000L)).entries
        assertEquals(listOf("2026-10-15"), entries.map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.incurredAt), ZoneOffset.UTC).toString() }.sorted())
    }

    @Test
    fun `async ingest and import-run messages apply through the FinOps service`() {
        val entryId = "cloudflare:${"a".repeat(40)}"
        run(
            """{"job":"async.finops.ingest","id":"e1","payload":{"entry":{"id":"$entryId","source":"cloudflare",""" +
                """"sourceRecordId":"usage:x","direction":"EXPENSE","amount":{"currency":"USD","minorUnits":5,"scale":6},""" +
                """"usdMicros":5,"incurredAt":1792000000000,"invoiceMonth":"2026-10","project":"shared","environment":"dev",""" +
                """"vendor":"cloudflare","service":"Workers","sku":"x","status":"ACCRUED","reconciliationKey":"cloudflare:2026-10:account",""" +
                """"sourceHash":"${sha("x")}","metadata":{}},"allocations":[]}}""",
        )
        assertEquals(entryId, repository.getEntry(entryId)?.id)
        run(
            """{"job":"async.finops.import-run","id":"r1","payload":{"id":"import:cloudflare:2026-10-22:0123456789abcdef",""" +
                """"source":"cloudflare","fromDate":"2026-10-01","toDateExclusive":"2026-10-22","sourceRecords":1,"entries":1,""" +
                """"sourceHash":"${sha("run")}","status":"succeeded","completedAt":1}}""",
        )
        assertEquals(1, repository.listImportRuns().size)
        assertFailsWith<IllegalArgumentException> {
            run(
                """{"job":"async.finops.import-run","id":"r2","payload":{"id":"import:x:2026-08-02:0123456789abcdef","source":"x",""" +
                    """"fromDate":"2026-08-01","toDateExclusive":"2026-08-02","sourceRecords":0,"entries":0,""" +
                    """"sourceHash":"${sha("old")}","status":"succeeded","completedAt":1}}""",
            )
        }
    }

    @Test
    fun `reconciliation and connectors keep their production and configuration gates`() {
        run("""{"job":"finops.reconcile","scheduledAt":"2026-10-21T05:51:00Z"}""")
        // Unconfigured connectors skip without network access.
        run("""{"job":"finops.gcp-billing","scheduledAt":"2026-10-21T05:01:00Z"}""")
        run("""{"job":"finops.cloudflare-billing","scheduledAt":"2026-10-21T05:11:00Z"}""")
        run("""{"job":"finops.stripe","scheduledAt":"2026-10-21T05:21:00Z"}""")
        environment["PORTFOLIO_ENV"] = "prod"
        assertFailsWith<IllegalStateException> { run("""{"job":"finops.stripe","scheduledAt":"2026-10-21T05:21:00Z"}""") }
        assertFailsWith<IllegalStateException> { run("""{"job":"finops.reconcile","scheduledAt":"2026-10-21T05:51:00Z"}""") }
        assertFailsWith<IllegalStateException> {
            run("""{"job":"async.finops.rollups.v2.reconcile","id":"x","payload":{"from":0,"toExclusive":86400000}}""")
        }
        environment["FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED"] = "true"
        run("""{"job":"async.finops.rollups.v2.reconcile","id":"x","payload":{"from":0,"toExclusive":86400000}}""")
        assertTrue(true)
    }

    private fun sha(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
