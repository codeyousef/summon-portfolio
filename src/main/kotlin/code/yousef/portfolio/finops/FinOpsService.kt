package code.yousef.portfolio.finops

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class FinOpsService(
    private val repository: FinOpsRepository,
    private val coverageSources: Set<String> = setOf("gcp", "cloudflare", "samurai", "stripe"),
    private val samuraiIdentityResolver: SamuraiIdentityResolver = NoopSamuraiIdentityResolver,
    private val coverageStartAt: Long = 0L,
) {
    init {
        require(coverageStartAt >= 0L) { "FinOps coverage start cannot be negative" }
    }
    fun summaryWithComparison(query: FinOpsQuery, actor: String? = null, now: Long = System.currentTimeMillis()): FinOpsSummary {
        val current = summary(query, actor)
        val duration = query.toExclusive - query.from
        val previousFrom = query.from - duration
        val previous = if (previousFrom >= coverageStartAt) {
            summary(query.copy(from = previousFrom, toExclusive = query.from, cursor = null), null)
        } else {
            null
        }
        val currentSpend = Math.addExact(
            Math.addExact(current.finalizedSpendUsdMicros, current.accruedSpendUsdMicros),
            current.estimatedSpendUsdMicros,
        )
        val previousSpend = previous?.let {
            Math.addExact(
                Math.addExact(it.finalizedSpendUsdMicros, it.accruedSpendUsdMicros),
                it.estimatedSpendUsdMicros,
            )
        }
        val forecastFrom = maxOf(query.from, coverageStartAt)
        val forecastDuration = (query.toExclusive - forecastFrom).coerceAtLeast(0L)
        val elapsed = (now - forecastFrom).coerceIn(0L, forecastDuration)
        val forecast = if (elapsed == 0L || elapsed == forecastDuration) currentSpend else
            java.math.BigDecimal.valueOf(currentSpend)
                .multiply(java.math.BigDecimal.valueOf(forecastDuration))
                .divide(java.math.BigDecimal.valueOf(elapsed), 0, java.math.RoundingMode.HALF_EVEN)
                .longValueExact()
        val changeBasisPoints = if (previousSpend == null || previousSpend == 0L) null else
            java.math.BigDecimal.valueOf(currentSpend - previousSpend)
                .multiply(java.math.BigDecimal("10000"))
                .divide(java.math.BigDecimal.valueOf(previousSpend), 0, java.math.RoundingMode.HALF_EVEN)
                .longValueExact()
        return current.copy(
            forecastSpendUsdMicros = forecast,
            previousPeriodSpendUsdMicros = previousSpend ?: 0L,
            monthOverMonthBasisPoints = changeBasisPoints,
        )
    }

    fun ingest(request: FinOpsIngestRequest, actor: String): FinOpsIngestReceipt {
        requireCoveredEntry(request.entry)
        require(request.allocations.all { it.entryId == request.entry.id }) {
            "all allocations must reference the ingested entry"
        }
        require(request.allocations.map(CostAllocation::id).toSet().size == request.allocations.size) {
            "allocation ids must be unique within an entry"
        }
        val allocated = request.allocations.fold(0L) { total, allocation ->
            Math.addExact(total, allocation.allocatedUsdMicros)
        }
        require(allocated <= request.entry.usdMicros) {
            "allocations cannot exceed the entry amount"
        }
        val residual = request.entry.usdMicros - allocated
        val residualId = "allocation:residual:${sha256Hex(request.entry.id).take(40)}"
        require(request.allocations.none { it.id == residualId }) { "residual allocation id is reserved" }
        val allocations = if (residual == 0L) request.allocations else request.allocations + CostAllocation(
            id = residualId,
            entryId = request.entry.id,
            project = request.entry.project,
            allocatedUsdMicros = residual,
            method = AllocationMethod.RESIDUAL,
        )
        val receiptAttachment = request.entry.toReceiptAttachment()
        receiptAttachment?.let { attachment ->
            val existing = repository.getReceiptAttachment(attachment.uploadId)
            if (existing != null && existing != attachment) {
                throw FinOpsConflictException("receipt upload id is already attached to another ledger entry")
            }
        }
        val result = repository.putEntry(request.entry)
        if (result == FinOpsWriteResult.Conflict) {
            throw FinOpsConflictException("entry id conflicts with another source payload")
        }
        receiptAttachment?.let { attachment ->
            if (repository.putReceiptAttachment(attachment) == FinOpsWriteResult.Conflict) {
                throw FinOpsConflictException("receipt upload id is already attached to another ledger entry")
            }
        }
        if (result == FinOpsWriteResult.Duplicate) {
            val expectedById = allocations.associateBy(CostAllocation::id)
            val existing = repository.listAllocations(setOf(request.entry.id))
            require(existing.all { expectedById[it.id] == it }) {
                "immutable entry conflicts with an existing allocation set"
            }
        }
        repository.putAllocations(allocations)
        // Rollup writes have their own deterministic idempotency keys. Retry
        // them for duplicates so a crash after the immutable entry insert but
        // before rollup completion is recoverable by the ordinary Queue replay.
        repository.ensureDailyRollups(request.entry, allocations)
        repository.ensureUnallocatedRollup(request.entry, allocations)
        mutationAudit(actor, "ingest", request.entry.id, request.entry.ingestAuditDetail())
        return when (result) {
            FinOpsWriteResult.Conflict -> error("unreachable")
            FinOpsWriteResult.Duplicate -> FinOpsIngestReceipt(request.entry.id, "duplicate", allocations.size)
            FinOpsWriteResult.Inserted -> FinOpsIngestReceipt(request.entry.id, "inserted", allocations.size)
        }
    }

    fun createManual(request: ManualFinOpsEntryRequest, actor: String): FinOpsIngestReceipt {
        return ingest(FinOpsIngestRequest(manualEntry(request)), actor)
    }

    private fun manualEntry(request: ManualFinOpsEntryRequest): FinOpsEntry {
        require(request.metadata.keys.none { it.startsWith("receipt.") }) {
            "receipt metadata is reserved; use the typed receipt reference"
        }
        val id = deterministicId("manual", request.sourceRecordId)
        return FinOpsEntry(
            id = id,
            source = "manual",
            sourceRecordId = request.sourceRecordId,
            direction = request.direction,
            amount = request.amount,
            usdMicros = request.usdMicros,
            incurredAt = request.incurredAt,
            invoiceMonth = invoiceMonth(request.incurredAt),
            project = request.project,
            environment = request.environment,
            vendor = request.vendor,
            service = request.service,
            sku = request.sku,
            status = request.status,
            reconciliationKey = request.reconciliationKey,
            sourceHash = request.sourceHash,
            metadata = request.metadata + request.receipt.toMetadata(),
        )
    }

    fun importManualCsv(value: String, actor: String): FinOpsCsvImportResult {
        val requests = parseManualFinOpsCsv(value)
        // Construct every immutable ledger entry before the first write so a
        // semantically invalid later row cannot leave a partial CSV import.
        val entries = requests.map(::manualEntry)
        entries.forEach(::requireCoveredEntry)
        val receipts = entries.map { ingest(FinOpsIngestRequest(it), actor) }
        audit(actor, "import_csv", "finops", mapOf("rows" to requests.size.toString()))
        return FinOpsCsvImportResult(
            rows = receipts.size,
            inserted = receipts.count { it.state == "inserted" },
            duplicates = receipts.count { it.state == "duplicate" },
        )
    }

    private fun requireCoveredEntry(entry: FinOpsEntry) {
        require(entry.incurredAt >= coverageStartAt) {
            "entry predates the configured FinOps coverage period"
        }
    }

    fun receiptForEntry(entryId: String, actor: String): FinOpsReceiptReference {
        require(entryId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9:._-]{0,239}$"))) { "invalid entry id" }
        val reference = repository.getEntry(entryId)?.metadata?.toReceiptReference()
            ?: throw NoSuchElementException("receipt not found")
        audit(actor, "read_receipt", entryId, mapOf("sha256" to reference.sha256))
        return reference
    }

    fun recordReceiptUpload(reference: FinOpsReceiptReference, actor: String) {
        audit(
            actor,
            "upload_receipt",
            reference.storageKey,
            mapOf(
                "sha256" to reference.sha256,
                "contentType" to reference.contentType,
                "size" to reference.size.toString(),
            ) + reference.uploadId?.let { mapOf("uploadId" to it) }.orEmpty(),
        )
    }

    fun receiptAttachment(uploadId: String): FinOpsReceiptAttachment? {
        require(uploadId.matches(Regex("^[a-f0-9]{32}$"))) { "invalid receipt upload id" }
        return repository.getReceiptAttachment(uploadId)
    }

    fun recordImportRun(run: FinOpsImportRun, actor: String): FinOpsIngestReceipt {
        val result = repository.putImportRun(run)
        if (result != FinOpsWriteResult.Conflict) {
            mutationAudit(actor, "import_run", run.id, mapOf("source" to run.source, "records" to run.sourceRecords.toString()))
        }
        return when (result) {
            FinOpsWriteResult.Conflict -> throw FinOpsConflictException("import run id conflicts with another source payload")
            FinOpsWriteResult.Duplicate -> FinOpsIngestReceipt(run.id, "duplicate", 0)
            FinOpsWriteResult.Inserted -> FinOpsIngestReceipt(run.id, "inserted", 0)
        }
    }

    fun recordBudget(budget: FinOpsBudget, actor: String): FinOpsIngestReceipt {
        val result = repository.putBudget(budget)
        if (result != FinOpsWriteResult.Conflict) {
            mutationAudit(actor, "budget_create", budget.id, mapOf("scope" to budget.scope.name.lowercase()))
        }
        return when (result) {
            FinOpsWriteResult.Conflict -> throw FinOpsConflictException("budget id conflicts with another source payload")
            FinOpsWriteResult.Duplicate -> FinOpsIngestReceipt(budget.id, "duplicate", 0)
            FinOpsWriteResult.Inserted -> FinOpsIngestReceipt(budget.id, "inserted", 0)
        }
    }

    fun budgets(actor: String? = null): List<FinOpsBudget> {
        actor?.let { audit(it, "read_budgets", "finops") }
        return repository.listBudgets()
    }

    fun recordFxRate(rate: FinOpsFxRate, actor: String): FinOpsIngestReceipt {
        val result = repository.putFxRate(rate)
        if (result != FinOpsWriteResult.Conflict) {
            mutationAudit(actor, "fx_rate_create", rate.id, mapOf("currency" to rate.sourceCurrency, "source" to rate.source))
        }
        return when (result) {
            FinOpsWriteResult.Conflict -> throw FinOpsConflictException("FX rate id conflicts with another source payload")
            FinOpsWriteResult.Duplicate -> FinOpsIngestReceipt(rate.id, "duplicate", 0)
            FinOpsWriteResult.Inserted -> FinOpsIngestReceipt(rate.id, "inserted", 0)
        }
    }

    fun fxRates(sourceCurrency: String? = null, actor: String? = null): List<FinOpsFxRate> {
        val normalized = sourceCurrency?.trim()?.uppercase()
        require(normalized == null || normalized.matches(Regex("^[A-Z]{3}$"))) { "invalid FX currency" }
        actor?.let { audit(it, "read_fx_rates", normalized ?: "all") }
        return repository.listFxRates(normalized)
    }

    fun recordRecurringExpense(expense: FinOpsRecurringExpense, actor: String): FinOpsIngestReceipt {
        val result = repository.putRecurringExpense(expense)
        if (result != FinOpsWriteResult.Conflict) {
            mutationAudit(actor, "recurring_expense_create", expense.id, mapOf("cadence" to expense.cadence.name.lowercase()))
        }
        return when (result) {
            FinOpsWriteResult.Conflict -> throw FinOpsConflictException("recurring expense id conflicts with another payload")
            FinOpsWriteResult.Duplicate -> FinOpsIngestReceipt(expense.id, "duplicate", 0)
            FinOpsWriteResult.Inserted -> FinOpsIngestReceipt(expense.id, "inserted", 0)
        }
    }

    fun recurringExpenses(actor: String? = null): List<FinOpsRecurringExpense> {
        actor?.let { audit(it, "read_recurring_expenses", "finops") }
        return repository.listRecurringExpenses()
    }

    fun materializeRecurringExpenses(
        fromDate: LocalDate,
        toDateExclusive: LocalDate,
        actor: String,
    ): FinOpsRecurringMaterialization {
        require(toDateExclusive > fromDate && toDateExclusive <= fromDate.plusYears(10)) {
            "recurring materialization range must be positive and no longer than 10 years"
        }
        val coverageStartDate = Instant.ofEpochMilli(coverageStartAt).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val coveredFromDate = maxOf(fromDate, coverageStartDate)
        val templates = repository.listRecurringExpenses().filter(FinOpsRecurringExpense::enabled)
        val receipts = if (coveredFromDate >= toDateExclusive) emptyList() else templates.flatMap { template ->
            template.occurrences(coveredFromDate, toDateExclusive).map { date ->
                createManual(
                    ManualFinOpsEntryRequest(
                        sourceRecordId = "recurring:${template.id}:$date",
                        direction = FinOpsDirection.EXPENSE,
                        amount = template.amount,
                        usdMicros = template.usdMicros,
                        incurredAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                        project = template.project,
                        environment = template.environment,
                        vendor = template.vendor,
                        service = template.service,
                        sku = template.sku,
                        status = FinOpsStatus.FINALIZED,
                        reconciliationKey = "manual-recurring:${template.id}:${date.toString().take(7)}",
                        sourceHash = sha256Hex("${template.sourceHash}:$date"),
                        metadata = mapOf("recurringExpenseId" to template.id, "recurrence" to template.cadence.name.lowercase()),
                    ),
                    actor,
                )
            }
        }
        audit(actor, "materialize_recurring_expenses", "finops", mapOf("occurrences" to receipts.size.toString()))
        return FinOpsRecurringMaterialization(
            templates = templates.size,
            occurrences = receipts.size,
            inserted = receipts.count { it.state == "inserted" },
            duplicates = receipts.count { it.state == "duplicate" },
        )
    }

    fun convertToUsdMicros(
        amount: MoneyAmount,
        effectiveDate: String,
        invoiceRate: FinOpsFxRate? = null,
    ): Long {
        if (amount.currency == "USD") return amount.decimal().movePointRight(USD_MICRO_SCALE)
            .setScale(0, java.math.RoundingMode.HALF_EVEN).longValueExact()
        val date = java.time.LocalDate.parse(effectiveDate)
        invoiceRate?.let { rate ->
            require(rate.provenance == FinOpsFxProvenance.INVOICE) { "explicit invoice rate must have invoice provenance" }
            require(rate.sourceCurrency == amount.currency && java.time.LocalDate.parse(rate.effectiveDate) <= date) {
                "invoice FX rate does not cover the amount date"
            }
            return rate.convertToUsdMicros(amount)
        }
        val candidates = repository.listFxRates(amount.currency).filter { rate ->
            rate.sourceCurrency == amount.currency && java.time.LocalDate.parse(rate.effectiveDate) <= date
        }
        val rate = candidates.maxWithOrNull(
            compareBy<FinOpsFxRate> { java.time.LocalDate.parse(it.effectiveDate) }
                .thenBy { it.provenance.ordinal }
                .thenBy { it.id },
        ) ?: throw IllegalArgumentException("missing FX rate for ${amount.currency} on $effectiveDate")
        return rate.convertToUsdMicros(amount)
    }

    fun entries(query: FinOpsQuery, actor: String? = null): FinOpsEntryPage {
        actor?.let { audit(it, "read_entries", "finops", mapOf("limit" to query.limit.toString())) }
        val covered = query.coveredBy(coverageStartAt) ?: return FinOpsEntryPage(emptyList())
        return repository.listEntries(covered)
    }

    fun backfillAllocationResiduals(query: FinOpsQuery, actor: String): FinOpsAllocationBackfillResult {
        val covered = query.coveredBy(coverageStartAt)
            ?: return FinOpsAllocationBackfillResult(processed = 0, residualAllocations = 0)
        val page = repository.listEntries(covered)
        var residuals = 0
        page.entries.forEach { entry ->
            val residualId = "allocation:residual:${sha256Hex(entry.id).take(40)}"
            val existing = repository.listAllocations(setOf(entry.id))
            val hadResidual = existing.any { it.id == residualId }
            val allocations = existing.filterNot { it.id == residualId }
            val receipt = ingest(FinOpsIngestRequest(entry, allocations), actor)
            if (!hadResidual && receipt.allocationCount > allocations.size) residuals += 1
        }
        audit(actor, "backfill_allocation_residuals", "finops", mapOf(
            "processed" to page.entries.size.toString(),
            "residualAllocations" to residuals.toString(),
        ))
        return FinOpsAllocationBackfillResult(page.entries.size, residuals, page.nextCursor)
    }

    fun reconcileAllocationEntryProjections(actor: String): FinOpsAllocationProjectionReconciliation {
        val result = repository.reconcileAllocationEntryProjections()
        audit(actor, "reconcile_allocation_entry_projections", "finops", mapOf(
            "expectedCount" to result.expectedCount.toString(),
            "actualCount" to result.actualCount.toString(),
            "missingCount" to result.missingCount.toString(),
            "unexpectedCount" to result.unexpectedCount.toString(),
            "mismatchedCount" to result.mismatchedCount.toString(),
            "invalidCount" to result.invalidCount.toString(),
            "orphanedAllocationCount" to result.orphanedAllocationCount.toString(),
            "ready" to result.ready.toString(),
        ))
        return result
    }

    fun summary(query: FinOpsQuery, actor: String? = null): FinOpsSummary {
        if (query.from < coverageStartAt) {
            val gap = coverageGapLabel()
            if (query.toExclusive <= coverageStartAt) {
                actor?.let { audit(it, "read_summary", "finops") }
                return FinOpsSummary(
                    from = query.from,
                    toExclusive = query.toExclusive,
                    finalizedSpendUsdMicros = 0,
                    accruedSpendUsdMicros = 0,
                    estimatedSpendUsdMicros = 0,
                    revenueUsdMicros = 0,
                    stripeFeesUsdMicros = 0,
                    directAiCostUsdMicros = 0,
                    grossMarginUsdMicros = 0,
                    contributionMarginUsdMicros = 0,
                    unreconciledUsdMicros = 0,
                    coverageGaps = listOf(gap),
                )
            }
            val covered = summary(query.copy(from = coverageStartAt, cursor = null), actor)
            return covered.copy(
                from = query.from,
                toExclusive = query.toExclusive,
                coverageGaps = (listOf(gap) + covered.coverageGaps).distinct(),
            )
        }
        if (query.userId != null || query.provider != null || query.modelId != null || query.runId != null) {
            return allocationFilteredSummary(query, actor)
        }
        val (rollups, boundaryEntries) = summaryData(query)
        val finalized = boundaryEntries.filter { it.status == FinOpsStatus.FINALIZED || it.status == FinOpsStatus.ADJUSTMENT }
        val finalizedSpend = rollups.sumOf(FinOpsDailyRollup::finalizedSpendUsdMicros) + finalized.sumOf(FinOpsEntry::signedSpendMicros)
        val rawAccrued = rollups.sumOf(FinOpsDailyRollup::accruedSpendUsdMicros) +
            boundaryEntries.filter { it.status == FinOpsStatus.ACCRUED }.sumOf(FinOpsEntry::signedSpendMicros)
        val rawEstimated = rollups.sumOf(FinOpsDailyRollup::estimatedSpendUsdMicros) +
            boundaryEntries.filter { it.status == FinOpsStatus.ESTIMATED }.sumOf(FinOpsEntry::signedSpendMicros)
        val reconciliationStates = if (query.status == null) reconciliationCostStates(query, boundaryEntries) else emptyMap()
        val (accrued, estimated) = if (query.status == null) {
            visibleUnfinalizedAmounts(reconciliationStates)
        } else {
            rawAccrued to rawEstimated
        }
        val revenue = rollups.sumOf(FinOpsDailyRollup::revenueUsdMicros) + finalized.sumOf(FinOpsEntry::signedRevenueMicros)
        val stripeFees = rollups.sumOf(FinOpsDailyRollup::stripeFeesUsdMicros) +
            finalized.sumOf(FinOpsEntry::signedStripeFeeMicros)
        val boundaryAllocations = repository.listAllocations(boundaryEntries.mapTo(mutableSetOf(), FinOpsEntry::id))
            .filter { it.method == AllocationMethod.RESIDUAL && it.userId == null }
            .groupBy(CostAllocation::entryId)
        val unallocated = rollups.sumOf(FinOpsDailyRollup::unallocatedUsdMicros) + boundaryEntries.sumOf { entry ->
            boundaryAllocations[entry.id].orEmpty().sumOf { allocation -> entry.signedCostAllocation(allocation) }
        }
        val coverageStartDate = Instant.ofEpochMilli(coverageStartAt)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .toString()
        // A receipt whose half-open window ends at or before the configured
        // future-only boundary provides no coverage for this dashboard.
        val importRuns = repository.listImportRuns()
            .filter { it.toDateExclusive > coverageStartDate }
        val latestRuns = importRuns.groupBy { it.source.lowercase() }
            .mapValues { (_, runs) -> runs.maxBy(FinOpsImportRun::completedAt) }
        val requestedFrom = Instant.ofEpochMilli(query.from).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val tomorrow = floorUtcDay(System.currentTimeMillis()) + 86_400_000L
        val effectiveToExclusive = if (query.from < tomorrow) minOf(query.toExclusive, tomorrow) else query.toExclusive
        val requestedTo = Instant.ofEpochMilli(
            effectiveToExclusive - 1L,
        ).atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1)
        val coverageGaps = coverageSources.mapNotNull { source ->
            val covering = importRuns.filter { run ->
                run.source.equals(source, true) &&
                    java.time.LocalDate.parse(run.fromDate) <= requestedFrom &&
                    java.time.LocalDate.parse(run.toDateExclusive) >= requestedTo
            }.maxByOrNull(FinOpsImportRun::completedAt)
            when (covering?.status) {
                "succeeded" -> null
                "partial" -> "$source: partial coverage" +
                    (covering.failureCode?.let { " ($it)" } ?: "")
                "failed" -> "$source: latest covering import failed" +
                    (covering.failureCode?.let { " ($it)" } ?: "")
                else -> "$source: no reconciled import coverage"
            }
        }.sorted()
        actor?.let { audit(it, "read_summary", "finops") }
        val budget = activeCloudPlatformBudget(query)
        val byCategory = if (query.status == null) visibleBreakdown(reconciliationStates) { it.costClass }
            else mergedBreakdown(rollups, boundaryEntries, query.status, FinOpsDailyRollup::costClass) {
                it.metadata["costClass"] ?: "unclassified"
            }
        val byProject = if (query.status == null) visibleBreakdown(reconciliationStates) { it.project }
            else mergedBreakdown(rollups, boundaryEntries, query.status, FinOpsDailyRollup::project, FinOpsEntry::project)
        val directAi = byCategory.firstOrNull { it.key == "direct_ai" }?.usdMicros ?: 0L
        val contributionCosts = byProject.firstOrNull { it.key.equals("samurai", true) }?.usdMicros ?: 0L
        val dailyFinalizedSpend = dailyFinalizedSpend(
            rollups.map { it.dayStart to it.finalizedSpendUsdMicros },
            finalized.map { floorUtcDay(it.incurredAt) to it.signedSpendMicros },
        )
        return FinOpsSummary(
            from = query.from,
            toExclusive = query.toExclusive,
            finalizedSpendUsdMicros = finalizedSpend,
            accruedSpendUsdMicros = accrued,
            estimatedSpendUsdMicros = estimated,
            revenueUsdMicros = revenue,
            stripeFeesUsdMicros = stripeFees,
            directAiCostUsdMicros = directAi,
            grossMarginUsdMicros = revenue - directAi - stripeFees,
            contributionMarginUsdMicros = revenue - contributionCosts,
            unreconciledUsdMicros = accrued + estimated,
            unallocatedUsdMicros = unallocated,
            cloudPlatformBudgetUsdMicros = budget?.amountUsdMicros ?: 100_000_000L,
            budgetWarningUsdMicros = budget?.warningUsdMicros() ?: 75_000_000L,
            budgetCriticalUsdMicros = budget?.criticalUsdMicros() ?: 90_000_000L,
            cloudPlatformSpendUsdMicros = byCategory.firstOrNull { it.key == "cloud_platform" }?.usdMicros ?: 0L,
            byProject = byProject,
            byEnvironment = if (query.status == null) visibleBreakdown(reconciliationStates) { it.environment }
                else mergedBreakdown(rollups, boundaryEntries, query.status, FinOpsDailyRollup::environment, FinOpsEntry::environment),
            byCategory = byCategory,
            byVendor = if (query.status == null) visibleBreakdown(reconciliationStates) { it.vendor }
                else mergedBreakdown(rollups, boundaryEntries, query.status, FinOpsDailyRollup::vendor, FinOpsEntry::vendor),
            byService = if (query.status == null) visibleBreakdown(reconciliationStates) { it.service }
                else mergedBreakdown(rollups, boundaryEntries, query.status, FinOpsDailyRollup::service, FinOpsEntry::service),
            dailyFinalizedSpend = dailyFinalizedSpend,
            coverageGaps = coverageGaps,
            dataFreshness = latestRuns.mapValues { it.value.completedAt }.toSortedMap(),
            connectorStates = latestRuns.mapValues { (_, run) ->
                FinOpsConnectorState(
                    status = run.status,
                    completedAt = run.completedAt,
                    failureCode = run.failureCode,
                )
            }.toSortedMap(),
        )
    }

    private fun coverageGapLabel(): String = "studio: coverage begins " +
        Instant.ofEpochMilli(coverageStartAt).atZone(java.time.ZoneOffset.UTC).toLocalDate()

    private fun allocationFilteredSummary(query: FinOpsQuery, actor: String?): FinOpsSummary {
        val base = summary(
            query.copy(userId = null, provider = null, modelId = null, runId = null, cursor = null),
            null,
        )
        val attributed = attributedEntries(query)
        val states = mutableMapOf<ReconciliationCostKey, ReconciliationCostState>()
        attributed.forEach { value ->
            states.merge(
                value.entry.reconciliationCostKey(),
                value.entry.toReconciliationCostState(value.costUsdMicros),
                ReconciliationCostState::plus,
            )
        }
        val finalized = attributed.filter { it.entry.status == FinOpsStatus.FINALIZED || it.entry.status == FinOpsStatus.ADJUSTMENT }
        val finalizedSpend = finalized.sumOf(AttributedFinOpsEntry::costUsdMicros)
        val (accrued, estimated) = visibleUnfinalizedAmounts(states)
        val revenue = finalized.sumOf(AttributedFinOpsEntry::revenueUsdMicros)
        val stripeFees = finalized.filter { value ->
            value.entry.vendor.equals("stripe", true) && value.entry.metadata["accountingClass"] == "expense" &&
                value.entry.direction in setOf(FinOpsDirection.FEE, FinOpsDirection.CREDIT, FinOpsDirection.REFUND)
        }.sumOf(AttributedFinOpsEntry::costUsdMicros)
        val byProject = visibleBreakdown(states) { it.project }
        val byEnvironment = visibleBreakdown(states) { it.environment }
        val byCategory = visibleBreakdown(states) { it.costClass }
        val byVendor = visibleBreakdown(states) { it.vendor }
        val byService = visibleBreakdown(states) { it.service }
        val directAi = byCategory.firstOrNull { it.key == "direct_ai" }?.usdMicros ?: 0L
        val contributionCosts = byProject.firstOrNull { it.key.equals("samurai", true) }?.usdMicros ?: 0L
        val dailyFinalizedSpend = dailyFinalizedSpend(
            emptyList(),
            finalized.map { floorUtcDay(it.entry.incurredAt) to it.costUsdMicros },
        )
        actor?.let { audit(it, "read_summary", "finops") }
        return base.copy(
            finalizedSpendUsdMicros = finalizedSpend,
            accruedSpendUsdMicros = accrued,
            estimatedSpendUsdMicros = estimated,
            revenueUsdMicros = revenue,
            stripeFeesUsdMicros = stripeFees,
            directAiCostUsdMicros = directAi,
            grossMarginUsdMicros = revenue - directAi - stripeFees,
            contributionMarginUsdMicros = revenue - contributionCosts,
            unreconciledUsdMicros = accrued + estimated,
            unallocatedUsdMicros = 0L,
            cloudPlatformSpendUsdMicros = byCategory.firstOrNull { it.key == "cloud_platform" }?.usdMicros ?: 0L,
            byProject = byProject,
            byEnvironment = byEnvironment,
            byCategory = byCategory,
            byVendor = byVendor,
            byService = byService,
            dailyFinalizedSpend = dailyFinalizedSpend,
        )
    }

    private fun dailyFinalizedSpend(
        rollupValues: List<Pair<Long, Long>>,
        boundaryValues: List<Pair<Long, Long>>,
    ): List<FinOpsBreakdown> = (rollupValues + boundaryValues)
        .groupingBy(Pair<Long, Long>::first)
        .fold(0L) { total, value -> Math.addExact(total, value.second) }
        .entries
        .sortedBy(Map.Entry<Long, Long>::key)
        .map { (dayStart, usdMicros) -> FinOpsBreakdown(utcDate(dayStart).toString(), usdMicros) }

    private fun attributedEntries(query: FinOpsQuery): List<AttributedFinOpsEntry> {
        val entries = readAll(query)
        val matchingByEntry = repository.listAllocations(entries.mapTo(mutableSetOf(), FinOpsEntry::id))
            .filter { allocation -> query.matchesAllocation(allocation) }
            .groupBy(CostAllocation::entryId)
        return entries.mapNotNull { entry ->
            matchingByEntry[entry.id]?.takeIf { it.isNotEmpty() }?.let { allocations ->
                AttributedFinOpsEntry(
                    entry = entry,
                    costUsdMicros = allocations.sumOf { allocation -> entry.signedCostAllocation(allocation) },
                    revenueUsdMicros = allocations.sumOf { allocation -> entry.signedRevenueAllocation(allocation) },
                )
            }
        }
    }

    private fun activeCloudPlatformBudget(query: FinOpsQuery): FinOpsBudget? = repository.listBudgets()
        .asSequence()
        .filter(FinOpsBudget::enabled)
        .filter { it.scope == FinOpsBudgetScope.COST_CLASS && it.scopeValue == "cloud_platform" }
        .filter { it.effectiveFrom < query.toExclusive && (it.effectiveToExclusive == null || it.effectiveToExclusive > query.from) }
        .maxWithOrNull(compareBy<FinOpsBudget>(FinOpsBudget::effectiveFrom).thenBy(FinOpsBudget::id))

    fun dimensions(query: FinOpsQuery, actor: String? = null): FinOpsDimensions {
        val covered = query.coveredBy(coverageStartAt)
        if (covered == null) {
            actor?.let { audit(it, "read_dimensions", "finops") }
            return FinOpsDimensions(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val (rollups, boundaryEntries) = summaryData(covered)
        val userRollups = if (covered.runId == null) {
            repository.listUserDailyRollups(covered.copy(cursor = null, limit = 500))
        } else {
            emptyList()
        }
        actor?.let { audit(it, "read_dimensions", "finops") }
        return FinOpsDimensions(
            projects = (rollups.map(FinOpsDailyRollup::project) + boundaryEntries.map(FinOpsEntry::project)).distinct().sorted(),
            environments = (rollups.map(FinOpsDailyRollup::environment) + boundaryEntries.map(FinOpsEntry::environment)).distinct().sorted(),
            vendors = (rollups.map(FinOpsDailyRollup::vendor) + boundaryEntries.map(FinOpsEntry::vendor)).distinct().sorted(),
            services = (rollups.map(FinOpsDailyRollup::service) + boundaryEntries.map(FinOpsEntry::service)).distinct().sorted(),
            providers = userRollups.map(FinOpsUserDailyRollup::provider).distinct().sorted(),
            models = userRollups.map(FinOpsUserDailyRollup::modelId).distinct().sorted(),
        )
    }

    fun reconciliations(query: FinOpsQuery, actor: String? = null): List<FinOpsReconciliation> {
        actor?.let { audit(it, "read_reconciliations", "finops") }
        val covered = query.coveredBy(coverageStartAt) ?: return emptyList()
        if (
            covered.from == floorUtcDay(covered.from) &&
            covered.toExclusive == floorUtcDay(covered.toExclusive) &&
            covered.status == null &&
            covered.userId == null &&
            covered.provider == null &&
            covered.modelId == null
        ) {
            return repository.listReconciliationRollups(covered)
                .groupBy(FinOpsReconciliationRollup::reconciliationKey)
                .map { (key, rollups) ->
                    reconciliation(
                        key = key,
                        estimated = rollups.sumOf { it.estimatedUsdMicros + it.accruedUsdMicros },
                        finalized = rollups.sumOf(FinOpsReconciliationRollup::finalizedUsdMicros),
                        finalizedCount = rollups.sumOf(FinOpsReconciliationRollup::finalizedEntryCount),
                    )
                }
                .sortedBy(FinOpsReconciliation::key)
        }
        val values = if (covered.userId != null || covered.provider != null || covered.modelId != null || covered.runId != null) {
            attributedEntries(covered)
        } else {
            readAll(covered).map { entry -> AttributedFinOpsEntry(entry, entry.signedSpendMicros, entry.signedRevenueMicros) }
        }
        return values
        .groupBy { it.entry.reconciliationKey }
        .map { (key, entries) ->
            val estimated = entries.filter { it.entry.status == FinOpsStatus.ESTIMATED || it.entry.status == FinOpsStatus.ACCRUED }
                .sumOf(AttributedFinOpsEntry::costUsdMicros)
            val finalized = entries.filter { it.entry.status == FinOpsStatus.FINALIZED || it.entry.status == FinOpsStatus.ADJUSTMENT }
                .sumOf(AttributedFinOpsEntry::costUsdMicros)
            val finalizedCount = entries.count { it.entry.status == FinOpsStatus.FINALIZED || it.entry.status == FinOpsStatus.ADJUSTMENT }.toLong()
            reconciliation(key, estimated, finalized, finalizedCount)
        }
        .sortedBy(FinOpsReconciliation::key)
    }

    private fun reconciliation(key: String, estimated: Long, finalized: Long, finalizedCount: Long): FinOpsReconciliation {
        val difference = finalized - estimated
        return FinOpsReconciliation(
            key = key,
            estimatedUsdMicros = estimated,
            finalizedUsdMicros = finalized,
            differenceUsdMicros = difference,
            state = when {
                finalizedCount == 0L -> "awaiting_invoice"
                difference == 0L -> "matched"
                else -> "difference"
            },
        )
    }

    fun samuraiUsers(query: FinOpsQuery, actor: String? = null): SamuraiUserSpendPage {
        actor?.let {
            audit(it, "read_samurai_users", "finops", mapOf(
                "limit" to query.samuraiUserLimit.toString(),
                "order" to query.samuraiUserOrder.name.lowercase(),
            ))
        }
        val covered = query.coveredBy(coverageStartAt) ?: return SamuraiUserSpendPage(emptyList())
        val idPage = repository.listSamuraiUserIdsPage(covered)
        return SamuraiUserSpendPage(
            users = enrichSamuraiIdentities(idPage.userIds.mapNotNull { userId ->
                val userQuery = covered.copy(userId = userId, cursor = null)
                if (
                    userQuery.from == floorUtcDay(userQuery.from) &&
                    userQuery.toExclusive == floorUtcDay(userQuery.toExclusive) &&
                    userQuery.status == null &&
                    userQuery.runId == null
                ) {
                    samuraiUserFromRollups(userId, repository.listUserDailyRollups(userQuery))
                } else {
                    samuraiUserFromEntries(userId, userQuery)
                }
            }),
            nextCursor = idPage.nextCursor,
        )
    }

    private fun samuraiUserFromRollups(userId: String, rollups: List<FinOpsUserDailyRollup>): SamuraiUserSpend? {
        if (rollups.isEmpty()) return null
        val providerCost = rollups.sumOf(FinOpsUserDailyRollup::providerCostUsdMicros)
        val invoiceCost = rollups.sumOf(FinOpsUserDailyRollup::allocatedInvoiceUsdMicros)
        val reconciledCosts = rollups.reconciledUserRollupCosts()
        val effectiveCost = reconciledCosts.sumOf(UserCostBreakdown::usdMicros)
        val revenue = rollups.sumOf(FinOpsUserDailyRollup::revenueUsdMicros)
        return SamuraiUserSpend(
            userId = userId,
            providerCostUsdMicros = providerCost,
            allocatedInvoiceUsdMicros = invoiceCost,
            creditsBurned = rollups.sumOf(FinOpsUserDailyRollup::creditsBurned),
            requests = rollups.sumOf(FinOpsUserDailyRollup::requests),
            revenueUsdMicros = revenue,
            grossMarginUsdMicros = revenue - effectiveCost,
            fallbacks = rollups.sumOf(FinOpsUserDailyRollup::fallbacks),
            toolCalls = rollups.sumOf(FinOpsUserDailyRollup::toolCalls),
            byProvider = reconciledCosts.groupBy(UserCostBreakdown::provider)
                .map { (key, values) -> FinOpsBreakdown(key, values.sumOf(UserCostBreakdown::usdMicros)) }
                .sortedByDescending(FinOpsBreakdown::usdMicros),
            byModel = reconciledCosts.groupBy(UserCostBreakdown::modelId)
                .map { (key, values) -> FinOpsBreakdown(key, values.sumOf(UserCostBreakdown::usdMicros)) }
                .sortedByDescending(FinOpsBreakdown::usdMicros),
        )
    }

    private fun samuraiUserFromEntries(userId: String, query: FinOpsQuery): SamuraiUserSpend? {
        val entries = readAll(query).filter { it.project.equals("samurai", true) }
        val byId = entries.associateBy(FinOpsEntry::id)
        val allocations = repository.listAllocations(byId.keys)
            .filter { it.userId == userId }
            .filter { query.matchesAllocation(it) }
        if (allocations.isEmpty()) return null
        val attributed = allocations.mapNotNull { allocation ->
            byId[allocation.entryId]?.let { allocation to it }
        }
        val metered = attributed.filter { (allocation, _) -> allocation.method == AllocationMethod.METERED }
        val invoice = attributed.filter { (allocation, _) -> allocation.method != AllocationMethod.METERED }
        val providerCost = metered.sumOf { (allocation, entry) -> entry.signedCostAllocation(allocation) }
        val invoiceCost = invoice.sumOf { (allocation, entry) -> entry.signedCostAllocation(allocation) }
        val reconciled = attributed.reconciledAttributedUserCosts()
        val effectiveCost = reconciled.sumOf(UserCostBreakdown::usdMicros)
        val revenue = attributed.sumOf { (allocation, entry) -> entry.signedRevenueAllocation(allocation) }
        val meteredEntries = metered
            .filter { (_, entry) -> entry.metadata["costClass"] == "direct_ai" }
            .map { it.second }
            .distinctBy(FinOpsEntry::id)
        return SamuraiUserSpend(
            userId = userId,
            providerCostUsdMicros = providerCost,
            allocatedInvoiceUsdMicros = invoiceCost,
            creditsBurned = meteredEntries.sumOf { it.metadata["creditsBurned"]?.toLongOrNull() ?: 0L },
            requests = meteredEntries.size.toLong(),
            revenueUsdMicros = revenue,
            grossMarginUsdMicros = revenue - effectiveCost,
            fallbacks = meteredEntries.count { it.metadata["fallback"].equals("true", true) }.toLong(),
            toolCalls = meteredEntries.sumOf { it.metadata["toolCallCount"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L },
            byProvider = reconciled.groupBy(UserCostBreakdown::provider)
                .map { (key, values) -> FinOpsBreakdown(key, values.sumOf(UserCostBreakdown::usdMicros)) }
                .sortedByDescending(FinOpsBreakdown::usdMicros),
            byModel = reconciled.groupBy(UserCostBreakdown::modelId)
                .map { (key, values) -> FinOpsBreakdown(key, values.sumOf(UserCostBreakdown::usdMicros)) }
                .sortedByDescending(FinOpsBreakdown::usdMicros),
        )
    }

    private fun enrichSamuraiIdentities(users: List<SamuraiUserSpend>): List<SamuraiUserSpend> {
        if (users.isEmpty()) return users
        val identities = runCatching {
            samuraiIdentityResolver.resolve(users.mapTo(linkedSetOf(), SamuraiUserSpend::userId))
        }.getOrDefault(emptyMap())
        return users.map { user ->
            identities[user.userId]?.let { identity ->
                user.copy(displayName = identity.displayName, email = identity.email)
            } ?: user
        }
    }

    private fun FinOpsQuery.matchesAllocation(allocation: CostAllocation): Boolean =
        (userId == null || allocation.userId == userId) &&
            (provider == null || allocation.provider == provider) &&
            (modelId == null || allocation.modelId == modelId) &&
            (runId == null || allocation.runId == runId)

    private fun FinOpsEntry.signedCostAllocation(allocation: CostAllocation): Long {
        if (direction == FinOpsDirection.REVENUE || metadata["accountingClass"] == "revenue") return 0L
        return when (direction) {
            FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -allocation.allocatedUsdMicros
            else -> allocation.allocatedUsdMicros
        }
    }

    private fun FinOpsEntry.signedRevenueAllocation(allocation: CostAllocation): Long {
        if (direction != FinOpsDirection.REVENUE && metadata["accountingClass"] != "revenue") return 0L
        return when (direction) {
            FinOpsDirection.CREDIT, FinOpsDirection.REFUND -> -allocation.allocatedUsdMicros
            else -> allocation.allocatedUsdMicros
        }
    }

    private fun List<FinOpsUserDailyRollup>.reconciledUserRollupCosts(): List<UserCostBreakdown> =
        groupBy(FinOpsUserDailyRollup::reconciliationKey).values.flatMap { rollups ->
            val invoice = rollups.filter { it.method != AllocationMethod.METERED }
            (invoice.ifEmpty { rollups.filter { it.method == AllocationMethod.METERED } }).map { rollup ->
                UserCostBreakdown(
                    provider = rollup.provider,
                    modelId = rollup.modelId,
                    usdMicros = if (rollup.method == AllocationMethod.METERED) {
                        rollup.providerCostUsdMicros
                    } else {
                        rollup.allocatedInvoiceUsdMicros
                    },
                )
            }
        }

    private fun List<Pair<CostAllocation, FinOpsEntry>>.reconciledAttributedUserCosts(): List<UserCostBreakdown> =
        groupBy { (_, entry) -> entry.reconciliationKey }.values.flatMap { values ->
            val invoice = values.filter { (allocation, _) -> allocation.method != AllocationMethod.METERED }
            (invoice.ifEmpty { values.filter { (allocation, _) -> allocation.method == AllocationMethod.METERED } })
                .map { (allocation, entry) ->
                    UserCostBreakdown(
                        provider = allocation.provider ?: entry.vendor,
                        modelId = allocation.modelId ?: entry.sku ?: "unattributed",
                        usdMicros = entry.signedCostAllocation(allocation),
                    )
                }
        }

    fun exportCsv(query: FinOpsQuery, actor: String): FinOpsCsvExport {
        val page = query.coveredBy(coverageStartAt)?.let(repository::listEntries) ?: FinOpsEntryPage(emptyList())
        audit(actor, "export_csv", "finops", mapOf(
            "rows" to page.entries.size.toString(),
            "hasMore" to (page.nextCursor != null).toString(),
        ))
        val csv = buildString {
            appendLine("id,incurred_at,invoice_month,direction,status,project,environment,vendor,service,sku,currency,source_amount,usd,source,source_record_id,reconciliation_key")
            page.entries.forEach { entry ->
                appendLine(listOf(
                    entry.id,
                    Instant.ofEpochMilli(entry.incurredAt).toString(),
                    entry.invoiceMonth,
                    entry.direction.name.lowercase(),
                    entry.status.name.lowercase(),
                    entry.project,
                    entry.environment,
                    entry.vendor,
                    entry.service,
                    entry.sku.orEmpty(),
                    entry.amount.currency,
                    entry.amount.decimal().toPlainString(),
                    formatUsdMicros(entry.usdMicros),
                    entry.source,
                    entry.sourceRecordId,
                    entry.reconciliationKey,
                ).joinToString(",") { csvCell(it) })
            }
        }
        return FinOpsCsvExport(csv = csv, nextCursor = page.nextCursor, rowCount = page.entries.size)
    }

    private fun readAll(query: FinOpsQuery): List<FinOpsEntry> {
        val result = mutableListOf<FinOpsEntry>()
        var cursor = query.cursor
        do {
            val page = repository.listEntries(query.copy(limit = 500, cursor = cursor))
            result += page.entries
            cursor = page.nextCursor
        } while (cursor != null)
        return result
    }

    private fun summaryData(query: FinOpsQuery): Pair<List<FinOpsDailyRollup>, List<FinOpsEntry>> {
        if (query.userId != null || query.provider != null || query.modelId != null || query.runId != null) {
            return emptyList<FinOpsDailyRollup>() to readAll(query)
        }
        val fullDayFrom = ceilUtcDay(query.from)
        val fullDayToExclusive = floorUtcDay(query.toExclusive)
        if (fullDayFrom >= fullDayToExclusive) return emptyList<FinOpsDailyRollup>() to readAll(query)
        val rollups = repository.listDailyRollups(
            query.copy(from = fullDayFrom, toExclusive = fullDayToExclusive, cursor = null, limit = 500),
        )
        val boundaries = buildList {
            if (query.from < fullDayFrom) addAll(readAll(query.copy(toExclusive = fullDayFrom, cursor = null)))
            if (fullDayToExclusive < query.toExclusive) {
                addAll(readAll(query.copy(from = fullDayToExclusive, cursor = null)))
            }
        }
        return rollups to boundaries
    }

    private fun reconciliationCostStates(
        query: FinOpsQuery,
        boundaryEntries: List<FinOpsEntry>,
    ): Map<ReconciliationCostKey, ReconciliationCostState> {
        val states = mutableMapOf<ReconciliationCostKey, ReconciliationCostState>()
        if (query.userId != null || query.provider != null || query.modelId != null || query.runId != null) {
            readAll(query).forEach { entry -> states.merge(entry.reconciliationCostKey(), entry.toReconciliationCostState(), ReconciliationCostState::plus) }
        } else {
            val fullDayFrom = ceilUtcDay(query.from)
            val fullDayToExclusive = floorUtcDay(query.toExclusive)
            if (fullDayFrom < fullDayToExclusive) {
                repository.listReconciliationRollups(
                    query.copy(from = fullDayFrom, toExclusive = fullDayToExclusive, cursor = null, limit = 500),
                ).forEach { rollup ->
                    states.merge(
                        rollup.reconciliationCostKey(),
                        ReconciliationCostState(
                            accrued = rollup.accruedUsdMicros,
                            estimated = rollup.estimatedUsdMicros,
                            finalized = rollup.finalizedUsdMicros,
                            finalizedCount = rollup.finalizedEntryCount,
                        ),
                        ReconciliationCostState::plus,
                    )
                }
            }
            boundaryEntries.forEach { entry -> states.merge(entry.reconciliationCostKey(), entry.toReconciliationCostState(), ReconciliationCostState::plus) }
        }
        return states
    }

    private fun visibleUnfinalizedAmounts(states: Map<ReconciliationCostKey, ReconciliationCostState>): Pair<Long, Long> {
        val finalizedKeys = states.filterValues(ReconciliationCostState::hasFinalized).keys.mapTo(mutableSetOf(), ReconciliationCostKey::reconciliationKey)
        val visible = states.filterKeys { it.reconciliationKey !in finalizedKeys }.values
        return visible.sumOf(ReconciliationCostState::accrued) to visible.sumOf(ReconciliationCostState::estimated)
    }

    private fun visibleBreakdown(
        states: Map<ReconciliationCostKey, ReconciliationCostState>,
        dimension: (ReconciliationCostKey) -> String,
    ): List<FinOpsBreakdown> {
        val finalizedKeys = states.filterValues(ReconciliationCostState::hasFinalized).keys.mapTo(mutableSetOf(), ReconciliationCostKey::reconciliationKey)
        val totals = mutableMapOf<String, Long>()
        states.forEach { (key, state) ->
            val visible = if (key.reconciliationKey in finalizedKeys) state.finalized else Math.addExact(state.accrued, state.estimated)
            totals[dimension(key)] = Math.addExact(totals[dimension(key)] ?: 0L, visible)
        }
        return totals.map { FinOpsBreakdown(it.key, it.value) }.sortedByDescending(FinOpsBreakdown::usdMicros)
    }

    private fun breakdown(entries: List<FinOpsEntry>, key: (FinOpsEntry) -> String): List<FinOpsBreakdown> = entries
        .groupBy(key)
        .map { (name, values) -> FinOpsBreakdown(name, values.sumOf(FinOpsEntry::signedSpendMicros)) }
        .sortedByDescending(FinOpsBreakdown::usdMicros)

    private fun mergedBreakdown(
        rollups: List<FinOpsDailyRollup>,
        entries: List<FinOpsEntry>,
        status: FinOpsStatus,
        rollupKey: (FinOpsDailyRollup) -> String,
        entryKey: (FinOpsEntry) -> String,
    ): List<FinOpsBreakdown> {
        val totals = mutableMapOf<String, Long>()
        rollups.forEach {
            val amount = when (status) {
                FinOpsStatus.FINALIZED, FinOpsStatus.ADJUSTMENT -> it.finalizedSpendUsdMicros
                FinOpsStatus.ACCRUED -> it.accruedSpendUsdMicros
                FinOpsStatus.ESTIMATED -> it.estimatedSpendUsdMicros
            }
            totals[rollupKey(it)] = Math.addExact(totals[rollupKey(it)] ?: 0L, amount)
        }
        entries.forEach { totals[entryKey(it)] = Math.addExact(totals[entryKey(it)] ?: 0L, it.signedSpendMicros) }
        return totals.map { FinOpsBreakdown(it.key, it.value) }.sortedByDescending(FinOpsBreakdown::usdMicros)
    }

    private fun audit(actor: String, action: String, target: String, detail: Map<String, String> = emptyMap()) {
        repository.appendAudit(
            FinOpsAuditEvent(
                id = "audit:${System.currentTimeMillis()}:${UUID.randomUUID()}",
                actor = actor.take(120),
                action = action,
                target = target.take(240),
                detail = detail,
            )
        )
    }

    private fun mutationAudit(actor: String, action: String, target: String, detail: Map<String, String> = emptyMap()) {
        val normalizedActor = actor.take(120)
        val normalizedTarget = target.take(240)
        repository.appendAudit(
            FinOpsAuditEvent(
                id = "audit:mutation:${sha256Hex("$normalizedActor\u0000$action\u0000$normalizedTarget").take(40)}",
                actor = normalizedActor,
                action = action,
                target = normalizedTarget,
                detail = detail,
            )
        )
    }
}

private data class ReconciliationCostState(
    val accrued: Long = 0L,
    val estimated: Long = 0L,
    val finalized: Long = 0L,
    val finalizedCount: Long = 0L,
) {
    fun hasFinalized(): Boolean = finalizedCount > 0L || finalized != 0L

    fun plus(other: ReconciliationCostState): ReconciliationCostState = ReconciliationCostState(
        accrued = Math.addExact(accrued, other.accrued),
        estimated = Math.addExact(estimated, other.estimated),
        finalized = Math.addExact(finalized, other.finalized),
        finalizedCount = Math.addExact(finalizedCount, other.finalizedCount),
    )
}

private data class ReconciliationCostKey(
    val reconciliationKey: String,
    val project: String,
    val environment: String,
    val costClass: String,
    val vendor: String,
    val service: String,
)

private data class AttributedFinOpsEntry(
    val entry: FinOpsEntry,
    val costUsdMicros: Long,
    val revenueUsdMicros: Long,
)

private data class UserCostBreakdown(
    val provider: String,
    val modelId: String,
    val usdMicros: Long,
)

private fun FinOpsEntry.reconciliationCostKey(): ReconciliationCostKey = ReconciliationCostKey(
    reconciliationKey = reconciliationKey,
    project = project,
    environment = environment,
    costClass = metadata["costClass"] ?: when {
        source.equals("gcp", true) || source.equals("cloudflare", true) ||
            vendor.equals("google-cloud", true) || vendor.equals("cloudflare", true) -> "cloud_platform"
        else -> "unclassified"
    },
    vendor = vendor,
    service = service,
)

private fun FinOpsReconciliationRollup.reconciliationCostKey(): ReconciliationCostKey = ReconciliationCostKey(
    reconciliationKey = reconciliationKey,
    project = project,
    environment = environment,
    costClass = costClass,
    vendor = vendor,
    service = service,
)

private fun FinOpsEntry.toReconciliationCostState(): ReconciliationCostState = when (status) {
    FinOpsStatus.ACCRUED -> ReconciliationCostState(accrued = signedSpendMicros)
    FinOpsStatus.ESTIMATED -> ReconciliationCostState(estimated = signedSpendMicros)
    FinOpsStatus.FINALIZED, FinOpsStatus.ADJUSTMENT -> ReconciliationCostState(
        finalized = signedSpendMicros,
        finalizedCount = 1L,
    )
}

private fun FinOpsEntry.toReconciliationCostState(costUsdMicros: Long): ReconciliationCostState = when (status) {
    FinOpsStatus.ACCRUED -> ReconciliationCostState(accrued = costUsdMicros)
    FinOpsStatus.ESTIMATED -> ReconciliationCostState(estimated = costUsdMicros)
    FinOpsStatus.FINALIZED, FinOpsStatus.ADJUSTMENT -> ReconciliationCostState(
        finalized = costUsdMicros,
        finalizedCount = 1L,
    )
}

private fun FinOpsRecurringExpense.occurrences(fromDate: LocalDate, toDateExclusive: LocalDate): List<LocalDate> {
    val start = LocalDate.parse(startDate)
    val end = endDateExclusive?.let(LocalDate::parse) ?: LocalDate.MAX
    return generateSequence(0L) { offset -> (offset + 1L).takeIf { it <= 2_400L } }
        .map { offset ->
            when (cadence) {
                FinOpsRecurrenceCadence.MONTHLY -> YearMonth.from(start).plusMonths(offset)
                    .atDay(start.dayOfMonth.coerceAtMost(YearMonth.from(start).plusMonths(offset).lengthOfMonth()))
                FinOpsRecurrenceCadence.YEARLY -> YearMonth.of(Math.toIntExact(start.year.toLong() + offset), start.month)
                    .atDay(start.dayOfMonth.coerceAtMost(YearMonth.of(Math.toIntExact(start.year.toLong() + offset), start.month).lengthOfMonth()))
            }
        }
        .takeWhile { it < toDateExclusive && it < end }
        .filter { it >= fromDate }
        .toList()
}

private fun FinOpsReceiptReference?.toMetadata(): Map<String, String> = this?.let { reference ->
    mapOf(
        "receipt.storageKey" to reference.storageKey,
        "receipt.sha256" to reference.sha256,
        "receipt.filename" to reference.filename,
        "receipt.contentType" to reference.contentType,
        "receipt.size" to reference.size.toString(),
    ) + reference.uploadId?.let { mapOf("receipt.uploadId" to it) }.orEmpty()
}.orEmpty()

private fun Map<String, String>.toReceiptReference(): FinOpsReceiptReference? {
    val storageKey = this["receipt.storageKey"] ?: return null
    return FinOpsReceiptReference(
        storageKey = storageKey,
        sha256 = requireNotNull(this["receipt.sha256"]) { "receipt metadata is missing its digest" },
        filename = requireNotNull(this["receipt.filename"]) { "receipt metadata is missing its filename" },
        contentType = requireNotNull(this["receipt.contentType"]) { "receipt metadata is missing its content type" },
        size = requireNotNull(this["receipt.size"]?.toLongOrNull()) { "receipt metadata has an invalid size" },
        uploadId = this["receipt.uploadId"],
    )
}

private fun FinOpsEntry.ingestAuditDetail(): Map<String, String> = buildMap {
    put("source", source)
    metadata["receipt.uploadId"]?.let { put("receiptUploadId", it) }
    metadata["receipt.sha256"]?.let { put("receiptSha256", it) }
}

private fun FinOpsEntry.toReceiptAttachment(): FinOpsReceiptAttachment? {
    val reference = metadata.toReceiptReference() ?: return null
    val uploadId = reference.uploadId ?: return null
    return FinOpsReceiptAttachment(
        uploadId = uploadId,
        entryId = id,
        storageKey = reference.storageKey,
        sha256 = reference.sha256,
    )
}

private const val DAY_MILLIS = 86_400_000L

private fun floorUtcDay(epochMillis: Long): Long = epochMillis - Math.floorMod(epochMillis, DAY_MILLIS)

private fun ceilUtcDay(epochMillis: Long): Long {
    val floor = floorUtcDay(epochMillis)
    return if (floor == epochMillis) floor else Math.addExact(floor, DAY_MILLIS)
}

class FinOpsConflictException(message: String) : IllegalStateException(message)

private fun FinOpsQuery.coveredBy(coverageStartAt: Long): FinOpsQuery? {
    if (toExclusive <= coverageStartAt) return null
    return if (from < coverageStartAt) copy(from = coverageStartAt, cursor = null) else this
}

fun deterministicId(source: String, sourceRecordId: String): String = "$source:${sha256Hex("$source:$sourceRecordId").take(40)}"

fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun csvCell(value: String): String {
    val flattened = value.replace("\r", " ").replace("\n", " ")
    val safe = if (flattened.firstOrNull() in setOf('=', '+', '-', '@', '\t')) "'$flattened" else flattened
    return "\"${safe.replace("\"", "\"\"")}\""
}
