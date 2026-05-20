package code.yousef.portfolio.ui.fifthwall

import io.materia.core.math.Color

object FifthWallTheme {
    private fun css(hex: String): String = Color(hex).toCssHexString()

    private fun blend(base: String, mix: String, t: Float): String {
        val color = Color(base)
        color.lerp(Color(mix), t)
        return color.toCssHexString()
    }

    val BASE = css("#0b1017")
    val SURFACE = css("#121a26")
    val SURFACE_STRONG = blend("#121a26", "#1f2b3d", 0.55f)
    val SURFACE_GLASS = blend("#121a26", "#0b1017", 0.35f)
    val ACCENT = css("#6bd6ff")
    val ACCENT_SOFT = blend("#6bd6ff", "#0b1017", 0.6f)
    val ACCENT_WARM = css("#f7b955")
    val SUCCESS = css("#45e0a8")
    val DANGER = css("#ff6b6b")
    val TEXT_PRIMARY = css("#e6f1ff")
    val TEXT_SECONDARY = blend("#e6f1ff", "#0b1017", 0.55f)
    val BORDER = blend("#121a26", "#ffffff", 0.08f)
    val BELT = blend("#0b1017", "#1a2432", 0.6f)
    val BELT_HIGHLIGHT = blend("#0b1017", "#6bd6ff", 0.2f)
}
