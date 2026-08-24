package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.finops.*
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.server.renderSummonDocument
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.clearPlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpendingDashboardPageTest {
    @Test
    fun `transaction view renders owner expense and CSV controls with CSRF`() {
        val document = Jsoup.parse(render(SpendingView.TRANSACTIONS))

        assertTrue(document.selectFirst("header.site-navigation-shell nav[aria-label=Primary]") != null)
        val spendingNavigation = requireNotNull(document.selectFirst("*[aria-label='Spending dashboard sections']"))
        assertEquals(7, spendingNavigation.select("a").count { it.text() in SpendingView.entries.map(SpendingView::label) })
        assertTrue(spendingNavigation.attr("style").contains("border-width: 0"))
        assertTrue(spendingNavigation.attr("style").contains("border-bottom-width: 1px"))
        assertEquals("csrf-token", document.selectFirst("form[action=/admin/spending/manual-entry] input[name=csrf]")?.attr("value"))
        assertEquals("owner:${"a".repeat(32)}", document.selectFirst("form[action=/admin/spending/manual-entry] input[name=source_record_id]")?.attr("value"))
        assertEquals("multipart/form-data", document.selectFirst("form[action=/admin/spending/manual-entry]")?.attr("enctype"))
        assertEquals("file", document.selectFirst("form[action=/admin/spending/manual-entry] input[name=receipt]")?.attr("type"))
        assertEquals("csrf-token", document.selectFirst("form[action=/admin/spending/import.csv] input[name=csrf]")?.attr("value"))
        assertEquals("file", document.selectFirst("form[action=/admin/spending/import.csv] input[name=csv]")?.attr("type"))
        val manualCurrency = requireNotNull(
            document.selectFirst("form[action=/admin/spending/manual-entry] input[name=currency]"),
        )
        assertEquals("USD", manualCurrency.attr("value"))
        assertEquals("[A-Za-z]{3}", manualCurrency.attr("pattern"))
        assertEquals("3", manualCurrency.attr("maxlength"))
        assertTrue(document.selectFirst("form[action=/admin/spending] input[name=provider]") != null)
        assertTrue(document.selectFirst("form[action=/admin/spending] input[name=model]") != null)
        assertTrue(document.selectFirst("form[action=/admin/spending] input[name=run]") != null)
        assertEquals("project", document.selectFirst("form[action=/admin/spending] select[name=group] option[selected]")?.attr("value"))
        assertEquals("date", document.selectFirst("form[action=/admin/spending] input[name=from]")?.attr("type"))
        assertEquals("2027-01-01", document.selectFirst("form[action=/admin/spending] input[name=from]")?.attr("value"))
        assertEquals("hidden", document.selectFirst("form[action=/admin/spending] input[name=view]")?.attr("type"))
        assertEquals("hidden", document.selectFirst("form[action=/admin/spending] input[name=currency]")?.attr("type"))
        assertEquals("hidden", document.selectFirst("form[action=/admin/spending] input[name=order]")?.attr("type"))
        assertEquals("hidden", document.selectFirst("form[action=/admin/spending] input[name=user_order]")?.attr("type"))
        assertEquals("hidden", document.selectFirst("form[action=/admin/spending] input[name=user_limit]")?.attr("type"))
        assertEquals(5, document.select("form[action=/admin/spending] input[type=hidden]").size)
        assertTrue(document.text().contains("Filter spending"))
        assertTrue(document.text().contains("Cost dimensions"))
        assertTrue(document.text().contains("Samurai and AI"))
        assertTrue(document.text().contains("Period and presentation"))
        assertTrue(document.select("*[onclick], *[onsubmit]").isEmpty())
    }

    @Test
    fun `transaction pagination preserves the opaque cursor and active filters`() {
        val document = Jsoup.parse(render(SpendingView.TRANSACTIONS, nextCursor = "opaque+/cursor="))
        val next = document.selectFirst("a:contains(Next page)")

        assertTrue(next != null)
        assertTrue(next.attr("href").contains("cursor=opaque%2B%2Fcursor%3D"))
        assertTrue(next.attr("href").contains("view=transactions"))
        assertTrue(next.attr("href").contains("currency=usd"))
        assertTrue(next.attr("href").contains("order=newest"))
    }

    @Test
    fun `Samurai run ids open a shareable cursor-reset drilldown`() {
        val entry = FinOpsEntry(
            id = "samurai:run-entry",
            source = "samurai",
            sourceRecordId = "run-entry",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("USD", 1, 0),
            usdMicros = 1_000_000L,
            incurredAt = 1_800_000_000_000L,
            invoiceMonth = "2027-01",
            project = "samurai",
            environment = "dev",
            vendor = "cloudflare",
            service = "workers-ai",
            sku = null,
            status = FinOpsStatus.ACCRUED,
            reconciliationKey = "run:run-42",
            sourceHash = sha256Hex("run-entry"),
            metadata = mapOf("runId" to "run-42"),
        )
        val document = Jsoup.parse(render(
            SpendingView.TRANSACTIONS,
            entries = FinOpsEntryPage(listOf(entry)),
            queryCursor = "old-cursor",
        ))
        val link = requireNotNull(document.selectFirst("a[aria-label='Show transactions for Samurai run run-42']"))

        assertEquals("run-42", link.text())
        assertTrue(link.attr("href").contains("run=run-42"))
        assertTrue(!link.attr("href").contains("cursor="))
    }

    @Test
    fun `transaction dates open a one-day cursor-reset drilldown`() {
        val entry = FinOpsEntry(
            id = "samurai:daily-entry",
            source = "samurai",
            sourceRecordId = "daily-entry",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("USD", 1, 0),
            usdMicros = 1_000_000L,
            incurredAt = 1_800_000_000_000L,
            invoiceMonth = "2027-01",
            project = "samurai",
            environment = "dev",
            vendor = "cloudflare",
            service = "workers-ai",
            sku = null,
            status = FinOpsStatus.ACCRUED,
            reconciliationKey = "daily:2027-01-15",
            sourceHash = sha256Hex("daily-entry"),
        )
        val document = Jsoup.parse(render(
            SpendingView.TRANSACTIONS,
            entries = FinOpsEntryPage(listOf(entry)),
            queryCursor = "old-cursor",
        ))
        val link = requireNotNull(document.selectFirst("a[aria-label='Show transactions for 2027-01-15 UTC']"))

        assertEquals("2027-01-15", link.text())
        assertTrue(link.attr("href").contains("from=1799971200000"))
        assertTrue(link.attr("href").contains("to=1800057600000"))
        assertTrue(!link.attr("href").contains("cursor="))
    }

    @Test
    fun `transactions expose effective FX and owner receipt download without exposing receipt metadata`() {
        val digest = "b".repeat(64)
        val entry = FinOpsEntry(
            id = "manual:invoice-entry",
            source = "manual",
            sourceRecordId = "invoice-entry",
            direction = FinOpsDirection.EXPENSE,
            amount = MoneyAmount("SAR", 375, 0),
            usdMicros = 100_000_000L,
            incurredAt = 1_800_000_000_000L,
            invoiceMonth = "2027-01",
            project = "portfolio",
            environment = "dev",
            vendor = "vendor",
            service = "subscription",
            sku = null,
            status = FinOpsStatus.FINALIZED,
            reconciliationKey = "vendor:2027-01",
            sourceHash = sha256Hex("invoice-entry"),
            metadata = mapOf(
                "receipt.storageKey" to "sha256/$digest/invoice.pdf",
                "receipt.sha256" to digest,
                "receipt.filename" to "invoice.pdf",
                "receipt.contentType" to "application/pdf",
                "receipt.size" to "128",
            ),
        )
        val document = Jsoup.parse(render(
            SpendingView.TRANSACTIONS,
            entries = FinOpsEntryPage(listOf(entry)),
        ))
        val download = requireNotNull(document.selectFirst("a[aria-label='Download receipt for manual:invoice-entry']"))

        assertTrue(document.text().contains("0.26666667 USD/SAR"))
        assertEquals("/api/admin/finops/entries/manual:invoice-entry/receipt", download.attr("href"))
        assertTrue(!document.text().contains(digest))
        assertTrue(!document.text().contains("sha256/"))
    }

    @Test
    fun `transactions show explicit direction and signed face value including revenue`() {
        fun entry(id: String, direction: FinOpsDirection) = FinOpsEntry(
            id = "ledger:$id",
            source = "manual",
            sourceRecordId = id,
            direction = direction,
            amount = MoneyAmount("USD", 200, 2),
            usdMicros = 2_000_000L,
            incurredAt = 1_800_000_000_000L,
            invoiceMonth = "2027-01",
            project = "samurai",
            environment = "dev",
            vendor = "stripe",
            service = "balance",
            status = FinOpsStatus.FINALIZED,
            reconciliationKey = "stripe:$id",
            sourceHash = sha256Hex(id),
            metadata = if (direction == FinOpsDirection.REFUND) mapOf("accountingClass" to "revenue") else emptyMap(),
        )
        val document = Jsoup.parse(render(
            SpendingView.TRANSACTIONS,
            entries = FinOpsEntryPage(listOf(
                entry("revenue", FinOpsDirection.REVENUE),
                entry("refund", FinOpsDirection.REFUND),
                entry("credit", FinOpsDirection.CREDIT),
                entry("expense", FinOpsDirection.EXPENSE),
            )),
        ))
        val rows = document.select("tbody tr").map { row -> row.select("td").map { it.text() } }

        assertTrue(document.select("th").any { it.text() == "Type" })
        assertEquals(listOf("revenue", "refund", "credit", "expense"), rows.map { it[4] })
        assertEquals(listOf("\$2.00", "-\$2.00", "-\$2.00", "\$2.00"), rows.map { it[8] })
    }

    @Test
    fun `CSV export is explicitly bounded and starts at the current transaction cursor`() {
        val document = Jsoup.parse(render(
            SpendingView.TRANSACTIONS,
            queryCursor = "v1:entry:filter:123:entry-id",
        ))
        val export = requireNotNull(document.selectFirst("a:contains(Export CSV (up to 500 rows))"))

        assertTrue(export.attr("href").startsWith("/api/admin/finops/export.csv?"))
        assertTrue(export.attr("href").contains("limit=500"))
        assertTrue(export.attr("href").contains("cursor=v1%3Aentry%3Afilter%3A123%3Aentry-id"))
        assertTrue(export.attr("href").contains("order=newest"))
    }

    @Test
    fun `empty dashboard tables explain absence instead of rendering blank data grids`() {
        val transactions = Jsoup.parse(render(SpendingView.TRANSACTIONS))
        val users = Jsoup.parse(render(SpendingView.SAMURAI_USERS))
        val reconciliation = Jsoup.parse(render(SpendingView.RECONCILIATION))

        assertTrue(transactions.text().contains("No transactions match this date range and filter combination"))
        assertTrue(users.text().contains("No Samurai users match this date range and filter combination"))
        assertTrue(reconciliation.text().contains("No reconciliation records match this date range and filter combination"))
    }

    @Test
    fun `responsive navigation and scrollable tables expose keyboard landmarks`() {
        val transactions = Jsoup.parse(render(SpendingView.TRANSACTIONS))
        val users = Jsoup.parse(render(
            SpendingView.SAMURAI_USERS,
            users = SamuraiUserSpendPage(listOf(samuraiUser())),
        ))

        assertTrue(transactions.selectFirst("[role=navigation][aria-label='Spending dashboard sections']") != null)
        assertTrue(transactions.selectFirst("[role=navigation][aria-label='Date range presets']") != null)
        assertEquals("page", transactions.selectFirst("a[aria-current=page]")?.attr("aria-current"))
        assertEquals("Transactions", transactions.selectFirst("a[aria-current=page]")?.text())
        assertEquals("0", transactions.selectFirst("[role=region][aria-label=Transactions]")?.attr("tabindex"))
        assertEquals("0", users.selectFirst("[role=region][aria-label='Samurai user spending']")?.attr("tabindex"))
    }

    @Test
    fun `budget view exposes immutable owner controls without enabling the hard gate`() {
        val document = Jsoup.parse(render(SpendingView.BUDGETS))
        val form = requireNotNull(document.selectFirst("form[action=/admin/spending/budget]"))

        assertEquals("csrf-token", form.selectFirst("input[name=csrf]")?.attr("value"))
        assertEquals("100.00", form.selectFirst("input[name=amount_usd]")?.attr("value"))
        assertEquals("75", form.selectFirst("input[name=warning_percent]")?.attr("value"))
        assertEquals("90", form.selectFirst("input[name=critical_percent]")?.attr("value"))
        assertEquals("date", form.selectFirst("input[name=effective_from]")?.attr("type"))
        assertTrue(form.selectFirst("input[name=effective_from]")?.hasAttr("required") == true)
        assertEquals("date", form.selectFirst("input[name=effective_to]")?.attr("type"))
        assertTrue(form.selectFirst("input[name=effective_to]")?.hasAttr("required") == false)
        assertTrue(form.selectFirst("input[name=hard_gate]") == null)
        assertTrue(document.text().contains("hard gate stays disabled until two billing cycles reconcile"))
    }

    @Test
    fun `transaction date sort is accessible shareable and resets its cursor`() {
        val newest = Jsoup.parse(render(SpendingView.TRANSACTIONS, nextCursor = "old-cursor"))
        val newestHeader = newest.selectFirst("th[aria-sort=descending]")
        val oldestLink = requireNotNull(newestHeader?.selectFirst("a"))

        assertEquals("Date ↓", oldestLink.text())
        assertEquals("Sort transactions by date oldest", oldestLink.attr("aria-label"))
        assertTrue(oldestLink.attr("href").contains("order=oldest"))
        assertTrue(!oldestLink.attr("href").contains("cursor="))

        val oldest = Jsoup.parse(render(SpendingView.TRANSACTIONS, order = FinOpsEntryOrder.OLDEST))
        val oldestHeader = oldest.selectFirst("th[aria-sort=ascending]")
        val newestLink = oldestHeader?.selectFirst("a")
        assertEquals("Date ↑", newestLink?.text())
        assertEquals("Sort transactions by date newest", newestLink?.attr("aria-label"))
        assertTrue(newestLink?.attr("href")?.contains("order=newest") == true)
    }

    @Test
    fun `Samurai user sort and pagination are accessible and shareable`() {
        val page = SamuraiUserSpendPage(
            users = listOf(SamuraiUserSpend(
                userId = "user-1",
                providerCostUsdMicros = 1_000_000L,
                allocatedInvoiceUsdMicros = 0L,
                creditsBurned = 1L,
                requests = 1L,
                revenueUsdMicros = 2_000_000L,
                grossMarginUsdMicros = 1_000_000L,
            )),
            nextCursor = "opaque+/user=cursor",
        )
        val document = Jsoup.parse(render(SpendingView.SAMURAI_USERS, users = page))
        val header = requireNotNull(document.selectFirst("th[aria-sort=ascending]"))
        val sort = requireNotNull(header.selectFirst("a"))

        assertEquals("User ↑", sort.text())
        assertEquals("Sort Samurai users descending", sort.attr("aria-label"))
        assertTrue(sort.attr("href").contains("user_order=descending"))
        assertTrue(!sort.attr("href").contains("user_cursor="))

        val next = requireNotNull(document.selectFirst("a:contains(Next user page)"))
        assertTrue(next.attr("href").contains("user_cursor=opaque%2B%2Fuser%3Dcursor"))
        assertTrue(next.attr("href").contains("user_order=ascending"))
        assertTrue(next.attr("href").contains("user_limit=50"))
    }

    @Test
    fun `budget view renders recurring control and configured schedule`() {
        val document = Jsoup.parse(render(SpendingView.BUDGETS))

        assertEquals("csrf-token", document.selectFirst("form[action=/admin/spending/recurring] input[name=csrf]")?.attr("value"))
        assertEquals("[A-Za-z]{3}", document.selectFirst("form[action=/admin/spending/recurring] input[name=currency]")?.attr("pattern"))
        assertTrue(document.text().contains("registrar / domain"))
        assertTrue(document.text().contains("monthly"))
    }

    @Test
    fun `overview exposes environment spending as a first-class dashboard breakdown`() {
        val document = Jsoup.parse(render(SpendingView.OVERVIEW, dashboardSummary = summary(80_000_000L)))

        assertTrue(document.text().contains("Environments"))
        assertTrue(document.text().contains("Cloud platform budget"))
        assertTrue(document.text().contains("Warning"))
        assertEquals(
            "Cloud platform budget status: Warning",
            document.selectFirst("[role=status]")?.attr("aria-label"),
        )
    }

    @Test
    fun `overview renders an accessible finalized daily trend`() {
        val document = Jsoup.parse(render(
            SpendingView.OVERVIEW,
            dashboardSummary = summary(dailyFinalizedSpend = listOf(
                FinOpsBreakdown("2027-01-14", 1_000_000L),
                FinOpsBreakdown("2027-01-15", 2_000_000L),
            )),
        ))
        val trend = requireNotNull(document.selectFirst("[role=region][aria-label='Finalized spend trend']"))

        assertTrue(trend.text().contains("Finalized spend by day"))
        assertTrue(trend.selectFirst("[aria-label='2027-01-15: \$2.00 finalized spend']") != null)
        val drillDown = requireNotNull(trend.selectFirst("a[aria-label='Show finalized transactions for 2027-01-15 UTC']"))
        assertTrue(drillDown.attr("href").contains("view=transactions"))
        assertTrue(drillDown.attr("href").contains("from=1799971200000"))
        assertTrue(drillDown.attr("href").contains("to=1800057600000"))
        assertTrue(!drillDown.attr("href").contains("cursor="))
    }

    @Test
    fun `breakdown values drill into exact transactions and preserve active filters`() {
        val query = FinOpsQuery(
            from = 1_798_761_600_000L,
            toExclusive = 1_801_440_000_000L,
            environment = "dev",
            provider = "workers-ai",
            cursor = "stale-entry-cursor",
            samuraiUserCursor = "stale-user-cursor",
        )
        val document = Jsoup.parse(render(
            SpendingView.PROJECTS,
            dashboardSummary = summary().copy(byProject = listOf(FinOpsBreakdown("portfolio", 12_000_000L))),
            queryOverride = query,
        ))
        val link = requireNotNull(document.selectFirst("a[aria-label='Show transactions for spend by project portfolio']"))

        assertTrue(link.attr("href").contains("view=transactions"))
        assertTrue(link.attr("href").contains("project=portfolio"))
        assertTrue(link.attr("href").contains("environment=dev"))
        assertTrue(link.attr("href").contains("provider=workers-ai"))
        assertTrue(!link.attr("href").contains("cursor="))
        assertTrue(!link.attr("href").contains("user_cursor="))
    }

    @Test
    fun `grouping is shareable and selects the focused overview breakdown`() {
        val document = Jsoup.parse(render(
            SpendingView.OVERVIEW,
            group = SpendingGroup.ENVIRONMENT,
            dashboardSummary = summary().copy(byEnvironment = listOf(FinOpsBreakdown("dev", 12_000_000L))),
        ))

        assertTrue(document.text().contains("Grouped by environment"))
        assertTrue(document.text().contains("dev"))
        assertTrue(document.select("a[href*=group%3Denvironment], a[href*=group=environment]").isNotEmpty())
        assertEquals("environment", document.selectFirst("select[name=group] option[selected]")?.attr("value"))
    }

    @Test
    fun `focused and dedicated breakdowns expose every value while compact panels total the remainder`() {
        val projects = (1..13).map { index -> FinOpsBreakdown("project-$index", index * 1_000_000L) }
        val summary = summary().copy(byProject = projects)
        val overview = Jsoup.parse(render(SpendingView.OVERVIEW, dashboardSummary = summary))

        val remainderLabel = requireNotNull(overview.getElementsContainingOwnText("1 more").firstOrNull())
        assertEquals("\$13.00", remainderLabel.parent()?.children()?.last()?.text())
        assertTrue(overview.select("a[aria-label^='Show transactions for grouped by project']").size >= 13)

        val dedicated = Jsoup.parse(render(SpendingView.PROJECTS, dashboardSummary = summary))
        assertTrue(!dedicated.text().contains("1 more"))
        assertTrue(
            dedicated.selectFirst("a[aria-label='Show transactions for spend by project project-13']") != null,
        )
    }

    @Test
    fun `negative breakdowns use conventional money signs and distinguish reductions from spend`() {
        val dashboardSummary = summary().copy(byProject = listOf(
            FinOpsBreakdown("refunds", -2_000_000L),
            FinOpsBreakdown("portfolio", 4_000_000L),
        ))
        val usd = Jsoup.parse(render(SpendingView.PROJECTS, dashboardSummary = dashboardSummary))
        val reduction = requireNotNull(
            usd.selectFirst("[role=img][aria-label='refunds: -\$2.00 credit or refund reduction']"),
        )
        val spend = requireNotNull(
            usd.selectFirst("[role=img][aria-label='portfolio: \$4.00 spend']"),
        )

        assertTrue(reduction.attr("style").contains("background-color: #3fb950"))
        assertTrue(spend.attr("style").contains("background-color: #2f81f7"))

        val sar = Jsoup.parse(render(
            SpendingView.PROJECTS,
            dashboardSummary = dashboardSummary,
            displayCurrency = "sar",
        ))
        assertTrue(sar.text().contains("-SAR 7.50"))
    }

    @Test
    fun `cloud platform budget state follows exact configured thresholds`() {
        assertEquals(CloudPlatformBudgetState.HEALTHY, cloudPlatformBudgetState(summary(74_999_999L)))
        assertEquals(CloudPlatformBudgetState.WARNING, cloudPlatformBudgetState(summary(75_000_000L)))
        assertEquals(CloudPlatformBudgetState.CRITICAL, cloudPlatformBudgetState(summary(90_000_000L)))
        assertEquals(CloudPlatformBudgetState.OVER_BUDGET, cloudPlatformBudgetState(summary(100_000_000L)))
    }

    @Test
    fun `connector freshness marks two missed daily cycles as stale`() {
        val completedAt = 1_800_000_000_000L
        assertEquals(
            ConnectorFreshnessState.FRESH,
            connectorFreshnessState(completedAt, completedAt + CONNECTOR_STALE_AFTER_MILLIS - 1L),
        )
        assertEquals(
            ConnectorFreshnessState.STALE,
            connectorFreshnessState(completedAt, completedAt + CONNECTOR_STALE_AFTER_MILLIS),
        )
        assertEquals(
            ConnectorFreshnessState.FRESH,
            connectorFreshnessState(completedAt + 1L, completedAt),
        )

        val document = Jsoup.parse(render(
            SpendingView.OVERVIEW,
            dashboardSummary = summary(
                dataFreshness = mapOf("gcp" to 1L),
                connectorStates = mapOf(
                    "cloudflare" to FinOpsConnectorState("partial", 1L, "restricted_api_unavailable"),
                ),
            ),
        ))
        assertTrue(document.text().contains("Cloudflare billing · Partial · Jan 1, 1970 · 3:00 AM Riyadh · check overdue · restricted usage API unavailable"))
        assertTrue(document.text().contains("Stale connectors: Cloudflare billing"))
        assertTrue(document.text().contains("Current-period costs may be incomplete"))
    }

    @Test
    fun `failed connector is explicit and never presented as a successful update`() {
        val completedAt = 1_800_000_000_000L
        val document = Jsoup.parse(render(
            SpendingView.OVERVIEW,
            dashboardSummary = summary(
                dataFreshness = mapOf("gcp" to completedAt),
                connectorStates = mapOf(
                    "gcp" to FinOpsConnectorState("failed", completedAt, "bigquery_authorization"),
                ),
            ),
        ))

        assertTrue(document.text().contains("GCP billing export · Failed"))
        assertTrue(document.text().contains("BigQuery authorization"))
        assertTrue(document.text().contains("Needs attention: GCP billing export (BigQuery authorization)"))
        assertTrue(document.text().contains("failed attempt added no costs"))
        assertTrue(!document.text().contains("GCP billing export · Succeeded"))
    }

    @Test
    fun `future-only coverage is visible and never presented as zero historical spend`() {
        SpendingView.entries.forEach { view ->
            val document = Jsoup.parse(render(
                view,
                dashboardSummary = summary(coverageGaps = listOf("studio: coverage begins 2026-08-24")),
            ))

            assertTrue(document.text().contains("Coverage gaps: Studio: coverage begins 2026-08-24"), view.name)
            assertTrue(document.text().contains("never treated as zero spend"), view.name)
        }
    }

    @Test
    fun `connector labels use billing purpose readable Riyadh time and safe failure copy`() {
        assertEquals("GCP billing export", connectorDisplayName("gcp"))
        assertEquals("Cloudflare billing", connectorDisplayName("cloudflare"))
        assertEquals("Jan 15, 2027 · 11:00 AM Riyadh", formatConnectorTime(1_800_000_000_000L))
        assertEquals(
            "GCP billing export: latest covering import failed (daily query cost cap reached)",
            humanizeCoverageGap("gcp: latest covering import failed (query_limit)"),
        )
    }

    private fun render(
        view: SpendingView,
        group: SpendingGroup = SpendingGroup.PROJECT,
        nextCursor: String? = null,
        queryCursor: String? = null,
        order: FinOpsEntryOrder = FinOpsEntryOrder.NEWEST,
        entries: FinOpsEntryPage = FinOpsEntryPage(emptyList(), nextCursor),
        users: SamuraiUserSpendPage = SamuraiUserSpendPage(emptyList()),
        dashboardSummary: FinOpsSummary = summary(),
        queryOverride: FinOpsQuery? = null,
        displayCurrency: String = "usd",
    ): String = synchronized(renderLock) {
        val renderer = PlatformRenderer()
        setPlatformRenderer(renderer)
        try {
            renderer.renderSummonDocument(SummonPage(content = {
                SpendingDashboardPage(
                    username = "owner",
                    summary = dashboardSummary,
                    entries = entries,
                    users = users,
                    reconciliations = emptyList(),
                    recurringExpenses = listOf(recurring()),
                    view = view,
                    group = group,
                    currency = displayCurrency,
                    rangeLabel = "2027-01-01 – 2027-01-31",
                    query = queryOverride ?: FinOpsQuery(
                        1_798_761_600_000L,
                        1_801_440_000_000L,
                        order = order,
                        cursor = queryCursor,
                    ),
                    csrfToken = "csrf-token",
                    manualSourceRecordId = "owner:${"a".repeat(32)}",
                    receiptsEnabled = true,
                )
            }))
        } finally {
            clearPlatformRenderer()
        }
    }

    private fun summary(
        cloudPlatformSpendUsdMicros: Long = 0L,
        dataFreshness: Map<String, Long> = emptyMap(),
        connectorStates: Map<String, FinOpsConnectorState> = emptyMap(),
        dailyFinalizedSpend: List<FinOpsBreakdown> = emptyList(),
        coverageGaps: List<String> = emptyList(),
    ) = FinOpsSummary(
        from = 1_798_761_600_000L,
        toExclusive = 1_801_440_000_000L,
        finalizedSpendUsdMicros = 0,
        accruedSpendUsdMicros = 0,
        estimatedSpendUsdMicros = 0,
        revenueUsdMicros = 0,
        stripeFeesUsdMicros = 0,
        directAiCostUsdMicros = 0,
        grossMarginUsdMicros = 0,
        contributionMarginUsdMicros = 0,
        unreconciledUsdMicros = 0,
        cloudPlatformSpendUsdMicros = cloudPlatformSpendUsdMicros,
        dataFreshness = dataFreshness,
        connectorStates = connectorStates,
        dailyFinalizedSpend = dailyFinalizedSpend,
        coverageGaps = coverageGaps,
    )

    private fun recurring() = FinOpsRecurringExpense(
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

    private fun samuraiUser() = SamuraiUserSpend(
        userId = "user-1",
        providerCostUsdMicros = 1_000_000L,
        allocatedInvoiceUsdMicros = 0L,
        creditsBurned = 1L,
        requests = 1L,
        revenueUsdMicros = 2_000_000L,
        grossMarginUsdMicros = 1_000_000L,
    )

    private companion object {
        val renderLock = Any()
    }
}
