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

data class AiSubLessonEntry(
    val parentSlug: String,
    val index: Int,
    val title: String,
    val markdown: String
)

class AiCurriculumCatalog(
    private val lessonDir: String = "ai/lessons"
) {
    private val entries: List<AiLessonEntry>
    private val bySlug: Map<String, AiLessonEntry>
    private val tree: DocsNavTree
    private val neighborMap: Map<String, NeighborLinks>
    private val subLessonCache: Map<String, List<AiSubLessonEntry>>

    init {
        entries = loadLessons()
        bySlug = entries.associateBy { it.slug }
        subLessonCache = buildSubLessonCache(entries)
        tree = buildNavTree(entries, subLessonCache)
        neighborMap = buildNeighborMap(entries, subLessonCache)
    }

    fun find(slug: String): AiLessonEntry? = bySlug[slug]
    fun navTree(): DocsNavTree = tree
    fun neighbors(slug: String): NeighborLinks = neighborMap[slug] ?: NeighborLinks(null, null)
    fun allEntries(): List<AiLessonEntry> = entries

    fun subLessonsOf(slug: String): List<AiSubLessonEntry> = subLessonCache[slug] ?: emptyList()

    fun findSub(slug: String, sub: Int): AiSubLessonEntry? =
        subLessonCache[slug]?.find { it.index == sub }

    fun introOf(slug: String): String {
        val entry = bySlug[slug] ?: return ""
        val lines = entry.markdown.lines()
        val firstH2 = lines.indexOfFirst { it.startsWith("## ") }
        if (firstH2 <= 0) return entry.markdown
        return lines.subList(0, firstH2).joinToString("\n").trimEnd()
    }

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

        private val TAIL_HEADINGS = setOf("checkpoint", "checkpoint exercise", "key takeaways", "further reading")

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

        internal fun splitAtH2(markdown: String): List<Pair<String, String>> {
            val lines = markdown.lines()
            val sections = mutableListOf<Pair<String, String>>() // title -> body
            var currentTitle = ""
            var currentLines = mutableListOf<String>()

            for (line in lines) {
                if (line.startsWith("## ")) {
                    if (currentTitle.isNotEmpty() || currentLines.isNotEmpty()) {
                        sections.add(currentTitle to currentLines.joinToString("\n").trimEnd())
                    }
                    currentTitle = line.removePrefix("## ").trim()
                    currentLines = mutableListOf()
                } else {
                    currentLines.add(line)
                }
            }
            if (currentTitle.isNotEmpty() || currentLines.isNotEmpty()) {
                sections.add(currentTitle to currentLines.joinToString("\n").trimEnd())
            }
            return sections
        }

        private fun buildSubLessonCache(entries: List<AiLessonEntry>): Map<String, List<AiSubLessonEntry>> {
            val cache = mutableMapOf<String, List<AiSubLessonEntry>>()

            for (entry in entries) {
                if (entry.slug == "overview") continue
                val allSections = splitAtH2(entry.markdown)

                // First section (before any H2) is intro, skip it
                // Filter: sections whose title is an H2 heading (non-empty title)
                val h2Sections = allSections.filter { it.first.isNotEmpty() }
                if (h2Sections.isEmpty()) continue

                // Separate content sections from tail sections (Checkpoint, Key Takeaways, etc.)
                val contentSections = mutableListOf<Pair<String, String>>()
                val tailSections = mutableListOf<Pair<String, String>>()

                for (section in h2Sections) {
                    val normalized = section.first.replace(Regex("^\\d+\\.\\s*"), "").trim().lowercase()
                    if (normalized in TAIL_HEADINGS) {
                        tailSections.add(section)
                    } else {
                        contentSections.add(section)
                    }
                }

                if (contentSections.isEmpty()) continue

                // Append tail sections to the last content section
                val lastIdx = contentSections.lastIndex
                if (tailSections.isNotEmpty()) {
                    val lastContent = contentSections[lastIdx]
                    val tailMd = tailSections.joinToString("\n\n") { (title, body) ->
                        "## $title\n$body"
                    }
                    contentSections[lastIdx] = lastContent.first to (lastContent.second + "\n\n" + tailMd)
                }

                val subLessons = contentSections.mapIndexed { idx, (title, body) ->
                    AiSubLessonEntry(
                        parentSlug = entry.slug,
                        index = idx + 1,
                        title = title,
                        markdown = "## $title\n$body"
                    )
                }
                cache[entry.slug] = subLessons
            }
            return cache
        }

        private fun buildNavTree(
            entries: List<AiLessonEntry>,
            subLessonCache: Map<String, List<AiSubLessonEntry>>
        ): DocsNavTree {
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
                    val subs = subLessonCache[lesson.slug] ?: emptyList()
                    val subChildren = subs.map { sub ->
                        DocsNavNode(
                            title = sub.title,
                            path = "/${lesson.slug}/${sub.index}"
                        )
                    }
                    DocsNavNode(
                        title = lesson.title,
                        path = "/${lesson.slug}",
                        children = subChildren
                    )
                }
                children.add(DocsNavNode(title = key, path = "", children = groupChildren))
            }

            return DocsNavTree(
                sections = listOf(
                    DocsNavNode(title = "AI Curriculum", path = "", children = children)
                )
            )
        }

        private fun buildNeighborMap(
            entries: List<AiLessonEntry>,
            subLessonCache: Map<String, List<AiSubLessonEntry>>
        ): Map<String, NeighborLinks> {
            // Build a flat chain: overview, 1-1, 1-1/1, 1-1/2, ..., 1-2, 1-2/1, ...
            data class ChainNode(val key: String, val title: String, val path: String)

            val chain = mutableListOf<ChainNode>()
            for (entry in entries) {
                val entryPath = if (entry.slug == "overview") "" else "/${entry.slug}"
                chain.add(ChainNode(entry.slug, entry.title, entryPath))

                val subs = subLessonCache[entry.slug] ?: emptyList()
                for (sub in subs) {
                    chain.add(
                        ChainNode(
                            "${entry.slug}/${sub.index}",
                            sub.title,
                            "/${entry.slug}/${sub.index}"
                        )
                    )
                }
            }

            val map = mutableMapOf<String, NeighborLinks>()
            for ((i, node) in chain.withIndex()) {
                val prev = if (i > 0) {
                    NavLink(title = chain[i - 1].title, path = chain[i - 1].path)
                } else null
                val next = if (i < chain.size - 1) {
                    NavLink(title = chain[i + 1].title, path = chain[i + 1].path)
                } else null
                map[node.key] = NeighborLinks(prev, next)
            }
            return map
        }
    }
}
