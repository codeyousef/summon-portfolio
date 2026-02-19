package code.yousef.portfolio.ssr

import code.yousef.portfolio.ai.AiCurriculumCatalog
import code.yousef.portfolio.ai.AiLessonEntry
import code.yousef.portfolio.docs.MarkdownMeta
import code.yousef.portfolio.docs.MarkdownRenderer
import code.yousef.portfolio.ui.ai.AiLessonPage
import code.yousef.portfolio.ui.ai.AiOverviewPage
import codes.yousef.summon.seo.HeadScope

class AiCurriculumRenderer(
    private val markdownRenderer: MarkdownRenderer,
    private val catalog: AiCurriculumCatalog
) {

    fun overviewPage(): SummonPage {
        val entries = catalog.allEntries()
        return SummonPage(
            head = headBlock("AI Curriculum"),
            content = {
                AiOverviewPage(entries = entries)
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
                    html = rendered.html,
                    toc = rendered.toc,
                    sidebar = navTree,
                    meta = meta,
                    neighbors = neighbors,
                    sectionId = entry.sectionId
                )
            }
        )
    }

    private fun headBlock(title: String): (HeadScope) -> Unit = { head ->
        head.title("$title · AI Curriculum · Yousef")
        head.meta("robots", null, "noindex, nofollow", null, null)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
        head.script("/static/ai-curriculum.js", "ai-curriculum", "application/javascript", false, true, null)
    }
}
