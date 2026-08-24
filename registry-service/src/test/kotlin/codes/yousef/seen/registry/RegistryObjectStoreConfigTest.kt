package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryObjectStoreConfigTest {
    @Test
    fun `GCS remains the default provider`() {
        val config = RegistryObjectStoreConfig.fromEnvironment(
            env = mapOf(
                "REGISTRY_QUARANTINE_BUCKET" to "seen-quarantine",
                "REGISTRY_PUBLIC_BUCKET" to "seen-public",
                "REGISTRY_METADATA_BUCKET" to "seen-metadata",
            ),
            requiredRoles = setOf(
                RegistryBucketRole.QUARANTINE,
                RegistryBucketRole.PUBLIC,
                RegistryBucketRole.METADATA,
            ),
        )

        assertEquals(RegistryObjectStoreProvider.GCS, config.provider)
        assertEquals("seen-public", config.buckets.require(RegistryBucketRole.PUBLIC))
    }

    @Test
    fun `R2 supports all six distinct logical bucket roles without exposing credentials`() {
        val environment = r2Environment() + RegistryBucketRole.entries.associate { role ->
            role.environmentName to "seen-${role.name.lowercase()}"
        }

        val config = RegistryObjectStoreConfig.fromEnvironment(
            environment,
            RegistryBucketRole.entries.toSet(),
        )

        assertEquals(RegistryObjectStoreProvider.R2, config.provider)
        assertEquals(RegistryBucketRole.entries.toSet(), config.buckets.roles)
        assertEquals("auto", config.region)
        assertFalse(config.toString().contains("secret-value"))
        assertFalse(config.toString().contains("access-value"))
        assertTrue(config.toString().contains("[redacted]"))
    }

    @Test
    fun `R2 fails closed on endpoint region credentials bucket aliases and excess roles`() {
        val buckets = mapOf(
            "REGISTRY_PUBLIC_BUCKET" to "seen-public",
            "REGISTRY_METADATA_BUCKET" to "seen-metadata",
        )
        val valid = r2Environment() + buckets
        val roles = setOf(RegistryBucketRole.PUBLIC, RegistryBucketRole.METADATA)

        listOf(
            valid - "REGISTRY_R2_ACCESS_KEY_ID",
            valid - "REGISTRY_R2_SECRET_ACCESS_KEY",
            valid + ("REGISTRY_R2_ENDPOINT" to "http://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"),
            valid + ("REGISTRY_R2_ENDPOINT" to "https://objects.example.com"),
            valid + ("REGISTRY_R2_REGION" to "us-east-1"),
            valid + ("REGISTRY_METADATA_BUCKET" to "seen-public"),
            valid + ("REGISTRY_BACKUP_BUCKET" to "seen-backup"),
        ).forEach { environment ->
            assertFailsWith<IllegalArgumentException> {
                RegistryObjectStoreConfig.fromEnvironment(environment, roles)
            }
        }
    }

    @Test
    fun `immutable keys are provider neutral SHA-256 addresses for every role`() {
        val digest = "a".repeat(64)

        RegistryBucketRole.entries.forEach { role ->
            val key = RegistryObjectKeys.contentAddressed("v1", role, digest)
            assertTrue(key.startsWith("v1/${role.contentNamespace}/sha256/"), role.name)
            assertTrue(key.endsWith(digest), role.name)
        }
        assertEquals(
            "v1/blobs/sha256/$digest",
            RegistryObjectKeys.contentAddressed("v1", RegistryBucketRole.PUBLIC, digest),
        )
        assertFailsWith<IllegalArgumentException> {
            RegistryObjectKeys.contentAddressed("../v1", RegistryBucketRole.PUBLIC, digest)
        }
    }

    @Test
    fun `production read only server retains Firestore while selecting R2 for objects`() {
        val environment = mapOf(
            "REGISTRY_STORAGE_MODE" to "gcp",
            "REGISTRY_ENVIRONMENT" to "production",
            "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
            "REGISTRY_ORIGIN" to "https://seen.yousef.codes/packages",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-prod",
            "REGISTRY_SERVER_MODE" to RegistryServerMode.READ_ONLY_PUBLIC_API.environmentValue,
            "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
            "REGISTRY_PUBLIC_BUCKET" to "seen-public",
            "REGISTRY_METADATA_BUCKET" to "seen-metadata",
        ) + r2Environment() + TufRole.ONLINE.mapIndexed { index, role ->
            "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" to
                (index + 31).toString(16).padStart(2, '0').repeat(32)
        }.toMap()

        val config = RegistryConfig.fromEnvironment(environment)

        assertEquals("portfolio-476219", config.projectId)
        assertEquals("seen-registry-prod", config.firestoreDatabase)
        assertEquals(RegistryObjectStoreProvider.R2, config.effectiveObjectStoreConfig().provider)
        assertEquals(
            setOf(RegistryBucketRole.PUBLIC, RegistryBucketRole.METADATA),
            config.effectiveObjectStoreConfig().buckets.roles,
        )
    }

    @Test
    fun `review workers retain Firestore while selecting least privilege R2 buckets`() {
        val environment = mapOf(
            "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-dev",
            "REGISTRY_QUARANTINE_BUCKET" to "seen-quarantine",
            "REGISTRY_ENVIRONMENT" to "development",
            "REGISTRY_PUBLIC_DELAY_SECONDS" to "259200",
        ) + r2Environment()

        val config = RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SCAN, environment)

        assertEquals("portfolio-476219", config.projectId)
        assertEquals("seen-registry-dev", config.firestoreDatabase)
        assertEquals(RegistryObjectStoreProvider.R2, config.objectStoreConfig?.provider)
        assertEquals(setOf(RegistryBucketRole.QUARANTINE), config.objectStoreConfig?.buckets?.roles)
    }

    @Test
    fun `TUF maintenance retains its Firestore lease while selecting metadata only R2`() {
        val mode = RegistryMaintenanceMode.ONLINE_BOOTSTRAP
        val environment = mapOf(
            "REGISTRY_STORAGE_MODE" to "gcp",
            "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-dev",
            "REGISTRY_METADATA_BUCKET" to "seen-metadata",
        ) + r2Environment() + TufRole.ONLINE.mapIndexed { index, role ->
            "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" to
                (index + 41).toString(16).padStart(2, '0').repeat(32)
        }.toMap() + mode.signingRoles.associate { role ->
            "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL" to "https://$role.example.run.app/sign"
        }

        val config = RegistryMaintenanceConfig.fromEnvironment(mode, environment)

        assertEquals("seen-registry-dev", config.firestoreDatabase)
        assertEquals(RegistryObjectStoreProvider.R2, config.objectStoreConfig?.provider)
        assertEquals(setOf(RegistryBucketRole.METADATA), config.objectStoreConfig?.buckets?.roles)
    }
}

private fun r2Environment(): Map<String, String> = mapOf(
    "REGISTRY_OBJECT_STORE_PROVIDER" to "r2",
    "REGISTRY_R2_ENDPOINT" to "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
    "REGISTRY_R2_REGION" to "auto",
    "REGISTRY_R2_ACCESS_KEY_ID" to "access-value",
    "REGISTRY_R2_SECRET_ACCESS_KEY" to "secret-value",
)
