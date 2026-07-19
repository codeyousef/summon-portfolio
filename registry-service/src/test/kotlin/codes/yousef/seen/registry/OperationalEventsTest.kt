package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OperationalEventsTest {
    @Test
    fun `expiry event is exact sanitized and emitted only for expired trust state`() {
        val event = IllegalStateException("Signed registry metadata is expired").tufMetadataExpiryEvent(
            environment = "production",
            runtime = "service-readiness",
        )

        assertEquals(
            "event=tuf_metadata_expiry_breach environment=production runtime=service-readiness failure=IllegalStateException",
            event,
        )
        assertNull(
            IllegalStateException("Signed registry metadata is unavailable").tufMetadataExpiryEvent(
                environment = "production",
                runtime = "service-readiness",
            ),
        )
    }

    @Test
    fun `signer rejection event preserves only bounded operational fields`() {
        assertEquals(
            "event=tuf_signing_rejected role=timestamp operation=bootstrap reason=deadline_expired",
            tufSigningRejectedEvent("timestamp", "bootstrap", "deadline expired"),
        )
    }
}
