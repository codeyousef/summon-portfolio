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
 */
object AuroraPalettes {
    /** Classic rainbow-ish aurora */
    val CLASSIC = AuroraPalette(
        name = "Classic",
        a = Triple(0.5f, 0.5f, 0.5f),
        b = Triple(0.5f, 0.5f, 0.5f),
        c = Triple(1.0f, 1.0f, 1.0f),
        d = Triple(0.0f, 0.33f, 0.67f)
    )
    
    /** Northern lights - deep blues and greens */
    val NORTHERN = AuroraPalette(
        name = "Northern",
        a = Triple(0.2f, 0.3f, 0.5f),
        b = Triple(0.3f, 0.3f, 0.3f),
        c = Triple(1.0f, 1.0f, 1.0f),
        d = Triple(0.0f, 0.10f, 0.20f)
    )
    
    /** Teal aurora */
    val TEAL = AuroraPalette(
        name = "Teal",
        a = Triple(0.1f, 0.5f, 0.4f),
        b = Triple(0.3f, 0.4f, 0.3f),
        c = Triple(0.8f, 1.0f, 0.9f),
        d = Triple(0.0f, 0.25f, 0.50f)
    )
    
    /** Sunset - warm oranges and golds */
    val SUNSET = AuroraPalette(
        name = "Sunset",
        a = Triple(0.5f, 0.3f, 0.2f),
        b = Triple(0.5f, 0.3f, 0.3f),
        c = Triple(1.0f, 0.7f, 0.4f),
        d = Triple(0.0f, 0.15f, 0.20f)
    )
    
    /** Neon - vibrant purples and yellows */
    val NEON = AuroraPalette(
        name = "Neon",
        a = Triple(0.5f, 0.1f, 0.5f),
        b = Triple(0.5f, 0.4f, 0.5f),
        c = Triple(1.0f, 1.0f, 0.5f),
        d = Triple(0.80f, 0.90f, 0.30f)
    )
    
    /** Forest - earthy greens */
    val FOREST = AuroraPalette(
        name = "Forest",
        a = Triple(0.2f, 0.4f, 0.2f),
        b = Triple(0.2f, 0.3f, 0.2f),
        c = Triple(1.0f, 1.0f, 1.0f),
        d = Triple(0.0f, 0.33f, 0.0f)
    )
    
    /** Nebula - cosmic purples */
    val NEBULA = AuroraPalette(
        name = "Nebula",
        a = Triple(0.3f, 0.1f, 0.4f),
        b = Triple(0.4f, 0.2f, 0.4f),
        c = Triple(1.0f, 1.0f, 0.5f),
        d = Triple(0.25f, 0.0f, 0.50f)
    )
    
    /** All available palettes */
    val ALL = listOf(CLASSIC, NORTHERN, TEAL, SUNSET, NEON, FOREST, NEBULA)
    
    /** Default palette */
    val DEFAULT = CLASSIC
}
