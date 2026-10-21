package code.yousef.aws.jobs

import code.yousef.portfolio.finops.FinOpsImportRun
import code.yousef.portfolio.finops.FinOpsIngestRequest
import code.yousef.portfolio.finops.FinOpsService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Ported from cloudflare/portfolio-edge/src/connector-failure.ts. */
class ConnectorFailure(val failureCode: String, message: String = "FinOps connector failed") : RuntimeException(message) {
    init {
        require(failureCode in FAILURE_PRIORITY) { "unknown connector failure code" }
    }

    companion object {
        val FAILURE_PRIORITY: Map<String, Int> = listOf(
            "configuration",
            "workload_identity",
            "provider_authorization",
            "bigquery_authorization",
            "query_limit",
            "pagination_limit",
            "provider_api",
            "bigquery_query",
            "provider_schema",
            "bigquery_schema",
            "restricted_api_unavailable",
            "record_validation",
            "unsupported_currency",
            "cost_unavailable",
            "queue_delivery",
            "unknown",
        ).withIndex().associate { (index, code) -> code to index }

        fun codeOf(error: Throwable): String = (error as? ConnectorFailure)?.failureCode ?: "unknown"

        fun wrap(code: String, error: Throwable, message: String = "FinOps connector failed"): ConnectorFailure =
            error as? ConnectorFailure ?: ConnectorFailure(code, message)

        fun highestPriority(codes: Collection<String>): String? = codes.minByOrNull { FAILURE_PRIORITY.getValue(it) }
    }
}

/** Where connector output goes: previously the `finops-ingest` Queue, now the FinOps service. */
interface FinOpsIngestSink {
    /** Accepts one `{entry, allocations}` payload exactly as the Worker enqueued it. */
    fun ingest(payload: JsonObject)

    /** Accepts one import-run payload exactly as the Worker enqueued it. */
    fun importRun(payload: JsonObject)
}

/**
 * Applies connector output directly to [FinOpsService]. Entry failures are isolated (they
 * were independent Queue messages before) and reported after the connector completes so
 * the scheduler message is retried and, if persistent, reaches the dead-letter queue.
 */
class FinOpsServiceSink(private val service: FinOpsService) : FinOpsIngestSink {
    private val log = LoggerFactory.getLogger(FinOpsServiceSink::class.java)
    var ingested: Int = 0
        private set
    var failed: Int = 0
        private set

    override fun ingest(payload: JsonObject) {
        try {
            service.ingest(connectorJson.decodeFromJsonElement(FinOpsIngestRequest.serializer(), payload), ENTRY_ACTOR)
            ingested++
        } catch (failure: Exception) {
            failed++
            // Never log vendor payloads; the entry id is a hash-derived identifier.
            log.error(
                "FinOps connector entry rejected id={} reason={}",
                (payload["entry"] as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.content }?.take(64),
                failure.javaClass.simpleName,
            )
        }
    }

    override fun importRun(payload: JsonObject) {
        service.recordImportRun(connectorJson.decodeFromJsonElement(FinOpsImportRun.serializer(), payload), IMPORT_ACTOR)
    }

    fun requireAllIngested() {
        if (failed > 0) throw IllegalStateException("$failed FinOps connector entries were rejected")
    }

    companion object {
        // The audit actors the Worker -> container hop used for the same operations.
        const val ENTRY_ACTOR = "samurai-service"
        const val IMPORT_ACTOR = "gcp-billing-service"
    }
}

internal val connectorJson = Json { ignoreUnknownKeys = false }

/** Environment shared by every scheduled FinOps connector. */
data class FinOpsConnectorEnvironment(
    val portfolioEnvironment: String,
    val coverageStartDate: String,
) {
    val coverageStartMillis: Long = coverageStartMillis(coverageStartDate)

    fun requireDev(what: String) {
        if (portfolioEnvironment != "dev") throw IllegalStateException("$what is dev-only until production approval")
    }

    companion object {
        private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun coverageStartMillis(date: String): Long {
            require(ISO_DATE.matches(date)) { "invalid FinOps coverage start date" }
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }

        fun fromEnvironment(environment: Map<String, String>): FinOpsConnectorEnvironment = FinOpsConnectorEnvironment(
            portfolioEnvironment = when (val value = environment["PORTFOLIO_ENV"]?.trim()) {
                "dev", "prod" -> value
                else -> throw ConnectorFailure("configuration", "PORTFOLIO_ENV must be dev or prod")
            },
            coverageStartDate = environment["FINOPS_COVERAGE_START_DATE"]?.trim()
                ?: throw ConnectorFailure("configuration", "invalid FinOps coverage start date"),
        )
    }
}

data class ConnectorHttpResponse(val status: Int, val body: ByteArray, val headers: Map<String, List<String>> = emptyMap())

/** Minimal HTTP seam so connectors can be tested without network access. */
fun interface ConnectorHttp {
    fun send(method: String, url: String, headers: Map<String, String>, body: String?, maximumBytes: Int): ConnectorHttpResponse
}

class JdkConnectorHttp(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : ConnectorHttp {
    override fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        maximumBytes: Int,
    ): ConnectorHttpResponse {
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60))
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { stream ->
            val read = stream.readNBytes(maximumBytes)
            if (stream.read() != -1) null else read
        }
        return ConnectorHttpResponse(
            status = response.statusCode(),
            body = bytes ?: OVERSIZED,
            headers = response.headers().map(),
        )
    }

    companion object {
        /** Marker returned when a body exceeds its bound; callers check identity. */
        val OVERSIZED = ByteArray(0)
    }
}

internal fun parseBoundedJson(response: ConnectorHttpResponse, failureCode: String): JsonElement {
    if (response.body === JdkConnectorHttp.OVERSIZED) throw ConnectorFailure(failureCode, "Provider response exceeds its byte limit")
    return try {
        Json.parseToJsonElement(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(response.body)).toString())
    } catch (_: Exception) {
        throw ConnectorFailure(failureCode, "Provider response is not valid JSON")
    }
}

/** The import-run payload every connector enqueued (`importRunEnvelope` in the Worker). */
internal fun importRunPayload(
    source: String,
    fromDate: String,
    toDateExclusive: String,
    sourceRecords: Long,
    entries: Long,
    sourceHash: String,
    status: String,
    completedAt: Long,
    failureCode: String?,
): JsonObject {
    val id = "import:$source:$toDateExclusive:${sourceHash.take(16)}"
    return buildJsonObject {
        put("id", id)
        put("source", source)
        put("fromDate", fromDate)
        put("toDateExclusive", toDateExclusive)
        put("sourceRecords", sourceRecords)
        put("entries", entries)
        put("sourceHash", sourceHash)
        put("status", status)
        put("completedAt", completedAt)
        failureCode?.let { put("failureCode", it) }
    }
}

/** `publishFailedImportRun`: a best-effort failed receipt whose hash covers the failure only. */
internal fun publishFailedImportRun(
    sink: FinOpsIngestSink,
    source: String,
    fromDate: String,
    toDateExclusive: String,
    sourceRecords: Long,
    entries: Long,
    completedAt: Long,
    failureCode: String,
) {
    try {
        val sourceHash = JsCanonicalJson.sha256Hex(
            JsCanonicalJson.stringify(
                buildJsonObject {
                    put("source", source)
                    put("fromDate", fromDate)
                    put("toDateExclusive", toDateExclusive)
                    put("status", "failed")
                    put("failureCode", failureCode)
                },
            ),
        )
        sink.importRun(
            importRunPayload(source, fromDate, toDateExclusive, sourceRecords, entries, sourceHash, "failed", completedAt, failureCode),
        )
    } catch (_: Exception) {
        LoggerFactory.getLogger("FinOpsConnectors").error(
            "{}_billing_failed_import_run_publish_failed failureCode={}",
            source,
            failureCode,
        )
    }
}

internal fun isoDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

internal fun utcTomorrowStart(now: Instant): Long =
    now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Sorted like JavaScript's default `Array.prototype.sort` (UTF-16 code units). */
internal fun List<String>.jsSorted(): List<String> = sortedWith { left, right -> left.compareTo(right) }
