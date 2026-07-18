package codes.yousef.seen.registry

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.IdTokenCredentials
import com.google.auth.oauth2.IdTokenProvider
import java.net.URI

/**
 * Supplies Google-signed OIDC ID tokens for one pinned signer service.
 *
 * Cloud Run validates the service origin (or an explicitly configured custom
 * audience), not the `/sign` request path. This provider therefore pins both
 * independently and refuses to mint a token for any other endpoint.
 */
class GoogleCloudRunTufIdTokenProvider(
    private val target: RemoteTufSignerTarget,
    idTokenProvider: IdTokenProvider = applicationDefaultIdTokenProvider(),
) : RemoteTufTokenProvider {
    private val credentials = IdTokenCredentials.newBuilder()
        .setIdTokenProvider(idTokenProvider)
        .setTargetAudience(target.audience)
        .setOptions(listOf(IdTokenProvider.Option.INCLUDE_EMAIL))
        .build()

    override fun accessToken(endpoint: URI): String {
        if (endpoint.normalize() != target.endpoint.normalize()) {
            throw RemoteTufSigningException("Remote signer authentication endpoint does not match its pinned target")
        }
        return try {
            credentials.refreshIfExpired()
            credentials.accessToken?.tokenValue
                ?: throw RemoteTufSigningException("Remote signer authentication returned no OIDC ID token")
        } catch (failure: RemoteTufSigningException) {
            throw failure
        } catch (failure: Exception) {
            throw RemoteTufSigningException("Remote signer OIDC ID-token acquisition failed", failure)
        }
    }

    companion object {
        private fun applicationDefaultIdTokenProvider(): IdTokenProvider {
            val credentials = GoogleCredentials.getApplicationDefault()
            return credentials as? IdTokenProvider
                ?: throw IllegalStateException("Application default credentials cannot mint OIDC ID tokens")
        }
    }
}

internal fun createRemoteTufOnlineSigners(
    activeRoles: Set<String>,
    operation: TufSigningOperation,
    publicKeysHex: Map<String, String>,
    targets: Map<String, RemoteTufSignerTarget>,
    tokenProviderFactory: (RemoteTufSignerTarget) -> RemoteTufTokenProvider =
        { target -> GoogleCloudRunTufIdTokenProvider(target) },
): TufOnlineSigners {
    require(activeRoles.all(TufRole.ONLINE::contains)) { "Unknown online TUF signing role" }
    require(activeRoles.all(operation::permitsRole)) { "TUF signing operation does not permit every active role" }
    require(publicKeysHex.keys == TufRole.ONLINE.toSet()) { "All and only online TUF public keys are required" }
    require(targets.keys == activeRoles) { "Remote signer targets must exactly match active roles" }

    fun signer(role: String): TufSigner {
        val publicKey = requireNotNull(publicKeysHex[role]).hexToBytes()
        val target = targets[role]
        return if (target == null) {
            PublicKeyOnlyTufSigner(publicKey)
        } else {
            RemoteTufSigner(
                endpoint = target.endpoint,
                role = role,
                operation = operation,
                publicKey = publicKey,
                tokenProvider = tokenProviderFactory(target),
            )
        }
    }

    return TufOnlineSigners(
        releases = signer(TufRole.RELEASES),
        security = signer(TufRole.SECURITY),
        snapshot = signer(TufRole.SNAPSHOT),
        timestamp = signer(TufRole.TIMESTAMP),
    )
}
