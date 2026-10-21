package code.yousef.aws.jobs

import code.yousef.portfolio.finops.FinOpsImportRun
import code.yousef.portfolio.finops.FinOpsIngestRequest
import code.yousef.portfolio.finops.FinOpsQuery
import code.yousef.portfolio.finops.FinOpsRollupBackfillRequest
import code.yousef.portfolio.finops.FinOpsRollupBackfillService
import code.yousef.portfolio.finops.FinOpsRollupReconcileRequest
import code.yousef.portfolio.finops.FinOpsService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One job message from `PORTFOLIO_SQS_JOBS_QUEUE_URL`.
 *
 * Scheduled (EventBridge Scheduler): `{"job":"finops.gcp-billing","scheduledAt":"<ISO-8601 instant>"}`
 * with job one of [SCHEDULED_JOBS]. Idempotency key: `<job>:<scheduledAt>`.
 *
 * Asynchronous work (replacing the `portfolio-async`/`finops-ingest` Queues):
 * `{"job":"async.<type>","id":"<unique id>","payload":{...}}` with type one of [ASYNC_JOBS].
 * Idempotency key: `<job>:<id>`; the same id with a different body is rejected.
 */
data class PortfolioJobMessage(
    val job: String,
    val scheduledAt: Instant?,
    val id: String?,
    val payload: JsonObject?,
    /** SHA-256 of the canonical message body, the lease fingerprint. */
    val fingerprint: String,
) {
    val idempotencyKey: String
        get() = if (scheduledAt != null) "$job:${scheduledAt}" else "$job:${requireNotNull(id)}"

    companion object {
        const val GCP_BILLING = "finops.gcp-billing"
        const val CLOUDFLARE_BILLING = "finops.cloudflare-billing"
        const val STRIPE = "finops.stripe"
        const val RECURRING_EXPENSES = "finops.recurring-expenses"
        const val RECONCILE = "finops.reconcile"
        const val ASYNC_FINOPS_INGEST = "async.finops.ingest"
        const val ASYNC_FINOPS_IMPORT_RUN = "async.finops.import-run"
        const val ASYNC_ROLLUPS_BACKFILL = "async.finops.rollups.v2.backfill"
        const val ASYNC_ROLLUPS_RECONCILE = "async.finops.rollups.v2.reconcile"

        val SCHEDULED_JOBS = setOf(GCP_BILLING, CLOUDFLARE_BILLING, STRIPE, RECURRING_EXPENSES, RECONCILE)
        val ASYNC_JOBS = setOf(ASYNC_FINOPS_INGEST, ASYNC_FINOPS_IMPORT_RUN, ASYNC_ROLLUPS_BACKFILL, ASYNC_ROLLUPS_RECONCILE)

        private const val MAX_BODY_BYTES = 200 * 1024
        private val ASYNC_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")

        fun parse(body: String): PortfolioJobMessage {
            require(body.toByteArray(Charsets.UTF_8).size <= MAX_BODY_BYTES) { "job message is too large" }
            val json = Json.parseToJsonElement(body) as? JsonObject ?: throw IllegalArgumentException("job message must be an object")
            fun string(name: String): String? = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val job = string("job") ?: throw IllegalArgumentException("job is required")
            val fingerprint = JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(json))
            return when (job) {
                in SCHEDULED_JOBS -> {
                    require(json.keys == setOf("job", "scheduledAt")) { "scheduled job messages contain only job and scheduledAt" }
                    val scheduledAt = runCatching { Instant.parse(string("scheduledAt")) }.getOrNull()
                        ?: throw IllegalArgumentException("scheduledAt must be an ISO-8601 instant")
                    PortfolioJobMessage(job, scheduledAt, null, null, fingerprint)
                }
                in ASYNC_JOBS -> {
                    require(json.keys == setOf("job", "id", "payload")) { "async job messages contain only job, id and payload" }
                    val id = string("id")?.takeIf(ASYNC_ID::matches) ?: throw IllegalArgumentException("async job id is invalid")
                    val payload = json["payload"] as? JsonObject ?: throw IllegalArgumentException("async job payload must be an object")
                    PortfolioJobMessage(job, null, id, payload, fingerprint)
                }
                else -> throw IllegalArgumentException("unsupported job")
            }
        }
    }
}

/** Executes job messages. Every job is idempotent on replay; the consumer adds a lease on top. */
class PortfolioJobRunner(
    private val finOpsService: FinOpsService,
    private val rollupBackfillService: FinOpsRollupBackfillService?,
    private val environment: Map<String, String>,
    /** True when the configured data backend is authoritative for reconciliation (DynamoDB). */
    private val authoritativeBackend: Boolean,
    private val http: ConnectorHttp = JdkConnectorHttp(),
    private val gcpAccessTokenProvider: (() -> String)? = environment["GCP_FINOPS_WIF_CREDENTIAL_CONFIG_JSON"]
        ?.trim()?.takeIf(String::isNotEmpty)
        ?.let { config -> lazyTokenProvider { GcpAwsWorkloadIdentity.accessTokenProvider(config) } },
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(PortfolioJobRunner::class.java)

    fun run(message: PortfolioJobMessage) {
        when (message.job) {
            PortfolioJobMessage.GCP_BILLING -> runConnector { sink ->
                GcpBillingConnector(connectorEnvironment(), environment, gcpAccessTokenProvider, http, clock)
                    .run(sink, requireNotNull(message.scheduledAt))
            }
            PortfolioJobMessage.CLOUDFLARE_BILLING -> runConnector { sink ->
                CloudflareBillingConnector(
                    connectorEnvironment(),
                    environment["CLOUDFLARE_BILLING_API_TOKEN"],
                    environment["CLOUDFLARE_ACCOUNT_ID"],
                    http,
                    clock,
                ).run(sink, requireNotNull(message.scheduledAt))
            }
            PortfolioJobMessage.STRIPE -> runConnector { sink ->
                StripeBillingConnector(connectorEnvironment(), environment["STRIPE_FINOPS_READ_KEY"], http, clock)
                    .run(sink, requireNotNull(message.scheduledAt))
            }
            PortfolioJobMessage.RECURRING_EXPENSES -> materializeRecurringExpenses(requireNotNull(message.scheduledAt))
            PortfolioJobMessage.RECONCILE -> reconcileAllocations(requireNotNull(message.scheduledAt))
            PortfolioJobMessage.ASYNC_FINOPS_INGEST -> {
                val request = connectorJson.decodeFromJsonElement(FinOpsIngestRequest.serializer(), requireNotNull(message.payload))
                requireCovered(request.entry.incurredAt)
                finOpsService.ingest(request, FinOpsServiceSink.ENTRY_ACTOR)
            }
            PortfolioJobMessage.ASYNC_FINOPS_IMPORT_RUN -> {
                val run = connectorJson.decodeFromJsonElement(FinOpsImportRun.serializer(), requireNotNull(message.payload))
                require(run.fromDate >= connectorEnvironment().coverageStartDate) { "FinOps import predates configured coverage" }
                finOpsService.recordImportRun(run, FinOpsServiceSink.IMPORT_ACTOR)
            }
            PortfolioJobMessage.ASYNC_ROLLUPS_BACKFILL -> {
                val service = requireRollupBackfill()
                val request = connectorJson.decodeFromJsonElement(FinOpsRollupBackfillRequest.serializer(), requireNotNull(message.payload))
                val result = service.backfillPage(request)
                log.info(
                    "finops_rollup_backfill_page processed={} complete={} hasNext={}",
                    result.processed,
                    result.complete,
                    result.nextCursor != null,
                )
            }
            PortfolioJobMessage.ASYNC_ROLLUPS_RECONCILE -> {
                val service = requireRollupBackfill()
                val request = connectorJson.decodeFromJsonElement(FinOpsRollupReconcileRequest.serializer(), requireNotNull(message.payload))
                val report = service.reconcile(request.from, request.toExclusive)
                log.info("finops_rollup_reconciliation ready={}", report.ready)
            }
            else -> throw IllegalArgumentException("unsupported job")
        }
    }

    private fun runConnector(block: (FinOpsServiceSink) -> Unit) {
        val sink = FinOpsServiceSink(finOpsService)
        block(sink)
        log.info("finops_connector_completed ingested={} rejected={}", sink.ingested, sink.failed)
        sink.requireAllIngested()
    }

    /** Port of `materializeRecurringExpenses` in worker.ts. */
    private fun materializeRecurringExpenses(scheduledAt: Instant) {
        val coverageStart = connectorEnvironment().coverageStartMillis
        val toDateExclusive = utcTomorrowStart(scheduledAt)
        val fromDate = maxOf(toDateExclusive - 35 * 86_400_000L, coverageStart)
        if (fromDate >= toDateExclusive) {
            log.info("recurring_expense_materialization_skipped reason=coverage_not_started")
            return
        }
        val result = finOpsService.materializeRecurringExpenses(
            LocalDate.parse(isoDate(fromDate)),
            LocalDate.parse(isoDate(toDateExclusive)),
            "aws-scheduler",
        )
        log.info("recurring_expense_materialization occurrences={} inserted={}", result.occurrences, result.inserted)
    }

    /** Port of `reconcileFinOpsAllocations` in worker.ts. */
    private fun reconcileAllocations(scheduledAt: Instant) {
        connectorEnvironment().requireDev("automatic FinOps reconciliation")
        val authoritative = authoritativeBackend || environment["FIRESTORE_WRITE_MODE"] == "target"
        if (!authoritative) {
            log.info("FinOps allocation reconciliation skipped: the configured backend is not authoritative")
            return
        }
        if (environment["FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY"] == "true") {
            log.info("FinOps allocation reconciliation skipped: projection gate is already enabled")
            return
        }
        val to = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        var cursor: String? = null
        var processed = 0
        var residualAllocations = 0
        repeat(RECONCILE_PAGE_BOUND) {
            val result = finOpsService.backfillAllocationResiduals(
                FinOpsQuery(from = 0L, toExclusive = to, limit = 500, cursor = cursor),
                "aws-reconciler",
            )
            processed += result.processed
            residualAllocations += result.residualAllocations
            val next = result.nextCursor
            if (next == null) {
                val projections = finOpsService.reconcileAllocationEntryProjections("aws-reconciler")
                check(projections.ready) { "FinOps projection reconciliation did not prove exact parity" }
                log.info(
                    "FinOps allocation reconciliation completed processed={} residualAllocations={} projectionCount={}",
                    processed,
                    residualAllocations,
                    projections.actualCount,
                )
                return
            }
            check(result.processed != 0 && next != cursor) { "FinOps allocation reconciliation cursor did not advance" }
            cursor = next
        }
        throw IllegalStateException("FinOps allocation reconciliation exceeded its $RECONCILE_PAGE_BOUND-page safety bound")
    }

    private fun requireCovered(incurredAt: Long) {
        require(incurredAt >= connectorEnvironment().coverageStartMillis) { "FinOps entry predates configured coverage" }
    }

    private fun requireRollupBackfill(): FinOpsRollupBackfillService {
        check(environment["FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED"] == "true" && rollupBackfillService != null) {
            "FinOps rollup backfill execution is disabled"
        }
        return rollupBackfillService
    }

    private fun connectorEnvironment(): FinOpsConnectorEnvironment = FinOpsConnectorEnvironment.fromEnvironment(environment)

    private companion object {
        const val RECONCILE_PAGE_BOUND = 25

        fun lazyTokenProvider(factory: () -> () -> String): () -> String {
            val provider by lazy(LazyThreadSafetyMode.SYNCHRONIZED, factory)
            return { provider() }
        }
    }
}
