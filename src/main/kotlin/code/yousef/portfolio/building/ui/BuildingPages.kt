package code.yousef.portfolio.building.ui

import code.yousef.portfolio.building.bulk.BulkCascadePlan
import code.yousef.portfolio.building.bulk.BulkOperationResult
import code.yousef.portfolio.building.model.*
import code.yousef.portfolio.building.payment.isUnpaidPastDue
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.html.H3
import codes.yousef.summon.components.html.Section
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.core.style.Color
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

// ===================== Login Page =====================

@Composable
fun BuildingLoginPage(errorMessage: String?) {
    BuildingAuthScaffold(
        title = BuildingStrings.LOGIN_TITLE,
        subtitle = BuildingStrings.LOGIN_SUBTITLE,
        errorMessage = errorMessage
    ) {
        Form(
            action = "/login",
            method = FormMethod.Post,
            modifier = Modifier().fillMaxWidth()
        ) {
            FormGroup(label = BuildingStrings.USERNAME) {
                FormTextField(
                    name = "username",
                    label = "",
                    defaultValue = "",
                    modifier = inputModifier()
                )
            }
            
            FormGroup(label = BuildingStrings.PASSWORD) {
                FormTextField(
                    name = "password",
                    label = "",
                    defaultValue = "",
                    type = FormTextFieldType.Password,
                    modifier = inputModifier()
                )
            }
            
            FormButton(
                text = BuildingStrings.LOGIN_BUTTON,
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.PRIMARY)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.md)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .fontWeight("600")
                    .margin(BuildingTheme.Spacing.md, "0", "0", "0")
            )
        }
    }
}

// ===================== Change Password Page =====================

@Composable
fun BuildingChangePasswordPage(username: String, errorMessage: String?) {
    BuildingAuthScaffold(
        title = BuildingStrings.CHANGE_PASSWORD_TITLE,
        subtitle = BuildingStrings.CHANGE_PASSWORD_SUBTITLE,
        errorMessage = errorMessage
    ) {
        Form(
            action = "/change-password",
            method = FormMethod.Post,
            modifier = Modifier().fillMaxWidth()
        ) {
            FormGroup(label = BuildingStrings.NEW_PASSWORD) {
                FormTextField(
                    name = "password",
                    label = "",
                    defaultValue = "",
                    type = FormTextFieldType.Password,
                    modifier = inputModifier()
                )
            }
            
            FormGroup(label = BuildingStrings.CONFIRM_PASSWORD) {
                FormTextField(
                    name = "confirm",
                    label = "",
                    defaultValue = "",
                    type = FormTextFieldType.Password,
                    modifier = inputModifier()
                )
            }
            
            FormButton(
                text = BuildingStrings.SAVE_PASSWORD,
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.PRIMARY)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.md)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .fontWeight("600")
                    .margin(BuildingTheme.Spacing.md, "0", "0", "0")
            )
        }
    }
}

// ===================== Reset Password Page =====================

@Composable
fun ResetPasswordPage(token: String?, errorMessage: String?) {
    BuildingAuthScaffold(
        title = BuildingStrings.RESET_PASSWORD_TITLE,
        subtitle = if (token != null) BuildingStrings.RESET_PASSWORD_SUBTITLE else "",
        errorMessage = errorMessage
    ) {
        if (token == null) {
            // Invalid/expired token - show error and login link
            Link(
                href = "/login",
                content = { Text(text = BuildingStrings.LOGIN_BUTTON) },
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.PRIMARY)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.md)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .fontWeight("600")
                    .textAlign(TextAlign.Center)
                    .textDecoration(TextDecoration.None)
                    .display(Display.Block)
            )
        } else {
            // Valid token - show password reset form
            Form(
                action = "/reset-password",
                method = FormMethod.Post,
                hiddenFields = listOf(FormHiddenField("token", token)),
                modifier = Modifier().fillMaxWidth()
            ) {
                FormGroup(label = BuildingStrings.NEW_PASSWORD) {
                    FormTextField(
                        name = "password",
                        label = "",
                        defaultValue = "",
                        type = FormTextFieldType.Password,
                        modifier = inputModifier()
                    )
                }
                
                FormGroup(label = BuildingStrings.CONFIRM_PASSWORD) {
                    FormTextField(
                        name = "confirm",
                        label = "",
                        defaultValue = "",
                        type = FormTextFieldType.Password,
                        modifier = inputModifier()
                    )
                }
                
                FormButton(
                    text = BuildingStrings.SAVE_PASSWORD,
                    modifier = Modifier()
                        .fillMaxWidth()
                        .backgroundColor(BuildingTheme.Colors.PRIMARY)
                        .color(BuildingTheme.Colors.TEXT_WHITE)
                        .padding(BuildingTheme.Spacing.md)
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .fontWeight("600")
                        .margin(BuildingTheme.Spacing.md, "0", "0", "0")
                )
            }
        }
    }
}

// ===================== User Management Page (Admin) =====================

@Composable
fun UserManagementPage(
    username: String,
    users: List<String>,
    generatedLink: String?,
    targetUser: String?
) {
    BuildingPageLayout(
        title = BuildingStrings.USER_MANAGEMENT,
        username = username,
        currentPath = "/admin/users"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = BuildingStrings.USER_MANAGEMENT)
            
            // Success message with generated link
            if (generatedLink != null && targetUser != null) {
                Box(
                    modifier = Modifier()
                        .fillMaxWidth()
                        .backgroundColor("#d4edda")
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .padding(BuildingTheme.Spacing.lg)
                        .margin("0", "0", BuildingTheme.Spacing.lg, "0")
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor("#c3e6cb")
                ) {
                    Column(modifier = Modifier().fillMaxWidth()) {
                        Text(
                            text = "${BuildingStrings.RESET_LINK_GENERATED} ($targetUser)",
                            modifier = Modifier()
                                .fontWeight("600")
                                .color("#155724")
                                .margin("0", "0", BuildingTheme.Spacing.sm, "0")
                        )
                        Text(
                            text = BuildingStrings.LINK_EXPIRES_IN,
                            modifier = Modifier()
                                .fontSize(BuildingTheme.FontSize.sm)
                                .color("#155724")
                                .margin("0", "0", BuildingTheme.Spacing.md, "0")
                        )
                        // Link display with copy functionality
                        Box(
                            modifier = Modifier()
                                .fillMaxWidth()
                                .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
                                .borderRadius(BuildingTheme.BorderRadius.sm)
                                .padding(BuildingTheme.Spacing.sm)
                                .wordBreak(WordBreak.BreakAll)
                                .fontFamily("monospace")
                                .fontSize(BuildingTheme.FontSize.sm)
                        ) {
                            Text(text = generatedLink)
                        }
                    }
                }
            }
            
            // Users table
            Box(
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.BG_CARD)
                    .borderRadius(BuildingTheme.BorderRadius.lg)
                    .overflow(Overflow.Hidden)
            ) {
                if (users.isEmpty()) {
                    Box(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .padding(BuildingTheme.Spacing.xl)
                            .textAlign(TextAlign.Center)
                    ) {
                        Text(
                            text = BuildingStrings.NO_USERS,
                            modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED)
                        )
                    }
                } else {
                    Column(modifier = Modifier().fillMaxWidth()) {
                        users.forEachIndexed { index, user ->
                            Row(
                                modifier = Modifier()
                                    .fillMaxWidth()
                                    .padding(BuildingTheme.Spacing.md, BuildingTheme.Spacing.lg)
                                    .alignItems(AlignItems.Center)
                                    .justifyContent(JustifyContent.SpaceBetween)
                                    .let { 
                                        if (index < users.size - 1) 
                                            it.border(
                                                BorderSide.Bottom,
                                                1,
                                                BorderStyle.Solid,
                                                BuildingTheme.Colors.BORDER,
                                            )
                                        else it
                                    }
                            ) {
                                Text(
                                    text = user,
                                    modifier = Modifier()
                                        .fontWeight("500")
                                        .color(BuildingTheme.Colors.TEXT_PRIMARY)
                                )
                                
                                Form(
                                    action = "/admin/users/$user/reset",
                                    method = FormMethod.Post,
                                    modifier = Modifier()
                                ) {
                                    FormButton(
                                        text = BuildingStrings.GENERATE_RESET_LINK,
                                        modifier = Modifier()
                                            .backgroundColor(BuildingTheme.Colors.WARNING)
                                            .color(BuildingTheme.Colors.TEXT_PRIMARY)
                                            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
                                            .borderRadius(BuildingTheme.BorderRadius.md)
                                            .fontWeight("500")
                                            .fontSize(BuildingTheme.FontSize.sm)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildingAuthScaffold(
    title: String,
    subtitle: String,
    errorMessage: String?,
    content: @Composable () -> Unit
) {
    BuildingBaseStyles()

    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .minHeight("100vh")
            .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
    ) {
        Column(
            modifier = Modifier()
                .width("100%")
                .maxWidth("400px")
                .padding(BuildingTheme.Spacing.lg)
        ) {
            // Logo/Title
            Text(
                text = BuildingStrings.APP_TITLE,
                modifier = Modifier()
                    .fontSize(BuildingTheme.FontSize.xxl)
                    .fontWeight("700")
                    .color(BuildingTheme.Colors.PRIMARY)
                    .textAlign(TextAlign.Center)
                    .margin("0", "0", BuildingTheme.Spacing.lg, "0")
            )
            
            // Card
            Column(
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.BG_CARD)
                    .borderRadius(BuildingTheme.BorderRadius.lg)
                    .padding(BuildingTheme.Spacing.xl)
                    .boxShadow("0 4px 6px ${BuildingTheme.Colors.SHADOW}")
            ) {
                Text(
                    text = title,
                    modifier = Modifier()
                        .fontSize(BuildingTheme.FontSize.xl)
                        .fontWeight("600")
                        .color(BuildingTheme.Colors.TEXT_PRIMARY)
                        .margin("0", "0", BuildingTheme.Spacing.sm, "0")
                )
                
                Text(
                    text = subtitle,
                    modifier = Modifier()
                        .fontSize(BuildingTheme.FontSize.sm)
                        .color(BuildingTheme.Colors.TEXT_SECONDARY)
                        .margin("0", "0", BuildingTheme.Spacing.lg, "0")
                )
                
                if (errorMessage != null) {
                    Alert(errorMessage, AlertType.ERROR)
                }
                
                content()
            }
        }
    }
}

@Composable
private fun FormGroup(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier()
            .fillMaxWidth()
            .margin(0.px, 0.px, BuildingTheme.Spacing.md, 0.px)
    ) {
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(BuildingTheme.FontSize.sm)
                .fontWeight("500")
                .color(BuildingTheme.Colors.TEXT_SECONDARY)
                .margin("0", "0", BuildingTheme.Spacing.xs, "0")
        )
        content()
    }
}

private fun inputModifier() = Modifier()
    .fillMaxWidth()
    .padding(BuildingTheme.Spacing.md)
    .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
    .borderRadius(BuildingTheme.BorderRadius.md)
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor(BuildingTheme.Colors.BORDER)
    .color(BuildingTheme.Colors.TEXT_PRIMARY)
    .fontSize(BuildingTheme.FontSize.base)

// ===================== Dashboard Page =====================

@Composable
fun BuildingDashboardPage(
    username: String,
    summary: DashboardSummary
) {
    BuildingPageLayout(
        title = BuildingStrings.DASHBOARD,
        username = username,
        currentPath = "/"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            // Notification Banner - show upcoming payments due within 30 days
            if (summary.upcomingPayments.isNotEmpty()) {
                NotificationBanner(summary.upcomingPayments)
            }
            
            // Overdue alert banner
            if (summary.overduePayments.isNotEmpty()) {
                OverdueAlertBanner(summary.overduePayments.size)
            }
            
            PageHeader(title = BuildingStrings.DASHBOARD)
            
            // Stats grid
            Box(
                modifier = Modifier()
                    .fillMaxWidth()
                    .display(Display.Grid)
                    .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(180.px), gridFraction())))
                    .gap(BuildingTheme.Spacing.md)
                    .margin("0", "0", BuildingTheme.Spacing.lg, "0")
            ) {
                StatCard(
                    value = summary.totalBuildings.toString(),
                    label = BuildingStrings.TOTAL_BUILDINGS,
                    color = BuildingTheme.Colors.PRIMARY
                )
                StatCard(
                    value = summary.totalUnits.toString(),
                    label = BuildingStrings.TOTAL_UNITS,
                    color = BuildingTheme.Colors.INFO
                )
                StatCard(
                    value = summary.occupiedUnits.toString(),
                    label = BuildingStrings.OCCUPIED_UNITS,
                    color = BuildingTheme.Colors.SUCCESS
                )
                StatCard(
                    value = summary.vacantUnits.toString(),
                    label = BuildingStrings.VACANT_UNITS,
                    color = BuildingTheme.Colors.WARNING
                )
                StatCard(
                    value = BuildingStrings.formatCurrency(summary.totalMonthlyIncome),
                    label = BuildingStrings.MONTHLY_INCOME,
                    color = BuildingTheme.Colors.SUCCESS
                )
            }
            
            // Overdue payments
            Card(
                title = BuildingStrings.OVERDUE_PAYMENTS,
                modifier = Modifier().fillMaxWidth().margin("0", "0", BuildingTheme.Spacing.lg, "0")
            ) {
                if (summary.overduePayments.isEmpty()) {
                    EmptyState(BuildingStrings.NO_OVERDUE_PAYMENTS)
                } else {
                    PaymentTable(summary.overduePayments)
                }
            }
            
            // Upcoming payments
            Card(
                title = BuildingStrings.UPCOMING_PAYMENTS,
                modifier = Modifier().fillMaxWidth()
            ) {
                if (summary.upcomingPayments.isEmpty()) {
                    EmptyState(BuildingStrings.NO_UPCOMING_PAYMENTS)
                } else {
                    PaymentTable(summary.upcomingPayments)
                }
            }
        }
    }
}

// ===================== Buildings List Page =====================

@Composable
fun BuildingsListPage(
    username: String,
    buildings: List<Building>,
    unitCounts: Map<String, Int>
) {
    BuildingPageLayout(
        title = BuildingStrings.BUILDINGS,
        username = username,
        currentPath = "/buildings"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = BuildingStrings.BUILDINGS)
            
            if (buildings.isEmpty()) {
                Card(modifier = Modifier().fillMaxWidth()) {
                    EmptyState(BuildingStrings.NO_BUILDINGS)
                }
            } else {
                Form(
                    action = "/buildings/bulk/review",
                    method = FormMethod.Post,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    BulkActionBar(
                        actions = listOf(
                            "edit" to BuildingStrings.BULK_EDIT,
                            "delete" to BuildingStrings.BULK_DELETE
                        )
                    )
                    Card(modifier = Modifier().fillMaxWidth()) {
                    // Table using CSS Grid
                    Column(modifier = Modifier().fillMaxWidth()) {
                        // Header row
                        Row(
                            modifier = Modifier()
                                .fillMaxWidth()
                                .display(Display.Grid)
                                .gridTemplateColumns(
                                    gridTrack(52.px),
                                    gridFraction(),
                                    gridFraction(),
                                    gridTrack(200.px),
                                )
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
                        ) {
                            GridHeaderCell(BuildingStrings.SELECT)
                            GridHeaderCell(BuildingStrings.BUILDING_NAME)
                            GridHeaderCell(BuildingStrings.TOTAL_UNITS)
                            GridHeaderCell("")
                        }
                        // Data rows
                        buildings.forEach { building ->
                            Row(
                                modifier = Modifier()
                                    .fillMaxWidth()
                                    .display(Display.Grid)
                                    .gridTemplateColumns(
                                        gridTrack(52.px),
                                        gridFraction(),
                                        gridFraction(),
                                        gridTrack(200.px),
                                    )
                                    .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
                            ) {
                                GridCell { BulkCheckbox(building.id) }
                                GridCell { Text(building.name) }
                                GridCell { Text((unitCounts[building.id] ?: 0).toString()) }
                                GridCell {
                                    Row(
                                        modifier = Modifier()
                                            .gap(BuildingTheme.Spacing.sm)
                                            .flexWrap(FlexWrap.Wrap)
                                    ) {
                                        Link(
                                            href = "/buildings/${building.id}",
                                            modifier = Modifier()
                                                .color(BuildingTheme.Colors.PRIMARY)
                                                .fontSize(BuildingTheme.FontSize.xs)
                                        ) {
                                            Text(BuildingStrings.VIEW_UNITS)
                                        }
                                        Link(
                                            href = "/buildings/${building.id}/edit",
                                            modifier = Modifier()
                                                .color(BuildingTheme.Colors.WARNING_TEXT)
                                                .fontSize(BuildingTheme.FontSize.xs)
                                        ) {
                                            Text(BuildingStrings.EDIT)
                                        }
                                        Link(
                                            href = "/buildings/${building.id}/delete",
                                            modifier = Modifier()
                                                .color(BuildingTheme.Colors.DANGER)
                                                .fontSize(BuildingTheme.FontSize.xs)
                                        ) {
                                            Text(BuildingStrings.DELETE)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

// ===================== Building Detail (Units) Page =====================

@Composable
fun BuildingUnitsPage(
    username: String,
    building: Building,
    apartments: List<ApartmentWithDetails>
) {
    BuildingPageLayout(
        title = building.name,
        username = username,
        currentPath = "/buildings/${building.id}"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            // Breadcrumb
            Row(
                modifier = Modifier()
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    .gap(BuildingTheme.Spacing.sm)
            ) {
                codes.yousef.summon.components.navigation.Link(
                    href = "/buildings",
                    modifier = Modifier().color(BuildingTheme.Colors.TEXT_SECONDARY)
                ) {
                    Text(BuildingStrings.BUILDINGS)
                }
                Text(" / ", modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED))
                Text(building.name, modifier = Modifier().color(BuildingTheme.Colors.TEXT_PRIMARY))
            }
            
            PageHeader(title = "${BuildingStrings.UNITS} - ${building.name}")
            
            if (apartments.isEmpty()) {
                Card(modifier = Modifier().fillMaxWidth()) {
                    EmptyState(BuildingStrings.NO_UNITS)
                }
            } else {
                Form(
                    action = "/buildings/${building.id}/units/bulk/review",
                    method = FormMethod.Post,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    BulkActionBar(
                        actions = listOf(
                            "edit" to BuildingStrings.BULK_EDIT,
                            "delete" to BuildingStrings.BULK_DELETE,
                            "dates" to BuildingStrings.BULK_UPDATE_DATES
                        )
                    )
                    Card(modifier = Modifier().fillMaxWidth()) {
                    // Units table using CSS Grid
                    Column(modifier = Modifier().fillMaxWidth()) {
                        // Header row
                        Row(
                            modifier = Modifier()
                                .fillMaxWidth()
                                .display(Display.Grid)
                                .gridTemplateColumns(
                                    gridTrack(52.px),
                                    gridTrack(80.px),
                                    gridFraction(),
                                    gridTrack(120.px),
                                    gridTrack(160.px),
                                    gridTrack(100.px),
                                    gridTrack(150.px),
                                )
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
                        ) {
                            GridHeaderCell(BuildingStrings.SELECT)
                            GridHeaderCell(BuildingStrings.UNIT_NUMBER)
                            GridHeaderCell(BuildingStrings.TENANT)
                            GridHeaderCell(BuildingStrings.ANNUAL_RENT)
                            GridHeaderCell(BuildingStrings.LEASE_PERIOD)
                            GridHeaderCell(BuildingStrings.STATUS)
                            GridHeaderCell("")
                        }
                        // Data rows
                        apartments.forEach { detail ->
                            Row(
                                modifier = Modifier()
                                    .fillMaxWidth()
                                    .display(Display.Grid)
                                    .gridTemplateColumns(
                                        gridTrack(52.px),
                                        gridTrack(80.px),
                                        gridFraction(),
                                        gridTrack(120.px),
                                        gridTrack(160.px),
                                        gridTrack(100.px),
                                        gridTrack(150.px),
                                    )
                                    .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
                            ) {
                                GridCell { BulkCheckbox(detail.apartment.id) }
                                GridCell { Text(detail.apartment.unitNumber) }
                                GridCell { Text(detail.tenant?.name ?: "-") }
                                GridCell { 
                                    Text(detail.currentLease?.let { 
                                        BuildingStrings.formatCurrency(it.annualRent) 
                                    } ?: "-")
                                }
                                GridCell {
                                    if (detail.currentLease != null) {
                                        Text("${detail.currentLease.startDate} - ${detail.currentLease.endDate}")
                                    } else {
                                        Text("-")
                                    }
                                }
                                GridCell {
                                    if (detail.currentLease != null) {
                                        Text(
                                            text = BuildingStrings.OCCUPIED,
                                            modifier = Modifier()
                                                .backgroundColor(BuildingTheme.Colors.SUCCESS_BG)
                                                .color(BuildingTheme.Colors.SUCCESS_TEXT)
                                                .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.sm)
                                                .borderRadius(BuildingTheme.BorderRadius.full)
                                                .fontSize(BuildingTheme.FontSize.xs)
                                        )
                                    } else {
                                        Text(
                                            text = BuildingStrings.VACANT,
                                            modifier = Modifier()
                                                .backgroundColor(BuildingTheme.Colors.WARNING_BG)
                                                .color(BuildingTheme.Colors.WARNING_TEXT)
                                                .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.sm)
                                                .borderRadius(BuildingTheme.BorderRadius.full)
                                                .fontSize(BuildingTheme.FontSize.xs)
                                        )
                                    }
                                }
                                GridCell {
                                    Row(
                                        modifier = Modifier()
                                            .gap(BuildingTheme.Spacing.sm)
                                            .flexWrap(FlexWrap.Wrap)
                                    ) {
                                        codes.yousef.summon.components.navigation.Link(
                                            href = "/apartments/${detail.apartment.id}/edit",
                                            modifier = Modifier()
                                                .color(BuildingTheme.Colors.WARNING_TEXT)
                                                .fontSize(BuildingTheme.FontSize.sm)
                                        ) {
                                            Text(BuildingStrings.EDIT)
                                        }
                                        if (detail.currentLease != null) {
                                            codes.yousef.summon.components.navigation.Link(
                                                href = "/apartments/${detail.apartment.id}/payments",
                                                modifier = Modifier()
                                                    .color(BuildingTheme.Colors.PRIMARY)
                                                    .fontSize(BuildingTheme.FontSize.sm)
                                            ) {
                                                Text(BuildingStrings.VIEW_PAYMENTS)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

// ===================== Payments List Page =====================

@Composable
fun PaymentsListPage(
    username: String,
    payments: List<PaymentWithDetails>,
    currentFilter: String?
) {
    BuildingPageLayout(
        title = BuildingStrings.PAYMENTS,
        username = username,
        currentPath = "/payments"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = BuildingStrings.PAYMENTS)
            
            // Filter buttons
            Row(
                modifier = Modifier()
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    .gap(BuildingTheme.Spacing.sm)
            ) {
                FilterButton(BuildingStrings.ALL, null, currentFilter)
                FilterButton(BuildingStrings.OVERDUE, "OVERDUE", currentFilter)
                FilterButton(BuildingStrings.PENDING, "PENDING", currentFilter)
                FilterButton(BuildingStrings.PAID, "PAID", currentFilter)
            }
            
            if (payments.isEmpty()) {
                Card(modifier = Modifier().fillMaxWidth()) {
                    EmptyState(BuildingStrings.NO_PAYMENTS)
                }
            } else {
                val redirectPath = if (currentFilter.isNullOrBlank()) {
                    "/payments"
                } else {
                    "/payments?status=$currentFilter"
                }
                Form(
                    action = "/payments/bulk/review",
                    method = FormMethod.Post,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    BulkActionBar(
                        actions = listOf(
                            "edit" to BuildingStrings.BULK_EDIT,
                            "delete" to BuildingStrings.BULK_DELETE,
                            "dates" to BuildingStrings.BULK_UPDATE_DATES
                        )
                    )
                    Card(modifier = Modifier().fillMaxWidth()) {
                        PaymentTable(payments, selectable = true, showStatusActions = true)
                    }
                }
                PaymentStatusForms(payments, redirectPath)
            }
        }
    }
}

@Composable
private fun FilterButton(label: String, filter: String?, currentFilter: String?) {
    val isActive = filter == currentFilter
    val href = if (filter == null) "/payments" else "/payments?status=$filter"
    
    codes.yousef.summon.components.navigation.Link(
        href = href,
        modifier = Modifier()
            .backgroundColor(if (isActive) BuildingTheme.Colors.PRIMARY else BuildingTheme.Colors.BG_HOVER)
            .color(if (isActive) BuildingTheme.Colors.TEXT_WHITE else BuildingTheme.Colors.TEXT_PRIMARY)
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .fontSize(BuildingTheme.FontSize.sm)
    ) {
        Text(label)
    }
}

@Composable
private fun PaymentTable(
    payments: List<PaymentWithDetails>,
    selectable: Boolean = false,
    showStatusActions: Boolean = false
) {
    val gridColumns = buildList<GridTrack> {
        if (selectable) add(gridTrack(52.px))
        add(gridMinMax(gridTrack(130.px), gridFraction()))
        add(gridTrack(80.px))
        add(gridMinMax(gridTrack(120.px), gridFraction()))
        add(gridTrack(90.px))
        add(gridTrack(110.px))
        add(gridTrack(110.px))
        add(gridTrack(130.px))
        add(gridMinMax(gridTrack(140.px), gridFraction()))
        if (showStatusActions) add(gridTrack(170.px))
    }.toTypedArray()
    // Payment table using CSS Grid
    Column(modifier = Modifier().fillMaxWidth()) {
        // Header row
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .display(Display.Grid)
                .gridTemplateColumns(*gridColumns)
                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
        ) {
            if (selectable) GridHeaderCell(BuildingStrings.SELECT)
            GridHeaderCell(BuildingStrings.BUILDING_NAME)
            GridHeaderCell(BuildingStrings.UNIT_NUMBER)
            GridHeaderCell(BuildingStrings.TENANT)
            GridHeaderCell(BuildingStrings.PAYMENT_NUMBER)
            GridHeaderCell(BuildingStrings.PAYMENT_AMOUNT)
            GridHeaderCell(BuildingStrings.DUE_DATE)
            GridHeaderCell(BuildingStrings.PAYMENT_STATUS)
            GridHeaderCell(BuildingStrings.NOTES)
            if (showStatusActions) GridHeaderCell(BuildingStrings.UPDATE_STATUS)
        }
        // Data rows
        payments.forEach { detail ->
            Row(
                modifier = Modifier()
                    .fillMaxWidth()
                    .display(Display.Grid)
                    .gridTemplateColumns(*gridColumns)
                    .border(BorderSide.Bottom, 1, BorderStyle.Solid, BuildingTheme.Colors.BORDER)
            ) {
                if (selectable) GridCell { BulkCheckbox(detail.payment.id) }
                GridCell { Text(detail.building?.name ?: "-") }
                GridCell { Text(detail.apartment?.unitNumber ?: "-") }
                GridCell { Text(detail.tenant?.name ?: "-") }
                GridCell { Text(BuildingStrings.formatPaymentNumber(detail.payment.paymentNumber)) }
                GridCell { Text(BuildingStrings.formatCurrency(detail.payment.amount)) }
                GridCell { Text(detail.payment.dueDate) }
                GridCell {
                    Column(modifier = Modifier().gap(BuildingTheme.Spacing.xs)) {
                        StatusBadge(detail.payment.status)
                        if (isUnpaidPastDue(detail.payment)) {
                            PastDueWarningBadge()
                        }
                    }
                }
                GridCell { Text(detail.payment.notes) }
                if (showStatusActions) {
                    GridCell {
                        PaymentStatusButtons(detail.payment)
                    }
                }
            }
        }
    }
}

@Composable
private fun PastDueWarningBadge() {
    Text(
        text = BuildingStrings.PAST_DUE_UNPAID,
        modifier = Modifier()
            .backgroundColor(BuildingTheme.Colors.DANGER_BG)
            .color(BuildingTheme.Colors.DANGER_TEXT)
            .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.sm)
            .borderRadius(BuildingTheme.BorderRadius.full)
            .fontSize(BuildingTheme.FontSize.xs)
            .fontWeight("500")
            .margin(BuildingTheme.Spacing.xs, "0", "0", "0")
    )
}

// ===================== Bulk Review Pages =====================

@Composable
fun BulkErrorPage(username: String, title: String, message: String, returnHref: String) {
    BuildingPageLayout(title = title, username = username, currentPath = returnHref) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("720px")) {
            PageHeader(title = title)
            Card(modifier = Modifier().fillMaxWidth()) {
                Alert(message, AlertType.ERROR)
                Link(
                    href = returnHref,
                    modifier = Modifier()
                        .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                        .color(BuildingTheme.Colors.TEXT_PRIMARY)
                        .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .textDecoration(TextDecoration.None)
                ) {
                    Text(BuildingStrings.BACK)
                }
            }
        }
    }
}

@Composable
fun BulkResultPage(
    username: String,
    title: String,
    result: BulkOperationResult,
    returnHref: String,
    message: String
) {
    BuildingPageLayout(title = title, username = username, currentPath = returnHref) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("720px")) {
            PageHeader(title = title)
            Card(modifier = Modifier().fillMaxWidth()) {
                Alert(message, AlertType.SUCCESS)
                Text("${BuildingStrings.AFFECTED_RECORDS}: ${result.applied}", modifier = Modifier().margin("0", "0", BuildingTheme.Spacing.sm, "0"))
                if (result.skipped > 0) {
                    Alert("${BuildingStrings.SKIPPED_RECORDS}: ${result.skipped}", AlertType.WARNING)
                }
                Link(
                    href = returnHref,
                    modifier = Modifier()
                        .backgroundColor(BuildingTheme.Colors.PRIMARY)
                        .color(BuildingTheme.Colors.TEXT_WHITE)
                        .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .textDecoration(TextDecoration.None)
                ) {
                    Text(BuildingStrings.BACK)
                }
            }
        }
    }
}

@Composable
fun BulkBuildingsReviewPage(
    username: String,
    action: String,
    buildings: List<Building>,
    cascadePlan: BulkCascadePlan?,
    errorMessage: String?
) {
    BuildingPageLayout(title = BuildingStrings.BULK_REVIEW, username = username, currentPath = "/buildings") {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = "${BuildingStrings.BULK_REVIEW} - ${BuildingStrings.BUILDINGS}")
            Card(modifier = Modifier().fillMaxWidth()) {
                errorMessage?.let { Alert(it, AlertType.ERROR) }
                if (action == "delete") {
                    CascadeSummary(cascadePlan)
                    BulkDeleteForm("/buildings/bulk/apply", buildings.map { it.id }, "/buildings")
                } else {
                    BuildingsEditForm(buildings)
                }
            }
        }
    }
}

@Composable
fun BulkApartmentsReviewPage(
    username: String,
    building: Building,
    action: String,
    apartments: List<ApartmentWithDetails>,
    cascadePlan: BulkCascadePlan?,
    errorMessage: String?
) {
    BuildingPageLayout(title = BuildingStrings.BULK_REVIEW, username = username, currentPath = "/buildings/${building.id}") {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = "${BuildingStrings.BULK_REVIEW} - ${building.name}")
            Card(modifier = Modifier().fillMaxWidth()) {
                errorMessage?.let { Alert(it, AlertType.ERROR) }
                when (action) {
                    "delete" -> {
                        CascadeSummary(cascadePlan)
                        BulkDeleteForm(
                            action = "/buildings/${building.id}/units/bulk/apply",
                            ids = apartments.map { it.apartment.id },
                            returnHref = "/buildings/${building.id}"
                        )
                    }
                    "dates" -> ApartmentDateForm(building.id, apartments)
                    else -> ApartmentsEditForm(building.id, apartments)
                }
            }
        }
    }
}

@Composable
fun BulkPaymentsReviewPage(
    username: String,
    action: String,
    payments: List<PaymentWithDetails>,
    cascadePlan: BulkCascadePlan?,
    errorMessage: String?
) {
    BuildingPageLayout(title = BuildingStrings.BULK_REVIEW, username = username, currentPath = "/payments") {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = "${BuildingStrings.BULK_REVIEW} - ${BuildingStrings.PAYMENTS}")
            Card(modifier = Modifier().fillMaxWidth()) {
                errorMessage?.let { Alert(it, AlertType.ERROR) }
                when (action) {
                    "delete" -> {
                        CascadeSummary(cascadePlan)
                        BulkDeleteForm("/payments/bulk/apply", payments.map { it.payment.id }, "/payments")
                    }
                    "dates" -> PaymentDateForm(payments)
                    else -> PaymentsEditForm(payments)
                }
            }
        }
    }
}

@Composable
private fun CascadeSummary(plan: BulkCascadePlan?) {
    if (plan == null) return
    Alert("راجع الأعداد قبل التنفيذ. السجلات غير الموجودة ستتجاوز تلقائياً.", AlertType.WARNING)
    Column(modifier = Modifier().margin("0", "0", BuildingTheme.Spacing.md, "0")) {
        Text("${BuildingStrings.AFFECTED_RECORDS}: ${plan.existing}")
        if (plan.buildings > 0) Text("${BuildingStrings.BUILDINGS}: ${plan.buildings}")
        if (plan.apartments > 0) Text("${BuildingStrings.UNITS}: ${plan.apartments}")
        if (plan.leases > 0) Text("العقود: ${plan.leases}")
        if (plan.payments > 0) Text("${BuildingStrings.PAYMENTS}: ${plan.payments}")
        if (plan.tenants > 0) Text("المستأجرون غير المرتبطين بعقود أخرى: ${plan.tenants}")
        if (plan.skipped > 0) Text("${BuildingStrings.SKIPPED_RECORDS}: ${plan.skipped}")
    }
}

// ===================== Import Page =====================

@Composable
fun ImportPage(
    username: String,
    successMessage: String?,
    errorMessage: String?
) {
    BuildingPageLayout(
        title = BuildingStrings.IMPORT_DATA,
        username = username,
        currentPath = "/import"
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            PageHeader(title = BuildingStrings.IMPORT_DATA)
            
            if (successMessage != null) {
                Alert(successMessage, AlertType.SUCCESS)
            }
            if (errorMessage != null) {
                Alert(errorMessage, AlertType.ERROR)
            }
            
            Card(
                title = BuildingStrings.IMPORT_TITLE,
                modifier = Modifier().fillMaxWidth().margin("0", "0", BuildingTheme.Spacing.lg, "0")
            ) {
                Text(
                    text = BuildingStrings.IMPORT_SUBTITLE,
                    modifier = Modifier()
                        .color(BuildingTheme.Colors.TEXT_SECONDARY)
                        .fontSize(BuildingTheme.FontSize.sm)
                        .margin("0", "0", BuildingTheme.Spacing.md, "0")
                )
                
                Form(
                    action = "/import",
                    method = FormMethod.Post,
                    encType = FormEncType.Multipart,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    Column(modifier = Modifier().fillMaxWidth()) {
                        // File input - using native input via LocalPlatformRenderer
                        FileInputField(name = "file", accept = ".xlsx,.xls")
                        
                        FormButton(
                            text = BuildingStrings.IMPORT_BUTTON,
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.PRIMARY)
                                .color(BuildingTheme.Colors.TEXT_WHITE)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .fontWeight("500")
                        )
                    }
                }
            }
            
            Card(
                title = BuildingStrings.CLEAR_DATA,
                modifier = Modifier().fillMaxWidth()
            ) {
                Text(
                    text = BuildingStrings.CLEAR_CONFIRM,
                    modifier = Modifier()
                        .color(BuildingTheme.Colors.TEXT_SECONDARY)
                        .fontSize(BuildingTheme.FontSize.sm)
                        .margin("0", "0", BuildingTheme.Spacing.md, "0")
                )
                
                Link(
                    href = "/clear-data/confirm",
                    modifier = Modifier()
                        .display(Display.InlineBlock)
                        .backgroundColor(BuildingTheme.Colors.DANGER)
                        .color(BuildingTheme.Colors.TEXT_WHITE)
                        .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .fontWeight("500")
                        .textDecoration(TextDecoration.None)
                ) {
                    Text(BuildingStrings.CLEAR_DATA)
                }
            }
        }
    }
}

@Composable
fun ClearDataConfirmationPage(username: String) {
    BuildingPageLayout(
        title = BuildingStrings.CLEAR_DATA,
        username = username,
        currentPath = "/import"
    ) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("720px")) {
            PageHeader(title = BuildingStrings.CLEAR_DATA)
            Card(modifier = Modifier().fillMaxWidth()) {
                Alert(BuildingStrings.CLEAR_DATA_CONFIRM_DIALOG, AlertType.WARNING)
                Row(
                    modifier = Modifier()
                        .gap(BuildingTheme.Spacing.sm)
                        .flexWrap(FlexWrap.Wrap)
                        .alignItems(AlignItems.Center)
                ) {
                    Form(action = "/clear-data", method = FormMethod.Post) {
                        FormButton(
                            text = BuildingStrings.CLEAR_DATA,
                            variant = FormButtonVariant.Danger,
                            fullWidth = false,
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.DANGER)
                                .color(BuildingTheme.Colors.TEXT_WHITE)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .fontWeight("600")
                        )
                    }
                    Link(
                        href = "/import",
                        modifier = Modifier()
                            .display(Display.InlineBlock)
                            .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                            .color(BuildingTheme.Colors.TEXT_PRIMARY)
                            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                            .borderRadius(BuildingTheme.BorderRadius.md)
                            .fontWeight("600")
                            .textDecoration(TextDecoration.None)
                    ) {
                        Text(BuildingStrings.CANCEL)
                    }
                }
            }
        }
    }
}

// ===================== Edit Building Page =====================

@Composable
fun EditBuildingPage(
    username: String,
    building: Building,
    errorMessage: String?
) {
    BuildingPageLayout(
        title = BuildingStrings.EDIT_BUILDING,
        username = username,
        currentPath = "/buildings"
    ) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("600px")) {
            // Breadcrumb
            Row(
                modifier = Modifier()
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    .gap(BuildingTheme.Spacing.sm)
            ) {
                Link(
                    href = "/buildings",
                    modifier = Modifier().color(BuildingTheme.Colors.PRIMARY).fontSize(BuildingTheme.FontSize.sm)
                ) { Text(BuildingStrings.BUILDINGS) }
                Text(text = "←", modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED))
                Text(text = building.name, modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED).fontSize(BuildingTheme.FontSize.sm))
            }
            
            PageHeader(title = BuildingStrings.EDIT_BUILDING)
            
            Card(modifier = Modifier().fillMaxWidth()) {
                if (errorMessage != null) {
                    Alert(errorMessage, AlertType.ERROR)
                }
                
                Form(
                    action = "/buildings/${building.id}/edit",
                    method = FormMethod.Post,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    FormGroup(label = BuildingStrings.BUILDING_NAME) {
                        FormTextField(
                            name = "name",
                            label = "",
                            defaultValue = building.name,
                            modifier = inputModifier()
                        )
                    }
                    
                    FormGroup(label = BuildingStrings.BUILDING_ADDRESS) {
                        FormTextField(
                            name = "address",
                            label = "",
                            defaultValue = building.address,
                            modifier = inputModifier()
                        )
                    }
                    
                    Row(
                        modifier = Modifier()
                            .gap(BuildingTheme.Spacing.md)
                            .margin(BuildingTheme.Spacing.md, "0", "0", "0")
                    ) {
                        FormButton(
                            text = BuildingStrings.SAVE,
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.PRIMARY)
                                .color(BuildingTheme.Colors.TEXT_WHITE)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .fontWeight("500")
                        )
                        Link(
                            href = "/buildings",
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .color(BuildingTheme.Colors.TEXT_PRIMARY)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .textDecoration(TextDecoration.None)
                        ) {
                            Text(BuildingStrings.CANCEL)
                        }
                    }
                }
            }
        }
    }
}

// ===================== Delete Building Page =====================

@Composable
fun DeleteBuildingPage(
    username: String,
    building: Building,
    unitCount: Int
) {
    BuildingPageLayout(
        title = BuildingStrings.DELETE_BUILDING,
        username = username,
        currentPath = "/buildings"
    ) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("600px")) {
            // Breadcrumb
            Row(
                modifier = Modifier()
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    .gap(BuildingTheme.Spacing.sm)
            ) {
                Link(
                    href = "/buildings",
                    modifier = Modifier().color(BuildingTheme.Colors.PRIMARY).fontSize(BuildingTheme.FontSize.sm)
                ) { Text(BuildingStrings.BUILDINGS) }
                Text(text = "←", modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED))
                Text(text = building.name, modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED).fontSize(BuildingTheme.FontSize.sm))
            }
            
            PageHeader(title = BuildingStrings.DELETE_BUILDING)
            
            Card(modifier = Modifier().fillMaxWidth()) {
                // Warning banner
                Box(
                    modifier = Modifier()
                        .fillMaxWidth()
                        .backgroundColor(BuildingTheme.Colors.DANGER_BG)
                        .borderRadius(BuildingTheme.BorderRadius.md)
                        .padding(BuildingTheme.Spacing.md)
                        .margin("0", "0", BuildingTheme.Spacing.lg, "0")
                ) {
                    Column {
                        Text(
                            text = "⚠️ ${BuildingStrings.CONFIRM_DELETE_BUILDING}",
                            modifier = Modifier()
                                .color(BuildingTheme.Colors.DANGER_TEXT)
                                .fontWeight("600")
                                .fontSize(BuildingTheme.FontSize.sm)
                        )
                        if (unitCount > 0) {
                            Text(
                                text = "سيتم حذف $unitCount شقة مع جميع العقود والدفعات المرتبطة بها.",
                                modifier = Modifier()
                                    .color(BuildingTheme.Colors.DANGER_TEXT)
                                    .fontSize(BuildingTheme.FontSize.xs)
                                    .margin(BuildingTheme.Spacing.xs, "0", "0", "0")
                            )
                        }
                    }
                }
                
                // Building info
                Column(
                    modifier = Modifier()
                        .fillMaxWidth()
                        .margin("0", "0", BuildingTheme.Spacing.lg, "0")
                ) {
                    Text(
                        text = "${BuildingStrings.BUILDING_NAME}: ${building.name}",
                        modifier = Modifier().fontSize(BuildingTheme.FontSize.sm).margin("0", "0", BuildingTheme.Spacing.xs, "0")
                    )
                    Text(
                        text = "${BuildingStrings.TOTAL_UNITS}: $unitCount",
                        modifier = Modifier().fontSize(BuildingTheme.FontSize.sm).color(BuildingTheme.Colors.TEXT_SECONDARY)
                    )
                }
                
                // Action buttons
                Row(
                    modifier = Modifier()
                        .gap(BuildingTheme.Spacing.md)
                ) {
                    Form(
                        action = "/buildings/${building.id}/delete",
                        method = FormMethod.Post,
                        modifier = Modifier()
                    ) {
                        FormButton(
                            text = BuildingStrings.DELETE,
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.DANGER)
                                .color(BuildingTheme.Colors.TEXT_WHITE)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .fontWeight("500")
                        )
                    }
                    Link(
                        href = "/buildings",
                        modifier = Modifier()
                            .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                            .color(BuildingTheme.Colors.TEXT_PRIMARY)
                            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                            .borderRadius(BuildingTheme.BorderRadius.md)
                            .textDecoration(TextDecoration.None)
                    ) {
                        Text(BuildingStrings.CANCEL)
                    }
                }
            }
        }
    }
}

// ===================== Edit Apartment Page =====================

@Composable
fun EditApartmentPage(
    username: String,
    building: Building,
    apartment: Apartment,
    tenant: Tenant?,
    lease: Lease?,
    errorMessage: String?
) {
    BuildingPageLayout(
        title = BuildingStrings.EDIT_APARTMENT,
        username = username,
        currentPath = "/buildings/${building.id}"
    ) {
        Column(modifier = Modifier().fillMaxWidth().maxWidth("700px")) {
            // Breadcrumb
            Row(
                modifier = Modifier()
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    .gap(BuildingTheme.Spacing.sm)
            ) {
                Link(
                    href = "/buildings",
                    modifier = Modifier().color(BuildingTheme.Colors.PRIMARY).fontSize(BuildingTheme.FontSize.sm)
                ) { Text(BuildingStrings.BUILDINGS) }
                Text(text = "←", modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED))
                Link(
                    href = "/buildings/${building.id}",
                    modifier = Modifier().color(BuildingTheme.Colors.PRIMARY).fontSize(BuildingTheme.FontSize.sm)
                ) { Text(building.name) }
                Text(text = "←", modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED))
                Text(
                    text = apartment.unitNumber,
                    modifier = Modifier().color(BuildingTheme.Colors.TEXT_MUTED).fontSize(BuildingTheme.FontSize.sm)
                )
            }

            PageHeader(title = "${BuildingStrings.EDIT_APARTMENT} - ${apartment.unitNumber}")

            Card(modifier = Modifier().fillMaxWidth()) {
                if (errorMessage != null) {
                    Alert(errorMessage, AlertType.ERROR)
                }

                Form(
                    action = "/apartments/${apartment.id}/edit",
                    method = FormMethod.Post,
                    modifier = Modifier().fillMaxWidth()
                ) {
                    // Section: Apartment Info
                    Text(
                        text = "معلومات الشقة",
                        modifier = Modifier()
                            .fontSize(BuildingTheme.FontSize.lg)
                            .fontWeight("600")
                            .color(BuildingTheme.Colors.PRIMARY)
                            .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    )

                    Row(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .display(Display.Grid)
                            .gridTemplateColumns(gridFraction(), gridFraction())
                            .gap(BuildingTheme.Spacing.md)
                            .margin("0", "0", BuildingTheme.Spacing.lg, "0")
                    ) {
                        Column {
                            FormGroup(label = BuildingStrings.UNIT_NUMBER) {
                                FormTextField(
                                    name = "unitNumber",
                                    label = "",
                                    defaultValue = apartment.unitNumber,
                                    modifier = inputModifier()
                                )
                            }
                        }
                        Column {
                            FormGroup(label = BuildingStrings.FLOOR) {
                                FormTextField(
                                    name = "floor",
                                    label = "",
                                    defaultValue = apartment.floor?.toString() ?: "",
                                    modifier = inputModifier()
                                )
                            }
                        }
                    }

                    FormGroup(label = BuildingStrings.APARTMENT_NOTES) {
                        FormTextField(
                            name = "apartmentNotes",
                            label = "",
                            defaultValue = apartment.notes,
                            modifier = inputModifier()
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .height("1px")
                            .backgroundColor(BuildingTheme.Colors.BORDER)
                            .margin(BuildingTheme.Spacing.lg, "0")
                    ) {}

                    // Section: Tenant Info
                    Text(
                        text = "معلومات المستأجر",
                        modifier = Modifier()
                            .fontSize(BuildingTheme.FontSize.lg)
                            .fontWeight("600")
                            .color(BuildingTheme.Colors.PRIMARY)
                            .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    )

                    Row(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .display(Display.Grid)
                            .gridTemplateColumns(gridFraction(), gridFraction())
                            .gap(BuildingTheme.Spacing.md)
                    ) {
                        Column {
                            FormGroup(label = BuildingStrings.TENANT_NAME) {
                                FormTextField(
                                    name = "tenantName",
                                    label = "",
                                    defaultValue = tenant?.name ?: "",
                                    modifier = inputModifier()
                                )
                            }
                        }
                        Column {
                            FormGroup(label = BuildingStrings.TENANT_PHONE) {
                                FormTextField(
                                    name = "tenantPhone",
                                    label = "",
                                    defaultValue = tenant?.phone ?: "",
                                    modifier = inputModifier()
                                )
                            }
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .height("1px")
                            .backgroundColor(BuildingTheme.Colors.BORDER)
                            .margin(BuildingTheme.Spacing.lg, "0")
                    ) {}

                    // Section: Lease Info
                    Text(
                        text = "معلومات العقد",
                        modifier = Modifier()
                            .fontSize(BuildingTheme.FontSize.lg)
                            .fontWeight("600")
                            .color(BuildingTheme.Colors.PRIMARY)
                            .margin("0", "0", BuildingTheme.Spacing.md, "0")
                    )

                    FormGroup(label = BuildingStrings.ANNUAL_RENT) {
                        FormTextField(
                            name = "annualRent",
                            label = "",
                            defaultValue = lease?.annualRent?.toString() ?: "",
                            modifier = inputModifier()
                        )
                    }

                    Row(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .display(Display.Grid)
                            .gridTemplateColumns(gridFraction(), gridFraction())
                            .gap(BuildingTheme.Spacing.md)
                    ) {
                        Column {
                            FormGroup(label = BuildingStrings.START_DATE) {
                                FormTextField(
                                    name = "startDate",
                                    label = "",
                                    defaultValue = lease?.startDate ?: "",
                                    modifier = inputModifier()
                                )
                            }
                        }
                        Column {
                            FormGroup(label = BuildingStrings.END_DATE) {
                                FormTextField(
                                    name = "endDate",
                                    label = "",
                                    defaultValue = lease?.endDate ?: "",
                                    modifier = inputModifier()
                                )
                            }
                        }
                    }

                    FormGroup(label = BuildingStrings.NOTES) {
                        FormTextField(
                            name = "leaseNotes",
                            label = "",
                            defaultValue = lease?.notes ?: "",
                            modifier = inputModifier()
                        )
                    }

                    Row(
                        modifier = Modifier()
                            .gap(BuildingTheme.Spacing.md)
                            .margin(BuildingTheme.Spacing.lg, "0", "0", "0")
                    ) {
                        FormButton(
                            text = BuildingStrings.SAVE,
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.PRIMARY)
                                .color(BuildingTheme.Colors.TEXT_WHITE)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .fontWeight("500")
                        )
                        Link(
                            href = "/buildings/${building.id}",
                            modifier = Modifier()
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .color(BuildingTheme.Colors.TEXT_PRIMARY)
                                .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                                .borderRadius(BuildingTheme.BorderRadius.md)
                                .textDecoration(TextDecoration.None)
                        ) {
                            Text(BuildingStrings.CANCEL)
                        }
                    }
                }
            }
        }
    }
}

// ===================== Helper Components =====================

@Composable
private fun BulkActionBar(actions: List<Pair<String, String>>) {
    Row(
        modifier = Modifier()
            .gap(BuildingTheme.Spacing.sm)
            .alignItems(AlignItems.Center)
            .flexWrap(FlexWrap.Wrap)
            .margin("0", "0", BuildingTheme.Spacing.md, "0")
    ) {
        Text(
            text = BuildingStrings.BULK_ACTIONS,
            modifier = Modifier()
                .fontWeight(600)
                .color(BuildingTheme.Colors.TEXT_SECONDARY)
        )
        actions.forEach { (value, label) ->
            val isDelete = value == "delete"
            FormButton(
                text = label,
                variant = if (isDelete) FormButtonVariant.Danger else FormButtonVariant.Primary,
                fullWidth = false,
                name = "bulkAction",
                value = value,
                modifier = Modifier()
                    .borderStyle(BorderStyle.None)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .padding(BuildingTheme.Spacing.sm, 14.px)
                    .backgroundColor(if (isDelete) BuildingTheme.Colors.DANGER else BuildingTheme.Colors.PRIMARY)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .fontWeight(600)
                    .cursor(Cursor.Pointer)
            )
        }
    }
}

@Composable
private fun BulkCheckbox(id: String) {
    FormCheckbox(
        name = "select_$id",
        label = "",
        value = "1",
        modifier = Modifier().margin(0.px),
        checkboxModifier = Modifier()
            .width(18.px)
            .height(18.px)
            .margin(0.px)
            .cursor(Cursor.Pointer)
            .ariaAttribute("label", BuildingStrings.SELECT)
    )
}

@Composable
private fun BulkDeleteForm(action: String, ids: List<String>, returnHref: String) {
    Form(
        action = action,
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("delete", ids)
    ) {
        ApplyCancelButtons(returnHref = returnHref, destructive = true)
    }
}

@Composable
private fun BuildingsEditForm(buildings: List<Building>) {
    Form(
        action = "/buildings/bulk/apply",
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("edit", buildings.map { it.id })
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns(gridFraction(), gridFraction())
                .gap(14.px)
        ) {
            buildings.forEach { building ->
                RecordCard("عمارة: ${building.name}") {
                    BulkTextField("name_${building.id}", BuildingStrings.BUILDING_NAME, building.name)
                    BulkTextField("address_${building.id}", BuildingStrings.BUILDING_ADDRESS, building.address)
                }
            }
        }
        ApplyCancelButtons("/buildings")
    }
}

@Composable
private fun ApartmentsEditForm(buildingId: String, apartments: List<ApartmentWithDetails>) {
    Form(
        action = "/buildings/$buildingId/units/bulk/apply",
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("edit", apartments.map { it.apartment.id })
    ) {
        Column(modifier = responsiveRecordGridModifier()) {
            apartments.forEach { detail ->
                val id = detail.apartment.id
                RecordCard("شقة: ${detail.apartment.unitNumber}") {
                    BulkTextField("unitNumber_$id", BuildingStrings.UNIT_NUMBER, detail.apartment.unitNumber)
                    BulkTextField(
                        name = "floor_$id",
                        label = BuildingStrings.FLOOR,
                        value = detail.apartment.floor?.toString().orEmpty(),
                        type = FormTextFieldType.Number
                    )
                    BulkTextField("apartmentNotes_$id", BuildingStrings.APARTMENT_NOTES, detail.apartment.notes)
                    detail.tenant?.let { tenant ->
                        BulkTextField("tenantName_$id", BuildingStrings.TENANT_NAME, tenant.name)
                        BulkTextField("tenantPhone_$id", BuildingStrings.TENANT_PHONE, tenant.phone)
                        BulkTextField("tenantEmail_$id", "البريد الإلكتروني", tenant.email)
                        BulkTextField("tenantNationalId_$id", "رقم الهوية", tenant.nationalId)
                        BulkTextField("tenantNotes_$id", BuildingStrings.NOTES, tenant.notes)
                    }
                    detail.currentLease?.let { lease ->
                        BulkTextField(
                            name = "annualRent_$id",
                            label = BuildingStrings.ANNUAL_RENT,
                            value = lease.annualRent.toString(),
                            type = FormTextFieldType.Number,
                            step = 0.01
                        )
                        BulkTextField("startDate_$id", BuildingStrings.START_DATE, lease.startDate, FormTextFieldType.Date)
                        BulkTextField("endDate_$id", BuildingStrings.END_DATE, lease.endDate, FormTextFieldType.Date)
                        BulkTextField("leaseNotes_$id", BuildingStrings.NOTES, lease.notes)
                    }
                }
            }
        }
        ApplyCancelButtons("/buildings/$buildingId")
    }
}

@Composable
private fun PaymentsEditForm(payments: List<PaymentWithDetails>) {
    Form(
        action = "/payments/bulk/apply",
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("edit", payments.map { it.payment.id })
    ) {
        Column(modifier = responsiveRecordGridModifier()) {
            payments.forEach { detail ->
                val payment = detail.payment
                val title = "${detail.building?.name ?: "-"} / ${detail.apartment?.unitNumber ?: "-"} / ${BuildingStrings.formatPaymentNumber(payment.paymentNumber)}"
                RecordCard(title) {
                    BulkTextField(
                        "paymentNumber_${payment.id}",
                        BuildingStrings.PAYMENT_NUMBER,
                        payment.paymentNumber.toString(),
                        FormTextFieldType.Number
                    )
                    BulkTextField(
                        name = "amount_${payment.id}",
                        label = BuildingStrings.PAYMENT_AMOUNT,
                        value = payment.amount.toString(),
                        type = FormTextFieldType.Number,
                        step = 0.01
                    )
                    BulkTextField(
                        "periodStart_${payment.id}",
                        "${BuildingStrings.PERIOD} - ${BuildingStrings.FROM}",
                        payment.periodStart,
                        FormTextFieldType.Date
                    )
                    BulkTextField(
                        "periodEnd_${payment.id}",
                        "${BuildingStrings.PERIOD} - ${BuildingStrings.TO}",
                        payment.periodEnd,
                        FormTextFieldType.Date
                    )
                    BulkTextField("dueDate_${payment.id}", BuildingStrings.DUE_DATE, payment.dueDate, FormTextFieldType.Date)
                    BulkTextField("paidDate_${payment.id}", BuildingStrings.PAID_DATE, payment.paidDate.orEmpty(), FormTextFieldType.Date)
                    StatusSelect("status_${payment.id}", payment.status)
                    BulkTextField("notes_${payment.id}", BuildingStrings.NOTES, payment.notes)
                }
            }
        }
        ApplyCancelButtons("/payments")
    }
}

@Composable
private fun PaymentDateForm(payments: List<PaymentWithDetails>) {
    Form(
        action = "/payments/bulk/apply",
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("dates", payments.map { it.payment.id })
    ) {
        DateTool(
            fields = listOf(
                "periodStart" to "${BuildingStrings.PERIOD} - ${BuildingStrings.FROM}",
                "periodEnd" to "${BuildingStrings.PERIOD} - ${BuildingStrings.TO}",
                "dueDate" to BuildingStrings.DUE_DATE,
                "paidDate" to BuildingStrings.PAID_DATE
            ),
            returnHref = "/payments"
        )
    }
}

@Composable
private fun PaymentStatusForms(payments: List<PaymentWithDetails>, redirectPath: String) {
    val statuses = listOf(PaymentStatus.PAID, PaymentStatus.OVERDUE, PaymentStatus.PENDING)
    payments.forEach { detail ->
        statuses.forEach { status ->
            Form(
                action = "/payments/${detail.payment.id}/status",
                method = FormMethod.Post,
                hiddenFields = listOf(
                    FormHiddenField("status", status.name),
                    FormHiddenField("redirect", redirectPath)
                ),
                modifier = Modifier()
                    .id(paymentStatusFormId(detail.payment, status))
                    .display(Display.None)
            ) {}
        }
    }
}

@Composable
private fun PaymentStatusButtons(payment: Payment) {
    val statuses = listOf(PaymentStatus.PAID, PaymentStatus.OVERDUE, PaymentStatus.PENDING)
    Row(modifier = Modifier().gap(6.px).flexWrap(FlexWrap.Wrap)) {
        statuses.forEach { status ->
            val isCurrent = status == payment.status
            var buttonModifier = Modifier()
                .borderStyle(BorderStyle.None)
                .borderRadius(BuildingTheme.BorderRadius.md)
                .padding(7.px, 9.px)
                .backgroundColor(statusActionBackground(status))
                .color(statusActionColor(status))
                .fontSize(BuildingTheme.FontSize.xs)
                .fontWeight(700)
                .cursor(Cursor.Pointer)
            if (isCurrent) {
                buttonModifier = buttonModifier
                    .opacity(0.72f)
                    .boxShadow(
                        horizontalOffset = 0,
                        verticalOffset = 0,
                        blurRadius = 0.px,
                        spreadRadius = 2.px,
                        color = Color.rgba(0, 0, 0, 0.08f),
                        inset = true
                    )
            }
            FormButton(
                text = BuildingStrings.formatStatus(status),
                fullWidth = false,
                ariaLabel = "${BuildingStrings.UPDATE_STATUS}: ${BuildingStrings.formatStatus(status)}",
                formId = paymentStatusFormId(payment, status),
                modifier = buttonModifier
            )
        }
    }
}

private fun paymentStatusFormId(payment: Payment, status: PaymentStatus): String =
    "payment-status-${payment.id}-${status.name}"

private fun statusActionBackground(status: PaymentStatus): String = when (status) {
    PaymentStatus.PAID -> BuildingTheme.Colors.SUCCESS
    PaymentStatus.PENDING -> BuildingTheme.Colors.WARNING
    PaymentStatus.OVERDUE -> BuildingTheme.Colors.DANGER
}

private fun statusActionColor(status: PaymentStatus): String = when (status) {
    PaymentStatus.PENDING -> BuildingTheme.Colors.TEXT_PRIMARY
    PaymentStatus.PAID,
    PaymentStatus.OVERDUE -> BuildingTheme.Colors.TEXT_WHITE
}

@Composable
private fun ApartmentDateForm(buildingId: String, apartments: List<ApartmentWithDetails>) {
    Form(
        action = "/buildings/$buildingId/units/bulk/apply",
        method = FormMethod.Post,
        hiddenFields = bulkHiddenFields("dates", apartments.map { it.apartment.id })
    ) {
        DateTool(
            fields = listOf(
                "startDate" to BuildingStrings.START_DATE,
                "endDate" to BuildingStrings.END_DATE
            ),
            returnHref = "/buildings/$buildingId"
        )
    }
}

@Composable
private fun DateTool(fields: List<Pair<String, String>>, returnHref: String) {
    Column(modifier = Modifier().gap(BuildingTheme.Spacing.md).maxWidth(760.px)) {
        Column(modifier = Modifier().gap(BuildingTheme.Spacing.sm)) {
            Text(BuildingStrings.DATE_FIELDS, modifier = Modifier().fontWeight(700))
            Row(
                modifier = Modifier()
                    .gap(12.px)
                    .flexWrap(FlexWrap.Wrap)
                    .alignItems(AlignItems.Center)
            ) {
                fields.forEach { (value, label) ->
                    FormCheckbox(
                        name = "field_$value",
                        label = label,
                        value = "1",
                        modifier = Modifier().margin(0.px)
                    )
                }
            }
        }
        FormRadioGroup(
            name = "dateMode",
            label = BuildingStrings.DATE_MODE,
            options = listOf(
                FormRadioOption("set", BuildingStrings.SET_DATE),
                FormRadioOption("shift", BuildingStrings.SHIFT_DATE)
            ),
            selectedValue = "set",
            modifier = Modifier().margin(0.px),
            optionModifier = Modifier().margin(0.px)
        )
        BulkTextField("setDate", BuildingStrings.SET_DATE, "", FormTextFieldType.Date)
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns(gridFraction(), gridFraction())
                .gap(12.px)
        ) {
            BulkTextField("shiftAmount", BuildingStrings.SHIFT_AMOUNT, "0", FormTextFieldType.Number)
            FormSelect(
                name = "shiftUnit",
                label = BuildingStrings.SHIFT_UNIT,
                options = listOf(
                    FormSelectOption("days", BuildingStrings.DAYS_UNIT),
                    FormSelectOption("months", BuildingStrings.MONTHS_UNIT)
                ),
                selectedValue = "days",
                modifier = Modifier().margin(0.px),
                fieldModifier = bulkInputModifier(BuildingTheme.Colors.BG_PRIMARY)
            )
        }
    }
    ApplyCancelButtons(returnHref)
}

private fun bulkHiddenFields(action: String, ids: List<String>): List<FormHiddenField> =
    listOf(FormHiddenField("bulkAction", action)) + ids.distinct().mapIndexed { index, id ->
        FormHiddenField("record_$index", id)
    }

private fun responsiveRecordGridModifier(): Modifier = Modifier()
    .display(Display.Grid)
    .gridTemplateColumns(gridAutoFit(gridMinMax(gridTrack(320.px), gridFraction())))
    .gap(14.px)

@Composable
private fun RecordCard(title: String, content: @Composable () -> Unit) {
    Section(
        modifier = Modifier()
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(BuildingTheme.Colors.BORDER)
            .borderRadius(12.px)
            .padding(14.px)
            .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
    ) {
        H3(
            modifier = Modifier()
                .margin(0.px, 0.px, 12.px, 0.px)
                .color(BuildingTheme.Colors.TEXT_PRIMARY)
                .fontSize(16.px)
        ) {
            Text(title)
        }
        Column(modifier = Modifier().gap(10.px)) {
            content()
        }
    }
}

@Composable
private fun BulkTextField(
    name: String,
    label: String,
    value: String,
    type: FormTextFieldType = FormTextFieldType.Text,
    step: Number? = null
) {
    FormTextField(
        name = name,
        label = label,
        defaultValue = value,
        type = type,
        step = step,
        modifier = Modifier().margin(0.px),
        fieldModifier = bulkInputModifier(BuildingTheme.Colors.BG_CARD)
    )
}

@Composable
private fun StatusSelect(name: String, current: PaymentStatus) {
    FormSelect(
        name = name,
        label = BuildingStrings.PAYMENT_STATUS,
        options = PaymentStatus.entries.map { status ->
            FormSelectOption(status.name, BuildingStrings.formatStatus(status))
        },
        selectedValue = current.name,
        modifier = Modifier().margin(0.px),
        fieldModifier = bulkInputModifier(BuildingTheme.Colors.BG_CARD)
    )
}

private fun bulkInputModifier(backgroundColor: String): Modifier = Modifier()
    .padding(10.px)
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor(BuildingTheme.Colors.BORDER)
    .borderRadius(BuildingTheme.BorderRadius.md)
    .backgroundColor(backgroundColor)
    .color(BuildingTheme.Colors.TEXT_PRIMARY)

@Composable
private fun ApplyCancelButtons(returnHref: String, destructive: Boolean = false) {
    Row(
        modifier = Modifier()
            .gap(10.px)
            .flexWrap(FlexWrap.Wrap)
            .alignItems(AlignItems.Center)
            .marginTop(BuildingTheme.Spacing.md)
    ) {
        FormButton(
            text = if (destructive) BuildingStrings.BULK_DELETE else BuildingStrings.BULK_APPLY,
            variant = if (destructive) FormButtonVariant.Danger else FormButtonVariant.Primary,
            fullWidth = false,
            modifier = Modifier()
                .borderStyle(BorderStyle.None)
                .borderRadius(BuildingTheme.BorderRadius.md)
                .padding(10.px, 18.px)
                .backgroundColor(if (destructive) BuildingTheme.Colors.DANGER else BuildingTheme.Colors.PRIMARY)
                .color(BuildingTheme.Colors.TEXT_WHITE)
                .fontWeight(700)
                .cursor(Cursor.Pointer)
        )
        Link(
            href = returnHref,
            modifier = Modifier()
                .display(Display.InlineBlock)
                .borderRadius(BuildingTheme.BorderRadius.md)
                .padding(10.px, 18.px)
                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                .color(BuildingTheme.Colors.TEXT_PRIMARY)
                .textDecoration(TextDecoration.None)
                .fontWeight(600)
        ) {
            Text(BuildingStrings.CANCEL)
        }
    }
}

@Composable
private fun GridHeaderCell(text: String) {
    Box(
        modifier = Modifier()
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .fontWeight("600")
            .fontSize(BuildingTheme.FontSize.sm)
            .color(BuildingTheme.Colors.TEXT_SECONDARY)
            .textAlign(TextAlign.Right)
    ) {
        Text(text)
    }
}

@Composable
private fun GridCell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier()
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .textAlign(TextAlign.Right)
    ) {
        content()
    }
}

@Composable
private fun FileInputField(name: String, accept: String) {
    val renderer = codes.yousef.summon.runtime.LocalPlatformRenderer.current
    renderer.renderNativeInput(
        type = "file",
        modifier = Modifier()
            .fillMaxWidth()
            .margin("0", "0", BuildingTheme.Spacing.md, "0")
            .attribute("name", name)
            .attribute("accept", accept),
        value = ""
    )
}

// ===================== Notification Components =====================

@Composable
private fun NotificationBanner(upcomingPayments: List<PaymentWithDetails>) {
    Column(
        modifier = Modifier()
            .fillMaxWidth()
            .margin("0", "0", BuildingTheme.Spacing.lg, "0")
    ) {
        // Group payments by urgency
        val today = java.time.LocalDate.now()
        val urgentPayments = upcomingPayments.filter { payment ->
            try {
                val dueDate = java.time.LocalDate.parse(payment.payment.dueDate)
                val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
                daysUntilDue <= 7
            } catch (_: Exception) { false }
        }
        val upcomingInMonth = upcomingPayments.filter { payment ->
            try {
                val dueDate = java.time.LocalDate.parse(payment.payment.dueDate)
                val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
                daysUntilDue > 7
            } catch (_: Exception) { false }
        }
        
        // Show urgent notifications (due within 7 days) - Warning color
        urgentPayments.forEach { payment ->
            val daysUntilDue = try {
                val dueDate = java.time.LocalDate.parse(payment.payment.dueDate)
                java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
            } catch (_: Exception) { 30L }
            
            NotificationCard(
                payment = payment,
                daysUntilDue = daysUntilDue,
                isUrgent = true
            )
        }
        
        // Show upcoming notifications (due within 30 days) - Info color
        if (upcomingInMonth.isNotEmpty()) {
            // Summary card for less urgent payments
            Box(
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.INFO_BG)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .padding(BuildingTheme.Spacing.md)
                    .margin("0", "0", BuildingTheme.Spacing.sm, "0")
                    .border(BorderSide.Right, 4, BorderStyle.Solid, BuildingTheme.Colors.INFO)
            ) {
                Row(
                    modifier = Modifier()
                        .fillMaxWidth()
                        .alignItems(AlignItems.Center)
                        .gap(BuildingTheme.Spacing.md)
                ) {
                    // Bell icon
                    Text(
                        text = "🔔",
                        modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
                    )
                    Column(modifier = Modifier().flex(grow = 1, shrink = 1, basis = "0%")) {
                        Text(
                            text = "${BuildingStrings.NOTIFICATIONS}: ${upcomingInMonth.size} ${BuildingStrings.PAYMENT_DUE_SOON}",
                            modifier = Modifier()
                                .fontWeight("600")
                                .color(BuildingTheme.Colors.INFO_TEXT)
                                .fontSize(BuildingTheme.FontSize.sm)
                        )
                        Text(
                            text = "دفعات مستحقة خلال الشهر القادم",
                            modifier = Modifier()
                                .fontSize(BuildingTheme.FontSize.xs)
                                .color(BuildingTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    payment: PaymentWithDetails,
    daysUntilDue: Long,
    isUrgent: Boolean
) {
    val bgColor = if (isUrgent) BuildingTheme.Colors.WARNING_BG else BuildingTheme.Colors.INFO_BG
    val borderColor = if (isUrgent) BuildingTheme.Colors.WARNING else BuildingTheme.Colors.INFO
    val textColor = if (isUrgent) BuildingTheme.Colors.WARNING_TEXT else BuildingTheme.Colors.INFO_TEXT
    val icon = if (isUrgent) "⚠️" else "🔔"
    
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .backgroundColor(bgColor)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .padding(BuildingTheme.Spacing.md)
            .margin("0", "0", BuildingTheme.Spacing.sm, "0")
            .border(BorderSide.Right, 4, BorderStyle.Solid, borderColor)
    ) {
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .alignItems(AlignItems.Center)
                .gap(BuildingTheme.Spacing.md)
        ) {
            // Icon
            Text(
                text = icon,
                modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
            )
            
            // Content
            Column(modifier = Modifier().flex(grow = 1, shrink = 1, basis = "0%")) {
                Text(
                    text = "${payment.building?.name ?: ""} - ${payment.apartment?.unitNumber ?: ""}",
                    modifier = Modifier()
                        .fontWeight("600")
                        .color(textColor)
                        .fontSize(BuildingTheme.FontSize.sm)
                )
                Row(
                    modifier = Modifier()
                        .gap(BuildingTheme.Spacing.md)
                        .flexWrap(FlexWrap.Wrap)
                ) {
                    Text(
                        text = "${BuildingStrings.PAYMENT_AMOUNT}: ${BuildingStrings.formatCurrency(payment.payment.amount)}",
                        modifier = Modifier()
                            .fontSize(BuildingTheme.FontSize.xs)
                            .color(BuildingTheme.Colors.TEXT_SECONDARY)
                    )
                    Text(
                        text = "${BuildingStrings.DUE_DATE}: ${payment.payment.dueDate}",
                        modifier = Modifier()
                            .fontSize(BuildingTheme.FontSize.xs)
                            .color(BuildingTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
            
            // Days remaining badge
            Box(
                modifier = Modifier()
                    .backgroundColor(borderColor)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.sm)
                    .borderRadius(BuildingTheme.BorderRadius.full)
                    .fontSize(BuildingTheme.FontSize.xs)
                    .fontWeight("600")
            ) {
                Text(BuildingStrings.formatDaysRemaining(daysUntilDue))
            }
        }
    }
}

@Composable
private fun OverdueAlertBanner(overdueCount: Int) {
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .backgroundColor(BuildingTheme.Colors.DANGER_BG)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .padding(BuildingTheme.Spacing.md)
            .margin("0", "0", BuildingTheme.Spacing.lg, "0")
            .border(BorderSide.Right, 4, BorderStyle.Solid, BuildingTheme.Colors.DANGER)
    ) {
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .alignItems(AlignItems.Center)
                .gap(BuildingTheme.Spacing.md)
        ) {
            Text(
                text = "🚨",
                modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
            )
            Column(modifier = Modifier().flex(grow = 1, shrink = 1, basis = "0%")) {
                Text(
                    text = "تنبيه: $overdueCount دفعات متأخرة",
                    modifier = Modifier()
                        .fontWeight("600")
                        .color(BuildingTheme.Colors.DANGER_TEXT)
                        .fontSize(BuildingTheme.FontSize.sm)
                )
                Text(
                    text = "يرجى مراجعة قائمة الدفعات المتأخرة أدناه",
                    modifier = Modifier()
                        .fontSize(BuildingTheme.FontSize.xs)
                        .color(BuildingTheme.Colors.TEXT_SECONDARY)
                )
            }
            Link(
                href = "/payments?status=OVERDUE",
                modifier = Modifier()
                    .backgroundColor(BuildingTheme.Colors.DANGER)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.md)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .fontSize(BuildingTheme.FontSize.xs)
                    .fontWeight("600")
                    .textDecoration(TextDecoration.None)
            ) {
                Text("عرض")
            }
        }
    }
}
