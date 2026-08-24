package code.yousef.portfolio.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinOpsRoutesTest {
    @Test
    fun `only the current owner session can access FinOps`() {
        assertTrue(isFinOpsOwnerSession("owner", mustChangePassword = false, ownerUsername = "owner"))
        assertFalse(isFinOpsOwnerSession(null, mustChangePassword = false, ownerUsername = "owner"))
        assertFalse(isFinOpsOwnerSession("another-admin", mustChangePassword = false, ownerUsername = "owner"))
        assertFalse(isFinOpsOwnerSession("owner", mustChangePassword = true, ownerUsername = "owner"))
        assertFalse(isFinOpsOwnerSession("owner", mustChangePassword = false, ownerUsername = ""))
    }

    @Test
    fun `uploaded receipt names are reduced to safe basenames`() {
        assertEquals("Cloudflare-August-2026.pdf", normalizeUploadedFinOpsReceiptFilename("C:\\fakepath\\Cloudflare August 2026.pdf"))
        assertEquals("receipt", normalizeUploadedFinOpsReceiptFilename("../../..."))
    }

    @Test
    fun `FinOps ranges accept shareable ISO dates and legacy epoch milliseconds`() {
        assertEquals(1_798_761_600_000L, parseFinOpsRangeParameter("2027-01-01"))
        assertEquals(1_798_761_600_000L, parseFinOpsRangeParameter("1798761600000"))
        assertEquals(null, parseFinOpsRangeParameter("  "))
    }

    @Test
    fun `manual expense mutation ids are opaque and strictly validated`() {
        val generated = newManualFinOpsSourceRecordId()
        assertTrue(generated.matches(Regex("^owner:[a-f0-9]{32}$")))
        assertEquals(generated, requireManualFinOpsSourceRecordId(generated))
        assertEquals(generated, manualFinOpsSourceRecordId(generated))
        assertTrue(manualFinOpsSourceRecordId("owner:../../other").matches(Regex("^owner:[a-f0-9]{32}$")))
        assertFailsWith<IllegalArgumentException> { requireManualFinOpsSourceRecordId("owner:../../other") }
    }
}
