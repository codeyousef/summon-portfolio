package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.ContactStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.input.FormField
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.theme.ColorHelpers.textColor

@Composable
fun ContactSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val actionPath = if (locale == PortfolioLocale.EN) "/api/contact" else "/${locale.code}/api/contact"
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            Paragraph(
                text = ContactStrings.lead.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            Paragraph(
                text = ContactStrings.contactMethods.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.4)
            )
        }
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexWrap(FlexWrap.Wrap)
                .alignItems(AlignItems.Stretch)
                .gap(PortfolioTheme.Spacing.xl)
                .width(100.percent)
        ) {
            Column(
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "320px")
                    .width("min(100%, 420px)")
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = ContactStrings.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.5.rem)
                        .fontWeight(700)
                )
                Text(
                    text = ContactStrings.subtitle.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.8)
                )
            }

            ContactForm(
                locale = locale,
                action = actionPath,
                modifier = Modifier()
                    .flex(grow = 1, shrink = 1, basis = "360px")
                    .width("min(100%, 520px)")
                    .let { base ->
                        if (locale.direction == "rtl") {
                            base.paddingLeft(PortfolioTheme.Spacing.xl)
                        } else {
                            base.paddingRight(PortfolioTheme.Spacing.xl)
                        }
                    }
            )
        }
    }
}

@Composable
private fun ContactForm(
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
        // Labels via component props to avoid duplicates
        // Styled label + asterisk (avoid built-in label to control color and duplication)
        Row(modifier = Modifier().display(Display.Flex).alignItems(AlignItems.Center).gap(PortfolioTheme.Spacing.xs)) {
//            Text(
//                text = ContactCopy.name.resolve(locale),
//                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
//            )
        }
        FormField(
            label = {
                Text(
                    ContactStrings.name.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = true
        ) {
            FormTextField(
                name = "name",
                label = "",
                defaultValue = "",
                placeholder = ContactStrings.name.resolve(locale),
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
        }
        FormField(
            label = {
                Text(
                    ContactStrings.email.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = false
        ) {
            FormTextField(
                name = "email",
                label = "",
                defaultValue = "",
                placeholder = ContactStrings.email.resolve(locale),
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
        }
        FormField(
            label = {
                Text(
                    ContactStrings.whatsapp.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = false
        ) {
            FormTextField(
                name = "whatsapp",
                label = "",
                defaultValue = "",
                placeholder = ContactStrings.whatsappPlaceholder.resolve(locale),
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
        }
        FormField(
            label = {
                Text(
                    ContactStrings.requirements.resolve(locale),
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
            },
            isRequired = true
        ) {
            FormTextArea(
                name = "requirements",
                label = "",
                defaultValue = "",
                required = true,
                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
            )
        }
        FormButton(
            text = ContactStrings.submit.resolve(locale)
        )
    }
}

