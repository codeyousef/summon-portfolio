package code.yousef.portfolio.theme

import code.yousef.summon.extensions.px

object PortfolioTheme {
    object Colors {
        const val background = "#050505"
        const val backgroundAlt = "#0b0b0f"
        const val surface = "rgba(12, 12, 18, 0.85)"
        const val surfaceStrong = "rgba(18, 18, 24, 0.95)"
        const val border = "rgba(255, 255, 255, 0.08)"
        const val borderStrong = "rgba(255, 255, 255, 0.16)"
        const val textPrimary = "#f8f8ff"
        const val textSecondary = "rgba(248, 248, 255, 0.72)"
        const val accent = "#B9314F"
        const val accentAlt = "#9F7AEA"
        const val accentGlow = "rgba(191, 99, 255, 0.35)"
        const val success = "#3dd598"
        const val warning = "#fbbf24"
        const val danger = "#ff4d4d"
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
        const val low = "0 4px 30px rgba(0, 0, 0, 0.35)"
        const val medium = "0 20px 50px rgba(5, 5, 10, 0.55)"
        const val high = "0 30px 120px rgba(12, 8, 20, 0.75)"
    }

    object Motion {
        const val default = "200ms ease"
        const val emphasized = "300ms cubic-bezier(0.4, 0, 0.2, 1)"
        const val bounce = "400ms cubic-bezier(0.34, 1.56, 0.64, 1)"
    }

    object Typography {
        const val fontSans = "\"Space Grotesk\", \"Inter\", system-ui, -apple-system, BlinkMacSystemFont, sans-serif"
        const val fontMono = "\"JetBrains Mono\", \"Fira Code\", ui-monospace, SFMono-Regular, Menlo, monospace"
        const val heroTracking = "-0.025em"
    }

    object Gradients {
        const val hero = "radial-gradient(circle at 20% 20%, rgba(159, 122, 234, 0.35), transparent 55%), " +
            "radial-gradient(circle at 80% 0%, rgba(185, 49, 79, 0.45), transparent 45%), " +
            "linear-gradient(180deg, rgba(5,5,10,0.95) 0%, rgba(5,5,5,0.8) 50%, #050505 100%)"
        const val accent = "linear-gradient(120deg, #B9314F, #9F7AEA)"
    }
}
