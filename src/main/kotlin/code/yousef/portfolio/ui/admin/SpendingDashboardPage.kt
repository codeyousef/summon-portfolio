package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.finops.*
import code.yousef.portfolio.ui.components.SiteNavigation
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.*
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.html.Table
import codes.yousef.summon.components.html.Tbody
import codes.yousef.summon.components.html.Td
import codes.yousef.summon.components.html.Th
import codes.yousef.summon.components.html.Thead
import codes.yousef.summon.components.html.Tr
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.input.FormField
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.runtime.LocalPlatformRenderer
import codes.yousef.summon.runtime.NativeSelectOption
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SpendingView(val slug: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    TRANSACTIONS("transactions", "Transactions"),
    PROJECTS("projects", "Projects"),
    VENDORS("vendors", "Vendors"),
    SAMURAI_USERS("samurai-users", "Samurai users"),
    BUDGETS("budgets", "Budgets"),
    RECONCILIATION("reconciliation", "Reconciliation");

    companion object {
        fun from(value: String?): SpendingView = entries.firstOrNull { it.slug == value } ?: OVERVIEW
    }
}

enum class SpendingGroup(val slug: String, val label: String) {
    PROJECT("project", "Project"),
    ENVIRONMENT("environment", "Environment"),
    CATEGORY("category", "Category"),
    VENDOR("vendor", "Vendor"),
    SERVICE("service", "Service");

    companion object {
        fun from(value: String?): SpendingGroup = entries.firstOrNull { it.slug == value } ?: PROJECT
    }

    fun values(summary: FinOpsSummary): List<FinOpsBreakdown> = when (this) {
        PROJECT -> summary.byProject
        ENVIRONMENT -> summary.byEnvironment
        CATEGORY -> summary.byCategory
        VENDOR -> summary.byVendor
        SERVICE -> summary.byService
    }
}

internal enum class CloudPlatformBudgetState(val label: String, val color: String) {
    HEALTHY("Healthy", "#238636"),
    WARNING("Warning", "#9e6a03"),
    CRITICAL("Critical", "#d29922"),
    OVER_BUDGET("Over budget", "#da3633"),
}

internal enum class ConnectorFreshnessState(val label: String) {
    FRESH("checked recently"),
    STALE("check overdue"),
}

internal const val CONNECTOR_STALE_AFTER_MILLIS: Long = 48L * 60L * 60L * 1_000L

internal fun connectorFreshnessState(completedAt: Long, now: Long): ConnectorFreshnessState = when {
    completedAt >= now -> ConnectorFreshnessState.FRESH
    now - completedAt >= CONNECTOR_STALE_AFTER_MILLIS -> ConnectorFreshnessState.STALE
    else -> ConnectorFreshnessState.FRESH
}

internal fun connectorFailureLabel(code: String?): String? = when (code) {
    null -> null
    "configuration" -> "configuration"
    "workload_identity" -> "GCP identity"
    "provider_authorization" -> "provider authorization"
    "provider_api" -> "provider API"
    "provider_schema" -> "provider response format"
    "bigquery_authorization" -> "BigQuery authorization"
    "bigquery_query" -> "BigQuery query"
    "bigquery_schema" -> "BigQuery response format"
    "query_limit" -> "daily query cost cap reached"
    "pagination_limit" -> "pagination safety limit"
    "queue_delivery" -> "queue delivery"
    "record_validation" -> "invalid provider records"
    "unsupported_currency" -> "FX rate required"
    "cost_unavailable" -> "cost fields unavailable"
    "restricted_api_unavailable" -> "restricted usage API unavailable"
    else -> "unknown connector error"
}

internal fun connectorDisplayName(source: String): String = when (source.lowercase()) {
    "cloudflare" -> "Cloudflare billing"
    "gcp" -> "GCP billing export"
    "samurai" -> "Samurai usage"
    "stripe" -> "Stripe revenue"
    else -> source.replaceFirstChar(Char::uppercase)
}

internal fun connectorStatusLabel(status: String): String = status
    .lowercase()
    .replaceFirstChar(Char::uppercase)

internal fun formatConnectorTime(completedAt: Long): String = CONNECTOR_TIME_FORMATTER.format(
    Instant.ofEpochMilli(completedAt).atZone(RIYADH_ZONE),
) + " Riyadh"

internal fun humanizeCoverageGap(gap: String): String {
    val source = gap.substringBefore(':', missingDelimiterValue = "")
    val detail = gap.substringAfter(':', missingDelimiterValue = gap).trim()
        .replace("(query_limit)", "(daily query cost cap reached)")
    return if (source.isBlank()) detail else "${connectorDisplayName(source)}: $detail"
}

private val RIYADH_ZONE: ZoneId = ZoneId.of("Asia/Riyadh")
private val CONNECTOR_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH)

internal fun cloudPlatformBudgetState(summary: FinOpsSummary): CloudPlatformBudgetState = when {
    summary.cloudPlatformSpendUsdMicros >= summary.cloudPlatformBudgetUsdMicros ->
        CloudPlatformBudgetState.OVER_BUDGET
    summary.cloudPlatformSpendUsdMicros >= summary.budgetCriticalUsdMicros ->
        CloudPlatformBudgetState.CRITICAL
    summary.cloudPlatformSpendUsdMicros >= summary.budgetWarningUsdMicros ->
        CloudPlatformBudgetState.WARNING
    else -> CloudPlatformBudgetState.HEALTHY
}

@Composable
fun SpendingDashboardPage(
    username: String,
    summary: FinOpsSummary,
    entries: FinOpsEntryPage,
    users: SamuraiUserSpendPage,
    reconciliations: List<FinOpsReconciliation>,
    recurringExpenses: List<FinOpsRecurringExpense>,
    view: SpendingView,
    group: SpendingGroup,
    currency: String,
    rangeLabel: String,
    query: FinOpsQuery,
    csrfToken: String,
    manualSourceRecordId: String,
    receiptsEnabled: Boolean,
    successMessage: String? = null,
    errorMessage: String? = null,
) {
    Column(
        modifier = Modifier()
            .minHeight("100vh")
            .backgroundColor("#080d14")
            .color("#e6edf3")
            .fontFamily("ui-sans-serif, system-ui, sans-serif")
            .padding(24.px)
            .gap(22.px)
    ) {
        SiteNavigation(compact = true, showLocale = false)
        DashboardHeader(username, currency, rangeLabel, view, group, query)
        DashboardNav(view, currency, group, query)
        DatePresets(view, currency, group, query)
        FilterControls(view, currency, group, query)
        ActiveFilters(query)
        if (!successMessage.isNullOrBlank()) DashboardNotice(successMessage, "#0f2f1d", "#238636")
        if (!errorMessage.isNullOrBlank()) DashboardNotice(errorMessage, "#3d1117", "#da3633")
        if (summary.coverageGaps.isNotEmpty()) {
            DashboardNotice(
                "Coverage gaps: ${summary.coverageGaps.joinToString(transform = ::humanizeCoverageGap)}. " +
                    "Missing sources are shown explicitly and are never treated as zero spend.",
                "#2b2111",
                "#9e6a03",
            )
        }
        when (view) {
            SpendingView.OVERVIEW -> Overview(summary, currency, group, query)
            SpendingView.TRANSACTIONS -> Transactions(entries, currency, group, query, csrfToken, manualSourceRecordId, receiptsEnabled)
            SpendingView.PROJECTS -> BreakdownPanel(
                "Spend by project",
                summary.byProject,
                currency,
                limit = null,
                drillDown = { breakdownDrillDownHref(SpendingGroup.PROJECT, it.key, currency, group, query) },
            )
            SpendingView.VENDORS -> BreakdownPanel(
                "Spend by vendor",
                summary.byVendor,
                currency,
                limit = null,
                drillDown = { breakdownDrillDownHref(SpendingGroup.VENDOR, it.key, currency, group, query) },
            )
            SpendingView.SAMURAI_USERS -> SamuraiUsers(users, currency, group, query)
            SpendingView.BUDGETS -> Budgets(summary, currency, recurringExpenses, csrfToken)
            SpendingView.RECONCILIATION -> Reconciliation(reconciliations, currency)
        }
    }
}

@Composable
private fun FilterControls(view: SpendingView, currency: String, group: SpendingGroup, query: FinOpsQuery) {
    Form(
        action = "/admin/spending",
        method = FormMethod.Get,
        modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column)
            .gap(18.px).width(100.percent).padding(18.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px),
    ) {
        Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(4.px)) {
            Text("Filter spending", Modifier().fontSize(1.05.rem).fontWeight(800).color("#f0f6fc"))
            Text(
                "Narrow costs by studio dimensions or Samurai usage. Empty fields include everything.",
                Modifier().fontSize(0.82.rem).color("#9ba7b4"),
            )
        }
        FilterSection("Cost dimensions") {
            DashboardFilterTextField("project", "Project", query.project.orEmpty())
            DashboardFilterTextField("environment", "Environment", query.environment.orEmpty())
            DashboardFilterTextField("vendor", "Vendor", query.vendor.orEmpty())
            DashboardFilterTextField("service", "Service", query.service.orEmpty())
        }
        FilterSection("Samurai and AI") {
            DashboardFilterTextField("user", "Samurai user ID", query.userId.orEmpty())
            DashboardFilterTextField("provider", "AI provider", query.provider.orEmpty())
            DashboardFilterTextField("model", "AI model", query.modelId.orEmpty())
            DashboardFilterTextField("run", "Samurai run ID", query.runId.orEmpty())
        }
        FilterSection("Period and presentation") {
            DashboardFilterSelect(
                name = "status",
                label = "Entry state",
                options = listOf(FormSelectOption("", "All states")) + FinOpsStatus.entries.map {
                    FormSelectOption(it.name.lowercase(), it.name.lowercase().replaceFirstChar(Char::uppercase))
                },
                selectedValue = query.status?.name?.lowercase().orEmpty(),
            )
            DashboardFilterSelect(
                name = "group",
                label = "Group results by",
                options = SpendingGroup.entries.map { FormSelectOption(it.slug, it.label) },
                selectedValue = group.slug,
            )
            FormDateField("from", "From", utcDate(query.from).toString())
            FormDateField("to", "To (exclusive)", utcDate(query.toExclusive).toString())
        }
        DashboardHiddenField("view", view.slug)
        DashboardHiddenField("currency", currency)
        DashboardHiddenField("order", query.order.name.lowercase())
        DashboardHiddenField("user_order", query.samuraiUserOrder.name.lowercase())
        DashboardHiddenField("user_limit", query.samuraiUserLimit.toString())
        Row(modifier = Modifier().display(Display.Flex).justifyContent(JustifyContent.FlexEnd).width(100.percent)) {
            FormButton(
                text = "Apply filters",
                variant = FormButtonVariant.Primary,
                modifier = Modifier().minWidth(180.px).padding(11.px, 18.px).fontWeight(800),
                fullWidth = false,
            )
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(9.px)) {
        Text(title, Modifier().fontSize(0.78.rem).fontWeight(800).color("#aebaca"))
        Row(
            modifier = Modifier().display(Display.Grid)
                .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(210.px), gridFraction())))
                .gap(12.px).width(100.percent),
        ) {
            content()
        }
    }
}

@Composable
private fun DashboardFilterTextField(name: String, label: String, value: String) {
    val fieldId = "spending-filter-$name"
    Column(modifier = DashboardFilterFieldModifier()) {
        DashboardFilterLabel(label, fieldId)
        LocalPlatformRenderer.current.renderNativeInput(
            type = "text",
            modifier = DashboardFilterInputModifier().attribute("id", fieldId).attribute("name", name),
            value = value,
        )
    }
}

@Composable
private fun DashboardFilterSelect(
    name: String,
    label: String,
    options: List<FormSelectOption>,
    selectedValue: String,
) {
    val fieldId = "spending-filter-$name"
    Column(modifier = DashboardFilterFieldModifier()) {
        DashboardFilterLabel(label, fieldId)
        LocalPlatformRenderer.current.renderNativeSelect(
            modifier = DashboardFilterInputModifier().attribute("id", fieldId).attribute("name", name),
            options = options.map { option ->
                NativeSelectOption(option.value, option.label, option.value == selectedValue, option.disabled, false)
            },
        )
    }
}

@Composable
private fun DashboardFilterLabel(label: String, fieldId: String, required: Boolean = false) {
    Row(modifier = Modifier().display(Display.Flex).alignItems(AlignItems.Center).gap(4.px)) {
        Label(label, Modifier().color("#d4dde7").fontSize(0.82.rem).fontWeight(700), fieldId)
        if (required) {
            Text("*", Modifier().color("#ff7b72").fontWeight(800).attribute("aria-hidden", "true"))
        }
    }
}

@Composable
private fun DashboardHiddenField(name: String, value: String) {
    LocalPlatformRenderer.current.renderNativeInput(
        type = "hidden",
        modifier = Modifier().attribute("name", name),
        value = value,
    )
}

private fun DashboardFilterFieldModifier(): Modifier = Modifier()
    .display(Display.Flex)
    .flexDirection(FlexDirection.Column)
    .gap(7.px)
    .minWidth(0.px)
    .marginBottom(0.px)
    .color("#d4dde7")
    .fontSize(0.82.rem)
    .fontWeight(700)

private fun DashboardFilterInputModifier(): Modifier = Modifier()
    .width(100.percent)
    .minWidth(0.px)
    .height(42.px)
    .boxSizing(BoxSizing.BorderBox)
    .padding(9.px, 11.px)
    .backgroundColor("#0b121b")
    .color("#f0f6fc")
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor("#3a4a5e")
    .borderRadius(6.px)
    .fontSize(0.9.rem)

@Composable
private fun FormDateField(name: String, label: String, value: String, required: Boolean = true) {
    val fieldId = "spending-filter-$name"
    Column(modifier = DashboardFilterFieldModifier()) {
        DashboardFilterLabel(label, fieldId, required)
        var modifier = DashboardFilterInputModifier().attribute("id", fieldId).attribute("name", name)
        if (required) modifier = modifier.attribute("required", "required")
        LocalPlatformRenderer.current.renderNativeInput(
            type = "date",
            modifier = modifier,
            value = value,
        )
    }
}

@Composable
private fun DashboardHeader(username: String, currency: String, rangeLabel: String, view: SpendingView, group: SpendingGroup, query: FinOpsQuery) {
    Row(
        modifier = Modifier().width(100.percent).display(Display.Flex).justifyContent(JustifyContent.SpaceBetween)
            .alignItems(AlignItems.FlexStart).gap(16.px).flexWrap(FlexWrap.Wrap)
    ) {
        Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(5.px)) {
            Text("Studio spending", Modifier().fontSize(2.2.rem).fontWeight(850))
            Text("$rangeLabel · signed in as $username", Modifier().color("#8b949e"))
        }
        Row(modifier = Modifier().display(Display.Flex).gap(8.px).flexWrap(FlexWrap.Wrap)) {
            DashboardLink(if (currency == "sar") "USD" else "SAR", dashboardHref(view, if (currency == "sar") "usd" else "sar", group, query))
            DashboardLink("Export CSV (up to 500 rows)", csvExportHref(query, group))
            DashboardLink("Admin", "/admin")
        }
    }
}

@Composable
private fun DashboardNav(active: SpendingView, currency: String, group: SpendingGroup, query: FinOpsQuery) {
    Row(
        modifier = Modifier().display(Display.Flex).gap(8.px).flexWrap(FlexWrap.Wrap).width(100.percent)
            .paddingBottom(12.px).borderWidth(0).borderBottomWidth(1)
            .borderStyle(BorderStyle.Solid).borderColor("#263241")
            .attribute("role", "navigation").attribute("aria-label", "Spending dashboard sections")
    ) {
        SpendingView.entries.forEach { item ->
            AnchorLink(
                label = item.label,
                href = dashboardHref(item, currency, group, query),
                modifier = Modifier().padding(9.px, 12.px).borderRadius(6.px)
                    .backgroundColor(if (item == active) "#1f6feb" else "#111923")
                    .color("#f0f6fc").fontWeight(if (item == active) 800 else 600).textDecoration(TextDecoration.None)
                    .attribute("aria-current", if (item == active) "page" else "false"),
                navigationMode = LinkNavigationMode.Native,
            )
        }
    }
}

@Composable
private fun DatePresets(view: SpendingView, currency: String, group: SpendingGroup, query: FinOpsQuery) {
    val now = LocalDate.now(ZoneOffset.UTC)
    val presets = listOf(
        "Today" to (now to now.plusDays(1)),
        "7 days" to (now.minusDays(6) to now.plusDays(1)),
        "Month to date" to (now.withDayOfMonth(1) to now.plusDays(1)),
        "Previous month" to (now.withDayOfMonth(1).minusMonths(1) to now.withDayOfMonth(1)),
        "Quarter" to (now.withDayOfMonth(1).minusMonths(((now.monthValue - 1) % 3).toLong()) to now.plusDays(1)),
        "Year" to (now.withDayOfYear(1) to now.plusDays(1)),
    )
    Row(
        modifier = Modifier().display(Display.Flex).gap(7.px).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center)
            .attribute("role", "navigation").attribute("aria-label", "Date range presets"),
    ) {
        Text("Range", Modifier().color("#8b949e").fontSize(0.8.rem).fontWeight(700))
        presets.forEach { (label, dates) ->
            DashboardLink(
                label,
                dashboardHref(
                    view,
                    currency,
                    group,
                    query.copy(
                        from = dates.first.utcMillis(),
                        toExclusive = dates.second.utcMillis(),
                        cursor = null,
                        samuraiUserCursor = null,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun ActiveFilters(query: FinOpsQuery) {
    val filters = listOfNotNull(
        query.project?.let { "Project: $it" },
        query.environment?.let { "Environment: $it" },
        query.vendor?.let { "Vendor: $it" },
        query.service?.let { "Service: $it" },
        query.userId?.let { "User: $it" },
        query.provider?.let { "Provider: $it" },
        query.modelId?.let { "Model: $it" },
        query.runId?.let { "Run: $it" },
        query.status?.let { "Status: ${it.name.lowercase()}" },
    )
    if (filters.isNotEmpty()) {
        Row(modifier = Modifier().display(Display.Flex).gap(7.px).flexWrap(FlexWrap.Wrap)) {
            filters.forEach { StatusBadge(it) }
            DashboardLink("Clear filters", "/admin/spending")
        }
    }
}

@Composable
private fun Overview(summary: FinOpsSummary, currency: String, group: SpendingGroup, query: FinOpsQuery) {
    CloudPlatformBudgetPanel(summary, currency)
    Row(
        modifier = Modifier().display(Display.Grid)
            .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(205.px), gridFraction())))
            .gap(12.px).width(100.percent)
    ) {
        MetricCard("Finalized spend", money(summary.finalizedSpendUsdMicros, currency), "Invoice-grade entries")
        MetricCard("Accrued", money(summary.accruedSpendUsdMicros, currency), "Current unfinalized charges")
        MetricCard("Forecast", money(summary.forecastSpendUsdMicros, currency), "Projected through the selected period")
        MetricCard("Period change", formatBasisPoints(summary.monthOverMonthBasisPoints), "Compared with the preceding equal-length period")
        MetricCard("Revenue", money(summary.revenueUsdMicros, currency), "Kept separate from spend")
        MetricCard("Gross margin", money(summary.grossMarginUsdMicros, currency), "Revenue − AI − Stripe fees")
        MetricCard("Contribution margin", money(summary.contributionMarginUsdMicros, currency), "Revenue − all Samurai operating costs")
        MetricCard("Direct AI", money(summary.directAiCostUsdMicros, currency), "Executed provider routes")
        MetricCard("Stripe fees", money(summary.stripeFeesUsdMicros, currency), "Actual payment processing fees")
        MetricCard("Unreconciled", money(summary.unreconciledUsdMicros, currency), "Estimated and accrued")
        MetricCard("Unallocated", money(summary.unallocatedUsdMicros, currency), "Charges not yet attributed to a user or resource")
    }
    FinalizedSpendTrend(summary.dailyFinalizedSpend, currency, group, query)
    BreakdownPanel(
        "Grouped by ${group.label.lowercase()}",
        group.values(summary),
        currency,
        limit = null,
        drillDown = { breakdownDrillDownHref(group, it.key, currency, group, query) },
    )
    Row(
        modifier = Modifier().display(Display.Grid)
            .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(310.px), gridFraction())))
            .gap(16.px).width(100.percent)
    ) {
        BreakdownPanel(
            "Projects",
            summary.byProject,
            currency,
            drillDown = { breakdownDrillDownHref(SpendingGroup.PROJECT, it.key, currency, group, query) },
        )
        BreakdownPanel(
            "Environments",
            summary.byEnvironment,
            currency,
            drillDown = { breakdownDrillDownHref(SpendingGroup.ENVIRONMENT, it.key, currency, group, query) },
        )
        BreakdownPanel("Categories", summary.byCategory, currency)
        BreakdownPanel(
            "Vendors",
            summary.byVendor,
            currency,
            drillDown = { breakdownDrillDownHref(SpendingGroup.VENDOR, it.key, currency, group, query) },
        )
        BreakdownPanel(
            "Services",
            summary.byService,
            currency,
            drillDown = { breakdownDrillDownHref(SpendingGroup.SERVICE, it.key, currency, group, query) },
        )
    }
    if (summary.connectorStates.isNotEmpty()) {
        val now = System.currentTimeMillis()
        val freshness = summary.connectorStates.mapValues { (_, state) ->
            connectorFreshnessState(state.completedAt, now)
        }
        Row(modifier = Modifier().display(Display.Flex).gap(7.px).flexWrap(FlexWrap.Wrap)) {
            summary.connectorStates.forEach { (source, state) ->
                val failure = connectorFailureLabel(state.failureCode)?.let { " · $it" }.orEmpty()
                StatusBadge(
                    "${connectorDisplayName(source)} · ${connectorStatusLabel(state.status)} · " +
                        "${formatConnectorTime(state.completedAt)} · " +
                        freshness.getValue(source).label + failure,
                )
            }
        }
        val failedSources = summary.connectorStates
            .filterValues { it.status == "failed" }
            .map { (source, state) ->
                connectorFailureLabel(state.failureCode)?.let { "${connectorDisplayName(source)} ($it)" }
                    ?: connectorDisplayName(source)
            }
            .sorted()
        if (failedSources.isNotEmpty()) {
            DashboardNotice(
                "Needs attention: ${failedSources.joinToString()}. These connectors read billing or usage records only; " +
                    "the failed attempt added no costs and existing ledger data was left unchanged.",
                "#2b1115",
                "#a33a46",
            )
        }
        val staleSources = freshness.filterValues { it == ConnectorFreshnessState.STALE }.keys
            .map(::connectorDisplayName)
            .sorted()
        if (staleSources.isNotEmpty()) {
            DashboardNotice(
                "Stale connectors: ${staleSources.joinToString()}. Current-period costs may be incomplete until the next successful import.",
                "#2b2111",
                "#9e6a03",
            )
        }
    } else if (summary.dataFreshness.isNotEmpty()) {
        val now = System.currentTimeMillis()
        Row(modifier = Modifier().display(Display.Flex).gap(7.px).flexWrap(FlexWrap.Wrap)) {
            summary.dataFreshness.forEach { (source, completedAt) ->
                StatusBadge(
                    "${connectorDisplayName(source)} · Status unavailable · ${formatConnectorTime(completedAt)} · " +
                        connectorFreshnessState(completedAt, now).label,
                )
            }
        }
    } else {
        DashboardNotice("No connector freshness receipts have arrived yet.", "#161d27", "#526273")
    }
}

@Composable
private fun FinalizedSpendTrend(
    values: List<FinOpsBreakdown>,
    currency: String,
    group: SpendingGroup,
    query: FinOpsQuery,
) {
    val displayed = values.takeLast(31)
    val maximum = displayed.maxOfOrNull { kotlin.math.abs(it.usdMicros) }?.coerceAtLeast(1L) ?: 1L
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px)
            .attribute("role", "region").attribute("aria-label", "Finalized spend trend"),
    ) {
        Text("Finalized spend by day", Modifier().fontSize(1.08.rem).fontWeight(800))
        Text(
            if (values.size > displayed.size) "Most recent 31 covered days" else "Invoice-grade daily totals",
            Modifier().color("#8b949e").fontSize(0.8.rem),
        )
        if (displayed.isEmpty()) {
            Text("No finalized daily spend yet.", Modifier().color("#8b949e"))
        } else {
            Row(
                modifier = Modifier().width(100.percent).overflowX(Overflow.Auto).gap(9.px)
                    .alignItems(AlignItems.FlexEnd).paddingBottom(4.px),
            ) {
                displayed.forEach { point ->
                    val height = ((kotlin.math.abs(point.usdMicros).toDouble() / maximum) * 104.0)
                        .coerceAtLeast(3.0).toInt()
                    Column(
                        modifier = Modifier().minWidth(68.px).gap(5.px).alignItems(AlignItems.Center)
                            .attribute("aria-label", "${point.key}: ${money(point.usdMicros, currency)} finalized spend"),
                    ) {
                        Text(money(point.usdMicros, currency), Modifier().fontSize(0.7.rem).fontFamily("ui-monospace, monospace"))
                        Box(
                            modifier = Modifier().width(22.px).height(height.px)
                                .backgroundColor(if (point.usdMicros < 0L) "#3fb950" else "#2f81f7")
                                .borderRadius(4.px),
                        ) {}
                        val day = LocalDate.parse(point.key)
                        AnchorLink(
                            label = point.key.substring(5),
                            href = dashboardHref(
                                SpendingView.TRANSACTIONS,
                                currency,
                                group,
                                query.copy(
                                    from = day.utcMillis(),
                                    toExclusive = day.plusDays(1).utcMillis(),
                                    cursor = null,
                                    samuraiUserCursor = null,
                                ),
                            ),
                            modifier = Modifier().color("#58a6ff").fontSize(0.7.rem)
                                .textDecoration(TextDecoration.None)
                                .attribute("aria-label", "Show finalized transactions for $day UTC"),
                            navigationMode = LinkNavigationMode.Native,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudPlatformBudgetPanel(summary: FinOpsSummary, currency: String) {
    val state = cloudPlatformBudgetState(summary)
    val used = summary.cloudPlatformSpendUsdMicros.coerceAtLeast(0L)
    val budget = summary.cloudPlatformBudgetUsdMicros
    val percentage = ((used.toDouble() / budget.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    Column(
        modifier = Modifier().padding(17.px).gap(10.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor(state.color).borderRadius(8.px)
            .attribute("role", "status").attribute("aria-label", "Cloud platform budget status: ${state.label}"),
    ) {
        Row(
            modifier = Modifier().display(Display.Flex).justifyContent(JustifyContent.SpaceBetween)
                .alignItems(AlignItems.Center).gap(12.px).flexWrap(FlexWrap.Wrap),
        ) {
            Column(modifier = Modifier().gap(4.px)) {
                Text("Cloud platform budget", Modifier().fontSize(1.08.rem).fontWeight(800))
                Text(
                    "${money(used, currency)} of ${money(budget, currency)} used",
                    Modifier().color("#9ba7b4").fontSize(0.86.rem),
                )
            }
            StatusBadge(state.label)
        }
        Box(modifier = Modifier().height(8.px).width(100.percent).backgroundColor("#202c3a").borderRadius(4.px)) {
            if (percentage > 0.0) {
                Box(
                    modifier = Modifier().height(8.px).width(percentage.percent)
                        .backgroundColor(state.color).borderRadius(4.px),
                ) {}
            }
        }
        Text(
            "Warning ${money(summary.budgetWarningUsdMicros, currency)} · " +
                "Critical ${money(summary.budgetCriticalUsdMicros, currency)} · " +
                "Remaining ${money((budget - used).coerceAtLeast(0L), currency)}",
            Modifier().color("#718096").fontSize(0.78.rem),
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String) {
    Column(
        modifier = Modifier().padding(16.px).gap(7.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px)
    ) {
        Text(title, Modifier().color("#9ba7b4").fontSize(0.82.rem).fontWeight(700))
        Text(value, Modifier().fontSize(1.65.rem).fontWeight(850))
        Text(detail, Modifier().color("#718096").fontSize(0.78.rem))
    }
}

@Composable
private fun BreakdownPanel(
    title: String,
    values: List<FinOpsBreakdown>,
    currency: String,
    limit: Int? = 12,
    drillDown: ((FinOpsBreakdown) -> String?)? = null,
) {
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px)
            .minWidth(0.px)
    ) {
        Text(title, Modifier().fontSize(1.08.rem).fontWeight(800))
        if (values.isEmpty()) Text("No covered spend yet.", Modifier().color("#8b949e"))
        val maximum = values.maxOfOrNull { kotlin.math.abs(it.usdMicros) }?.coerceAtLeast(1L) ?: 1L
        val displayed = limit?.let(values::take) ?: values
        displayed.forEach { item ->
            Column(modifier = Modifier().gap(5.px)) {
                Row(modifier = Modifier().display(Display.Flex).justifyContent(JustifyContent.SpaceBetween).gap(12.px)) {
                    val href = drillDown?.invoke(item)
                    if (href == null) {
                        Text(item.key, Modifier().fontSize(0.86.rem))
                    } else {
                        AnchorLink(
                            label = item.key,
                            href = href,
                            modifier = Modifier().color("#58a6ff").fontSize(0.86.rem)
                                .textDecoration(TextDecoration.None)
                                .attribute("aria-label", "Show transactions for ${title.lowercase()} ${item.key}"),
                            navigationMode = LinkNavigationMode.Native,
                        )
                    }
                    Text(money(item.usdMicros, currency), Modifier().fontFamily("ui-monospace, monospace").fontSize(0.82.rem))
                }
                Box(modifier = Modifier().height(5.px).width(100.percent).backgroundColor("#202c3a").borderRadius(4.px)) {
                    Box(
                        modifier = Modifier().height(5.px)
                            .width(((kotlin.math.abs(item.usdMicros).toDouble() / maximum) * 100).coerceIn(1.0, 100.0).percent)
                            .backgroundColor(if (item.usdMicros < 0L) "#3fb950" else "#2f81f7")
                            .borderRadius(4.px)
                            .attribute("role", "img")
                            .attribute(
                                "aria-label",
                                if (item.usdMicros < 0L) {
                                    "${item.key}: ${money(item.usdMicros, currency)} credit or refund reduction"
                                } else {
                                    "${item.key}: ${money(item.usdMicros, currency)} spend"
                                },
                            )
                    ) {}
                }
            }
        }
        val remaining = values.drop(displayed.size)
        if (remaining.isNotEmpty()) {
            val remainingUsdMicros = remaining.fold(0L) { total, item -> Math.addExact(total, item.usdMicros) }
            Row(
                modifier = Modifier().display(Display.Flex).justifyContent(JustifyContent.SpaceBetween).gap(12.px)
                    .paddingTop(7.px).borderTopWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241"),
            ) {
                Text("${remaining.size} more", Modifier().color("#8b949e").fontSize(0.82.rem))
                Text(money(remainingUsdMicros, currency), Modifier().fontFamily("ui-monospace, monospace").fontSize(0.82.rem))
            }
        }
    }
}

@Composable
private fun Transactions(
    page: FinOpsEntryPage,
    currency: String,
    group: SpendingGroup,
    query: FinOpsQuery,
    csrfToken: String,
    manualSourceRecordId: String,
    receiptsEnabled: Boolean,
) {
    Column(modifier = Modifier().gap(12.px)) {
        ManualExpenseForm(csrfToken, manualSourceRecordId, receiptsEnabled)
        CsvImportForm(csrfToken)
        if (page.entries.isEmpty()) {
            DashboardNotice(
                "No transactions match this date range and filter combination. Missing connector coverage is reported on Overview rather than shown as zero spend.",
                "#161d27",
                "#526273",
            )
        }
        TableShell("Transactions") {
            Table {
                    Thead {
                        Tr {
                            val nextOrder = if (query.order == FinOpsEntryOrder.NEWEST) {
                                FinOpsEntryOrder.OLDEST
                            } else {
                                FinOpsEntryOrder.NEWEST
                            }
                            Th(
                                scope = "col",
                                modifier = Modifier().attribute(
                                    "aria-sort",
                                    if (query.order == FinOpsEntryOrder.NEWEST) "descending" else "ascending",
                                ),
                            ) {
                                AnchorLink(
                                    label = if (query.order == FinOpsEntryOrder.NEWEST) "Date ↓" else "Date ↑",
                                    href = dashboardHref(
                                        SpendingView.TRANSACTIONS,
                                        currency,
                                        group,
                                        query.copy(order = nextOrder, cursor = null),
                                    ),
                                    modifier = Modifier().color("#e6edf3").fontWeight(800)
                                        .textDecoration(TextDecoration.None)
                                        .attribute("aria-label", "Sort transactions by date ${nextOrder.name.lowercase()}"),
                                    navigationMode = LinkNavigationMode.Native,
                                )
                            }
                            listOf(
                                "Project", "Vendor / service", "Run", "Type", "State", "Source amount",
                                "Effective USD rate", currency.uppercase(), "Receipt",
                            )
                                .forEach { Th(scope = "col") { Text(it) } }
                        }
                    }
                    Tbody {
                        page.entries.forEach { entry ->
                            Tr {
                                Td {
                                    val day = utcDate(entry.incurredAt)
                                    AnchorLink(
                                        label = day.toString(),
                                        href = dashboardHref(
                                            SpendingView.TRANSACTIONS,
                                            currency,
                                            group,
                                            query.copy(
                                                from = day.utcMillis(),
                                                toExclusive = day.plusDays(1).utcMillis(),
                                                cursor = null,
                                                samuraiUserCursor = null,
                                            ),
                                        ),
                                        modifier = Modifier().color("#58a6ff").textDecoration(TextDecoration.None)
                                            .attribute("aria-label", "Show transactions for $day UTC"),
                                        navigationMode = LinkNavigationMode.Native,
                                    )
                                }
                                Td { Text(entry.project) }
                                Td { Text("${entry.vendor} / ${entry.service}") }
                                Td {
                                    entry.metadata["runId"]?.let { runId ->
                                        AnchorLink(
                                            label = runId,
                                            href = dashboardHref(
                                                SpendingView.TRANSACTIONS,
                                                currency,
                                                group,
                                                query.copy(runId = runId, cursor = null, samuraiUserCursor = null),
                                            ),
                                            modifier = Modifier().fontFamily("ui-monospace, monospace").fontSize(0.78.rem)
                                                .color("#58a6ff").textDecoration(TextDecoration.None)
                                                .attribute("aria-label", "Show transactions for Samurai run $runId"),
                                            navigationMode = LinkNavigationMode.Native,
                                        )
                                    } ?: Text(
                                        "—",
                                        Modifier().fontFamily("ui-monospace, monospace").fontSize(0.78.rem),
                                    )
                                }
                                Td { StatusBadge(entry.direction.name.lowercase()) }
                                Td { StatusBadge(entry.status.name.lowercase()) }
                                Td { Text("${entry.amount.decimal().toPlainString()} ${entry.amount.currency}") }
                                Td { Text(effectiveUsdRate(entry), Modifier().fontFamily("ui-monospace, monospace").fontSize(0.78.rem)) }
                                Td { Text(money(entry.signedLedgerMicros, currency)) }
                                Td {
                                    if (entry.hasReceiptMetadata()) {
                                        AnchorLink(
                                            label = "Download",
                                            href = "/api/admin/finops/entries/${entry.id}/receipt",
                                            modifier = Modifier().color("#58a6ff").textDecoration(TextDecoration.None)
                                                .attribute("aria-label", "Download receipt for ${entry.id}"),
                                            navigationMode = LinkNavigationMode.Native,
                                        )
                                    } else {
                                        Text("—")
                                    }
                                }
                            }
                        }
                    }
            }
        }
        page.nextCursor?.let { cursor ->
            DashboardLink(
                "Next page",
                apiHref(
                    "/admin/spending",
                    query,
                    mapOf(
                        "view" to SpendingView.TRANSACTIONS.slug,
                        "currency" to currency,
                        "group" to group.slug,
                        "cursor" to cursor,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun CsvImportForm(csrfToken: String) {
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px),
    ) {
        Text("Import CSV", Modifier().fontSize(1.08.rem).fontWeight(800))
        Text("Up to 5,000 rows or 1 MiB. The whole file is validated before the first ledger write.", Modifier().color("#8b949e"))
        Form(
            action = "/admin/spending/import.csv",
            method = FormMethod.Post,
            encType = FormEncType.Multipart,
            hiddenFields = listOf(FormHiddenField("csrf", csrfToken)),
            modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(10.px),
        ) {
            FormField(label = { Text("CSV file") }, isRequired = true) {
                LocalPlatformRenderer.current.renderNativeInput(
                    type = "file",
                    modifier = Modifier().attribute("name", "csv").attribute("accept", ".csv,text/csv")
                        .attribute("required", "required"),
                )
            }
            Text(
                "Required headers: source_record_id, direction, amount, currency, usd_micros, incurred_at, project, environment, vendor, service, status, reconciliation_key.",
                Modifier().color("#718096").fontSize(0.78.rem),
            )
            FormButton(text = "Import CSV", variant = FormButtonVariant.Primary, fullWidth = false)
        }
    }
}

@Composable
private fun ManualExpenseForm(csrfToken: String, sourceRecordId: String, receiptsEnabled: Boolean) {
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px),
    ) {
        Text("Add an expense", Modifier().fontSize(1.08.rem).fontWeight(800))
        Text("Amounts stay in their original currency and are converted to exact USD micros.", Modifier().color("#8b949e"))
        Form(
            action = "/admin/spending/manual-entry",
            method = FormMethod.Post,
            encType = FormEncType.Multipart,
            hiddenFields = listOf(
                FormHiddenField("csrf", csrfToken),
                FormHiddenField("source_record_id", sourceRecordId),
            ),
            modifier = Modifier().display(Display.Grid)
                .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(175.px), gridFraction())))
                .gap(10.px).width(100.percent),
        ) {
            FormSelect(
                name = "direction",
                label = "Type",
                options = listOf(
                    FormSelectOption("expense", "Expense"),
                    FormSelectOption("fee", "Fee"),
                    FormSelectOption("tax", "Tax"),
                    FormSelectOption("refund", "Refund"),
                    FormSelectOption("credit", "Credit"),
                ),
                selectedValue = "expense",
            )
            FormTextField(name = "amount", label = "Amount", fieldModifier = Modifier().attribute("required", "required"))
            FormCurrencyField()
            FormTextField(name = "incurred_date", label = "Date (YYYY-MM-DD)", defaultValue = LocalDate.now(ZoneOffset.UTC).toString(), fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "project", label = "Project", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "environment", label = "Environment", defaultValue = "prod", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "vendor", label = "Vendor", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "service", label = "Service", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "sku", label = "SKU / plan (optional)")
            FormSelect(
                name = "status",
                label = "State",
                options = listOf(
                    FormSelectOption("finalized", "Finalized"),
                    FormSelectOption("accrued", "Accrued"),
                    FormSelectOption("estimated", "Estimated"),
                ),
                selectedValue = "finalized",
            )
            FormTextField(name = "reconciliation_key", label = "Reconciliation key (optional)")
            if (receiptsEnabled) {
                FormField(label = { Text("Receipt (optional)") }) {
                    LocalPlatformRenderer.current.renderNativeInput(
                        type = "file",
                        modifier = Modifier().attribute("name", "receipt")
                            .attribute("accept", ".pdf,.json,.csv,.txt,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"),
                    )
                }
            }
            FormButton(text = "Save expense", variant = FormButtonVariant.Primary, fullWidth = false)
        }
    }
}

@Composable
private fun SamuraiUsers(page: SamuraiUserSpendPage, currency: String, group: SpendingGroup, query: FinOpsQuery) {
    Column(modifier = Modifier().gap(12.px)) {
        if (page.users.isEmpty()) {
            DashboardNotice(
                "No Samurai users match this date range and filter combination. User identities appear only after metered or allocated activity is available.",
                "#161d27",
                "#526273",
            )
        } else TableShell("Samurai user spending") {
            Table {
                Thead {
                    Tr {
                        val nextOrder = if (query.samuraiUserOrder == SamuraiUserOrder.ASCENDING) {
                            SamuraiUserOrder.DESCENDING
                        } else {
                            SamuraiUserOrder.ASCENDING
                        }
                        Th(
                            scope = "col",
                            modifier = Modifier().attribute(
                                "aria-sort",
                                query.samuraiUserOrder.name.lowercase(),
                            ),
                        ) {
                            AnchorLink(
                                label = if (query.samuraiUserOrder == SamuraiUserOrder.ASCENDING) "User ↑" else "User ↓",
                                href = dashboardHref(
                                    SpendingView.SAMURAI_USERS,
                                    currency,
                                    group,
                                    query.copy(samuraiUserOrder = nextOrder, samuraiUserCursor = null),
                                ),
                                modifier = Modifier().color("#e6edf3").fontWeight(800)
                                    .textDecoration(TextDecoration.None)
                                    .attribute("aria-label", "Sort Samurai users ${nextOrder.name.lowercase()}"),
                                navigationMode = LinkNavigationMode.Native,
                            )
                        }
                        listOf("Requests", "Fallbacks", "Tools", "Credits", "Providers / models", "Metered cost", "Invoice allocation", "Revenue", "Margin")
                            .forEach { Th(scope = "col") { Text(it) } }
                    }
                }
                Tbody {
                    page.users.forEach { user ->
                        Tr {
                            Td {
                                DashboardLink(
                                    user.displayName ?: user.email ?: user.userId,
                                    dashboardHref(
                                        SpendingView.TRANSACTIONS,
                                        currency,
                                        group,
                                        query.copy(userId = user.userId, cursor = null, samuraiUserCursor = null),
                                    ),
                                )
                            }
                            Td { Text(user.requests.toString()) }
                            Td { Text(user.fallbacks.toString()) }
                            Td { Text(user.toolCalls.toString()) }
                            Td { Text(user.creditsBurned.toString()) }
                            Td {
                                Text(
                                    (user.byProvider.take(2).map(FinOpsBreakdown::key) + user.byModel.take(2).map(FinOpsBreakdown::key))
                                        .distinct().joinToString(" · ").ifBlank { "—" },
                                    Modifier().fontSize(0.78.rem),
                                )
                            }
                            Td { Text(money(user.providerCostUsdMicros, currency)) }
                            Td { Text(money(user.allocatedInvoiceUsdMicros, currency)) }
                            Td { Text(money(user.revenueUsdMicros, currency)) }
                            Td { Text(money(user.grossMarginUsdMicros, currency)) }
                        }
                    }
                }
            }
        }
        page.nextCursor?.let { cursor ->
            DashboardLink(
                "Next user page",
                apiHref(
                    "/admin/spending",
                    query,
                    mapOf(
                        "view" to SpendingView.SAMURAI_USERS.slug,
                        "currency" to currency,
                        "group" to group.slug,
                        "user_cursor" to cursor,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun Budgets(
    summary: FinOpsSummary,
    currency: String,
    recurringExpenses: List<FinOpsRecurringExpense>,
    csrfToken: String,
) {
    val used = summary.cloudPlatformSpendUsdMicros
    Column(modifier = Modifier().maxWidth(760.px).gap(14.px)) {
        CloudPlatformBudgetPanel(summary, currency)
        MetricCard("Cloud platform budget", money(summary.cloudPlatformBudgetUsdMicros, currency), "Separate from overall studio spend")
        MetricCard("Used this period", money(used, currency), "Warnings at ${money(summary.budgetWarningUsdMicros, currency)} and ${money(summary.budgetCriticalUsdMicros, currency)}")
        MetricCard("Remaining", money((summary.cloudPlatformBudgetUsdMicros - used).coerceAtLeast(0L), currency), "Enforcement begins after two reconciled cycles")
        CloudPlatformBudgetForm(csrfToken)
        RecurringExpenseForm(csrfToken)
        Text("Recurring expenses", Modifier().fontSize(1.08.rem).fontWeight(800))
        if (recurringExpenses.isEmpty()) {
            Text("No recurring expenses configured.", Modifier().color("#8b949e"))
        } else {
            TableShell("Recurring expenses") {
                Table {
                    Thead { Tr { listOf("Vendor / service", "Project", "Cadence", "Starts", "Ends", "Amount", "USD").forEach { Th(scope = "col") { Text(it) } } } }
                    Tbody {
                        recurringExpenses.forEach { expense ->
                            Tr {
                                Td { Text("${expense.vendor} / ${expense.service}") }
                                Td { Text(expense.project) }
                                Td { Text(expense.cadence.name.lowercase()) }
                                Td { Text(expense.startDate) }
                                Td { Text(expense.endDateExclusive ?: "—") }
                                Td { Text("${expense.amount.decimal()} ${expense.amount.currency}") }
                                Td { Text(money(expense.usdMicros, currency)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudPlatformBudgetForm(csrfToken: String) {
    val monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px),
    ) {
        Text("Set cloud platform budget", Modifier().fontSize(1.08.rem).fontWeight(800))
        Text(
            "Creates an immutable, effective-dated budget version. The hard gate stays disabled until two billing cycles reconcile.",
            Modifier().color("#8b949e"),
        )
        Form(
            action = "/admin/spending/budget",
            method = FormMethod.Post,
            hiddenFields = listOf(FormHiddenField("csrf", csrfToken)),
            modifier = Modifier().display(Display.Grid)
                .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(175.px), gridFraction())))
                .gap(10.px).width(100.percent),
        ) {
            FormTextField(
                name = "amount_usd",
                label = "Budget (USD)",
                defaultValue = "100.00",
                fieldModifier = Modifier().attribute("required", "required").attribute("inputmode", "decimal"),
            )
            FormTextField(
                name = "warning_percent",
                label = "Warning (%)",
                defaultValue = "75",
                fieldModifier = Modifier().attribute("required", "required").attribute("type", "number")
                    .attribute("min", "1").attribute("max", "100").attribute("step", "1"),
            )
            FormTextField(
                name = "critical_percent",
                label = "Critical (%)",
                defaultValue = "90",
                fieldModifier = Modifier().attribute("required", "required").attribute("type", "number")
                    .attribute("min", "1").attribute("max", "100").attribute("step", "1"),
            )
            FormDateField("effective_from", "Effective from", monthStart.toString())
            FormDateField("effective_to", "Effective until (exclusive, optional)", "", required = false)
            FormButton(text = "Save budget version", variant = FormButtonVariant.Primary, fullWidth = false)
        }
    }
}

@Composable
private fun RecurringExpenseForm(csrfToken: String) {
    Column(
        modifier = Modifier().padding(17.px).gap(12.px).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px),
    ) {
        Text("Add recurring expense", Modifier().fontSize(1.08.rem).fontWeight(800))
        Text("For fixed vendors without an API. Each due date becomes one finalized, idempotent ledger entry.", Modifier().color("#8b949e"))
        Form(
            action = "/admin/spending/recurring",
            method = FormMethod.Post,
            hiddenFields = listOf(FormHiddenField("csrf", csrfToken)),
            modifier = Modifier().display(Display.Grid)
                .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(175.px), gridFraction())))
                .gap(10.px).width(100.percent),
        ) {
            FormTextField(name = "amount", label = "Amount", fieldModifier = Modifier().attribute("required", "required"))
            FormCurrencyField()
            FormSelect(
                name = "cadence",
                label = "Cadence",
                options = listOf(FormSelectOption("monthly", "Monthly"), FormSelectOption("yearly", "Yearly")),
                selectedValue = "monthly",
            )
            FormTextField(name = "start_date", label = "Starts (YYYY-MM-DD)", defaultValue = LocalDate.now(ZoneOffset.UTC).toString(), fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "end_date", label = "Ends before (optional)")
            FormTextField(name = "project", label = "Project", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "environment", label = "Environment", defaultValue = "prod", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "vendor", label = "Vendor", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "service", label = "Service", fieldModifier = Modifier().attribute("required", "required"))
            FormTextField(name = "sku", label = "SKU / plan (optional)")
            FormButton(text = "Save recurring expense", variant = FormButtonVariant.Primary, fullWidth = false)
        }
    }
}

@Composable
private fun FormCurrencyField() {
    FormTextField(
        name = "currency",
        label = "Currency (ISO 4217)",
        defaultValue = "USD",
        fieldModifier = Modifier()
            .attribute("required", "required")
            .attribute("pattern", "[A-Za-z]{3}")
            .attribute("maxlength", "3")
            .attribute("autocomplete", "off"),
    )
}

@Composable
private fun Reconciliation(values: List<FinOpsReconciliation>, currency: String) {
    if (values.isEmpty()) {
        DashboardNotice(
            "No reconciliation records match this date range and filter combination.",
            "#161d27",
            "#526273",
        )
        return
    }
    TableShell("Reconciliation") {
        Table {
            Thead { Tr { listOf("Key", "Estimated", "Finalized", "Difference", "State").forEach { Th(scope = "col") { Text(it) } } } }
            Tbody {
                values.forEach { item ->
                    Tr {
                        Td { Text(item.key) }
                        Td { Text(money(item.estimatedUsdMicros, currency)) }
                        Td { Text(money(item.finalizedUsdMicros, currency)) }
                        Td { Text(money(item.differenceUsdMicros, currency)) }
                        Td { StatusBadge(item.state) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableShell(label: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier().width(100.percent).overflowX(Overflow.Auto).backgroundColor("#111923")
            .borderWidth(1).borderStyle(BorderStyle.Solid).borderColor("#263241").borderRadius(8.px)
            .padding(12.px)
            .attribute("role", "region").attribute("aria-label", label).attribute("tabindex", "0")
    ) { content() }
}

@Composable
private fun StatusBadge(label: String) {
    Text(
        label.replace('_', ' '),
        Modifier().display(Display.InlineFlex).padding(4.px, 7.px).backgroundColor("#1b2735")
            .borderRadius(999.px).fontSize(0.72.rem).fontWeight(700)
    )
}

@Composable
private fun DashboardNotice(text: String, background: String, border: String) {
    Text(
        text,
        Modifier().padding(12.px, 14.px).backgroundColor(background).borderWidth(1)
            .borderStyle(BorderStyle.Solid).borderColor(border).borderRadius(6.px).lineHeight(1.5)
    )
}

@Composable
private fun DashboardLink(label: String, href: String) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier().padding(9.px, 12.px).borderWidth(1).borderStyle(BorderStyle.Solid)
            .borderColor("#344456").borderRadius(6.px).color("#e6edf3").textDecoration(TextDecoration.None),
        navigationMode = LinkNavigationMode.Native,
    )
}

private fun money(usdMicros: Long, currency: String): String {
    val negative = usdMicros < 0L
    val unsigned = when (currency.lowercase()) {
        "sar" -> "SAR ${formatSarFromUsdMicros(usdMicros).removePrefix("-")}"
        else -> "\$${formatUsdMicros(usdMicros).removePrefix("-")}"
    }
    return if (negative) "-$unsigned" else unsigned
}

private fun effectiveUsdRate(entry: FinOpsEntry): String {
    if (entry.amount.minorUnits == 0L) return "—"
    val usd = java.math.BigDecimal.valueOf(entry.usdMicros, 6)
    return usd.divide(entry.amount.decimal(), 8, java.math.RoundingMode.HALF_EVEN)
        .stripTrailingZeros()
        .toPlainString() + " USD/${entry.amount.currency}"
}

private fun FinOpsEntry.hasReceiptMetadata(): Boolean = listOf(
    "receipt.storageKey",
    "receipt.sha256",
    "receipt.filename",
    "receipt.contentType",
    "receipt.size",
).all(metadata::containsKey)

private fun formatBasisPoints(value: Long?): String = when {
    value == null -> "—"
    value > 0L -> "+${java.math.BigDecimal.valueOf(value, 2).setScale(2)}%"
    else -> "${java.math.BigDecimal.valueOf(value, 2).setScale(2)}%"
}

private fun dashboardHref(view: SpendingView, currency: String, group: SpendingGroup, query: FinOpsQuery): String =
    apiHref("/admin/spending", query, mapOf("view" to view.slug, "currency" to currency, "group" to group.slug))

private fun csvExportHref(query: FinOpsQuery, group: SpendingGroup): String = apiHref(
    "/api/admin/finops/export.csv",
    query,
    buildMap {
        put("group", group.slug)
        put("limit", "500")
        query.cursor?.let { put("cursor", it) }
    },
)

private fun breakdownDrillDownHref(
    dimension: SpendingGroup,
    value: String,
    currency: String,
    group: SpendingGroup,
    query: FinOpsQuery,
): String? {
    val filtered = when (dimension) {
        SpendingGroup.PROJECT -> query.copy(project = value, cursor = null, samuraiUserCursor = null)
        SpendingGroup.ENVIRONMENT -> query.copy(environment = value, cursor = null, samuraiUserCursor = null)
        SpendingGroup.VENDOR -> query.copy(vendor = value, cursor = null, samuraiUserCursor = null)
        SpendingGroup.SERVICE -> query.copy(service = value, cursor = null, samuraiUserCursor = null)
        SpendingGroup.CATEGORY -> return null
    }
    return dashboardHref(SpendingView.TRANSACTIONS, currency, group, filtered)
}

private fun apiHref(path: String, query: FinOpsQuery, extra: Map<String, String> = emptyMap()): String {
    val parameters = linkedMapOf(
        "from" to query.from.toString(),
        "to" to query.toExclusive.toString(),
    )
    query.project?.let { parameters["project"] = it }
    query.environment?.let { parameters["environment"] = it }
    query.vendor?.let { parameters["vendor"] = it }
    query.service?.let { parameters["service"] = it }
    query.userId?.let { parameters["user"] = it }
    query.provider?.let { parameters["provider"] = it }
    query.modelId?.let { parameters["model"] = it }
    query.runId?.let { parameters["run"] = it }
    query.status?.let { parameters["status"] = it.name.lowercase() }
    parameters["order"] = query.order.name.lowercase()
    parameters["user_order"] = query.samuraiUserOrder.name.lowercase()
    parameters["user_limit"] = query.samuraiUserLimit.toString()
    query.samuraiUserCursor?.let { parameters["user_cursor"] = it }
    parameters.putAll(extra)
    return "$path?" + parameters.entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
