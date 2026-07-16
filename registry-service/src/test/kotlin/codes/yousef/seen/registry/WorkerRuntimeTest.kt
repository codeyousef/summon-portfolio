package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkerRuntimeTest {
    private val common = mapOf(
        "GOOGLE_CLOUD_PROJECT" to "project",
        "REGISTRY_FIRESTORE_DATABASE" to "registry-dev",
        "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
        "REGISTRY_ENVIRONMENT" to "development",
        "REGISTRY_PUBLIC_DELAY_SECONDS" to "259200",
    )

    @Test
    fun `scanner config needs no writer secret signing key or public bucket`() {
        val config = RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SCAN, common)
        assertEquals(RegistryWorkerMode.SCAN, config.mode)
        assertNull(config.publicBucket)
        assertEquals(emptyMap(), config.kmsKeyVersions)
    }

    @Test
    fun `source config requires a complete forge credential and ignores writer auth`() {
        assertFailsWith<IllegalArgumentException> {
            RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SOURCE, common)
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryWorkerConfig.fromEnvironment(
                RegistryWorkerMode.SOURCE,
                common + ("REGISTRY_GITHUB_APP_ID" to "12345"),
            )
        }
        val config = RegistryWorkerConfig.fromEnvironment(
            RegistryWorkerMode.SOURCE,
            common + mapOf(
                "REGISTRY_GITHUB_APP_ID" to "12345",
                "REGISTRY_GITHUB_APP_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----",
            ),
        )
        assertEquals("12345", config.githubAppId)
        assertEquals(true, config.githubAppPrivateKeyPem?.contains("BEGIN PRIVATE KEY"))
    }

    @Test
    fun `promoter accepts security public key but rejects security signing authority`() {
        val promoter = common + mapOf(
            "REGISTRY_PUBLIC_BUCKET" to "public",
            "REGISTRY_METADATA_BUCKET" to "metadata",
            "REGISTRY_KMS_RELEASES_KEY_VERSION" to "releases-version",
            "REGISTRY_KMS_RELEASES_PUBLIC_KEY_HEX" to "1".repeat(64),
            "REGISTRY_KMS_SECURITY_PUBLIC_KEY_HEX" to "2".repeat(64),
            "REGISTRY_KMS_SNAPSHOT_KEY_VERSION" to "snapshot-version",
            "REGISTRY_KMS_SNAPSHOT_PUBLIC_KEY_HEX" to "3".repeat(64),
            "REGISTRY_KMS_TIMESTAMP_KEY_VERSION" to "timestamp-version",
            "REGISTRY_KMS_TIMESTAMP_PUBLIC_KEY_HEX" to "4".repeat(64),
        )
        val config = RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.PROMOTE, promoter)
        assertEquals(setOf("releases", "snapshot", "timestamp"), config.kmsKeyVersions.keys)
        assertFailsWith<IllegalArgumentException> {
            RegistryWorkerConfig.fromEnvironment(
                RegistryWorkerMode.PROMOTE,
                promoter + ("REGISTRY_KMS_SECURITY_KEY_VERSION" to "forbidden-version"),
            )
        }
    }
}
