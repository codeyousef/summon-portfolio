package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormButtonVariant
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormTextField
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.modifier.*

/** A server-native terminal: commands submit with a normal GET and return typed Summon output. */
@Composable
fun TerminalOverlay(command: String? = null) {
    val response = command.toTerminalResponse()

    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .bottom(0.px)
            .left(0.px)
            .right(0.px)
            .height(200.px)
            .backgroundColor(ScratchpadTheme.BG_SECONDARY)
            .border(BorderSide.Top, 2, BorderStyle.Solid, ScratchpadTheme.TEXT_MUTED)
            .zIndex(9500)
            .fontFamily(ScratchpadTheme.FONT_MONO)
            .id("terminal-overlay")
    ) {
        Column(
            modifier = Modifier()
                .height(100.percent)
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .padding(12.px)
                .minHeight(0.px)
                .overflow(Overflow.Hidden)
        ) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .justifyContent(JustifyContent.SpaceBetween)
                    .alignItems(AlignItems.Center)
                    .paddingBottom(8.px)
                    .border(BorderSide.Bottom, 1, BorderStyle.Solid, ScratchpadTheme.BORDER)
                    .marginBottom(8.px)
            ) {
                TerminalMutedText("SCRATCHPAD TERMINAL v0.2.0")
                TerminalMutedText("Server-native · type 'help'")
            }

            Box(
                modifier = Modifier()
                    .flex(1, 1, "auto")
                    .overflowY(Overflow.Auto)
                    .paddingRight(8.px)
                    .minHeight(0.px)
                    .scrollbarWidth(ScrollbarWidth.Thin)
                    .scrollbarColor(ScratchpadTheme.TEXT_MUTED, ScratchpadTheme.BG_SECONDARY)
                    .id("terminal-output")
            ) {
                Text(
                    text = response.output,
                    modifier = Modifier()
                        .color(response.color)
                        .lineHeight(1.6)
                        .whiteSpace(WhiteSpace.PreWrap)
                        .wordBreak(WordBreak.BreakWord)
                )
            }

            Form(
                action = "/scratchpad",
                method = FormMethod.Get,
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.FlexEnd)
                    .gap(8.px)
                    .paddingTop(8.px)
                    .border(BorderSide.Top, 1, BorderStyle.Solid, ScratchpadTheme.BORDER)
            ) {
                Text(
                    text = "guest@scratchpad:~$",
                    modifier = Modifier()
                        .color(ScratchpadTheme.ACCENT_ALT)
                        .paddingBottom(10.px)
                        .whiteSpace(WhiteSpace.NoWrap)
                )
                FormTextField(
                    name = "command",
                    label = "",
                    defaultValue = "",
                    placeholder = "enter command...",
                    autoComplete = "off",
                    modifier = Modifier().flex(1, 1, "auto").margin(0),
                    fieldModifier = Modifier()
                        .backgroundColor("transparent")
                        .borderWidth(0)
                        .borderRadius(0)
                        .color(ScratchpadTheme.TEXT_PRIMARY)
                        .fontFamily(ScratchpadTheme.FONT_MONO)
                        .fontSize(14.px)
                        .outline(OutlineStyle.None.value)
                )
                FormButton(
                    text = "RUN",
                    variant = FormButtonVariant.Secondary,
                    fullWidth = false,
                    modifier = Modifier()
                        .backgroundColor(ScratchpadTheme.BG_SURFACE)
                        .color(ScratchpadTheme.TEXT_PRIMARY)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(ScratchpadTheme.TEXT_MUTED)
                        .borderRadius(0)
                        .padding(8.px, 12.px)
                )
            }
        }
    }

    if (response.showBlueScreen) {
        BlueScreenOverlay()
    }
}

@Composable
private fun TerminalMutedText(value: String) {
    Text(text = value, modifier = Modifier().color(ScratchpadTheme.TEXT_MUTED).fontSize(12.px))
}

@Composable
private fun BlueScreenOverlay() {
    Column(
        modifier = Modifier()
            .position(Position.Fixed)
            .inset(0)
            .width(100.percent)
            .height(100.percent)
            .backgroundColor("#0000aa")
            .color("#ffffff")
            .fontFamily(ScratchpadTheme.FONT_MONO)
            .zIndex(99999)
            .padding(40.px)
            .boxSizing(BoxSizing.BorderBox)
            .gap(20.px)
    ) {
        Text(
            text = "Windows",
            modifier = Modifier()
                .display(Display.InlineBlock)
                .backgroundColor("#aaaaaa")
                .color("#0000aa")
                .padding(4.px, 8.px)
                .fontSize(24.px)
                .fontWeight(700)
        )
        Text(
            text = BLUE_SCREEN_MESSAGE,
            modifier = Modifier().fontSize(16.px).lineHeight(1.6).whiteSpace(WhiteSpace.PreWrap)
        )
        Text(text = "Physical memory dump complete.")
        Text(text = "Use the EXIT link when you are done admiring the void.", modifier = Modifier().fontSize(14.px))
    }
}

private data class TerminalResponse(
    val output: String,
    val color: String = ScratchpadTheme.TEXT_SECONDARY,
    val showBlueScreen: Boolean = false
)

private fun String?.toTerminalResponse(): TerminalResponse {
    val normalized = this?.trim()?.lowercase().orEmpty()
    return when (normalized) {
        "" -> TerminalResponse("Welcome to the void. Type 'help' to see what you can do.")
        "help" -> TerminalResponse(
            "help             show this list\n" +
                "whoami           inspect the current operator\n" +
                "spawn balls      reveal a harmless burst\n" +
                "clear            clear the terminal\n" +
                "sudo rm -rf /     absolutely do not"
        )
        "whoami" -> TerminalResponse("guest — curious, unauthenticated, and probably procrastinating.", ScratchpadTheme.ACCENT_ALT)
        "spawn balls", "spawn cube" -> TerminalResponse("Color burst spawned on the canvas.", ScratchpadTheme.ACCENT_ALT)
        "clear" -> TerminalResponse("")
        "sudo rm -rf /" -> TerminalResponse(
            "Permission granted. This was a terrible idea.",
            ScratchpadTheme.DANGER,
            showBlueScreen = true
        )
        else -> TerminalResponse("command not found: $normalized", ScratchpadTheme.DANGER)
    }
}

private const val BLUE_SCREEN_MESSAGE = """A problem has been detected and Windows has been shut down to prevent damage
to your computer.

IRQL_NOT_LESS_OR_EQUAL

*** STOP: 0x0000000A (0x00000000, 0x00000002, 0x00000001, 0x80544BE8)

*** rm.sys - Address 0x80544BE8 base at 0x80544000

Beginning dump of physical memory..."""
