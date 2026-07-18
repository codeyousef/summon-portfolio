package codes.yousef.seen.registry

import com.google.auth.oauth2.IdToken
import com.google.auth.oauth2.IdTokenProvider
import java.net.URI
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RemoteSignerRuntimeWiringTest {
    @Test
    fun `Cloud Run token provider requests an OIDC ID token for the configured origin audience`() {
        val endpoint = URI.create("https://releases.example.run.app/sign")
        val target = RemoteTufSignerTarget(endpoint, RemoteTufSignerTarget.defaultAudience(endpoint))
        var observedAudience: String? = null
        val jwt = testIdToken()
        val provider = GoogleCloudRunTufIdTokenProvider(
            target,
            IdTokenProvider { audience, _ ->
                observedAudience = audience
                IdToken.create(jwt)
            },
        )

        assertEquals(jwt, provider.accessToken(endpoint))
        assertEquals("https://releases.example.run.app", observedAudience)
        assertFailsWith<RemoteTufSigningException> {
            provider.accessToken(URI.create("https://security.example.run.app/sign"))
        }
    }

    @Test
    fun `public API rejects every signer URL private key and key version`() {
        val public = gcpEnvironment()
        assertEquals(emptyMap(), RegistryConfig.fromEnvironment(public).remoteOnlineSignerTargets)

        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(
                public + (signerUrl(TufRole.RELEASES) to endpoint(TufRole.RELEASES)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(
                public + ("REGISTRY_KMS_RELEASES_KEY_VERSION" to "projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(
                public + ("REGISTRY_RELEASES_SIGNING_KEY_PKCS8_BASE64" to "private-material"),
            )
        }
    }

    @Test
    fun `release and security coordinators accept only their exact remote roles`() {
        val releaseRoles = RegistryServerMode.RELEASE_ACTIONS.signingRoles
        val releaseEnvironment = actionEnvironment(RegistryServerMode.RELEASE_ACTIONS) +
            ("REGISTRY_SERVER_MODE" to RegistryServerMode.RELEASE_ACTIONS.environmentValue) +
            signerEnvironment(releaseRoles) +
            ("REGISTRY_TUF_SNAPSHOT_SIGNER_AUDIENCE" to "https://snapshot-audience.example")
        val release = RegistryConfig.fromEnvironment(releaseEnvironment)

        assertEquals(releaseRoles, release.configuredSigningRoles)
        assertEquals(releaseRoles, release.remoteOnlineSignerTargets.keys)
        assertEquals(
            "https://releases.example.run.app",
            release.remoteOnlineSignerTargets.getValue(TufRole.RELEASES).audience,
        )
        assertEquals(
            "https://snapshot-audience.example",
            release.remoteOnlineSignerTargets.getValue(TufRole.SNAPSHOT).audience,
        )

        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(
                releaseEnvironment + (signerUrl(TufRole.SECURITY) to endpoint(TufRole.SECURITY)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(releaseEnvironment - signerUrl(TufRole.TIMESTAMP))
        }

        val securityRoles = RegistryServerMode.SECURITY_ACTIONS.signingRoles
        val security = RegistryConfig.fromEnvironment(
            actionEnvironment(RegistryServerMode.SECURITY_ACTIONS) +
                ("REGISTRY_SERVER_MODE" to RegistryServerMode.SECURITY_ACTIONS.environmentValue) +
                signerEnvironment(securityRoles),
        )
        assertEquals(securityRoles, security.remoteOnlineSignerTargets.keys)
        assertEquals(false, TufRole.RELEASES in security.remoteOnlineSignerTargets)
    }

    @Test
    fun `server config rejects bootstrap and offline maintenance authority`() {
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(gcpEnvironment() + ("REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64" to "root"))
        }
        assertFailsWith<IllegalArgumentException> {
            RegistryConfig.fromEnvironment(gcpEnvironment() + ("REGISTRY_OFFLINE_ROOT_PRIVATE_KEY" to "private"))
        }
    }

    @Test
    fun `online signer factory creates remote signers only for validated active roles`() {
        val roles = RegistryServerMode.RELEASE_ACTIONS.signingRoles
        val targets = (gcpEnvironment() + signerEnvironment(roles)).remoteSignerTargets()
        val providerTargets = mutableListOf<RemoteTufSignerTarget>()
        val signers = createRemoteTufOnlineSigners(
            activeRoles = roles,
            operation = TufSigningOperation.RELEASE,
            publicKeysHex = publicKeys(),
            targets = targets,
            tokenProviderFactory = { target ->
                providerTargets += target
                RemoteTufTokenProvider { "test-oidc-token" }
            },
        )
        try {
            assertIs<RemoteTufSigner>(signers.releases)
            assertIs<PublicKeyOnlyTufSigner>(signers.security)
            assertIs<RemoteTufSigner>(signers.snapshot)
            assertIs<RemoteTufSigner>(signers.timestamp)
            assertEquals(targets.values.toSet(), providerTargets.toSet())
        } finally {
            signers.close()
        }
    }

    private fun gcpEnvironment(): Map<String, String> = mapOf(
        "REGISTRY_STORAGE_MODE" to "gcp",
        "GOOGLE_CLOUD_PROJECT" to "seen-dev",
        "REGISTRY_QUARANTINE_BUCKET" to "quarantine",
        "REGISTRY_PUBLIC_BUCKET" to "public",
        "REGISTRY_METADATA_BUCKET" to "metadata",
        "REGISTRY_OWNER_ALLOWLIST" to "seen",
        "REGISTRY_WRITER_TOKEN" to "w".repeat(32),
        "REGISTRY_TRUST_AND_SAFETY_TOKEN" to "r".repeat(32),
    ) + publicKeys().mapKeys { (role, _) -> "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX" }

    private fun actionEnvironment(mode: RegistryServerMode): Map<String, String> = buildMap {
        putAll(gcpEnvironment())
        remove("REGISTRY_QUARANTINE_BUCKET")
        remove("REGISTRY_PUBLIC_BUCKET")
        remove("REGISTRY_OWNER_ALLOWLIST")
        remove("REGISTRY_TRUST_AND_SAFETY_TOKEN")
        if (mode == RegistryServerMode.SECURITY_ACTIONS) {
            remove("REGISTRY_WRITER_TOKEN")
            put("REGISTRY_SECURITY_TOKEN", "s".repeat(32))
        }
    }

    private fun publicKeys(): Map<String, String> = TufRole.ONLINE.mapIndexed { index, role ->
        role to (index + 1).toString(16).padStart(2, '0').repeat(32)
    }.toMap()

    private fun signerEnvironment(roles: Set<String>): Map<String, String> =
        roles.associate { role -> signerUrl(role) to endpoint(role) }

    private fun signerUrl(role: String): String = "REGISTRY_TUF_${role.uppercase()}_SIGNER_URL"
    private fun endpoint(role: String): String = "https://$role.example.run.app/sign"

    private fun testIdToken(): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun encoded(value: String): String = encoder.encodeToString(value.encodeToByteArray())
        return "${encoded("""{"alg":"none","typ":"JWT"}""")}.${encoded("""{"exp":4102444800}""")}.${encoded("signature")}"
    }
}
