package code.yousef.portfolio.ui.aurora

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.foundation.Canvas
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.left
import codes.yousef.summon.modifier.LayoutModifiers.top
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializable effect configuration for client-side hydration.
 */
@Serializable
data class AuroraEffectData(
    val timeScale: Float,
    val noiseScale: Float,
    val initialPaletteIndex: Int,
    val enableMouse: Boolean,
    val enableKeyboard: Boolean,
    val enableClick: Boolean,
    val palettes: List<PaletteData>
)

@Serializable
data class PaletteData(
    val name: String,
    val a: List<Float>,
    val b: List<Float>,
    val c: List<Float>,
    val d: List<Float>
)

/**
 * Aurora background effect component using WebGPU.
 * 
 * This component renders a WebGPU-powered aurora background effect.
 * 
 * SSR renders:
 * - A fixed-position canvas element
 * - Serialized effect configuration as a data attribute
 * 
 * Client-side (via AuroraHydrationScript):
 * - Parses the effect configuration
 * - Creates WebGPU pipeline with aurora shader
 * - Handles mouse/keyboard interactions
 * - Runs render loop with time uniform updates
 */
@Composable
fun AuroraBackground(
    config: AuroraConfig = AuroraConfig()
) {
    val json = Json { prettyPrint = false }
    
    // Convert palettes to serializable format
    val paletteData = AuroraPalettes.ALL.map { palette ->
        PaletteData(
            name = palette.name,
            a = listOf(palette.a.first, palette.a.second, palette.a.third),
            b = listOf(palette.b.first, palette.b.second, palette.b.third),
            c = listOf(palette.c.first, palette.c.second, palette.c.third),
            d = listOf(palette.d.first, palette.d.second, palette.d.third)
        )
    }
    
    // Create effect configuration
    val effectData = AuroraEffectData(
        timeScale = config.timeScale,
        noiseScale = config.noiseScale,
        initialPaletteIndex = config.initialPaletteIndex,
        enableMouse = config.enableMouseInteraction,
        enableKeyboard = config.enableKeyboardCycle,
        enableClick = config.enableClickCycle,
        palettes = paletteData
    )
    
    // Serialize the effect for client-side hydration
    val effectJson = json.encodeToString(effectData)
    
    // Container for the aurora canvas - positioned fixed behind all content
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.percent)
            .height(config.height.px)
            .zIndex(0)
            .pointerEvents(PointerEvents.None) // Allow clicks to pass through
    ) {
        // The WebGPU canvas element with embedded effect data
        Canvas(
            id = config.canvasId,
            modifier = Modifier()
                .width(100.percent)
                .height(100.percent)
                .display(Display.Block),
            dataAttributes = mapOf(
                "aurora-effect" to effectJson
            )
        )
    }
}
