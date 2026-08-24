package code.yousef.portfolio.finops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FinOpsServiceTest {
    @Test
    fun `future-only coverage rejects old writes and exposes earlier periods as an explicit gap`() {
        val cutoff = 1_800_000_000_000L
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet(), coverageStartAt = cutoff)
        val boundary = request("future-boundary", 1_000_000L, FinOpsStatus.FINALIZED)
        val before = boundary.copy(entry = boundary.entry.copy(
            id = deterministicId("test", "before-cutoff"),
            sourceRecordId = "before-cutoff",
            incurredAt = cutoff - 1,
            sourceHash = sha256Hex("before-cutoff"),
        ))

        assertFailsWith<IllegalArgumentException> { service.ingest(before, "test") }
        service.ingest(boundary, "test")

        val summary = service.summary(query())
        assertEquals(1_000_000L, summary.finalizedSpendUsdMicros)
        assertTrue(summary.coverageGaps.single().startsWith("studio: coverage begins "))
        assertEquals(listOf(boundary.entry.id), service.entries(query()).entries.map(FinOpsEntry::id))
        assertTrue(service.summary(query().copy(toExclusive = cutoff)).coverageGaps.isNotEmpty())

        val comparison = service.summaryWithComparison(
            query().copy(from = cutoff - 1_000L, toExclusive = cutoff + 1_000L),
            now = cutoff + 500L,
        )
        assertEquals(2_000_000L, comparison.forecastSpendUsdMicros)
        assertEquals(0L, comparison.previousPeriodSpendUsdMicros)
        assertEquals(null, comparison.monthOverMonthBasisPoints)
    }

    @Test
    fun `import runs reject malformed coverage dates and timestamps`() {
        val valid = FinOpsImportRun(
            id = "import:gcp:2027-01-03",
            source = "gcp",
            fromDate = "2027-01-01",
            toDateExclusive = "2027-02-01",
            sourceRecords = 0,
            entries = 0,
            status = "succeeded",
            completedAt = 1_800_000_000_000L,
            sourceHash = sha256Hex("gcp-zero-spend"),
        )

        assertFailsWith<IllegalArgumentException> { valid.copy(fromDate = "2027-02-30") }
        assertFailsWith<IllegalArgumentException> { valid.copy(toDateExclusive = valid.fromDate) }
        assertFailsWith<IllegalArgumentException> { valid.copy(toDateExclusive = "2026-12-31") }
        assertFailsWith<IllegalArgumentException> { valid.copy(completedAt = -1L) }
        assertFailsWith<IllegalArgumentException> { valid.copy(failureCode = "raw error: denied") }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(status = "failed", failureCode = "provider_timeout_with_internal_details")
        }
        assertFailsWith<IllegalArgumentException> {
            FinOpsConnectorState("failed", 1L, "provider_timeout_with_internal_details")
        }
        assertFailsWith<IllegalArgumentException> { valid.copy(failureCode = "workload_identity") }
        assertEquals(
            "workload_identity",
            valid.copy(status = "failed", failureCode = "workload_identity").failureCode,
        )
        assertEquals(
            "unsupported_currency",
            valid.copy(status = "partial", failureCode = "unsupported_currency").failureCode,
        )
        assertEquals(
            FINOPS_CONNECTOR_FAILURE_CODES,
            setOf(
                "configuration",
                "workload_identity",
                "provider_authorization",
                "provider_api",
                "provider_schema",
                "bigquery_authorization",
                "bigquery_query",
                "bigquery_schema",
                "query_limit",
                "pagination_limit",
                "queue_delivery",
                "record_validation",
                "unsupported_currency",
                "cost_unavailable",
                "restricted_api_unavailable",
                "unknown",
            ),
        )
    }

    @Test
    fun `owner dashboard reads produce audit events without auditing internal comparison reads`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val query = query()

        service.summaryWithComparison(query, "owner")
        service.entries(query, "owner")
        service.dimensions(query, "owner")
        service.samuraiUsers(query, "owner")
        service.reconciliations(query, "owner")
        service.budgets("owner")
        service.fxRates(actor = "owner")
        service.recurringExpenses("owner")
        service.exportCsv(query, "owner")

        assertEquals(
            listOf(
                "export_csv",
                "read_budgets",
                "read_dimensions",
                "read_entries",
                "read_fx_rates",
                "read_reconciliations",
                "read_recurring_expenses",
                "read_samurai_users",
                "read_summary",
            ),
            repository.auditEvents().map(FinOpsAuditEvent::action).sorted(),
        )
    }

    @Test
    fun `immutable mutation retry repairs a failed audit without duplicating the audit event`() {
        val repository = InMemoryFinOpsRepository()
        val failingAuditRepository = object : FinOpsRepository by repository {
            var failNextAudit = true

            override fun appendAudit(event: FinOpsAuditEvent) {
                if (failNextAudit) {
                    failNextAudit = false
                    throw IllegalStateException("simulated audit outage")
                }
                repository.appendAudit(event)
            }
        }
        val service = FinOpsService(failingAuditRepository, coverageSources = emptySet())
        val request = request("manual:audit-repair", 100_000L, FinOpsStatus.FINALIZED)

        assertFailsWith<IllegalStateException> { service.ingest(request, "owner") }
        assertEquals("duplicate", service.ingest(request, "owner").state)
        assertEquals(listOf("ingest"), repository.auditEvents().map(FinOpsAuditEvent::action))

        assertEquals("duplicate", service.ingest(request, "owner").state)
        assertEquals(1, repository.auditEvents().size)
    }

    @Test
    fun `money parsing is fixed point and rejects excessive precision`() {
        assertEquals(MoneyAmount("USD", 1234567, 4), MoneyAmount.parse("usd", "123.4567"))
        assertFailsWith<IllegalArgumentException> { MoneyAmount.parse("USD", "0.1234567891") }
    }

    @Test
    fun `FX conversion is fixed point and invoice provenance takes precedence`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository)
        val stored = FinOpsFxRate(
            id = "fx:sar:2027-01-01:central",
            sourceCurrency = "SAR",
            effectiveDate = "2027-01-01",
            usdPerMajorUnit = MoneyAmount("USD", 250_000_000, 9),
            provenance = FinOpsFxProvenance.CENTRAL_BANK,
            source = "central-bank",
            sourceHash = sha256Hex("stored-sar-rate"),
        )
        service.recordFxRate(stored, "test")
        val invoice = stored.copy(
            id = "fx:sar:2027-01-15:invoice",
            effectiveDate = "2027-01-15",
            usdPerMajorUnit = MoneyAmount("USD", 266_666_667, 9),
            provenance = FinOpsFxProvenance.INVOICE,
            source = "vendor-invoice",
            sourceHash = sha256Hex("invoice-sar-rate"),
        )

        assertEquals(93_750_000L, service.convertToUsdMicros(MoneyAmount("SAR", 375, 0), "2027-01-15"))
        assertEquals(100_000_000L, service.convertToUsdMicros(MoneyAmount("SAR", 375, 0), "2027-01-15", invoice))
        assertEquals(1_234_568L, service.convertToUsdMicros(MoneyAmount("USD", 1_234_567_500, 9), "2027-01-15"))
        assertFailsWith<IllegalArgumentException> {
            service.convertToUsdMicros(MoneyAmount("EUR", 100, 2), "2027-01-15")
        }
    }

    @Test
    fun `active cloud platform budget drives exact summary thresholds`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val budget = FinOpsBudget(
            id = "budget:cloud-platform:2027",
            scope = FinOpsBudgetScope.COST_CLASS,
            scopeValue = "cloud_platform",
            amountUsdMicros = 120_000_001L,
            warningThresholdBasisPoints = 7_500,
            criticalThresholdBasisPoints = 9_000,
            hardGate = true,
            effectiveFrom = 1_790_000_000_000L,
            sourceHash = sha256Hex("cloud-platform-budget-2027"),
        )
        assertEquals("inserted", service.recordBudget(budget, "test").state)
        assertEquals("duplicate", service.recordBudget(budget, "test").state)

        val summary = service.summary(query())
        assertEquals(120_000_001L, summary.cloudPlatformBudgetUsdMicros)
        assertEquals(90_000_001L, summary.budgetWarningUsdMicros)
        assertEquals(108_000_001L, summary.budgetCriticalUsdMicros)
    }

    @Test
    fun `ten thousand replays do not duplicate spend`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val request = request("gcp:one", 1_250_000L, FinOpsStatus.FINALIZED)
        assertEquals("inserted", service.ingest(request, "test").state)
        repeat(10_000) {
            assertEquals("duplicate", service.ingest(request, "test").state)
        }
        val summary = service.summary(query())
        assertEquals(1_250_000L, summary.finalizedSpendUsdMicros)
    }

    @Test
    fun `duplicate entries retry idempotent rollups without duplicating spend`() {
        val backing = InMemoryFinOpsRepository()
        var rollupCalls = 0
        val repository = object : FinOpsRepository by backing {
            override fun ensureDailyRollups(entry: FinOpsEntry, allocations: List<CostAllocation>) {
                rollupCalls += 1
                backing.ensureDailyRollups(entry, allocations)
            }
        }
        val service = FinOpsService(repository)
        val request = request("gcp:schema-replay", 2_000_000L, FinOpsStatus.FINALIZED)

        assertEquals("inserted", service.ingest(request, "test").state)
        assertEquals("duplicate", service.ingest(request, "test").state)
        assertEquals(2, rollupCalls)
        assertEquals(2_000_000L, service.summary(query()).finalizedSpendUsdMicros)
    }

    @Test
    fun `duplicate replay repairs a crash between immutable entry and rollup writes`() {
        val backing = InMemoryFinOpsRepository()
        var failFirstRollup = true
        val repository = object : FinOpsRepository by backing {
            override fun ensureDailyRollups(entry: FinOpsEntry, allocations: List<CostAllocation>) {
                if (failFirstRollup) {
                    failFirstRollup = false
                    throw IllegalStateException("simulated crash before rollup commit")
                }
                backing.ensureDailyRollups(entry, allocations)
            }
        }
        val service = FinOpsService(repository, coverageSources = emptySet())
        val request = request("gcp:crash-recovery", 3_000_000L, FinOpsStatus.FINALIZED)

        assertFailsWith<IllegalStateException> { service.ingest(request, "test") }
        assertEquals("duplicate", service.ingest(request, "test").state)
        assertEquals(3_000_000L, service.summary(query()).finalizedSpendUsdMicros)
    }

    @Test
    fun `manual entries store typed receipt metadata and reject reserved spoofing`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val digest = "a".repeat(64)
        val uploadId = "b".repeat(32)
        val receipt = FinOpsReceiptReference(
            storageKey = "sha256/$digest/uploads/$uploadId/invoice.pdf",
            sha256 = digest,
            filename = "invoice.pdf",
            contentType = "application/pdf",
            size = 123,
            uploadId = uploadId,
        )
        val request = ManualFinOpsEntryRequest(
            sourceRecordId = "invoice-with-receipt",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("USD", 100, 2),
            usdMicros = 1_000_000,
            incurredAt = 1_800_000_000_000L,
            project = "portfolio",
            environment = "dev",
            vendor = "cloudflare",
            service = "workers",
            reconciliationKey = "cloudflare:2027-01",
            sourceHash = sha256Hex("invoice-with-receipt"),
            receipt = receipt,
        )
        val created = service.createManual(request, "owner")
        assertEquals(receipt, service.receiptForEntry(created.entryId, "owner"))
        assertEquals(
            FinOpsReceiptAttachment(uploadId, created.entryId, receipt.storageKey, receipt.sha256),
            service.receiptAttachment(uploadId),
        )
        assertEquals(
            mapOf("source" to "manual", "receiptUploadId" to uploadId, "receiptSha256" to digest),
            repository.auditEvents().single { it.action == "ingest" }.detail,
        )
        assertFailsWith<IllegalArgumentException> {
            service.createManual(request.copy(sourceRecordId = "spoof", metadata = mapOf("receipt.sha256" to digest)), "owner")
        }
        val entryCount = service.entries(query()).entries.size
        assertFailsWith<FinOpsConflictException> {
            service.createManual(request.copy(sourceRecordId = "other-entry"), "owner")
        }
        assertEquals(entryCount, service.entries(query()).entries.size)
    }

    @Test
    fun `duplicate ingest repairs receipt attachment projection after a post-entry failure`() {
        val backing = InMemoryFinOpsRepository()
        val repository = object : FinOpsRepository by backing {
            var failNextAttachment = true

            override fun putReceiptAttachment(attachment: FinOpsReceiptAttachment): FinOpsWriteResult {
                if (failNextAttachment) {
                    failNextAttachment = false
                    throw IllegalStateException("simulated attachment projection outage")
                }
                return backing.putReceiptAttachment(attachment)
            }
        }
        val service = FinOpsService(repository, coverageSources = emptySet())
        val digest = "c".repeat(64)
        val uploadId = "d".repeat(32)
        val reference = FinOpsReceiptReference(
            storageKey = "sha256/$digest/uploads/$uploadId/invoice.pdf",
            sha256 = digest,
            filename = "invoice.pdf",
            contentType = "application/pdf",
            size = 10,
            uploadId = uploadId,
        )
        val request = ManualFinOpsEntryRequest(
            sourceRecordId = "attachment-repair",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("USD", 100, 2),
            usdMicros = 1_000_000,
            incurredAt = 1_800_000_000_000L,
            project = "portfolio",
            environment = "dev",
            vendor = "cloudflare",
            service = "workers",
            reconciliationKey = "cloudflare:2027-01",
            sourceHash = sha256Hex("attachment-repair"),
            receipt = reference,
        )

        assertFailsWith<IllegalStateException> { service.createManual(request, "owner") }
        assertEquals("duplicate", service.createManual(request, "owner").state)
        assertEquals(deterministicId("manual", request.sourceRecordId), service.receiptAttachment(uploadId)?.entryId)
    }

    @Test
    fun `recurring expenses materialize calendar anchored occurrences exactly once`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val monthly = FinOpsRecurringExpense(
            id = "recurring:domain",
            amount = MoneyAmount("USD", 1200, 2),
            usdMicros = 12_000_000,
            project = "portfolio",
            environment = "prod",
            vendor = "registrar",
            service = "domain",
            cadence = FinOpsRecurrenceCadence.MONTHLY,
            startDate = "2027-01-31",
            sourceHash = sha256Hex("recurring-domain"),
        )
        assertEquals("inserted", service.recordRecurringExpense(monthly, "owner").state)
        assertEquals("duplicate", service.recordRecurringExpense(monthly, "owner").state)

        val first = service.materializeRecurringExpenses(
            java.time.LocalDate.parse("2027-01-01"),
            java.time.LocalDate.parse("2027-04-01"),
            "scheduler",
        )
        assertEquals(FinOpsRecurringMaterialization(1, 3, 3, 0), first)
        val replay = service.materializeRecurringExpenses(
            java.time.LocalDate.parse("2027-01-01"),
            java.time.LocalDate.parse("2027-04-01"),
            "scheduler",
        )
        assertEquals(FinOpsRecurringMaterialization(1, 3, 0, 3), replay)
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31"),
            service.entries(
                FinOpsQuery(
                    java.time.LocalDate.parse("2027-01-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                    java.time.LocalDate.parse("2027-04-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                    limit = 500,
                ),
            ).entries.map { utcDate(it.incurredAt).toString() }.sorted(),
        )
    }

    @Test
    fun `recurring expenses materialize only inside future-only coverage`() {
        val cutoff = java.time.Instant.parse("2026-08-24T00:00:00Z").toEpochMilli()
        val service = FinOpsService(
            InMemoryFinOpsRepository(),
            coverageSources = emptySet(),
            coverageStartAt = cutoff,
        )
        service.recordRecurringExpense(
            FinOpsRecurringExpense(
                id = "recurring:monthly",
                amount = MoneyAmount("USD", 1, 0),
                usdMicros = 1_000_000L,
                project = "portfolio",
                environment = "dev",
                vendor = "vendor",
                service = "service",
                cadence = FinOpsRecurrenceCadence.MONTHLY,
                startDate = "2026-08-23",
                sourceHash = sha256Hex("recurring-monthly"),
            ),
            "owner",
        )

        val result = service.materializeRecurringExpenses(
            java.time.LocalDate.parse("2026-08-01"),
            java.time.LocalDate.parse("2026-10-01"),
            "scheduler",
        )

        assertEquals(FinOpsRecurringMaterialization(1, 1, 1, 0), result)
        assertEquals(
            listOf("2026-09-23"),
            service.entries(FinOpsQuery(cutoff, java.time.Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), limit = 10))
                .entries.map { utcDate(it.incurredAt).toString() }
                .sorted(),
        )
    }

    @Test
    fun `final invoice replaces matching accrual and estimate in headline totals`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val accrued = request("provider-accrual", 10_000_000L, FinOpsStatus.ACCRUED)
        val estimated = request("provider-estimate", 9_000_000L, FinOpsStatus.ESTIMATED)
        val finalized = request("provider-invoice", 12_000_000L, FinOpsStatus.FINALIZED)
        listOf(accrued, estimated, finalized).forEach { original ->
            service.ingest(
                original.copy(entry = original.entry.copy(
                    reconciliationKey = "provider:2027-01",
                    metadata = mapOf("costClass" to "direct_ai"),
                )),
                "test",
            )
        }

        val summary = service.summary(query())
        assertEquals(12_000_000L, summary.finalizedSpendUsdMicros)
        assertEquals(0L, summary.accruedSpendUsdMicros)
        assertEquals(0L, summary.estimatedSpendUsdMicros)
        assertEquals(0L, summary.unreconciledUsdMicros)
        assertEquals(12_000_000L, summary.directAiCostUsdMicros)
        assertEquals(-12_000_000L, summary.grossMarginUsdMicros)
        assertEquals(-12_000_000L, summary.contributionMarginUsdMicros)
        assertEquals(12_000_000L, summary.byProject.single().usdMicros)
        assertEquals(12_000_000L, summary.byEnvironment.single().usdMicros)
        assertEquals(12_000_000L, summary.byVendor.single().usdMicros)
        val reconciliation = service.reconciliations(query()).single()
        assertEquals(19_000_000L, reconciliation.estimatedUsdMicros)
        assertEquals(12_000_000L, reconciliation.finalizedUsdMicros)
        assertEquals(-7_000_000L, reconciliation.differenceUsdMicros)
    }

    @Test
    fun `allocations do not add to financial spend`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val base = request("cloudflare:invoice", 8_000_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            base.copy(
                allocations = listOf(
                    CostAllocation("allocation:one", base.entry.id, "samurai", "user-1", allocatedUsdMicros = 3_000_000L, method = AllocationMethod.METERED),
                    CostAllocation("allocation:two", base.entry.id, "samurai", "user-2", allocatedUsdMicros = 5_000_000L, method = AllocationMethod.RESIDUAL),
                )
            ),
            "test",
        )
        assertEquals(8_000_000L, service.summary(query()).finalizedSpendUsdMicros)
        assertEquals(
            8_000_000L,
            service.samuraiUsers(query()).users.sumOf { it.providerCostUsdMicros + it.allocatedInvoiceUsdMicros },
        )
    }

    @Test
    fun `Samurai user margin replaces metered accrual with invoice allocation`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val metered = request("samurai:user-metered", 6_000_000L, FinOpsStatus.ACCRUED)
        val invoice = request("samurai:user-invoice", 7_000_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            metered.copy(
                entry = metered.entry.copy(
                    reconciliationKey = "provider:2027-01",
                    metadata = mapOf("costClass" to "direct_ai", "creditsBurned" to "12"),
                ),
                allocations = listOf(CostAllocation(
                    id = "allocation:user-metered",
                    entryId = metered.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    provider = "provider",
                    modelId = "model",
                    allocatedUsdMicros = 6_000_000L,
                    method = AllocationMethod.METERED,
                )),
            ),
            "test",
        )
        service.ingest(
            invoice.copy(
                entry = invoice.entry.copy(reconciliationKey = "provider:2027-01"),
                allocations = listOf(CostAllocation(
                    id = "allocation:user-invoice",
                    entryId = invoice.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    provider = "provider",
                    modelId = "model",
                    allocatedUsdMicros = 7_000_000L,
                    method = AllocationMethod.PROPORTIONAL,
                )),
            ),
            "test",
        )

        val fullDay = FinOpsQuery(1_799_971_200_000L, 1_800_057_600_000L, limit = 500)
        listOf(service.samuraiUsers(query()).users.single(), service.samuraiUsers(fullDay).users.single()).forEach { user ->
            assertEquals(6_000_000L, user.providerCostUsdMicros)
            assertEquals(7_000_000L, user.allocatedInvoiceUsdMicros)
            assertEquals(-7_000_000L, user.grossMarginUsdMicros)
            assertEquals(1L, user.requests)
            assertEquals(12L, user.creditsBurned)
            assertEquals(7_000_000L, user.byProvider.single().usdMicros)
            assertEquals(7_000_000L, user.byModel.single().usdMicros)
        }
    }

    @Test
    fun `owner Samurai view resolves identity outside financial events and degrades to opaque ids`() {
        val base = request("samurai:identity", 800_000L, FinOpsStatus.ACCRUED)
        val repository = InMemoryFinOpsRepository()
        val resolver = SamuraiIdentityResolver { ids ->
            assertEquals(setOf("user-1"), ids)
            mapOf("user-1" to SamuraiFinOpsIdentity("user-1", "Samurai User", "samurai@example.test"))
        }
        val service = FinOpsService(repository, samuraiIdentityResolver = resolver)
        service.ingest(
            base.copy(allocations = listOf(CostAllocation(
                id = "allocation:identity",
                entryId = base.entry.id,
                project = "samurai",
                userId = "user-1",
                allocatedUsdMicros = base.entry.usdMicros,
                method = AllocationMethod.METERED,
            ))),
            "test",
        )

        val user = service.samuraiUsers(query()).users.single()
        assertEquals("Samurai User", user.displayName)
        assertEquals("samurai@example.test", user.email)
        assertEquals(null, service.entries(query()).entries.single().metadata["email"])

        val unavailable = FinOpsService(repository, samuraiIdentityResolver = SamuraiIdentityResolver { error("offline") })
            .samuraiUsers(query()).users.single()
        assertEquals("user-1", unavailable.userId)
        assertEquals(null, unavailable.displayName)
        assertEquals(null, unavailable.email)
    }

    @Test
    fun `underallocated charges receive an explicit residual without changing spend`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val base = request("provider:partially-allocated", 10_000_000L, FinOpsStatus.FINALIZED)

        val receipt = service.ingest(
            base.copy(allocations = listOf(CostAllocation(
                id = "allocation:known-user",
                entryId = base.entry.id,
                project = "samurai",
                userId = "user-1",
                allocatedUsdMicros = 6_000_000L,
                method = AllocationMethod.METERED,
            ))),
            "test",
        )

        assertEquals(2, receipt.allocationCount)
        assertEquals(10_000_000L, service.summary(query()).finalizedSpendUsdMicros)
        assertEquals(4_000_000L, service.summary(query()).unallocatedUsdMicros)
        assertEquals(
            listOf(4_000_000L, 6_000_000L),
            repository.listAllocations(setOf(base.entry.id)).map(CostAllocation::allocatedUsdMicros).sorted(),
        )
    }

    @Test
    fun `user-filtered summary reports the allocation instead of the full charge`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val base = request("provider:user-filtered-summary", 10_000_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            base.copy(allocations = listOf(CostAllocation(
                id = "allocation:summary-user",
                entryId = base.entry.id,
                project = "samurai",
                userId = "user-1",
                provider = "provider",
                modelId = "model",
                allocatedUsdMicros = 6_000_000L,
                method = AllocationMethod.METERED,
            ))),
            "test",
        )

        val summary = service.summary(query().copy(userId = "user-1"))
        assertEquals(6_000_000L, summary.finalizedSpendUsdMicros)
        assertEquals(6_000_000L, summary.byProject.single().usdMicros)
        assertEquals(0L, summary.unallocatedUsdMicros)
        assertEquals(6_000_000L, service.reconciliations(query().copy(userId = "user-1")).single().finalizedUsdMicros)
    }

    @Test
    fun `allocation replay cannot add a different allocation set to an immutable entry`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val base = request("provider:immutable-allocation", 1_000_000L, FinOpsStatus.FINALIZED)
        val first = CostAllocation(
            id = "allocation:first",
            entryId = base.entry.id,
            project = "samurai",
            userId = "user-1",
            allocatedUsdMicros = 1_000_000L,
            method = AllocationMethod.DIRECT,
        )
        service.ingest(base.copy(allocations = listOf(first)), "test")

        assertFailsWith<IllegalArgumentException> {
            service.ingest(
                base.copy(allocations = listOf(first.copy(id = "allocation:second", userId = "user-2"))),
                "test",
            )
        }
    }

    @Test
    fun `allocation backfill repairs legacy residuals once and advances by cursor`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = emptySet())
        val legacy = request("provider:legacy-allocation", 9_000_000L, FinOpsStatus.FINALIZED)
        val known = CostAllocation(
            id = "allocation:legacy-known",
            entryId = legacy.entry.id,
            project = "samurai",
            userId = "user-1",
            allocatedUsdMicros = 5_000_000L,
            method = AllocationMethod.METERED,
        )
        assertEquals(FinOpsWriteResult.Inserted, repository.putEntry(legacy.entry))
        repository.putAllocations(listOf(known))
        repository.ensureDailyRollups(legacy.entry, listOf(known))

        val first = service.backfillAllocationResiduals(query().copy(limit = 1), "owner")
        val second = service.backfillAllocationResiduals(query().copy(limit = 1), "owner")

        assertEquals(1, first.processed)
        assertEquals(1, first.residualAllocations)
        assertEquals(1, second.processed)
        assertEquals(0, second.residualAllocations)
        assertEquals(4_000_000L, service.summary(query()).unallocatedUsdMicros)
        assertEquals(2, repository.listAllocations(setOf(legacy.entry.id)).size)
    }

    @Test
    fun `cloud platform budget excludes unrelated studio spending`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val cloud = request("cloud-platform", 4_000_000L, FinOpsStatus.ACCRUED)
        val software = request("software", 7_000_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            cloud.copy(entry = cloud.entry.copy(
                reconciliationKey = "cloudflare:2027-01",
                metadata = mapOf("costClass" to "cloud_platform"),
            )),
            "test",
        )
        service.ingest(
            software.copy(entry = software.entry.copy(reconciliationKey = "software:2027-01")),
            "test",
        )

        val summary = service.summary(query())
        assertEquals(11_000_000L, summary.finalizedSpendUsdMicros + summary.accruedSpendUsdMicros)
        assertEquals(4_000_000L, summary.cloudPlatformSpendUsdMicros)
        assertEquals(
            mapOf("cloud_platform" to 4_000_000L, "unclassified" to 7_000_000L),
            summary.byCategory.associate { it.key to it.usdMicros },
        )
    }

    @Test
    fun `revenue allocation increases user revenue without inflating provider cost`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val base = request("stripe:user-revenue", 4_000_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            base.copy(
                entry = base.entry.copy(
                    direction = FinOpsDirection.REVENUE,
                    vendor = "stripe",
                    metadata = mapOf("accountingClass" to "revenue"),
                ),
                allocations = listOf(CostAllocation(
                    id = "allocation:user-revenue",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    allocatedUsdMicros = 4_000_000L,
                    method = AllocationMethod.DIRECT,
                )),
            ),
            "test",
        )

        val user = service.samuraiUsers(query()).users.single()
        assertEquals(0L, user.providerCostUsdMicros)
        assertEquals(4_000_000L, user.revenueUsdMicros)
        assertEquals(4_000_000L, user.grossMarginUsdMicros)
    }

    @Test
    fun `allocated provider credit reduces user cost`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val base = request("provider:credit", 750_000L, FinOpsStatus.FINALIZED)
        service.ingest(
            base.copy(
                entry = base.entry.copy(direction = FinOpsDirection.CREDIT),
                allocations = listOf(CostAllocation(
                    id = "allocation:provider-credit",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    allocatedUsdMicros = 750_000L,
                    method = AllocationMethod.PROPORTIONAL,
                )),
            ),
            "test",
        )

        val user = service.samuraiUsers(query()).users.single()
        assertEquals(0L, user.providerCostUsdMicros)
        assertEquals(-750_000L, user.allocatedInvoiceUsdMicros)
        assertEquals(750_000L, user.grossMarginUsdMicros)
    }

    @Test
    fun `same id with another source hash is a conflict`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val request = request("manual:one", 100_000L, FinOpsStatus.FINALIZED)
        service.ingest(request, "test")
        assertFailsWith<FinOpsConflictException> {
            service.ingest(request.copy(entry = request.entry.copy(sourceHash = sha256Hex("changed"))), "test")
        }
    }

    @Test
    fun `same id and claimed source hash cannot change immutable ledger fields`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val request = request("manual:forged-replay", 100_000L, FinOpsStatus.FINALIZED)
        service.ingest(request, "test")

        assertFailsWith<FinOpsConflictException> {
            service.ingest(
                request.copy(entry = request.entry.copy(usdMicros = 200_000L)),
                "test",
            )
        }
    }

    @Test
    fun `ledger directions carry sign and reject negative amount magnitudes`() {
        val entry = request("manual:negative", 100_000L, FinOpsStatus.FINALIZED).entry
        assertEquals(100_000L, entry.copy(direction = FinOpsDirection.EXPENSE).signedLedgerMicros)
        assertEquals(100_000L, entry.copy(direction = FinOpsDirection.REVENUE).signedLedgerMicros)
        assertEquals(-100_000L, entry.copy(direction = FinOpsDirection.CREDIT).signedLedgerMicros)
        assertEquals(-100_000L, entry.copy(direction = FinOpsDirection.REFUND).signedLedgerMicros)
        assertFailsWith<IllegalArgumentException> {
            entry.copy(usdMicros = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(amount = entry.amount.copy(minorUnits = -1L))
        }
    }

    @Test
    fun `metering metadata rejects malformed and negative counters`() {
        val entry = request("manual:invalid-metering", 100_000L, FinOpsStatus.ACCRUED).entry
        assertFailsWith<IllegalArgumentException> {
            entry.copy(metadata = mapOf("creditsBurned" to "-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(metadata = mapOf("toolCallCount" to "not-a-number"))
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(metadata = mapOf("fallback" to "sometimes"))
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(metadata = mapOf("reason" to "unsafe\nlog line"))
        }
    }

    @Test
    fun `positive adjustments remain consistent across daily and user rollups`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val base = request("manual:adjustment", 1_000_000L, FinOpsStatus.ADJUSTMENT)
        service.ingest(
            base.copy(
                entry = base.entry.copy(direction = FinOpsDirection.ADJUSTMENT),
                allocations = listOf(CostAllocation(
                    id = "allocation:adjustment",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    allocatedUsdMicros = 1_000_000L,
                    method = AllocationMethod.DIRECT,
                )),
            ),
            "test",
        )
        val fullDay = FinOpsQuery(
            from = 1_799_971_200_000L,
            toExclusive = 1_800_057_600_000L,
            limit = 500,
        )

        assertEquals(1_000_000L, service.summary(fullDay).finalizedSpendUsdMicros)
        val user = service.samuraiUsers(fullDay).users.single()
        assertEquals(0L, user.providerCostUsdMicros)
        assertEquals(1_000_000L, user.allocatedInvoiceUsdMicros)
        assertEquals(-1_000_000L, user.grossMarginUsdMicros)
    }

    @Test
    fun `finalized zero total is reconciled instead of awaiting an invoice`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        service.ingest(request("manual:zero-final", 0L, FinOpsStatus.FINALIZED), "test")
        val fullDay = FinOpsQuery(
            from = 1_799_971_200_000L,
            toExclusive = 1_800_057_600_000L,
            limit = 500,
        )

        val reconciliation = service.reconciliations(fullDay).single()
        assertEquals(0L, reconciliation.finalizedUsdMicros)
        assertEquals("matched", reconciliation.state)
    }

    @Test
    fun `csv neutralizes line breaks and quotes`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val request = request("manual:csv", 100_000L, FinOpsStatus.FINALIZED)
        service.ingest(request.copy(entry = request.entry.copy(service = "domain, \"renewal\"")), "test")
        val csv = service.exportCsv(query(), "test")
        assertTrue(csv.csv.contains("\"domain, \"\"renewal\"\"\""))
    }

    @Test
    fun `csv neutralizes spreadsheet formulas`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val request = request("manual:formula", 100_000L, FinOpsStatus.FINALIZED)
        service.ingest(request.copy(entry = request.entry.copy(service = "=HYPERLINK(\"https://invalid\")")), "test")
        assertTrue(service.exportCsv(query(), "test").csv.contains("\"'=HYPERLINK"))
    }

    @Test
    fun `csv row fields remain aligned with the declared schema`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        service.ingest(request("manual:aligned", 100_000L, FinOpsStatus.FINALIZED), "test")

        val lines = service.exportCsv(query(), "test").csv.lines().filter(String::isNotBlank)
        val header = lines.single { it.startsWith("id,") }.split(',')
        val row = lines.single { it.startsWith("\"") }
            .removePrefix("\"")
            .removeSuffix("\"")
            .split("\",\"")

        assertEquals(header.size, row.size)
        assertEquals("project", header[5])
        assertEquals("samurai", row[5])
        assertEquals("environment", header[6])
        assertEquals("prod", row[6])
        assertEquals("vendor", header[7])
        assertEquals("vendor", row[7])
        assertEquals("service", header[8])
        assertEquals("service", row[8])
    }

    @Test
    fun `csv export is one cursor-bounded page instead of a full-history scan`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        repeat(501) { index ->
            service.ingest(request("manual:csv-page-$index", 100_000L, FinOpsStatus.FINALIZED), "test")
        }
        val first = service.exportCsv(query().copy(limit = 500), "owner")
        assertEquals(500, first.rowCount)
        assertNotNull(first.nextCursor)
        assertEquals(501, first.csv.lines().count(String::isNotBlank))

        val second = service.exportCsv(query().copy(limit = 500, cursor = first.nextCursor), "owner")
        assertEquals(1, second.rowCount)
        assertEquals(null, second.nextCursor)
        assertEquals(2, second.csv.lines().count(String::isNotBlank))
    }

    @Test
    fun `user filter returns only directly allocated entries`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        listOf("one" to "user-1", "two" to "user-2").forEach { (suffix, userId) ->
            val request = request("samurai:$suffix", 200_000L, FinOpsStatus.ACCRUED)
            service.ingest(
                request.copy(allocations = listOf(CostAllocation(
                    id = "allocation:$suffix",
                    entryId = request.entry.id,
                    project = "samurai",
                    userId = userId,
                    allocatedUsdMicros = request.entry.usdMicros,
                    method = AllocationMethod.METERED,
                ))),
                "test",
            )
        }
        assertEquals(1, service.entries(query().copy(userId = "user-1")).entries.size)
    }

    @Test
    fun `stripe refund reduces revenue without reducing operational spend`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        FinOpsConnectorNormalizer.stripe(
            StripeBalanceRow("charge", 1_800_000_000_000L, "charge", "USD", 1000, 0, grossUsdMicros = 10_000_000, feeUsdMicros = 0),
            environment = "prod",
        ).forEach { service.ingest(it, "test") }
        FinOpsConnectorNormalizer.stripe(
            StripeBalanceRow("refund", 1_800_000_000_100L, "refund", "USD", -250, 0, grossUsdMicros = -2_500_000, feeUsdMicros = 0),
            environment = "prod",
        ).forEach { service.ingest(it, "test") }
        val summary = service.summary(query())
        assertEquals(7_500_000L, summary.revenueUsdMicros)
        assertEquals(0L, summary.finalizedSpendUsdMicros)
    }

    @Test
    fun `stripe fee reversal reduces fees spend and preserves margin arithmetic`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        FinOpsConnectorNormalizer.stripe(
            StripeBalanceRow("charge-fee", 1_800_000_000_000L, "charge", "USD", 1000, 100, grossUsdMicros = 10_000_000, feeUsdMicros = 1_000_000),
            environment = "prod",
        ).forEach { service.ingest(it, "test") }
        FinOpsConnectorNormalizer.stripe(
            StripeBalanceRow("refund-fee", 1_800_000_000_100L, "refund", "USD", -250, -25, grossUsdMicros = -2_500_000, feeUsdMicros = -250_000),
            environment = "prod",
        ).forEach { service.ingest(it, "test") }

        val summary = service.summary(query())
        assertEquals(7_500_000L, summary.revenueUsdMicros)
        assertEquals(750_000L, summary.stripeFeesUsdMicros)
        assertEquals(750_000L, summary.finalizedSpendUsdMicros)
        assertEquals(6_750_000L, summary.grossMarginUsdMicros)

        val dayStart = java.time.Instant.ofEpochMilli(1_800_000_000_000L)
            .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            .toEpochMilli()
        val rollupSummary = service.summary(FinOpsQuery(
            from = dayStart,
            toExclusive = dayStart + 86_400_000L,
            limit = 500,
        ))
        assertEquals(750_000L, rollupSummary.stripeFeesUsdMicros)
        assertEquals(6_750_000L, rollupSummary.grossMarginUsdMicros)
    }

    @Test
    fun `late GCP credit reduces finalized spend without rewriting the original charge`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val original = GcpBillingRow(
            billingAccountId = "billing",
            sourceRecordId = "original-charge",
            usageStartMillis = 1_800_000_000_000L,
            invoiceMonth = "2027-01",
            projectId = "portfolio-prod",
            service = "Firestore",
            sku = "reads",
            currency = "USD",
            costMinorUnits = 1_000,
            costScale = 2,
            usdMicros = 10_000_000L,
            exportTimeMillis = 1_800_000_001_000L,
        )
        val lateCredit = original.copy(
            sourceRecordId = "late-credit",
            costMinorUnits = 0L,
            usdMicros = 0L,
            creditsMinorUnits = -200L,
            creditsUsdMicros = -2_000_000L,
            exportTimeMillis = 1_800_086_401_000L,
        )

        (FinOpsConnectorNormalizer.gcp(original, environment = "prod") +
            FinOpsConnectorNormalizer.gcp(lateCredit, environment = "prod"))
            .forEach { service.ingest(it, "test") }

        val summary = service.summary(query())
        assertEquals(8_000_000L, summary.finalizedSpendUsdMicros)
        assertEquals(3, service.entries(query()).entries.size)
        assertEquals(8_000_000L, service.reconciliations(query()).single().finalizedUsdMicros)
    }

    @Test
    fun `partial UTC day queries do not include the rest of a daily rollup`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository)
        val dayStart = 1_800_057_600_000L
        val early = request("early", 100_000L, FinOpsStatus.FINALIZED).copy(
            entry = request("early", 100_000L, FinOpsStatus.FINALIZED).entry.copy(incurredAt = dayStart + 1_000),
        )
        val included = request("included", 250_000L, FinOpsStatus.FINALIZED).copy(
            entry = request("included", 250_000L, FinOpsStatus.FINALIZED).entry.copy(incurredAt = dayStart + 43_200_000L),
        )
        service.ingest(early, "test")
        service.ingest(included, "test")

        val summary = service.summary(FinOpsQuery(
            from = dayStart + 21_600_000L,
            toExclusive = dayStart + 64_800_000L,
        ))

        assertEquals(250_000L, summary.finalizedSpendUsdMicros)
    }

    @Test
    fun `status-filtered full-day breakdowns use the matching rollup amount`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val dayStart = java.time.Instant.ofEpochMilli(1_800_000_000_000L)
            .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            .toEpochMilli()
        val accrued = request("accrued-breakdown", 625_000L, FinOpsStatus.ACCRUED)
        service.ingest(
            accrued.copy(entry = accrued.entry.copy(incurredAt = dayStart + 1_000L, project = "samurai")),
            "test",
        )

        val summary = service.summary(FinOpsQuery(
            from = dayStart,
            toExclusive = dayStart + 86_400_000L,
            status = FinOpsStatus.ACCRUED,
        ))

        assertEquals(625_000L, summary.accruedSpendUsdMicros)
        assertEquals(listOf(FinOpsBreakdown("samurai", 625_000L)), summary.byProject)
    }

    @Test
    fun `coverage and freshness come from durable import runs rather than nonzero spend`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = setOf("gcp", "cloudflare"))
        repository.putImportRun(FinOpsImportRun(
            id = "import:gcp:2027-01-03",
            source = "gcp",
            fromDate = "2026-12-01",
            toDateExclusive = "2027-02-01",
            sourceRecords = 0,
            entries = 0,
            status = "succeeded",
            completedAt = 1_800_000_000_000L,
            sourceHash = sha256Hex("gcp-zero-spend"),
        ))
        repository.putImportRun(FinOpsImportRun(
            id = "import:cloudflare:2027-01-03",
            source = "cloudflare",
            fromDate = "2026-12-01",
            toDateExclusive = "2027-02-01",
            sourceRecords = 1,
            entries = 1,
            status = "partial",
            completedAt = 1_800_000_000_100L,
            sourceHash = sha256Hex("cloudflare-partial"),
            failureCode = "restricted_api_unavailable",
        ))

        val summary = service.summary(FinOpsQuery(
            from = java.time.LocalDate.parse("2027-01-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            toExclusive = java.time.LocalDate.parse("2027-02-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        ))
        assertEquals(
            listOf("cloudflare: partial coverage (restricted_api_unavailable)"),
            summary.coverageGaps,
        )
        assertEquals(1_800_000_000_000L, summary.dataFreshness["gcp"])
        assertEquals(1_800_000_000_100L, summary.dataFreshness["cloudflare"])
        assertEquals(FinOpsConnectorState("succeeded", 1_800_000_000_000L), summary.connectorStates["gcp"])
        assertEquals(
            FinOpsConnectorState("partial", 1_800_000_000_100L, "restricted_api_unavailable"),
            summary.connectorStates["cloudflare"],
        )
    }

    @Test
    fun `pre-coverage connector receipts are excluded from dashboard state`() {
        val repository = InMemoryFinOpsRepository()
        val coverageStartDate = java.time.LocalDate.parse("2026-08-24")
        val coverageStartAt = coverageStartDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val service = FinOpsService(repository, coverageSources = setOf("gcp"), coverageStartAt = coverageStartAt)
        repository.putImportRun(FinOpsImportRun(
            id = "import:gcp:pre-coverage-failure",
            source = "gcp",
            fromDate = "2026-08-23",
            toDateExclusive = "2026-08-24",
            sourceRecords = 0,
            entries = 0,
            status = "failed",
            completedAt = coverageStartAt - 1L,
            sourceHash = sha256Hex("pre-coverage-failure"),
            failureCode = "query_limit",
        ))

        val summary = service.summary(FinOpsQuery(
            from = coverageStartAt,
            toExclusive = coverageStartDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        ))

        assertTrue(summary.connectorStates.isEmpty())
        assertTrue(summary.dataFreshness.isEmpty())
        assertEquals(listOf("gcp: no reconciled import coverage"), summary.coverageGaps)
    }

    @Test
    fun `failed connector reason remains redacted in summary state`() {
        val repository = InMemoryFinOpsRepository()
        val service = FinOpsService(repository, coverageSources = setOf("gcp"))
        repository.putImportRun(FinOpsImportRun(
            id = "import:gcp:2027-01-03:failure",
            source = "gcp",
            fromDate = "2026-12-01",
            toDateExclusive = "2027-02-01",
            sourceRecords = 0,
            entries = 0,
            status = "failed",
            completedAt = 1_800_000_000_000L,
            sourceHash = sha256Hex("gcp-authorization-failure"),
            failureCode = "bigquery_authorization",
        ))

        val summary = service.summary(FinOpsQuery(
            from = java.time.LocalDate.parse("2027-01-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            toExclusive = java.time.LocalDate.parse("2027-02-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        ))

        assertEquals(
            FinOpsConnectorState("failed", 1_800_000_000_000L, "bigquery_authorization"),
            summary.connectorStates["gcp"],
        )
        assertEquals(
            listOf("gcp: latest covering import failed (bigquery_authorization)"),
            summary.coverageGaps,
        )
    }

    @Test
    fun `forecast and period comparison use equal bounded periods`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val rangeStart = 1_800_000_000_000L
        val duration = 10_000L
        val previous = request("previous-period", 1_000_000L, FinOpsStatus.FINALIZED).copy(
            entry = request("previous-period", 1_000_000L, FinOpsStatus.FINALIZED).entry.copy(incurredAt = rangeStart - 1_000L),
        )
        val current = request("current-period", 1_500_000L, FinOpsStatus.ACCRUED).copy(
            entry = request("current-period", 1_500_000L, FinOpsStatus.ACCRUED).entry.copy(incurredAt = rangeStart + 1_000L),
        )
        service.ingest(previous, "test")
        service.ingest(current, "test")

        val summary = service.summaryWithComparison(
            FinOpsQuery(from = rangeStart, toExclusive = rangeStart + duration),
            now = rangeStart + duration / 2,
        )
        assertEquals(3_000_000L, summary.forecastSpendUsdMicros)
        assertEquals(1_000_000L, summary.previousPeriodSpendUsdMicros)
        assertEquals(5_000L, summary.monthOverMonthBasisPoints)
    }

    @Test
    fun `samurai user attribution exposes executed provider model fallback and tools`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val base = request("samurai:attribution", 900_000L, FinOpsStatus.ACCRUED)
        service.ingest(
            base.copy(
                entry = base.entry.copy(
                    vendor = "cloudflare",
                    sku = "kimi-k2.5",
                    metadata = mapOf(
                        "costClass" to "direct_ai",
                        "creditsBurned" to "9",
                        "fallback" to "true",
                        "toolCallCount" to "3",
                        "runId" to "run-owner-visible",
                    ),
                ),
                allocations = listOf(CostAllocation(
                    id = "allocation:attribution",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    provider = "cloudflare",
                    modelId = "kimi-k2.5",
                    runId = "run-owner-visible",
                    allocatedUsdMicros = 900_000L,
                    method = AllocationMethod.METERED,
                )),
            ),
            "test",
        )

        val user = service.samuraiUsers(query()).users.single()
        assertEquals(1L, user.fallbacks)
        assertEquals(3L, user.toolCalls)
        assertEquals("cloudflare", user.byProvider.single().key)
        assertEquals("kimi-k2.5", user.byModel.single().key)
    }

    @Test
    fun `provider and model filters isolate matching Samurai allocations`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        listOf(
            Triple("cloudflare", "kimi-k2.5", "user-1"),
            Triple("anthropic", "claude-sonnet", "user-2"),
        ).forEachIndexed { index, (provider, model, userId) ->
            val base = request("samurai:route:$index", 500_000L + index, FinOpsStatus.ACCRUED)
            service.ingest(
                base.copy(allocations = listOf(CostAllocation(
                    id = "allocation:route:$index",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = userId,
                    provider = provider,
                    modelId = model,
                    allocatedUsdMicros = base.entry.usdMicros,
                    method = AllocationMethod.METERED,
                ))),
                "test",
            )
        }

        val filtered = query().copy(provider = "cloudflare", modelId = "kimi-k2.5")
        assertEquals(1, service.entries(filtered).entries.size)
        assertEquals(listOf("user-1"), service.samuraiUsers(filtered).users.map(SamuraiUserSpend::userId))
        assertEquals(listOf("cloudflare"), service.dimensions(filtered).providers)
        assertEquals(listOf("kimi-k2.5"), service.dimensions(filtered).models)
    }

    @Test
    fun `daily finalized trend is chronological and equals the finalized headline`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        val first = request("trend:first", 1_000_000L, FinOpsStatus.FINALIZED)
        val second = request("trend:second", 2_000_000L, FinOpsStatus.FINALIZED).let { request ->
            request.copy(entry = request.entry.copy(incurredAt = 1_800_086_400_000L))
        }
        service.ingest(first, "test")
        service.ingest(second, "test")

        val summary = service.summary(query())
        assertEquals(listOf("2027-01-15", "2027-01-16"), summary.dailyFinalizedSpend.map(FinOpsBreakdown::key))
        assertEquals(listOf(1_000_000L, 2_000_000L), summary.dailyFinalizedSpend.map(FinOpsBreakdown::usdMicros))
        assertEquals(summary.finalizedSpendUsdMicros, summary.dailyFinalizedSpend.sumOf(FinOpsBreakdown::usdMicros))
    }

    @Test
    fun `run filters isolate allocations and bind pagination cursors`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        listOf("run-42", "run-42", "run-43").forEachIndexed { index, runId ->
            val base = request("samurai:run-filter:$index", 500_000L + index, FinOpsStatus.ACCRUED)
            service.ingest(
                base.copy(
                    entry = base.entry.copy(metadata = base.entry.metadata + ("runId" to runId)),
                    allocations = listOf(CostAllocation(
                        id = "allocation:run-filter:$index",
                        entryId = base.entry.id,
                        project = "samurai",
                        userId = "user-1",
                        provider = "cloudflare",
                        modelId = "kimi-k2.5",
                        runId = runId,
                        allocatedUsdMicros = base.entry.usdMicros,
                        method = AllocationMethod.METERED,
                    )),
                ),
                "test",
            )
        }

        val filtered = query().copy(runId = "run-42", limit = 1)
        val page = service.entries(filtered)
        assertEquals(1, page.entries.size)
        assertEquals(1_000_001L, service.summary(filtered).accruedSpendUsdMicros)
        assertFailsWith<IllegalArgumentException> {
            service.entries(filtered.copy(runId = "run-43", cursor = requireNotNull(page.nextCursor)))
        }
    }

    @Test
    fun `pagination cursor is bound to its complete allocation filter`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        repeat(2) { index ->
            val base = request("samurai:cursor:$index", 500_000L + index, FinOpsStatus.ACCRUED)
            service.ingest(
                base.copy(allocations = listOf(CostAllocation(
                    id = "allocation:cursor:$index",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-1",
                    provider = "cloudflare",
                    modelId = "kimi-k2.5",
                    allocatedUsdMicros = base.entry.usdMicros,
                    method = AllocationMethod.METERED,
                ))),
                "test",
            )
        }

        val query = query().copy(userId = "user-1", provider = "cloudflare", limit = 1)
        val cursor = requireNotNull(service.entries(query).nextCursor)
        assertEquals(1, service.entries(query.copy(cursor = cursor)).entries.size)
        assertFailsWith<IllegalArgumentException> {
            service.entries(query.copy(provider = "anthropic", cursor = cursor))
        }
    }

    @Test
    fun `entry ordering is cursor safe in both directions and cursors cannot cross orders`() {
        val service = FinOpsService(InMemoryFinOpsRepository())
        val requests = (0..2).map { index ->
            val base = request("manual:ordered:$index", 500_000L + index, FinOpsStatus.FINALIZED)
            base.copy(entry = base.entry.copy(incurredAt = 1_800_000_000_000L + index))
        }
        requests.forEach { service.ingest(it, "test") }

        val newest = query().copy(limit = 2, order = FinOpsEntryOrder.NEWEST)
        val newestFirst = service.entries(newest)
        assertEquals(
            listOf("manual:ordered:2", "manual:ordered:1"),
            newestFirst.entries.map(FinOpsEntry::sourceRecordId),
        )
        assertEquals(
            listOf("manual:ordered:0"),
            service.entries(newest.copy(cursor = requireNotNull(newestFirst.nextCursor))).entries
                .map(FinOpsEntry::sourceRecordId),
        )

        val oldest = newest.copy(order = FinOpsEntryOrder.OLDEST)
        val oldestFirst = service.entries(oldest)
        assertEquals(
            listOf("manual:ordered:0", "manual:ordered:1"),
            oldestFirst.entries.map(FinOpsEntry::sourceRecordId),
        )
        assertEquals(
            listOf("manual:ordered:2"),
            service.entries(oldest.copy(cursor = requireNotNull(oldestFirst.nextCursor))).entries
                .map(FinOpsEntry::sourceRecordId),
        )
        assertFailsWith<IllegalArgumentException> {
            service.entries(oldest.copy(cursor = requireNotNull(newestFirst.nextCursor)))
        }
    }

    @Test
    fun `Samurai user pages are bounded ordered and cursor isolated`() {
        val service = FinOpsService(InMemoryFinOpsRepository(), coverageSources = emptySet())
        (1..3).forEach { index ->
            val base = request("samurai:user-page:$index", 100_000L * index, FinOpsStatus.ACCRUED)
            service.ingest(
                base.copy(allocations = listOf(CostAllocation(
                    id = "allocation:user-page:$index",
                    entryId = base.entry.id,
                    project = "samurai",
                    userId = "user-$index",
                    provider = "cloudflare",
                    modelId = "model-$index",
                    allocatedUsdMicros = base.entry.usdMicros,
                    method = AllocationMethod.METERED,
                ))),
                "test",
            )
        }

        val ascending = query().copy(samuraiUserLimit = 2)
        val first = service.samuraiUsers(ascending)
        assertEquals(listOf("user-1", "user-2"), first.users.map(SamuraiUserSpend::userId))
        val cursor = requireNotNull(first.nextCursor)
        assertEquals(
            listOf("user-3"),
            service.samuraiUsers(ascending.copy(samuraiUserCursor = cursor)).users.map(SamuraiUserSpend::userId),
        )

        val descending = ascending.copy(samuraiUserOrder = SamuraiUserOrder.DESCENDING)
        assertEquals(
            listOf("user-3", "user-2"),
            service.samuraiUsers(descending).users.map(SamuraiUserSpend::userId),
        )
        assertFailsWith<IllegalArgumentException> {
            service.samuraiUsers(descending.copy(samuraiUserCursor = cursor))
        }
        assertEquals(
            listOf("user-2"),
            service.samuraiUsers(ascending.copy(userId = "user-2", samuraiUserCursor = null)).users
                .map(SamuraiUserSpend::userId),
        )
    }

    private fun request(sourceRecordId: String, micros: Long, status: FinOpsStatus): FinOpsIngestRequest {
        val id = deterministicId("test", sourceRecordId)
        return FinOpsIngestRequest(
            FinOpsEntry(
                id = id,
                source = "manual",
                sourceRecordId = sourceRecordId,
                direction = FinOpsDirection.EXPENSE,
                amount = MoneyAmount("USD", micros, 6),
                usdMicros = micros,
                incurredAt = 1_800_000_000_000L,
                invoiceMonth = "2027-01",
                project = "samurai",
                environment = "prod",
                vendor = "vendor",
                service = "service",
                status = status,
                reconciliationKey = "vendor:2027-01",
                sourceHash = sha256Hex("$sourceRecordId:$micros"),
            )
        )
    }

    private fun query() = FinOpsQuery(
        from = 1_700_000_000_000L,
        toExclusive = 1_900_000_000_000L,
        limit = 500,
    )
}
