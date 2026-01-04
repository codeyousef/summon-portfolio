package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.ContactStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormTextArea
import codes.yousef.summon.components.forms.FormTextField
import codes.yousef.summon.components.input.FormField
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.theme.ColorHelpers.textColor

@Composable
fun ContactFooterSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val actionPath = if (locale == PortfolioLocale.EN) "/api/contact" else "/${locale.code}/api/contact"

    ContentSection(modifier = modifier) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexWrap(FlexWrap.Wrap)
                .alignItems(AlignItems.Stretch)
                .gap(PortfolioTheme.Spacing.xl)
                .width(100.percent)
        ) {
            // Left side: Title and booking link
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "280px")
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = ContactStrings.Footer.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.rem)
                        .fontWeight(700)
                )
                Text(
                    text = ContactStrings.Footer.subtitle.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.6)
                )

                // Motion booking link
                Column(
                    modifier = Modifier()
                        .marginTop(PortfolioTheme.Spacing.md)
                        .gap(PortfolioTheme.Spacing.xs)
                ) {
                    AnchorLink(
                        label = ContactStrings.Footer.bookingLink.resolve(locale),
                        href = "https://app.usemotion.com/meet/motion.duckling867/meeting",
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.ACCENT)
                            .fontWeight(600)
                            .textDecoration(TextDecoration.Underline)
                            .style("text-underline-offset", "4px"),
                        target = "_blank",
                        rel = "noopener noreferrer",
                        navigationMode = LinkNavigationMode.Native,
                        title = null,
                        id = null,
                        ariaLabel = null,
                        ariaDescribedBy = null,
                        dataHref = null,
                        dataAttributes = emptyMap()
                    )
                }
            }

            // Right side: Compact form
            val formModifier = Modifier()
                .flex(grow = 1, shrink = 1, basis = "320px")
                .width("min(100%, 480px)")
                .let { base ->
                    if (locale.direction == "rtl") {
                        base.paddingLeft(PortfolioTheme.Spacing.xl)
                    } else {
                        base.paddingRight(PortfolioTheme.Spacing.xl)
                    }
                }
            CompactContactForm(
                locale = locale,
                action = actionPath,
                modifier = formModifier
            )
        }
    }
}

@Composable
private fun CompactContactForm(
    locale: PortfolioLocale,
    action: String,
    modifier: Modifier = Modifier()
) {
    Form(
        action = action,
        method = FormMethod.Post,
        modifier = modifier
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .width(100.percent)
            .textColor(PortfolioTheme.Colors.TEXT_PRIMARY)
            .gap(PortfolioTheme.Spacing.md)
            .minWidth(0.px)
    ) {
        // Contact field (email or WhatsApp)
        FormField(
            label = {
                Text(
                    ContactStrings.Footer.contactLabel.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = true
        ) {
            FormTextField(
                name = "contact",
                label = "",
                defaultValue = "",
                placeholder = ContactStrings.Footer.contactPlaceholder.resolve(locale),
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
        }

        // Message field
        FormField(
            label = {
                Text(
                    ContactStrings.Footer.messageLabel.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = true
        ) {
            FormTextArea(
                name = "message",
                label = "",
                defaultValue = "",
                required = true,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .minHeight("100px")
            )
        }

        // Submit button
        FormButton(
            text = ContactStrings.Footer.submit.resolve(locale)
        )
    }
}

