package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant

class RemoteTufSigningException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class TufSigningRequestException(message: String) : IllegalArgumentException(message)

fun interface RemoteTufTokenProvider {
    /** Returns the raw bearer token, without the `Bearer` prefix. */
    fun accessToken(endpoint: URI): String
}

data class RemoteTufHttpRequest(
    val endpoint: URI,
    val role: String,
    val operation: String,
    val authorization: String,
    val body: ByteArray,
    val timeout: Duration,
    val maximumResponseBytes: Int,
)

data class RemoteTufHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
)

fun interface RemoteTufHttpTransport {
    fun post(request: RemoteTufHttpRequest): RemoteTufHttpResponse
}

/**
 * Bounded JDK transport for the remote signing protocol.
 *
 * The request body is the canonical TUF `signed` object. A successful response
 * body is one raw 64-byte Ed25519 signature. Redirects are deliberately disabled
 * so credentials cannot be forwarded to a different authority.
 */
class JdkRemoteTufHttpTransport(
    connectTimeout: Duration = Duration.ofSeconds(5),
) : RemoteTufHttpTransport {
    private val client: HttpClient

    init {
        requireBoundedTimeout(connectTimeout, "Remote signer connect timeout")
        client = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }

    override fun post(request: RemoteTufHttpRequest): RemoteTufHttpResponse {
        val httpRequest = HttpRequest.newBuilder(request.endpoint)
            .timeout(request.timeout)
            .header("Authorization", request.authorization)
            .header("Content-Type", SIGNED_METADATA_CONTENT_TYPE)
            .header("Accept", SIGNATURE_CONTENT_TYPE)
            .header(ROLE_HEADER, request.role)
            .header(OPERATION_HEADER, request.operation)
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.body))
            .build()
        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { body ->
            body.readNBytes(request.maximumResponseBytes + 1)
        }
        if (bytes.size > request.maximumResponseBytes) {
            throw RemoteTufSigningException("Remote signer response exceeds the configured size limit")
        }
        return RemoteTufHttpResponse(response.statusCode(), bytes)
    }

    companion object {
        const val ROLE_HEADER = "X-Seen-Tuf-Role"
        const val OPERATION_HEADER = "X-Seen-Tuf-Operation"
        const val SIGNED_METADATA_CONTENT_TYPE = "application/vnd.seen.tuf-signed+json"
        const val SIGNATURE_CONTENT_TYPE = "application/vnd.seen.ed25519-signature"
    }
}

/**
 * A TUF signer backed by a separately authenticated signing workload.
 *
 * The remote workload is not trusted to identify its own key: every returned
 * signature is verified locally against [publicKey] before it can enter a TUF
 * envelope.
 */
class RemoteTufSigner(
    endpoint: URI,
    private val role: String,
    private val operation: TufSigningOperation,
    publicKey: ByteArray,
    private val tokenProvider: RemoteTufTokenProvider,
    private val transport: RemoteTufHttpTransport = JdkRemoteTufHttpTransport(),
    private val requestTimeout: Duration = Duration.ofSeconds(30),
    private val maximumRequestBytes: Int = DEFAULT_MAXIMUM_REQUEST_BYTES,
    private val maximumResponseBytes: Int = DEFAULT_MAXIMUM_RESPONSE_BYTES,
) : TufSigner {
    private val endpoint: URI = endpoint.normalize()
    private val pinnedPublicKey = publicKey.copyOf()

    override val publicKey: ByteArray
        get() = pinnedPublicKey.copyOf()

    override val commitsTimestampPointer: Boolean
        get() = role == TufRole.TIMESTAMP

    override val timestampCommitRecoveryTimeout: Duration
        get() = if (role == TufRole.TIMESTAMP) Duration.ofSeconds(11) else Duration.ZERO

    init {
        require(this.endpoint.scheme == "https" && this.endpoint.host != null) {
            "Remote signer endpoint must be an absolute HTTPS URI"
        }
        require(this.endpoint.rawUserInfo == null && this.endpoint.rawQuery == null && this.endpoint.rawFragment == null) {
            "Remote signer endpoint cannot contain user info, a query, or a fragment"
        }
        require(role in TufRole.ONLINE) { "Remote signer role is invalid" }
        require(operation.permitsRole(role)) { "Remote signer operation cannot sign $role metadata" }
        require(pinnedPublicKey.size == ED25519_PUBLIC_KEY_BYTES) {
            "Remote signer public key must contain 32 raw Ed25519 bytes"
        }
        requireBoundedTimeout(requestTimeout, "Remote signer request timeout")
        require(maximumRequestBytes in 1..ABSOLUTE_MAXIMUM_REQUEST_BYTES) {
            "Remote signer request size limit is invalid"
        }
        require(maximumResponseBytes in ED25519_SIGNATURE_BYTES..ABSOLUTE_MAXIMUM_RESPONSE_BYTES) {
            "Remote signer response size limit is invalid"
        }
    }

    override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
        requireCanonicalSignedObject(canonicalSignedBytes, maximumRequestBytes)
        val token = try {
            tokenProvider.accessToken(endpoint)
        } catch (failure: Exception) {
            throw RemoteTufSigningException("Remote signer authentication failed", failure)
        }
        if (token.isBlank() || token.length > MAXIMUM_BEARER_TOKEN_LENGTH || token.any(Char::isISOControl)) {
            throw RemoteTufSigningException("Remote signer authentication produced an invalid bearer token")
        }
        val request = RemoteTufHttpRequest(
            endpoint = endpoint,
            role = role,
            operation = operation.wireValue,
            authorization = "Bearer $token",
            body = canonicalSignedBytes.copyOf(),
            timeout = requestTimeout,
            maximumResponseBytes = maximumResponseBytes,
        )
        val response = try {
            transport.post(request)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RemoteTufSigningException("Remote signer request was interrupted", failure)
        } catch (failure: RemoteTufSigningException) {
            throw failure
        } catch (failure: Exception) {
            throw RemoteTufSigningException("Remote signer request failed", failure)
        }
        if (response.body.size > maximumResponseBytes) {
            throw RemoteTufSigningException("Remote signer response exceeds the configured size limit")
        }
        if (response.statusCode != 200) {
            throw RemoteTufSigningException("Remote signer returned HTTP ${response.statusCode}")
        }
        if (response.body.size != ED25519_SIGNATURE_BYTES) {
            throw RemoteTufSigningException("Remote signer returned a non-Ed25519 signature")
        }
        if (!verifyEd25519(pinnedPublicKey, canonicalSignedBytes, response.body)) {
            throw RemoteTufSigningException("Remote signer returned a signature that does not match the pinned key")
        }
        return response.body.copyOf()
    }

    companion object {
        const val DEFAULT_MAXIMUM_REQUEST_BYTES = 1024 * 1024
        const val DEFAULT_MAXIMUM_RESPONSE_BYTES = 1024
        private const val ABSOLUTE_MAXIMUM_REQUEST_BYTES = 8 * 1024 * 1024
        private const val ABSOLUTE_MAXIMUM_RESPONSE_BYTES = 8 * 1024
        private const val MAXIMUM_BEARER_TOKEN_LENGTH = 8 * 1024
    }
}

enum class TufSigningOperation(
    val wireValue: String,
    val changingRole: String?,
) {
    RELEASE("release", TufRole.RELEASES),
    SECURITY("security", TufRole.SECURITY),
    BOOTSTRAP("bootstrap", null),
    TARGETS_RENEWAL("targets-renewal", null),
    TARGETS_ROTATION_RELEASES("targets-rotation:releases", TufRole.RELEASES),
    TARGETS_ROTATION_SECURITY("targets-rotation:security", TufRole.SECURITY),
    ;

    fun permitsRole(role: String): Boolean = when (this) {
        RELEASE -> role in setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
        SECURITY -> role in setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
        BOOTSTRAP -> role in TufRole.ONLINE
        TARGETS_RENEWAL -> role in setOf(TufRole.SNAPSHOT, TufRole.TIMESTAMP)
        TARGETS_ROTATION_RELEASES -> role in setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
        TARGETS_ROTATION_SECURITY -> role in setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
    }

    companion object {
        fun parse(value: String): TufSigningOperation = entries.firstOrNull { it.wireValue == value }
            ?: throw TufSigningRequestException("TUF signing operation is invalid")

        fun targetsRotation(role: String): TufSigningOperation = when (role) {
            TufRole.RELEASES -> TARGETS_ROTATION_RELEASES
            TufRole.SECURITY -> TARGETS_ROTATION_SECURITY
            else -> throw IllegalArgumentException("Targets rotation role must be releases or security")
        }
    }
}

data class RoleLockedTufSigningPolicy(
    val role: String,
    val environment: String,
    val repositoryId: String,
    val acceptedVersions: LongRange,
    val maximumExpiry: Duration,
    val maximumRequestBytes: Int = RemoteTufSigner.DEFAULT_MAXIMUM_REQUEST_BYTES,
) {
    init {
        require(role in TufRole.ONLINE) { "Signing policy role is invalid" }
        require(environment in setOf("development", "production")) { "Signing policy environment is invalid" }
        require(repositoryId.isNotBlank() && repositoryId.length <= 128) { "Signing policy repository is invalid" }
        require(!acceptedVersions.isEmpty() && acceptedVersions.first >= 1L && acceptedVersions.last <= MAXIMUM_METADATA_VERSION) {
            "Signing policy version range is invalid"
        }
        require(!maximumExpiry.isZero && !maximumExpiry.isNegative && maximumExpiry <= ABSOLUTE_MAXIMUM_EXPIRY) {
            "Signing policy expiry bound is invalid"
        }
        require(maximumRequestBytes in 1..ABSOLUTE_MAXIMUM_REQUEST_BYTES) {
            "Signing policy request size limit is invalid"
        }
    }

    private companion object {
        const val MAXIMUM_METADATA_VERSION = 9_007_199_254_740_991L
        val ABSOLUTE_MAXIMUM_EXPIRY: Duration = Duration.ofDays(31)
        const val ABSOLUTE_MAXIMUM_REQUEST_BYTES = 8 * 1024 * 1024
    }
}

/**
 * Validates one authenticated role request before invoking exactly one signer.
 * Authentication and HTTP routing stay outside this reusable policy primitive.
 */
class RoleLockedTufSigningService(
    private val policy: RoleLockedTufSigningPolicy,
    private val signer: TufSigner,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    init {
        require(signer.publicKey.size == ED25519_PUBLIC_KEY_BYTES) {
            "Role signer public key must contain 32 raw Ed25519 bytes"
        }
    }

    fun sign(declaredRole: String, canonicalSignedBytes: ByteArray): ByteArray {
        if (declaredRole != policy.role) reject("Signing request role is invalid")
        val signed = requireCanonicalSignedObject(canonicalSignedBytes, policy.maximumRequestBytes)
        validateCommon(signed)
        when (policy.role) {
            TufRole.RELEASES -> validateTargets(signed, securityRole = false)
            TufRole.SECURITY -> validateTargets(signed, securityRole = true)
            TufRole.SNAPSHOT -> validateSnapshot(signed)
            TufRole.TIMESTAMP -> validateTimestamp(signed)
        }
        val signature = signer.sign(canonicalSignedBytes)
        if (signature.size != ED25519_SIGNATURE_BYTES ||
            !verifyEd25519(signer.publicKey, canonicalSignedBytes, signature)
        ) {
            throw RemoteTufSigningException("Configured role signer returned an invalid Ed25519 signature")
        }
        return signature.copyOf()
    }

    override fun close() = signer.close()

    private fun validateCommon(signed: JsonObject) {
        val expectedType = when (policy.role) {
            TufRole.RELEASES, TufRole.SECURITY -> "targets"
            TufRole.SNAPSHOT -> "snapshot"
            TufRole.TIMESTAMP -> "timestamp"
            else -> error("Unreachable signing role")
        }
        val roleField = if (expectedType == "targets") "targets" else "meta"
        val expectedFields = COMMON_FIELDS + roleField
        if (signed.keys != expectedFields) reject("Signing request metadata fields are invalid")
        if (signed.requiredString("_type") != expectedType) reject("Signing request metadata type is invalid")
        if (signed.requiredString("spec_version") != "1.0") reject("Signing request TUF specification version is invalid")
        if (signed.requiredString("environment") != policy.environment) reject("Signing request environment is invalid")
        if (signed.requiredString("repository_id") != policy.repositoryId) reject("Signing request repository is invalid")

        val versionValue = signed["version"] as? JsonPrimitive
            ?: reject("Signing request version is invalid")
        val version = versionValue.takeUnless(JsonPrimitive::isString)?.content?.toLongOrNull()
            ?: reject("Signing request version is invalid")
        if (version !in policy.acceptedVersions) reject("Signing request version is outside the configured bound")

        val expiresText = signed.requiredString("expires")
        val expires = runCatching { Instant.parse(expiresText) }
            .getOrElse { reject("Signing request expiry is invalid") }
        if (expires.toString() != expiresText) reject("Signing request expiry is not canonical")
        val remaining = runCatching { Duration.between(clock.instant(), expires) }
            .getOrElse { reject("Signing request expiry is invalid") }
        if (remaining.isZero || remaining.isNegative || remaining > policy.maximumExpiry) {
            reject("Signing request expiry is outside the configured bound")
        }
    }

    private fun validateTargets(signed: JsonObject, securityRole: Boolean) {
        val targets = signed["targets"] as? JsonObject ?: reject("Signing request targets are invalid")
        targets.forEach { (path, value) ->
            if (!TARGET_PATH.matches(path)) reject("Signing request target path is invalid")
            val target = value as? JsonObject ?: reject("Signing request target is invalid")
            if (target.keys != setOf("length", "hashes", "custom")) reject("Signing request target fields are invalid")
            positiveLong(target, "length", "Signing request target length is invalid")
            validateHashes(target["hashes"] as? JsonObject ?: reject("Signing request target hashes are invalid"))
            val custom = target["custom"] as? JsonObject ?: reject("Signing request target custom metadata is invalid")
            val availability = custom.requiredString("availability")
            if (securityRole) {
                if (availability != "security-quarantined" || custom.requiredString("security_action") != "quarantine") {
                    reject("Security signing request contains a non-quarantine target")
                }
                if (!SECURITY_INCIDENT_ID.matches(custom.requiredString("incident_id"))) {
                    reject("Security signing request incident identity is invalid")
                }
            } else if (availability !in setOf("available", "yanked") ||
                "incident_id" in custom || "security_action" in custom
            ) {
                reject("Releases signing request contains security-only target state")
            }
        }
    }

    private fun validateSnapshot(signed: JsonObject) {
        val meta = signed["meta"] as? JsonObject ?: reject("Signing request snapshot metadata is invalid")
        if (meta.keys != setOf("targets.json", "releases.json", "security.json")) {
            reject("Signing request snapshot references invalid roles")
        }
        val signedVersion = signed.requiredLong("version")
        meta.values.forEach { reference ->
            validateFileReference(reference as? JsonObject ?: reject("Signing request snapshot reference is invalid"), signedVersion)
        }
    }

    private fun validateTimestamp(signed: JsonObject) {
        val meta = signed["meta"] as? JsonObject ?: reject("Signing request timestamp metadata is invalid")
        if (meta.keys != setOf("snapshot.json")) reject("Signing request timestamp references invalid roles")
        val signedVersion = signed.requiredLong("version")
        val reference = meta.getValue("snapshot.json") as? JsonObject
            ?: reject("Signing request timestamp reference is invalid")
        validateFileReference(reference, signedVersion)
        if (reference.requiredLong("version") != signedVersion) {
            reject("Signing request timestamp references a different snapshot version")
        }
    }

    private fun validateFileReference(reference: JsonObject, maximumVersion: Long) {
        if (reference.keys != setOf("version", "length", "hashes")) {
            reject("Signing request metadata reference fields are invalid")
        }
        val version = reference.requiredLong("version")
        if (version !in 1L..maximumVersion) reject("Signing request metadata reference version is invalid")
        positiveLong(reference, "length", "Signing request metadata reference length is invalid")
        validateHashes(reference["hashes"] as? JsonObject ?: reject("Signing request metadata reference hashes are invalid"))
    }

    private fun validateHashes(hashes: JsonObject) {
        if (hashes.keys != setOf("sha256") || !SHA256.matches(hashes.requiredString("sha256"))) {
            reject("Signing request SHA-256 digest is invalid")
        }
    }

    private companion object {
        val COMMON_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id")
        val TARGET_PATH = Regex("^packages/[a-z0-9][a-z0-9-]{0,62}/[a-z0-9][a-z0-9-]{0,62}/[^/]{1,128}/[0-9a-f]{64}/[^/]{1,256}$")
        val SECURITY_INCIDENT_ID = Regex("^inc_[A-Za-z0-9_-]{8,96}$")
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

private const val ED25519_PUBLIC_KEY_BYTES = 32
private const val ED25519_SIGNATURE_BYTES = 64
private val ABSOLUTE_MAXIMUM_REMOTE_TIMEOUT: Duration = Duration.ofSeconds(30)

private fun requireBoundedTimeout(timeout: Duration, label: String) {
    require(!timeout.isZero && !timeout.isNegative && timeout <= ABSOLUTE_MAXIMUM_REMOTE_TIMEOUT) {
        "$label must be positive and no more than 30 seconds"
    }
}

private fun requireCanonicalSignedObject(bytes: ByteArray, maximumBytes: Int): JsonObject {
    if (bytes.isEmpty() || bytes.size > maximumBytes) reject("Signing request body is outside the configured size bound")
    val parsed = runCatching { RegistryJson.parseToJsonElement(bytes.decodeToString()) }
        .getOrElse { reject("Signing request body is not valid JSON") }
    val signed = parsed as? JsonObject ?: reject("Signing request body must be a JSON object")
    val canonical = runCatching { canonicalJson(signed) }
        .getOrElse { reject("Signing request body is not canonical TUF JSON") }
    if (!canonical.contentEquals(bytes)) reject("Signing request body is not canonical TUF JSON")
    return signed
}

private fun JsonObject.requiredString(name: String): String {
    val value = this[name] as? JsonPrimitive ?: reject("Signing request $name is invalid")
    if (!value.isString) reject("Signing request $name is invalid")
    return value.content
}

private fun JsonObject.requiredLong(name: String): Long {
    val value = this[name] as? JsonPrimitive ?: reject("Signing request $name is invalid")
    if (value.isString) reject("Signing request $name is invalid")
    return value.content.toLongOrNull() ?: reject("Signing request $name is invalid")
}

private fun positiveLong(value: JsonObject, name: String, message: String): Long {
    val parsed = runCatching { value.requiredLong(name) }.getOrElse { reject(message) }
    if (parsed < 1L) reject(message)
    return parsed
}

private fun verifyEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
    if (publicKey.size != ED25519_PUBLIC_KEY_BYTES || signature.size != ED25519_SIGNATURE_BYTES) return false
    return Ed25519Signer().run {
        init(false, Ed25519PublicKeyParameters(publicKey))
        update(message, 0, message.size)
        verifySignature(signature)
    }
}

private fun reject(message: String): Nothing = throw TufSigningRequestException(message)
