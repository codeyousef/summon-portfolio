package codes.yousef.seen.registry

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryMaintenanceConfigTest {
    @Test
    fun `every maintenance command receives exactly its signer roles and lease capability`() {
        RegistryMaintenanceMode.entries.forEach { mode ->
            val config = RegistryMaintenanceConfig.fromEnvironment(
                mode,
                maintenanceGcpEnvironment() +
                    maintenanceSignerEnvironment(mode.signingRoles) +
                    if (mode.requiresBootstrapEnvelopes) bootstrapEnvelopes() else emptyMap(),
            )

            assertEquals(mode.signingRoles, config.remoteOnlineSignerTargets.keys, mode.command)
            assertTrue(mode.signingRoles.all { role -> mode.signingOperation?.permitsRole(role) == true }, mode.command)
            if (mode.requiresPublicationLease) {
                assertEquals("seen-registry-dev", config.firestoreDatabase, mode.command)
            } else {
                assertNull(config.firestoreDatabase, mode.command)
            }
        }
    }

    @Test
    fun `offline bootstrap importer rejects online signing and unrelated authority`() {
        val env = maintenanceGcpEnvironment() + bootstrapEnvelopes()
        val config = RegistryMaintenanceConfig.fromEnvironment(
            RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP,
            env,
        )
        assertEquals(emptySet(), config.remoteOnlineSignerTargets.keys)
        assertFalse(config.mode.requiresPublicationLease)
        assertTrue(config.mode.allowRootPointerWrite)

        listOf(
            "REGISTRY_TUF_RELEASES_SIGNER_URL" to "https://releases.example.run.app/sign",
            "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
            "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
            "REGISTRY_KMS_RELEASES_KEY_VERSION" to "projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1",
            "REGISTRY_RELEASES_SIGNING_KEY_PKCS8_BASE64" to "private",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-dev",
        ).forEach { extra ->
            assertFailsWith<IllegalArgumentException>(extra.first) {
                RegistryMaintenanceConfig.fromEnvironment(
                    RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP,
                    env + extra,
                )
            }
        }
    }

    @Test
    fun `online bootstrap rejects offline envelopes and missing or excess signer roles`() {
        val mode = RegistryMaintenanceMode.ONLINE_BOOTSTRAP
        val valid = maintenanceGcpEnvironment() + maintenanceSignerEnvironment(mode.signingRoles)
        val config = RegistryMaintenanceConfig.fromEnvironment(mode, valid)
        assertEquals(TufRole.ONLINE.toSet(), config.remoteOnlineSignerTargets.keys)
        assertFalse(config.mode.allowRootPointerWrite)

        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(mode, valid + bootstrapEnvelopes())
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(
                mode,
                valid - maintenanceSignerUrl(TufRole.TIMESTAMP),
            )
        }
    }

    @Test
    fun `purpose refresh commands cannot receive the opposite delegated signer`() {
        val releaseMode = RegistryMaintenanceMode.REFRESH_RELEASES
        val release = maintenanceGcpEnvironment() + maintenanceSignerEnvironment(releaseMode.signingRoles)
        assertEquals(
            setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            RegistryMaintenanceConfig.fromEnvironment(releaseMode, release).remoteOnlineSignerTargets.keys,
        )
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(
                releaseMode,
                release + (maintenanceSignerUrl(TufRole.SECURITY) to maintenanceSignerEndpoint(TufRole.SECURITY)),
            )
        }

        val securityMode = RegistryMaintenanceMode.REFRESH_SECURITY
        val security = maintenanceGcpEnvironment() + maintenanceSignerEnvironment(securityMode.signingRoles)
        assertEquals(
            setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            RegistryMaintenanceConfig.fromEnvironment(securityMode, security).remoteOnlineSignerTargets.keys,
        )
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(
                securityMode,
                security + (maintenanceSignerUrl(TufRole.RELEASES) to maintenanceSignerEndpoint(TufRole.RELEASES)),
            )
        }
    }

    @Test
    fun `maintenance accepts the inert container port but rejects server mode`() {
        val environment = maintenanceGcpEnvironment() + ("PORT" to "8080")
        val config = RegistryMaintenanceConfig.fromEnvironment(
            RegistryMaintenanceMode.ROOT_VERIFY,
            environment,
        )

        assertEquals(RegistryMaintenanceMode.ROOT_VERIFY, config.mode)
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(
                RegistryMaintenanceMode.ROOT_VERIFY,
                environment + ("REGISTRY_SERVER_MODE" to "public-api"),
            )
        }
    }

    @Test
    fun `combined bootstrap command is rejected and explicit phases parse separately`() {
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceInvocation.parse(arrayOf("bootstrap"))
        }
        assertEquals(
            RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP,
            RegistryMaintenanceInvocation.parse(arrayOf("import-offline-bootstrap"))?.mode,
        )
        assertEquals(
            RegistryMaintenanceMode.ONLINE_BOOTSTRAP,
            RegistryMaintenanceInvocation.parse(arrayOf("bootstrap-online"))?.mode,
        )
        assertEquals(
            RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY,
            RegistryMaintenanceInvocation.parse(
                arrayOf("import-offline-targets-rotation", "security", "2.targets.json"),
            )?.mode,
        )
    }
}

private fun maintenanceGcpEnvironment(): Map<String, String> = mapOf(
    "REGISTRY_STORAGE_MODE" to "gcp",
    "GOOGLE_CLOUD_PROJECT" to "seen-dev",
    "REGISTRY_METADATA_BUCKET" to "metadata",
) + maintenancePublicKeys().mapKeys { (role, _) -> "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" }

private fun maintenancePublicKeys(): Map<String, String> = TufRole.ONLINE.mapIndexed { index, role ->
    role to (index + 11).toString(16).padStart(2, '0').repeat(32)
}.toMap()

private fun maintenanceSignerEnvironment(roles: Set<String>): Map<String, String> = roles.associate { role ->
    maintenanceSignerUrl(role) to maintenanceSignerEndpoint(role)
}

private fun maintenanceSignerUrl(role: String): String = "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL"
private fun maintenanceSignerEndpoint(role: String): String = "https://$role.example.run.app/sign"

private fun bootstrapEnvelopes(): Map<String, String> = mapOf(
    "REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64" to "cm9vdA==",
    "REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64" to "dGFyZ2V0cw==",
)

internal fun maintenanceMemoryConfig(mode: RegistryMaintenanceMode): RegistryMaintenanceConfig =
    RegistryMaintenanceConfig(
        mode = mode,
        environment = "development",
        repositoryId = "seen-dev-registry-v1",
        registryOrigin = "https://seen.dev.yousef.codes/packages",
        storageMode = "memory",
        projectId = null,
        firestoreDatabase = null,
        metadataBucket = null,
        objectPrefix = "test",
        onlinePublicKeysHex = maintenancePublicKeys(),
        remoteOnlineSignerTargets = mode.signingRoles.associateWith { role ->
            val endpoint = URI.create(maintenanceSignerEndpoint(role))
            RemoteTufSignerTarget(endpoint, RemoteTufSignerTarget.defaultAudience(endpoint))
        },
        bootstrapRootEnvelopeBase64 = "cm9vdA==".takeIf { mode.requiresBootstrapEnvelopes },
        bootstrapTargetsEnvelopeBase64 = "dGFyZ2V0cw==".takeIf { mode.requiresBootstrapEnvelopes },
    )
