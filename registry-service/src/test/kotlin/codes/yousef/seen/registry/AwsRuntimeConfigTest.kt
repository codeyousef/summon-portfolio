package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsRuntimeConfigTest {
    private fun publicKeys(offset: Int) = TufRole.ONLINE.mapIndexed { index, role ->
        "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" to
            (index + offset).toString(16).padStart(2, '0').repeat(32)
    }.toMap()

    private fun productionReadOnly(): Map<String, String> = mapOf(
        "REGISTRY_STORAGE_MODE" to "dynamodb",
        "AWS_REGION" to "eu-north-1",
        "REGISTRY_ENVIRONMENT" to "production",
        "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
        "REGISTRY_ORIGIN" to "https://seen.yousef.codes/packages",
        "REGISTRY_SERVER_MODE" to RegistryServerMode.READ_ONLY_PUBLIC_API.environmentValue,
        "REGISTRY_PUBLIC_BUCKET" to "seen-registry-prod-public",
        "REGISTRY_METADATA_BUCKET" to "seen-registry-prod-metadata",
    ) + publicKeys(31)

    @Test
    fun `production read only API selects DynamoDB and native S3 by default`() {
        val config = RegistryConfig.fromEnvironment(productionReadOnly())
        assertTrue(config.usesCloudStorage)
        assertEquals("eu-north-1", config.awsRegion)
        assertEquals("seen-registry-prod", config.dynamoDbTablePrefix)
        assertNull(config.projectId)
        val objectStore = config.effectiveObjectStoreConfig()
        assertEquals(RegistryObjectStoreProvider.S3, objectStore.provider)
        assertEquals("eu-north-1", objectStore.region)
        assertNull(objectStore.credentials)
        assertNull(objectStore.endpoint)
        assertEquals(setOf(RegistryBucketRole.PUBLIC, RegistryBucketRole.METADATA), objectStore.buckets.roles)
    }

    @Test
    fun `DynamoDB mode fails closed on cross cloud and mismatched identity configuration`() {
        val base = productionReadOnly()
        listOf(
            base + ("GOOGLE_CLOUD_PROJECT" to "portfolio-476219"),
            base + ("REGISTRY_FIRESTORE_DATABASE" to "seen-registry-prod"),
            base + ("REGISTRY_DYNAMODB_TABLE_PREFIX" to "seen-registry-dev"),
            base - "AWS_REGION",
            base + ("AWS_REGION" to "not a region"),
            base + ("REGISTRY_OBJECT_STORE_PROVIDER" to "gcs"),
            base + ("REGISTRY_R2_ACCESS_KEY_ID" to "static"),
            base + ("REGISTRY_METADATA_BUCKET" to "seen-registry-prod-public"),
            base + ("REGISTRY_PUBLIC_BUCKET" to "Bad.Bucket"),
            base + ("REGISTRY_AWS_KMS_TIMESTAMP_KEY_ARN" to
                "arn:aws:kms:eu-north-1:123456789012:key/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"),
        ).forEach { environment ->
            assertFailsWith<IllegalArgumentException>(environment.toString()) {
                RegistryConfig.fromEnvironment(environment)
            }
        }
    }

    @Test
    fun `GCP mode rejects DynamoDB and S3 configuration`() {
        val gcp = mapOf(
            "REGISTRY_STORAGE_MODE" to "gcp",
            "REGISTRY_ENVIRONMENT" to "production",
            "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
            "REGISTRY_ORIGIN" to "https://seen.yousef.codes/packages",
            "REGISTRY_FIRESTORE_DATABASE" to "seen-registry-prod",
            "REGISTRY_SERVER_MODE" to RegistryServerMode.READ_ONLY_PUBLIC_API.environmentValue,
            "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
            "REGISTRY_PUBLIC_BUCKET" to "seen-public",
            "REGISTRY_METADATA_BUCKET" to "seen-metadata",
        ) + publicKeys(31)
        RegistryConfig.fromEnvironment(gcp)
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(gcp + ("REGISTRY_DYNAMODB_TABLE_PREFIX" to "seen-registry-prod"))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(gcp + mapOf("REGISTRY_OBJECT_STORE_PROVIDER" to "s3", "AWS_REGION" to "eu-north-1"))
        }
    }

    @Test
    fun `development release actions use DynamoDB S3 metadata and role locked signers`() {
        val environment = mapOf(
            "REGISTRY_STORAGE_MODE" to "dynamodb",
            "AWS_REGION" to "eu-north-1",
            "REGISTRY_SERVER_MODE" to RegistryServerMode.RELEASE_ACTIONS.environmentValue,
            "REGISTRY_METADATA_BUCKET" to "seen-registry-dev-metadata",
            "REGISTRY_WRITER_TOKEN" to "w".repeat(40),
        ) + publicKeys(51) + RegistryServerMode.RELEASE_ACTIONS.signingRoles.associate { role ->
            "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL" to "https://$role.signers.internal.example/sign"
        }
        val config = RegistryConfig.fromEnvironment(environment)
        assertEquals("seen-registry-dev", config.dynamoDbTablePrefix)
        assertEquals(setOf(RegistryBucketRole.METADATA), config.effectiveObjectStoreConfig().buckets.roles)
        assertEquals(RegistryObjectStoreProvider.S3, config.effectiveObjectStoreConfig().provider)
    }

    @Test
    fun `review workers and maintenance select DynamoDB without Firestore`() {
        val worker = RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SCAN, mapOf(
            "REGISTRY_STORAGE_MODE" to "dynamodb",
            "AWS_REGION" to "eu-north-1",
            "REGISTRY_QUARANTINE_BUCKET" to "seen-registry-dev-quarantine",
            "REGISTRY_ENVIRONMENT" to "development",
        ))
        assertNull(worker.projectId)
        assertNull(worker.firestoreDatabase)
        assertEquals("seen-registry-dev", worker.dynamoDbTablePrefix)
        assertEquals(RegistryObjectStoreProvider.S3, worker.objectStoreConfig?.provider)
        assertFailsWith<IllegalArgumentException> {
            RegistryWorkerConfig.fromEnvironment(RegistryWorkerMode.SCAN, mapOf(
                "REGISTRY_STORAGE_MODE" to "dynamodb",
                "AWS_REGION" to "eu-north-1",
                "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
                "REGISTRY_QUARANTINE_BUCKET" to "seen-registry-dev-quarantine",
            ))
        }

        val mode = RegistryMaintenanceMode.REFRESH_RELEASES
        val maintenance = RegistryMaintenanceConfig.fromEnvironment(mode, mapOf(
            "REGISTRY_STORAGE_MODE" to "dynamodb",
            "AWS_REGION" to "eu-north-1",
            "REGISTRY_METADATA_BUCKET" to "seen-registry-dev-metadata",
        ) + publicKeys(61) + mode.signingRoles.associate { role ->
            "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL" to "https://$role.signers.internal.example/sign"
        })
        assertEquals("seen-registry-dev", maintenance.dynamoDbTablePrefix)
        assertEquals(RegistryObjectStoreProvider.S3, maintenance.objectStoreConfig?.provider)

        val verify = RegistryMaintenanceConfig.fromEnvironment(RegistryMaintenanceMode.ROOT_VERIFY, mapOf(
            "REGISTRY_STORAGE_MODE" to "dynamodb",
            "AWS_REGION" to "eu-north-1",
            "REGISTRY_METADATA_BUCKET" to "seen-registry-dev-metadata",
        ) + publicKeys(61))
        assertNull(verify.dynamoDbTablePrefix)
        assertFailsWith<IllegalArgumentException> {
            RegistryMaintenanceConfig.fromEnvironment(RegistryMaintenanceMode.ROOT_VERIFY, mapOf(
                "REGISTRY_STORAGE_MODE" to "dynamodb",
                "AWS_REGION" to "eu-north-1",
                "REGISTRY_DYNAMODB_TABLE_PREFIX" to "seen-registry-dev",
                "REGISTRY_METADATA_BUCKET" to "seen-registry-dev-metadata",
            ) + publicKeys(61))
        }
    }

    @Test
    fun `S3 object storage forbids static keys endpoints and invalid buckets`() {
        val roles = setOf(RegistryBucketRole.METADATA)
        val good = mapOf(
            "REGISTRY_OBJECT_STORE_PROVIDER" to "s3",
            "AWS_REGION" to "eu-north-1",
            "REGISTRY_METADATA_BUCKET" to "seen-registry-dev-metadata",
        )
        assertEquals(RegistryObjectStoreProvider.S3, RegistryObjectStoreConfig.fromEnvironment(good, roles).provider)
        listOf(
            good + ("REGISTRY_R2_ENDPOINT" to "https://x.r2.cloudflarestorage.com"),
            good + ("REGISTRY_R2_SECRET_ACCESS_KEY" to "secret"),
            good - "AWS_REGION",
            good + ("REGISTRY_METADATA_BUCKET" to "xn--bucket"),
            good + ("REGISTRY_METADATA_BUCKET" to "with.dots"),
        ).forEach { environment ->
            assertFailsWith<IllegalArgumentException>(environment.toString()) {
                RegistryObjectStoreConfig.fromEnvironment(environment, roles)
            }
        }
    }
}
