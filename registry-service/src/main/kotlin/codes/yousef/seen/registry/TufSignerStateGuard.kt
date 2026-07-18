package codes.yousef.seen.registry

import com.google.auth.oauth2.TokenVerifier
import com.google.common.util.concurrent.UncheckedExecutionException
import com.google.cloud.ServiceOptions
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

data class TufSignerStateGuardConfig(
    val metadataBucket: String,
    val objectPrefix: String,
    val trustedRootV1Sha256: String,
    val audience: String,
    val callerEmails: Map<TufSigningOperation, Set<String>>,
) {
    init {
        require(BUCKET.matches(metadataBucket)) { "REGISTRY_TUF_SIGNER_METADATA_BUCKET is invalid" }
        require(PREFIX.matches(objectPrefix)) { "REGISTRY_TUF_SIGNER_OBJECT_PREFIX is invalid" }
        require(SHA256.matches(trustedRootV1Sha256)) { "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256 is invalid" }
        require(audience.startsWith("https://") && audience.length <= 2048 && audience.none(Char::isWhitespace)) {
            "REGISTRY_TUF_SIGNER_AUDIENCE is invalid"
        }
        require(
            callerEmails.isNotEmpty() &&
                callerEmails.values.all { emails -> emails.isNotEmpty() && emails.all(CALLER_EMAIL::matches) },
        ) {
            "REGISTRY_TUF_SIGNER_CALLER_BINDINGS is invalid"
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String>): TufSignerStateGuardConfig {
            fun required(name: String): String = env[name]?.trim()?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("$name is required")
            val bindings = required("REGISTRY_TUF_SIGNER_CALLER_BINDINGS")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { binding ->
                    val separator = binding.indexOf('=')
                    require(separator > 0 && separator == binding.lastIndexOf('=')) {
                        "REGISTRY_TUF_SIGNER_CALLER_BINDINGS is invalid"
                    }
                    TufSigningOperation.parse(binding.substring(0, separator)) to
                        binding.substring(separator + 1).trim()
                }
            return TufSignerStateGuardConfig(
                metadataBucket = required("REGISTRY_TUF_SIGNER_METADATA_BUCKET"),
                objectPrefix = env["REGISTRY_TUF_SIGNER_OBJECT_PREFIX"]?.trim('/')?.takeIf(String::isNotEmpty) ?: "v1",
                trustedRootV1Sha256 = required("REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256"),
                audience = required("REGISTRY_TUF_SIGNER_AUDIENCE"),
                callerEmails = bindings.groupBy({ it.first }, { it.second }).mapValues { (_, emails) -> emails.toSet() },
            )
        }

        private val BUCKET = Regex("^[a-z0-9][a-z0-9._-]{1,221}[a-z0-9]$")
        private val PREFIX = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val CALLER_EMAIL = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}@[A-Za-z0-9.-]{1,190}$")
    }
}

data class TufSignerMetadataObject(
    val bytes: ByteArray,
    val generation: Long,
)

internal const val MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES: Long = 8L * 1024L * 1024L

interface TufSignerMetadataStore {
    fun get(filename: String): TufSignerMetadataObject?

    /** The sole mutable write exposed to signer code. */
    fun commitTimestamp(expectedGeneration: Long?, bytes: ByteArray): Boolean
}

class GcsTufSignerMetadataStore(
    private val storage: Storage,
    private val bucket: String,
    private val prefix: String,
) : TufSignerMetadataStore {
    override fun get(filename: String): TufSignerMetadataObject? {
        require(METADATA_NAME.matches(filename)) { "TUF signer metadata filename is invalid" }
        val blob = storage.get(BlobId.of(bucket, "$prefix/metadata/$filename")) ?: return null
        if (blob.size !in 1..MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES) {
            rejectSigning("TUF metadata object $filename exceeds the signer size bound")
        }
        return TufSignerMetadataObject(blob.getContent(), requireNotNull(blob.generation))
    }

    override fun commitTimestamp(expectedGeneration: Long?, bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size.toLong() > MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES) {
            rejectSigning("timestamp.json exceeds the signer size bound")
        }
        val info = BlobInfo.newBuilder(BlobId.of(bucket, "$prefix/metadata/timestamp.json"))
            .setContentType("application/json")
            .setCacheControl("public,max-age=300,must-revalidate")
            .build()
        val precondition = expectedGeneration?.let { Storage.BlobTargetOption.generationMatch(it) }
            ?: Storage.BlobTargetOption.doesNotExist()
        return try {
            storage.create(info, bytes, precondition)
            true
        } catch (failure: StorageException) {
            if (failure.code == 412) false else throw failure
        }
    }

    private companion object {
        val METADATA_NAME = Regex("^(?:[1-9][0-9]*\\.)?(?:root|targets|releases|security|snapshot)\\.json$|^timestamp\\.json$")
    }
}

data class VerifiedTufSignerCaller(val email: String)

internal class TufSignerCallerVerificationUnavailableException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)

fun interface TufSignerCallerTokenVerifier {
    fun verify(token: String, audience: String): VerifiedTufSignerCaller
}

class GoogleOidcTufSignerCallerTokenVerifier private constructor(
    private val verifier: GoogleIdTokenVerifier,
) : TufSignerCallerTokenVerifier {
    constructor() : this(CachingGoogleIdTokenVerifier())

    internal constructor(verify: (String, String, String) -> Map<String, Any?>) : this(
        GoogleIdTokenVerifier(verify),
    )

    override fun verify(token: String, audience: String): VerifiedTufSignerCaller {
        val payload = try {
            verifier.verify(token, audience, GOOGLE_ISSUER)
        } catch (failure: Exception) {
            if (failure.isPublicKeyLoadFailure()) {
                throw TufSignerCallerVerificationUnavailableException(
                    "TUF signer caller identity verification is temporarily unavailable",
                    failure,
                )
            }
            rejectSigning("TUF signer caller identity token is invalid")
        }
        val email = payload["email"] as? String
            ?: rejectSigning("TUF signer caller identity token has no email")
        if (payload["email_verified"] != true) {
            rejectSigning("TUF signer caller email is not verified")
        }
        return VerifiedTufSignerCaller(email)
    }

    private companion object {
        const val GOOGLE_ISSUER = "https://accounts.google.com"
    }
}

private fun Throwable.isPublicKeyLoadFailure(): Boolean =
    cause is ExecutionException || cause is UncheckedExecutionException

private fun interface GoogleIdTokenVerifier {
    fun verify(token: String, audience: String, issuer: String): Map<String, Any?>
}

private class CachingGoogleIdTokenVerifier : GoogleIdTokenVerifier {
    private val verifiers = ConcurrentHashMap<String, TokenVerifier>()

    override fun verify(token: String, audience: String, issuer: String): Map<String, Any?> =
        verifiers.computeIfAbsent("$issuer\u0000$audience") {
            TokenVerifier.newBuilder()
                .setAudience(audience)
                .setIssuer(issuer)
                .build()
        }.verify(token).payload
}

interface TufTimestampCommitStatePolicyGuard : TufSignerStatePolicyGuard {
    fun commitTimestamp(
        request: TufSignerAuthorizationRequest,
        signature: ByteArray,
        publicKey: ByteArray,
    )
}

object GcsTufSignerStatePolicyGuardFactory : TufSignerStatePolicyGuardFactory {
    override fun create(config: TufSignerServerConfig): TufSignerStatePolicyGuard {
        val guardConfig = requireNotNull(config.stateGuardConfig) {
            "${config.role} signing requires committed metadata state configuration"
        }
        val rpcTimeoutMillis = config.signingTimeout.toMillis().toInt()
        val storage = StorageOptions.newBuilder()
            .setRetrySettings(ServiceOptions.getNoRetrySettings())
            .setTransportOptions(
                HttpTransportOptions.newBuilder()
                    .setConnectTimeout(rpcTimeoutMillis)
                    .setReadTimeout(rpcTimeoutMillis)
                    .build(),
            )
            .build()
            .service
        return StateAwareTufSignerPolicyGuard(
            role = config.role,
            environment = config.environment,
            repositoryId = config.repositoryId,
            config = guardConfig,
            metadata = GcsTufSignerMetadataStore(storage, guardConfig.metadataBucket, guardConfig.objectPrefix),
            tokenVerifier = GoogleOidcTufSignerCallerTokenVerifier(),
        )
    }
}

/**
 * Authorizes one role from the pinned root and authoritative committed pointer.
 * Legacy committed target custom fields are intentionally treated as opaque:
 * cutover validates their signatures/hashes while new candidates are validated
 * by RoleLockedTufSigningService after this state check.
 */
class StateAwareTufSignerPolicyGuard(
    private val role: String,
    private val environment: String,
    private val repositoryId: String,
    private val config: TufSignerStateGuardConfig,
    private val metadata: TufSignerMetadataStore,
    private val tokenVerifier: TufSignerCallerTokenVerifier,
    private val clock: Clock = Clock.systemUTC(),
) : TufTimestampCommitStatePolicyGuard {
    init {
        require(role in TufRole.ONLINE)
    }

    override fun authorize(request: TufSignerAuthorizationRequest) {
        requireAuthorizedCaller(request)
        val state = loadTrustedState()
        requireRetainedDelegationState(request.operation, state.current)
        val candidate = parseCanonicalSigned(request.canonicalSignedBytes)
        when (request.role) {
            TufRole.RELEASES, TufRole.SECURITY -> authorizeDelegated(request, candidate, state)
            TufRole.SNAPSHOT -> authorizeSnapshot(request.operation, candidate, state)
            TufRole.TIMESTAMP -> authorizeTimestamp(request.operation, candidate, state)
            else -> rejectSigning("TUF signer role is invalid")
        }
    }

    override fun commitTimestamp(
        request: TufSignerAuthorizationRequest,
        signature: ByteArray,
        publicKey: ByteArray,
    ) {
        if (role != TufRole.TIMESTAMP || request.role != TufRole.TIMESTAMP) {
            rejectSigning("Only the timestamp signer may commit timestamp.json")
        }
        requireCommitDeadline(request)
        // Re-read and re-authorize after KMS so the CAS expectation and all
        // opposite-role retention constraints are based on the latest pointer.
        requireAuthorizedCaller(request)
        val state = loadTrustedState()
        requireRetainedDelegationState(request.operation, state.current)
        authorizeTimestamp(request.operation, parseCanonicalSigned(request.canonicalSignedBytes), state)
        if (!publicKey.contentEquals(state.root.timestampKey)) {
            rejectSigning("Timestamp signer key does not match the trusted root")
        }
        if (!verifyRawEd25519(publicKey, request.canonicalSignedBytes, signature)) {
            rejectSigning("Timestamp signature does not match the configured key")
        }
        val envelope = canonicalJson(buildJsonObject {
            put("signatures", buildJsonArray {
                add(buildJsonObject {
                    put("keyid", tufKeyId(publicKey))
                    put("sig", signature.toHex())
                })
            })
            put("signed", parseCanonicalSigned(request.canonicalSignedBytes))
        })
        requireCommitDeadline(request)
        if (!metadata.commitTimestamp(state.timestampGeneration, envelope)) {
            rejectSigning("Committed timestamp changed during signer authorization")
        }
    }

    private fun requireCommitDeadline(request: TufSignerAuthorizationRequest) {
        request.commitDeadline?.let { deadline ->
            if (!clock.instant().isBefore(deadline)) {
                rejectSigning("Timestamp commit authorization expired before publication")
            }
        }
    }

    private fun requireAuthorizedCaller(request: TufSignerAuthorizationRequest) {
        if (request.role != role || !request.operation.permitsRole(role)) {
            rejectSigning("TUF signing role or operation is not authorized")
        }
        val caller = tokenVerifier.verify(request.bearerToken, config.audience)
        val expected = config.callerEmails[request.operation]
            ?: rejectSigning("TUF signing operation has no configured caller")
        if (caller.email !in expected) rejectSigning("TUF signing caller is not authorized for the operation")
    }

    private fun authorizeDelegated(
        request: TufSignerAuthorizationRequest,
        candidate: JsonObject,
        state: TrustedSignerState,
    ) {
        requireCommon(candidate, "targets")
        if (candidate.keys != COMMON_FIELDS + "targets") rejectSigning("Delegated metadata fields are invalid")
        val allowedOperation = when (request.role) {
            TufRole.RELEASES -> request.operation in setOf(
                TufSigningOperation.RELEASE,
                TufSigningOperation.BOOTSTRAP,
                TufSigningOperation.TARGETS_ROTATION_RELEASES,
            )
            TufRole.SECURITY -> request.operation in setOf(
                TufSigningOperation.SECURITY,
                TufSigningOperation.BOOTSTRAP,
                TufSigningOperation.TARGETS_ROTATION_SECURITY,
            )
            else -> false
        }
        if (!allowedOperation) rejectSigning("Operation cannot advance this delegated role")
        val version = candidate.long("version")
        if (state.current == null) {
            if (request.operation != TufSigningOperation.BOOTSTRAP || version != 1L) {
                rejectSigning("Only version-one bootstrap metadata may sign without a committed pointer")
            }
        } else if (version <= state.current.version) {
            rejectSigning("Delegated metadata version does not advance the committed transaction")
        }
    }

    private fun authorizeSnapshot(
        operation: TufSigningOperation,
        candidate: JsonObject,
        state: TrustedSignerState,
    ) {
        requireCommon(candidate, "snapshot")
        if (candidate.keys != COMMON_FIELDS + "meta") rejectSigning("Snapshot metadata fields are invalid")
        val version = candidate.long("version")
        requireTransactionVersion(operation, version, state.current)
        val descriptors = candidate.obj("meta")
        if (descriptors.keys != SNAPSHOT_NAMES) rejectSigning("Snapshot references unexpected roles")

        val targetsDescriptor = descriptor(descriptors.getValue("targets.json"))
        val releasesDescriptor = descriptor(descriptors.getValue("releases.json"))
        val securityDescriptor = descriptor(descriptors.getValue("security.json"))
        val targetsBytes = referenced(targetsDescriptor, "targets")
        val targets = validateTopLevelTargets(targetsBytes, state.root)
        val releasesBytes = referenced(releasesDescriptor, TufRole.RELEASES)
        val securityBytes = referenced(securityDescriptor, TufRole.SECURITY)
        verifyDelegatedEnvelope(releasesBytes, TufRole.RELEASES, releasesDescriptor.version, targets)
        verifyDelegatedEnvelope(securityBytes, TufRole.SECURITY, securityDescriptor.version, targets)

        val current = state.current
        when (operation) {
            TufSigningOperation.BOOTSTRAP -> {
                if (current != null || version != 1L || targetsDescriptor.version != 1L ||
                    releasesDescriptor.version != 1L || securityDescriptor.version != 1L
                ) rejectSigning("Bootstrap snapshot is not the first complete transaction")
            }
            TufSigningOperation.RELEASE -> {
                val committed = requireNotNull(current) { "Release signing requires committed metadata" }
                requireUnchanged(targetsDescriptor, committed.targets, "targets")
                requireUnchanged(securityDescriptor, committed.security, TufRole.SECURITY)
                requireChanged(releasesDescriptor, committed.releases, version, TufRole.RELEASES)
            }
            TufSigningOperation.SECURITY -> {
                val committed = requireNotNull(current) { "Security signing requires committed metadata" }
                requireUnchanged(targetsDescriptor, committed.targets, "targets")
                requireUnchanged(releasesDescriptor, committed.releases, TufRole.RELEASES)
                requireChanged(securityDescriptor, committed.security, version, TufRole.SECURITY)
            }
            TufSigningOperation.TARGETS_RENEWAL -> {
                val committed = requireNotNull(current) { "Targets renewal requires committed metadata" }
                requireSequentialTargets(targetsDescriptor, committed.targets)
                requireUnchanged(releasesDescriptor, committed.releases, TufRole.RELEASES)
                requireUnchanged(securityDescriptor, committed.security, TufRole.SECURITY)
                requireDelegationsEqual(state.targets, targets)
            }
            TufSigningOperation.TARGETS_ROTATION_RELEASES,
            TufSigningOperation.TARGETS_ROTATION_SECURITY,
            -> {
                val committed = requireNotNull(current) { "Targets rotation requires committed metadata" }
                requireSequentialTargets(targetsDescriptor, committed.targets)
                val affected = requireNotNull(operation.changingRole)
                val oldAffected = if (affected == TufRole.RELEASES) committed.releases else committed.security
                val newAffected = if (affected == TufRole.RELEASES) releasesDescriptor else securityDescriptor
                requireChanged(newAffected, oldAffected, version, affected)
                val oldUnaffected = if (affected == TufRole.RELEASES) committed.security else committed.releases
                val newUnaffected = if (affected == TufRole.RELEASES) securityDescriptor else releasesDescriptor
                requireUnchanged(newUnaffected, oldUnaffected, if (affected == TufRole.RELEASES) TufRole.SECURITY else TufRole.RELEASES)
                requireSingleDelegationRotation(state.targets, targets, affected)
            }
        }
    }

    private fun authorizeTimestamp(
        operation: TufSigningOperation,
        candidate: JsonObject,
        state: TrustedSignerState,
    ) {
        requireCommon(candidate, "timestamp")
        if (candidate.keys != COMMON_FIELDS + "meta") rejectSigning("Timestamp metadata fields are invalid")
        val version = candidate.long("version")
        requireTransactionVersion(operation, version, state.current)
        val meta = candidate.obj("meta")
        if (meta.keys != setOf("snapshot.json")) rejectSigning("Timestamp references unexpected roles")
        val snapshotDescriptor = descriptor(meta.getValue("snapshot.json"))
        if (snapshotDescriptor.version != version) rejectSigning("Timestamp references a different snapshot version")
        val snapshotBytes = referenced(snapshotDescriptor, "snapshot")
        val snapshot = verifyEnvelope(
            bytes = snapshotBytes,
            type = "snapshot",
            version = version,
            publicKey = state.root.snapshotKey,
        )
        authorizeSnapshot(operation, snapshot, state)
    }

    private fun loadTrustedState(): TrustedSignerState {
        val rootV1 = requireObject("1.root.json").bytes
        if (sha256(rootV1) != config.trustedRootV1Sha256) rejectSigning("Trusted root v1 pin does not match")
        val currentRoot = requireObject("root.json").bytes
        val currentRootVersion = envelopeSigned(currentRoot).long("version")
        if (currentRootVersion !in 1L..MAX_ROOT_CHAIN) rejectSigning("Trusted root chain length is invalid")
        var chainBytes = 0L
        val chain = (1L..currentRootVersion).map { version ->
            requireObject("$version.root.json").bytes.also { bytes ->
                chainBytes = Math.addExact(chainBytes, bytes.size.toLong())
                if (chainBytes > MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES) {
                    rejectSigning("Trusted root chain exceeds the signer size bound")
                }
            }
        }
        if (!chain.first().contentEquals(rootV1) || !chain.last().contentEquals(currentRoot)) {
            rejectSigning("Trusted root pointer does not match its sequential chain")
        }
        runCatching {
            TufRootRotationPolicy(environment, repositoryId, clock).validateChain(chain)
        }.getOrElse { rejectSigning("Trusted root chain is invalid") }
        val root = parseRootTrust(currentRoot)

        val timestampObject = optionalObject("timestamp.json")
        if (timestampObject == null) {
            val targetsBytes = requireObject("1.targets.json").bytes
            val targets = validateTopLevelTargets(targetsBytes, root)
            if (targets.version != 1L) rejectSigning("Bootstrap targets version is invalid")
            return TrustedSignerState(root, targets, null, null)
        }
        val timestamp = verifyEnvelope(timestampObject.bytes, "timestamp", null, root.timestampKey)
        val timestampMeta = timestamp.obj("meta")
        if (timestampMeta.keys != setOf("snapshot.json")) rejectSigning("Committed timestamp references unexpected roles")
        val snapshotDescriptor = descriptor(timestampMeta.getValue("snapshot.json"))
        if (snapshotDescriptor.version != timestamp.long("version")) {
            rejectSigning("Committed timestamp and snapshot versions differ")
        }
        val snapshotBytes = referenced(snapshotDescriptor, "snapshot")
        val snapshot = verifyEnvelope(snapshotBytes, "snapshot", snapshotDescriptor.version, root.snapshotKey)
        val meta = snapshot.obj("meta")
        if (meta.keys != SNAPSHOT_NAMES) rejectSigning("Committed snapshot references unexpected roles")
        val targetsDescriptor = descriptor(meta.getValue("targets.json"))
        val releasesDescriptor = descriptor(meta.getValue("releases.json"))
        val securityDescriptor = descriptor(meta.getValue("security.json"))
        val targets = validateTopLevelTargets(referenced(targetsDescriptor, "targets"), root)
        val releases = verifyDelegatedEnvelope(
            referenced(releasesDescriptor, TufRole.RELEASES),
            TufRole.RELEASES,
            releasesDescriptor.version,
            targets,
        )
        val security = verifyDelegatedEnvelope(
            referenced(securityDescriptor, TufRole.SECURITY),
            TufRole.SECURITY,
            securityDescriptor.version,
            targets,
        )
        return TrustedSignerState(
            root = root,
            targets = targets,
            current = CurrentTransaction(
                version = timestamp.long("version"),
                targets = targetsDescriptor,
                releases = releasesDescriptor,
                security = securityDescriptor,
                releasesExpiry = Instant.parse(releases.string("expires")),
                securityExpiry = Instant.parse(security.string("expires")),
            ),
            timestampGeneration = timestampObject.generation,
        )
    }

    private fun parseRootTrust(bytes: ByteArray): RootTrust {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope.getValue("signed").jsonObject
        val keys = parseKeys(signed.obj("keys"))
        val roles = signed.obj("roles")
        val rootIds = roleIds(roles, "root", 3, 2)
        val targetsIds = roleIds(roles, "targets", 2, 2)
        val snapshotId = roleIds(roles, "snapshot", 1, 1).single()
        val timestampId = roleIds(roles, "timestamp", 1, 1).single()
        if (keys.keys != (rootIds + targetsIds + snapshotId + timestampId).toSet()) {
            rejectSigning("Trusted root keys do not match its roles")
        }
        return RootTrust(keys, targetsIds, keys.getValue(snapshotId), keys.getValue(timestampId))
    }

    private fun validateTopLevelTargets(bytes: ByteArray, root: RootTrust): TopLevelTargets {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope.getValue("signed").jsonObject
        requireCommon(signed, "targets")
        if (!Instant.parse(signed.string("expires")).isAfter(clock.instant())) {
            rejectSigning("Top-level targets metadata is expired")
        }
        if (signed.keys != TOP_LEVEL_TARGETS_FIELDS || signed.obj("targets").isNotEmpty()) {
            rejectSigning("Top-level targets metadata fields are invalid")
        }
        val signatures = signatureIds(envelope)
        if (signatures.toSet() != root.targetsKeyIds.toSet() || signatures.size != 2) {
            rejectSigning("Top-level targets signatures are invalid")
        }
        runCatching { verifyThreshold(envelope, root.keys, root.targetsKeyIds.toSet(), 2) }
            .getOrElse { rejectSigning("Top-level targets threshold is invalid") }
        val delegations = signed.obj("delegations")
        if (delegations.keys != setOf("keys", "roles")) rejectSigning("Targets delegations fields are invalid")
        val delegatedKeys = parseKeys(delegations.obj("keys"))
        val roleObjects = delegations.getValue("roles").jsonArray.map { it.jsonObject }
        if (roleObjects.map { it.string("name") } != listOf(TufRole.SECURITY, TufRole.RELEASES)) {
            rejectSigning("Targets delegation order is invalid")
        }
        val roleKeyIds = roleObjects.associate { delegated ->
            val name = delegated.string("name")
            if (delegated.keys != DELEGATION_FIELDS || delegated.long("threshold") != 1L ||
                delegated.getValue("terminating").jsonPrimitive.content != "false" ||
                delegated.getValue("paths").jsonArray.map { it.jsonPrimitive.content } != listOf("packages/*/*/*/*/*")
            ) rejectSigning("$name delegation policy is invalid")
            val ids = delegated.getValue("keyids").jsonArray.map { it.jsonPrimitive.content }
            if (ids.size != 1 || ids.single() !in delegatedKeys) rejectSigning("$name delegation key is invalid")
            name to ids.single()
        }
        if (roleKeyIds.keys != setOf(TufRole.RELEASES, TufRole.SECURITY) ||
            roleKeyIds.values.toSet().size != 2 || roleKeyIds.values.toSet() != delegatedKeys.keys
        ) rejectSigning("Targets delegated keys are invalid")
        return TopLevelTargets(
            version = signed.long("version"),
            bytes = bytes,
            signed = signed,
            roleKeys = roleKeyIds.mapValues { (_, id) -> delegatedKeys.getValue(id) },
        )
    }

    private fun verifyDelegatedEnvelope(
        bytes: ByteArray,
        role: String,
        version: Long,
        targets: TopLevelTargets,
    ): JsonObject = verifyEnvelope(bytes, "targets", version, targets.roleKeys.getValue(role))

    private fun verifyEnvelope(
        bytes: ByteArray,
        type: String,
        version: Long?,
        publicKey: ByteArray,
    ): JsonObject {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope.getValue("signed").jsonObject
        requireCommon(signed, type)
        if (version != null && signed.long("version") != version) rejectSigning("$type metadata version is invalid")
        val keyId = tufKeyId(publicKey)
        if (signatureIds(envelope) != listOf(keyId)) rejectSigning("$type metadata signature identity is invalid")
        runCatching { verifyThreshold(envelope, mapOf(keyId to publicKey), setOf(keyId), 1) }
            .getOrElse { rejectSigning("$type metadata signature is invalid") }
        return signed
    }

    private fun requireCommon(signed: JsonObject, type: String) {
        if (signed.string("_type") != type || signed.string("spec_version") != "1.0" ||
            signed.string("environment") != environment || signed.string("repository_id") != repositoryId ||
            signed.long("version") < 1L
        ) rejectSigning("$type metadata identity is invalid")
        runCatching { Instant.parse(signed.string("expires")) }
            .getOrElse { rejectSigning("$type metadata expiry is invalid") }
    }

    private fun requireTransactionVersion(
        operation: TufSigningOperation,
        version: Long,
        current: CurrentTransaction?,
    ) {
        if (current == null) {
            if (operation != TufSigningOperation.BOOTSTRAP || version != 1L) {
                rejectSigning("Only transaction one may bootstrap an empty repository")
            }
        } else if (operation == TufSigningOperation.BOOTSTRAP || version <= current.version) {
            rejectSigning("Candidate transaction does not advance the committed version")
        }
    }

    /**
     * Ordinary purpose-specific publication may replace an expired changing
     * role while the retained role is still valid. Retaining an already-expired
     * opposite role is allowed only for the fail-closed dual-expiry recovery
     * transaction; every signer enforces this independently of the coordinator.
     */
    private fun requireRetainedDelegationState(
        operation: TufSigningOperation,
        current: CurrentTransaction?,
    ) {
        val committed = current ?: return
        val retainedExpiry = when (operation) {
            TufSigningOperation.RELEASE -> committed.securityExpiry
            TufSigningOperation.SECURITY -> committed.releasesExpiry
            else -> return
        }
        val now = clock.instant()
        if (!retainedExpiry.isAfter(now) &&
            (committed.releasesExpiry.isAfter(now) || committed.securityExpiry.isAfter(now))
        ) {
            rejectSigning("Expired opposite delegated metadata may be retained only when both delegated roles are expired")
        }
    }

    private fun requireSequentialTargets(candidate: MetadataDescriptor, current: MetadataDescriptor) {
        if (candidate.version != Math.addExact(current.version, 1L)) {
            rejectSigning("Offline targets version is not sequential")
        }
    }

    private fun requireUnchanged(candidate: MetadataDescriptor, current: MetadataDescriptor, role: String) {
        if (candidate != current) rejectSigning("$role descriptor changed outside its authorized operation")
    }

    private fun requireChanged(
        candidate: MetadataDescriptor,
        current: MetadataDescriptor,
        transactionVersion: Long,
        role: String,
    ) {
        if (candidate.version != transactionVersion || candidate.version <= current.version || candidate == current) {
            rejectSigning("$role descriptor does not advance exactly with the transaction")
        }
    }

    private fun requireDelegationsEqual(current: TopLevelTargets, candidate: TopLevelTargets) {
        if (current.signed.getValue("delegations") != candidate.signed.getValue("delegations")) {
            rejectSigning("Targets renewal changed delegated roles")
        }
    }

    private fun requireSingleDelegationRotation(
        current: TopLevelTargets,
        candidate: TopLevelTargets,
        affectedRole: String,
    ) {
        val other = if (affectedRole == TufRole.RELEASES) TufRole.SECURITY else TufRole.RELEASES
        if (candidate.roleKeys.getValue(other).contentEquals(current.roleKeys.getValue(other)).not() ||
            candidate.roleKeys.getValue(affectedRole).contentEquals(current.roleKeys.getValue(affectedRole)) ||
            current.roleKeys.values.any { it.contentEquals(candidate.roleKeys.getValue(affectedRole)) }
        ) rejectSigning("Targets rotation did not replace exactly the affected delegated key")
    }

    private fun referenced(descriptor: MetadataDescriptor, role: String): ByteArray {
        val bytes = requireObject("${descriptor.version}.$role.json").bytes
        if (bytes.size.toLong() != descriptor.length || sha256(bytes) != descriptor.sha256) {
            rejectSigning("$role candidate does not match its signed descriptor")
        }
        return bytes
    }

    private fun descriptor(element: kotlinx.serialization.json.JsonElement): MetadataDescriptor {
        val value = element as? JsonObject ?: rejectSigning("Metadata descriptor is invalid")
        if (value.keys != setOf("version", "length", "hashes")) rejectSigning("Metadata descriptor fields are invalid")
        val hashes = value.obj("hashes")
        if (hashes.keys != setOf("sha256")) rejectSigning("Metadata descriptor hashes are invalid")
        val digest = hashes.string("sha256")
        if (!SHA256.matches(digest)) rejectSigning("Metadata descriptor digest is invalid")
        val version = value.long("version")
        val length = value.long("length")
        if (version < 1L || length !in 1..MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES) {
            rejectSigning("Metadata descriptor bounds are invalid")
        }
        return MetadataDescriptor(version, length, digest)
    }

    private fun roleIds(roles: JsonObject, role: String, keys: Int, threshold: Int): List<String> {
        val value = roles[role] as? JsonObject ?: rejectSigning("Trusted root role is missing")
        if (value.keys != setOf("keyids", "threshold") || value.long("threshold") != threshold.toLong()) {
            rejectSigning("Trusted root $role policy is invalid")
        }
        return value.getValue("keyids").jsonArray.map { it.jsonPrimitive.content }.also {
            if (it.size != keys || it.toSet().size != keys) rejectSigning("Trusted root $role keys are invalid")
        }
    }

    private fun signatureIds(envelope: JsonObject): List<String> =
        (envelope["signatures"] as? JsonArray ?: rejectSigning("Metadata signatures are invalid")).map { signature ->
            val value = signature as? JsonObject ?: rejectSigning("Metadata signature is invalid")
            if (value.keys != setOf("keyid", "sig")) rejectSigning("Metadata signature fields are invalid")
            value.string("keyid")
        }

    private fun parseCanonicalSigned(bytes: ByteArray): JsonObject {
        val value = runCatching { RegistryJson.parseToJsonElement(bytes.decodeToString()) as? JsonObject }
            .getOrNull() ?: rejectSigning("TUF signed object is invalid")
        if (!canonicalJson(value).contentEquals(bytes)) rejectSigning("TUF signed object is not canonical")
        return value
    }

    private fun envelopeSigned(bytes: ByteArray): JsonObject = runCatching {
        parseCanonicalEnvelope(bytes).getValue("signed").jsonObject
    }.getOrElse { rejectSigning("TUF metadata envelope is invalid") }

    private fun optionalObject(filename: String): TufSignerMetadataObject? = metadata.get(filename)?.also { value ->
        if (value.bytes.isEmpty() || value.bytes.size.toLong() > MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES) {
            rejectSigning("TUF metadata object $filename exceeds the signer size bound")
        }
    }

    private fun requireObject(filename: String): TufSignerMetadataObject = optionalObject(filename)
        ?: rejectSigning("Required TUF metadata $filename is missing")

    private fun JsonObject.obj(name: String): JsonObject = this[name] as? JsonObject
        ?: rejectSigning("TUF metadata $name is invalid")

    private fun JsonObject.string(name: String): String {
        val value = this[name] as? JsonPrimitive ?: rejectSigning("TUF metadata $name is invalid")
        if (!value.isString) rejectSigning("TUF metadata $name is invalid")
        return value.content
    }

    private fun JsonObject.long(name: String): Long {
        val value = this[name] as? JsonPrimitive ?: rejectSigning("TUF metadata $name is invalid")
        if (value.isString) rejectSigning("TUF metadata $name is invalid")
        return value.content.toLongOrNull() ?: rejectSigning("TUF metadata $name is invalid")
    }

    private data class RootTrust(
        val keys: Map<String, ByteArray>,
        val targetsKeyIds: List<String>,
        val snapshotKey: ByteArray,
        val timestampKey: ByteArray,
    )

    private data class TopLevelTargets(
        val version: Long,
        val bytes: ByteArray,
        val signed: JsonObject,
        val roleKeys: Map<String, ByteArray>,
    )

    private data class MetadataDescriptor(val version: Long, val length: Long, val sha256: String)

    private data class CurrentTransaction(
        val version: Long,
        val targets: MetadataDescriptor,
        val releases: MetadataDescriptor,
        val security: MetadataDescriptor,
        val releasesExpiry: Instant,
        val securityExpiry: Instant,
    )

    private data class TrustedSignerState(
        val root: RootTrust,
        val targets: TopLevelTargets,
        val current: CurrentTransaction?,
        val timestampGeneration: Long?,
    )

    private companion object {
        val COMMON_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id")
        val TOP_LEVEL_TARGETS_FIELDS = COMMON_FIELDS + setOf("targets", "delegations")
        val SNAPSHOT_NAMES = setOf("targets.json", "releases.json", "security.json")
        val DELEGATION_FIELDS = setOf("name", "keyids", "threshold", "terminating", "paths")
        val SHA256 = Regex("^[0-9a-f]{64}$")
        const val MAX_ROOT_CHAIN = 1024L
    }
}

private fun verifyRawEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
    if (publicKey.size != 32 || signature.size != 64) return false
    return org.bouncycastle.crypto.signers.Ed25519Signer().run {
        init(false, org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(publicKey))
        update(message, 0, message.size)
        verifySignature(signature)
    }
}

private fun rejectSigning(message: String): Nothing = throw TufSigningRequestException(message)
