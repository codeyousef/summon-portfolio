package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineConfigEnvironmentTest {
    @Test
    fun `offline bootstrap accepts the exact production repository identity`() {
        val config = OfflineBootstrapConfig.fromEnvironment(
            onlinePublicKeys + mapOf(
                "REGISTRY_ENVIRONMENT" to "production",
                "REGISTRY_REPOSITORY_ID" to "seen-prod-registry-v1",
            ),
        )

        assertEquals("production", config.environment)
        assertEquals("seen-prod-registry-v1", config.repositoryId)
    }

    @Test
    fun `offline signing rejects cross-environment and unknown repository identities`() {
        listOf(
            "production" to "seen-dev-registry-v1",
            "development" to "seen-prod-registry-v1",
            "staging" to "seen-staging-registry-v1",
        ).forEach { (environment, repositoryId) ->
            assertFailsWith<IllegalArgumentException>("$environment/$repositoryId") {
                OfflineBootstrapConfig.fromEnvironment(
                    onlinePublicKeys + mapOf(
                        "REGISTRY_ENVIRONMENT" to environment,
                        "REGISTRY_REPOSITORY_ID" to repositoryId,
                    ),
                )
            }
        }
    }

    private companion object {
        val onlinePublicKeys = mapOf(
            "REGISTRY_KMS_RELEASES_PUBLIC_KEY_HEX" to "11".repeat(32),
            "REGISTRY_KMS_SECURITY_PUBLIC_KEY_HEX" to "22".repeat(32),
            "REGISTRY_KMS_SNAPSHOT_PUBLIC_KEY_HEX" to "33".repeat(32),
            "REGISTRY_KMS_TIMESTAMP_PUBLIC_KEY_HEX" to "44".repeat(32),
        )
    }
}
