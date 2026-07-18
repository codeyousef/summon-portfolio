package code.yousef.portfolio.ssr

import code.yousef.portfolio.ai.AiCurriculumCatalog
import code.yousef.portfolio.ai.AiLessonEntry
import code.yousef.portfolio.ai.AiProgressStore
import code.yousef.portfolio.docs.MarkdownMeta
import code.yousef.portfolio.docs.MarkdownRenderer
import code.yousef.portfolio.ui.ai.AiLessonLandingPage
import code.yousef.portfolio.ui.ai.AiLessonPage
import code.yousef.portfolio.ui.ai.AiOverviewPage
import code.yousef.portfolio.ui.ai.AiSubLessonPage
import codes.yousef.summon.seo.HeadScope

class AiCurriculumRenderer(
    private val markdownRenderer: MarkdownRenderer,
    private val catalog: AiCurriculumCatalog,
    private val progressStore: AiProgressStore
) {

    suspend fun overviewPage(): SummonPage {
        val entries = catalog.allEntries()
        val progress = progressStore.getProgress()
        return SummonPage(
            head = headBlock("AI Curriculum"),
            content = {
                AiOverviewPage(entries = entries, progress = progress)
            }
        )
    }

    fun lessonPage(slug: String): SummonPage? {
        val entry = catalog.find(slug) ?: return null
        val rendered = markdownRenderer.render(entry.markdown, "/ai/$slug")
        val meta = MarkdownMeta(
            title = entry.title,
            description = entry.phaseTitle ?: "AI Curriculum"
        )
        val neighbors = catalog.neighbors(slug)
        val navTree = catalog.navTree()

        return SummonPage(
            head = headBlock(entry.title),
            content = {
                AiLessonPage(
                    requestPath = "/$slug",
                    document = rendered.document,
                    toc = rendered.toc,
                    sidebar = navTree,
                    meta = meta,
                    neighbors = neighbors,
                    sectionId = entry.sectionId
                )
            }
        )
    }

    suspend fun lessonLandingPage(slug: String): SummonPage? {
        val entry = catalog.find(slug) ?: return null
        val subLessons = catalog.subLessonsOf(slug)
        if (subLessons.isEmpty()) {
            // No sub-lessons — fall back to full lesson page
            return lessonPage(slug)
        }

        val introMd = catalog.introOf(slug)
        val introRendered = markdownRenderer.render(introMd, "/ai/$slug")
        val progress = progressStore.getProgress()
        val navTree = catalog.navTree()

        return SummonPage(
            head = headBlock(entry.title),
            content = {
                AiLessonLandingPage(
                    slug = slug,
                    entry = entry,
                    introDocument = introRendered.document,
                    subLessons = subLessons,
                    progress = progress,
                    sidebar = navTree,
                    requestPath = "/$slug"
                )
            }
        )
    }

    suspend fun subLessonPage(slug: String, sub: Int): SummonPage? {
        val entry = catalog.find(slug) ?: return null
        val subLesson = catalog.findSub(slug, sub) ?: return null
        val rendered = markdownRenderer.render(subLesson.markdown, "/ai/$slug/$sub")
        val meta = MarkdownMeta(
            title = subLesson.title,
            description = entry.title
        )
        val neighbors = catalog.neighbors("$slug/$sub")
        val navTree = catalog.navTree()
        val progress = progressStore.getProgress()
        val progressId = "${entry.sectionId}.$sub"
        val isCompleted = progress[progressId] == true

        return SummonPage(
            head = headBlock(subLesson.title),
            content = {
                AiSubLessonPage(
                    requestPath = "/$slug/$sub",
                    document = rendered.document.withParagraphCodePairs(),
                    toc = rendered.toc,
                    sidebar = navTree,
                    meta = meta,
                    neighbors = neighbors,
                    progressId = progressId,
                    isCompleted = isCompleted,
                    currentPath = "/ai/$slug/$sub"
                )
            }
        )
    }

    private fun headBlock(title: String): (HeadScope) -> Unit = { head ->
        head.title("$title · AI Curriculum · Yousef")
        head.meta("robots", null, "noindex, nofollow", null, null)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        // Hydration
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
    }

}
