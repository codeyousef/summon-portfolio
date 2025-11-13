package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Paragraph
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.forms.*
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.styles.GlobalStyle
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight

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
                        .fontWeight(700)
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
    val optionalLabel = ContactCopy.optional.resolve(locale)
    FormStyleSheet()
    ContactFormStyles()
    Form(
        action = action,
        modifier = modifier
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .width(100.percent)
            .gap(PortfolioTheme.Spacing.md)
            .minWidth("0px")
    ) {
        FormTextField(
            name = "name",
            label = ContactCopy.name.resolve(locale),
            required = true,
            placeholder = ContactCopy.name.resolve(locale),
            autoComplete = "name",
            optionalLabel = optionalLabel,
            fullWidth = true
        )
        FormTextField(
            name = "email",
            label = ContactCopy.email.resolve(locale),
            type = FormTextFieldType.EMAIL,
            placeholder = ContactCopy.email.resolve(locale),
            autoComplete = "email",
            optionalLabel = optionalLabel,
            required = false,
            fullWidth = true
        )
        FormTextField(
            name = "whatsapp",
            label = ContactCopy.whatsapp.resolve(locale),
            placeholder = ContactCopy.whatsappPlaceholder.resolve(locale),
            optionalLabel = optionalLabel,
            inputMode = "tel",
            required = false,
            fullWidth = true
        )
        FormTextArea(
            name = "requirements",
            label = ContactCopy.requirements.resolve(locale),
            required = true,
            placeholder = null,
            minHeight = "180px",
            optionalLabel = optionalLabel,
            fullWidth = true
        )
        FormButton(
            text = ContactCopy.submit.resolve(locale),
            tone = FormButtonTone.ACCENT,
            fullWidth = true
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

@Composable
private fun ContactFormStyles() {
    GlobalStyle(
        """
        textarea::placeholder {
          opacity: 0.6;
        }
        """.trimIndent()
    )
}
