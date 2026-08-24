package code.yousef.portfolio.server

import kotlin.test.Test
import kotlin.test.assertEquals

class AetherExtensionsTimingTest {
    @Test
    fun `summon render timing is bounded and locale independent`() {
        assertEquals("summon_render;dur=12.3", summonRenderServerTiming(12_345_678L))
        assertEquals("summon_render;dur=0.0", summonRenderServerTiming(-1L))
        assertEquals("summon_render;dur=120000.0", summonRenderServerTiming(Long.MAX_VALUE))
    }
}
