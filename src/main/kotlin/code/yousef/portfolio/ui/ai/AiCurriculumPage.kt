package code.yousef.portfolio.ui.ai

import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.components.Prose
import code.yousef.portfolio.docs.summon.components.Toc
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*

@Composable
fun AiCurriculumPage(html: String, toc: List<TocEntry>) {
    AiCurriculumStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1400) {
            // Progress summary placeholder — JS populates this on load
            Box(
                modifier = Modifier()
                    .id("ai-progress-summary")
                    .width(100.percent)
            ) {}

            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.lg)
                    .alignItems(AlignItems.FlexStart)
                    .flexWrap(FlexWrap.Wrap)
                    .width(100.percent)
            ) {
                Column(
                    modifier = Modifier()
                        .flex(grow = 1, shrink = 1, basis = "0%")
                        .minWidth(0.px)
                        .gap(PortfolioTheme.Spacing.lg)
                ) {
                    Prose(html = html)
                }
                Toc(entries = toc)
            }
        }

        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun AiCurriculumStyles() {
    GlobalStyle(
        """
        /* Checkbox styling */
        .ai-checkbox {
            width: 18px;
            height: 18px;
            accent-color: ${PortfolioTheme.Colors.ACCENT};
            cursor: pointer;
            margin-right: 8px;
            vertical-align: middle;
            flex-shrink: 0;
        }
        .ai-checkbox-label {
            display: inline-flex;
            align-items: center;
            cursor: pointer;
        }
        .ai-subsection-done {
            opacity: 0.6;
            text-decoration: line-through;
        }

        /* Phase badge */
        .ai-phase-badge {
            display: inline-block;
            margin-left: 12px;
            padding: 2px 10px;
            border-radius: 12px;
            font-size: 0.8rem;
            font-weight: 600;
            background: ${PortfolioTheme.Colors.SURFACE};
            border: 1px solid ${PortfolioTheme.Colors.BORDER};
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
            vertical-align: middle;
        }
        .ai-phase-badge.all-done {
            background: ${PortfolioTheme.Colors.ACCENT};
            color: #fff;
            border-color: ${PortfolioTheme.Colors.ACCENT};
        }

        /* Progress bar */
        #ai-progress-summary {
            margin-bottom: 8px;
        }
        .ai-progress-bar-track {
            width: 100%;
            height: 8px;
            background: ${PortfolioTheme.Colors.BORDER};
            border-radius: 4px;
            overflow: hidden;
            margin-top: 6px;
        }
        .ai-progress-bar-fill {
            height: 100%;
            background: ${PortfolioTheme.Colors.ACCENT};
            border-radius: 4px;
            transition: width 0.3s ease;
        }
        .ai-summary-text {
            font-size: 0.9rem;
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
            font-weight: 600;
        }
        """
    )
}
