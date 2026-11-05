package code.yousef.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `Firestore migration defaults preserve the current source database`() {
        val config = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))

        assertEquals(DEFAULT_FIRESTORE_DATABASE_ID, config.firestoreDatabaseId)
        assertEquals(FirestoreWriteMode.SOURCE, config.firestoreWriteMode)
        assertNull(config.firestoreMigrationProofId)
        assertNull(config.firestoreServiceAccountJsonBase64)
        assertEquals(false, config.firestoreSeedOnStart)
    }

    @Test
    fun `Firestore startup seeding is explicit and strictly parsed`() {
        assertTrue(
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_SEED_ON_START" to "true",
                ),
            ).firestoreSeedOnStart,
        )
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_SEED_ON_START" to "yes",
                ),
            )
        }
    }

    @Test
    fun `Firestore Container credentials use only the canonical secret name`() {
        val config = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64" to " c2VydmljZS1hY2NvdW50 ",
            ),
        )

        assertEquals("c2VydmljZS1hY2NvdW50", config.firestoreServiceAccountJsonBase64)
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "GOOGLE_SERVICE_ACCOUNT_JSON_B64" to "legacy",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64" to "  ",
                ),
            )
        }
    }

    @Test
    fun `photography storage defaults preserve existing local or GCS source behavior`() {
        val local = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))
        val gcs = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "PHOTOGRAPHY_UPLOAD_BUCKET" to "existing-gcs-bucket",
            ),
        )

        assertEquals(PhotographyAssetWriteMode.SOURCE, local.photographyWriteMode)
        assertEquals(PhotographyAssetWriteMode.SOURCE, gcs.photographyWriteMode)
        assertNull(local.photographyR2BaseUrl)
        assertEquals("existing-gcs-bucket", gcs.photographyUploadBucket)
    }

    @Test
    fun `FinOps receipt storage accepts only private edge URLs and bounded sizes`() {
        val config = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "FINOPS_RECEIPT_BASE_URL" to "https://dev.yousef.codes/internal/finops",
                "FINOPS_RECEIPT_MAX_BYTES" to "1048576",
            ),
        )
        assertEquals("https://dev.yousef.codes/internal/finops", config.finOpsReceiptBaseUrl)
        assertEquals(1_048_576L, config.finOpsReceiptMaxBytes)

        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(mapOf("USE_LOCAL_STORE" to "true", "FINOPS_RECEIPT_BASE_URL" to "http://example.com/internal/finops"))
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(mapOf("USE_LOCAL_STORE" to "true", "FINOPS_RECEIPT_MAX_BYTES" to "0"))
        }
    }

    @Test
    fun `FinOps allocation projections remain disabled until explicitly marked ready`() {
        assertEquals(false, loadAppConfig(mapOf("USE_LOCAL_STORE" to "true")).finOpsAllocationEntryProjectionsReady)
        assertTrue(
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY" to "true",
                ),
            ).finOpsAllocationEntryProjectionsReady,
        )
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY" to "yes",
                ),
            )
        }
    }

    @Test
    fun `FinOps sharded rollups remain disabled until their versioned backfill is proven`() {
        assertEquals(false, loadAppConfig(mapOf("USE_LOCAL_STORE" to "true")).finOpsShardedRollupWritesEnabled)
        assertEquals(false, loadAppConfig(mapOf("USE_LOCAL_STORE" to "true")).finOpsShardedRollupsReady)
        val writesOnly = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "FINOPS_SHARDED_ROLLUP_WRITES_ENABLED" to "true",
            ),
        )
        assertTrue(writesOnly.finOpsShardedRollupWritesEnabled)
        assertEquals(false, writesOnly.finOpsShardedRollupsReady)
        val ready = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "FINOPS_SHARDED_ROLLUP_WRITES_ENABLED" to "true",
                "FINOPS_SHARDED_ROLLUPS_READY" to "true",
            ),
        )
        assertTrue(ready.finOpsShardedRollupsReady)
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FINOPS_SHARDED_ROLLUPS_READY" to "true",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FINOPS_SHARDED_ROLLUPS_READY" to "yes",
                ),
            )
        }
    }

    @Test
    fun `FinOps future-only coverage starts at an exact UTC date`() {
        val config = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "FINOPS_COVERAGE_START_DATE" to "2026-08-24",
            ),
        )
        assertEquals(1_787_529_600_000L, config.finOpsCoverageStartAt)
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(mapOf("USE_LOCAL_STORE" to "true", "FINOPS_COVERAGE_START_DATE" to "2026-02-30"))
        }
    }

    @Test
    fun `photography migration modes require explicit R2 and durable mirror configuration`() {
        val dual = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "PHOTOGRAPHY_WRITE_MODE" to "dual",
                "PHOTOGRAPHY_UPLOAD_BUCKET" to "existing-gcs-bucket",
                "PHOTOGRAPHY_R2_BASE_URL" to "https://yousef.codes/internal/media",
            ),
        )
        val target = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "PHOTOGRAPHY_WRITE_MODE" to "target",
                "PHOTOGRAPHY_UPLOAD_BUCKET" to "existing-gcs-bucket",
                "PHOTOGRAPHY_R2_BASE_URL" to "https://yousef.codes/internal/media",
                "PHOTOGRAPHY_R2_REVERSE_MIRROR" to "true",
            ),
        )

        assertEquals(PhotographyAssetWriteMode.DUAL, dual.photographyWriteMode)
        assertEquals(PhotographyAssetWriteMode.TARGET, target.photographyWriteMode)
        assertTrue(target.photographyR2ReverseMirror)

        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(mapOf("USE_LOCAL_STORE" to "true", "PHOTOGRAPHY_WRITE_MODE" to "dual"))
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "PHOTOGRAPHY_WRITE_MODE" to "target",
                    "PHOTOGRAPHY_R2_BASE_URL" to "https://yousef.codes/internal/media",
                    "PHOTOGRAPHY_R2_REVERSE_MIRROR" to "true",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "PHOTOGRAPHY_R2_REVERSE_MIRROR" to "yes",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "PHOTOGRAPHY_WRITE_MODE" to "target",
                    "PHOTOGRAPHY_R2_BASE_URL" to "http://example.com/internal/media",
                ),
            )
        }
    }

    @Test
    fun `Firestore dual and target modes require and accept a named database`() {
        listOf(FirestoreWriteMode.DUAL, FirestoreWriteMode.TARGET).forEach { mode ->
            val config = loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_DATABASE_ID" to " portfolio-me-prod ",
                    "FIRESTORE_WRITE_MODE" to mode.environmentValue,
                    "FIRESTORE_MIGRATION_PROOF_ID" to "portfolio-me-prod-initial-v1",
                ),
            )

            assertEquals("portfolio-me-prod", config.firestoreDatabaseId)
            assertEquals(mode, config.firestoreWriteMode)
            assertEquals("portfolio-me-prod-initial-v1", config.firestoreMigrationProofId)
        }
    }

    @Test
    fun `Firestore migration configuration fails closed`() {
        listOf("dual", "target").forEach { mode ->
            val missingDatabase = assertFailsWith<IllegalArgumentException> {
                loadAppConfig(
                    mapOf(
                        "USE_LOCAL_STORE" to "true",
                        "FIRESTORE_WRITE_MODE" to mode,
                    ),
                )
            }
            assertTrue(missingDatabase.message.orEmpty().contains("requires a named FIRESTORE_DATABASE_ID"))
        }

        val invalidMode = assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_DATABASE_ID" to "portfolio-me-prod",
                    "FIRESTORE_WRITE_MODE" to "DUAL",
                ),
            )
        }
        assertTrue(invalidMode.message.orEmpty().contains("must be one of"))

        val missingProof = assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_DATABASE_ID" to "portfolio-me-prod",
                    "FIRESTORE_WRITE_MODE" to "dual",
                ),
            )
        }
        assertTrue(missingProof.message.orEmpty().contains("requires FIRESTORE_MIGRATION_PROOF_ID"))

        val invalidDatabase = assertFailsWith<IllegalArgumentException> {
            loadAppConfig(
                mapOf(
                    "USE_LOCAL_STORE" to "true",
                    "FIRESTORE_DATABASE_ID" to "Portfolio/Prod",
                ),
            )
        }
        assertTrue(invalidDatabase.message.orEmpty().contains("valid named Firestore database ID"))
    }

    @Test
    fun `registry routing stays disabled when no registry values are configured`() {
        val config = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))

        assertNull(config.registryPublicHost)
        assertNull(config.registryUpstreamUrl)
        assertNull(config.registryReleaseActionsUpstreamUrl)
        assertNull(config.registrySecurityActionsUpstreamUrl)
    }

    @Test
    fun `registry routing requires the public host and API origin together`() {
        val partialConfigurations = listOf(
            mapOf("SEEN_REGISTRY_UPSTREAM_URL" to API_URL),
            mapOf("SEEN_REGISTRY_PUBLIC_HOST" to "seen.dev.yousef.codes"),
            mapOf(
                "SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL" to RELEASE_URL,
                "SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL" to SECURITY_URL,
            ),
        )

        partialConfigurations.forEach { registryEnvironment ->
            val error = assertFailsWith<IllegalArgumentException> {
                loadAppConfig(mapOf("USE_LOCAL_STORE" to "true") + registryEnvironment)
            }
            assertTrue(error.message.orEmpty().contains("public host and API upstream URL"))
        }
    }

    @Test
    fun `registry action routing requires both isolated action origins`() {
        val partialConfigurations = listOf(
            mapOf(
                "SEEN_REGISTRY_PUBLIC_HOST" to "seen.dev.yousef.codes",
                "SEEN_REGISTRY_UPSTREAM_URL" to API_URL,
                "SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL" to RELEASE_URL,
            ),
            mapOf(
                "SEEN_REGISTRY_PUBLIC_HOST" to "seen.dev.yousef.codes",
                "SEEN_REGISTRY_UPSTREAM_URL" to API_URL,
                "SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL" to SECURITY_URL,
            ),
        )

        partialConfigurations.forEach { registryEnvironment ->
            val error = assertFailsWith<IllegalArgumentException> {
                loadAppConfig(mapOf("USE_LOCAL_STORE" to "true") + registryEnvironment)
            }
            assertTrue(error.message.orEmpty().contains("both isolated action upstream URLs or neither"))
        }
    }

    @Test
    fun `accepts API-only registry routing configuration`() {
        val config = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "SEEN_REGISTRY_PUBLIC_HOST" to " seen.yousef.codes ",
                "SEEN_REGISTRY_UPSTREAM_URL" to API_URL,
            ),
        )

        assertEquals("seen.yousef.codes", config.registryPublicHost)
        assertEquals(API_URL, config.registryUpstreamUrl)
        assertNull(config.registryReleaseActionsUpstreamUrl)
        assertNull(config.registrySecurityActionsUpstreamUrl)
    }

    @Test
    fun `accepts complete isolated registry routing configuration`() {
        val config = loadAppConfig(
            mapOf(
                "USE_LOCAL_STORE" to "true",
                "SEEN_REGISTRY_PUBLIC_HOST" to " seen.dev.yousef.codes ",
                "SEEN_REGISTRY_UPSTREAM_URL" to API_URL,
                "SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL" to RELEASE_URL,
                "SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL" to SECURITY_URL,
            ),
        )

        assertEquals("seen.dev.yousef.codes", config.registryPublicHost)
        assertEquals(API_URL, config.registryUpstreamUrl)
        assertEquals(RELEASE_URL, config.registryReleaseActionsUpstreamUrl)
        assertEquals(SECURITY_URL, config.registrySecurityActionsUpstreamUrl)
    }

    private companion object {
        const val API_URL = "https://registry-api.example.run.app"
        const val RELEASE_URL = "https://registry-release-actions.example.run.app"
        const val SECURITY_URL = "https://registry-security-actions.example.run.app"
    }
}
