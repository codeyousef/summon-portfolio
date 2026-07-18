package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RegistryServerConfigIsolationTest {
    @Test
    fun `public API receives package buckets publisher and report review credentials only`() {
        val env = serverGcpBase() + mapOf(
            "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
            "REGISTRY_PUBLIC_BUCKET" to "public",
            "REGISTRY_OWNER_ALLOWLIST" to "seen",
            "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
            "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
        )
        val config = RegistryConfig.fromEnvironment(env)
        assertEquals(RegistryServerMode.PUBLIC_API, config.serverMode)
        assertEquals(emptySet(), config.remoteOnlineSignerTargets.keys)
        assertNull(config.securityToken)

        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_SECURITY_TOKEN" to "s".repeat(32)))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_TUF_RELEASES_SIGNER_URL" to serverSignerEndpoint(TufRole.RELEASES)))
        }
    }

    @Test
    fun `release actions receive metadata publisher credential and release signer roles only`() {
        val mode = RegistryServerMode.RELEASE_ACTIONS
        val env = serverGcpBase() +
            mapOf(
                "REGISTRY_SERVER_MODE" to mode.environmentValue,
                "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
            ) + serverSignerEnvironment(mode.signingRoles)
        val config = RegistryConfig.fromEnvironment(env)
        assertEquals(mode.signingRoles, config.remoteOnlineSignerTargets.keys)
        assertNull(config.quarantineBucket)
        assertNull(config.publicBucket)
        assertNull(config.trustAndSafetyToken)
        assertNull(config.securityToken)

        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_PUBLIC_BUCKET" to "public"))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_SECURITY_TOKEN" to "s".repeat(32)))
        }
    }

    @Test
    fun `security actions receive metadata security credential and security signer roles only`() {
        val mode = RegistryServerMode.SECURITY_ACTIONS
        val env = serverGcpBase() +
            mapOf(
                "REGISTRY_SERVER_MODE" to mode.environmentValue,
                "REGISTRY_SECURITY_TOKEN" to "s".repeat(32),
            ) + serverSignerEnvironment(mode.signingRoles)
        val config = RegistryConfig.fromEnvironment(env)
        assertEquals(mode.signingRoles, config.remoteOnlineSignerTargets.keys)
        assertEquals("", config.writerToken)
        assertNull(config.quarantineBucket)
        assertNull(config.publicBucket)
        assertNull(config.trustAndSafetyToken)

        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_WRITER_TOKEN" to "w".repeat(32)))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env + ("REGISTRY_QUARANTINE_BUCKET" to "quarantine"))
        }
    }
}

private fun serverGcpBase(): Map<String, String> = mapOf(
    "REGISTRY_STORAGE_MODE" to "gcp",
    "GOOGLE_CLOUD_PROJECT" to "seen-dev",
    "REGISTRY_METADATA_BUCKET" to "metadata",
) + TufRole.ONLINE.mapIndexed { index, role ->
    "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" to
        (index + 21).toString(16).padStart(2, '0').repeat(32)
}.toMap()

private fun serverSignerEnvironment(roles: Set<String>): Map<String, String> = roles.associate { role ->
    "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL" to serverSignerEndpoint(role)
}

private fun serverSignerEndpoint(role: String): String = "https://$role.example.run.app/sign"
