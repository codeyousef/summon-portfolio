package code.yousef.summon.components.forms

import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Label
import code.yousef.summon.components.display.Markdown
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.runtime.LocalPlatformRenderer
import kotlin.math.abs

/**
 * HTTP method for form submissions.
 */
enum class FormMethod(val value: String) {
    GET("get"),
    POST("post")
}

/**
 * Supported input types for [FormTextField].
 */
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

/**
 * Data model for `<select>` options.
 */
data class FormSelectOption(
    val value: String,
    val label: String,
    val disabled: Boolean = false
)

/**
 * Represents a hidden input that should be emitted at the start of a form.
 */
data class FormHiddenField(
    val name: String,
    val value: String
)

/**
 * Submit/reset button type attribute.
 */
enum class FormButtonType(val value: String) {
    SUBMIT("submit"),
    BUTTON("button"),
    RESET("reset")
}

/**
 * Visual tone for [FormButton].
 */
enum class FormButtonTone {
    PRIMARY,
    ACCENT,
    DANGER,
    GHOST
}

/**
 * Injects form-specific CSS classes. Safe to call multiple times per page.
 */
@Composable
fun FormStyleSheet() {
    RawHtml(
        """
        <style>
          .summon-form-stack {
            display: flex;
            flex-direction: column;
            gap: 28px;
            padding: 24px 24px 32px;
            box-sizing: border-box;
          }
          @media (min-width: 900px) {
            .summon-form-stack {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            }
            .summon-form-group.full-width {
              grid-column: 1 / -1;
            }
          }
          .summon-form-group {
            display: flex;
            flex-direction: column;
            gap: 12px;
          }
          .summon-form-control,
          .summon-form-select,
          .summon-form-textarea {
            width: 100%;
            padding: 18px 20px;
            border-radius: 16px;
            border: 1px solid rgba(255,255,255,0.1);
            background: rgba(255,255,255,0.04);
            color: #eaeaf0;
            font-size: 1rem;
            font-family: inherit;
            transition: border-color 180ms ease, box-shadow 180ms ease;
          }
          .summon-form-control:focus,
          .summon-form-select:focus,
          .summon-form-textarea:focus {
            border-color: rgba(255,255,255,0.35);
            outline: none;
            box-shadow: 0 0 0 2px rgba(255,255,255,0.08);
          }
          .summon-form-checkbox-label {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 0.95rem;
            color: #eaeaf0;
          }
          .summon-form-checkbox {
            width: 18px;
            height: 18px;
            border-radius: 6px;
          }
          .summon-form-button {
            border: none;
            border-radius: 22px;
            padding: 14px 24px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 160ms ease, box-shadow 160ms ease;
            margin-top: 8px;
          }
          .summon-form-button:hover {
            transform: translateY(-1px);
          }
          .summon-form-button:focus {
            outline: none;
            box-shadow: 0 0 0 2px rgba(255,255,255,0.12);
          }
          .summon-form-button--primary {
            background: linear-gradient(120deg,#2c2f41,#3b3f55);
            color: #f0f0f5;
          }
          .summon-form-button--accent {
            background: linear-gradient(120deg,#ff3b6a,#b01235);
            color: #ffffff;
          }
          .summon-form-button--danger {
            background: transparent;
            color: #ff7a7a;
            border: 1px solid rgba(255,122,122,0.6);
          }
          .summon-form-button--ghost {
            background: transparent;
            color: #eaeaf0;
            border: 1px solid rgba(255,255,255,0.12);
          }
          .summon-form-actions {
            display: flex;
            justify-content: flex-end;
            gap: 12px;
          }
          .summon-form-actions.full-width {
            justify-content: stretch;
          }
        </style>
        """.trimIndent()
    )
}

/**
 * Form container that emits semantic `<form>` markup without requiring hydration.
 */
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
        hiddenFields.forEach { hidden ->
            RawHtml(
                """<input type="hidden" name="${hidden.name.htmlEscape()}" value="${hidden.value.htmlEscape()}">"""
            )
        }

        Column(
            modifier = Modifier()
                .addFormClass("summon-form-stack")
        ) {
            content()
        }
    }
}

/**
 * General-purpose text field with label + helper support.
 */
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
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        RawHtml(
            buildString {
                append("<input")
                append(" class=\"summon-form-control\"")
                append(" id=\"$fieldId\"")
                append(" name=\"${name.htmlEscape()}\"")
                append(" type=\"${type.value}\"")
                append(" value=\"${defaultValue.htmlEscape()}\"")
                if (required) append(" required")
                if (!placeholder.isNullOrBlank()) {
                    append(" placeholder=\"${placeholder.htmlEscape()}\"")
                }
                if (!autoComplete.isNullOrBlank()) {
                    append(" autocomplete=\"${autoComplete.htmlEscape()}\"")
                }
                if (!inputMode.isNullOrBlank()) {
                    append(" inputmode=\"${inputMode.htmlEscape()}\"")
                }
                if (readonly) append(" readonly")
                dataAttributes.forEach { (key, value) ->
                    append(" data-${key.htmlEscape()}=\"${value.htmlEscape()}\"")
                }
                append(">")
            }
        )
    }
}

/**
 * Multi-line form control for longer responses.
 */
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
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        RawHtml(
            buildString {
                append("<textarea")
                append(" class=\"summon-form-textarea\"")
                append(" id=\"$fieldId\"")
                append(" name=\"${name.htmlEscape()}\"")
                append(" style=\"min-height:$minHeight\"")
                if (required) append(" required")
                if (!placeholder.isNullOrBlank()) {
                    append(" placeholder=\"${placeholder.htmlEscape()}\"")
                }
                dataAttributes.forEach { (key, value) ->
                    append(" data-${key.htmlEscape()}=\"${value.htmlEscape()}\"")
                }
                append(">")
                append(defaultValue.htmlEscape())
                append("</textarea>")
            }
        )
    }
}

/**
 * Select element for enumerated choices.
 */
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
    FormFieldGroup(
        fieldId = fieldId,
        label = label,
        required = required,
        description = description,
        optionalLabel = optionalLabel,
        helperText = helperText,
        fullWidth = fullWidth
    ) {
        RawHtml(
            buildString {
                append("<select")
                append(" class=\"summon-form-select\"")
                append(" id=\"$fieldId\"")
                append(" name=\"${name.htmlEscape()}\"")
                if (required) append(" required")
                append(">")
                options.forEach { option ->
                    append("<option value=\"${option.value.htmlEscape()}\"")
                    if (selectedValue != null && option.value == selectedValue) {
                        append(" selected")
                    }
                    if (option.disabled) append(" disabled")
                    append(">")
                    append(option.label.htmlEscape())
                    append("</option>")
                }
                append("</select>")
            }
        )
    }
}

/**
 * Checkbox input with inline helper text.
 */
@Composable
fun FormCheckbox(
    name: String,
    label: String,
    checked: Boolean = false,
    description: String? = null
) {
    val fieldId = rememberFieldId(name)
    Column(
        modifier = Modifier()
            .addFormClass("summon-form-group full-width")
    ) {
        RawHtml(
            buildString {
                append("<label class=\"summon-form-checkbox-label\" for=\"$fieldId\">")
                append("<input type=\"checkbox\" class=\"summon-form-checkbox\"")
                append(" id=\"$fieldId\"")
                append(" name=\"${name.htmlEscape()}\"")
                if (checked) append(" checked")
                append(">")
                append(label.htmlEscape())
                append("</label>")
            }
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

/**
 * Semantic button designed for classic form submissions.
 */
@Composable
fun FormButton(
    text: String,
    type: FormButtonType = FormButtonType.SUBMIT,
    tone: FormButtonTone = FormButtonTone.ACCENT,
    fullWidth: Boolean = false,
    dataAttributes: Map<String, String> = emptyMap()
) {
    val toneClass = when (tone) {
        FormButtonTone.PRIMARY -> "summon-form-button--primary"
        FormButtonTone.ACCENT -> "summon-form-button--accent"
        FormButtonTone.DANGER -> "summon-form-button--danger"
        FormButtonTone.GHOST -> "summon-form-button--ghost"
    }
    RawHtml(
        buildString {
            val actionsClass =
                if (fullWidth) "summon-form-group full-width summon-form-actions full-width" else "summon-form-group full-width summon-form-actions"
            append("<div class=\"$actionsClass\">")
            append("<button type=\"${type.value}\"")
            append(" class=\"summon-form-button $toneClass\"")
            if (fullWidth) {
                append(" style=\"width:100%\"")
            }
            dataAttributes.forEach { (key, value) ->
                append(" data-${key.htmlEscape()}=\"${value.htmlEscape()}\"")
            }
            append(">")
            append(text.htmlEscape())
            append("</button>")
            append("</div>")
        }
    )
}

/**
 * Minimal markdown editor used for blog content authoring.
 * Renders a textarea plus optional preview rendered via [Markdown].
 */
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
            .addFormClass("summon-form-group full-width")
            .gap("12px")
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
            .addFormClass(
                buildString {
                    append("summon-form-group")
                    if (fullWidth) append(" full-width")
                }
            )
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

private fun Modifier.addFormClass(className: String): Modifier =
    this.attribute("class", className)

private fun rememberFieldId(name: String): String {
    val base = name.lowercase().replace("""[^a-z0-9_-]""".toRegex(), "-")
    val suffix = abs(name.hashCode()).toString(16).take(4)
    return "field-$base-$suffix"
}

private fun String.htmlEscape(): String =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private const val PRIMARY_TEXT = "#eaeaf0"
private const val SECONDARY_TEXT = "#a7a7b3"
