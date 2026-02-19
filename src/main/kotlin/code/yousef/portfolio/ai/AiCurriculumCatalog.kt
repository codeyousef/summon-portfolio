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
    resourcePath: String = "ai/unified_ai_curriculum.md"
) {
    private val entries: List<AiLessonEntry>
    private val bySlug: Map<String, AiLessonEntry>
    private val tree: DocsNavTree
    private val neighborMap: Map<String, NeighborLinks>

    init {
        val markdown = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourcePath)!!
            .bufferedReader().readText()
        entries = parseMarkdown(markdown)
        bySlug = entries.associateBy { it.slug }
        tree = buildNavTree(entries)
        neighborMap = buildNeighborMap(entries)
    }

    fun find(slug: String): AiLessonEntry? = bySlug[slug]
    fun navTree(): DocsNavTree = tree
    fun neighbors(slug: String): NeighborLinks = neighborMap[slug] ?: NeighborLinks(null, null)
    fun allEntries(): List<AiLessonEntry> = entries

    companion object {
        private val PHASE_REGEX = Regex("""^#\s+Phase\s+(\d+):\s*(.+)""")
        private val SUBSECTION_REGEX = Regex("""^##\s+(\d+\.\d+)\s+(.+)""")
        private val APPENDIX_REGEX = Regex("""^#\s+Appendix.*""")

        private data class HeadingInfo(
            val lineIndex: Int,
            val level: Int,
            val text: String
        )

        private fun parseMarkdown(markdown: String): List<AiLessonEntry> {
            val lines = markdown.lines()
            val entries = mutableListOf<AiLessonEntry>()

            // Collect all H1 and H2 headings with positions
            val headings = mutableListOf<HeadingInfo>()
            for ((i, line) in lines.withIndex()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("## ") ->
                        headings.add(HeadingInfo(i, 2, trimmed.removePrefix("## ").trim()))
                    trimmed.startsWith("# ") ->
                        headings.add(HeadingInfo(i, 1, trimmed.removePrefix("# ").trim()))
                }
            }

            // Find the first Phase heading — everything before it is overview
            val firstPhaseIdx = headings.indexOfFirst { it.level == 1 && PHASE_REGEX.matches("# ${it.text}") }
            val overviewEndLine = if (firstPhaseIdx >= 0) headings[firstPhaseIdx].lineIndex else lines.size

            entries.add(
                AiLessonEntry(
                    slug = "overview",
                    sectionId = null,
                    title = "Overview",
                    phaseTitle = null,
                    phaseNumber = null,
                    markdown = lines.take(overviewEndLine).joinToString("\n").trimEnd()
                )
            )

            // Walk through headings after overview, tracking phase context
            var currentPhaseTitle: String? = null
            var currentPhaseNumber: Int? = null
            var inAppendix = false

            for ((idx, heading) in headings.withIndex()) {
                if (heading.lineIndex < overviewEndLine) continue

                if (heading.level == 1) {
                    val phaseMatch = PHASE_REGEX.matchEntire("# ${heading.text}")
                    when {
                        phaseMatch != null -> {
                            currentPhaseNumber = phaseMatch.groupValues[1].toInt()
                            currentPhaseTitle = heading.text
                            inAppendix = false
                        }
                        APPENDIX_REGEX.matches("# ${heading.text}") -> {
                            inAppendix = true
                            currentPhaseTitle = "Appendix"
                            currentPhaseNumber = null
                        }
                    }
                    continue
                }

                // H2 headings — create lesson entries
                val sectionMd = extractSection(lines, heading.lineIndex, headings, idx)

                when {
                    !inAppendix -> {
                        val subsectionMatch = SUBSECTION_REGEX.matchEntire("## ${heading.text}")
                        if (subsectionMatch != null) {
                            val id = subsectionMatch.groupValues[1]
                            entries.add(
                                AiLessonEntry(
                                    slug = id.replace('.', '-'),
                                    sectionId = id,
                                    title = heading.text,
                                    phaseTitle = currentPhaseTitle,
                                    phaseNumber = currentPhaseNumber,
                                    markdown = sectionMd
                                )
                            )
                        } else if (heading.text.startsWith("Choose") && currentPhaseNumber == 14) {
                            entries.add(
                                AiLessonEntry(
                                    slug = "14-capstone",
                                    sectionId = null,
                                    title = "Capstone Projects",
                                    phaseTitle = currentPhaseTitle,
                                    phaseNumber = currentPhaseNumber,
                                    markdown = sectionMd
                                )
                            )
                        }
                    }
                    inAppendix -> {
                        val slug = "appendix-" + heading.text.lowercase()
                            .replace(Regex("[^a-z0-9\\s-]"), "")
                            .trim()
                            .replace(Regex("\\s+"), "-")
                        entries.add(
                            AiLessonEntry(
                                slug = slug,
                                sectionId = null,
                                title = heading.text,
                                phaseTitle = "Appendix",
                                phaseNumber = null,
                                markdown = sectionMd
                            )
                        )
                    }
                }
            }

            return entries
        }

        private fun extractSection(
            lines: List<String>,
            startLine: Int,
            headings: List<HeadingInfo>,
            headingIdx: Int
        ): String {
            val nextHeading = headings.subList(headingIdx + 1, headings.size)
                .firstOrNull { it.level <= 2 }
            val endLine = nextHeading?.lineIndex ?: lines.size

            // Trim trailing blank lines and --- separators
            var trimmedEnd = endLine
            while (trimmedEnd > startLine + 1) {
                val prev = lines[trimmedEnd - 1].trim()
                if (prev.isEmpty() || prev == "---") trimmedEnd--
                else break
            }

            return lines.subList(startLine, trimmedEnd).joinToString("\n")
        }

        private fun buildNavTree(entries: List<AiLessonEntry>): DocsNavTree {
            // Group entries by phase, maintaining order
            val phaseOrder = mutableListOf<String>()
            val phaseGroups = mutableLinkedMapOf<String, MutableList<AiLessonEntry>>()

            for (entry in entries) {
                if (entry.slug == "overview") continue
                val key = entry.phaseTitle ?: "Other"
                phaseGroups.getOrPut(key) {
                    phaseOrder.add(key)
                    mutableListOf()
                }.add(entry)
            }

            val children = mutableListOf<DocsNavNode>()

            // Overview link (direct, no children)
            children.add(DocsNavNode(title = "Overview", path = ""))

            // Phase groups
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

        private fun <K, V> mutableLinkedMapOf(): LinkedHashMap<K, V> = LinkedHashMap()

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
