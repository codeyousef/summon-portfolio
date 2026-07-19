package codes.yousef.seen.registry

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryServerConfigIsolationTest {
    @Test
    fun `production read only API accepts only public read credentials and buckets`() {
        val env = serverGcpBase() + mapOf(
            "REGISTRY_ENVIRONMENT" to "production",
            "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
            "REGISTRY_ORIGIN" to "https://seen.yousef.codes/packages",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-prod",
            "REGISTRY_SERVER_MODE" to RegistryServerMode.READ_ONLY_PUBLIC_API.environmentValue,
            "REGISTRY_PUBLIC_BUCKET" to "public",
        )
        val config = RegistryConfig.fromEnvironment(env)

        assertEquals(RegistryServerMode.READ_ONLY_PUBLIC_API, config.serverMode)
        assertTrue(config.serverMode.exposesRegistryRoutes)
        assertFalse(config.serverMode.exposesRegistryMutations)
        assertNull(config.quarantineBucket)
        assertEquals("public", config.publicBucket)
        assertEquals("", config.writerMode)
        assertEquals("", config.writerToken)
        assertEquals("", config.writerPrincipal)
        assertEquals(emptySet(), config.ownerAllowlist)
        assertFalse(config.writersEnabled)
        assertEquals(Duration.ZERO, config.publicDelay)
        assertNull(config.trustAndSafetyToken)
        assertEquals("", config.trustAndSafetyPrincipal)
        assertNull(config.securityToken)
        assertEquals("", config.securityPrincipal)
        assertEquals(emptySet(), config.remoteOnlineSignerTargets.keys)

        listOf(
            "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
            "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
            "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
            "REGISTRY_SECURITY_TOKEN" to "s".repeat(32),
            "REGISTRY_WRITERS_ENABLED" to "false",
            "REGISTRY_PUBLIC_DELAY_SECONDS" to "0",
        ).forEach { forbidden ->
            assertFailsWith<IllegalArgumentException>(forbidden.first) {
                RegistryConfig.fromEnvironment(env + forbidden)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(env - "REGISTRY_PUBLIC_BUCKET")
        }
        listOf(
            "REGISTRY_REPOSITORY_ID" to "seen-dev-registry-v1",
            "REGISTRY_ORIGIN" to "https://seen.dev.yousef.codes/packages",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-dev",
        ).forEach { mismatch ->
            assertFailsWith<IllegalArgumentException>(mismatch.first) {
                RegistryConfig.fromEnvironment(env + mismatch)
            }
        }
    }

    @Test
    fun `production rejects every credentialed or action server mode`() {
        val publicApi = serverGcpBase() + mapOf(
            "REGISTRY_ENVIRONMENT" to "production",
            "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
            "REGISTRY_ORIGIN" to "https://seen.yousef.codes/packages",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-prod",
            "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
            "REGISTRY_PUBLIC_BUCKET" to "public",
            "REGISTRY_OWNER_ALLOWLIST" to "seen",
            "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
            "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
        )
        assertFailsWith<IllegalArgumentException> { RegistryConfig.fromEnvironment(publicApi) }

        val developmentReadOnly = (publicApi - setOf(
            "REGISTRY_QUARANTINE_BUCKET",
            "REGISTRY_OWNER_ALLOWLIST",
            "REGISTRY_WRITER_TOKEN",
            "REGISTRY_TRUST_AND_SAFETY_TOKEN",
        )) + ("REGISTRY_SERVER_MODE" to RegistryServerMode.READ_ONLY_PUBLIC_API.environmentValue) +
            ("REGISTRY_ENVIRONMENT" to "development")
        assertFailsWith<IllegalArgumentException> { RegistryConfig.fromEnvironment(developmentReadOnly) }
    }

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
