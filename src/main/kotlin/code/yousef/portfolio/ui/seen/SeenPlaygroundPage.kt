package code.yousef.portfolio.ui.seen

import code.yousef.portfolio.seen.SEEN_PLAYGROUND_MAX_CODE_LENGTH
import code.yousef.portfolio.seen.SeenPlaygroundCatalog
import code.yousef.portfolio.seen.SeenPlaygroundViewState
import code.yousef.portfolio.ssr.seenDocsBaseUrl
import code.yousef.portfolio.ui.components.ContextNavigationIds
import code.yousef.portfolio.ui.components.GlobalNavigationDestination
import code.yousef.portfolio.ui.components.LandingBranding
import code.yousef.portfolio.ui.components.SiteNavigation
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormButtonVariant
import codes.yousef.summon.components.forms.FormHiddenField
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormSelect
import codes.yousef.summon.components.forms.FormSelectOption
import codes.yousef.summon.components.forms.FormTextArea
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.StyleElement
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StylePseudoElement
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.i18n.LayoutDirection
import codes.yousef.summon.modifier.*

private const val PLAYGROUND_BORDER = "#30363d"
private const val PLAYGROUND_BACKGROUND = "#0d1117"
private const val PLAYGROUND_SURFACE = "#161b22"
private const val PLAYGROUND_TEXT = "#e6edf3"
private const val PLAYGROUND_MUTED = "#8b949e"
private const val PLAYGROUND_ACCENT = "#58a6ff"
private const val PLAYGROUND_MONO = "JetBrains Mono, Fira Code, Cascadia Code, ui-monospace, monospace"

@Composable
fun SeenPlaygroundPage(
    state: SeenPlaygroundViewState,
    packagesUrl: String? = null,
) {
    val docsUrl = seenDocsBaseUrl()
    SeenPlaygroundStyleSheet()

    Column(
        modifier = Modifier()
            .width(100.percent)
            .minHeight(100.vh)
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .backgroundColor(PLAYGROUND_BACKGROUND)
            .color(PLAYGROUND_TEXT),
    ) {
        SiteNavigation(
            activeDestination = GlobalNavigationDestination.ECOSYSTEM,
            context = LandingBranding.seen(
                docsUrl = docsUrl,
                apiReferenceUrl = "${docsUrl.trimEnd('/')}/api-reference",
                packagesUrl = packagesUrl,
                playgroundUrl = "/playground",
            ).navigationContext(ContextNavigationIds.PLAYGROUND),
            compact = true,
            showLocale = false,
        )

        PlaygroundToolbar(state)
        PlaygroundWorkspace(state)

        Box(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .padding(8.px, 16.px)
                .backgroundColor(PLAYGROUND_SURFACE)
                .border(BorderSide.Top, 1, BorderStyle.Solid, PLAYGROUND_BORDER)
                .fontSize(0.75.rem)
                .color("#6e7681"),
        ) {
            Text(text = "Powered by the self-hosted Seen compiler and a server-native Summon/Aether flow")
        }
    }
}

@Composable
private fun PlaygroundToolbar(state: SeenPlaygroundViewState) {
    Row(
        modifier = Modifier()
            .className("seen-playground-toolbar")
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .padding(12.px, 18.px)
            .backgroundColor(PLAYGROUND_SURFACE)
            .border(BorderSide.Bottom, 1, BorderStyle.Solid, PLAYGROUND_BORDER)
            .gap(16.px),
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(12.px),
        ) {
            Image(
                src = "/static/seen-logo.png",
                alt = "Seen",
                modifier = Modifier().width(28.px).height(28.px),
            )
            Column(modifier = Modifier().display(Display.Flex).flexDirection(FlexDirection.Column).gap(2.px)) {
                Text(
                    text = "Seen Playground",
                    modifier = Modifier().fontSize(1.1.rem).fontWeight(700).color(PLAYGROUND_TEXT),
                )
                Text(
                    text = "Native form, server execution",
                    modifier = Modifier().fontSize(0.75.rem).color(PLAYGROUND_MUTED),
                )
            }
        }

        Form(
            action = "/playground",
            method = FormMethod.Get,
            modifier = Modifier()
                .className("seen-playground-sample-form")
                .display(Display.Flex)
                .alignItems(AlignItems.FlexEnd)
                .gap(10.px),
        ) {
            FormSelect(
                name = "language",
                label = "Language",
                options = SeenPlaygroundCatalog.languages.map { FormSelectOption(it.value, it.label) },
                selectedValue = state.language,
                modifier = CompactFieldModifier(),
                fieldModifier = PlaygroundSelectModifier(),
            )
            FormSelect(
                name = "sample",
                label = "Sample",
                options = SeenPlaygroundCatalog.sampleOptions(state.language)
                    .map { FormSelectOption(it.value, it.label) },
                selectedValue = state.sample,
                modifier = CompactFieldModifier(),
                fieldModifier = PlaygroundSelectModifier(),
            )
            FormButton(
                text = "Load",
                variant = FormButtonVariant.Secondary,
                fullWidth = false,
                modifier = ToolbarButtonModifier(),
            )
        }

        AnchorLink(
            label = "GitHub",
            href = "https://github.com/YousefCodeworx/seen",
            modifier = Modifier()
                .color(PLAYGROUND_MUTED)
                .fontSize(0.85.rem)
                .fontWeight(600)
                .textDecoration(TextDecoration.None)
                .padding(8.px, 10.px)
                .borderRadius(6.px)
                .hover(Modifier().color(PLAYGROUND_ACCENT).backgroundColor("#21262d"))
                .focusVisible(
                    Modifier()
                        .outline(2, OutlineStyle.Solid, PLAYGROUND_ACCENT)
                        .outlineOffset(2),
                ),
            navigationMode = LinkNavigationMode.Native,
        )
    }
}

@Composable
private fun PlaygroundWorkspace(state: SeenPlaygroundViewState) {
    Form(
        action = "/playground/run",
        method = FormMethod.Post,
        hiddenFields = listOf(
            FormHiddenField("language", state.language),
            FormHiddenField("sample", state.sample),
        ),
        modifier = Modifier()
            .className("seen-playground-workspace")
            .display(Display.Flex)
            .alignItems(AlignItems.Stretch)
            .flex(grow = 1, shrink = 0, basis = "auto")
            .width(100.percent),
    ) {
        Column(
            modifier = Modifier()
                .className("seen-playground-editor")
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .flex(grow = 3, shrink = 1, basis = "0")
                .minWidth(0.px)
                .border(BorderSide.Right, 1, BorderStyle.Solid, PLAYGROUND_BORDER),
        ) {
            PanelHeader(title = "main.seen", subtitle = "${state.code.length} / $SEEN_PLAYGROUND_MAX_CODE_LENGTH")
            FormTextArea(
                name = "code",
                label = "Source code",
                defaultValue = state.code,
                rows = 24,
                maxLength = SEEN_PLAYGROUND_MAX_CODE_LENGTH,
                required = true,
                description = "Edit the sample and submit it to the bounded Seen runner.",
                modifier = Modifier()
                    .className("seen-playground-code-field")
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .flex(grow = 1, shrink = 1, basis = "auto")
                    .margin(0.px)
                    .padding(14.px),
                fieldModifier = Modifier()
                    .className("seen-playground-code-input")
                    .minHeight(480.px)
                    .padding(16.px)
                    .backgroundColor(PLAYGROUND_BACKGROUND)
                    .color(PLAYGROUND_TEXT)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PLAYGROUND_BORDER)
                    .borderRadius(8.px)
                    .fontFamily(PLAYGROUND_MONO)
                    .fontSize(0.9.rem)
                    .lineHeight(1.55)
                    .direction(if (state.language == "ar") LayoutDirection.RTL else LayoutDirection.LTR)
                    .focusVisible(
                        Modifier()
                            .borderColor(PLAYGROUND_ACCENT)
                            .outline(2, OutlineStyle.Solid, "rgba(88, 166, 255, 0.3)")
                            .outlineOffset(1),
                    ),
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .gap(12.px)
                    .padding(12.px, 14.px)
                    .backgroundColor(PLAYGROUND_SURFACE)
                    .border(BorderSide.Top, 1, BorderStyle.Solid, PLAYGROUND_BORDER),
            ) {
                Text(
                    text = "Execution is capped by the server timeout and output limit.",
                    modifier = Modifier().fontSize(0.78.rem).color(PLAYGROUND_MUTED),
                )
                FormButton(
                    text = "Run Seen",
                    fullWidth = false,
                    modifier = RunButtonModifier(),
                )
            }
        }

        PlaygroundOutput(state)
    }
}

@Composable
private fun PlaygroundOutput(state: SeenPlaygroundViewState) {
    val result = state.result
    val failed = result != null && (result.exitCode != 0 || result.error.isNotBlank())
    val status = when {
        state.validationMessage != null -> "Input rejected"
        result == null -> "Ready"
        failed -> "Failed (${result.exitCode})"
        else -> "Completed"
    }
    val statusColor = when {
        state.validationMessage != null || failed -> "#f85149"
        result != null -> "#3fb950"
        else -> PLAYGROUND_MUTED
    }
    val output = when {
        state.validationMessage != null -> state.validationMessage
        result == null -> "Load a sample, edit the source, and choose Run Seen."
        result.error.isNotBlank() && result.output.isNotBlank() -> "${result.output}\n${result.error}"
        result.error.isNotBlank() -> result.error
        result.output.isNotBlank() -> result.output
        else -> "Program completed without output."
    }

    Column(
        modifier = Modifier()
            .className("seen-playground-output")
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .flex(grow = 2, shrink = 1, basis = "0")
            .minWidth(320.px)
            .backgroundColor(PLAYGROUND_BACKGROUND),
    ) {
        PanelHeader(title = "Output", subtitle = status, subtitleColor = statusColor)
        Box(
            modifier = Modifier()
                .className("seen-playground-output-content")
                .role(if (failed || state.validationMessage != null) "alert" else "status")
                .ariaAttribute("live", "polite")
                .flex(grow = 1, shrink = 1, basis = "auto")
                .minHeight(320.px)
                .padding(18.px)
                .overflow(Overflow.Auto)
                .fontFamily(PLAYGROUND_MONO)
                .fontSize(0.88.rem)
                .lineHeight(1.6)
                .whiteSpace(WhiteSpace.PreWrap)
                .overflowWrap(OverflowWrap.Anywhere)
                .color(if (failed || state.validationMessage != null) "#ff7b72" else PLAYGROUND_TEXT)
                .direction(if (state.language == "ar") LayoutDirection.RTL else LayoutDirection.LTR),
        ) {
            Text(text = output)
        }
        if (result != null) {
            Row(
                modifier = Modifier()
                    .className("seen-playground-timing")
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(18.px)
                    .padding(9.px, 14.px)
                    .backgroundColor(PLAYGROUND_SURFACE)
                    .border(BorderSide.Top, 1, BorderStyle.Solid, PLAYGROUND_BORDER)
                    .fontFamily(PLAYGROUND_MONO)
                    .fontSize(0.75.rem)
                    .color(PLAYGROUND_MUTED),
            ) {
                Text(text = "compile + run: ${result.compileTimeMs} ms")
                Text(text = "exit: ${result.exitCode}")
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    subtitle: String,
    subtitleColor: String = PLAYGROUND_MUTED,
) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.SpaceBetween)
            .gap(12.px)
            .padding(9.px, 14.px)
            .backgroundColor(PLAYGROUND_SURFACE)
            .border(BorderSide.Bottom, 1, BorderStyle.Solid, PLAYGROUND_BORDER),
    ) {
        Text(text = title, modifier = Modifier().fontSize(0.82.rem).fontWeight(700).color(PLAYGROUND_TEXT))
        Text(text = subtitle, modifier = Modifier().fontSize(0.75.rem).fontWeight(600).color(subtitleColor))
    }
}

@Composable
private fun SeenPlaygroundStyleSheet() {
    val toolbar = StyleSelector.className("seen-playground-toolbar")
    val sampleForm = StyleSelector.className("seen-playground-sample-form")
    val workspace = StyleSelector.className("seen-playground-workspace")
    val editor = StyleSelector.className("seen-playground-editor")
    val output = StyleSelector.className("seen-playground-output")
    val outputContent = StyleSelector.className("seen-playground-output-content")
    val codeField = StyleSelector.className("seen-playground-code-field")

    TypedStyleSheet {
        rule(
            StyleSelector.all(
                StyleSelector.element(StyleElement.Html),
                StyleSelector.element(StyleElement.Body),
            ),
            Modifier()
                .margin(0.px)
                .padding(0.px)
                .backgroundColor(PLAYGROUND_BACKGROUND)
                .color(PLAYGROUND_TEXT)
                .fontFamily("system-ui, sans-serif"),
        )
        rule(
            StyleSelector.className("seen-playground-select")
                .pseudoClass(StylePseudoClass.Hover),
            Modifier().borderColor(PLAYGROUND_ACCENT),
        )
        rule(
            StyleSelector.all(
                sampleForm.descendant(StyleSelector.element(StyleElement.Label)),
                codeField.descendant(StyleSelector.element(StyleElement.Label)),
            ),
            Modifier().color(PLAYGROUND_MUTED),
            StyleRulePriority.Important,
        )
        rule(
            codeField.descendant(StyleSelector.element(StyleElement.Span)),
            Modifier().color(PLAYGROUND_MUTED),
            StyleRulePriority.Important,
        )
        rule(
            StyleSelector.className("seen-playground-select")
                .pseudoClass(StylePseudoClass.FocusVisible),
            Modifier()
                .borderColor(PLAYGROUND_ACCENT)
                .outline(2, OutlineStyle.Solid, "rgba(88, 166, 255, 0.3)")
                .outlineOffset(1),
        )
        rule(
            outputContent.pseudoElement(StylePseudoElement.WebkitScrollbar),
            Modifier().width(8.px),
        )
        rule(
            outputContent.pseudoElement(StylePseudoElement.WebkitScrollbarThumb),
            Modifier().backgroundColor(PLAYGROUND_BORDER).borderRadius(4.px),
        )
        rule(
            outputContent.pseudoElement(StylePseudoElement.WebkitScrollbarTrack),
            Modifier().backgroundColor(PLAYGROUND_BACKGROUND),
        )
        media(MediaQuery.MaxWidth(980)) {
            rule(
                toolbar,
                Modifier()
                    .flexWrap(FlexWrap.Wrap)
                    .alignItems(AlignItems.FlexStart),
                StyleRulePriority.Important,
            )
            rule(
                sampleForm,
                Modifier().width(100.percent),
                StyleRulePriority.Important,
            )
        }
        media(MediaQuery.MaxWidth(760)) {
            rule(
                workspace,
                Modifier().flexDirection(FlexDirection.Column),
                StyleRulePriority.Important,
            )
            rule(
                editor,
                Modifier()
                    .border(BorderSide.Right, 0, BorderStyle.Solid, PLAYGROUND_BORDER)
                    .border(BorderSide.Bottom, 1, BorderStyle.Solid, PLAYGROUND_BORDER),
                StyleRulePriority.Important,
            )
            rule(
                output,
                Modifier().minWidth(0.px).width(100.percent),
                StyleRulePriority.Important,
            )
            rule(
                sampleForm,
                Modifier()
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Stretch),
                StyleRulePriority.Important,
            )
        }
        media(MediaQuery.PrefersReducedMotion) {
            rule(
                StyleSelector.className("seen-playground-run-button"),
                Modifier().transitionProperty(TransitionProperty.None),
                StyleRulePriority.Important,
            )
        }
    }
}

private fun CompactFieldModifier(): Modifier = Modifier()
    .display(Display.Flex)
    .flexDirection(FlexDirection.Column)
    .gap(4.px)
    .margin(0.px)
    .minWidth(150.px)
    .fontSize(0.76.rem)
    .color(PLAYGROUND_MUTED)

private fun PlaygroundSelectModifier(): Modifier = Modifier()
    .className("seen-playground-select")
    .minWidth(150.px)
    .padding(7.px, 10.px)
    .backgroundColor("#21262d")
    .color(PLAYGROUND_TEXT)
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor(PLAYGROUND_BORDER)
    .borderRadius(6.px)
    .fontSize(0.82.rem)
    .cursor(Cursor.Pointer)

private fun ToolbarButtonModifier(): Modifier = Modifier()
    .padding(8.px, 13.px)
    .backgroundColor("#21262d")
    .color(PLAYGROUND_TEXT)
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor(PLAYGROUND_BORDER)
    .borderRadius(6.px)
    .fontSize(0.82.rem)
    .fontWeight(700)
    .cursor(Cursor.Pointer)
    .hover(Modifier().borderColor(PLAYGROUND_ACCENT).color(PLAYGROUND_ACCENT))
    .focusVisible(
        Modifier()
            .outline(2, OutlineStyle.Solid, PLAYGROUND_ACCENT)
            .outlineOffset(2),
    )

private fun RunButtonModifier(): Modifier = Modifier()
    .className("seen-playground-run-button")
    .padding(9.px, 18.px)
    .backgroundColor("#238636")
    .color("#ffffff")
    .borderWidth(1)
    .borderStyle(BorderStyle.Solid)
    .borderColor("#2ea043")
    .borderRadius(6.px)
    .fontSize(0.88.rem)
    .fontWeight(700)
    .cursor(Cursor.Pointer)
    .transition(
        property = TransitionProperty.BackgroundColor,
        duration = 150,
        timingFunction = TransitionTimingFunction.Ease,
    )
    .hover(Modifier().backgroundColor("#2ea043"))
    .focusVisible(
        Modifier()
            .outline(2, OutlineStyle.Solid, "#7ee787")
            .outlineOffset(2),
    )
