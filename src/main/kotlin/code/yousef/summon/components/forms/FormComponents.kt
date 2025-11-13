package code.yousef.summon.components.forms

import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Label
import code.yousef.summon.components.display.Markdown
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.input.Button
import code.yousef.summon.components.input.ButtonVariant
import code.yousef.summon.components.input.Checkbox
import code.yousef.summon.components.input.Select
import code.yousef.summon.components.input.TextArea
import code.yousef.summon.components.input.TextField
import code.yousef.summon.components.input.TextFieldType as SummonTextFieldType
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.runtime.LocalPlatformRenderer
import code.yousef.summon.runtime.SelectOption
import code.yousef.summon.runtime.rememberMutableStateOf
import kotlin.math.abs

enum class FormMethod(val value: String) {
    GET("get"),
    POST("post")
}

enum class FormTextFieldType(val value: String) {
    TEXT("text"),
    PASSWORD("password"),
    EMAIL("email"),
    NUMBER("number"),
    DATE("date"),
    URL("url"),
    TEL("tel"),
    SLUG("text");
}

data class FormSelectOption(
    val value: String,
    val label: String,
    val disabled: Boolean = false
)

data class FormHiddenField(
    val name: String,
    val value: String
)

enum class FormButtonType(val value: String) {
    SUBMIT("submit"),
    BUTTON("button"),
    RESET("reset")
}

enum class FormButtonTone {
    PRIMARY,
    ACCENT,
    DANGER,
    GHOST
}

@Composable
fun FormStyleSheet() {
    // No-op: layout + styling handled directly through modifiers.
}

@Composable
fun Form(
    action: String,
    method: FormMethod = FormMethod.POST,
    enctype: String? = null,
    hiddenFields: List<FormHiddenField> = emptyList(),
    modifier: Modifier = Modifier(),
    content: @Composable () -> Unit
) {
    val renderer = LocalPlatformRenderer.current
    var formModifier = modifier
        .attribute("action", action)
        .attribute("method", method.value)

    if (!enctype.isNullOrBlank()) {
        formModifier = formModifier.attribute("enctype", enctype)
    }

    renderer.renderForm(onSubmit = null, modifier = formModifier) {
        hiddenFields.forEach { HiddenFormField(it) }
        Column(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(280px, 1fr))")
                .gap("28px")
                .padding("24px 24px 32px")
        ) {
            content()
        }
    }
}

@Composable
fun FormTextField(
    name: String,
    label: String,
    defaultValue: String = "",
    type: FormTextFieldType = FormTextFieldType.TEXT,
    required: Boolean = false,
    optionalLabel: String? = "Optional",
    placeholder: String? = null,
    description: String? = null,
    helperText: String? = null,
    autoComplete: String? = null,
    inputMode: String? = null,
    readonly: Boolean = false,
    dataAttributes: Map<String, String> = emptyMap(),
    fullWidth: Boolean = false
) {
    val fieldId = rememberFieldId(name)
    val valueState = rememberMutableStateOf(defaultValue)
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        val modifier = baseInputModifier(fieldId, name, required, dataAttributes)
            .attributeIfNotNull("autocomplete", autoComplete)
            .attributeIfNotNull("inputmode", inputMode)
        TextField(
            value = valueState.value,
            onValueChange = { valueState.value = it },
            modifier = modifier,
            label = "",
            placeholder = placeholder.orEmpty(),
            type = type.toSummonType(),
            isError = false,
            isEnabled = true,
            isReadOnly = readonly,
            validators = emptyList()
        )
    }
}

@Composable
fun FormTextArea(
    name: String,
    label: String,
    defaultValue: String = "",
    required: Boolean = false,
    optionalLabel: String? = "Optional",
    placeholder: String? = null,
    description: String? = null,
    helperText: String? = null,
    minHeight: String = "160px",
    dataAttributes: Map<String, String> = emptyMap(),
    fullWidth: Boolean = true
) {
    val fieldId = rememberFieldId(name)
    val valueState = rememberMutableStateOf(defaultValue)
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        TextArea(
            valueState.value,
            { valueState.value = it },
            baseInputModifier(fieldId, name, required, dataAttributes).minHeight(minHeight),
            true,
            false,
            null,
            null,
            placeholder ?: ""
        )
    }
}

@Composable
fun FormSelect(
    name: String,
    label: String,
    options: List<FormSelectOption>,
    selectedValue: String? = null,
    required: Boolean = false,
    optionalLabel: String? = "Optional",
    description: String? = null,
    helperText: String? = null,
    fullWidth: Boolean = false
) {
    val fieldId = rememberFieldId(name)
    val selectOptions = options.map { SelectOption(it.value, it.label, it.disabled) }
    val initialSelection = selectedValue ?: selectOptions.firstOrNull()?.value
    val selectedState = rememberMutableStateOf<String?>(initialSelection)
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        Select(
            selectedState,
            selectOptions,
            { selectedState.value = it },
            "",
            "",
            baseInputModifier(fieldId, name, required, emptyMap()),
            true,
            false,
            selectOptions.size,
            emptyList()
        )
    }
}

@Composable
fun FormCheckbox(
    name: String,
    label: String,
    description: String? = null,
    checked: Boolean = false
) {
    val fieldId = rememberFieldId(name)
    val checkedState = rememberMutableStateOf(checked)
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap("8px")
            .gridColumn("1 / -1")
    ) {
        Checkbox(
            checked = checkedState.value,
            onCheckedChange = { checkedState.value = it },
            modifier = Modifier()
                .attribute("name", name)
                .attribute("value", "true")
                .id(fieldId)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap("10px"),
            enabled = true,
            label = label,
            isIndeterminate = false,
            validators = emptyList()
        )
        description?.let {
            Text(
                text = it,
                modifier = Modifier()
                    .color(SECONDARY_TEXT)
                    .fontSize("0.85rem")
            )
        }
    }
}

@Composable
fun FormButton(
    text: String,
    type: FormButtonType = FormButtonType.SUBMIT,
    tone: FormButtonTone = FormButtonTone.ACCENT,
    fullWidth: Boolean = false,
    dataAttributes: Map<String, String> = emptyMap()
) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .justifyContent(JustifyContent.FlexEnd)
            .gridColumn("1 / -1")
            .let { base -> if (fullWidth) base.width("100%") else base }
    ) {
        Button(
            onClick = {},
            label = text,
            modifier = buttonModifier(fullWidth, tone)
                .attribute("type", type.value)
                .applyDataAttributes(dataAttributes),
            variant = tone.toButtonVariant(),
            disabled = false
        )
    }
}

@Composable
fun MarkdownEditorField(
    name: String,
    label: String,
    defaultValue: String = "",
    required: Boolean = false,
    optionalLabel: String? = "Optional",
    placeholder: String? = null,
    description: String? = null,
    showPreview: Boolean = true,
    previewLabel: String = "Preview"
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap("12px")
            .gridColumn("1 / -1")
    ) {
        FormTextArea(
            name = name,
            label = label,
            defaultValue = defaultValue,
            required = required,
            optionalLabel = optionalLabel,
            placeholder = placeholder,
            description = description
        )
        if (showPreview) {
            Text(
                text = previewLabel,
                modifier = Modifier()
                    .fontWeight(600)
                    .fontSize("0.9rem")
                    .color(SECONDARY_TEXT)
            )
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap("8px")
                    .padding("16px")
                    .backgroundColor("rgba(255,255,255,0.02)")
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor("rgba(255,255,255,0.08)")
                    .borderRadius("16px")
            ) {
                val markdown = if (defaultValue.isBlank()) "_Nothing to preview yet._" else defaultValue
                Markdown(
                    markdownContent = markdown,
                    modifier = Modifier().color(PRIMARY_TEXT)
                )
            }
        }
    }
}

@Composable
private fun FormFieldGroup(
    fieldId: String,
    label: String,
    required: Boolean,
    description: String?,
    optionalLabel: String?,
    helperText: String?,
    fullWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap("12px")
            .let { base -> if (fullWidth) base.gridColumn("1 / -1") else base }
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
        ) {
            Label(
                text = if (required) "$label *" else label,
                modifier = Modifier()
                    .fontWeight(600)
                    .color(PRIMARY_TEXT),
                forElement = fieldId
            )
            if (!required) {
                optionalLabel?.let {
                    Text(
                        text = it,
                        modifier = Modifier()
                            .fontSize("0.75rem")
                            .color(SECONDARY_TEXT)
                    )
                }
            }
        }
        description?.let {
            Text(
                text = it,
                modifier = Modifier()
                    .color(SECONDARY_TEXT)
                    .fontSize("0.85rem")
            )
        }
        content()
        helperText?.let {
            Text(
                text = it,
                modifier = Modifier()
                    .color(SECONDARY_TEXT)
                    .fontSize("0.8rem")
            )
        }
    }
}

@Composable
private fun HiddenFormField(field: FormHiddenField) {
    val state = rememberMutableStateOf(field.value)
    TextField(
        value = state.value,
        onValueChange = { state.value = it },
        modifier = Modifier()
            .attribute("name", field.name)
            .display(Display.None),
        label = "",
        placeholder = "",
        type = SummonTextFieldType.Text,
        isError = false,
        isEnabled = true,
        isReadOnly = true,
        validators = emptyList()
    )
}

private fun FormTextFieldType.toSummonType(): SummonTextFieldType =
    when (this) {
        FormTextFieldType.TEXT, FormTextFieldType.SLUG -> SummonTextFieldType.Text
        FormTextFieldType.PASSWORD -> SummonTextFieldType.Password
        FormTextFieldType.EMAIL -> SummonTextFieldType.Email
        FormTextFieldType.NUMBER -> SummonTextFieldType.Number
        FormTextFieldType.DATE -> SummonTextFieldType.Date
        FormTextFieldType.URL -> SummonTextFieldType.Url
        FormTextFieldType.TEL -> SummonTextFieldType.Tel
    }

private fun Modifier.baseInputStyling(): Modifier =
    this
        .width("100%")
        .padding("12px 10px")
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor("rgba(255,255,255,0.1)")
        .borderRadius("16px")
        .backgroundColor("rgba(255,255,255,0.04)")
        .color(PRIMARY_TEXT)
        .fontSize("1rem")

private fun baseInputModifier(
    fieldId: String,
    name: String,
    required: Boolean,
    dataAttributes: Map<String, String>
): Modifier {
    var modifier = Modifier()
        .id(fieldId)
        .attribute("name", name)
        .baseInputStyling()
    if (required) {
        modifier = modifier.attribute("required", "required")
    }
    modifier = modifier.applyDataAttributes(dataAttributes)
    return modifier
}

private fun Modifier.applyDataAttributes(data: Map<String, String>): Modifier {
    var current = this
    data.forEach { (key, value) ->
        current = current.attribute("data-$key", value)
    }
    return current
}

private fun Modifier.attributeIfNotNull(attribute: String, value: String?): Modifier =
    if (value.isNullOrBlank()) this else this.attribute(attribute, value)

private fun buttonModifier(fullWidth: Boolean, tone: FormButtonTone): Modifier {
    var base = Modifier()
        .padding("14px 24px")
        .borderRadius("22px")
        .fontWeight(600)
        .color(PRIMARY_TEXT)
        .attribute("data-form-button", tone.name.lowercase())
    if (fullWidth) {
        base = base.width("100%")
    }
    base = when (tone) {
        FormButtonTone.PRIMARY -> base
            .backgroundColor("linear-gradient(120deg,#2c2f41,#3b3f55)")
            .borderWidth(0)
        FormButtonTone.ACCENT -> base
            .backgroundColor("linear-gradient(120deg,#ff3b6a,#b01235)")
            .borderWidth(0)
        FormButtonTone.DANGER -> base
            .backgroundColor("transparent")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("rgba(255,122,122,0.6)")
        FormButtonTone.GHOST -> base
            .backgroundColor("transparent")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("rgba(255,255,255,0.12)")
    }
    return base
}

private fun FormButtonTone.toButtonVariant(): ButtonVariant =
    when (this) {
        FormButtonTone.PRIMARY -> ButtonVariant.PRIMARY
        FormButtonTone.ACCENT -> ButtonVariant.PRIMARY
        FormButtonTone.DANGER -> ButtonVariant.DANGER
        FormButtonTone.GHOST -> ButtonVariant.GHOST
    }

private fun rememberFieldId(name: String): String {
    val base = name.lowercase().replace("""[^a-z0-9_-]""".toRegex(), "-")
    val suffix = abs(name.hashCode()).toString(16).take(4)
    return "field-$base-$suffix"
}

private const val PRIMARY_TEXT = "#eaeaf0"
private const val SECONDARY_TEXT = "#a7a7b3"
