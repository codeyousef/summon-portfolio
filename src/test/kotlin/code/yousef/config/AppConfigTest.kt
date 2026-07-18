package code.yousef.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `registry routing stays disabled when no registry values are configured`() {
        val config = loadAppConfig(mapOf("USE_LOCAL_STORE" to "true"))

        assertNull(config.registryPublicHost)
        assertNull(config.registryUpstreamUrl)
        assertNull(config.registryReleaseActionsUpstreamUrl)
        assertNull(config.registrySecurityActionsUpstreamUrl)
    }

    @Test
    fun `registry routing requires host and all three isolated origins`() {
        val partialConfigurations = listOf(
            mapOf("SEEN_REGISTRY_UPSTREAM_URL" to API_URL),
            mapOf(
                "SEEN_REGISTRY_PUBLIC_HOST" to "seen.dev.yousef.codes",
                "SEEN_REGISTRY_UPSTREAM_URL" to API_URL,
                "SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL" to RELEASE_URL,
            ),
            mapOf("SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL" to SECURITY_URL),
        )

        partialConfigurations.forEach { registryEnvironment ->
            val error = assertFailsWith<IllegalArgumentException> {
                loadAppConfig(mapOf("USE_LOCAL_STORE" to "true") + registryEnvironment)
            }
            assertTrue(error.message.orEmpty().contains("all three isolated upstream URLs"))
        }
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
