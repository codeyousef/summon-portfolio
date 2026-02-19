package code.yousef.portfolio.ai

import code.yousef.portfolio.docs.*

data class AiLessonEntry(
    val slug: String,
    val sectionId: String?,
    val title: String,
    val phaseTitle: String?,
    val phaseNumber: Int?,
    val markdown: String
)

class AiCurriculumCatalog(
    private val lessonDir: String = "ai/lessons"
) {
    private val entries: List<AiLessonEntry>
    private val bySlug: Map<String, AiLessonEntry>
    private val tree: DocsNavTree
    private val neighborMap: Map<String, NeighborLinks>

    init {
        entries = loadLessons()
        bySlug = entries.associateBy { it.slug }
        tree = buildNavTree(entries)
        neighborMap = buildNeighborMap(entries)
    }

    fun find(slug: String): AiLessonEntry? = bySlug[slug]
    fun navTree(): DocsNavTree = tree
    fun neighbors(slug: String): NeighborLinks = neighborMap[slug] ?: NeighborLinks(null, null)
    fun allEntries(): List<AiLessonEntry> = entries

    private fun loadLessons(): List<AiLessonEntry> {
        val cl = Thread.currentThread().contextClassLoader
        val index = cl.getResourceAsStream("$lessonDir/index.txt")
            ?: error("Missing $lessonDir/index.txt")
        val fileNames = index.bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        return fileNames.map { fileName ->
            val content = cl.getResourceAsStream("$lessonDir/$fileName")
                ?.bufferedReader()?.readText()
                ?: error("Missing lesson file: $lessonDir/$fileName")
            parseLessonFile(fileName, content)
        }
    }

    companion object {
        private val FRONT_MATTER_REGEX = Regex("""^---\s*\n(.*?)\n---\s*\n""", RegexOption.DOT_MATCHES_ALL)
        private val YAML_LINE = Regex("""^(\w+):\s*"?(.*?)"?\s*$""")

        private fun parseLessonFile(fileName: String, content: String): AiLessonEntry {
            val frontMatch = FRONT_MATTER_REGEX.find(content)
            val yaml = mutableMapOf<String, String>()
            val markdown: String

            if (frontMatch != null) {
                for (line in frontMatch.groupValues[1].lines()) {
                    val m = YAML_LINE.matchEntire(line.trim()) ?: continue
                    yaml[m.groupValues[1]] = m.groupValues[2]
                }
                markdown = content.substring(frontMatch.range.last + 1)
            } else {
                markdown = content
            }

            val slug = fileName.removeSuffix(".md")
            val title = yaml["title"] ?: slug
            val sectionId = yaml["section_id"]?.takeIf { it.isNotEmpty() }
            val phaseNumber = yaml["phase"]?.toIntOrNull()?.takeIf { it > 0 }
            val phaseTitle = yaml["phase_title"]

            return AiLessonEntry(
                slug = slug,
                sectionId = sectionId,
                title = title,
                phaseTitle = phaseTitle,
                phaseNumber = phaseNumber,
                markdown = markdown.trimEnd()
            )
        }

        private fun buildNavTree(entries: List<AiLessonEntry>): DocsNavTree {
            val phaseOrder = mutableListOf<String>()
            val phaseGroups = LinkedHashMap<String, MutableList<AiLessonEntry>>()

            for (entry in entries) {
                if (entry.slug == "overview") continue
                val key = entry.phaseTitle ?: "Other"
                phaseGroups.getOrPut(key) {
                    phaseOrder.add(key)
                    mutableListOf()
                }.add(entry)
            }

            val children = mutableListOf<DocsNavNode>()
            children.add(DocsNavNode(title = "Overview", path = ""))

            for (key in phaseOrder) {
                val lessons = phaseGroups[key]!!
                val groupChildren = lessons.map { lesson ->
                    DocsNavNode(title = lesson.title, path = "/${lesson.slug}")
                }
                children.add(DocsNavNode(title = key, path = "", children = groupChildren))
            }

            return DocsNavTree(
                sections = listOf(
                    DocsNavNode(title = "AI Curriculum", path = "", children = children)
                )
            )
        }

        private fun buildNeighborMap(entries: List<AiLessonEntry>): Map<String, NeighborLinks> {
            val map = mutableMapOf<String, NeighborLinks>()
            for ((i, entry) in entries.withIndex()) {
                val prev = if (i > 0) {
                    val prevEntry = entries[i - 1]
                    NavLink(
                        title = prevEntry.title,
                        path = if (prevEntry.slug == "overview") "" else "/${prevEntry.slug}"
                    )
                } else null

                val next = if (i < entries.size - 1) {
                    val nextEntry = entries[i + 1]
                    NavLink(
                        title = nextEntry.title,
                        path = "/${nextEntry.slug}"
                    )
                } else null

                map[entry.slug] = NeighborLinks(prev, next)
            }
            return map
        }
    }
}
