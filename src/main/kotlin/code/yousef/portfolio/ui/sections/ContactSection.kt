package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.forms.*
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.flexWrap
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight

private const val SCHEDULE_CALL_URL = "https://cal.com/yousef/intro"

@Composable
fun ContactSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val actionPath = if (locale == PortfolioLocale.EN) "/api/contact" else "/${locale.code}/api/contact"
    ContentSection(modifier = modifier) {
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(320px, 1fr))")
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
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
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = "yousef.baitalmal.dev@email.com",
                        modifier = Modifier()
                            .fontSize(1.1.rem)
                            .fontWeight(600)
                    )
                    Row(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.sm)
                            .flexWrap(FlexWrap.Wrap)
                    ) {
                        ContactLinkChip(
                            label = ContactCopy.emailCta.resolve(locale),
                            href = "mailto:yousef.baitalmal.dev@email.com",
                            filled = true,
                            dataAttributes = mapOf("cta" to "contact-email")
                        )
                        ContactLinkChip(
                            label = ContactCopy.scheduleCall.resolve(locale),
                            href = SCHEDULE_CALL_URL,
                            filled = false,
                            openInNewTab = true,
                            dataAttributes = mapOf("cta" to "contact-call")
                        )
                    }
                }
            }

            ContactForm(locale = locale, action = actionPath)
        }
    }
}

@Composable
private fun ContactForm(locale: PortfolioLocale, action: String) {
    val optionalLabel = ContactCopy.optional.resolve(locale)
    FormStyleSheet()
    Form(
        action = action,
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .backdropBlur(18.px)
    ) {
        FormTextField(
            name = "name",
            label = ContactCopy.name.resolve(locale),
            required = true,
            placeholder = ContactCopy.name.resolve(locale),
            autoComplete = "name",
            optionalLabel = optionalLabel
        )
        FormTextField(
            name = "email",
            label = ContactCopy.email.resolve(locale),
            type = FormTextFieldType.EMAIL,
            placeholder = ContactCopy.email.resolve(locale),
            autoComplete = "email",
            optionalLabel = optionalLabel
        )
        FormTextField(
            name = "whatsapp",
            label = ContactCopy.whatsapp.resolve(locale),
            placeholder = ContactCopy.whatsapp.resolve(locale),
            autoComplete = "tel",
            inputMode = "tel",
            optionalLabel = optionalLabel
        )
        FormTextArea(
            name = "requirements",
            label = ContactCopy.requirements.resolve(locale),
            required = true,
            placeholder = ContactCopy.requirements.resolve(locale),
            minHeight = "180px",
            optionalLabel = optionalLabel
        )
        FormButton(
            text = ContactCopy.submit.resolve(locale),
            tone = FormButtonTone.ACCENT,
            fullWidth = true
        )
    }
}
@Composable
private fun ContactLinkChip(
    label: String,
    href: String,
    filled: Boolean,
    openInNewTab: Boolean = false,
    dataAttributes: Map<String, String> = emptyMap()
) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .gap(PortfolioTheme.Spacing.xs)
            .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
            .borderWidth(if (filled) 0 else 1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(if (filled) "transparent" else PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.pill)
            .background(if (filled) PortfolioTheme.Gradients.ACCENT else PortfolioTheme.Colors.GLASS)
            .color(if (filled) "#ffffff" else PortfolioTheme.Colors.TEXT_PRIMARY)
            .fontWeight(600)
            .whiteSpace(WhiteSpace.NoWrap)
            .boxShadow(if (filled) PortfolioTheme.Shadows.LOW else "none"),
        target = if (openInNewTab) "_blank" else null,
        rel = if (openInNewTab) "noopener noreferrer" else null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null,
        dataAttributes = dataAttributes
    )
}

private object ContactCopy {
    val title = LocalizedText("Collaborate", "تعاون")
    val subtitle = LocalizedText(
        en = "Tell me what you’re building. I’ll reply with a focused plan (and timelines) you can immediately act on.",
        ar = "أخبرني بما تعمل عليه وسأعود إليك بخطة واضحة وجدول زمني يمكن البدء به فورًا."
    )
    val emailCta = LocalizedText("Send an email", "أرسل بريدًا")
    val scheduleCall = LocalizedText("Schedule a call", "احجز مكالمة")
    val formTitle = LocalizedText("Project details", "تفاصيل المشروع")
    val name = LocalizedText("Name", "الاسم")
    val email = LocalizedText("Email", "البريد الإلكتروني")
    val whatsapp = LocalizedText("WhatsApp / Signal", "واتساب / سيجنال")
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
