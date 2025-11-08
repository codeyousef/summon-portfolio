package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
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
    val labelStyles =
        "display:flex;justify-content:space-between;align-items:center;font-size:0.9rem;font-weight:600;color:${PortfolioTheme.Colors.TEXT_PRIMARY};"
    val optionalBadge = ContactCopy.optional.resolve(locale)
    val optionalChip =
        "<span style=\"font-size:0.75rem;color:${PortfolioTheme.Colors.TEXT_SECONDARY};font-weight:500\">$optionalBadge</span>"
    val inputStyles =
        "width:100%;padding:14px 18px;border:1px solid ${PortfolioTheme.Colors.BORDER};border-radius:16px;background:${PortfolioTheme.Colors.BACKGROUND_ALT};color:${PortfolioTheme.Colors.TEXT_PRIMARY};font-size:1rem;box-shadow:${PortfolioTheme.Shadows.LOW};"
    val textareaStyles = "$inputStyles min-height:160px;resize:vertical;"
    val buttonStyles =
        "width:100%;padding:16px;border:none;border-radius:16px;background:${PortfolioTheme.Gradients.ACCENT};color:#ffffff;font-weight:700;font-size:1rem;cursor:pointer;box-shadow:${PortfolioTheme.Shadows.MEDIUM};"
    val formHtml = buildString {
        append(
            """
            <form method="post" action="$action" style="display:flex;flex-direction:column;gap:16px;">
                <label style="$labelStyles">${ContactCopy.name.resolve(locale)} *</label>
                <input type="text" name="name" required style="$inputStyles" placeholder="${
                ContactCopy.name.resolve(
                    locale
                )
            }" autocomplete="name" />
                <label style="$labelStyles">${ContactCopy.email.resolve(locale)} $optionalChip</label>
                <input type="email" name="email" style="$inputStyles" placeholder="${ContactCopy.email.resolve(locale)}" autocomplete="email" />
                <label style="$labelStyles">${ContactCopy.whatsapp.resolve(locale)} $optionalChip</label>
                <input type="text" name="whatsapp" style="$inputStyles" placeholder="${
                ContactCopy.whatsapp.resolve(
                    locale
                )
            }" autocomplete="tel" />
                <label style="$labelStyles">${ContactCopy.requirements.resolve(locale)} *</label>
                <textarea name="requirements" required style="$textareaStyles" placeholder="${
                ContactCopy.requirements.resolve(
                    locale
                )
            }"></textarea>
                <button type="submit" style="$buttonStyles">${ContactCopy.submit.resolve(locale)}</button>
            </form>
            """.trimIndent()
        )
    }
    RawHtml(
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
            .backdropBlur(18.px),
        sanitize = true
    ) {
        append(formHtml)
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
