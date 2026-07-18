package code.yousef.portfolio.ui.photography

import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.PhotographySourceKind
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormEncType
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormSelect
import codes.yousef.summon.components.forms.FormSelectOption
import codes.yousef.summon.components.forms.FormTextArea
import codes.yousef.summon.components.forms.FormTextField
import codes.yousef.summon.components.input.FormField
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.runtime.LocalPlatformRenderer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun PhotographyAdminPage(
    photos: List<PhotographyPhoto>,
    errorMessage: String?,
    successMessage: String?
) {
    Column(
        modifier = Modifier()
            .minHeight("100vh")
            .backgroundColor("#0d1117")
            .color("#e6edf3")
            .fontFamily("ui-sans-serif, system-ui, sans-serif")
            .padding(28.px)
            .gap(28.px)
            .alignItems(AlignItems.FlexStart)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap(16.px)
                .width(100.percent)
                .maxWidth(1180.px)
        ) {
            Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(6.px)) {
                Text(text = "Photography Admin", modifier = Modifier().fontSize(2.rem).fontWeight(700))
                Text(
                    text = "Upload and manage the public photography and motion page.",
                    modifier = Modifier().color("#8b949e").fontSize(0.95.rem)
                )
            }
            Row(modifier = Modifier().display(Display.Flex).gap(10.px)) {
                AdminLink("View page", "/photography")
                AdminLink("Admin home", "/admin")
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Notice(text = errorMessage, background = "#3d1117", border = "#da3633", color = "#ffd8d3")
        }
        if (!successMessage.isNullOrBlank()) {
            Notice(text = successMessage, background = "#0f2f1d", border = "#238636", color = "#d4f8df")
        }

        UploadPanel()

        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(16.px)
                .width(100.percent)
                .maxWidth(1180.px)
        ) {
            Text(text = "Photos", modifier = Modifier().fontSize(1.2.rem).fontWeight(700))
            if (photos.isEmpty()) {
                Text(
                    text = "No uploaded photos yet.",
                    modifier = Modifier().color("#8b949e")
                )
            } else {
                photos.forEach { photo -> AdminPhotoRow(photo) }
            }
        }
    }
}

@Composable
private fun UploadPanel() {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(18.px)
            .padding(20.px)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#30363d")
            .borderRadius(6.px)
            .backgroundColor("#161b22")
            .width(100.percent)
            .maxWidth(760.px)
    ) {
        Text(text = "Create media", modifier = Modifier().fontSize(1.2.rem).fontWeight(700))
        Form(
            action = "/admin/photography",
            method = FormMethod.Post,
            encType = FormEncType.Multipart,
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(14.px)
                .width(100.percent)
        ) {
            TextInputField("Title", "title", "", required = true)
            TextInputField("Alt text", "altText", "", required = true)
            TextAreaField("Caption", "caption", "")
            Row(modifier = Modifier().display(Display.Flex).gap(14.px).flexWrap(FlexWrap.Wrap)) {
                SelectField(
                    label = "Media type",
                    name = "mediaType",
                    value = PhotographyMediaType.PHOTO.name,
                    options = mediaTypeOptions(),
                    modifier = Modifier().flex(grow = 1, shrink = 1, basis = "220px")
                )
                SelectField(
                    label = "Source",
                    name = "sourceKind",
                    value = PhotographySourceKind.UPLOAD.name,
                    options = sourceKindOptions(),
                    modifier = Modifier().flex(grow = 1, shrink = 1, basis = "180px")
                )
            }
            Row(modifier = Modifier().display(Display.Flex).gap(14.px).flexWrap(FlexWrap.Wrap)) {
                TextInputField("Category", "category", "Uncategorized", required = false, modifier = Modifier().flex(grow = 1, shrink = 1, basis = "220px"))
                TextInputField("Album", "albumTitle", "", required = false, modifier = Modifier().flex(grow = 1, shrink = 1, basis = "220px"))
            }
            TextInputField("External URL", "externalUrl", "", required = false)
            TextInputField("Thumbnail URL", "thumbnailUrl", "", required = false)
            Row(modifier = Modifier().display(Display.Flex).gap(14.px).flexWrap(FlexWrap.Wrap)) {
                NativeInputField("Taken at", "date", "takenAt", "", modifier = Modifier().flex(grow = 1, shrink = 1, basis = "220px"))
                NativeInputField("Order", "number", "order", "0", modifier = Modifier().flex(grow = 1, shrink = 1, basis = "160px"))
            }
            FileInputField("Upload file", "photo", "image/jpeg,image/png,image/webp,video/mp4,video/webm,video/quicktime", required = false)
            CheckboxField("Publish now", "published", checked = true)
            CheckboxField("Feature", "featured", checked = false)
            FormButton(text = "Save media", modifier = PrimaryAdminButtonModifier())
        }
    }
}

@Composable
private fun AdminPhotoRow(photo: PhotographyPhoto) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .gap(18.px)
            .alignItems(AlignItems.FlexStart)
            .padding(16.px)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#30363d")
            .borderRadius(6.px)
            .backgroundColor("#161b22")
            .flexWrap(FlexWrap.Wrap)
            .width(100.percent)
    ) {
        AdminMediaPreview(photo)
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(12.px)
                .flex(grow = 1, shrink = 1, basis = "320px")
                .maxWidth(760.px)
        ) {
            Form(
                action = "/admin/photography/${photo.id}",
                method = FormMethod.Post,
                encType = FormEncType.Multipart,
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(12.px)
                    .width(100.percent)
            ) {
                TextInputField("Title", "title", photo.title, required = true)
                TextInputField("Alt text", "altText", photo.altText, required = true)
                TextAreaField("Caption", "caption", photo.caption.orEmpty())
                Row(modifier = Modifier().display(Display.Flex).gap(12.px).flexWrap(FlexWrap.Wrap)) {
                    SelectField(
                        label = "Media type",
                        name = "mediaType",
                        value = photo.mediaType.name,
                        options = mediaTypeOptions(),
                        modifier = Modifier().flex(grow = 1, shrink = 1, basis = "180px")
                    )
                    SelectField(
                        label = "Source",
                        name = "sourceKind",
                        value = photo.sourceKind.name,
                        options = sourceKindOptions(),
                        modifier = Modifier().flex(grow = 1, shrink = 1, basis = "160px")
                    )
                }
                Row(modifier = Modifier().display(Display.Flex).gap(12.px).flexWrap(FlexWrap.Wrap)) {
                    TextInputField("Category", "category", photo.category, required = false, modifier = Modifier().flex(grow = 1, shrink = 1, basis = "180px"))
                    TextInputField("Album", "albumTitle", photo.albumTitle.orEmpty(), required = false, modifier = Modifier().flex(grow = 1, shrink = 1, basis = "180px"))
                }
                TextInputField("External URL", "externalUrl", photo.externalUrl.orEmpty(), required = false)
                TextInputField("Thumbnail URL", "thumbnailUrl", photo.thumbnailUrl.orEmpty(), required = false)
                Row(modifier = Modifier().display(Display.Flex).gap(12.px).flexWrap(FlexWrap.Wrap)) {
                    NativeInputField("Taken at", "date", "takenAt", photo.takenAt?.toString().orEmpty(), modifier = Modifier().flex(grow = 1, shrink = 1, basis = "180px"))
                    NativeInputField("Order", "number", "order", photo.order.toString(), modifier = Modifier().flex(grow = 1, shrink = 1, basis = "120px"))
                }
                FileInputField("Replace upload", "photo", "image/jpeg,image/png,image/webp,video/mp4,video/webm,video/quicktime", required = false)
                CheckboxField("Published", "published", checked = photo.published)
                CheckboxField("Featured", "featured", checked = photo.featured)
                FormButton(text = "Save", modifier = SecondaryAdminButtonModifier())
            }
            Form(
                action = "/admin/photography/${photo.id}/delete",
                method = FormMethod.Post,
                encType = FormEncType.UrlEncoded
            ) {
                FormButton(
                    text = "Delete",
                    modifier = DangerAdminButtonModifier()
                )
            }
            Text(
                text = "${photo.mediaType.label()} · ${photo.sourceKind.label()} · ${photo.category} · ${photo.contentType} · ${photo.sizeBytes / 1024} KB",
                modifier = Modifier().fontSize(0.78.rem).color("#8b949e")
            )
        }
    }
}

@Composable
private fun AdminMediaPreview(photo: PhotographyPhoto) {
    val previewSrc = when {
        !photo.thumbnailUrl.isNullOrBlank() -> photo.thumbnailUrl
        photo.sourceKind == PhotographySourceKind.UPLOAD && photo.mediaType == PhotographyMediaType.PHOTO -> photo.uploadAssetHref()
        else -> null
    }
    if (previewSrc != null) {
        Image(
            src = previewSrc,
            alt = photo.altText,
            modifier = Modifier()
                .width(180.px)
                .height(132.px)
                .objectFit(ObjectFit.Cover)
                .backgroundColor("#0d1117")
                .borderRadius(4.px)
                .attribute("loading", "lazy")
        )
    } else {
        Box(
            modifier = Modifier()
                .width(180.px)
                .height(132.px)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .backgroundColor("#0d1117")
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor("#30363d")
                .borderRadius(4.px)
        ) {
            Text(text = photo.mediaType.label(), modifier = Modifier().color("#8b949e").fontSize(0.85.rem))
        }
    }
}

@Composable
private fun TextInputField(
    label: String,
    name: String,
    value: String,
    required: Boolean,
    modifier: Modifier = Modifier()
) {
    FormField(label = { AdminLabel(label) }, isRequired = required, modifier = modifier) {
        FormTextField(
            name = name,
            label = "",
            defaultValue = value,
            modifier = InputModifier().let { if (required) it.attribute("required", "required") else it }
        )
    }
}

@Composable
private fun SelectField(
    label: String,
    name: String,
    value: String,
    options: List<SelectOption>,
    modifier: Modifier = Modifier()
) {
    FormSelect(
        name = name,
        label = label,
        options = options.map { option ->
            FormSelectOption(value = option.value, label = option.label)
        },
        selectedValue = value,
        modifier = modifier
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(6.px)
            .marginBottom(0.px),
        fieldModifier = InputModifier()
    )
}

@Composable
private fun TextAreaField(label: String, name: String, value: String) {
    FormField(label = { AdminLabel(label) }, isRequired = false) {
        FormTextArea(
            name = name,
            label = "",
            defaultValue = value,
            required = false,
            modifier = InputModifier().minHeight(96.px)
        )
    }
}

@Composable
private fun NativeInputField(label: String, type: String, name: String, value: String, modifier: Modifier = Modifier()) {
    Column(modifier = modifier.display(Display.Flex).flexDirection(FlexDirection.Column).gap(6.px)) {
        AdminLabel(label)
        LocalPlatformRenderer.current.renderNativeInput(
            type = type,
            modifier = InputModifier()
                .attribute("name", name),
            value = value
        )
    }
}

@Composable
private fun FileInputField(label: String, name: String, accept: String, required: Boolean) {
    Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(6.px)) {
        AdminLabel(label)
        var modifier = InputModifier()
            .attribute("name", name)
            .attribute("accept", accept)
        if (required) modifier = modifier.attribute("required", "required")
        LocalPlatformRenderer.current.renderNativeInput(
            type = "file",
            modifier = modifier,
            value = ""
        )
    }
}

@Composable
private fun CheckboxField(label: String, name: String, checked: Boolean) {
    Row(modifier = Modifier().display(Display.Flex).alignItems(AlignItems.Center).gap(8.px)) {
        var modifier = Modifier()
            .width(18.px)
            .height(18.px)
            .attribute("name", name)
        if (checked) modifier = modifier.attribute("checked", "checked")
        LocalPlatformRenderer.current.renderNativeInput(
            type = "checkbox",
            modifier = modifier,
            value = "on"
        )
        AdminLabel(label)
    }
}

@Composable
private fun AdminLabel(label: String) {
    Text(text = label, modifier = Modifier().fontSize(0.9.rem).fontWeight(600).color("#c9d1d9"))
}

@Composable
private fun AdminLink(label: String, href: String) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .height(38.px)
            .padding(0.px, 14.px)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#30363d")
            .borderRadius(6.px)
            .color("#e6edf3")
            .textDecoration(TextDecoration.None),
        navigationMode = LinkNavigationMode.Native
    )
}

@Composable
private fun Notice(text: String, background: String, border: String, color: String) {
    Box(
        modifier = Modifier()
            .backgroundColor(background)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(border)
            .borderRadius(6.px)
            .padding(12.px, 14.px)
    ) {
        Text(text = text, modifier = Modifier().color(color))
    }
}

private fun InputModifier(): Modifier =
    Modifier()
        .width(100.percent)
        .padding(10.px, 12.px)
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor("#30363d")
        .borderRadius(6.px)
        .backgroundColor("#0d1117")
        .color("#e6edf3")

private fun PrimaryAdminButtonModifier(): Modifier =
    Modifier()
        .backgroundColor("#238636")
        .color("#ffffff")
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor("#2ea043")
        .borderRadius(6.px)
        .padding(10.px, 16.px)
        .fontWeight(700)

private fun SecondaryAdminButtonModifier(): Modifier =
    Modifier()
        .backgroundColor("#21262d")
        .color("#e6edf3")
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor("#30363d")
        .borderRadius(6.px)
        .padding(9.px, 14.px)
        .fontWeight(700)

private fun DangerAdminButtonModifier(): Modifier =
    Modifier()
        .backgroundColor("#3d1117")
        .color("#ffd8d3")
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor("#da3633")
        .borderRadius(6.px)
        .padding(9.px, 14.px)
        .fontWeight(700)

private data class SelectOption(val value: String, val label: String)

private fun mediaTypeOptions(): List<SelectOption> =
    PhotographyMediaType.values().map { SelectOption(it.name, it.label()) }

private fun sourceKindOptions(): List<SelectOption> =
    PhotographySourceKind.values().map { SelectOption(it.name, it.label()) }

private fun PhotographyMediaType.label(): String =
    when (this) {
        PhotographyMediaType.PHOTO -> "Photo"
        PhotographyMediaType.VIDEO -> "Video"
        PhotographyMediaType.VIDEO_360 -> "360 Video"
    }

private fun PhotographySourceKind.label(): String =
    when (this) {
        PhotographySourceKind.UPLOAD -> "Upload"
        PhotographySourceKind.EXTERNAL -> "External"
    }

private fun PhotographyPhoto.uploadAssetHref(): String =
    "/uploads/photography/${uploadAssetRef().urlPathSegment()}"

private fun PhotographyPhoto.uploadAssetRef(): String =
    storageKey.replace('\\', '/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: id

private fun String.urlPathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")
