package codes.yousef.seen.registry

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class WriterPrincipal(val subject: String)

/**
 * Temporary development-only boundary used for internal publishing.
 *
 * The token comparison is constant-time. Authorization is additionally bound
 * to an owner allowlist so possession of this token cannot reserve arbitrary
 * namespaces. This class must be removed when Aether short-lived bearer tokens
 * are connected.
 */
class OpaqueDevWriterAuthenticator(
    expectedToken: String,
    private val principal: String,
    private val allowedOwners: Set<String>,
) {
    private val expected = expectedToken.toByteArray(StandardCharsets.UTF_8)

    fun authenticate(authorization: String?): WriterPrincipal {
        val presented = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = false) }
            ?.removePrefix("Bearer ")
            ?.toByteArray(StandardCharsets.UTF_8)
            ?: ByteArray(0)
        if (!MessageDigest.isEqual(expected, presented)) {
            throw RegistryException(401, "unauthenticated", "Authentication is required")
        }
        return WriterPrincipal(principal)
    }

    fun authorizeOwner(owner: String) {
        if (owner !in allowedOwners) {
            throw RegistryException(403, "forbidden", "Namespace is not authorized for this publisher")
        }
    }
}
