package code.yousef.portfolio.theme

import code.yousef.summon.extensions.px

object PortfolioTheme {
    object Colors {
        const val BACKGROUND = "#0a0b0d"
        const val BACKGROUND_ALT = "#0f0f14"
        const val SURFACE = "rgba(255, 255, 255, 0.06)"
        const val SURFACE_STRONG = "rgba(255, 255, 255, 0.12)"
        const val GLASS = "rgba(255, 255, 255, 0.08)"
        const val BORDER = "#ffffff18"
        const val BORDER_STRONG = "#ffffff24"
        const val TEXT_PRIMARY = "#eaeaf0"
        const val TEXT_SECONDARY = "#a7a7b3"
        const val ACCENT = "#b01235"
        const val ACCENT_ALT = "#ff3b6a"
        const val SUCCESS = "#3dd598"
        const val WARNING = "#fbbf24"
        const val DANGER = "#ff4d4d"
    }

    object Spacing {
        val xs = 4.px
        val sm = 8.px
        val md = 16.px
        val lg = 24.px
        val xl = 32.px
        val xxl = 48.px

        fun scale(multiplier: Int): String = (multiplier * 4).px
    }

    object Radii {
        val sm = 8.px
        val md = 16.px
        val lg = 24.px
        val pill = 999.px
    }

    object Shadows {
        const val LOW = "0 10px 40px rgba(0,0,0,.45), 0 2px 10px rgba(0,0,0,.35)"
        const val MEDIUM = "0 20px 60px rgba(0,0,0,.55)"
        const val HIGH = "0 30px 120px rgba(0,0,0,.65)"
    }

    object Motion {
        const val DEFAULT = "200ms ease"
        const val EMPHASIZED = "300ms cubic-bezier(0.4, 0, 0.2, 1)"
        const val BOUNCE = "400ms cubic-bezier(0.34, 1.56, 0.64, 1)"
    }

    object Typography {
        const val FONT_SANS = "\"Space Grotesk\", \"Inter\", system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
        const val FONT_MONO = "\"JetBrains Mono\", \"Fira Code\", ui-monospace, SFMono-Regular, Menlo, monospace"
        const val HERO_TRACKING = "-0.025em"
    }

    object Gradients {
        const val HERO =
            "radial-gradient(1200px 900px at 25% 12%, #15161c 0%, ${Colors.BACKGROUND} 55%), ${Colors.BACKGROUND_ALT}"
        const val ACCENT = "linear-gradient(180deg, ${Colors.ACCENT_ALT}, ${Colors.ACCENT})"
        const val CARD = "linear-gradient(180deg, #111318, #0f1116)"
        const val GLASS = "linear-gradient(180deg, #ffffff10, #ffffff06)"
    }
}
