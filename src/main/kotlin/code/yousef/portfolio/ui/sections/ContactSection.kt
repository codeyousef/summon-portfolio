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
import code.yousef.summon.extensions.px
import code.yousef.summon.modifier.*
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
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.md)
                .backgroundColor(PortfolioTheme.Colors.surface)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.border)
                .borderRadius(PortfolioTheme.Radii.lg)
                .style("padding", PortfolioTheme.Spacing.lg)
        ) {
            successMessage.value?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier()
                        .backgroundColor("rgba(61, 213, 152, 0.12)")
                        .color(PortfolioTheme.Colors.success)
                        .style("font-weight", "600")
                        .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
                        .borderRadius(PortfolioTheme.Radii.sm)
                )
            }
            failureMessage.value?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier()
                        .backgroundColor("rgba(255, 77, 77, 0.18)")
                        .color(PortfolioTheme.Colors.danger)
                        .style("font-weight", "600")
                        .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
                        .borderRadius(PortfolioTheme.Radii.sm)
                )
            }
            Text(
                text = ContactCopy.formTitle.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "1.2rem")
                    .style("font-weight", "600")
            )
            TextField(
                value = name.value,
                onValueChange = { name.value = it },
                placeholder = ContactCopy.namePlaceholder.resolve(locale),
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "name")
                    .attribute("required", "required")
            )
            fieldErrors.value["name"]?.let { FieldError(it) }
            TextField(
                value = email.value,
                onValueChange = { email.value = it },
                placeholder = ContactCopy.emailPlaceholder.resolve(locale),
                type = TextFieldType.Email,
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "email")
            )
            fieldErrors.value["email"]?.let { FieldError(it) }
            TextField(
                value = whatsapp.value,
                onValueChange = { whatsapp.value = it },
                placeholder = ContactCopy.whatsappPlaceholder.resolve(locale),
                modifier = Modifier()
                    .style("width", "100%")
                    .attribute("name", "whatsapp")
                    .attribute("required", "required")
            )
            fieldErrors.value["whatsapp"]?.let { FieldError(it) }
            TextArea(
                value = requirements.value,
                onValueChange = { requirements.value = it },
                modifier = Modifier()
                    .style("width", "100%")
                    .style("min-height", 140.px)
                    .style("align-items", "flex-start")
                    .attribute("name", "requirements")
                    .attribute("required", "required"),
                placeholder = ContactCopy.requirementsPlaceholder.resolve(locale)
            )
            fieldErrors.value["requirements"]?.let { FieldError(it) }
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
                submitting.value,
                "",
                IconPosition.START
            )
        }
    }
}

@Composable
private fun FieldError(message: String) {
    Text(
        text = message,
        modifier = Modifier()
            .color(PortfolioTheme.Colors.danger)
            .style("font-size", "0.85rem")
            .style("font-weight", "600")
    )
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
    val validationName = LocalizedText("Please enter your name.", "يرجى إدخال اسمك.")
    val validationWhatsapp = LocalizedText("WhatsApp number is required.", "رقم الواتساب مطلوب.")
    val validationRequirements = LocalizedText("Tell me a bit about your project.", "يرجى توضيح متطلبات مشروعك.")
    val validationEmail = LocalizedText("Use a valid email address.", "يرجى إدخال بريد إلكتروني صالح.")
    val success = LocalizedText("Thanks! I’ll reply within 24 hours.", "شكرًا! سأرد خلال 24 ساعة.")
    val errorGeneric = LocalizedText("Something went wrong. Please try again.", "حدث خطأ ما. يرجى المحاولة مرة أخرى.")
}

private data class ContactFormPayload(
    val name: String,
    val email: String?,
    val whatsapp: String,
    val requirements: String
) {
    fun toMap(): Map<String, String> = buildMap {
        put("name", name)
        email?.let { put("email", it) }
        put("whatsapp", whatsapp)
        put("requirements", requirements)
    }

    fun validate(locale: PortfolioLocale): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) {
            errors["name"] = ContactCopy.validationName.resolve(locale)
        }
        if (!email.isNullOrBlank() && !email.contains("@")) {
            errors["email"] = ContactCopy.validationEmail.resolve(locale)
        }
        if (whatsapp.isBlank()) {
            errors["whatsapp"] = ContactCopy.validationWhatsapp.resolve(locale)
        }
        if (requirements.isBlank()) {
            errors["requirements"] = ContactCopy.validationRequirements.resolve(locale)
        }
        return errors
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

private fun handleHttpError(
    response: HttpResponse,
    failureMessage: SummonMutableState<String?>,
    locale: PortfolioLocale
) {
    val fallback = ContactCopy.errorGeneric.resolve(locale)
    val message = extractErrorMessage(response.body) ?: fallback
    failureMessage.value = message
}

private fun handleSubmitFailure(
    failureMessage: SummonMutableState<String?>,
    locale: PortfolioLocale,
    details: String?
) {
    failureMessage.value = details ?: ContactCopy.errorGeneric.resolve(locale)
}

private fun extractErrorMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        CONTACT_JSON.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()
}

private val CONTACT_JSON = Json { ignoreUnknownKeys = true }
