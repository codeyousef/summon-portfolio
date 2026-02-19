package code.yousef.portfolio.ui.ai

import code.yousef.portfolio.ai.AiLessonEntry
import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.MarkdownMeta
import code.yousef.portfolio.docs.NeighborLinks
import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.DocsShell
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.foundation.RawHtml
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

// ── Lesson page (wraps DocsShell) ──────────────────────────────────────────

@Composable
fun AiLessonPage(
    requestPath: String,
    html: String,
    toc: List<TocEntry>,
    sidebar: DocsNavTree,
    meta: MarkdownMeta,
    neighbors: NeighborLinks,
    sectionId: String?
) {
    AiCurriculumStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1500) {
            if (sectionId != null) {
                RawHtml(
                    html = """<div id="ai-lesson-progress" data-section-id="${htmlEscape(sectionId)}" style="width:100%"></div>"""
                )
            }

            DocsShell(
                requestPath = requestPath,
                html = html,
                toc = toc,
                sidebar = sidebar,
                meta = meta,
                neighbors = neighbors,
                basePath = "/ai"
            )
        }
    }
}

// ── Overview page (dashboard with phase cards) ─────────────────────────────

@Composable
fun AiOverviewPage(entries: List<AiLessonEntry>) {
    AiCurriculumStyles()
    AiOverviewStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1400) {
            // Progress summary — JS populates
            Box(
                modifier = Modifier()
                    .id("ai-progress-summary")
                    .width(100.percent)
            ) {}

            // Title
            Text(
                text = "AI Curriculum",
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
                    .marginBottom(PortfolioTheme.Spacing.xs)
            )
            Text(
                text = "From Zero to Research Frontier — A Learn-by-Doing Journey",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.1.rem)
                    .marginBottom(PortfolioTheme.Spacing.xl)
            )

            // Phase cards
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.lg)
                    .width(100.percent)
            ) {
                // Group entries by phase, preserving order
                val lessons = entries.filter { it.slug != "overview" }
                val phaseOrder = mutableListOf<String>()
                val phaseGroups = linkedMapOf<String, MutableList<AiLessonEntry>>()

                for (lesson in lessons) {
                    val key = lesson.phaseTitle ?: "Other"
                    phaseGroups.getOrPut(key) {
                        phaseOrder.add(key)
                        mutableListOf()
                    }.add(lesson)
                }

                for (key in phaseOrder) {
                    PhaseCard(phaseTitle = key, lessons = phaseGroups[key]!!)
                }
            }
        }

        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun PhaseCard(phaseTitle: String, lessons: List<AiLessonEntry>) {
    Column(
        modifier = Modifier()
            .className("ai-phase-card")
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
            .width(100.percent)
    ) {
        // Phase title with badge placeholder
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
                .marginBottom(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = phaseTitle,
                modifier = Modifier()
                    .className("ai-phase-title")
                    .fontSize(1.25.rem)
                    .fontWeight(600)
            )
        }

        // Lesson links
        Column(
            modifier = Modifier()
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            lessons.forEach { lesson ->
                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .gap(PortfolioTheme.Spacing.sm)
                        .padding(PortfolioTheme.Spacing.xs, 0.px)
                ) {
                    // Checkbox placeholder — JS injects checkbox here
                    if (lesson.sectionId != null) {
                        RawHtml(
                            html = """<span class="ai-overview-checkbox" data-subsection="${htmlEscape(lesson.sectionId)}"></span>"""
                        )
                    }

                    AnchorLink(
                        label = lesson.title,
                        href = "/ai/${lesson.slug}",
                        modifier = Modifier()
                            .className("ai-lesson-link")
                            .color(PortfolioTheme.Colors.ACCENT_ALT)
                            .textDecoration(TextDecoration.None)
                            .hover(Modifier().textDecoration(TextDecoration.Underline)),
                        navigationMode = LinkNavigationMode.Native,
                        dataAttributes = buildMap {
                            if (lesson.sectionId != null) put("section-id", lesson.sectionId)
                        }
                    )
                }
            }
        }
    }
}

// ── Shared styles ──────────────────────────────────────────────────────────

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

@Composable
private fun AiOverviewStyles() {
    GlobalStyle(
        """
        /* Lesson progress on lesson page */
        #ai-lesson-progress {
            margin-bottom: 12px;
        }
        #ai-lesson-progress label {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            font-weight: 600;
            color: ${PortfolioTheme.Colors.TEXT_SECONDARY};
        }

        /* Overview lesson link hover */
        .ai-lesson-link:visited {
            color: ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        """
    )
}

private fun htmlEscape(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
