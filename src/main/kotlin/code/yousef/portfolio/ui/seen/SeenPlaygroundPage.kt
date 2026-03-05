package code.yousef.portfolio.ui.seen

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.*
import codes.yousef.summon.modifier.*

@Composable
fun SeenPlaygroundPage() {
    GlobalStyle(
        css = """
        html, body {
            margin: 0;
            padding: 0;
            overflow: hidden;
            background: #0d1117;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            color: #e6edf3;
        }
        .example-select {
            background: #21262d;
            color: #e6edf3;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 4px 8px;
            font-size: 0.8rem;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            cursor: pointer;
            outline: none;
            min-width: 140px;
        }
        .example-select:hover {
            border-color: #58a6ff;
        }
        .example-select:focus {
            border-color: #58a6ff;
            box-shadow: 0 0 0 2px rgba(88, 166, 255, 0.3);
        }
        .run-button {
            transition: all 0.15s ease;
            user-select: none;
        }
        .run-button:active {
            transform: scale(0.96);
        }
        #output::-webkit-scrollbar {
            width: 6px;
        }
        #output::-webkit-scrollbar-track {
            background: transparent;
        }
        #output::-webkit-scrollbar-thumb {
            background: #30363d;
            border-radius: 3px;
        }
        #output::-webkit-scrollbar-thumb:hover {
            background: #484f58;
        }
        #timing-bar {
            gap: 16px;
            font-family: "JetBrains Mono", "Fira Code", "Cascadia Code", ui-monospace, monospace;
        }
        @media (max-width: 768px) {
            #editor-container {
                min-height: 40vh !important;
            }
        }
        .monaco-editor .suggest-widget {
            border: 1px solid #30363d !important;
        }
        """
    )

    // Main container
    Column(
        modifier = Modifier()
            .width(100.vw)
            .height(100.vh)
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .overflow(Overflow.Hidden)
    ) {
        // Header bar
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .padding(8.px, 16.px)
                .backgroundColor("#161b22")
                .style("border-bottom", "1px solid #30363d")
                .flexShrink(0)
                .gap(12.px)
        ) {
            // Left: logo + title
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(12.px)
            ) {
                Image(
                    src = "/static/seen-logo.png",
                    alt = "Seen",
                    modifier = Modifier()
                        .width(24.px)
                        .height(24.px)
                )
                Text(
                    text = "Seen",
                    modifier = Modifier()
                        .fontSize(1.25.rem)
                        .fontWeight(700)
                        .color("#58a6ff")
                )
                Text(
                    text = "Playground",
                    modifier = Modifier()
                        .fontSize(1.rem)
                        .color("#8b949e")
                )
            }

            // Center: example selector
            Box(
                modifier = Modifier()
                    .className("example-selector-container")
            ) {
                // select element rendered by JS — placeholder div
                Box(
                    modifier = Modifier()
                        .id("example-selector")
                ) {}
            }

            // Right: run button + links
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .alignItems(AlignItems.Center)
                    .gap(8.px)
            ) {
                Box(
                    modifier = Modifier()
                        .id("run-btn")
                        .className("run-button")
                        .padding(6.px, 16.px)
                        .backgroundColor("#238636")
                        .color("#ffffff")
                        .borderRadius(6.px)
                        .cursor(Cursor.Pointer)
                        .fontSize(0.875.rem)
                        .fontWeight(600)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor("#2ea043")
                        .hover(
                            Modifier().backgroundColor("#2ea043")
                        )
                ) {
                    Text(text = "Run")
                }

                AnchorLink(
                    label = "GitHub",
                    href = "https://github.com/YousefCodeworx/seen",
                    modifier = Modifier()
                        .color("#8b949e")
                        .fontSize(0.8.rem)
                        .textDecoration(TextDecoration.None)
                        .hover(Modifier().color("#58a6ff")),
                    navigationMode = LinkNavigationMode.Native
                )
            }
        }

        // Main content: editor + output
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flex("1")
                .overflow(Overflow.Hidden)
        ) {
            // Editor panel
            Box(
                modifier = Modifier()
                    .flex("1")
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .style("border-right", "1px solid #30363d")
                    .minWidth(0.px)
            ) {
                // Editor tab bar
                Box(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .padding(4.px, 12.px)
                        .backgroundColor("#0d1117")
                        .style("border-bottom", "1px solid #30363d")
                        .fontSize(0.8.rem)
                        .color("#8b949e")
                ) {
                    Text(
                        text = "main.seen",
                        modifier = Modifier()
                            .padding(4.px, 8.px)
                            .backgroundColor("#161b22")
                            .borderRadius(4.px)
                            .color("#e6edf3")
                            .fontSize(0.8.rem)
                    )
                }

                // Monaco editor container
                Box(
                    modifier = Modifier()
                        .id("editor-container")
                        .flex("1")
                        .minHeight(0.px)
                ) {}
            }

            // Output panel
            Box(
                modifier = Modifier()
                    .width(40.percent)
                    .minWidth(300.px)
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .backgroundColor("#0d1117")
            ) {
                // Output tab bar
                Box(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .justifyContent(JustifyContent.SpaceBetween)
                        .padding(4.px, 12.px)
                        .backgroundColor("#0d1117")
                        .style("border-bottom", "1px solid #30363d")
                ) {
                    Text(
                        text = "Output",
                        modifier = Modifier()
                            .padding(4.px, 8.px)
                            .color("#e6edf3")
                            .fontSize(0.8.rem)
                            .fontWeight(600)
                    )
                    Box(
                        modifier = Modifier()
                            .id("status-indicator")
                            .fontSize(0.75.rem)
                            .color("#8b949e")
                    ) {
                        Text(text = "Ready")
                    }
                }

                // Output content
                Box(
                    modifier = Modifier()
                        .id("output")
                        .flex("1")
                        .padding(12.px)
                        .overflow(Overflow.Auto)
                        .fontFamily("\"JetBrains Mono\", \"Fira Code\", \"Cascadia Code\", ui-monospace, monospace")
                        .fontSize(0.85.rem)
                        .lineHeight("1.5")
                        .whiteSpace(WhiteSpace.PreWrap)
                        .color("#8b949e")
                ) {
                    Text(text = "Click \"Run\" or press Ctrl+Enter to execute your code.")
                }

                // Timing bar
                Box(
                    modifier = Modifier()
                        .id("timing-bar")
                        .display(Display.None)
                        .padding(6.px, 12.px)
                        .style("border-top", "1px solid #30363d")
                        .fontSize(0.75.rem)
                        .color("#8b949e")
                        .backgroundColor("#161b22")
                ) {}
            }
        }

        // Footer
        Box(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .padding(4.px)
                .backgroundColor("#161b22")
                .style("border-top", "1px solid #30363d")
                .fontSize(0.7.rem)
                .color("#484f58")
                .flexShrink(0)
        ) {
            Text(text = "Powered by the Seen compiler - 100% self-hosted, multi-language systems programming")
        }
    }
}
