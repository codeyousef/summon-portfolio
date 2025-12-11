package code.yousef.portfolio.ui.aurora

import kotlinx.serialization.Serializable

/**
 * Aurora color palette definition using IQ's palette formula coefficients.
 * 
 * The palette formula is: color(t) = a + b * cos(2π * (c * t + d))
 * This creates smooth, organic color gradients perfect for aurora effects.
 */
@Serializable
data class AuroraPalette(
    val name: String,
    /** Base color (the "offset") */
    val a: Triple<Float, Float, Float>,
    /** Amplitude (how much the color varies) */
    val b: Triple<Float, Float, Float>,
    /** Frequency (how many color cycles) */
    val c: Triple<Float, Float, Float>,
    /** Phase (where in the color cycle to start) */
    val d: Triple<Float, Float, Float>
) {
    /**
     * Converts this palette to a JSON-serializable map for client-side use.
     */
    fun toJsonMap(): Map<String, List<Float>> = mapOf(
        "a" to listOf(a.first, a.second, a.third),
        "b" to listOf(b.first, b.second, b.third),
        "c" to listOf(c.first, c.second, c.third),
        "d" to listOf(d.first, d.second, d.third)
    )
}

/**
 * Predefined aurora color palettes using IQ's palette formula.
 * Users can cycle through these with keyboard (1-7) or click.
 * Palettes focus on reds, blues, and purples (no greens).
 */
object AuroraPalettes {
    /** Deep cosmic - blues and purples */
    val COSMIC = AuroraPalette(
        name = "Cosmic",
        a = Triple(0.3f, 0.1f, 0.5f),
        b = Triple(0.4f, 0.2f, 0.4f),
        c = Triple(1.0f, 0.8f, 1.0f),
        d = Triple(0.0f, 0.1f, 0.5f)
    )
    
    /** Crimson night - deep reds and magentas */
    val CRIMSON = AuroraPalette(
        name = "Crimson",
        a = Triple(0.5f, 0.1f, 0.3f),
        b = Triple(0.4f, 0.1f, 0.3f),
        c = Triple(1.0f, 0.5f, 0.8f),
        d = Triple(0.0f, 0.0f, 0.2f)
    )
    
    /** Electric blue - vibrant blues */
    val ELECTRIC = AuroraPalette(
        name = "Electric",
        a = Triple(0.1f, 0.2f, 0.5f),
        b = Triple(0.2f, 0.3f, 0.5f),
        c = Triple(0.8f, 1.0f, 1.0f),
        d = Triple(0.0f, 0.15f, 0.35f)
    )
    
    /** Nebula - cosmic purples and magentas */
    val NEBULA = AuroraPalette(
        name = "Nebula",
        a = Triple(0.3f, 0.1f, 0.4f),
        b = Triple(0.4f, 0.2f, 0.4f),
        c = Triple(1.0f, 0.7f, 1.0f),
        d = Triple(0.25f, 0.0f, 0.50f)
    )
    
    /** Midnight - deep blues with subtle purple */
    val MIDNIGHT = AuroraPalette(
        name = "Midnight",
        a = Triple(0.1f, 0.1f, 0.4f),
        b = Triple(0.2f, 0.2f, 0.4f),
        c = Triple(1.0f, 1.0f, 1.0f),
        d = Triple(0.0f, 0.05f, 0.25f)
    )
    
    /** Ruby - rich reds */
    val RUBY = AuroraPalette(
        name = "Ruby",
        a = Triple(0.4f, 0.1f, 0.2f),
        b = Triple(0.5f, 0.15f, 0.25f),
        c = Triple(1.0f, 0.6f, 0.7f),
        d = Triple(0.0f, 0.1f, 0.15f)
    )
    
    /** Violet dream - purples and pinks */
    val VIOLET = AuroraPalette(
        name = "Violet",
        a = Triple(0.4f, 0.15f, 0.5f),
        b = Triple(0.35f, 0.2f, 0.4f),
        c = Triple(1.0f, 0.8f, 1.0f),
        d = Triple(0.3f, 0.0f, 0.4f)
    )
    
    /** All available palettes - reds, blues, purples only */
    val ALL = listOf(COSMIC, CRIMSON, ELECTRIC, NEBULA, MIDNIGHT, RUBY, VIOLET)
    
    /** Default palette */
    val DEFAULT = COSMIC
}
