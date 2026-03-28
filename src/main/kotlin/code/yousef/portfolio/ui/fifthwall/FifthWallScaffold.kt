package code.yousef.portfolio.ui.fifthwall

import code.yousef.portfolio.ui.aurora.AuroraBackground
import code.yousef.portfolio.ui.aurora.AuroraConfig
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*

@Composable
fun FifthWallScaffold(
    content: () -> Unit
) {
    GlobalStyle(
        css = """
        :root {
            --fw-base: ${FifthWallTheme.BASE};
            --fw-surface: ${FifthWallTheme.SURFACE};
            --fw-surface-strong: ${FifthWallTheme.SURFACE_STRONG};
            --fw-surface-glass: ${FifthWallTheme.SURFACE_GLASS};
            --fw-accent: ${FifthWallTheme.ACCENT};
            --fw-accent-soft: ${FifthWallTheme.ACCENT_SOFT};
            --fw-accent-warm: ${FifthWallTheme.ACCENT_WARM};
            --fw-success: ${FifthWallTheme.SUCCESS};
            --fw-danger: ${FifthWallTheme.DANGER};
            --fw-text: ${FifthWallTheme.TEXT_PRIMARY};
            --fw-text-muted: ${FifthWallTheme.TEXT_SECONDARY};
            --fw-border: ${FifthWallTheme.BORDER};
            --fw-belt: ${FifthWallTheme.BELT};
            --fw-belt-highlight: ${FifthWallTheme.BELT_HIGHLIGHT};
            --fw-font-display: "Space Grotesk", "Inter", system-ui, sans-serif;
            --fw-font-sans: "Space Grotesk", "Inter", system-ui, sans-serif;
            --fw-font-mono: "JetBrains Mono", "Fira Code", ui-monospace, SFMono-Regular, monospace;
        }

        html, body {
            margin: 0;
            padding: 0;
            background: var(--fw-base);
            color: var(--fw-text);
            font-family: var(--fw-font-sans);
        }

        * {
            box-sizing: border-box;
        }

        ::selection {
            background: rgba(107, 214, 255, 0.25);
            color: var(--fw-text);
        }
        """
    )

    Box(
        modifier = Modifier()
            .position(Position.Relative)
            .width(100.vw)
            .minHeight(100.vh)
            .overflow(Overflow.Hidden)
            .className("fw-shell")
    ) {
        AuroraBackground(
            config = AuroraConfig(
                canvasId = "fifth-wall-aurora",
                height = 2800,
                initialPaletteIndex = 1,
                enableMouseInteraction = false,
                enableKeyboardCycle = false,
                enableClickCycle = false,
                timeScale = 0.35f
            )
        )
        Box(modifier = Modifier().className("fw-shell-inner")) {
            content()
        }
    }
}
