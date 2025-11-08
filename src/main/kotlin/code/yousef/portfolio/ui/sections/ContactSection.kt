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
import code.yousef.summon.effects.HttpResponse
import code.yousef.summon.effects.createHttpClient
import code.yousef.summon.extensions.percent
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.AttributeModifiers.buttonType
import code.yousef.summon.modifier.LayoutModifiers.flexDirection
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.LayoutModifiers.minHeight
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import code.yousef.summon.runtime.LaunchedEffect
import code.yousef.summon.runtime.remember
import code.yousef.summon.runtime.rememberMutableStateOf
import code.yousef.summon.state.SummonMutableState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
                    ButtonLink(
                        label = ContactCopy.schedule.resolve(locale),
                        href = "mailto:yousef.baitalmal.dev@email.com",
                        dataHref = "mailto:yousef.baitalmal.dev@email.com",
                        dataAttributes = mapOf("cta" to "contact-email"),
                        modifier = Modifier()
                            .backgroundColor(PortfolioTheme.Colors.ACCENT)
                            .color("#ffffff")
                            .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
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
    val name = rememberMutableStateOf("")
    val email = rememberMutableStateOf("")
    val whatsapp = rememberMutableStateOf("")
    val requirements = rememberMutableStateOf("")
    val submitting = rememberMutableStateOf(false)
    val fieldErrors = rememberMutableStateOf<Map<String, String>>(emptyMap())
    val successMessage = rememberMutableStateOf<String?>(null)
    val failureMessage = rememberMutableStateOf<String?>(null)
    val pendingPayload = rememberMutableStateOf<ContactFormPayload?>(null)
    val submissionKey = rememberMutableStateOf(0)
    val httpClient = remember { createHttpClient() }

    LaunchedEffect(submissionKey.value) {
        val payload = pendingPayload.value ?: return@LaunchedEffect
        submitting.value = true
        try {
            val response = runCatching {
                httpClient.post(action, "application/json", payload.toMap())
            }.getOrElse {
                handleSubmitFailure(failureMessage, locale, it.message)
                return@LaunchedEffect
            }
            if (response.isSuccess) {
                handleSubmitSuccess(
                    locale = locale,
                    successMessage = successMessage,
                    failureMessage = failureMessage,
                    fieldErrors = fieldErrors,
                    name = name,
                    email = email,
                    whatsapp = whatsapp,
                    requirements = requirements
                )
            } else {
                handleHttpError(response, failureMessage, locale)
            }
        } finally {
            submitting.value = false
            pendingPayload.value = null
        }
    }

    Form(
        onSubmit = {
            val payload = ContactFormPayload(
                name = name.value,
                email = email.value.ifBlank { null },
                whatsapp = whatsapp.value,
                requirements = requirements.value
            )
            val validation = payload.validate(locale)
            if (validation.isNotEmpty()) {
                fieldErrors.value = validation
                failureMessage.value = null
                return@Form
            }
            fieldErrors.value = emptyMap()
            failureMessage.value = null
            successMessage.value = null
            pendingPayload.value = payload
            submissionKey.value = submissionKey.value + 1
        },
        modifier = Modifier()
            .attribute("action", action)
            .attribute("method", "post")
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
                .backgroundColor(PortfolioTheme.Colors.SURFACE)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .padding(PortfolioTheme.Spacing.lg)
        ) {
            successMessage.value?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier()
                        .backgroundColor("rgba(61, 213, 152, 0.12)")
                        .color(PortfolioTheme.Colors.SUCCESS)
                        .fontWeight(600)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.sm)
                )
            }
            failureMessage.value?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier()
                        .backgroundColor("rgba(255, 77, 77, 0.18)")
                        .color(PortfolioTheme.Colors.DANGER)
                        .fontWeight(600)
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .borderRadius(PortfolioTheme.Radii.sm)
                )
            }
            Text(
                text = ContactCopy.formTitle.resolve(locale),
                modifier = Modifier()
                    .fontSize(1.2.rem)
                    .fontWeight(600)
            )
            InputField(
                value = name,
                placeholder = ContactCopy.name.resolve(locale),
                error = fieldErrors.value["name"],
                required = true
            )
            InputField(
                value = email,
                placeholder = ContactCopy.email.resolve(locale),
                error = fieldErrors.value["email"],
                required = false
            )
            InputField(
                value = whatsapp,
                placeholder = ContactCopy.whatsapp.resolve(locale),
                error = fieldErrors.value["whatsapp"],
                required = false
            )
            TextAreaField(
                value = requirements,
                placeholder = ContactCopy.requirements.resolve(locale),
                error = fieldErrors.value["requirements"],
                minHeight = 160.px
            )
            SubmitButton(locale = locale, submitting = submitting.value)
        }
    }
}

@Composable
private fun InputField(
    value: SummonMutableState<String>,
    placeholder: String,
    error: String?,
    required: Boolean
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xs)
    ) {
        TextField(
            value.value,
            { value.value = it },
            Modifier()
                .width(100.percent)
                .padding(PortfolioTheme.Spacing.xs)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(if (error != null) PortfolioTheme.Colors.DANGER else PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.md),
            placeholder,
            "",
            TextFieldType.Text,
            false,
            false,
            required,
            emptyList()
        )
        error?.let {
            Text(
                text = it,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.DANGER)
                    .fontSize(0.85.rem)
            )
        }
    }
}

@Composable
private fun TextAreaField(
    value: SummonMutableState<String>,
    placeholder: String,
    error: String?,
    minHeight: String
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xs)
    ) {
        TextArea(
            value.value,
            { value.value = it },
            Modifier()
                .width(100.percent)
                .minHeight(minHeight)
                .padding(PortfolioTheme.Spacing.xs)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(if (error != null) PortfolioTheme.Colors.DANGER else PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.md),
            false,
            false,
            null,
            null,
            placeholder
        )
        error?.let {
            Text(
                text = it,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.DANGER)
                    .fontSize(0.85.rem)
            )
        }
    }
}

@Composable
private fun SubmitButton(locale: PortfolioLocale, submitting: Boolean) {
    val label = if (submitting) ContactCopy.submitting.resolve(locale) else ContactCopy.submit.resolve(locale)
    Button(
        onClick = null,
        label = label,
        modifier = Modifier()
            .backgroundColor(PortfolioTheme.Colors.ACCENT)
            .color("#ffffff")
            .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.xl)
            .borderRadius(PortfolioTheme.Radii.pill)
            .textAlign(TextAlign.Center)
            .buttonType(ButtonType.Submit),
        variant = ButtonVariant.PRIMARY,
        disabled = submitting,
        dataAttributes = mapOf("form" to "contact-submit")
    )
}

private data class ContactFormPayload(
    val name: String,
    val email: String?,
    val whatsapp: String,
    val requirements: String
) {
    fun validate(locale: PortfolioLocale): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = ContactCopy.errorName.resolve(locale)
        if (requirements.isBlank()) errors["requirements"] = ContactCopy.errorRequirements.resolve(locale)
        if (email?.isNotBlank() == true && !email.contains("@")) {
            errors["email"] = ContactCopy.errorEmail.resolve(locale)
        }
        return errors
    }

    fun toMap(): Map<String, String> = buildMap {
        put("name", name)
        if (!email.isNullOrBlank()) {
            put("email", email)
        }
        if (whatsapp.isNotBlank()) {
            put("whatsapp", whatsapp)
        }
        put("requirements", requirements)
    }
}

private fun handleSubmitSuccess(
    locale: PortfolioLocale,
    successMessage: SummonMutableState<String?>,
    failureMessage: SummonMutableState<String?>,
    fieldErrors: SummonMutableState<Map<String, String>>,
    name: SummonMutableState<String>,
    email: SummonMutableState<String>,
    whatsapp: SummonMutableState<String>,
    requirements: SummonMutableState<String>
) {
    successMessage.value = ContactCopy.success.resolve(locale)
    failureMessage.value = null
    fieldErrors.value = emptyMap()
    name.value = ""
    email.value = ""
    whatsapp.value = ""
    requirements.value = ""
}

private fun handleSubmitFailure(
    failureMessage: SummonMutableState<String?>,
    locale: PortfolioLocale,
    reason: String?
) {
    failureMessage.value = reason ?: ContactCopy.failure.resolve(locale)
}

private fun handleHttpError(
    response: HttpResponse,
    failureMessage: SummonMutableState<String?>,
    locale: PortfolioLocale
) {
    val body = response.body
    if (body.isNotBlank()) {
        runCatching {
            val json = Json.parseToJsonElement(body).jsonObject
            val error = json["error"]?.jsonPrimitive?.content
            error?.let {
                failureMessage.value = it
                return
            }
        }
    }
    failureMessage.value = ContactCopy.failure.resolve(locale)
}

private object ContactCopy {
    val title = LocalizedText("Collaborate", "تعاون")
    val subtitle = LocalizedText(
        en = "Tell me what you’re building. I’ll reply with a focused plan (and timelines) you can immediately act on.",
        ar = "أخبرني بما تعمل عليه وسأعود إليك بخطة واضحة وجدول زمني يمكن البدء به فورًا."
    )
    val schedule = LocalizedText("Schedule a call", "احجز مكالمة")
    val formTitle = LocalizedText("Project details", "تفاصيل المشروع")
    val name = LocalizedText("Name", "الاسم")
    val email = LocalizedText("Email (optional)", "البريد الإلكتروني (اختياري)")
    val whatsapp = LocalizedText("WhatsApp / Signal", "واتساب / سيجنال")
    val requirements = LocalizedText("What are we building?", "ماذا سنبني؟")
    val submit = LocalizedText("Send request", "أرسل الطلب")
    val submitting = LocalizedText("Sending...", "جاري الإرسال...")
    val success = LocalizedText("Thanks! I’ll reply soon.", "شكرًا! سأتواصل معك قريبًا.")
    val failure = LocalizedText("Something went wrong. Please try again.", "حدث خطأ. حاول مرة أخرى.")
    val errorName = LocalizedText("Name is required", "الاسم مطلوب")
    val errorEmail = LocalizedText("Please enter a valid email", "يرجى إدخال بريد صحيح")
    val errorRequirements = LocalizedText("Share a bit about your project", "شارك تفاصيل حول مشروعك")
}
