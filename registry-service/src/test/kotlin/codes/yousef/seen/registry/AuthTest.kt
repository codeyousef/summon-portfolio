package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
            "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
        ).apply {
            TufRole.ONLINE.forEach { role -> put("REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64", "test-$role") }
        }
        assertEquals(token, RegistryConfig.fromEnvironment(base).writerToken)
        base["REGISTRY_WRITER_TOKEN"] = "${"t".repeat(16)} ${"t".repeat(16)}"
        assertFailsWith<IllegalArgumentException> { RegistryConfig.fromEnvironment(base) }
    }

    @Test
    fun `server modes default to verification-only public API and declare least privilege roles`() {
        val base = mutableMapOf(
            "REGISTRY_STORAGE_MODE" to "memory",
            "REGISTRY_OWNER_ALLOWLIST" to "seen",
            "REGISTRY_WRITER_TOKEN" to "t".repeat(32),
            "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
        ).apply {
            TufRole.ONLINE.forEach { role ->
                put("REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64", "test-$role")
            }
        }

        assertEquals(RegistryServerMode.PUBLIC_API, RegistryConfig.fromEnvironment(base).serverMode)
        assertEquals(emptySet(), RegistryServerMode.PUBLIC_API.signingRoles)
        assertEquals(
            setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            RegistryServerMode.RELEASE_ACTIONS.signingRoles,
        )
        assertEquals(
            setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            RegistryServerMode.SECURITY_ACTIONS.signingRoles,
        )

        val release = base.toMutableMap().apply {
            this["REGISTRY_SERVER_MODE"] = "release-actions"
            remove("REGISTRY_OWNER_ALLOWLIST")
            remove("REGISTRY_TRUST_AND_SAFETY_TOKEN")
        }
        assertEquals(RegistryServerMode.RELEASE_ACTIONS, RegistryConfig.fromEnvironment(release).serverMode)
        assertEquals(
            RegistryServerMode.SECURITY_ACTIONS,
            RegistryServerMode.fromArgument("serve-security-actions"),
        )
        assertNull(RegistryServerMode.fromArgument("security-actions"))

        release["REGISTRY_SERVER_MODE"] = "combined"
        assertFailsWith<IllegalArgumentException> { RegistryConfig.fromEnvironment(release) }
    }
}
