package code.yousef.portfolio.ui.aurora

import kotlinx.serialization.Serializable

/**
 * Configuration for the Aurora background effect.
 * This configures Sigil's built-in aurora effect.
 */
@Serializable
data class AuroraConfig(
    /** Canvas element ID */
    val canvasId: String = "aurora-canvas",
    
    /** Canvas height in pixels */
    val height: Int = 3500,
    
    /** Initial color palette index (0-6) */
    val initialPaletteIndex: Int = 0,
    
    /** Enable mouse position tracking for interactive effects */
    val enableMouseInteraction: Boolean = true,
    
    /** Enable keyboard number keys (1-7) to cycle palettes */
    val enableKeyboardCycle: Boolean = true,
    
    /** Enable click to cycle palettes */
    val enableClickCycle: Boolean = true,
    
    /** Animation speed multiplier */
    val timeScale: Float = 1.0f,
    
    /** Noise scale for the aurora pattern */
    val noiseScale: Float = 1.0f
)
