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

        // Phase groups should have children
        val phase1 = root.children[1]
        assertTrue(phase1.title.startsWith("Phase 1"))
        assertTrue(phase1.children.isNotEmpty())
        assertTrue(phase1.children.first().title.contains("1.1"))
    }

    @Test
    fun `neighbors link correctly`() {
        // Overview has no prev, first lesson as next
        val overviewNeighbors = catalog.neighbors("overview")
        assertNull(overviewNeighbors.previous)
        assertNotNull(overviewNeighbors.next)

        // First lesson has overview as prev
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
}
