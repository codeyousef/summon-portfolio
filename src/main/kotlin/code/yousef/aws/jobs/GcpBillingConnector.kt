package code.yousef.aws.jobs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

/**
 * Kotlin port of cloudflare/portfolio-edge/src/gcp-billing.ts: reads the BigQuery standard
 * billing export for the rolling 35-day window and ingests one immutable FinOps entry per
 * cost or credit line. Ids, source hashes and import-run receipts are byte-identical to the
 * Worker implementation.
 */
class GcpBillingConnector(
    private val environment: FinOpsConnectorEnvironment,
    private val config: Map<String, String>,
    private val accessTokenProvider: (() -> String)?,
    private val http: ConnectorHttp = JdkConnectorHttp(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(GcpBillingConnector::class.java)

    data class BillingConfig(
        val projectId: String,
        val dataset: String,
        val table: String,
        val location: String,
        val maximumBytesBilled: Long,
    )

    /** [windowAt] anchors the billing window (the scheduled time), keeping retries deterministic. */
    fun run(sink: FinOpsIngestSink, windowAt: Instant = clock.instant()) {
        environment.requireDev("GCP billing ingestion")
        val (fromDate, toDateExclusive) = billingWindow(windowAt, environment.coverageStartDate)
        var sourceRecords = 0L
        var entries = 0L
        try {
            val billingConfig = try {
                readBillingConfig(config)
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("configuration", error)
            }
            if (billingConfig == null) {
                log.info("gcp_billing_import_skipped reason=not_configured")
                return
            }
            if (fromDate >= toDateExclusive) {
                log.info("gcp_billing_import_skipped reason=coverage_not_started")
                return
            }
            val token = try {
                accessTokenProvider?.invoke()
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("workload_identity", error)
            } ?: throw ConnectorFailure("workload_identity", "GCP billing configuration requires a complete WIF identity")
            val rows = queryBillingRows(billingConfig, token, fromDate, toDateExclusive)
            sourceRecords = rows.size.toLong()
            val sourceRecordIds = ArrayList<String>(rows.size)
            var partial = false
            for (row in rows) {
                sourceRecordIds += row.getValue("source_record_id").lowercase()
                val payload = try {
                    normalizeGcpBillingLine(row, environment.portfolioEnvironment)
                } catch (_: Exception) {
                    partial = true
                    log.error("gcp_billing_record_rejected scope=billing_export_line")
                    continue
                }
                sink.ingest(payload)
                entries++
            }
            val coverage = billingExportCoverage(rows, toDateExclusive)
            if (!coverage.complete) {
                partial = true
                log.error(
                    "gcp_billing_export_freshness_gap latestExportAt={} minimumExpectedAt={}",
                    coverage.latestExportAt?.let { Instant.ofEpochMilli(it).toString() } ?: "missing",
                    Instant.ofEpochMilli(coverage.minimumExpectedAt),
                )
            }
            val sourceHash = JsCanonicalJson.sha256Hex(
                JsCanonicalJson.stringify(
                    buildJsonObject {
                        put("records", JsonArray(sourceRecordIds.jsSorted().map(::JsonPrimitive)))
                        put("exportCoverage", coverage.toJson())
                    },
                ),
            )
            try {
                sink.importRun(
                    importRunPayload(
                        source = "gcp",
                        fromDate = fromDate,
                        toDateExclusive = toDateExclusive,
                        sourceRecords = sourceRecords,
                        entries = entries,
                        sourceHash = sourceHash,
                        status = if (partial) "partial" else "succeeded",
                        completedAt = clock.millis(),
                        failureCode = null,
                    ),
                )
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("queue_delivery", error)
            }
        } catch (error: Exception) {
            val failureCode = ConnectorFailure.codeOf(error)
            log.error("gcp_billing_import_failed failureCode={}", failureCode)
            publishFailedImportRun(sink, "gcp", fromDate, toDateExclusive, sourceRecords, entries, clock.millis(), failureCode)
            throw error
        }
    }

    data class BillingExportCoverage(val complete: Boolean, val latestExportAt: Long?, val minimumExpectedAt: Long) {
        fun toJson(): JsonObject = buildJsonObject {
            put("complete", complete)
            put("latestExportAt", latestExportAt?.let(::JsonPrimitive) ?: JsonNull)
            put("minimumExpectedAt", minimumExpectedAt)
        }
    }

    private fun queryBillingRows(
        config: BillingConfig,
        token: String,
        fromDate: String,
        toDateExclusive: String,
    ): List<Map<String, String>> {
        val table = "`${config.projectId}.${config.dataset}.${config.table}`"
        val request = buildJsonObject {
            put("query", GCP_BILLING_SQL.replace("__TABLE__", table))
            put("useLegacySql", false)
            put("useQueryCache", false)
            put("maximumBytesBilled", config.maximumBytesBilled.toString())
            put("maxResults", QUERY_PAGE_SIZE)
            put("timeoutMs", 10_000)
            put("location", config.location)
            put("parameterMode", "NAMED")
            putJsonArray("queryParameters") {
                add(dateParameter("fromDate", fromDate))
                add(dateParameter("toDateExclusive", toDateExclusive))
            }
        }
        var page = bigQueryFetch(
            "$BIGQUERY_ROOT/projects/${encode(config.projectId)}/queries",
            token,
            "POST",
            request.toString(),
        )
        val jobId = (page["jobReference"] as? JsonObject)?.get("jobId")?.let { (it as? JsonPrimitive)?.content }
            ?: throw ConnectorFailure("bigquery_schema", "BigQuery response is missing its job reference")
        val rows = ArrayList<Map<String, String>>()
        repeat(MAX_QUERY_PAGES) {
            assertQuerySuccess(page, config.maximumBytesBilled)
            if ((page["jobComplete"] as? JsonPrimitive)?.content != "true") {
                page = queryResults(config, token, jobId, null)
                return@repeat
            }
            rows += decodeRows(page)
            if (rows.size > GCP_BILLING_SOURCE_ROW_LIMIT) {
                throw ConnectorFailure("query_limit", "GCP billing query exceeded $GCP_BILLING_SOURCE_ROW_LIMIT rows")
            }
            val pageToken = (page["pageToken"] as? JsonPrimitive)?.content?.takeIf(String::isNotEmpty) ?: return rows
            page = queryResults(config, token, jobId, pageToken)
        }
        throw ConnectorFailure("query_limit", "GCP billing query exceeded its page/poll safety bound")
    }

    private fun queryResults(config: BillingConfig, token: String, jobId: String, pageToken: String?): JsonObject {
        val query = buildList {
            add("location=${encode(config.location)}")
            add("maxResults=$QUERY_PAGE_SIZE")
            add("timeoutMs=10000")
            pageToken?.let { add("pageToken=${encode(it)}") }
        }.joinToString("&")
        return bigQueryFetch(
            "$BIGQUERY_ROOT/projects/${encode(config.projectId)}/queries/${encode(jobId)}?$query",
            token,
            "GET",
            null,
        )
    }

    private fun bigQueryFetch(url: String, token: String, method: String, body: String?): JsonObject {
        val headers = buildMap {
            put("authorization", "Bearer $token")
            put("accept", "application/json")
            if (body != null) put("content-type", "application/json")
        }
        val response = http.send(method, url, headers, body, 10 * 1024 * 1024)
        if (response.body === JdkConnectorHttp.OVERSIZED) {
            throw ConnectorFailure("query_limit", "BigQuery response exceeds 10 MiB")
        }
        val decoded = try {
            parseBoundedJson(response, "bigquery_schema")
        } catch (_: ConnectorFailure) {
            throw ConnectorFailure("bigquery_schema", "BigQuery response is not JSON")
        }
        if (response.status !in 200..299) {
            val code = if (response.status == 401 || response.status == 403) "bigquery_authorization" else "bigquery_query"
            throw ConnectorFailure(code, "BigQuery request failed with HTTP ${response.status}")
        }
        return decoded as? JsonObject ?: throw ConnectorFailure("bigquery_schema", "BigQuery response is not an object")
    }

    private fun assertQuerySuccess(response: JsonObject, maximumBytesBilled: Long) {
        val errors = response["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) {
            val reason = ((errors.first() as? JsonObject)?.get("reason") as? JsonPrimitive)?.content ?: "unknown"
            throw ConnectorFailure("bigquery_query", "BigQuery job failed: $reason")
        }
        response["totalBytesProcessed"]?.let { value ->
            val bytes = (value as? JsonPrimitive)?.content?.toLongOrNull()
            if (bytes == null || bytes < 0 || bytes > maximumBytesBilled || bytes > MAX_SAFE_INTEGER) {
                throw ConnectorFailure("query_limit", "BigQuery processed bytes exceeded its configured cap")
            }
        }
    }

    private fun decodeRows(response: JsonObject): List<Map<String, String>> {
        val names = ((response["schema"] as? JsonObject)?.get("fields") as? JsonArray)
            ?.map { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.content ?: "" }
            .orEmpty()
        if (names != REQUIRED_COLUMNS) throw ConnectorFailure("bigquery_schema", "unexpected GCP billing query schema")
        val rows = response["rows"] as? JsonArray ?: return emptyList()
        return rows.map { row ->
            val cells = (row as? JsonObject)?.get("f") as? JsonArray
            if (cells == null || cells.size != names.size) {
                throw ConnectorFailure("bigquery_schema", "invalid GCP billing row shape")
            }
            names.indices.associateTo(LinkedHashMap()) { index ->
                val value = ((cells[index] as? JsonObject)?.get("v") as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw ConnectorFailure("bigquery_schema", "GCP billing field ${names[index]} is not a string")
                names[index] to value
            }
        }
    }

    companion object {
        private const val BIGQUERY_ROOT = "https://bigquery.googleapis.com/bigquery/v2"
        private const val MAX_QUERY_PAGES = 100
        const val GCP_BILLING_SOURCE_ROW_LIMIT = 100_000
        private const val QUERY_PAGE_SIZE = 1_000
        private const val BILLING_EXPORT_MAX_LAG_DAYS = 3
        private const val DAY_MS = 86_400_000L
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

        val REQUIRED_COLUMNS = listOf(
            "source_record_id", "billing_account_id", "invoice_month", "project_id", "service", "sku",
            "incurred_at", "export_time", "currency", "signed_original_micros", "signed_usd_micros",
            "line_kind", "cost_type",
        )

        /** Identical to `GCP_BILLING_SQL` in gcp-billing.ts. */
        val GCP_BILLING_SQL: String = """WITH base AS (
  SELECT
    billing_account_id,
    invoice.month AS invoice_month,
    COALESCE(project.id, 'unattributed') AS project_id,
    service.description AS service,
    sku.description AS sku,
    FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E3SZ', usage_start_time, 'UTC') AS incurred_at,
    FORMAT_TIMESTAMP('%Y-%m-%dT%H:%M:%E3SZ', export_time, 'UTC') AS export_time,
    currency,
    currency_conversion_rate,
    cost,
    credits,
    cost_type,
    adjustment_info
  FROM __TABLE__
  -- Standard billing export partitions follow export time. A one-day buffer
  -- covers timezone edges without scanning historical pre-coverage partitions;
  -- the rolling usage window still replays late corrections idempotently.
  WHERE _PARTITIONDATE >= DATE_SUB(@fromDate, INTERVAL 1 DAY)
    AND _PARTITIONDATE < @toDateExclusive
    AND DATE(usage_start_time) >= @fromDate
    AND DATE(usage_start_time) < @toDateExclusive
), cost_lines_unfiltered AS (
  SELECT
    TO_HEX(SHA256(TO_JSON_STRING(STRUCT('cost' AS component, billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, cost, cost_type, adjustment_info)))) AS source_record_id,
    billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, currency,
    CAST(ROUND(cost * 1000000) AS INT64) AS signed_original_micros,
    CAST(ROUND(SAFE_DIVIDE(cost, COALESCE(NULLIF(currency_conversion_rate, 0), IF(currency = 'USD', 1, NULL))) * 1000000) AS INT64) AS signed_usd_micros,
    'cost' AS line_kind,
    COALESCE(cost_type, 'regular') AS cost_type
  FROM base
  WHERE cost != 0
), cost_lines AS (
  SELECT *
  FROM cost_lines_unfiltered
  WHERE signed_original_micros != 0
    OR signed_usd_micros != 0
    OR signed_original_micros IS NULL
    OR signed_usd_micros IS NULL
), credit_lines_unfiltered AS (
  SELECT
    TO_HEX(SHA256(TO_JSON_STRING(STRUCT('credit' AS component, billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, credit_offset, credit.id, credit.type, credit.name, credit.amount)))) AS source_record_id,
    billing_account_id, invoice_month, project_id, service, sku, incurred_at, export_time, currency,
    CAST(ROUND(credit.amount * 1000000) AS INT64) AS signed_original_micros,
    CAST(ROUND(SAFE_DIVIDE(credit.amount, COALESCE(NULLIF(currency_conversion_rate, 0), IF(currency = 'USD', 1, NULL))) * 1000000) AS INT64) AS signed_usd_micros,
    'credit' AS line_kind,
    'credit' AS cost_type
  FROM base, UNNEST(credits) AS credit WITH OFFSET AS credit_offset
  WHERE credit.amount != 0
), credit_lines AS (
  SELECT *
  FROM credit_lines_unfiltered
  WHERE signed_original_micros != 0
    OR signed_usd_micros != 0
    OR signed_original_micros IS NULL
    OR signed_usd_micros IS NULL
)
SELECT * FROM cost_lines
UNION ALL
SELECT * FROM credit_lines
ORDER BY incurred_at, source_record_id"""

        fun readBillingConfig(env: Map<String, String>): BillingConfig? {
            val values = listOf(
                "GCP_FINOPS_BILLING_PROJECT_ID",
                "GCP_FINOPS_BILLING_DATASET",
                "GCP_FINOPS_BILLING_TABLE",
                "GCP_FINOPS_BILLING_LOCATION",
                "GCP_FINOPS_MAX_BYTES_BILLED",
            ).map { env[it]?.trim().orEmpty() }
            if (values.all(String::isEmpty)) return null
            if (values.any(String::isEmpty)) throw IllegalArgumentException("incomplete GCP billing configuration")
            val (projectId, dataset, table, location, maximumBytesText) = values
            require(Regex("^[a-z][a-z0-9-]{4,61}[a-z0-9]$").matches(projectId)) { "invalid GCP billing project" }
            require(Regex("^[A-Za-z0-9_]{1,1024}$").matches(dataset) && Regex("^[A-Za-z0-9_]{1,1024}$").matches(table)) {
                "invalid GCP billing table"
            }
            require(Regex("^[A-Za-z0-9_-]{2,32}$").matches(location)) { "invalid BigQuery location" }
            val maximumBytesBilled = maximumBytesText.toLongOrNull()
            require(maximumBytesBilled != null && maximumBytesBilled in 1..1_000_000_000L) {
                "GCP billing maximum bytes must be between 1 and 1,000,000,000"
            }
            return BillingConfig(projectId, dataset, table, location, maximumBytesBilled)
        }

        fun billingWindow(now: Instant, coverageStartDate: String): Pair<String, String> {
            val tomorrow = utcTomorrowStart(now)
            val from = isoDate(tomorrow - 35 * DAY_MS)
            return listOf(from, coverageStartDate).jsSorted().last() to isoDate(tomorrow)
        }

        fun billingExportCoverage(rows: List<Map<String, String>>, toDateExclusive: String): BillingExportCoverage {
            val windowEnd = FinOpsConnectorEnvironment.coverageStartMillis(toDateExclusive)
            val minimumExpectedAt = windowEnd - BILLING_EXPORT_MAX_LAG_DAYS * DAY_MS
            var latest: Long? = null
            rows.forEach { row ->
                val timestamp = parseJsTimestamp(row["export_time"].orEmpty()) ?: return@forEach
                if (timestamp < 0) return@forEach
                latest = latest?.let { maxOf(it, timestamp) } ?: timestamp
            }
            return BillingExportCoverage(
                complete = latest != null && latest!! >= minimumExpectedAt,
                latestExportAt = latest,
                minimumExpectedAt = minimumExpectedAt,
            )
        }

        /** `normalizeGcpBillingLine`: returns the `{entry, allocations}` queue payload. */
        fun normalizeGcpBillingLine(row: Map<String, String>, environment: String): JsonObject {
            val sourceRecordId = row.getValue("source_record_id").trim().also {
                require(Regex("^[A-Fa-f0-9]{64}$").matches(it)) { "invalid source record id" }
            }
            val billingAccountId = safeDimension(row.getValue("billing_account_id"))
            val invoiceMonth = requiredInvoiceMonth(row.getValue("invoice_month"))
            val projectId = safeDimension(row.getValue("project_id"))
            val service = safeDimension(row.getValue("service"))
            val sku = safeDimension(row.getValue("sku"))
            val incurredAt = requiredTimestamp(row.getValue("incurred_at"), "incurred timestamp")
            requiredTimestamp(row.getValue("export_time"), "export timestamp")
            val currency = row.getValue("currency").trim().uppercase().also {
                require(Regex("^[A-Z]{3}$").matches(it)) { "invalid GCP billing currency" }
            }
            val originalMicros = requiredSignedMicros(row.getValue("signed_original_micros"), "original amount")
            val usdMicros = requiredSignedMicros(row.getValue("signed_usd_micros"), "USD amount")
            require(originalMicros != 0L && usdMicros != 0L && java.lang.Long.signum(originalMicros) == java.lang.Long.signum(usdMicros)) {
                "GCP original-currency and USD amounts must have matching nonzero signs"
            }
            val lineKind = when (row.getValue("line_kind")) {
                "credit" -> "credit"
                "cost" -> "cost"
                else -> throw IllegalArgumentException("invalid GCP billing line kind")
            }
            val costType = safeDimension(row.getValue("cost_type").lowercase())
            val direction = when {
                lineKind == "credit" || usdMicros < 0 -> "CREDIT"
                costType == "tax" -> "TAX"
                costType == "adjustment" || costType == "rounding_error" -> "ADJUSTMENT"
                else -> "EXPENSE"
            }
            val entryId = "gcp:${sourceRecordId.take(40).lowercase()}"
            val rawReconciliationKey = "gcp:$billingAccountId:$invoiceMonth:$projectId:$service"
            val reconciliationKey = if (rawReconciliationKey.length <= 240) {
                rawReconciliationKey
            } else {
                "${rawReconciliationKey.take(175)}:${JsCanonicalJson.sha256Hex(rawReconciliationKey).take(64)}"
            }
            val rowJson = JsonObject(row.mapValues { JsonPrimitive(it.value) })
            val project = classifyProject(projectId)
            return buildJsonObject {
                putJsonObject("entry") {
                    put("id", entryId)
                    put("source", "gcp")
                    put("sourceRecordId", "line:${sourceRecordId.lowercase()}")
                    put("direction", direction)
                    putJsonObject("amount") {
                        put("currency", currency)
                        put("minorUnits", Math.abs(originalMicros))
                        put("scale", 6)
                    }
                    put("usdMicros", Math.abs(usdMicros))
                    put("incurredAt", incurredAt)
                    put("invoiceMonth", invoiceMonth)
                    put("project", project)
                    put("environment", environment)
                    put("vendor", "google-cloud")
                    put("service", service)
                    put("sku", sku)
                    put("status", "FINALIZED")
                    put("reconciliationKey", reconciliationKey)
                    put("sourceHash", JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(rowJson)))
                    putJsonObject("metadata") {
                        put("costClass", "cloud_platform")
                        put("gcpProjectId", projectId)
                        put("gcpLineKind", lineKind)
                        put("gcpCostType", costType)
                        put("exportTime", row.getValue("export_time"))
                    }
                }
                put("allocations", buildJsonArray { })
            }
        }

        private fun dateParameter(name: String, value: String): JsonObject {
            require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) { "invalid BigQuery date parameter" }
            return buildJsonObject {
                put("name", name)
                putJsonObject("parameterType") { put("type", "DATE") }
                putJsonObject("parameterValue") { put("value", value) }
            }
        }

        private fun requiredInvoiceMonth(value: String): String {
            val normalized = value.trim().replace(Regex("^(\\d{4})(\\d{2})$"), "$1-$2")
            require(Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(normalized)) { "invalid GCP invoice month" }
            return normalized
        }

        private fun requiredTimestamp(value: String, label: String): Long {
            val timestamp = parseJsTimestamp(value)
            require(timestamp != null && timestamp >= 0 && timestamp <= MAX_SAFE_INTEGER) { "invalid $label" }
            return timestamp
        }

        private fun requiredSignedMicros(value: String, label: String): Long {
            require(Regex("^-?\\d+$").matches(value)) { "invalid $label" }
            val result = value.toBigInteger()
            require(result.abs() < java.math.BigInteger.valueOf(MAX_SAFE_INTEGER)) {
                "$label exceeds the safe fixed-point range"
            }
            return result.toLong()
        }

        private fun safeDimension(value: String): String =
            value.replace(Regex("[\r\n\t]"), " ").trim().take(120).ifEmpty { "unattributed" }

        private fun classifyProject(projectId: String): String {
            val normalized = projectId.lowercase()
            return when {
                "samurai" in normalized || "teddy" in normalized -> "samurai"
                "seen" in normalized -> "seen"
                "portfolio" in normalized -> "portfolio"
                else -> "shared"
            }
        }

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
}

/** `Date.parse` for the ISO-8601 shapes the connectors receive; null when unparseable. */
internal fun parseJsTimestamp(value: String): Long? {
    val text = value.trim()
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
}
