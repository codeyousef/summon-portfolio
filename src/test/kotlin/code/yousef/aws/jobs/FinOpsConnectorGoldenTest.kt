package code.yousef.aws.jobs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compares the Kotlin connectors with vectors produced by running the original TypeScript
 * connectors (scripts/finops-connector-golden). Entry ids, payloads, payload hashes and
 * import-run receipts must be byte-identical so replays after the migration are duplicates.
 */
class FinOpsConnectorGoldenTest {
    private val golden: JsonObject = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/aws/finops-connectors-golden.json")).readText(),
    ).jsonObject
    private val now: Instant = Instant.parse(golden.getValue("now").jsonPrimitive.content)
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val environment = FinOpsConnectorEnvironment("dev", "2026-09-01")

    private class CapturingSink : FinOpsIngestSink {
        val payloads = mutableListOf<JsonObject>()
        val runs = mutableListOf<JsonObject>()
        override fun ingest(payload: JsonObject) { payloads += payload }
        override fun importRun(payload: JsonObject) { runs += payload }
    }

    private data class Call(val method: String, val url: String, val body: String?)

    private fun respond(value: JsonElement, status: Int = 200) =
        ConnectorHttpResponse(status, value.toString().toByteArray(Charsets.UTF_8))

    @Test
    fun `canonical JSON matches JavaScript canonicalJson`() {
        golden.getValue("canonical").jsonArray.forEach { vector ->
            val case = vector.jsonObject
            assertEquals(case.getValue("output").jsonPrimitive.content, JsCanonicalJson.stringify(case.getValue("input")))
        }
    }

    @Test
    fun `decimal micros matches the Worker`() {
        golden.getValue("decimalMicros").jsonArray.forEach { pair ->
            val (input, expected) = pair.jsonArray
            assertEquals(expected.jsonPrimitive.content.toLong(), CloudflareBillingConnector.decimalMicros(input))
        }
    }

    @Test
    fun `GCP billing connector reproduces Worker entries and import run`() {
        val gcp = golden.getValue("gcp").jsonObject
        val pages = gcp.getValue("pages").jsonArray
        val calls = mutableListOf<Call>()
        val http = ConnectorHttp { method, url, _, body, _ ->
            calls += Call(method, url, body)
            respond(pages[calls.size - 1])
        }
        val sink = CapturingSink()
        GcpBillingConnector(environment, gcpConfig(), { "gcp-token" }, http, clock).run(sink, now)

        assertEquals("POST", calls[0].method)
        assertTrue(calls[0].url.endsWith("/projects/billing-proj/queries"))
        assertTrue(calls[1].url.contains("/queries/job-1?") && calls[1].url.contains("pageToken=page-2"))
        assertSentMatches(gcp.getValue("sent").jsonArray, sink)
    }

    @Test
    fun `Cloudflare billing connector reproduces Worker entries and import run`() {
        val cloudflare = golden.getValue("cloudflare").jsonObject
        val accountId = cloudflare.getValue("accountId").jsonPrimitive.content
        val zoneId = cloudflare.getValue("zones").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.content
        fun envelope(key: String) = JsonObject(mapOf("success" to JsonPrimitive(true), "result" to cloudflare.getValue(key)))
        val http = ConnectorHttp { _, url, headers, _, _ ->
            assertEquals("Bearer cf-token", headers["authorization"])
            val path = url.substringBefore('?')
            when {
                path.endsWith("/accounts/$accountId/subscriptions") -> respond(envelope("accountSubscriptions"))
                path.endsWith("/zones") -> respond(envelope("zones"))
                path.endsWith("/zones/$zoneId/subscriptions") -> respond(envelope("zoneSubscriptions"))
                path.endsWith("/billable/usage") -> respond(envelope("usage"))
                else -> error("unexpected $url")
            }
        }
        val sink = CapturingSink()
        CloudflareBillingConnector(environment, "cf-token", accountId, http, clock).run(sink, now)
        assertSentMatches(cloudflare.getValue("sent").jsonArray, sink)
    }

    @Test
    fun `Stripe billing connector reproduces Worker entries and import run`() {
        val stripe = golden.getValue("stripe").jsonObject
        val pages = stripe.getValue("pages").jsonArray
        val urls = mutableListOf<String>()
        val http = ConnectorHttp { _, url, _, _, _ ->
            urls += url
            respond(pages[urls.size - 1])
        }
        val sink = CapturingSink()
        StripeBillingConnector(environment, stripe.getValue("key").jsonPrimitive.content, http, clock).run(sink, now)
        assertTrue(urls[1].contains("starting_after=txn_2"))
        assertTrue(urls[0].contains("created%5Bgte%5D=") && urls[0].contains("expand%5B%5D=data.source"))
        assertSentMatches(stripe.getValue("sent").jsonArray, sink)
    }

    @Test
    fun `GCP export coverage matches the Worker`() {
        val gcp = golden.getValue("gcp").jsonObject
        val fields = gcp.getValue("fields").jsonArray.map { it.jsonPrimitive.content }
        val rows = gcp.getValue("rows").jsonArray.map { row ->
            fields.zip(row.jsonArray.map { it.jsonPrimitive.content }).toMap()
        }
        assertEquals(golden.getValue("exportCoverage"), GcpBillingConnector.billingExportCoverage(rows, "2026-10-22").toJson())
    }

    @Test
    fun `failed GCP collection records a redacted failed import run and rethrows`() {
        val http = ConnectorHttp { _, _, _, _, _ -> ConnectorHttpResponse(403, "{}".toByteArray()) }
        val sink = CapturingSink()
        val failure = runCatching {
            GcpBillingConnector(environment, gcpConfig(), { "gcp-token" }, http, clock).run(sink, now)
        }.exceptionOrNull()
        assertEquals("bigquery_authorization", (failure as ConnectorFailure).failureCode)
        val run = sink.runs.single()
        assertEquals("failed", run.getValue("status").jsonPrimitive.content)
        assertEquals("bigquery_authorization", run.getValue("failureCode").jsonPrimitive.content)
        assertTrue(run.getValue("id").jsonPrimitive.content.startsWith("import:gcp:2026-10-22:"))
    }

    @Test
    fun `connectors refuse production until approved`() {
        val prod = FinOpsConnectorEnvironment("prod", "2026-09-01")
        val sink = CapturingSink()
        val failure = runCatching {
            StripeBillingConnector(prod, "rk_test_" + "a".repeat(24), { _, _, _, _, _ -> error("no call") }, clock).run(sink, now)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(sink.runs.isEmpty())
    }

    private fun assertSentMatches(sent: JsonArray, sink: CapturingSink) {
        val expectedEntries = sent.map { it.jsonObject }.filter { it.getValue("aggregateType").jsonPrimitive.content == "finops.ingest" }
        val expectedRuns = sent.map { it.jsonObject }.filter { it.getValue("aggregateType").jsonPrimitive.content == "finops.import.run" }
        assertEquals(expectedEntries.size, sink.payloads.size, "entry count")
        expectedEntries.zip(sink.payloads).forEach { (envelope, payload) ->
            assertEquals(envelope.getValue("payload"), payload)
            // The Worker's payloadSha256 is sha256(canonicalJson(payload)); equality proves the
            // canonical serialization of every real connector payload.
            assertEquals(
                envelope.getValue("payloadSha256").jsonPrimitive.content,
                JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(payload)),
            )
        }
        assertEquals(expectedRuns.size, sink.runs.size, "import run count")
        expectedRuns.zip(sink.runs).forEach { (envelope, run) ->
            assertEquals(envelope.getValue("payload"), run)
            assertEquals(envelope.getValue("payloadSha256").jsonPrimitive.content, JsCanonicalJson.sha256Hex(JsCanonicalJson.stringify(run)))
        }
    }

    private fun gcpConfig() = mapOf(
        "GCP_FINOPS_BILLING_PROJECT_ID" to "billing-proj",
        "GCP_FINOPS_BILLING_DATASET" to "billing_export",
        "GCP_FINOPS_BILLING_TABLE" to "gcp_billing_export_v1_X",
        "GCP_FINOPS_BILLING_LOCATION" to "EU",
        "GCP_FINOPS_MAX_BYTES_BILLED" to "100000000",
    )
}
