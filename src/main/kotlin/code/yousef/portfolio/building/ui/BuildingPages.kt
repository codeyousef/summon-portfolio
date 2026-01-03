package code.yousef.portfolio.building.ui

import code.yousef.portfolio.building.model.*
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Div
import codes.yousef.summon.components.layout.Row
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
                                .style("grid-template-columns", "1fr 1fr 100px")
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
                                    .style("grid-template-columns", "1fr 1fr 100px")
                                    .style("border-bottom", "1px solid ${BuildingTheme.Colors.BORDER}")
                            ) {
                                GridCell { Text(building.name) }
                                GridCell { Text((unitCounts[building.id] ?: 0).toString()) }
                                GridCell {
                                    codes.yousef.summon.components.navigation.Link(
                                        href = "/buildings/${building.id}",
                                        modifier = Modifier()
                                            .color(BuildingTheme.Colors.PRIMARY)
                                            .fontSize(BuildingTheme.FontSize.sm)
                                    ) {
                                        Text(BuildingStrings.VIEW_UNITS)
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
                                .style("grid-template-columns", "80px 1fr 120px 160px 100px 100px")
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
                                    .style("grid-template-columns", "80px 1fr 120px 160px 100px 100px")
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