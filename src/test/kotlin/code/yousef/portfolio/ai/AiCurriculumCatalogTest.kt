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
        assertNull(overview.phaseTitle)
        assertTrue(overview.markdown.contains("Unified AI Curriculum"))
    }

    @Test
    fun `parses numbered subsections`() {
        val entry = catalog.find("1-1")
        assertNotNull(entry)
        assertEquals("1.1", entry.sectionId)
        assertTrue(entry.title.contains("Python"))
        assertEquals(1, entry.phaseNumber)
        assertTrue(entry.markdown.contains("NumPy"))
    }

    @Test
    fun `parses capstone section`() {
        val entry = catalog.find("14-capstone")
        assertNotNull(entry)
        assertNull(entry.sectionId)
        assertEquals("Capstone Projects", entry.title)
        assertEquals(14, entry.phaseNumber)
        assertTrue(entry.markdown.contains("Choose One"))
    }

    @Test
    fun `parses appendix sections`() {
        val entry = catalog.find("appendix-recommended-hardware-progression")
        assertNotNull(entry)
        assertNull(entry.sectionId)
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
}
