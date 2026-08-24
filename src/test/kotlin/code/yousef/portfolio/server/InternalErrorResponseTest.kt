package code.yousef.portfolio.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InternalErrorResponseTest {
    @Test
    fun `production response is opaque`() {
        val body = internalErrorBody("error-123", IllegalStateException("database password leaked"), debug = false)
        assertTrue(body.contains("error-123"))
        assertFalse(body.contains("database"))
        assertFalse(body.contains("IllegalStateException"))
    }

    @Test
    fun `debug details require explicit non-production mode`() {
        assertTrue(portfolioDebugErrorsEnabled(mapOf("ENVIRONMENT" to "dev", "PORTFOLIO_DEBUG_ERRORS" to "true")))
        assertFalse(portfolioDebugErrorsEnabled(mapOf("ENVIRONMENT" to "prod", "PORTFOLIO_DEBUG_ERRORS" to "true")))
        assertFalse(portfolioDebugErrorsEnabled(mapOf("ENVIRONMENT" to "dev")))
    }
}
