package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.PageScaffold
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormHiddenField
import codes.yousef.summon.components.forms.FormTextField
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap

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
            // Improve label contrast on dark surface
            Text(
                text = "",
                modifier = Modifier().rawStyle("label{color:${PortfolioTheme.Colors.TEXT_PRIMARY} !important;}")
            )
            FormTextField(
                name = "username",
                label = "Username",
                required = true,
                defaultValue = ""
            )
            FormTextField(
                name = "password",
                label = "Password",
                required = true,
                defaultValue = ""
            )
            FormButton(
                text = "Sign In"
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
                defaultValue = currentUsername
            )
            FormTextField(
                name = "password",
                label = "New Password",
                required = true,
                defaultValue = ""
            )
            FormTextField(
                name = "confirm",
                label = "Confirm Password",
                required = true,
                defaultValue = ""
            )
            FormButton(
                text = "Save Credentials"
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
                // Form styles handled via modifiers
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
