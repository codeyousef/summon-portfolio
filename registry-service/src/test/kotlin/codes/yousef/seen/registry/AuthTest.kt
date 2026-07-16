package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthTest {
    private val auth = OpaqueDevWriterAuthenticator("x".repeat(32), "publisher", setOf("seen"))

    @Test
    fun `accepts exact bearer and enforces owner allowlist`() {
        assertEquals("publisher", auth.authenticate("Bearer ${"x".repeat(32)}").subject)
        auth.authorizeOwner("seen")
        assertEquals("forbidden", assertFailsWith<RegistryException> { auth.authorizeOwner("other") }.code)
    }

    @Test
    fun `does not accept a prefix or suffix token`() {
        listOf("x".repeat(31), "x".repeat(33), "bearer ${"x".repeat(32)}", null).forEach { value ->
            assertEquals("unauthenticated", assertFailsWith<RegistryException> { auth.authenticate(value) }.code)
        }
    }

    @Test
    fun `environment token trims file whitespace once and rejects embedded whitespace`() {
        val token = "t".repeat(32)
        val base = mutableMapOf(
            "REGISTRY_STORAGE_MODE" to "memory",
            "REGISTRY_OWNER_ALLOWLIST" to "seen",
            "REGISTRY_WRITER_TOKEN" to "$token \n",
        ).apply {
            TufRole.ONLINE.forEach { role -> put("REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64", "test-$role") }
        }
        assertEquals(token, RegistryConfig.fromEnvironment(base).writerToken)
        base["REGISTRY_WRITER_TOKEN"] = "${"t".repeat(16)} ${"t".repeat(16)}"
        assertFailsWith<IllegalArgumentException> { RegistryConfig.fromEnvironment(base) }
    }
}
