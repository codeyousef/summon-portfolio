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
    
    /** Fixed background height in viewport units, with bounded overscan to avoid a hard fold cut-off. */
    val heightVh: Int = 120,
    
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

    /** Overall glow strength multiplier */
    val glowIntensity: Float = 2.0f,
    
    /** Noise scale for the aurora pattern */
    val noiseScale: Float = 1.0f
)
