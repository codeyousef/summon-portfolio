package code.yousef.portfolio.ui.scratchpad

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.foundation.RawHtml
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

/**
 * A terminal overlay at the bottom of the scratchpad.
 * Supports commands like: help, clear, whoami, spawn cube, sudo rm -rf /
 */
@Composable
fun TerminalOverlay() {
    GlobalStyle(
        css = """
        #terminal-overlay {
            font-family: ${ScratchpadTheme.FONT_MONO};
        }

        #terminal-output {
            scrollbar-width: thin;
            scrollbar-color: ${ScratchpadTheme.TEXT_MUTED} ${ScratchpadTheme.BG_SECONDARY};
        }

        #terminal-input {
            background: transparent;
            border: none;
            color: ${ScratchpadTheme.TEXT_PRIMARY};
            font-family: ${ScratchpadTheme.FONT_MONO};
            font-size: 14px;
            outline: none;
            width: 100%;
            caret-color: ${ScratchpadTheme.TEXT_PRIMARY};
        }

        #terminal-input::placeholder {
            color: ${ScratchpadTheme.TEXT_MUTED};
        }

        .terminal-line {
            line-height: 1.6;
            white-space: pre-wrap;
            word-break: break-word;
        }

        .terminal-prompt {
            color: ${ScratchpadTheme.ACCENT_ALT};
        }

        .terminal-command {
            color: ${ScratchpadTheme.TEXT_PRIMARY};
        }

        .terminal-output {
            color: ${ScratchpadTheme.TEXT_SECONDARY};
        }

        .terminal-error {
            color: ${ScratchpadTheme.DANGER};
        }

        .terminal-success {
            color: ${ScratchpadTheme.ACCENT_ALT};
        }

        /* Blinking cursor effect */
        @keyframes blink {
            0%, 50% { opacity: 1; }
            51%, 100% { opacity: 0; }
        }

        .terminal-cursor {
            display: inline-block;
            width: 8px;
            height: 14px;
            background: ${ScratchpadTheme.TEXT_PRIMARY};
            animation: blink 1s step-end infinite;
            vertical-align: middle;
            margin-left: 2px;
        }

        /* BSOD styles */
        #bsod-overlay {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: #0000aa;
            color: white;
            font-family: ${ScratchpadTheme.FONT_MONO};
            z-index: 99999;
            padding: 40px;
            box-sizing: border-box;
        }

        #bsod-overlay.active {
            display: block;
        }

        #bsod-overlay h1 {
            background: #aaaaaa;
            color: #0000aa;
            display: inline-block;
            padding: 4px 8px;
            margin-bottom: 20px;
        }

        #bsod-overlay pre {
            font-size: 16px;
            line-height: 1.6;
        }
    """
    )

    // Terminal container
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .bottom(0.px)
            .left(0.px)
            .right(0.px)
            .height(200.px)
            .backgroundColor(ScratchpadTheme.BG_SECONDARY)
            .style("border-top", "2px solid ${ScratchpadTheme.TEXT_MUTED}")
            .zIndex(9500)
            .id("terminal-overlay")
    ) {
        RawHtml(
            html = """
            <div style="height: 100%; display: flex; flex-direction: column; padding: 12px;">
                <!-- Terminal header -->
                <div style="display: flex; justify-content: space-between; align-items: center; padding-bottom: 8px; border-bottom: 1px solid ${ScratchpadTheme.BORDER}; margin-bottom: 8px;">
                    <span style="color: ${ScratchpadTheme.TEXT_MUTED}; font-size: 12px;">SCRATCHPAD TERMINAL v0.1.0</span>
                    <span style="color: ${ScratchpadTheme.TEXT_MUTED}; font-size: 12px;">Type 'help' for commands</span>
                </div>

                <!-- Output area -->
                <div id="terminal-output" style="flex: 1; overflow-y: auto; padding-right: 8px;">
                    <div class="terminal-line terminal-output">Welcome to the void. Type 'help' to see what you can do.</div>
                </div>

                <!-- Input area -->
                <div style="display: flex; align-items: center; padding-top: 8px; border-top: 1px solid ${ScratchpadTheme.BORDER};">
                    <span class="terminal-prompt">guest@scratchpad:~$&nbsp;</span>
                    <input type="text" id="terminal-input" placeholder="enter command..." autocomplete="off" spellcheck="false">
                </div>
            </div>
        """.trimIndent()
        )
    }

    // BSOD overlay (hidden by default)
    Box(
        modifier = Modifier()
            .id("bsod-overlay")
    ) {
        RawHtml(
            html = """
            <h1>Windows</h1>
            <pre>
A problem has been detected and Windows has been shut down to prevent damage
to your computer.

IRQL_NOT_LESS_OR_EQUAL

If this is the first time you've seen this Stop error screen,
restart your computer. If this screen appears again, follow
these steps:

Check to make sure any new hardware or software is properly installed.
If this is a new installation, ask your hardware or software manufacturer
for any Windows updates you might need.

If problems continue, disable or remove any newly installed hardware
or software. Disable BIOS memory options such as caching or shadowing.

Technical Information:

*** STOP: 0x0000000A (0x00000000, 0x00000002, 0x00000001, 0x80544BE8)

*** rm.sys - Address 0x80544BE8 base at 0x80544000 DateStamp 0x47d6d7df

Beginning dump of physical memory...
            </pre>
            <div id="bsod-progress" style="margin-top: 20px;">Physical memory dump complete.</div>
            <div style="margin-top: 20px; font-size: 14px;">Press any key to continue... (just kidding, click anywhere)</div>
        """.trimIndent()
        )
    }
}
