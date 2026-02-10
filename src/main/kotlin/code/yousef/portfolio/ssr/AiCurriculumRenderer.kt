package code.yousef.portfolio.ssr

import code.yousef.portfolio.docs.MarkdownRenderer
import code.yousef.portfolio.ui.ai.AiCurriculumPage
import codes.yousef.summon.seo.HeadScope
import java.nio.file.Path
import kotlin.io.path.readText

class AiCurriculumRenderer(
    private val markdownRenderer: MarkdownRenderer,
    private val curriculumPath: Path = Path.of("docs/private/unified_ai_curriculum.md")
) {

    fun curriculumPage(): SummonPage {
        val markdown = curriculumPath.readText()
        val rendered = markdownRenderer.render(markdown, "/ai")
        return SummonPage(
            head = headBlock(),
            content = {
                AiCurriculumPage(html = rendered.html, toc = rendered.toc)
            }
        )
    }

    private fun headBlock(): (HeadScope) -> Unit = { head ->
        head.title("AI Curriculum · Yousef")
        head.meta("robots", null, "noindex, nofollow", null, null)
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.script(HYDRATION_SCRIPT_PATH, "summon-hydration-runtime", "application/javascript", false, false, null)
        head.script("/static/ai-curriculum.js", "ai-curriculum", "application/javascript", false, true, null)
    }
}
