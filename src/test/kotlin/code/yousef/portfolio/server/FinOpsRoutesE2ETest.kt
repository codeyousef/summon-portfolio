package code.yousef.portfolio.server

import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.finops.FinOpsQuery
import code.yousef.portfolio.finops.FinOpsFxProvenance
import code.yousef.portfolio.finops.FinOpsFxRate
import code.yousef.portfolio.finops.FinOpsService
import code.yousef.portfolio.finops.InMemoryFinOpsRepository
import code.yousef.portfolio.finops.MoneyAmount
import code.yousef.portfolio.ssr.PortfolioRenderer
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.session.DefaultSession
import codes.yousef.aether.core.session.InMemorySessionStore
import codes.yousef.aether.core.session.SessionConfig
import codes.yousef.aether.core.session.SessionMiddleware
import codes.yousef.aether.web.router
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinOpsRoutesE2ETest {
    private lateinit var server: VertxServer
    private lateinit var baseUrl: String
    private lateinit var service: FinOpsService
    private lateinit var repository: InMemoryFinOpsRepository
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @BeforeTest
    fun startServer() {
        val sessionConfig = SessionConfig(cookieName = SESSION_COOKIE_NAME, maxAge = 60)
        val sessionStore = InMemorySessionStore(sessionConfig)
        runBlocking {
            sessionStore.save(ownerSession())
            sessionStore.save(nonOwnerSession())
            sessionStore.save(expiredOwnerSession())
        }

        repository = InMemoryFinOpsRepository()
        service = FinOpsService(repository, coverageSources = emptySet())
        val tempDir = Files.createTempDirectory("finops-routes")
        val renderer = PortfolioRenderer(
            PortfolioContentService(FileContentStore(tempDir.resolve("content.json"))),
        )
        val route = router {
            registerFinOpsRoutes(
                service = service,
                renderer = renderer,
                internalIngestToken = INTERNAL_TOKEN,
                receiptStore = null,
                ownerUsername = { OWNER_USERNAME },
            )
        }
        val pipeline = Pipeline().apply {
            use(SessionMiddleware(sessionStore, sessionConfig).asMiddleware())
            use(route.asMiddleware())
        }
        val port = ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"
        server = VertxServer(VertxServerConfig(port = port), pipeline) { exchange ->
            exchange.notFound("Route not found")
        }
        runBlocking { server.start() }
    }

    @AfterTest
    fun stopServer() {
        runBlocking { server.stop() }
    }

    @Test
    fun `manual expense route denies anonymous cross-user and forged CSRF requests`() {
        assertEquals(401, submit(sessionId = null).statusCode())
        assertEquals(401, submit(sessionId = NON_OWNER_SESSION_ID).statusCode())
        assertEquals(403, submit(csrf = "forged").statusCode())
        assertEquals(0, ledgerEntries())
    }

    @Test
    fun `manual multipart replay is idempotent and conflicting replay fails closed`() {
        val sourceRecordId = "owner:${"a".repeat(32)}"
        val first = submit(sourceRecordId = sourceRecordId)
        val replay = submit(sourceRecordId = sourceRecordId)
        val conflicting = submit(sourceRecordId = sourceRecordId, amount = "13.00")

        assertEquals(302, first.statusCode())
        assertEquals("/admin/spending?view=transactions&saved=true", first.location())
        assertEquals(302, replay.statusCode())
        assertEquals("/admin/spending?view=transactions&saved=true", replay.location())
        assertEquals(302, conflicting.statusCode())
        assertEquals(
            "/admin/spending?view=transactions&error=invalid&retry=$sourceRecordId",
            conflicting.location(),
        )
        assertEquals(1, ledgerEntries())
    }

    @Test
    fun `manual form accepts any ISO currency only when an authoritative FX rate exists`() {
        service.recordFxRate(
            FinOpsFxRate(
                id = "fx:eur:2026-08-22",
                sourceCurrency = "EUR",
                effectiveDate = "2026-08-22",
                usdPerMajorUnit = MoneyAmount("USD", 110, 2),
                provenance = FinOpsFxProvenance.MANUAL,
                source = "owner",
                sourceHash = "f".repeat(64),
            ),
            OWNER_USERNAME,
        )

        val inserted = submit(
            sourceRecordId = "owner:${"c".repeat(32)}",
            amount = "10.00",
            currency = "eur",
        )
        val missingRate = submit(
            sourceRecordId = "owner:${"d".repeat(32)}",
            amount = "1000",
            currency = "JPY",
        )

        assertEquals("/admin/spending?view=transactions&saved=true", inserted.location())
        assertEquals(
            "/admin/spending?view=transactions&error=invalid&retry=owner:${"d".repeat(32)}",
            missingRate.location(),
        )
        val entry = repository.listEntries(FinOpsQuery(0L, Long.MAX_VALUE)).entries.single()
        assertEquals("EUR", entry.amount.currency)
        assertEquals(11_000_000L, entry.usdMicros)
    }

    @Test
    fun `owner JSON API protects reads and mutations with session and CSRF`() {
        assertEquals(401, get("/api/admin/finops/summary", sessionId = null).statusCode())
        assertEquals(401, get("/api/admin/finops/summary", sessionId = NON_OWNER_SESSION_ID).statusCode())
        assertEquals(401, get("/api/admin/finops/summary", sessionId = EXPIRED_OWNER_SESSION_ID).statusCode())

        val ownerRead = get("/api/admin/finops/summary")
        assertEquals(200, ownerRead.statusCode())
        assertEquals(CSRF_TOKEN, ownerRead.headers().firstValue("X-CSRF-Token").orElse(null))
        assertEquals("private, no-store", ownerRead.headers().firstValue("Cache-Control").orElse(null))

        assertEquals(401, postJson("/api/admin/finops/import", ingestJson(), NON_OWNER_SESSION_ID, CSRF_TOKEN).statusCode())
        assertEquals(403, postJson("/api/admin/finops/import", ingestJson(), OWNER_SESSION_ID, null).statusCode())
        assertEquals(403, postJson("/api/admin/finops/import", ingestJson(), OWNER_SESSION_ID, "forged").statusCode())

        val inserted = postJson("/api/admin/finops/import", ingestJson(), OWNER_SESSION_ID, CSRF_TOKEN)
        val replay = postJson("/api/admin/finops/import", ingestJson(), OWNER_SESSION_ID, CSRF_TOKEN)
        assertEquals(201, inserted.statusCode())
        assertEquals(200, replay.statusCode())
        assertEquals(1, ledgerEntries())

        val export = get("/api/admin/finops/export.csv?limit=1")
        assertEquals(200, export.statusCode())
        assertEquals("1", export.headers().firstValue("X-FinOps-Export-Row-Count").orElse(null))
        assertEquals("true", export.headers().firstValue("X-FinOps-Export-Complete").orElse(null))
        assertEquals(false, export.headers().firstValue("X-FinOps-Next-Cursor").isPresent)
    }

    @Test
    fun `dashboard tab avoids unused summary scans and validates sort order`() {
        val transactions = get("/admin/spending?view=transactions&order=oldest&user=user-1")
        assertEquals(200, transactions.statusCode())
        assertEquals(listOf("read_entries"), repository.auditEvents().map { it.action })
        assertTrue(transactions.body().contains("aria-sort=\"ascending\""))
        assertEquals(400, get("/api/admin/finops/entries?order=sideways").statusCode())

        val overview = get("/admin/spending?view=overview")
        assertEquals(200, overview.statusCode())
        assertEquals(
            listOf("read_entries", "read_summary"),
            repository.auditEvents().map { it.action }.sorted(),
        )
    }

    @Test
    fun `cloud platform budget form is owner only CSRF protected and immutable`() {
        assertEquals(401, submitBudget(sessionId = null).statusCode())
        assertEquals(401, submitBudget(sessionId = NON_OWNER_SESSION_ID).statusCode())
        assertEquals(403, submitBudget(csrf = "forged").statusCode())
        assertEquals(emptyList(), repository.listBudgets())

        val inserted = submitBudget()
        val replay = submitBudget()
        assertEquals(302, inserted.statusCode())
        assertEquals("/admin/spending?view=budgets&saved=budget", inserted.location())
        assertEquals("/admin/spending?view=budgets&saved=budget", replay.location())
        val budget = repository.listBudgets().single()
        assertEquals("cloud_platform", budget.scopeValue)
        assertEquals(100_000_000L, budget.amountUsdMicros)
        assertEquals(7_500, budget.warningThresholdBasisPoints)
        assertEquals(9_000, budget.criticalThresholdBasisPoints)
        assertEquals(false, budget.hardGate)

        val invalid = submitBudget(warningPercent = "80", criticalPercent = "70")
        assertEquals("/admin/spending?view=budgets&error=invalid", invalid.location())
        assertEquals(1, repository.listBudgets().size)
    }

    @Test
    fun `internal ingestion conceals the route and rejects forged events before replay`() {
        assertEquals(404, postJson("/internal/finops/events", ingestJson(), bearerToken = null).statusCode())
        assertEquals(404, postJson("/internal/finops/events", ingestJson(), bearerToken = "wrong").statusCode())
        assertEquals(
            400,
            postJson(
                "/internal/finops/events",
                ingestJson(metadataJson = "{\"inputTokens\":\"-1\"}"),
                bearerToken = INTERNAL_TOKEN,
            ).statusCode(),
        )
        assertEquals(0, ledgerEntries())

        val inserted = postJson("/internal/finops/events", ingestJson(), bearerToken = INTERNAL_TOKEN)
        val replay = postJson("/internal/finops/events", ingestJson(), bearerToken = INTERNAL_TOKEN)
        val conflict = postJson(
            "/internal/finops/events",
            ingestJson(sourceHash = "c".repeat(64)),
            bearerToken = INTERNAL_TOKEN,
        )
        assertEquals(202, inserted.statusCode())
        assertEquals(200, replay.statusCode())
        assertEquals(409, conflict.statusCode())
        assertEquals(1, ledgerEntries())
    }

    @Test
    fun `internal import receipts reject invented failure reasons before Firestore persistence`() {
        val path = "/internal/finops/import-run"
        assertEquals(404, postJson(path, importRunJson("provider_api"), bearerToken = null).statusCode())
        assertEquals(404, postJson(path, importRunJson("provider_api"), bearerToken = "wrong").statusCode())
        assertEquals(
            400,
            postJson(path, importRunJson("provider_timeout_with_internal_details"), bearerToken = INTERNAL_TOKEN)
                .statusCode(),
        )
        assertEquals(emptyList(), repository.listImportRuns())

        val inserted = postJson(path, importRunJson("provider_api"), bearerToken = INTERNAL_TOKEN)
        val replay = postJson(path, importRunJson("provider_api"), bearerToken = INTERNAL_TOKEN)
        assertEquals(202, inserted.statusCode())
        assertEquals(200, replay.statusCode())
        assertEquals(listOf("provider_api"), repository.listImportRuns().map { it.failureCode })
    }

    @Test
    fun `internal projection proof is concealed and returns fail-closed evidence`() {
        val path = "/internal/finops/projection-reconciliation"
        assertEquals(404, postJson(path, "{}", bearerToken = null).statusCode())
        assertEquals(404, postJson(path, "{}", bearerToken = "wrong").statusCode())

        val response = postJson(path, "{}", bearerToken = INTERNAL_TOKEN)
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"expectedCount\":0"))
        assertTrue(response.body().contains("\"actualCount\":0"))
        assertTrue(response.body().contains("\"ready\":true"))
    }

    private fun submit(
        sessionId: String? = OWNER_SESSION_ID,
        csrf: String = CSRF_TOKEN,
        sourceRecordId: String = "owner:${"b".repeat(32)}",
        amount: String = "12.34",
        currency: String = "USD",
    ): HttpResponse<String> {
        val boundary = "----finops-route-boundary"
        val body = multipartBody(
            boundary,
            linkedMapOf(
                "csrf" to csrf,
                "source_record_id" to sourceRecordId,
                "currency" to currency,
                "amount" to amount,
                "incurred_date" to "2026-08-22",
                "direction" to "expense",
                "project" to "portfolio",
                "environment" to "dev",
                "vendor" to "Cloudflare",
                "service" to "Workers",
                "status" to "finalized",
            ),
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/admin/spending/manual-entry"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .apply {
                if (sessionId != null) header("Cookie", "$SESSION_COOKIE_NAME=$sessionId")
            }
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun submitBudget(
        sessionId: String? = OWNER_SESSION_ID,
        csrf: String = CSRF_TOKEN,
        warningPercent: String = "75",
        criticalPercent: String = "90",
    ): HttpResponse<String> = postForm(
        path = "/admin/spending/budget",
        sessionId = sessionId,
        fields = linkedMapOf(
            "csrf" to csrf,
            "amount_usd" to "100.00",
            "warning_percent" to warningPercent,
            "critical_percent" to criticalPercent,
            "effective_from" to "2026-08-01",
            "effective_to" to "",
        ),
    )

    private fun postForm(path: String, sessionId: String?, fields: Map<String, String>): HttpResponse<String> {
        val body = fields.entries.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$path"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .apply {
                if (sessionId != null) header("Cookie", "$SESSION_COOKIE_NAME=$sessionId")
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, sessionId: String? = OWNER_SESSION_ID): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$path"))
            .apply {
                if (sessionId != null) header("Cookie", "$SESSION_COOKIE_NAME=$sessionId")
            }
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun postJson(
        path: String,
        body: String,
        sessionId: String? = null,
        csrf: String? = null,
        bearerToken: String? = null,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$path"))
            .header("Content-Type", "application/json")
            .apply {
                if (sessionId != null) header("Cookie", "$SESSION_COOKIE_NAME=$sessionId")
                if (csrf != null) header("X-CSRF-Token", csrf)
                if (bearerToken != null) header("Authorization", "Bearer $bearerToken")
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun ingestJson(
        sourceHash: String = "d".repeat(64),
        metadataJson: String = "{}",
    ): String = """
        {
          "entry": {
            "id": "samurai:route-event",
            "source": "samurai",
            "sourceRecordId": "run:route-event",
            "direction": "EXPENSE",
            "amount": {"currency": "USD", "minorUnits": 125, "scale": 2},
            "usdMicros": 1250000,
            "incurredAt": 1787356800000,
            "invoiceMonth": "2026-08",
            "project": "samurai",
            "environment": "dev",
            "vendor": "workers-ai",
            "service": "inference",
            "status": "ACCRUED",
            "reconciliationKey": "samurai:2026-08:workers-ai",
            "sourceHash": "$sourceHash",
            "metadata": $metadataJson
          },
          "allocations": []
        }
    """.trimIndent()

    private fun importRunJson(failureCode: String): String = """
        {
          "id": "import:cloudflare:2026-08-23:route-test",
          "source": "cloudflare",
          "fromDate": "2026-08-01",
          "toDateExclusive": "2026-08-23",
          "sourceRecords": 0,
          "entries": 0,
          "status": "failed",
          "completedAt": 1787443200000,
          "sourceHash": "${"e".repeat(64)}",
          "failureCode": "$failureCode"
        }
    """.trimIndent()

    private fun ledgerEntries(): Int = service.entries(
        FinOpsQuery(
            from = 1_700_000_000_000L,
            toExclusive = 1_900_000_000_000L,
            limit = 500,
        ),
    ).entries.size

    private fun ownerSession() = DefaultSession(
        id = OWNER_SESSION_ID,
        createdAt = System.currentTimeMillis(),
    ).apply {
        set("username", OWNER_USERNAME)
        set("mustChangePassword", "false")
        set("finops_csrf", CSRF_TOKEN)
    }

    private fun nonOwnerSession() = DefaultSession(
        id = NON_OWNER_SESSION_ID,
        createdAt = System.currentTimeMillis(),
    ).apply {
        set("username", "another-admin")
        set("mustChangePassword", "false")
        set("finops_csrf", CSRF_TOKEN)
    }

    private fun expiredOwnerSession() = DefaultSession(
        id = EXPIRED_OWNER_SESSION_ID,
        createdAt = 1L,
    ).apply {
        set("username", OWNER_USERNAME)
        set("mustChangePassword", "false")
        set("finops_csrf", CSRF_TOKEN)
    }

    private fun HttpResponse<*>.location(): String? = headers().firstValue("Location").orElse(null)

    private fun multipartBody(boundary: String, fields: Map<String, String>): ByteArray = buildString {
        fields.forEach { (name, value) ->
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
            append(value).append("\r\n")
        }
        append("--").append(boundary).append("--\r\n")
    }.toByteArray(Charsets.UTF_8)

    private companion object {
        const val SESSION_COOKIE_NAME = "admin_session"
        const val OWNER_SESSION_ID = "finops-owner-session"
        const val NON_OWNER_SESSION_ID = "finops-non-owner-session"
        const val EXPIRED_OWNER_SESSION_ID = "finops-expired-owner-session"
        const val OWNER_USERNAME = "owner"
        const val CSRF_TOKEN = "finops-route-csrf-token"
        const val INTERNAL_TOKEN = "finops-internal-route-token"
    }
}
