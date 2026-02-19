package code.yousef.portfolio.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiCurriculumCatalogTest {

    private val catalog = AiCurriculumCatalog()

    @Test
    fun `parses overview as first entry`() {
        val entries = catalog.allEntries()
        assertTrue(entries.isNotEmpty())
        val overview = entries.first()
        assertEquals("overview", overview.slug)
        assertNull(overview.sectionId)
        assertEquals("Overview", overview.title)
    }

    @Test
    fun `parses numbered subsections`() {
        val entry = catalog.find("1-1")
        assertNotNull(entry)
        assertEquals("1.1", entry.sectionId)
        assertTrue(entry.title.contains("1.1"))
        assertEquals(1, entry.phaseNumber)
    }

    @Test
    fun `parses capstone section`() {
        val entry = catalog.find("14-capstone")
        assertNotNull(entry)
        assertEquals("Capstone Projects", entry.title)
        assertEquals(14, entry.phaseNumber)
    }

    @Test
    fun `parses appendix sections`() {
        val entry = catalog.find("appendix-recommended-hardware-progression")
        assertNotNull(entry)
        assertEquals("Recommended Hardware Progression", entry.title)
        assertEquals("Appendix", entry.phaseTitle)
    }

    @Test
    fun `nav tree has correct structure`() {
        val tree = catalog.navTree()
        assertEquals(1, tree.sections.size)
        val root = tree.sections.first()
        assertEquals("AI Curriculum", root.title)

        // First child should be Overview
        val overview = root.children.first()
        assertEquals("Overview", overview.title)
        assertTrue(overview.children.isEmpty()) // direct link

        // Phase groups should have children (lessons)
        val phase1 = root.children[1]
        assertTrue(phase1.title.startsWith("Phase 1"))
        assertTrue(phase1.children.isNotEmpty())
        assertTrue(phase1.children.first().title.contains("1.1"))

        // Lessons should now have sub-lesson children (3-level hierarchy)
        val lesson11 = phase1.children.first()
        assertTrue(lesson11.children.isNotEmpty(), "Lesson 1.1 should have sub-lesson children")
    }

    @Test
    fun `neighbors link correctly`() {
        // Overview has no prev, first lesson as next
        val overviewNeighbors = catalog.neighbors("overview")
        assertNull(overviewNeighbors.previous)
        assertNotNull(overviewNeighbors.next)

        // First lesson has overview as prev, first sub-lesson as next
        val firstLesson = catalog.neighbors("1-1")
        assertNotNull(firstLesson.previous)
        assertEquals("Overview", firstLesson.previous!!.title)
        assertNotNull(firstLesson.next)
    }

    @Test
    fun `all entries have non-empty markdown`() {
        catalog.allEntries().forEach { entry ->
            assertTrue(entry.markdown.isNotBlank(), "Entry ${entry.slug} has empty markdown")
        }
    }

    @Test
    fun `has entries for all 14 phases`() {
        val phaseNumbers = catalog.allEntries()
            .mapNotNull { it.phaseNumber }
            .distinct()
            .sorted()
        assertEquals((1..14).toList(), phaseNumbers)
    }

    @Test
    fun `front matter is stripped from markdown content`() {
        val entry = catalog.find("1-1")
        assertNotNull(entry)
        // Markdown should not contain the YAML front matter delimiters
        assertTrue(!entry.markdown.startsWith("---"), "Markdown should not start with front matter")
    }

    @Test
    fun `all expected slugs exist`() {
        val expectedSlugs = listOf(
            "overview",
            "1-1", "1-2", "1-3",
            "2-1", "2-2", "2-3",
            "3-1", "3-2", "3-3",
            "4-1", "4-2", "4-3",
            "5-1", "5-2", "5-3",
            "6-1", "6-2",
            "7-1", "7-2", "7-3",
            "8-1", "8-2", "8-3",
            "9-1", "9-2",
            "10-1", "10-2", "10-3",
            "11-1", "11-2", "11-3",
            "12-1", "12-2", "12-3",
            "13-1", "13-2", "13-3",
            "14-capstone",
            "appendix-recommended-hardware-progression",
            "appendix-key-libraries-to-master",
            "appendix-reading-list-papers",
            "appendix-how-to-use-this-curriculum"
        )
        for (slug in expectedSlugs) {
            assertNotNull(catalog.find(slug), "Missing lesson for slug: $slug")
        }
    }

    // ── Sub-lesson tests ──────────────────────────────────────────────────

    @Test
    fun `splits lesson at H2 boundaries`() {
        val subs = catalog.subLessonsOf("1-1")
        assertTrue(subs.isNotEmpty(), "Lesson 1-1 should have sub-lessons")
        // 1-1.md has 5 content H2 sections (numbered 1-5), plus Checkpoint/Key Takeaways/Further Reading appended to last
        assertTrue(subs.size >= 3, "Should have at least 3 sub-lessons, got ${subs.size}")
        // All sub-lessons should have the right parent
        subs.forEach { sub ->
            assertEquals("1-1", sub.parentSlug)
        }
        // Indices should be 1-based sequential
        assertEquals((1..subs.size).toList(), subs.map { it.index })
    }

    @Test
    fun `sub-lesson titles come from H2 headings`() {
        val subs = catalog.subLessonsOf("1-1")
        assertTrue(subs.isNotEmpty())
        // First sub-lesson should be the first H2 section
        assertTrue(subs.first().title.contains("Python Patterns"), "First sub should be about Python Patterns, got: ${subs.first().title}")
    }

    @Test
    fun `intro is separated from sub-lessons`() {
        val intro = catalog.introOf("1-1")
        assertTrue(intro.isNotBlank(), "Intro should not be blank")
        // Intro should not start with ## (it's the content before the first H2)
        assertTrue(!intro.trimStart().startsWith("## "), "Intro should not start with an H2 heading")
    }

    @Test
    fun `findSub returns correct sub-lesson`() {
        val sub = catalog.findSub("1-1", 1)
        assertNotNull(sub, "Should find sub-lesson 1 of 1-1")
        assertEquals("1-1", sub.parentSlug)
        assertEquals(1, sub.index)
        assertTrue(sub.markdown.startsWith("## "), "Sub-lesson markdown should start with H2")
    }

    @Test
    fun `findSub returns null for invalid index`() {
        assertNull(catalog.findSub("1-1", 0))
        assertNull(catalog.findSub("1-1", 999))
        assertNull(catalog.findSub("nonexistent", 1))
    }

    @Test
    fun `sub-lesson neighbors chain correctly`() {
        val subs = catalog.subLessonsOf("1-1")
        assertTrue(subs.size >= 2, "Need at least 2 sub-lessons to test chaining")

        // First sub-lesson's prev should be the parent lesson
        val first = catalog.neighbors("1-1/1")
        assertNotNull(first.previous, "First sub-lesson should have prev (the parent lesson)")

        // Second sub-lesson's prev should be the first sub-lesson
        val second = catalog.neighbors("1-1/2")
        assertNotNull(second.previous, "Second sub-lesson should have prev")
        assertEquals("/1-1/1", second.previous!!.path)

        // Last sub-lesson's next should be the next lesson (1-2)
        val lastIdx = subs.last().index
        val last = catalog.neighbors("1-1/$lastIdx")
        assertNotNull(last.next, "Last sub-lesson of 1-1 should have a next link")
    }

    @Test
    fun `checkpoint and key takeaways are appended to last sub-lesson`() {
        val subs = catalog.subLessonsOf("1-1")
        assertTrue(subs.isNotEmpty())
        val lastSub = subs.last()
        // "Checkpoint" or "Key Takeaways" should not be standalone sub-lessons
        subs.forEach { sub ->
            val normalizedTitle = sub.title.lowercase()
            assertTrue(
                normalizedTitle != "checkpoint" &&
                    normalizedTitle != "checkpoint exercise" &&
                    normalizedTitle != "key takeaways" &&
                    normalizedTitle != "further reading",
                "Tail section '${sub.title}' should not be a standalone sub-lesson"
            )
        }
        // The last sub-lesson's markdown should contain tail content
        assertTrue(
            lastSub.markdown.contains("Checkpoint") || lastSub.markdown.contains("Key Takeaways") || lastSub.markdown.contains("Further Reading"),
            "Last sub-lesson should contain appended tail sections"
        )
    }

    @Test
    fun `overview has no sub-lessons`() {
        val subs = catalog.subLessonsOf("overview")
        assertTrue(subs.isEmpty(), "Overview should have no sub-lessons")
    }

    @Test
    fun `splitAtH2 utility works correctly`() {
        val md = """
# Title

Intro text here.

## Section One

Content one.

## Section Two

Content two.

## Checkpoint

Review stuff.
        """.trimIndent()

        val result = AiCurriculumCatalog.splitAtH2(md)
        // First chunk is the intro (empty title)
        assertEquals("", result[0].first)
        assertTrue(result[0].second.contains("Intro text"))
        // Then the H2 sections
        assertEquals("Section One", result[1].first)
        assertTrue(result[1].second.contains("Content one"))
        assertEquals("Section Two", result[2].first)
        assertEquals("Checkpoint", result[3].first)
    }
}
