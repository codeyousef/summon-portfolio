package code.yousef.portfolio.server

import kotlin.test.Test
import kotlin.test.assertEquals

class DeploymentVersionTest {
    @Test
    fun `deployment version exposes the exact Cloud Run revision`() {
        assertEquals(
            "portfolio-dev-00559-cir",
            deploymentVersionPayload(mapOf("K_REVISION" to " portfolio-dev-00559-cir "))["revision"],
        )
        assertEquals("local", deploymentVersionPayload(emptyMap())["revision"])
    }
}
