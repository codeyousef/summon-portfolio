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
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Kotlin port of cloudflare/portfolio-edge/src/cloudflare-billing.ts: account and zone
 * subscriptions plus the restricted billable-usage API for the current month, authenticated
 * with `CLOUDFLARE_BILLING_API_TOKEN` for `CLOUDFLARE_ACCOUNT_ID`.
 */
class CloudflareBillingConnector(
    private val environment: FinOpsConnectorEnvironment,
    private val token: String?,
    private val accountId: String?,
    private val http: ConnectorHttp = JdkConnectorHttp(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(CloudflareBillingConnector::class.java)

    /** [windowAt] anchors the billing window (the scheduled time), keeping retries deterministic. */
    fun run(sink: FinOpsIngestSink, windowAt: Instant = clock.instant()) {
        environment.requireDev("Cloudflare billing ingestion")
        val (from, to) = currentBillingWindow(windowAt, environment.coverageStartMillis)
        val fromDate = from.take(10)
        val toDateExclusive = to.take(10)
        if (from >= to) {
            log.info("cloudflare_billing_import_skipped reason=coverage_not_started")
            return
        }
        val token = token?.trim()?.takeIf(String::isNotEmpty)
        val accountId = accountId?.trim()?.takeIf(String::isNotEmpty)
        if (token == null && accountId == null) {
            log.info("cloudflare_billing_import_skipped reason=not_configured")
            return
        }
        val records = mutableListOf<JsonObject>()
        val sourceMaterials = mutableListOf<String>()
        var sourceRecords = 0L
        try {
            if (token == null || accountId == null || !Regex("^[a-f0-9]{32}$").matches(accountId)) {
                throw ConnectorFailure("configuration", "Invalid Cloudflare billing connector configuration")
            }
            val degradationCodes = linkedSetOf<String>()
            val accountSubscriptions = try {
                apiGetArray("$API_ROOT/accounts/$accountId/subscriptions", token)
            } catch (error: Exception) {
                recordDegradation(degradationCodes, "account-subscriptions", ConnectorFailure.codeOf(error))
                emptyList()
            }
            for (candidate in accountSubscriptions) {
                val subscription = candidate as? JsonObject
                if (subscription == null) {
                    degradationCodes += "record_validation"
                    logInvalidRecord("account-subscription")
                    continue
                }
                sourceRecords++
                val context = buildJsonObject { put("scope", "account") }
                sourceMaterials += JsCanonicalJson.stringify(buildJsonObject {
                    put("context", context)
                    put("subscription", subscription)
                })
                subscriptionCoverageCode(subscription)?.let(degradationCodes::add)
                try {
                    normalizeSubscription(subscription, "account", null, null, environment.portfolioEnvironment)?.let(records::add)
                } catch (_: Exception) {
                    degradationCodes += "record_validation"
                    logInvalidRecord("account-subscription")
                }
            }

            val zones = try {
                apiGetArray("$API_ROOT/zones?account.id=$accountId&per_page=50", token)
            } catch (error: Exception) {
                recordDegradation(degradationCodes, "zones", ConnectorFailure.codeOf(error))
                emptyList()
            }
            for (candidate in zones) {
                val zone = candidate as? JsonObject
                val zoneId = (zone?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
                val zoneNameElement = zone?.get("name")
                val validName = zoneNameElement == null || (zoneNameElement is JsonPrimitive && zoneNameElement.isString)
                if (zone == null || zoneId == null || !Regex("^[a-f0-9]{32}$").matches(zoneId) || !validName) {
                    degradationCodes += "record_validation"
                    logInvalidRecord("zone")
                    continue
                }
                val zoneName = (zoneNameElement as? JsonPrimitive)?.content
                val subscriptions = try {
                    apiGetArray("$API_ROOT/zones/$zoneId/subscriptions", token)
                } catch (error: Exception) {
                    recordDegradation(degradationCodes, "zone-subscriptions", ConnectorFailure.codeOf(error))
                    emptyList()
                }
                for (subscriptionCandidate in subscriptions) {
                    val subscription = subscriptionCandidate as? JsonObject
                    if (subscription == null) {
                        degradationCodes += "record_validation"
                        logInvalidRecord("zone-subscription")
                        continue
                    }
                    val context = buildJsonObject {
                        put("scope", "zone")
                        put("zoneId", zoneId)
                        // `if (zone.name) context.zoneName = zone.name` — empty names are omitted.
                        if (!zoneName.isNullOrEmpty()) put("zoneName", zoneName)
                    }
                    sourceRecords++
                    sourceMaterials += JsCanonicalJson.stringify(buildJsonObject {
                        put("context", context)
                        put("subscription", subscription)
                    })
                    subscriptionCoverageCode(subscription)?.let(degradationCodes::add)
                    try {
                        normalizeSubscription(
                            subscription,
                            "zone",
                            zoneId,
                            zoneName?.takeIf(String::isNotEmpty),
                            environment.portfolioEnvironment,
                        )?.let(records::add)
                    } catch (_: Exception) {
                        degradationCodes += "record_validation"
                        logInvalidRecord("zone-subscription")
                    }
                }
            }

            val usage = try {
                apiGetArray(
                    "$API_ROOT/accounts/$accountId/billable/usage?from=${encode(from)}&to=${encode(to)}",
                    token,
                    "restricted_api_unavailable",
                )
            } catch (error: Exception) {
                recordDegradation(degradationCodes, "restricted-usage", ConnectorFailure.codeOf(error))
                emptyList()
            }
            for (candidate in usage) {
                val item = candidate as? JsonObject
                if (item == null) {
                    degradationCodes += "record_validation"
                    logInvalidRecord("usage")
                    continue
                }
                sourceRecords++
                sourceMaterials += JsCanonicalJson.stringify(buildJsonObject {
                    put("context", buildJsonObject { put("scope", "usage") })
                    put("item", item)
                })
                usageCoverageCode(item)?.let(degradationCodes::add)
                try {
                    normalizeUsage(item, environment.portfolioEnvironment)?.let(records::add)
                } catch (_: Exception) {
                    degradationCodes += "record_validation"
                    logInvalidRecord("usage")
                }
            }

            val covered = records.filter { payload ->
                val incurredAt = ((payload["entry"] as JsonObject)["incurredAt"] as JsonPrimitive).content.toLongOrNull()
                incurredAt != null && incurredAt >= environment.coverageStartMillis
            }
            try {
                covered.forEach(sink::ingest)
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("queue_delivery", error, "Cloudflare billing queue delivery failed")
            }
            val failureCode = ConnectorFailure.highestPriority(degradationCodes)
            val sourceHash = JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(buildJsonObject {
                put("records", JsonArray(sourceMaterials.jsSorted().map(::JsonPrimitive)))
                put("failureCode", failureCode?.let(::JsonPrimitive) ?: JsonNull)
            }))
            try {
                sink.importRun(
                    importRunPayload(
                        source = "cloudflare",
                        fromDate = fromDate,
                        toDateExclusive = toDateExclusive,
                        sourceRecords = sourceRecords,
                        entries = covered.size.toLong(),
                        sourceHash = sourceHash,
                        status = if (failureCode == null) "succeeded" else "partial",
                        completedAt = clock.millis(),
                        failureCode = failureCode,
                    ),
                )
            } catch (error: Exception) {
                throw ConnectorFailure.wrap("queue_delivery", error, "Cloudflare billing queue delivery failed")
            }
        } catch (error: Exception) {
            val failureCode = ConnectorFailure.codeOf(error)
            log.error("cloudflare_billing_import_failed failureCode={}", failureCode)
            publishFailedImportRun(
                sink, "cloudflare", fromDate, toDateExclusive, sourceRecords, records.size.toLong(), clock.millis(), failureCode,
            )
            throw error
        }
    }

    private fun apiGetArray(url: String, token: String, authorizationFailureCode: String = "provider_authorization"): JsonArray {
        val response = http.send(
            "GET",
            url,
            mapOf("authorization" to "Bearer $token", "accept" to "application/json"),
            null,
            MAX_API_RESPONSE_BYTES,
        )
        if (response.status !in 200..299) {
            val code = if (response.status == 401 || response.status == 403) authorizationFailureCode else "provider_api"
            throw ConnectorFailure(code, "Cloudflare API request failed with HTTP ${response.status}")
        }
        val body = parseBoundedJson(response, "provider_schema") as? JsonObject
        val success = (body?.get("success") as? JsonPrimitive)?.content == "true"
        val result = body?.get("result") as? JsonArray
        if (!success || result == null) {
            throw ConnectorFailure("provider_schema", "Cloudflare API response has an unexpected shape")
        }
        return result
    }

    private fun recordDegradation(codes: MutableSet<String>, scope: String, failureCode: String) {
        codes += failureCode
        log.error("cloudflare_billing_source_unavailable scope={} failureCode={}", scope, failureCode)
    }

    private fun logInvalidRecord(scope: String) {
        // Never log vendor payloads.
        log.error("cloudflare_billing_record_rejected scope={} failureCode=record_validation", scope)
    }

    companion object {
        private const val API_ROOT = "https://api.cloudflare.com/client/v4"
        private const val MAX_API_RESPONSE_BYTES = 1024 * 1024

        fun currentBillingWindow(now: Instant, coverageStartAt: Long): Pair<String, String> {
            val date = now.atZone(ZoneOffset.UTC).toLocalDate()
            val from = date.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return jsIso(maxOf(from, coverageStartAt)) to jsIso(to)
        }

        fun normalizeSubscription(
            subscription: JsonObject,
            scope: String,
            zoneId: String?,
            zoneName: String?,
            environment: String,
        ): JsonObject? {
            val ratePlan = subscription["rate_plan"] as? JsonObject
            val id = requiredSafeText(subscription["id"], "subscription id")
            val periodStart = requiredTimestamp(subscription["current_period_start"], "subscription period start")
            val currency = requiredCurrency(
                coalesce(subscription["currency"], ratePlan?.get("currency"))?.let(::requireJsString) ?: "USD",
            )
            if (currency != "USD") return null
            val signedMicros = decimalMicros(subscription["price"].nullish() ?: JsonPrimitive(0))
            if (signedMicros == 0L) return null
            val service = safeDimension(
                coalesce(ratePlan?.get("public_name"), ratePlan?.get("id"))?.let(::requireJsString) ?: "Cloudflare subscription",
            )
            val sourceRecordId = "subscription:$scope:${zoneId ?: "account"}:$id:$periodStart"
            val skuSource: JsonElement = coalesce(ratePlan?.get("id"), subscription["frequency"]) ?: JsonPrimitive("recurring")
            val recordHash = JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(buildJsonObject {
                put("sourceRecordId", sourceRecordId)
                put("currency", currency)
                put("signedMicros", signedMicros)
                put("service", service)
                put("sku", skuSource)
            }))
            return finOpsPayload(
                sourceRecordId = sourceRecordId,
                direction = if (signedMicros < 0) "CREDIT" else "EXPENSE",
                usdMicros = Math.abs(signedMicros),
                incurredAt = requireNotNull(parseJsTimestamp(periodStart)),
                invoiceMonth = periodStart.take(7),
                project = classifyProject(zoneName, service),
                environment = environment,
                service = service,
                sku = safeDimension(requireJsString(skuSource)),
                reconciliationKey = "cloudflare:${periodStart.take(7)}:${zoneId ?: "account"}",
                sourceHash = recordHash,
                metadata = linkedMapOf(
                    "costClass" to "cloud_platform",
                    "cloudflareScope" to scope,
                    "coverage" to "subscription",
                ),
                status = "ACCRUED",
            )
        }

        fun normalizeUsage(record: JsonObject, environment: String): JsonObject? {
            val rawCost = firstDefined(record, "BilledCost", "EffectiveCost", "ContractedCost", "ListCost") ?: return null
            val signedMicros = decimalMicros(rawCost)
            if (signedMicros == 0L) return null
            val currency = requiredCurrency(jsStringify(firstDefined(record, "BillingCurrency", "Currency")) ?: "USD")
            if (currency != "USD") return null
            val periodStart = requiredTimestamp(
                firstDefined(record, "ChargePeriodStart", "BillingPeriodStart", "UsagePeriodStart"),
                "usage period start",
            )
            val zoneName = optionalText(firstDefined(record, "x_ZoneName", "ZoneName"))
            val service = safeDimension(
                jsStringify(firstDefined(record, "x_ProductFamilyName", "ServiceName", "ChargeCategory")) ?: "Cloudflare usage",
            )
            val recordHash = JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(record))
            return finOpsPayload(
                sourceRecordId = "usage:${recordHash.take(48)}",
                direction = if (signedMicros < 0) "CREDIT" else "EXPENSE",
                usdMicros = Math.abs(signedMicros),
                incurredAt = requireNotNull(parseJsTimestamp(periodStart)),
                invoiceMonth = periodStart.take(7),
                project = classifyProject(zoneName, service),
                environment = environment,
                service = service,
                sku = safeDimension(
                    jsStringify(firstDefined(record, "SkuPriceId", "PricingUnit", "ChargeCategory")) ?: "billable-usage",
                ),
                reconciliationKey = "cloudflare:${periodStart.take(7)}:${optionalText(firstDefined(record, "x_ZoneId", "ZoneId")) ?: "account"}",
                sourceHash = recordHash,
                metadata = linkedMapOf(
                    "costClass" to "cloud_platform",
                    "cloudflareScope" to "usage",
                    "coverage" to "restricted-usage-api",
                ),
                status = "ACCRUED",
            )
        }

        /** `decimalMicros`: exact decimal -> signed micros, rounding half up on magnitude. */
        fun decimalMicros(input: JsonElement): Long {
            val text = when {
                input is JsonPrimitive && !input.isString && input.content != "true" && input.content != "false" ->
                    JsCanonicalJson.jsNumber(input.content.toDouble())
                else -> (jsStringify(input) ?: "null").trim()
            }
            val match = Regex("^([+-]?)(\\d+)(?:\\.(\\d+))?(?:[eE]([+-]?\\d+))?$").matchEntire(text)
                ?: throw IllegalArgumentException("invalid decimal cost")
            val negative = match.groupValues[1] == "-"
            val fraction = match.groupValues[3]
            val exponent = match.groupValues[4].ifEmpty { "0" }.toInt()
            require(Math.abs(exponent) <= 100) { "decimal exponent is out of range" }
            var value = BigInteger(match.groupValues[2] + fraction)
            val shift = 6 - fraction.length + exponent
            if (shift >= 0) {
                value = value.multiply(BigInteger.TEN.pow(shift))
            } else {
                val divisor = BigInteger.TEN.pow(-shift)
                val (quotient, remainder) = value.divideAndRemainder(divisor)
                value = quotient + if (remainder.shiftLeft(1) >= divisor) BigInteger.ONE else BigInteger.ZERO
            }
            if (negative) value = value.negate()
            require(value.abs() <= BigInteger.valueOf(9_007_199_254_740_991L)) { "cost exceeds fixed-point range" }
            return value.toLong()
        }

        private fun finOpsPayload(
            sourceRecordId: String,
            direction: String,
            usdMicros: Long,
            incurredAt: Long,
            invoiceMonth: String,
            project: String,
            environment: String,
            service: String,
            sku: String,
            reconciliationKey: String,
            sourceHash: String,
            metadata: Map<String, String>,
            status: String,
        ): JsonObject {
            val id = "cloudflare:${JsCanonicalJson.sha256Hex(sourceRecordId).take(40)}"
            return buildJsonObject {
                putJsonObject("entry") {
                    put("id", id)
                    put("source", "cloudflare")
                    put("sourceRecordId", sourceRecordId)
                    put("direction", direction)
                    putJsonObject("amount") {
                        put("currency", "USD")
                        put("minorUnits", usdMicros)
                        put("scale", 6)
                    }
                    put("usdMicros", usdMicros)
                    put("incurredAt", incurredAt)
                    put("invoiceMonth", invoiceMonth)
                    put("project", project)
                    put("environment", environment)
                    put("vendor", "cloudflare")
                    put("service", service)
                    put("sku", sku)
                    put("status", status)
                    put("reconciliationKey", reconciliationKey)
                    put("sourceHash", sourceHash)
                    put("metadata", JsonObject(metadata.mapValues { JsonPrimitive(it.value) }))
                }
                put("allocations", buildJsonArray { })
            }
        }

        private fun subscriptionCoverageCode(subscription: JsonObject): String? {
            if (subscription["price"].nullish() == null) return "cost_unavailable"
            val ratePlan = subscription["rate_plan"] as? JsonObject
            val currency = (
                jsStringify(subscription["currency"].nullish()) ?: jsStringify(ratePlan?.get("currency").nullish()) ?: "USD"
                ).trim().uppercase()
            return if (Regex("^[A-Z]{3}$").matches(currency) && currency != "USD") "unsupported_currency" else null
        }

        private fun usageCoverageCode(record: JsonObject): String? {
            if (firstDefined(record, "BilledCost", "EffectiveCost", "ContractedCost", "ListCost") == null) return "cost_unavailable"
            val currency = (jsStringify(firstDefined(record, "BillingCurrency", "Currency")) ?: "USD").trim().uppercase()
            return if (Regex("^[A-Z]{3}$").matches(currency) && currency != "USD") "unsupported_currency" else null
        }

        private fun classifyProject(zoneName: String?, service: String): String {
            val zone = zoneName?.lowercase().orEmpty()
            val product = service.lowercase()
            return when {
                zone == "yousef.codes" -> "portfolio"
                zone == "felidai.com" || "ai gateway" in product || "vectorize" in product -> "samurai"
                "seen" in product -> "seen"
                else -> "shared"
            }
        }

        /** `keys.map(key => record[key]).find(value => value !== undefined && value !== null)`. */
        private fun firstDefined(record: JsonObject, vararg keys: String): JsonElement? =
            keys.asSequence().mapNotNull { record[it].nullish() }.firstOrNull()

        private fun JsonElement?.nullish(): JsonElement? = this?.takeUnless { it is JsonNull }

        /** TypeScript `a ?? b ?? ...` over JSON values. */
        private fun coalesce(vararg values: JsonElement?): JsonElement? = values.firstNotNullOfOrNull { it.nullish() }

        /** A string method on a non-string throws in the Worker; mirror that rejection. */
        private fun requireJsString(value: JsonElement): String =
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("expected a string billing field")

        /** JavaScript `String(value)` for JSON values; null for missing/null. */
        private fun jsStringify(value: JsonElement?): String? = when (val element = value.nullish()) {
            null -> null
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.content == "true" || element.content == "false" -> element.content
                else -> JsCanonicalJson.jsNumber(element.content.toDouble())
            }
            is JsonArray -> element.joinToString(",") { jsStringify(it).orEmpty() }
            is JsonObject -> "[object Object]"
        }

        private fun requiredSafeText(value: JsonElement?, label: String): String {
            val text = (jsStringify(value) ?: "").trim()
            require(Regex("^[A-Za-z0-9._:-]{1,180}$").matches(text)) { "invalid $label" }
            return text
        }

        /** `new Date(text).toISOString()` with validation. */
        private fun requiredTimestamp(value: JsonElement?, label: String): String {
            val text = jsStringify(value) ?: "undefined"
            val millis = parseJsTimestamp(text) ?: throw IllegalArgumentException("invalid $label")
            return jsIso(millis)
        }

        private fun requiredCurrency(value: String): String {
            val currency = value.trim().uppercase()
            require(Regex("^[A-Z]{3}$").matches(currency)) { "invalid billing currency" }
            return currency
        }

        private fun safeDimension(value: String): String =
            value.replace(Regex("[\r\n\t]"), " ").trim().take(120).ifEmpty { "unknown" }

        private fun optionalText(value: JsonElement?): String? {
            val text = (jsStringify(value) ?: "").trim()
            return if (text.isEmpty()) null else safeDimension(text)
        }

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
}

/** JavaScript `Date.prototype.toISOString` (always millisecond precision, UTC). */
internal fun jsIso(millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochMilli(millis))
