package code.yousef.portfolio.server

import code.yousef.portfolio.finops.*
import code.yousef.portfolio.photography.extractMultipartBoundary
import code.yousef.portfolio.photography.parseMultipartFormData
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ui.admin.SpendingView
import code.yousef.portfolio.ui.admin.SpendingGroup
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.jvm.receiveParameters
import codes.yousef.aether.core.respondJson
import codes.yousef.aether.core.session.session
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParam
import kotlinx.serialization.decodeFromString
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private val finOpsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
private const val FINOPS_CSRF_SESSION_KEY = "finops_csrf"
private const val MAX_FINOPS_BODY_BYTES = 1_048_576

fun Router.registerFinOpsRoutes(
    service: FinOpsService,
    renderer: PortfolioRenderer,
    internalIngestToken: String?,
    receiptStore: FinOpsReceiptStore?,
    ownerUsername: () -> String,
) {
    get("/admin/spending") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername, browser = true) ?: return@get
        val query = exchange.finOpsQueryOrRespond(defaultLimit = 100) ?: return@get
        val view = SpendingView.from(exchange.request.queryParameter("view"))
        val group = SpendingGroup.from(exchange.request.queryParameter("group"))
        val currency = exchange.request.queryParameter("currency")?.lowercase().takeIf { it == "sar" } ?: "usd"
        val csrfToken = exchange.ensureCsrfToken()
        exchange.response.setHeader("Cache-Control", "private, no-store")
        exchange.respondSummonPage(
            renderer.spendingDashboardPage(
                username = username,
                summary = when (view) {
                    SpendingView.OVERVIEW,
                    SpendingView.PROJECTS,
                    SpendingView.VENDORS,
                    SpendingView.BUDGETS,
                    -> service.summaryWithComparison(query, username)

                    SpendingView.TRANSACTIONS,
                    SpendingView.SAMURAI_USERS,
                    SpendingView.RECONCILIATION,
                    -> emptyFinOpsSummary(query)
                },
                entries = if (view == SpendingView.TRANSACTIONS) {
                    service.entries(query, username)
                } else {
                    FinOpsEntryPage(emptyList())
                },
                users = if (view == SpendingView.SAMURAI_USERS) {
                    service.samuraiUsers(query, username)
                } else {
                    SamuraiUserSpendPage(emptyList())
                },
                reconciliations = if (view == SpendingView.RECONCILIATION) {
                    service.reconciliations(query, username)
                } else {
                    emptyList()
                },
                recurringExpenses = if (view == SpendingView.BUDGETS) service.recurringExpenses(username) else emptyList(),
                view = view,
                group = group,
                currency = currency,
                rangeLabel = "${Instant.ofEpochMilli(query.from).atZone(ZoneOffset.UTC).toLocalDate()} – ${Instant.ofEpochMilli(query.toExclusive - 1).atZone(ZoneOffset.UTC).toLocalDate()}",
                query = query,
                csrfToken = csrfToken,
                manualSourceRecordId = manualFinOpsSourceRecordId(exchange.request.queryParameter("retry")),
                receiptsEnabled = receiptStore != null,
                successMessage = when (exchange.request.queryParameter("saved")) {
                    "true" -> "Expense saved."
                    "budget" -> "Cloud platform budget saved."
                    "recurring" -> "Recurring expense saved."
                    "csv" -> "CSV import completed."
                    else -> null
                },
                errorMessage = exchange.request.queryParameter("error")?.takeIf { it == "invalid" }?.let { "The change could not be saved. Check every field and try again." },
            )
        )
    }

    post("/admin/spending/manual-entry") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@post
        val declared = exchange.request.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > MAX_FINOPS_MANUAL_MULTIPART_BYTES) {
            exchange.redirectWithLength("/admin/spending?view=transactions&error=invalid")
            return@post
        }
        val form = runCatching {
            val boundary = requireNotNull(extractMultipartBoundary(exchange.request.headers["Content-Type"].orEmpty())) {
                "invalid expense form"
            }
            val body = exchange.request.bodyBytes()
            require(body.size <= MAX_FINOPS_MANUAL_MULTIPART_BYTES) { "expense form is too large" }
            parseMultipartFormData(body, boundary, "receipt")
        }.getOrElse {
            exchange.redirectWithLength("/admin/spending?view=transactions&error=invalid")
            return@post
        }
        val params = form.fields
        if (!exchange.hasValidFinOpsFormCsrf(params["csrf"])) {
            exchange.respondJson(403, mapOf("error" to "invalid CSRF token"))
            return@post
        }
        val sourceRecordId = runCatching {
            requireManualFinOpsSourceRecordId(params.requiredFormValue("source_record_id"))
        }.getOrElse {
            exchange.redirectWithLength("/admin/spending?view=transactions&error=invalid")
            return@post
        }
        val result = runCatching {
            val amount = MoneyAmount.parse(params.requiredFormValue("currency"), params.requiredFormValue("amount"))
            val incurredDate = java.time.LocalDate.parse(params.requiredFormValue("incurred_date"))
            val incurredAt = incurredDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val usdMicros = service.convertToUsdMicros(amount, incurredDate.toString())
            val receipt = form.file
                ?.takeIf { !it.originalFilename.isNullOrBlank() && it.bytes.isNotEmpty() }
                ?.let { file ->
                    val store = requireNotNull(receiptStore) { "receipt storage is not configured" }
                    store.save(
                        normalizeUploadedFinOpsReceiptFilename(requireNotNull(file.originalFilename)),
                        normalizeFinOpsReceiptContentType(file.contentType),
                        file.bytes,
                        uploadId = sha256Hex("manual-receipt:$sourceRecordId").take(32),
                    ).also { service.recordReceiptUpload(it, username) }
                }
            val fingerprint = listOf(
                sourceRecordId,
                params.requiredFormValue("direction"),
                amount.toString(),
                usdMicros.toString(),
                incurredAt.toString(),
                params.requiredFormValue("project"),
                params.requiredFormValue("environment"),
                params.requiredFormValue("vendor"),
                params.requiredFormValue("service"),
                params["sku"].orEmpty(),
                params.requiredFormValue("status"),
                receipt?.sha256.orEmpty(),
            ).joinToString("|")
            service.createManual(
                ManualFinOpsEntryRequest(
                    sourceRecordId = sourceRecordId,
                    direction = FinOpsDirection.valueOf(params.requiredFormValue("direction").uppercase()),
                    amount = amount,
                    usdMicros = usdMicros,
                    incurredAt = incurredAt,
                    project = params.requiredFormValue("project"),
                    environment = params.requiredFormValue("environment"),
                    vendor = params.requiredFormValue("vendor"),
                    service = params.requiredFormValue("service"),
                    sku = params["sku"]?.trim()?.takeIf(String::isNotEmpty),
                    status = FinOpsStatus.valueOf(params.requiredFormValue("status").uppercase()),
                    reconciliationKey = params["reconciliation_key"]?.trim()?.takeIf(String::isNotEmpty)
                        ?: "manual:${incurredDate.toString().take(7)}:${params.requiredFormValue("vendor")}",
                    sourceHash = sha256Hex(fingerprint),
                    receipt = receipt,
                ),
                username,
            )
        }
        exchange.redirectWithLength(
            if (result.isSuccess) "/admin/spending?view=transactions&saved=true"
            else "/admin/spending?view=transactions&error=invalid&retry=$sourceRecordId",
        )
    }

    post("/admin/spending/budget") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@post
        val params = runCatching { exchange.receiveParameters() }.getOrElse {
            exchange.redirectWithLength("/admin/spending?view=budgets&error=invalid")
            return@post
        }
        if (!exchange.hasValidFinOpsFormCsrf(params["csrf"])) {
            exchange.respondJson(403, mapOf("error" to "invalid CSRF token"))
            return@post
        }
        val result = runCatching {
            val effectiveFromDate = java.time.LocalDate.parse(params.requiredFormValue("effective_from"))
            val effectiveToDate = params["effective_to"]?.trim()?.takeIf(String::isNotEmpty)
                ?.let(java.time.LocalDate::parse)
            val warningPercent = params.requiredFormValue("warning_percent").toInt()
            val criticalPercent = params.requiredFormValue("critical_percent").toInt()
            require(warningPercent in 1..100) { "warning percent must be between 1 and 100" }
            require(criticalPercent in warningPercent..100) { "critical percent must be at least the warning percent" }
            val amount = MoneyAmount.parse("USD", params.requiredFormValue("amount_usd"))
            val amountUsdMicros = service.convertToUsdMicros(amount, effectiveFromDate.toString())
            require(amountUsdMicros > 0L) { "budget amount must be positive" }
            val effectiveFrom = effectiveFromDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val effectiveToExclusive = effectiveToDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
            val fingerprint = listOf(
                amountUsdMicros.toString(),
                warningPercent.toString(),
                criticalPercent.toString(),
                effectiveFrom.toString(),
                effectiveToExclusive?.toString().orEmpty(),
            ).joinToString("|")
            val sourceHash = sha256Hex("cloud-platform-budget|$fingerprint")
            service.recordBudget(
                FinOpsBudget(
                    id = "budget:cloud-platform:${effectiveFromDate}:${sourceHash.take(24)}",
                    scope = FinOpsBudgetScope.COST_CLASS,
                    scopeValue = "cloud_platform",
                    amountUsdMicros = amountUsdMicros,
                    warningThresholdBasisPoints = warningPercent * 100,
                    criticalThresholdBasisPoints = criticalPercent * 100,
                    hardGate = false,
                    effectiveFrom = effectiveFrom,
                    effectiveToExclusive = effectiveToExclusive,
                    sourceHash = sourceHash,
                ),
                username,
            )
        }
        exchange.redirectWithLength(
            if (result.isSuccess) "/admin/spending?view=budgets&saved=budget"
            else "/admin/spending?view=budgets&error=invalid",
        )
    }

    post("/admin/spending/recurring") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@post
        val params = runCatching { exchange.receiveParameters() }.getOrElse {
            exchange.redirectWithLength("/admin/spending?view=budgets&error=invalid")
            return@post
        }
        if (!exchange.hasValidFinOpsFormCsrf(params["csrf"])) {
            exchange.respondJson(403, mapOf("error" to "invalid CSRF token"))
            return@post
        }
        val result = runCatching {
            val amount = MoneyAmount.parse(params.requiredFormValue("currency"), params.requiredFormValue("amount"))
            val startDate = java.time.LocalDate.parse(params.requiredFormValue("start_date"))
            val usdMicros = service.convertToUsdMicros(amount, startDate.toString())
            val values = listOf(
                amount.toString(), usdMicros.toString(), params.requiredFormValue("project"),
                params.requiredFormValue("environment"), params.requiredFormValue("vendor"),
                params.requiredFormValue("service"), params["sku"].orEmpty(),
                params.requiredFormValue("cadence"), startDate.toString(), params["end_date"].orEmpty(),
            ).joinToString("|")
            val sourceHash = sha256Hex(values)
            service.recordRecurringExpense(
                FinOpsRecurringExpense(
                    id = "recurring:${sourceHash.take(40)}",
                    amount = amount,
                    usdMicros = usdMicros,
                    project = params.requiredFormValue("project"),
                    environment = params.requiredFormValue("environment"),
                    vendor = params.requiredFormValue("vendor"),
                    service = params.requiredFormValue("service"),
                    sku = params["sku"]?.trim()?.takeIf(String::isNotEmpty),
                    cadence = FinOpsRecurrenceCadence.valueOf(params.requiredFormValue("cadence").uppercase()),
                    startDate = startDate.toString(),
                    endDateExclusive = params["end_date"]?.trim()?.takeIf(String::isNotEmpty),
                    sourceHash = sourceHash,
                ),
                username,
            )
        }
        exchange.redirectWithLength(
            if (result.isSuccess) "/admin/spending?view=budgets&saved=recurring" else "/admin/spending?view=budgets&error=invalid",
        )
    }

    post("/admin/spending/import.csv") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@post
        val declared = exchange.request.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > MAX_FINOPS_CSV_MULTIPART_BYTES) {
            exchange.redirectWithLength("/admin/spending?view=transactions&error=invalid")
            return@post
        }
        val form = runCatching {
            val boundary = requireNotNull(extractMultipartBoundary(exchange.request.headers["Content-Type"].orEmpty())) {
                "invalid CSV upload"
            }
            val body = exchange.request.bodyBytes()
            require(body.size <= MAX_FINOPS_CSV_MULTIPART_BYTES) { "CSV upload is too large" }
            parseMultipartFormData(body, boundary, "csv")
        }.getOrElse {
            exchange.redirectWithLength("/admin/spending?view=transactions&error=invalid")
            return@post
        }
        if (!exchange.hasValidFinOpsFormCsrf(form.fields["csrf"])) {
            exchange.respondJson(403, mapOf("error" to "invalid CSRF token"))
            return@post
        }
        val result = runCatching {
            val file = requireNotNull(form.file) { "CSV file is required" }
            require(file.bytes.size <= MAX_FINOPS_BODY_BYTES) { "CSV file is too large" }
            require(file.originalFilename?.lowercase()?.endsWith(".csv") == true) { "file must use a .csv extension" }
            val csv = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(file.bytes))
                .toString()
            service.importManualCsv(csv, username)
        }
        exchange.redirectWithLength(
            if (result.isSuccess) "/admin/spending?view=transactions&saved=csv" else "/admin/spending?view=transactions&error=invalid",
        )
    }

    get("/api/admin/finops/summary") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val query = exchange.finOpsQueryOrRespond() ?: return@get
        exchange.respondJson(200, service.summaryWithComparison(query, username))
    }

    get("/api/admin/finops/entries") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val query = exchange.finOpsQueryOrRespond() ?: return@get
        exchange.respondJson(200, service.entries(query, username))
    }

    get("/api/admin/finops/dimensions") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val query = exchange.finOpsQueryOrRespond() ?: return@get
        exchange.respondJson(200, service.dimensions(query, username))
    }

    get("/api/admin/finops/samurai/users") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val query = exchange.finOpsQueryOrRespond() ?: return@get
        exchange.respondJson(200, service.samuraiUsers(query, username))
    }

    get("/api/admin/finops/reconciliations") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val query = exchange.finOpsQueryOrRespond() ?: return@get
        exchange.respondJson(200, service.reconciliations(query, username))
    }

    get("/api/admin/finops/budgets") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        exchange.respondJson(200, service.budgets(username))
    }

    get("/api/admin/finops/fx-rates") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        val currency = exchange.request.queryParameter("currency")
        runCatching { service.fxRates(currency, username) }
            .onSuccess { exchange.respondJson(200, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to "invalid FX currency")) }
    }

    get("/api/admin/finops/recurring") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.exposeCsrfToken()
        exchange.respondJson(200, service.recurringExpenses(username))
    }

    get("/api/admin/finops/export.csv") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        exchange.response.setHeader("Content-Type", "text/csv; charset=utf-8")
        exchange.response.setHeader("Content-Disposition", "attachment; filename=felidai-spending.csv")
        exchange.response.setHeader("Cache-Control", "private, no-store")
        val query = exchange.finOpsQueryOrRespond(defaultLimit = 500) ?: return@get
        val export = service.exportCsv(query, username)
        exchange.response.setHeader("X-FinOps-Export-Row-Count", export.rowCount.toString())
        exchange.response.setHeader("X-FinOps-Export-Complete", (export.nextCursor == null).toString())
        export.nextCursor?.let { exchange.response.setHeader("X-FinOps-Next-Cursor", it) }
        exchange.respond(200, export.csv)
    }

    post("/api/admin/finops/receipt") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        if (receiptStore == null) {
            exchange.respondJson(503, mapOf("error" to "receipt storage is not configured"))
            return@post
        }
        val declared = exchange.request.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > MAX_FINOPS_RECEIPT_BYTES) {
            exchange.respondJson(413, mapOf("error" to "receipt is too large"))
            return@post
        }
        val result = runCatching {
            val filename = requireFinOpsReceiptFilename(
                requireNotNull(exchange.request.headers["X-FinOps-Receipt-Filename"]) { "receipt filename is required" },
            )
            val contentType = normalizeFinOpsReceiptContentType(
                requireNotNull(exchange.request.headers["Content-Type"]) { "receipt content type is required" },
            )
            val bytes = exchange.request.bodyBytes()
            require(bytes.size <= MAX_FINOPS_RECEIPT_BYTES) { "receipt is too large" }
            receiptStore.save(filename, contentType, bytes).also { service.recordReceiptUpload(it, username) }
        }
        result.onSuccess { exchange.respondJson(201, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to (it.message ?: "invalid receipt"))) }
    }

    get("/api/admin/finops/entries/:entryId/receipt") { exchange ->
        val username = exchange.requireFinOpsAdmin(ownerUsername) ?: return@get
        if (receiptStore == null) {
            exchange.respondJson(503, mapOf("error" to "receipt storage is not configured"))
            return@get
        }
        val result = runCatching {
            val reference = service.receiptForEntry(exchange.pathParam("entryId").orEmpty(), username)
            reference to receiptStore.load(reference)
        }
        result.onSuccess { (reference, bytes) ->
            exchange.response.setHeader("Content-Disposition", "attachment; filename=\"${reference.filename}\"")
            exchange.response.setHeader("Cache-Control", "private, no-store")
            exchange.response.setHeader("X-Content-Type-Options", "nosniff")
            exchange.response.setHeader("X-FinOps-Receipt-Sha256", reference.sha256)
            exchange.respondBytes(200, reference.contentType, bytes)
        }.onFailure { error ->
            exchange.respondJson(if (error is NoSuchElementException) 404 else 400, mapOf("error" to (error.message ?: "receipt unavailable")))
        }
    }

    post("/api/admin/finops/manual-entry") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val request = exchange.decodeFinOpsBody<ManualFinOpsEntryRequest>() ?: return@post
        runCatching {
            request.receipt?.let { reference ->
                requireNotNull(receiptStore) { "receipt storage is not configured" }.requirePresent(reference)
            }
            service.createManual(request, username)
        }
            .onSuccess { exchange.respondJson(201, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid entry"))) }
    }

    post("/api/admin/finops/import") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        if (exchange.request.headers["Content-Type"]?.substringBefore(';')?.trim()?.equals("text/csv", true) == true) {
            val body = exchange.readBoundedFinOpsBody() ?: return@post
            runCatching { service.importManualCsv(body, username) }
                .onSuccess { exchange.respondJson(if (it.inserted > 0) 201 else 200, it) }
                .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid CSV import"))) }
        } else {
            val request = exchange.decodeFinOpsBody<FinOpsIngestRequest>() ?: return@post
            runCatching { service.ingest(request, username) }
                .onSuccess { exchange.respondJson(if (it.state == "inserted") 201 else 200, it) }
                .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid import"))) }
        }
    }

    post("/api/admin/finops/reconcile") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val query = exchange.finOpsQueryOrRespond() ?: return@post
        val backfill = service.backfillAllocationResiduals(query, username)
        exchange.respondJson(200, FinOpsReconcileResult(
            allocationBackfill = backfill,
            allocationProjections = service.reconcileAllocationEntryProjections(username),
            reconciliations = service.reconciliations(query, username),
        ))
    }

    post("/api/admin/finops/budget") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val request = exchange.decodeFinOpsBody<FinOpsBudget>() ?: return@post
        runCatching { service.recordBudget(request, username) }
            .onSuccess { exchange.respondJson(if (it.state == "inserted") 201 else 200, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid budget"))) }
    }

    post("/api/admin/finops/fx-rate") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val request = exchange.decodeFinOpsBody<FinOpsFxRate>() ?: return@post
        runCatching { service.recordFxRate(request, username) }
            .onSuccess { exchange.respondJson(if (it.state == "inserted") 201 else 200, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid FX rate"))) }
    }

    post("/api/admin/finops/recurring") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val request = exchange.decodeFinOpsBody<FinOpsRecurringExpense>() ?: return@post
        runCatching { service.recordRecurringExpense(request, username) }
            .onSuccess { exchange.respondJson(if (it.state == "inserted") 201 else 200, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid recurring expense"))) }
    }

    post("/api/admin/finops/recurring/materialize") { exchange ->
        val username = exchange.requireFinOpsMutation(ownerUsername) ?: return@post
        val request = exchange.decodeFinOpsBody<FinOpsRecurringMaterializeRequest>() ?: return@post
        runCatching {
            service.materializeRecurringExpenses(java.time.LocalDate.parse(request.fromDate), java.time.LocalDate.parse(request.toDateExclusive), username)
        }.onSuccess { exchange.respondJson(200, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to (it.message ?: "invalid recurring materialization"))) }
    }

    post("/internal/finops/events") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@post
        }
        val request = exchange.decodeFinOpsBody<FinOpsIngestRequest>() ?: return@post
        runCatching { service.ingest(request, "samurai-service") }
            .onSuccess { exchange.respondJson(if (it.state == "inserted") 202 else 200, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid event"))) }
    }

    post("/internal/finops/import-run") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@post
        }
        val request = exchange.decodeFinOpsBody<FinOpsImportRun>() ?: return@post
        runCatching { service.recordImportRun(request, "gcp-billing-service") }
            .onSuccess { exchange.respondJson(if (it.state == "inserted") 202 else 200, it) }
            .onFailure { exchange.respondJson(if (it is FinOpsConflictException) 409 else 400, mapOf("error" to (it.message ?: "invalid import run"))) }
    }

    get("/internal/finops/receipt-attachments/:uploadId") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@get
        }
        runCatching { service.receiptAttachment(exchange.pathParam("uploadId").orEmpty()) }
            .onSuccess { attachment ->
                if (attachment == null) exchange.respondJson(404, mapOf("error" to "not found"))
                else exchange.respondJson(200, attachment)
            }
            .onFailure { exchange.respondJson(400, mapOf("error" to "invalid receipt upload id")) }
    }

    post("/internal/finops/recurring/materialize") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@post
        }
        val request = exchange.decodeFinOpsBody<FinOpsRecurringMaterializeRequest>() ?: return@post
        runCatching {
            service.materializeRecurringExpenses(
                java.time.LocalDate.parse(request.fromDate),
                java.time.LocalDate.parse(request.toDateExclusive),
                "cloudflare-scheduler",
            )
        }.onSuccess { exchange.respondJson(200, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to (it.message ?: "invalid recurring materialization"))) }
    }

    post("/internal/finops/reconcile") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@post
        }
        val query = exchange.finOpsQueryOrRespond(defaultLimit = 500) ?: return@post
        runCatching { service.backfillAllocationResiduals(query, "cloudflare-reconciler") }
            .onSuccess { exchange.respondJson(200, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to (it.message ?: "allocation reconciliation failed"))) }
    }

    post("/internal/finops/projection-reconciliation") { exchange ->
        if (!exchange.hasInternalToken(internalIngestToken)) {
            exchange.respondJson(404, mapOf("error" to "not found"))
            return@post
        }
        runCatching { service.reconcileAllocationEntryProjections("cloudflare-reconciler") }
            .onSuccess { exchange.respondJson(200, it) }
            .onFailure { exchange.respondJson(400, mapOf("error" to (it.message ?: "projection reconciliation failed"))) }
    }
}

private fun emptyFinOpsSummary(query: FinOpsQuery): FinOpsSummary = FinOpsSummary(
    from = query.from,
    toExclusive = query.toExclusive,
    finalizedSpendUsdMicros = 0L,
    accruedSpendUsdMicros = 0L,
    estimatedSpendUsdMicros = 0L,
    revenueUsdMicros = 0L,
    stripeFeesUsdMicros = 0L,
    directAiCostUsdMicros = 0L,
    grossMarginUsdMicros = 0L,
    contributionMarginUsdMicros = 0L,
    unreconciledUsdMicros = 0L,
)

internal fun newManualFinOpsSourceRecordId(): String = "owner:${UUID.randomUUID().toString().replace("-", "")}"

internal fun manualFinOpsSourceRecordId(retry: String?): String = runCatching {
    requireManualFinOpsSourceRecordId(requireNotNull(retry))
}.getOrElse { newManualFinOpsSourceRecordId() }

internal fun requireManualFinOpsSourceRecordId(value: String): String = value.also {
    require(it.matches(Regex("^owner:[a-f0-9]{32}$"))) { "invalid manual expense mutation id" }
}

private const val MAX_FINOPS_RECEIPT_BYTES = 26_214_400
private const val MAX_FINOPS_CSV_MULTIPART_BYTES = 1_114_112
internal const val MAX_FINOPS_MANUAL_MULTIPART_BYTES = 28_311_552

private fun Exchange.finOpsQuery(defaultLimit: Int = 100): FinOpsQuery {
    val default = defaultMonthRange()
    val from = parseFinOpsRangeParameter(request.queryParameter("from")) ?: default.first
    val to = parseFinOpsRangeParameter(request.queryParameter("to")) ?: default.second
    val status = request.queryParameter("status")?.uppercase()?.let { runCatching { FinOpsStatus.valueOf(it) }.getOrNull() }
    val order = when (request.queryParameter("order")?.trim()?.lowercase()) {
        null, "", "newest" -> FinOpsEntryOrder.NEWEST
        "oldest" -> FinOpsEntryOrder.OLDEST
        else -> throw IllegalArgumentException("invalid FinOps entry order")
    }
    val samuraiUserOrder = when (request.queryParameter("user_order")?.trim()?.lowercase()) {
        null, "", "ascending" -> SamuraiUserOrder.ASCENDING
        "descending" -> SamuraiUserOrder.DESCENDING
        else -> throw IllegalArgumentException("invalid Samurai user order")
    }
    val samuraiUserLimit = request.queryParameter("user_limit")?.let { raw ->
        requireNotNull(raw.toIntOrNull()) { "invalid Samurai user limit" }.also {
            require(it in 1..100) { "invalid Samurai user limit" }
        }
    } ?: 50
    return FinOpsQuery(
        from = from,
        toExclusive = to,
        project = request.queryParameter("project")?.takeIf(String::isNotBlank),
        environment = request.queryParameter("environment")?.takeIf(String::isNotBlank),
        vendor = request.queryParameter("vendor")?.takeIf(String::isNotBlank),
        service = request.queryParameter("service")?.takeIf(String::isNotBlank),
        userId = request.queryParameter("user")?.takeIf(String::isNotBlank),
        provider = request.queryParameter("provider")?.takeIf(String::isNotBlank),
        modelId = request.queryParameter("model")?.takeIf(String::isNotBlank),
        runId = request.queryParameter("run")?.takeIf(String::isNotBlank),
        status = status,
        order = order,
        limit = request.queryParameter("limit")?.toIntOrNull()?.coerceIn(1, 500) ?: defaultLimit,
        cursor = request.queryParameter("cursor")?.takeIf(String::isNotBlank),
        samuraiUserOrder = samuraiUserOrder,
        samuraiUserLimit = samuraiUserLimit,
        samuraiUserCursor = request.queryParameter("user_cursor")?.takeIf(String::isNotBlank),
    )
}

internal fun parseFinOpsRangeParameter(value: String?): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    normalized.toLongOrNull()?.let { return it }
    return java.time.LocalDate.parse(normalized)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

private suspend fun Exchange.finOpsQueryOrRespond(defaultLimit: Int = 100): FinOpsQuery? =
    runCatching { finOpsQuery(defaultLimit) }.getOrElse {
        respondJson(400, mapOf("error" to "invalid FinOps query"))
        null
    }

private suspend fun Exchange.requireFinOpsAdmin(ownerUsername: () -> String, browser: Boolean = false): String? {
    val current = session()
    val username = current?.get("username") as? String
    val mustChange = current?.get("mustChangePassword")?.toString()?.toBoolean() ?: false
    val owner = ownerUsername()
    if (!isFinOpsOwnerSession(username, mustChange, owner)) {
        if (browser) redirectWithLength("/admin/login?next=/admin/spending") else respondJson(401, mapOf("error" to "unauthorized"))
        return null
    }
    return username
}

internal fun isFinOpsOwnerSession(username: String?, mustChangePassword: Boolean, ownerUsername: String): Boolean =
    username != null && !mustChangePassword && ownerUsername.isNotBlank() && constantTimeEquals(ownerUsername, username)

internal fun normalizeUploadedFinOpsReceiptFilename(value: String): String {
    val basename = value.substringAfterLast('/').substringAfterLast('\\').trim()
    val normalized = basename.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.', '_').take(192)
    return requireFinOpsReceiptFilename(normalized.takeIf { it.firstOrNull()?.isLetterOrDigit() == true } ?: "receipt")
}

private suspend fun Exchange.requireFinOpsMutation(ownerUsername: () -> String): String? {
    val username = requireFinOpsAdmin(ownerUsername) ?: return null
    val expected = session()?.get(FINOPS_CSRF_SESSION_KEY)?.toString()
    val supplied = request.headers["X-CSRF-Token"]
    if (expected == null || supplied == null || !constantTimeEquals(expected, supplied)) {
        respondJson(403, mapOf("error" to "invalid CSRF token"))
        return null
    }
    return username
}

private suspend fun Exchange.ensureCsrfToken(): String {
    val current = requireNotNull(session()) { "session middleware is required" }
    val existing = current.get(FINOPS_CSRF_SESSION_KEY)?.toString()
    if (existing != null) return existing
    return UUID.randomUUID().toString().also { current.set(FINOPS_CSRF_SESSION_KEY, it) }
}

private suspend fun Exchange.exposeCsrfToken() {
    response.setHeader("X-CSRF-Token", ensureCsrfToken())
    response.setHeader("Cache-Control", "private, no-store")
}

private suspend inline fun <reified T> Exchange.decodeFinOpsBody(): T? {
    val body = readBoundedFinOpsBody() ?: return null
    return runCatching { finOpsJson.decodeFromString<T>(body) }.getOrElse {
        respondJson(400, mapOf("error" to "invalid request body"))
        null
    }
}

private suspend fun Exchange.readBoundedFinOpsBody(): String? {
    val declared = request.headers["Content-Length"]?.toLongOrNull()
    if (declared != null && declared > MAX_FINOPS_BODY_BYTES) {
        respondJson(413, mapOf("error" to "request body is too large"))
        return null
    }
    val body = request.bodyText()
    if (body.toByteArray().size > MAX_FINOPS_BODY_BYTES) {
        respondJson(413, mapOf("error" to "request body is too large"))
        return null
    }
    return body
}

private fun Exchange.hasInternalToken(expected: String?): Boolean {
    if (expected.isNullOrBlank()) return false
    val supplied = request.headers["Authorization"]?.removePrefix("Bearer ") ?: return false
    return constantTimeEquals(expected, supplied)
}

private suspend fun Exchange.hasValidFinOpsFormCsrf(supplied: String?): Boolean {
    val expected = session()?.get(FINOPS_CSRF_SESSION_KEY)?.toString()
    return expected != null && supplied != null && constantTimeEquals(expected, supplied)
}

private fun Map<String, String>.requiredFormValue(name: String): String = requireNotNull(this[name]?.trim()?.takeIf(String::isNotEmpty)) {
    "$name is required"
}

private fun constantTimeEquals(expected: String, supplied: String): Boolean = MessageDigest.isEqual(
    expected.toByteArray(Charsets.UTF_8),
    supplied.toByteArray(Charsets.UTF_8),
)
