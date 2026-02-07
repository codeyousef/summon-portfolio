package code.yousef.portfolio.ui.music

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.foundation.RawHtml
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.modifier.*

@Composable
fun AudioPlayer(
    audioUrl: String,
    trackId: String
) {
    GlobalStyle(
        css = """
        /* Custom audio player styling */
        audio.custom-audio-player {
            width: 100%;
            height: 40px;
            border-radius: ${PortfolioTheme.Radii.sm};
            outline: none;
        }

        /* WebKit browsers */
        audio.custom-audio-player::-webkit-media-controls-panel {
            background: ${PortfolioTheme.Colors.SURFACE};
        }

        audio.custom-audio-player::-webkit-media-controls-play-button {
            background-color: ${PortfolioTheme.Colors.ACCENT};
            border-radius: 50%;
        }

        audio.custom-audio-player::-webkit-media-controls-current-time-display,
        audio.custom-audio-player::-webkit-media-controls-time-remaining-display {
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
        }
    """
    )

    Box(
        modifier = Modifier()
            .width(100.percent)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.sm)
            .padding(PortfolioTheme.Spacing.sm)
    ) {
        RawHtml(
            html = """
                <audio
                    id="audio-$trackId"
                    class="custom-audio-player"
                    controls
                    preload="metadata"
                >
                    <source src="$audioUrl" type="audio/mpeg">
                    Your browser does not support the audio element.
                </audio>
            """.trimIndent()
        )
    }
}
