package code.yousef.portfolio.ui.music

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.html.Audio
import codes.yousef.summon.components.html.Source
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun AudioPlayer(
    audioUrl: String,
    trackId: String
) {
    Box(
        modifier = Modifier()
            .width(100.percent)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderRadius(PortfolioTheme.Radii.sm)
            .padding(PortfolioTheme.Spacing.sm)
    ) {
        Audio(
            controls = true,
            preload = "metadata",
            modifier = Modifier()
                .id("audio-$trackId")
                .className("custom-audio-player")
                .width(100.percent)
                .height(40.px)
                .borderRadius(PortfolioTheme.Radii.sm)
                .backgroundColor(PortfolioTheme.Colors.SURFACE)
        ) {
            Source(src = audioUrl, type = "audio/mpeg")
            Text(text = "Your browser does not support the audio element.")
        }
    }
}
