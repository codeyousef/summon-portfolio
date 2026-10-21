package code.yousef.aws.jobs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

/**
 * Kotlin port of cloudflare/portfolio-edge/src/stripe-billing.ts: reads the Stripe balance
 * ledger (`/v1/balance_transactions`) for the rolling 35-day window with the restricted
 * `STRIPE_FINOPS_READ_KEY` and ingests gross and fee entries.
 */
class StripeBillingConnector(
    private val environment: FinOpsConnectorEnvironment,
    private val readKey: String?,
    private val http: ConnectorHttp = JdkConnectorHttp(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(StripeBillingConnector::class.java)

    data class Window(val fromSeconds: Long, val toSeconds: Long, val fromDate: String, val toDateExclusive: String)

    /** [windowAt] anchors the billing window (the scheduled time), keeping retries deterministic. */
    fun run(sink: FinOpsIngestSink, windowAt: Instant = clock.instant()) {
        environment.requireDev("Stripe billing ingestion")
        val window = stripeWindow(windowAt, environment.coverageStartDate, environment.coverageStartMillis / 1_000)
        if (window.fromSeconds >= window.toSeconds) {
            log.info("stripe_billing_import_skipped reason=coverage_not_started")
            return
        }
        val token = readKey?.trim()?.takeIf(String::isNotEmpty)
        if (token == null) {
            log.info("stripe_billing_import_skipped reason=not_configured")
            return
        }
        val payloads = mutableListOf<JsonObject>()
        val sourceMaterials = mutableListOf<String>()
        var sourceRecords = 0L
        try {
            if (!Regex("^rk_(?:test|live)_[A-Za-z0-9]{16,}$").matches(token)) {
                throw ConnectorFailure("configuration", "Invalid Stripe connector configuration")
            }
            val degradationCodes = linkedSetOf<String>()
            var cursor: String? = null
            for (page in 0 until MAX_PAGES) {
                val (transactions, hasMore) = fetchStripePage(token, window, cursor)
                if (transactions.isEmpty() && hasMore) {
                    throw ConnectorFailure("pagination_limit", "Stripe pagination did not advance")
                }
                for (transaction in transactions) {
                    sourceRecords++
                    sourceMaterials += JsCanonicalJson.stringify(stableStripeRecord(transaction))
                    try {
                        val normalized = normalizeStripeBalanceTransaction(transaction, environment.portfolioEnvironment)
                        if (normalized == null) degradationCodes += "unsupported_currency" else payloads += normalized
                    } catch (_: Exception) {
                        degradationCodes += "record_validation"
                        log.error("stripe_billing_record_rejected scope=balance-transaction failureCode=record_validation")
                    }
                }
                if (!hasMore) break
                val next = (transactions.last()["id"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (next.isEmpty() || next == cursor) {
                    throw ConnectorFailure("pagination_limit", "Stripe pagination cursor did not advance")
                }
                cursor = next
                if (page == MAX_PAGES - 1) {
                    throw ConnectorFailure("pagination_limit", "Stripe billing import exceeded its page safety bound")
                }
            }
            try {
                payloads.forEach(sink::ingest)
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("queue_delivery", error, "Stripe billing queue delivery failed")
            }
            val failureCode = ConnectorFailure.highestPriority(degradationCodes)
            val sourceHash = JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(buildJsonObject {
                put("records", JsonArray(sourceMaterials.jsSorted().map(::JsonPrimitive)))
                put("failureCode", failureCode?.let(::JsonPrimitive) ?: JsonNull)
            }))
            try {
                sink.importRun(
                    importRunPayload(
                        source = "stripe",
                        fromDate = window.fromDate,
                        toDateExclusive = window.toDateExclusive,
                        sourceRecords = sourceRecords,
                        entries = payloads.size.toLong(),
                        sourceHash = sourceHash,
                        status = if (failureCode == null) "succeeded" else "partial",
                        completedAt = clock.millis(),
                        failureCode = failureCode,
                    ),
                )
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("queue_delivery", error, "Stripe billing queue delivery failed")
            }
        } catch (error: Exception) {
            val failureCode = ConnectorFailure.codeOf(error)
            log.error("stripe_billing_import_failed failureCode={}", failureCode)
            publishFailedImportRun(
                sink, "stripe", window.fromDate, window.toDateExclusive, sourceRecords, payloads.size.toLong(), clock.millis(), failureCode,
            )
            throw error
        }
    }

    private fun fetchStripePage(token: String, window: Window, cursor: String?): Pair<List<JsonObject>, Boolean> {
        val query = buildList {
            add("limit=$PAGE_SIZE")
            add("${encode("created[gte]")}=${window.fromSeconds}")
            add("${encode("created[lt]")}=${window.toSeconds}")
            add("${encode("expand[]")}=${encode("data.source")}")
            cursor?.let { add("starting_after=${encode(it)}") }
        }.joinToString("&")
        val response = http.send(
            "GET",
            "$STRIPE_API?$query",
            mapOf("authorization" to "Bearer $token", "accept" to "application/json"),
            null,
            MAX_API_RESPONSE_BYTES,
        )
        if (response.status !in 200..299) {
            val code = if (response.status == 401 || response.status == 403) "provider_authorization" else "provider_api"
            throw ConnectorFailure(code, "Stripe balance ledger request failed")
        }
        val body = parseBoundedJson(response, "provider_schema") as? JsonObject
        val data = body?.get("data") as? JsonArray
        val hasMore = (body?.get("has_more") as? JsonPrimitive)?.takeIf { !it.isString }?.content
        if (
            body == null || (body["object"] as? JsonPrimitive)?.content != "list" || data == null ||
            data.any { it !is JsonObject } || (hasMore != "true" && hasMore != "false")
        ) {
            throw ConnectorFailure("provider_schema", "Stripe balance ledger response has an unexpected shape")
        }
        return data.map { it as JsonObject } to (hasMore == "true")
    }

    companion object {
        private const val STRIPE_API = "https://api.stripe.com/v1/balance_transactions"
        private const val MAX_PAGES = 100
        private const val PAGE_SIZE = 100
        private const val MAX_API_RESPONSE_BYTES = 1024 * 1024
        private const val DAY_MS = 86_400_000L
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        private val SAMURAI_USER_ID = Regex("^[A-Za-z0-9][A-Za-z0-9@._:+-]{0,199}$")

        fun stripeWindow(now: Instant, coverageDate: String, coverageEpochSeconds: Long): Window {
            val tomorrow = utcTomorrowStart(now)
            val from = tomorrow - 35 * DAY_MS
            return Window(
                fromSeconds = maxOf(Math.floorDiv(from, 1_000L), coverageEpochSeconds),
                toSeconds = Math.floorDiv(tomorrow, 1_000L),
                fromDate = listOf(isoDate(from), coverageDate).jsSorted().last(),
                toDateExclusive = isoDate(tomorrow),
            )
        }

        fun normalizeStripeBalanceTransaction(transaction: JsonObject, environment: String): List<JsonObject>? {
            val id = (jsString(transaction["id"]) ?: "").trim().also {
                require(Regex("^[A-Za-z0-9_]{3,180}$").matches(it)) { "invalid Stripe record id" }
            }
            val amount = requiredMinorUnits(transaction["amount"], "amount")
            val fee = requiredMinorUnits(transaction["fee"].nullish() ?: JsonPrimitive(0), "fee")
            val currency = (jsString(transaction["currency"]) ?: "").trim().uppercase()
            require(Regex("^[A-Z]{3}$").matches(currency)) { "invalid Stripe currency" }
            if (currency != "USD") return null
            val createdSeconds = requiredCreated(transaction["created"])
            val incurredAt = createdSeconds * 1_000
            val invoiceMonth = isoDate(incurredAt).take(7)
            val category = safeDimension(
                requireString(coalesce(transaction["reporting_category"], transaction["type"]) ?: JsonPrimitive("other")),
            )
            val isTransfer = "payout" in category || category == "transfer" || category == "connect_collection_transfer"
            val isRefund = amount < 0 || "refund" in category || "dispute" in category
            val userId = stripeUserId(transaction["source"])
            val stable = stableStripeRecord(transaction)
            val idHash = JsCanonicalJson.sha256Hex(id).take(40)
            val grossEntryId = "stripe:$idHash"
            val grossDirection = when {
                isTransfer -> "ADJUSTMENT"
                isRefund -> "REFUND"
                else -> "REVENUE"
            }
            val gross = stripeEntryPayload(
                entryId = grossEntryId,
                sourceRecordId = id,
                direction = grossDirection,
                originalMinorUnits = magnitude(amount),
                usdMicros = minorUnitsToUsdMicros(amount),
                incurredAt = incurredAt,
                invoiceMonth = invoiceMonth,
                environment = environment,
                service = if (isTransfer) "payouts" else "payments",
                sku = category,
                sourceHash = accountingHash(stable, "gross"),
                metadata = linkedMapOf(
                    "accountingClass" to if (isTransfer) "cash_transfer" else "revenue",
                    "stripeType" to safeDimension(requireString(transaction["type"].nullish() ?: JsonPrimitive("unknown"))),
                    "stripeReportingCategory" to category,
                ),
                userId = userId,
            )
            val results = mutableListOf(gross)
            if (fee != 0L) {
                results += stripeEntryPayload(
                    entryId = "stripe-fee:$idHash",
                    sourceRecordId = "$id:fee",
                    direction = if (fee < 0) "CREDIT" else "FEE",
                    originalMinorUnits = magnitude(fee),
                    usdMicros = minorUnitsToUsdMicros(fee),
                    incurredAt = incurredAt,
                    invoiceMonth = invoiceMonth,
                    environment = environment,
                    service = "payment-processing",
                    sku = category,
                    sourceHash = accountingHash(stable, "fee"),
                    metadata = linkedMapOf("accountingClass" to "expense", "stripeParentId" to id),
                    userId = userId,
                )
            }
            return results
        }

        fun stableStripeRecord(transaction: JsonObject): JsonObject {
            val source = transaction["source"].nullish()
            val sourceId: JsonElement = when (source) {
                null -> JsonNull
                is JsonPrimitive -> if (source.isString) source else JsonNull
                is JsonObject -> source["id"].nullish() ?: JsonNull
                else -> JsonNull
            }
            return buildJsonObject {
                put("id", transaction["id"].nullish() ?: JsonNull)
                put("amount", transaction["amount"].nullish() ?: JsonNull)
                put("fee", transaction["fee"].nullish() ?: JsonPrimitive(0))
                put("currency", transaction["currency"].nullish() ?: JsonNull)
                put("created", transaction["created"].nullish() ?: JsonNull)
                put("type", transaction["type"].nullish() ?: JsonNull)
                put("reportingCategory", transaction["reporting_category"].nullish() ?: JsonNull)
                put("sourceId", sourceId)
                put("samuraiUserId", stripeUserId(transaction["source"])?.let(::JsonPrimitive) ?: JsonNull)
            }
        }

        private fun stripeEntryPayload(
            entryId: String,
            sourceRecordId: String,
            direction: String,
            originalMinorUnits: Long,
            usdMicros: Long,
            incurredAt: Long,
            invoiceMonth: String,
            environment: String,
            service: String,
            sku: String,
            sourceHash: String,
            metadata: Map<String, String>,
            userId: String?,
        ): JsonObject = buildJsonObject {
            putJsonObject("entry") {
                put("id", entryId)
                put("source", "stripe")
                put("sourceRecordId", sourceRecordId)
                put("direction", direction)
                putJsonObject("amount") {
                    put("currency", "USD")
                    put("minorUnits", originalMinorUnits)
                    put("scale", 2)
                }
                put("usdMicros", usdMicros)
                put("incurredAt", incurredAt)
                put("invoiceMonth", invoiceMonth)
                put("project", "samurai")
                put("environment", environment)
                put("vendor", "stripe")
                put("service", service)
                put("sku", sku)
                put("status", "FINALIZED")
                put("reconciliationKey", "stripe:$invoiceMonth")
                put("sourceHash", sourceHash)
                put("metadata", JsonObject(metadata.mapValues { JsonPrimitive(it.value) }))
            }
            put("allocations", buildJsonArray {
                if (userId != null && direction != "ADJUSTMENT") {
                    add(buildJsonObject {
                        put("id", "allocation:${JsCanonicalJson.sha256Hex("$entryId:$userId").take(40)}")
                        put("entryId", entryId)
                        put("project", "samurai")
                        put("userId", userId)
                        put("allocatedUsdMicros", usdMicros)
                        put("method", "DIRECT")
                    })
                }
            })
        }

        private fun accountingHash(stable: JsonObject, part: String): String = JsCanonicalJson.sha256Hex(
            JsCanonicalJson.stringify(buildJsonObject {
                put("stable", stable)
                put("accountingPart", part)
            }),
        )

        private fun stripeUserId(source: JsonElement?): String? {
            val record = source.nullish() as? JsonObject ?: return null
            val metadata = record["metadata"].nullish() as? JsonObject ?: return null
            val candidate = jsString(metadata["samurai_user_id"])?.trim().orEmpty()
            return candidate.takeIf(SAMURAI_USER_ID::matches)
        }

        private fun requiredMinorUnits(value: JsonElement?, label: String): Long {
            val number = safeInteger(value) ?: throw IllegalArgumentException("invalid Stripe $label")
            require(number != -MAX_SAFE_INTEGER) { "invalid Stripe $label" }
            return number
        }

        private fun requiredCreated(value: JsonElement?): Long {
            val number = safeInteger(value)
            require(number != null && number in 0..253_402_300_799L) { "invalid Stripe created timestamp" }
            return number
        }

        /** `Number.isSafeInteger` over a JSON value. */
        private fun safeInteger(value: JsonElement?): Long? {
            val primitive = value.nullish() as? JsonPrimitive ?: return null
            if (primitive.isString || primitive.content == "true" || primitive.content == "false") return null
            val number = primitive.content.toDoubleOrNull() ?: return null
            if (!number.isFinite() || number != Math.floor(number) || Math.abs(number) > MAX_SAFE_INTEGER) return null
            return number.toLong()
        }

        private fun magnitude(value: Long): Long = Math.abs(value)

        private fun minorUnitsToUsdMicros(value: Long): Long {
            val result = Math.multiplyExact(magnitude(value), 10_000L)
            require(result <= MAX_SAFE_INTEGER) { "Stripe amount exceeds USD micro precision" }
            return result
        }

        private fun safeDimension(value: String): String =
            value.replace(Regex("[\r\n\t]"), " ").trim().take(120).ifEmpty { "unknown" }

        private fun JsonElement?.nullish(): JsonElement? = this?.takeUnless { it is JsonNull }

        private fun coalesce(vararg values: JsonElement?): JsonElement? = values.firstNotNullOfOrNull { it.nullish() }

        private fun jsString(value: JsonElement?): String? = (value.nullish() as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun requireString(value: JsonElement): String =
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("expected a string Stripe field")

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
}
