package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.*
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.ButtonLink
import code.yousef.summon.modifier.Modifier

@Composable
fun ContactSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val actionPath = if (locale == PortfolioLocale.EN) "/api/contact" else "/${locale.code}/api/contact"
    ContentSection(modifier = modifier) {
        Row(
            modifier = Modifier()
                .style("display", "grid")
                .style("grid-template-columns", "repeat(auto-fit, minmax(320px, 1fr))")
                .style("gap", PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = ContactCopy.title.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "2.5rem")
                        .style("font-weight", "700")
                )
                Text(
                    text = ContactCopy.subtitle.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.textSecondary)
                        .style("line-height", "1.8")
                )
                Column(
                    modifier = Modifier()
                        .style("display", "flex")
                        .style("flex-direction", "column")
                        .style("gap", PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = "yousef.baitalmal.dev@email.com",
                        modifier = Modifier()
                            .style("font-size", "1.1rem")
                            .style("font-weight", "600")
                    )
                    ButtonLink(
                        ContactCopy.schedule.resolve(locale),
                        "mailto:yousef.baitalmal.dev@email.com",
                        Modifier()
                            .backgroundColor(PortfolioTheme.Colors.accent)
                            .color("#ffffff")
                            .style("padding", "${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.xl}")
                            .borderRadius(PortfolioTheme.Radii.pill)
                    )
                }
            }

            ContactForm(locale = locale, action = actionPath)
        }
    }
}

@Composable
private fun ContactForm(locale: PortfolioLocale, action: String) {
    Form(
        onSubmit = {},
        modifier = Modifier()
            .attribute("action", action)
            .attribute("method", "post")
    ) {
        Column(
            modifier = Modifier()
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.md)
                .backgroundColor(PortfolioTheme.Colors.surface)
                .border("1px", "solid", PortfolioTheme.Colors.border)
                .borderRadius(PortfolioTheme.Radii.lg)
                .style("padding", PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = ContactCopy.formTitle.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "1.2rem")
                    .style("font-weight", "600")
            )
            TextField(
                value = "",
                onValueChange = {},
                placeholder = ContactCopy.namePlaceholder.resolve(locale),
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "name")
                    .attribute("required", "required")
            )
            TextField(
                value = "",
                onValueChange = {},
                placeholder = ContactCopy.emailPlaceholder.resolve(locale),
                type = TextFieldType.Email,
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "email")
            )
            TextField(
                value = "",
                onValueChange = {},
                placeholder = ContactCopy.whatsappPlaceholder.resolve(locale),
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "whatsapp")
                    .attribute("required", "required")
            )
            TextArea(
                value = "",
                onValueChange = {},
                modifier = Modifier()
                    .style("width", "100%")
                    .style("min-height", "140px")
                    .style("align-items", "flex-start")
                    .attribute("name", "requirements")
                    .attribute("required", "required"),
                placeholder = ContactCopy.requirementsPlaceholder.resolve(locale)
            )
            Button(
                onClick = {},
                label = ContactCopy.submit.resolve(locale),
                modifier = Modifier()
                    .backgroundColor(PortfolioTheme.Colors.accentAlt)
                    .color("#050505")
                    .style("padding", "${PortfolioTheme.Spacing.sm} ${PortfolioTheme.Spacing.xl}")
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .style("text-align", "center")
                    .attribute("type", "submit"),
                variant = ButtonVariant.PRIMARY,
                false,
                "",
                IconPosition.START
            )
        }
    }
}

private object ContactCopy {
    val title = LocalizedText("Let's Build.", "دعنا نبني.")
    val subtitle = LocalizedText(
        en = "If you're working on revolutionary products, I'd love to talk.",
        ar = "إذا كنت تعمل على منتجات ثورية، يسعدني أن نتحدث."
    )
    val schedule = LocalizedText("Send an email", "أرسل بريدًا إلكترونيًا")
    val formTitle = LocalizedText("Tell me about your project", "أخبرني عن مشروعك")
    val namePlaceholder = LocalizedText("Your name *", "اسمك *")
    val emailPlaceholder = LocalizedText("Email (optional)", "البريد الإلكتروني (اختياري)")
    val whatsappPlaceholder = LocalizedText("WhatsApp number *", "رقم الواتساب *")
    val requirementsPlaceholder = LocalizedText("Project requirements *", "متطلبات المشروع *")
    val submit = LocalizedText("Send Message", "أرسل الرسالة")
}
