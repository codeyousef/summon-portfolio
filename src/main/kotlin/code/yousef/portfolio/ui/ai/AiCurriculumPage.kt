package code.yousef.portfolio.ui.ai

import code.yousef.portfolio.ai.AiLessonEntry
import code.yousef.portfolio.ai.AiSubLessonEntry
import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.MarkdownDocument
import code.yousef.portfolio.docs.MarkdownMeta
import code.yousef.portfolio.docs.NeighborLinks
import code.yousef.portfolio.docs.TocEntry
import code.yousef.portfolio.docs.summon.DocsShell
import code.yousef.portfolio.docs.summon.components.Prose
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormEncType
import codes.yousef.summon.components.forms.FormHiddenField
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

// ── Lesson page (full — used as fallback when no sub-lessons exist) ────────

@Composable
fun AiLessonPage(
    requestPath: String,
    document: MarkdownDocument,
    toc: List<TocEntry>,
    sidebar: DocsNavTree,
    meta: MarkdownMeta,
    neighbors: NeighborLinks,
    sectionId: String?
) {
    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN)

        SectionWrap(maxWidthPx = 1500) {
            DocsShell(
                requestPath = requestPath,
                document = document,
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
    introDocument: MarkdownDocument,
    subLessons: List<AiSubLessonEntry>,
    progress: Map<String, Boolean>,
    sidebar: DocsNavTree,
    requestPath: String
) {
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
                        .fontSize(0.9.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .fontWeight(600)
                )
                AiProgressBar(percent = pct)
            }

            // Intro prose
            Prose(
                document = introDocument,
                modifier = Modifier()
                    .marginBottom(PortfolioTheme.Spacing.lg)
                    .width(100.percent)
            )

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
                            .transition(
                                property = TransitionProperty.BorderColor,
                                duration = 200,
                                timingFunction = TransitionTimingFunction.Ease
                            )
                            .hover(Modifier().borderColor(PortfolioTheme.Colors.ACCENT_ALT))
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
    document: MarkdownDocument,
    toc: List<TocEntry>,
    sidebar: DocsNavTree,
    meta: MarkdownMeta,
    neighbors: NeighborLinks,
    progressId: String,
    isCompleted: Boolean,
    currentPath: String
) {
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
                        text = if (isCompleted) "Completed \u2713" else "Mark as completed",
                        modifier = Modifier()
                            .padding(8.px, 20.px)
                            .borderRadius(8.px)
                            .fontWeight(600)
                            .cursor(Cursor.Pointer)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .backgroundColor(PortfolioTheme.Colors.SURFACE)
                            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                            .transition(
                                property = TransitionProperty.All,
                                duration = 200,
                                timingFunction = TransitionTimingFunction.Ease
                            )
                            .hover(
                                Modifier()
                                    .backgroundColor(PortfolioTheme.Colors.ACCENT)
                                    .color("#fff")
                                    .borderColor(PortfolioTheme.Colors.ACCENT)
                            )
                    )
                }
            }

            DocsShell(
                requestPath = requestPath,
                document = document,
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
                            .fontSize(0.9.rem)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .fontWeight(600)
                    )
                    AiProgressBar(percent = pct)
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
                Box(
                    modifier = Modifier()
                        .display(Display.InlineBlock)
                        .marginLeft(12.px)
                        .padding(2.px, 10.px)
                        .borderRadius(12.px)
                        .fontSize(0.8.rem)
                        .fontWeight(600)
                        .backgroundColor(if (allDone) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.SURFACE)
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(if (allDone) PortfolioTheme.Colors.ACCENT else PortfolioTheme.Colors.BORDER)
                        .color(if (allDone) "#fff" else PortfolioTheme.Colors.TEXT_SECONDARY)
                ) {
                    Text(text = "$phaseDone/$phaseTotal")
                }
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
                        Text(
                            text = if (isLessonDone) "\u2713" else "\u25cb",
                            modifier = Modifier()
                                .color(
                                    if (isLessonDone) PortfolioTheme.Colors.ACCENT
                                    else PortfolioTheme.Colors.TEXT_SECONDARY
                                )
                                .fontWeight(if (isLessonDone) 700 else 400)
                                .fontSize(1.1.rem)
                                .flexShrink(0)
                        )
                    }

                    AnchorLink(
                        label = lesson.title,
                        href = "/ai/${lesson.slug}",
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.ACCENT_ALT)
                            .textDecoration(TextDecoration.None)
                            .hover(Modifier().textDecoration(TextDecoration.Underline))
                            .visited(Modifier().color(PortfolioTheme.Colors.ACCENT_ALT))
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
private fun AiProgressBar(percent: Int) {
    val normalizedPercent = percent.coerceIn(0, 100)
    Box(
        modifier = Modifier()
            .width(100.percent)
            .height(8.px)
            .backgroundColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(4.px)
            .overflow(Overflow.Hidden)
            .marginTop(6.px)
            .role("progressbar")
            .ariaAttribute("valuemin", "0")
            .ariaAttribute("valuemax", "100")
            .ariaAttribute("valuenow", normalizedPercent.toString())
    ) {
        Box(
            modifier = Modifier()
                .width(normalizedPercent.percent)
                .height(100.percent)
                .backgroundColor(PortfolioTheme.Colors.ACCENT)
                .borderRadius(4.px)
                .transition(
                    property = TransitionProperty.Width,
                    duration = 300,
                    timingFunction = TransitionTimingFunction.Ease
                )
        ) {}
    }
}
