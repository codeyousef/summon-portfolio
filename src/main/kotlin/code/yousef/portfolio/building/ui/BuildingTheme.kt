package code.yousef.portfolio.building.ui

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.styles.StyleElement
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.i18n.LayoutDirection
import codes.yousef.summon.modifier.*

/**
 * Theme configuration for Arabic RTL building management UI.
 */
object BuildingTheme {
    object Colors {
        const val PRIMARY = "#1a73e8"
        const val PRIMARY_DARK = "#1557b0"
        const val PRIMARY_LIGHT = "#4285f4"

        const val BG_PRIMARY = "#f8fafc"
        const val BG_SECONDARY = "#ffffff"
        const val BG_CARD = "#ffffff"
        const val BG_HOVER = "#f1f5f9"

        const val TEXT_PRIMARY = "#1e293b"
        const val TEXT_SECONDARY = "#64748b"
        const val TEXT_MUTED = "#94a3b8"
        const val TEXT_WHITE = "#ffffff"

        const val SUCCESS = "#22c55e"
        const val SUCCESS_BG = "#dcfce7"
        const val SUCCESS_TEXT = "#166534"

        const val WARNING = "#f59e0b"
        const val WARNING_BG = "#fef3c7"
        const val WARNING_TEXT = "#92400e"

        const val DANGER = "#ef4444"
        const val DANGER_BG = "#fee2e2"
        const val DANGER_TEXT = "#991b1b"

        const val INFO = "#3b82f6"
        const val INFO_BG = "#dbeafe"
        const val INFO_TEXT = "#1e40af"

        const val BORDER = "#e2e8f0"
        const val BORDER_FOCUS = "#1a73e8"
        const val SHADOW = "rgba(0, 0, 0, 0.1)"
    }

    object Spacing {
        const val xs = "4px"
        const val sm = "8px"
        const val md = "16px"
        const val lg = "24px"
        const val xl = "32px"
        const val xxl = "48px"
    }

    object FontSize {
        const val xs = "12px"
        const val sm = "14px"
        const val base = "16px"
        const val lg = "18px"
        const val xl = "20px"
        const val xxl = "24px"
        const val xxxl = "30px"
    }

    object BorderRadius {
        const val sm = "4px"
        const val md = "8px"
        const val lg = "12px"
        const val xl = "16px"
        const val full = "9999px"
    }
}

/** Base document styles shared by authenticated and authentication building pages. */
@Composable
fun BuildingBaseStyles() {
    TypedStyleSheet {
        rule(
            StyleSelector.Universal,
            Modifier()
                .boxSizing(BoxSizing.BorderBox)
                .margin(0)
                .padding(0),
        )
        rule(
            StyleSelector.element(StyleElement.Body),
            Modifier()
                .fontFamily("'Tajawal', 'Segoe UI', Tahoma, sans-serif")
                .backgroundColor(BuildingTheme.Colors.BG_PRIMARY)
                .color(BuildingTheme.Colors.TEXT_PRIMARY)
                .direction(LayoutDirection.RTL)
                .textAlign(TextAlign.Right)
                .lineHeight(1.6),
        )

        val anchor = StyleSelector.element(StyleElement.Anchor)
        rule(
            anchor,
            Modifier()
                .color(BuildingTheme.Colors.PRIMARY)
                .textDecoration(TextDecoration.None),
        )
        rule(
            anchor.pseudoClass(StylePseudoClass.Hover),
            Modifier().textDecoration(TextDecoration.Underline),
        )
    }
}
