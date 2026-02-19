package code.yousef.portfolio.ui.ai

import code.yousef.portfolio.ai.AiLessonEntry
import code.yousef.portfolio.ai.AiSubLessonEntry
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
import codes.yousef.summon.components.display.RichText
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormEncType
import codes.yousef.summon.components.forms.FormHiddenField
import codes.yousef.summon.components.forms.FormMethod
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

// ── Lesson page (full — used as fallback when no sub-lessons exist) ────────

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

// ── Lesson landing page (intro + sub-lesson cards) ────────────────────────

@Composable
fun AiLessonLandingPage(
    slug: String,
    entry: AiLessonEntry,
    introHtml: String,
    subLessons: List<AiSubLessonEntry>,
    progress: Map<String, Boolean>,
    sidebar: DocsNavTree,
    requestPath: String
) {
    AiCurriculumStyles()
    AiLandingStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1200) {
            // Title
            Text(
                text = entry.title,
                modifier = Modifier()
                    .fontSize(2.rem)
                    .fontWeight(700)
                    .marginBottom(PortfolioTheme.Spacing.sm)
            )

            // Progress bar for this lesson
            val totalSubs = subLessons.size
            val completedSubs = subLessons.count { sub ->
                progress["${entry.sectionId}.${sub.index}"] == true
            }
            val pct = if (totalSubs > 0) (completedSubs * 100) / totalSubs else 0

            Column(
                modifier = Modifier()
                    .width(100.percent)
                    .marginBottom(PortfolioTheme.Spacing.md)
            ) {
                Text(
                    text = "$completedSubs / $totalSubs sub-lessons completed ($pct%)",
                    modifier = Modifier()
                        .className("ai-summary-text")
                )
                RawHtml(
                    html = """<div class="ai-progress-bar-track"><div class="ai-progress-bar-fill" style="width:${pct}%"></div></div>"""
                )
            }

            // Intro prose
            if (introHtml.isNotBlank()) {
                Column(
                    modifier = Modifier()
                        .className("prose")
                        .backgroundColor(PortfolioTheme.Colors.SURFACE)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .padding(PortfolioTheme.Spacing.xl)
                        .marginBottom(PortfolioTheme.Spacing.lg)
                        .width(100.percent)
                ) {
                    RichText(
                        introHtml,
                        modifier = Modifier()
                            .fontSize(1.rem)
                            .lineHeight(1.7)
                            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                            .fontFamily(PortfolioTheme.Typography.FONT_SANS)
                    )
                }
            }

            // Sub-lesson cards
            Text(
                text = "Sub-lessons",
                modifier = Modifier()
                    .fontSize(1.5.rem)
                    .fontWeight(600)
                    .marginBottom(PortfolioTheme.Spacing.md)
            )

            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.sm)
                    .width(100.percent)
            ) {
                subLessons.forEach { sub ->
                    val progressId = "${entry.sectionId}.${sub.index}"
                    val isCompleted = progress[progressId] == true

                    Row(
                        modifier = Modifier()
                            .className("ai-sub-card")
                            .display(Display.Flex)
                            .alignItems(AlignItems.Center)
                            .gap(PortfolioTheme.Spacing.md)
                            .backgroundColor(PortfolioTheme.Colors.SURFACE)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(if (isCompleted) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.md)
                            .padding(PortfolioTheme.Spacing.md)
                            .width(100.percent)
                    ) {
                        // Number badge
                        Box(
                            modifier = Modifier()
                                .display(Display.Flex)
                                .alignItems(AlignItems.Center)
                                .justifyContent(JustifyContent.Center)
                                .width(36.px)
                                .height(36.px)
                                .borderRadius("50%")
                                .backgroundColor(
                                    if (isCompleted) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.SURFACE_STRONG
                                )
                                .color(if (isCompleted) "#fff" else PortfolioTheme.Colors.TEXT_PRIMARY)
                                .fontWeight(700)
                                .fontSize(0.9.rem)
                                .flex(grow = 0, shrink = 0, basis = "36px")
                        ) {
                            Text(text = if (isCompleted) "\u2713" else "${sub.index}")
                        }

                        // Title link
                        AnchorLink(
                            label = sub.title,
                            href = "/ai/$slug/${sub.index}",
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.ACCENT_ALT)
                                .textDecoration(TextDecoration.None)
                                .hover(Modifier().textDecoration(TextDecoration.Underline))
                                .fontSize(1.05.rem)
                                .fontWeight(500)
                                .flex(grow = 1, shrink = 1, basis = "0%"),
                            navigationMode = LinkNavigationMode.Native
                        )
                    }
                }
            }
        }

        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

// ── Sub-lesson page (wraps DocsShell with completion form) ────────────────

@Composable
fun AiSubLessonPage(
    requestPath: String,
    html: String,
    toc: List<TocEntry>,
    sidebar: DocsNavTree,
    meta: MarkdownMeta,
    neighbors: NeighborLinks,
    progressId: String,
    isCompleted: Boolean,
    currentPath: String
) {
    AiCurriculumStyles()
    AiSubLessonStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1500) {
            // Completion form
            Box(
                modifier = Modifier()
                    .width(100.percent)
                    .marginBottom(PortfolioTheme.Spacing.md)
            ) {
                Form(
                    action = "/ai/api/progress",
                    method = FormMethod.Post,
                    encType = FormEncType.UrlEncoded,
                    hiddenFields = listOf(
                        FormHiddenField("id", progressId),
                        FormHiddenField("completed", (!isCompleted).toString()),
                        FormHiddenField("redirect", currentPath)
                    )
                ) {
                    FormButton(
                        text = if (isCompleted) "Completed \u2713" else "Mark as completed"
                    )
                }
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

// ── Overview page (dashboard with phase cards, server-side progress) ──────

@Composable
fun AiOverviewPage(entries: List<AiLessonEntry>, progress: Map<String, Boolean> = emptyMap()) {
    AiCurriculumStyles()
    AiOverviewStyles()

    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1400) {
            // Title
            Text(
                text = "AI Curriculum",
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
                    .marginBottom(PortfolioTheme.Spacing.xs)
            )
            Text(
                text = "From Zero to Research Frontier \u2014 A Learn-by-Doing Journey",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.1.rem)
                    .marginBottom(PortfolioTheme.Spacing.md)
            )

            // Compute overall progress
            val lessons = entries.filter { it.slug != "overview" && it.sectionId != null }
            val allIds = lessons.map { it.sectionId!! }
            val doneCount = allIds.count { progress[it] == true }
            val totalCount = allIds.size
            val pct = if (totalCount > 0) (doneCount * 100) / totalCount else 0

            // Server-rendered progress summary
            if (totalCount > 0) {
                Column(
                    modifier = Modifier()
                        .width(100.percent)
                        .marginBottom(PortfolioTheme.Spacing.lg)
                ) {
                    Text(
                        text = "$doneCount / $totalCount lessons completed ($pct%)",
                        modifier = Modifier()
                            .className("ai-summary-text")
                    )
                    RawHtml(
                        html = """<div class="ai-progress-bar-track"><div class="ai-progress-bar-fill" style="width:${pct}%"></div></div>"""
                    )
                }
            }

            // Phase cards
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.lg)
                    .width(100.percent)
            ) {
                // Group entries by phase, preserving order
                val allLessons = entries.filter { it.slug != "overview" }
                val phaseOrder = mutableListOf<String>()
                val phaseGroups = linkedMapOf<String, MutableList<AiLessonEntry>>()

                for (lesson in allLessons) {
                    val key = lesson.phaseTitle ?: "Other"
                    phaseGroups.getOrPut(key) {
                        phaseOrder.add(key)
                        mutableListOf()
                    }.add(lesson)
                }

                for (key in phaseOrder) {
                    PhaseCard(phaseTitle = key, lessons = phaseGroups[key]!!, progress = progress)
                }
            }
        }

        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun PhaseCard(phaseTitle: String, lessons: List<AiLessonEntry>, progress: Map<String, Boolean>) {
    // Compute phase progress
    val phaseIds = lessons.mapNotNull { it.sectionId }
    val phaseDone = phaseIds.count { progress[it] == true }
    val phaseTotal = phaseIds.size
    val allDone = phaseTotal > 0 && phaseDone == phaseTotal

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
        // Phase title with badge
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
            if (phaseTotal > 0) {
                RawHtml(
                    html = """<span class="ai-phase-badge${if (allDone) " all-done" else ""}">$phaseDone/$phaseTotal</span>"""
                )
            }
        }

        // Lesson links
        Column(
            modifier = Modifier()
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            lessons.forEach { lesson ->
                val isLessonDone = lesson.sectionId != null && progress[lesson.sectionId] == true

                Row(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .alignItems(AlignItems.Center)
                        .gap(PortfolioTheme.Spacing.sm)
                        .padding(PortfolioTheme.Spacing.xs, 0.px)
                ) {
                    // Server-rendered checkmark
                    if (lesson.sectionId != null) {
                        RawHtml(
                            html = if (isLessonDone)
                                """<span style="color:${PortfolioTheme.Colors.ACCENT};font-weight:700;font-size:1.1rem;flex-shrink:0;">&#10003;</span>"""
                            else
                                """<span style="color:${PortfolioTheme.Colors.TEXT_SECONDARY};font-size:1.1rem;flex-shrink:0;">&#9675;</span>"""
                        )
                    }

                    AnchorLink(
                        label = lesson.title,
                        href = "/ai/${lesson.slug}",
                        modifier = Modifier()
                            .className("ai-lesson-link")
                            .color(PortfolioTheme.Colors.ACCENT_ALT)
                            .textDecoration(TextDecoration.None)
                            .hover(Modifier().textDecoration(TextDecoration.Underline))
                            .let { mod ->
                                if (isLessonDone) mod.opacity(0.6f).textDecoration(TextDecoration.LineThrough)
                                else mod
                            },
                        navigationMode = LinkNavigationMode.Native
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
        /* Overview lesson link hover */
        .ai-lesson-link:visited {
            color: ${PortfolioTheme.Colors.ACCENT_ALT};
        }
        """
    )
}

@Composable
private fun AiLandingStyles() {
    GlobalStyle(
        """
        .ai-sub-card {
            transition: border-color 0.2s ease;
        }
        .ai-sub-card:hover {
            border-color: ${PortfolioTheme.Colors.ACCENT_ALT} !important;
        }
        """
    )
}

@Composable
private fun AiSubLessonStyles() {
    GlobalStyle(
        """
        /* Completion button styling */
        .ai-completion-form button {
            padding: 8px 20px;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            border: 1px solid ${PortfolioTheme.Colors.BORDER};
            background: ${PortfolioTheme.Colors.SURFACE};
            color: ${PortfolioTheme.Colors.TEXT_PRIMARY};
            transition: all 0.2s ease;
        }
        .ai-completion-form button:hover {
            background: ${PortfolioTheme.Colors.ACCENT};
            color: #fff;
            border-color: ${PortfolioTheme.Colors.ACCENT};
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
