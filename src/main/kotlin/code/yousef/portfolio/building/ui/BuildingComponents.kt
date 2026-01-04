package code.yousef.portfolio.building.ui

import code.yousef.portfolio.building.model.*
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.modifier.*
import codes.yousef.summon.runtime.Composable as RuntimeComposable

/**
 * Main layout wrapper for building management pages
 */
@Composable
fun BuildingPageLayout(
    title: String,
    username: String? = null,
    currentPath: String = "/",
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .minHeight("100vh")
            .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
    ) {
        Column(modifier = Modifier().fillMaxWidth()) {
            // Navigation
            BuildingNav(username = username, currentPath = currentPath)
            
            // Main content
            Box(
                modifier = Modifier()
                    .fillMaxWidth()
                    .maxWidth("1200px")
                    .margin("0 auto")
                    .padding(BuildingTheme.Spacing.lg)
            ) {
                content()
            }
        }
    }
}

@Composable
fun BuildingNav(username: String?, currentPath: String) {
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .backgroundColor(BuildingTheme.Colors.BG_CARD)
            .padding(BuildingTheme.Spacing.md, BuildingTheme.Spacing.lg)
            .style("box-shadow", "0 1px 3px ${BuildingTheme.Colors.SHADOW}")
    ) {
        Row(
            modifier = Modifier()
                .fillMaxWidth()
                .maxWidth("1200px")
                .margin("0 auto")
                .style("justify-content", "space-between")
                .style("align-items", "center")
        ) {
            // Title
            Text(
                text = BuildingStrings.APP_TITLE,
                modifier = Modifier()
                    .fontSize(BuildingTheme.FontSize.xl)
                    .fontWeight("700")
                    .color(BuildingTheme.Colors.PRIMARY)
            )
            
            // Nav links (only show if logged in)
            if (username != null) {
                Row(
                    modifier = Modifier()
                        .style("gap", BuildingTheme.Spacing.md)
                        .style("align-items", "center")
                ) {
                    NavLink(BuildingStrings.DASHBOARD, "/", currentPath)
                    NavLink(BuildingStrings.BUILDINGS, "/buildings", currentPath)
                    NavLink(BuildingStrings.PAYMENTS, "/payments", currentPath)
                    NavLink(BuildingStrings.IMPORT_DATA, "/import", currentPath)
                    
                    // User info and logout
                    Text(
                        text = username,
                        modifier = Modifier()
                            .color(BuildingTheme.Colors.TEXT_SECONDARY)
                            .fontSize(BuildingTheme.FontSize.sm)
                            .padding("0", BuildingTheme.Spacing.md)
                    )
                    
                    codes.yousef.summon.components.navigation.Link(
                        href = "/logout",
                        modifier = Modifier()
                            .color(BuildingTheme.Colors.DANGER)
                            .fontSize(BuildingTheme.FontSize.sm)
                    ) {
                        Text(BuildingStrings.LOGOUT)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavLink(label: String, href: String, currentPath: String) {
    val isActive = currentPath == href || (href != "/" && currentPath.startsWith(href))
    codes.yousef.summon.components.navigation.Link(
        href = href,
        modifier = Modifier()
            .color(if (isActive) BuildingTheme.Colors.PRIMARY else BuildingTheme.Colors.TEXT_SECONDARY)
            .fontSize(BuildingTheme.FontSize.sm)
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .backgroundColor(if (isActive) BuildingTheme.Colors.BG_HOVER else "transparent")
    ) {
        Text(label)
    }
}

/**
 * Card component
 */
@Composable
fun Card(
    title: String? = null,
    modifier: Modifier = Modifier(),
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .backgroundColor(BuildingTheme.Colors.BG_CARD)
            .borderRadius(BuildingTheme.BorderRadius.lg)
            .padding(BuildingTheme.Spacing.lg)
            .style("box-shadow", "0 1px 3px ${BuildingTheme.Colors.SHADOW}")
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier()
                    .fontSize(BuildingTheme.FontSize.lg)
                    .fontWeight("600")
                    .color(BuildingTheme.Colors.TEXT_PRIMARY)
                    .margin("0", "0", BuildingTheme.Spacing.md, "0")
            )
        }
        content()
    }
}

/**
 * Stat card for dashboard
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    color: String = BuildingTheme.Colors.PRIMARY
) {
    Column(
        modifier = Modifier()
            .backgroundColor(BuildingTheme.Colors.BG_CARD)
            .borderRadius(BuildingTheme.BorderRadius.lg)
            .padding(BuildingTheme.Spacing.lg)
            .style("text-align", "center")
            .style("box-shadow", "0 1px 3px ${BuildingTheme.Colors.SHADOW}")
    ) {
        Text(
            text = value,
            modifier = Modifier()
                .fontSize(BuildingTheme.FontSize.xxxl)
                .fontWeight("700")
                .color(color)
        )
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(BuildingTheme.FontSize.sm)
                .color(BuildingTheme.Colors.TEXT_SECONDARY)
                .margin(BuildingTheme.Spacing.xs, "0", "0", "0")
        )
    }
}

/**
 * Status badge
 */
@Composable
fun StatusBadge(status: PaymentStatus) {
    val (bgColor, textColor, label) = when (status) {
        PaymentStatus.PAID -> Triple(
            BuildingTheme.Colors.SUCCESS_BG,
            BuildingTheme.Colors.SUCCESS_TEXT,
            BuildingStrings.PAID
        )
        PaymentStatus.PENDING -> Triple(
            BuildingTheme.Colors.WARNING_BG,
            BuildingTheme.Colors.WARNING_TEXT,
            BuildingStrings.PENDING
        )
        PaymentStatus.OVERDUE -> Triple(
            BuildingTheme.Colors.DANGER_BG,
            BuildingTheme.Colors.DANGER_TEXT,
            BuildingStrings.OVERDUE
        )
    }
    
    Text(
        text = label,
        modifier = Modifier()
            .backgroundColor(bgColor)
            .color(textColor)
            .padding(BuildingTheme.Spacing.xs, BuildingTheme.Spacing.sm)
            .borderRadius(BuildingTheme.BorderRadius.full)
            .fontSize(BuildingTheme.FontSize.xs)
            .fontWeight("500")
    )
}

/**
 * Alert component
 */
@Composable
fun Alert(
    message: String,
    type: AlertType = AlertType.ERROR
) {
    val (bgColor, textColor, borderColor) = when (type) {
        AlertType.ERROR -> Triple(
            BuildingTheme.Colors.DANGER_BG,
            BuildingTheme.Colors.DANGER_TEXT,
            BuildingTheme.Colors.DANGER
        )
        AlertType.SUCCESS -> Triple(
            BuildingTheme.Colors.SUCCESS_BG,
            BuildingTheme.Colors.SUCCESS_TEXT,
            BuildingTheme.Colors.SUCCESS
        )
        AlertType.WARNING -> Triple(
            BuildingTheme.Colors.WARNING_BG,
            BuildingTheme.Colors.WARNING_TEXT,
            BuildingTheme.Colors.WARNING
        )
        AlertType.INFO -> Triple(
            BuildingTheme.Colors.INFO_BG,
            BuildingTheme.Colors.INFO_TEXT,
            BuildingTheme.Colors.INFO
        )
    }
    
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .backgroundColor(bgColor)
            .color(textColor)
            .padding(BuildingTheme.Spacing.md)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .style("border", "1px solid $borderColor")
            .margin("0", "0", BuildingTheme.Spacing.md, "0")
    ) {
        Text(message)
    }
}

enum class AlertType {
    ERROR, SUCCESS, WARNING, INFO
}

/**
 * Empty state component
 */
@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier()
            .fillMaxWidth()
            .padding(BuildingTheme.Spacing.xxl)
            .style("text-align", "center")
    ) {
        Text(
            text = message,
            modifier = Modifier()
                .color(BuildingTheme.Colors.TEXT_MUTED)
                .fontSize(BuildingTheme.FontSize.base)
        )
    }
}

/**
 * Button component
 */
@Composable
fun Button(
    text: String,
    type: ButtonType = ButtonType.PRIMARY,
    onClick: String? = null,
    modifier: Modifier = Modifier()
) {
    val (bgColor, textColor) = when (type) {
        ButtonType.PRIMARY -> BuildingTheme.Colors.PRIMARY to BuildingTheme.Colors.TEXT_WHITE
        ButtonType.SECONDARY -> BuildingTheme.Colors.BG_HOVER to BuildingTheme.Colors.TEXT_PRIMARY
        ButtonType.DANGER -> BuildingTheme.Colors.DANGER to BuildingTheme.Colors.TEXT_WHITE
        ButtonType.SUCCESS -> BuildingTheme.Colors.SUCCESS to BuildingTheme.Colors.TEXT_WHITE
    }
    
    Box(
        modifier = modifier
            .backgroundColor(bgColor)
            .color(textColor)
            .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
            .borderRadius(BuildingTheme.BorderRadius.md)
            .fontSize(BuildingTheme.FontSize.sm)
            .fontWeight("500")
            .style("cursor", "pointer")
            .style("display", "inline-flex")
            .style("align-items", "center")
            .style("justify-content", "center")
    ) {
        Text(text)
    }
}

enum class ButtonType {
    PRIMARY, SECONDARY, DANGER, SUCCESS
}

/**
 * Page header with title and optional action
 */
@Composable
fun PageHeader(
    title: String,
    actionLabel: String? = null,
    actionHref: String? = null
) {
    Row(
        modifier = Modifier()
            .fillMaxWidth()
            .style("justify-content", "space-between")
            .style("align-items", "center")
            .margin("0", "0", BuildingTheme.Spacing.lg, "0")
    ) {
        Text(
            text = title,
            modifier = Modifier()
                .fontSize(BuildingTheme.FontSize.xxl)
                .fontWeight("700")
                .color(BuildingTheme.Colors.TEXT_PRIMARY)
        )
        
        if (actionLabel != null && actionHref != null) {
            codes.yousef.summon.components.navigation.Link(
                href = actionHref,
                modifier = Modifier()
                    .backgroundColor(BuildingTheme.Colors.PRIMARY)
                    .color(BuildingTheme.Colors.TEXT_WHITE)
                    .padding(BuildingTheme.Spacing.sm, BuildingTheme.Spacing.md)
                    .borderRadius(BuildingTheme.BorderRadius.md)
                    .fontSize(BuildingTheme.FontSize.sm)
                    .fontWeight("500")
            ) {
                Text(actionLabel)
            }
        }
    }
}
