package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.forms.*
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun AdminLoginPage(
    errorMessage: String?,
    nextPath: String?
) {
    AdminAuthScaffold(
        title = "Admin Access",
        subtitle = "Sign in to update your portfolio content.",
        errorMessage = errorMessage
    ) {
        Text(
            text = "Default credentials: admin / admin. You'll be prompted to update them after signing in.",
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize("0.85rem")
                .textAlign(TextAlign.Center)
                .margin("0 0 8px 0")
        )
        Form(
            action = "/admin/login",
            hiddenFields = nextPath?.let { listOf(FormHiddenField("next", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "username",
                label = "Username",
                required = true,
                placeholder = "admin",
                fullWidth = true
            )
            FormTextField(
                name = "password",
                label = "Password",
                required = true,
                type = FormTextFieldType.PASSWORD,
                fullWidth = true
            )
            FormButton(
                text = "Sign In",
                tone = FormButtonTone.ACCENT,
                fullWidth = true
            )
        }
    }
}

@Composable
fun AdminChangePasswordPage(
    currentUsername: String,
    errorMessage: String?
) {
    AdminAuthScaffold(
        title = "Update Credentials",
        subtitle = "Please set a new username and password before continuing.",
        errorMessage = errorMessage
    ) {
        Form(action = "/admin/change-password") {
            FormTextField(
                name = "username",
                label = "New Username",
                required = true,
                defaultValue = currentUsername,
                fullWidth = true
            )
            FormTextField(
                name = "password",
                label = "New Password",
                required = true,
                type = FormTextFieldType.PASSWORD,
                fullWidth = true
            )
            FormTextField(
                name = "confirm",
                label = "Confirm Password",
                required = true,
                type = FormTextFieldType.PASSWORD,
                fullWidth = true
            )
            FormButton(
                text = "Save Credentials",
                tone = FormButtonTone.ACCENT,
                fullWidth = true
            )
        }
    }
}

@Composable
private fun AdminAuthScaffold(
    title: String,
    subtitle: String,
    errorMessage: String?,
    content: @Composable () -> Unit
) {
    PageScaffold(locale = PortfolioLocale.EN) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .justifyContent(JustifyContent.Center)
                .padding(PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch)
                    .gap(PortfolioTheme.Spacing.md)
                    .width("min(480px, 100%)")
                    .padding(PortfolioTheme.Spacing.xl)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.lg)
                    .backgroundColor("rgba(12,14,24,0.85)")
                    .boxShadow(PortfolioTheme.Shadows.MEDIUM)
                    .backdropBlur(24.px)
            ) {
                FormStyleSheet()
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.xs)
                ) {
                    Text(
                        text = title,
                        modifier = Modifier()
                            .fontSize("1.5rem")
                            .textAlign(TextAlign.Center)
                    )
                    Text(
                        text = subtitle,
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .textAlign(TextAlign.Center)
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.DANGER)
                            .textAlign(TextAlign.Center)
                            .padding(PortfolioTheme.Spacing.sm)
                            .backgroundColor("rgba(255,77,77,0.12)")
                            .borderRadius(PortfolioTheme.Radii.md)
                    )
                }
                content()
            }
        }
    }
}
