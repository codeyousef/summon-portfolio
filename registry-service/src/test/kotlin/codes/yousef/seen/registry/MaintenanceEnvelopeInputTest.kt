package codes.yousef.seen.registry

import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MaintenanceEnvelopeInputTest {
    private val envelope = """{"signatures":[],"signed":{"_type":"targets"}}""".encodeToByteArray()
    private val encoded = Base64.getEncoder().encodeToString(envelope)

    private fun environment(mode: RegistryMaintenanceMode, extra: Map<String, String> = emptyMap()) = mapOf(
        "REGISTRY_STORAGE_MODE" to "memory",
    ) + TufRole.ONLINE.mapIndexed { index, role ->
        "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" to (index + 71).toString(16).padStart(2, '0').repeat(32)
    }.toMap() + mode.signingRoles.associate { role ->
        "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL" to "https://$role.signers.example.test/sign"
    } + extra

    private fun config(mode: RegistryMaintenanceMode, extra: Map<String, String> = emptyMap()) =
        RegistryMaintenanceConfig.fromEnvironment(mode, environment(mode, extra))

    @Test
    fun `importers parse with or without the file argument`() {
        assertNull(RegistryMaintenanceInvocation.parse(arrayOf("import-offline-targets-renewal"))?.input)
        assertNull(RegistryMaintenanceInvocation.parse(arrayOf("import-offline-root-rotation"))?.input)
        val rotation = requireNotNull(RegistryMaintenanceInvocation.parse(arrayOf("import-offline-targets-rotation", "security")))
        assertEquals(RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY, rotation.mode)
        assertNull(rotation.input)
        assertEquals(
            "N.root.json",
            RegistryMaintenanceInvocation.parse(arrayOf("import-offline-root-rotation", "N.root.json"))?.input.toString(),
        )
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceInvocation.parse(arrayOf("import-offline-targets-rotation"))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceInvocation.parse(arrayOf("import-offline-root-rotation", "a", "b"))
        }
    }

    @Test
    fun `envelope variable is decoded in memory for each consuming importer`() {
        listOf(
            RegistryMaintenanceMode.TARGETS_RENEWAL,
            RegistryMaintenanceMode.TARGETS_ROTATION_RELEASES,
            RegistryMaintenanceMode.TARGETS_ROTATION_SECURITY,
            RegistryMaintenanceMode.ROOT_IMPORT,
        ).forEach { mode ->
            val config = config(mode, mapOf(MAINTENANCE_ENVELOPE_VARIABLE to encoded))
            assertContentEquals(envelope, resolveMaintenanceInput(RegistryMaintenanceInvocation(mode), config))
        }
    }

    @Test
    fun `file input still works and both or neither are rejected`() {
        val mode = RegistryMaintenanceMode.TARGETS_RENEWAL
        val file = Files.createTempFile("candidate", ".json")
        try {
            Files.write(file, envelope)
            assertContentEquals(envelope, resolveMaintenanceInput(RegistryMaintenanceInvocation(mode, file), config(mode)))
            val both = config(mode, mapOf(MAINTENANCE_ENVELOPE_VARIABLE to encoded))
            assertFailsWith<IllegalArgumentException> {
                resolveMaintenanceInput(RegistryMaintenanceInvocation(mode, file), both)
            }
            assertFailsWith<IllegalArgumentException> {
                resolveMaintenanceInput(RegistryMaintenanceInvocation(mode), config(mode))
            }
            // Ambiguity fails before any cloud resource is opened.
            assertFailsWith<IllegalArgumentException> {
                RegistryMaintenanceRuntime.run(RegistryMaintenanceInvocation(mode, file), both)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `envelope must be strict padded size capped Base64`() {
        val mode = RegistryMaintenanceMode.ROOT_IMPORT
        listOf(
            "",
            "YWI",
            "YW-_",
            " $encoded",
            "$encoded\n",
            "YQ==YQ==",
            Base64.getEncoder().encodeToString(ByteArray(MAXIMUM_MAINTENANCE_ENVELOPE_BYTES + 1)),
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value.take(32)) {
                config(mode, mapOf(MAINTENANCE_ENVELOPE_VARIABLE to value))
            }
        }
        assertEquals(
            MAXIMUM_MAINTENANCE_ENVELOPE_BYTES,
            decodeMaintenanceEnvelope(Base64.getEncoder().encodeToString(ByteArray(MAXIMUM_MAINTENANCE_ENVELOPE_BYTES))).size,
        )
    }

    @Test
    fun `every non consuming runtime rejects the envelope variable`() {
        val supplied = mapOf(MAINTENANCE_ENVELOPE_VARIABLE to encoded)
        RegistryMaintenanceMode.entries.filterNot(RegistryMaintenanceMode::acceptsEnvelopeInput).forEach { mode ->
            assertFailsWith<IllegalArgumentException>(mode.command) { config(mode, supplied) }
        }
        assertFailsWith<IllegalArgumentException> {
            config(RegistryMaintenanceMode.TARGETS_RENEWAL, mapOf("REGISTRY_MAINTENANCE_OTHER" to "x"))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(mapOf("REGISTRY_STORAGE_MODE" to "memory") + supplied)
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SCAN, supplied)
        }
        assertFailsWith<IllegalArgumentException> { TufSignerServerConfig.fromEnvironment(supplied) }
        assertFailsWith<IllegalArgumentException> { OfflineBootstrapConfig.fromEnvironment(supplied) }
        assertFailsWith<IllegalArgumentException> { OfflineTargetsRenewalConfig.fromEnvironment(supplied) }
        assertFailsWith<IllegalArgumentException> { OfflineTargetsRotationConfig.fromEnvironment(supplied) }
        assertFailsWith<IllegalArgumentException> { OfflineRootRotationConfig.fromEnvironment(supplied) }
    }
}
