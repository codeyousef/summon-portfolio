package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

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
                text = ContactCopy.lead.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.6)
            )
            Paragraph(
                text = ContactCopy.contactMethods.resolve(locale),
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
                    text = ContactCopy.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.5.rem)
                        .fontWeight("700")
                )
                Text(
                    text = ContactCopy.subtitle.resolve(locale),
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
            .gap(PortfolioTheme.Spacing.md)
            .minWidth("0px")
    ) {
        // Labels rendered explicitly for accessible contrast
        Text(
            text = ContactCopy.name.resolve(locale),
            modifier = Modifier()
                .fontWeight(600)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        FormTextField(
            name = "name",
            label = "",
            defaultValue = "",
            required = true,
            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        Text(
            text = ContactCopy.email.resolve(locale),
            modifier = Modifier()
                .fontWeight(600)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        FormTextField(
            name = "email",
            label = "",
            defaultValue = "",
            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        Text(
            text = ContactCopy.whatsapp.resolve(locale),
            modifier = Modifier()
                .fontWeight(600)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        FormTextField(
            name = "whatsapp",
            label = "",
            defaultValue = "",
            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        Text(
            text = ContactCopy.requirements.resolve(locale),
            modifier = Modifier()
                .fontWeight(600)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        FormTextArea(
            name = "requirements",
            label = "",
            defaultValue = "",
            required = true,
            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_PRIMARY)
        )
        FormButton(
            text = ContactCopy.submit.resolve(locale)
        )
    }
}

private object ContactCopy {
    val title = LocalizedText("Collaborate", "تعاون")
    val subtitle = LocalizedText(
        en = "Tell me what you’re building. I’ll reply with a focused plan (and timelines) you can immediately act on.",
        ar = "أخبرني بما تعمل عليه وسأعود إليك بخطة واضحة وجدول زمني يمكن البدء به فورًا."
    )
    val lead = LocalizedText(
        en = "Average response time: under 24h. Share a sentence or two about your project and I’ll follow up with a plan.",
        ar = "متوسط وقت الرد أقل من 24 ساعة. شارك سطرًا أو سطرين عن مشروعك وسأتواصل معك بخطة واضحة."
    )
    val contactMethods = LocalizedText(
        en = "Prefer WhatsApp or email? Use whichever is fastest — just include at least one so I can reply.",
        ar = "يمكنك استخدام البريد الإلكتروني أو رقم واتساب — فقط اذكر أحدهما على الأقل حتى أتمكن من الرد."
    )
    val formTitle = LocalizedText("Project details", "تفاصيل المشروع")
    val name = LocalizedText("Name", "الاسم")
    val email = LocalizedText("Email", "البريد الإلكتروني")
    val whatsapp = LocalizedText("WhatsApp", "رقم واتساب")
    val whatsappPlaceholder = LocalizedText("+1 555 123 4567", "+966 5X XXX XXXX")
    val requirements = LocalizedText("What are we building?", "ماذا سنبني؟")
    val optional = LocalizedText("Optional", "اختياري")
    val submit = LocalizedText("Send request", "أرسل الطلب")
    val submitting = LocalizedText("Sending...", "جاري الإرسال...")
    val success = LocalizedText("Thanks! I’ll reply soon.", "شكرًا! سأتواصل معك قريبًا.")
    val failure = LocalizedText("Something went wrong. Please try again.", "حدث خطأ. حاول مرة أخرى.")
    val errorName = LocalizedText("Name is required", "الاسم مطلوب")
    val errorEmail = LocalizedText("Please enter a valid email", "يرجى إدخال بريد صحيح")
    val errorRequirements = LocalizedText("Share a bit about your project", "شارك تفاصيل حول مشروعك")
}
