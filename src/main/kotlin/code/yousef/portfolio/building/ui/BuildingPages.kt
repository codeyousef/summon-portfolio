package code.yousef.portfolio.building.ui

import code.yousef.portfolio.building.model.*
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.Link
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
                    .style("text-align", "center")
                    .style("text-decoration", "none")
                    .style("display", "block")
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
                        .style("border", "1px solid #c3e6cb")
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
                                .style("word-break", "break-all")
                                .style("font-family", "monospace")
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
                    .style("overflow", "hidden")
            ) {
                if (users.isEmpty()) {
                    Box(
                        modifier = Modifier()
                            .fillMaxWidth()
                            .padding(BuildingTheme.Spacing.xl)
                            .style("text-align", "center")
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
                                    .style("align-items", "center")
                                    .style("justify-content", "space-between")
                                    .let { 
                                        if (index < users.size - 1) 
                                            it.style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
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
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .minHeight("100vh")
            .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
            .style("display", "flex")
            .style("align-items", "center")
            .style("justify-content", "center")
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
                    .style("text-align", "center")
                    .margin("0", "0", BuildingTheme.Spacing.lg, "0")
            )
            
            // Card
            Column(
                modifier = Modifier()
                    .fillMaxWidth()
                    .backgroundColor(BuildingTheme.Colors.BG_CARD)
                    .borderRadius(BuildingTheme.BorderRadius.lg)
                    .padding(BuildingTheme.Spacing.xl)
                    .style("box-shadow", "0 4px 6px ${BuildingTheme.Colors.SHADOW}")
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
            .margin("0", "0", BuildingTheme.Spacing.md, "0")
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
    .style("border", "1px solid ${BuildingTheme.Colors.BORDER}")
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
                    .style("display", "grid")
                    .style("grid-template-columns", "repeat(auto-fit, minmax(180px, 1fr))")
                    .style("gap", BuildingTheme.Spacing.md)
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
            
            Card(modifier = Modifier().fillMaxWidth()) {
                if (buildings.isEmpty()) {
                    EmptyState(BuildingStrings.NO_BUILDINGS)
                } else {
                    // Table using CSS Grid
                    Column(modifier = Modifier().fillMaxWidth()) {
                        // Header row
                        Row(
                            modifier = Modifier()
                                .fillMaxWidth()
                                .style("display", "grid")
                                .style("grid-template-columns", "1fr 1fr 200px")
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
                        ) {
                            GridHeaderCell(BuildingStrings.BUILDING_NAME)
                            GridHeaderCell(BuildingStrings.TOTAL_UNITS)
                            GridHeaderCell("")
                        }
                        // Data rows
                        buildings.forEach { building ->
                            Row(
                                modifier = Modifier()
                                    .fillMaxWidth()
                                    .style("display", "grid")
                                    .style("grid-template-columns", "1fr 1fr 200px")
                                    .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
                            ) {
                                GridCell { Text(building.name) }
                                GridCell { Text((unitCounts[building.id] ?: 0).toString()) }
                                GridCell {
                                    Row(
                                        modifier = Modifier()
                                            .style("gap", BuildingTheme.Spacing.sm)
                                            .style("flex-wrap", "wrap")
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
                    .style("gap", BuildingTheme.Spacing.sm)
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
            
            Card(modifier = Modifier().fillMaxWidth()) {
                if (apartments.isEmpty()) {
                    EmptyState(BuildingStrings.NO_UNITS)
                } else {
                    // Units table using CSS Grid
                    Column(modifier = Modifier().fillMaxWidth()) {
                        // Header row
                        Row(
                            modifier = Modifier()
                                .fillMaxWidth()
                                .style("display", "grid")
                                .style("grid-template-columns", "80px 1fr 120px 160px 100px 150px")
                                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                                .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
                        ) {
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
                                    .style("display", "grid")
                                    .style("grid-template-columns", "80px 1fr 120px 160px 100px 150px")
                                    .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
                            ) {
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
                                            .style("gap", BuildingTheme.Spacing.sm)
                                            .style("flex-wrap", "wrap")
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
                    .style("gap", BuildingTheme.Spacing.sm)
            ) {
                FilterButton(BuildingStrings.ALL, null, currentFilter)
                FilterButton(BuildingStrings.OVERDUE, "OVERDUE", currentFilter)
                FilterButton(BuildingStrings.PENDING, "PENDING", currentFilter)
                FilterButton(BuildingStrings.PAID, "PAID", currentFilter)
            }
            
            Card(modifier = Modifier().fillMaxWidth()) {
                if (payments.isEmpty()) {
                    EmptyState(BuildingStrings.NO_PAYMENTS)
                } else {
                    PaymentTable(payments)
                }
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
private fun PaymentTable(payments: List<PaymentWithDetails>) {
    // Payment table using CSS Grid
    Column(modifier = Modifier().fillMaxWidth()) {
        // Header row
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .style("display", "grid")
                .style("grid-template-columns", "1fr 80px 1fr 80px 100px 100px 80px 1fr")
                .backgroundColor(BuildingTheme.Colors.BG_HOVER)
                .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
        ) {
            GridHeaderCell(BuildingStrings.BUILDING_NAME)
            GridHeaderCell(BuildingStrings.UNIT_NUMBER)
            GridHeaderCell(BuildingStrings.TENANT)
            GridHeaderCell(BuildingStrings.PAYMENT_NUMBER)
            GridHeaderCell(BuildingStrings.PAYMENT_AMOUNT)
            GridHeaderCell(BuildingStrings.DUE_DATE)
            GridHeaderCell(BuildingStrings.PAYMENT_STATUS)
            GridHeaderCell(BuildingStrings.NOTES)
        }
        // Data rows
        payments.forEach { detail ->
            Row(
                modifier = Modifier()
                    .fillMaxWidth()
                    .style("display", "grid")
                    .style("grid-template-columns", "1fr 80px 1fr 80px 100px 100px 80px 1fr")
                    .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
            ) {
                GridCell { Text(detail.building?.name ?: "-") }
                GridCell { Text(detail.apartment?.unitNumber ?: "-") }
                GridCell { Text(detail.tenant?.name ?: "-") }
                GridCell { Text(BuildingStrings.formatPaymentNumber(detail.payment.paymentNumber)) }
                GridCell { Text(BuildingStrings.formatCurrency(detail.payment.amount)) }
                GridCell { Text(detail.payment.dueDate) }
                GridCell { StatusBadge(detail.payment.status) }
                GridCell { Text(detail.payment.notes) }
            }
        }
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
                
                Form(
                    action = "/clear-data",
                    method = FormMethod.Post,
                    modifier = Modifier()
                        .attribute("onsubmit", "return confirm('${BuildingStrings.CLEAR_DATA_CONFIRM_DIALOG}');")
                ) {
                    FormButton(
                        text = BuildingStrings.CLEAR_DATA,
                        modifier = Modifier()
                            .backgroundColor(BuildingTheme.Colors.DANGER)
                            .color(BuildingTheme.Colors.TEXT_WHITE)
                            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.lg)
                            .borderRadius(BuildingTheme.BorderRadius.md)
                            .fontWeight("500")
                    )
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
                    .style("gap", BuildingTheme.Spacing.sm)
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
                            .style("gap", BuildingTheme.Spacing.md)
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
                                .style("text-decoration", "none")
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
                    .style("gap", BuildingTheme.Spacing.sm)
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
                        .style("gap", BuildingTheme.Spacing.md)
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
                            .style("text-decoration", "none")
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
                    .style("gap", BuildingTheme.Spacing.sm)
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
                            .style("display", "grid")
                            .style("grid-template-columns", "1fr 1fr")
                            .style("gap", BuildingTheme.Spacing.md)
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
                            .style("display", "grid")
                            .style("grid-template-columns", "1fr 1fr")
                            .style("gap", BuildingTheme.Spacing.md)
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
                            .style("display", "grid")
                            .style("grid-template-columns", "1fr 1fr")
                            .style("gap", BuildingTheme.Spacing.md)
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
                            .style("gap", BuildingTheme.Spacing.md)
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
                                .style("text-decoration", "none")
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
private fun GridHeaderCell(text: String) {
    Box(
        modifier = Modifier()
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .fontWeight("600")
            .fontSize(BuildingTheme.FontSize.sm)
            .color(BuildingTheme.Colors.TEXT_SECONDARY)
            .style("text-align", "right")
    ) {
        Text(text)
    }
}

@Composable
private fun GridCell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier()
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .style("text-align", "right")
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
                    .style("border-right", "4px solid ${BuildingTheme.Colors.INFO}")
            ) {
                Row(
                    modifier = Modifier()
                        .fillMaxWidth()
                        .style("align-items", "center")
                        .style("gap", BuildingTheme.Spacing.md)
                ) {
                    // Bell icon
                    Text(
                        text = "🔔",
                        modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
                    )
                    Column(modifier = Modifier().style("flex", "1")) {
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
            .style("border-right", "4px solid $borderColor")
    ) {
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .style("align-items", "center")
                .style("gap", BuildingTheme.Spacing.md)
        ) {
            // Icon
            Text(
                text = icon,
                modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
            )
            
            // Content
            Column(modifier = Modifier().style("flex", "1")) {
                Text(
                    text = "${payment.building?.name ?: ""} - ${payment.apartment?.unitNumber ?: ""}",
                    modifier = Modifier()
                        .fontWeight("600")
                        .color(textColor)
                        .fontSize(BuildingTheme.FontSize.sm)
                )
                Row(
                    modifier = Modifier()
                        .style("gap", BuildingTheme.Spacing.md)
                        .style("flex-wrap", "wrap")
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
            .style("border-right", "4px solid ${BuildingTheme.Colors.DANGER}")
    ) {
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .style("align-items", "center")
                .style("gap", BuildingTheme.Spacing.md)
        ) {
            Text(
                text = "🚨",
                modifier = Modifier().fontSize(BuildingTheme.FontSize.xl)
            )
            Column(modifier = Modifier().style("flex", "1")) {
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
                    .style("text-decoration", "none")
            ) {
                Text("عرض")
            }
        }
    }
}