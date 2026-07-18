package codes.yousef.seen.registry

import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

interface TufSigner : AutoCloseable {
    val publicKey: ByteArray
    /** True only when signing also conditionally publishes timestamp.json. */
    val commitsTimestampPointer: Boolean get() = false
    /** Bounded readback window for an ambiguous remote timestamp response. */
    val timestampCommitRecoveryTimeout: Duration get() = Duration.ZERO
    fun sign(canonicalSignedBytes: ByteArray): ByteArray
    override fun close() = Unit
}

class LocalEd25519Signer private constructor(private val privateKey: Ed25519PrivateKeyParameters) : TufSigner {
    override val publicKey: ByteArray = privateKey.generatePublicKey().encoded
    override fun sign(canonicalSignedBytes: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, privateKey)
        update(canonicalSignedBytes, 0, canonicalSignedBytes.size)
        generateSignature()
    }

    companion object {
        fun fromPkcs8Base64(value: String): LocalEd25519Signer {
            val key = PrivateKeyFactory.createKey(Base64.getDecoder().decode(value))
            require(key is Ed25519PrivateKeyParameters) { "Local signing key must be Ed25519 PKCS#8" }
            return LocalEd25519Signer(key)
        }
        fun fromSeed(seed: ByteArray): LocalEd25519Signer {
            require(seed.size == 32)
            return LocalEd25519Signer(Ed25519PrivateKeyParameters(seed))
        }
    }
}

class KmsEd25519Signer(
    private val client: KeyManagementServiceClient,
    private val keyVersionName: String,
    override val publicKey: ByteArray,
) : TufSigner {
    init { require(publicKey.size == 32) { "KMS Ed25519 public key must contain 32 raw bytes" } }
    override fun sign(canonicalSignedBytes: ByteArray): ByteArray = client.asymmetricSign(
        AsymmetricSignRequest.newBuilder().setName(keyVersionName).setData(ByteString.copyFrom(canonicalSignedBytes)).build(),
    ).signature.toByteArray().also { require(it.size == 64) { "KMS returned a non-Ed25519 signature" } }
    override fun close() = client.close()
}

/** Public-key placeholder used by roles that may verify but must never sign. */
class PublicKeyOnlyTufSigner(override val publicKey: ByteArray) : TufSigner {
    init { require(publicKey.size == 32) { "TUF public key must contain 32 raw bytes" } }
    override fun sign(canonicalSignedBytes: ByteArray): ByteArray =
        error("This workload has no signing authority for the requested TUF role")
}

data class TufOnlineSigners(
    val releases: TufSigner,
    val security: TufSigner,
    val snapshot: TufSigner,
    val timestamp: TufSigner,
) : AutoCloseable {
    init {
        val ids = asMap().values.map { tufKeyId(it.publicKey) }
        require(ids.toSet().size == 4) { "Online TUF roles must have distinct Ed25519 keys" }
    }
    fun asMap(): Map<String, TufSigner> = mapOf(
        TufRole.RELEASES to releases,
        TufRole.SECURITY to security,
        TufRole.SNAPSHOT to snapshot,
        TufRole.TIMESTAMP to timestamp,
    )
    fun publicKeys(): TufOnlineKeys = TufOnlineKeys(releases.publicKey, security.publicKey, snapshot.publicKey, timestamp.publicKey)
    override fun close() = asMap().values.forEach(TufSigner::close)
}

data class TufOnlineKeys(
    val releases: ByteArray,
    val security: ByteArray,
    val snapshot: ByteArray,
    val timestamp: ByteArray,
) {
    init {
        val values = asMap().values
        require(values.all { it.size == 32 }) { "Online TUF public keys must be raw Ed25519 keys" }
        require(values.map(::tufKeyId).toSet().size == 4) { "Online TUF role keys must be distinct" }
    }
    fun asMap(): Map<String, ByteArray> = mapOf(
        TufRole.RELEASES to releases,
        TufRole.SECURITY to security,
        TufRole.SNAPSHOT to snapshot,
        TufRole.TIMESTAMP to timestamp,
    )
}

data class TufBootstrapResult(val root: ByteArray, val targets: ByteArray, val rootKeyIds: List<String>, val targetsKeyIds: List<String>)

data class TufTargetsRenewalResult(
    val version: Long,
    val targets: ByteArray,
    val targetsKeyIds: List<String>,
    val changedDelegatedRoles: Set<String> = emptySet(),
)

data class TufTargetsRenewalImportResult(
    val targetsVersion: Long,
    val onlineTransactionVersion: Long,
)

data class TufRootRotationResult(
    val version: Long,
    val root: ByteArray,
    val previousRootKeyIds: List<String>,
    val nextRootKeyIds: List<String>,
)

data class TufRootRotationImportResult(val rootVersion: Long)

private data class TargetsRootTrust(
    val keys: Map<String, ByteArray>,
    val rootKeyIds: List<String>,
    val targetsKeyIds: List<String>,
    val snapshotKeyId: String,
    val timestampKeyId: String,
)

internal data class TrustedTopLevelTargets(
    val root: ByteArray,
    val targets: ByteArray,
    val version: Long,
)

/** Shared verifier for the air-gapped ceremony and the one-shot online import. */
internal class TufTargetsRenewalPolicy(
    private val online: TufOnlineKeys,
    private val environment: String,
    private val repositoryId: String,
    private val clock: Clock,
) {
    fun prepare(rootBytes: ByteArray, currentTargetsBytes: ByteArray, signers: List<TufSigner>): TufTargetsRenewalResult {
        val root = validateRoot(rootBytes)
        val current = validateTargets(root, currentTargetsBytes, requireFresh = false, allowExpired = true)
        val signerIds = signers.map { tufKeyId(it.publicKey) }
        require(signerIds.size == 2 && signerIds.toSet() == root.targetsKeyIds.toSet()) {
            "Targets renewal requires both offline targets signers from the trusted root"
        }
        val nextVersion = Math.addExact(current.version, 1L)
        val renewedSigned = current.signed.toMutableMap().apply {
            put("version", JsonPrimitive(nextVersion))
            put("expires", JsonPrimitive(clock.instant().plus(TARGETS_LIFETIME).utc()))
        }.let(::JsonObject)
        val renewed = envelope(renewedSigned, signers)
        validateRenewal(rootBytes, currentTargetsBytes, renewed)
        return TufTargetsRenewalResult(nextVersion, renewed, root.targetsKeyIds)
    }

    fun validateRenewal(rootBytes: ByteArray, currentTargetsBytes: ByteArray, candidateBytes: ByteArray): TufTargetsRenewalResult {
        val root = validateRoot(rootBytes)
        val current = validateTargets(root, currentTargetsBytes, requireFresh = false, allowExpired = true)
        val candidate = validateTargets(root, candidateBytes, requireFresh = true)
        require(candidate.version == Math.addExact(current.version, 1L)) {
            "Targets renewal version must be exactly ${current.version + 1}"
        }
        require(candidate.signed.withoutRenewedFields() == current.signed.withoutRenewedFields()) {
            "Targets renewal may change only version and expires"
        }
        return TufTargetsRenewalResult(candidate.version, candidateBytes, root.targetsKeyIds)
    }

    fun prepareRotation(rootBytes: ByteArray, currentTargetsBytes: ByteArray, signers: List<TufSigner>): TufTargetsRenewalResult {
        val root = validateRoot(rootBytes)
        val current = validateTargets(
            root,
            currentTargetsBytes,
            requireFresh = false,
            allowExpired = true,
            requireConfiguredDelegations = false,
        )
        val signerIds = signers.map { tufKeyId(it.publicKey) }
        require(signerIds.size == 2 && signerIds.toSet() == root.targetsKeyIds.toSet()) {
            "Targets rotation requires both offline targets signers from the trusted root"
        }
        requireConfiguredOnlineKeysAreDistinctFromOffline(root)
        val nextVersion = Math.addExact(current.version, 1L)
        val rotatedSigned = current.signed.toMutableMap().apply {
            put("version", JsonPrimitive(nextVersion))
            put("expires", JsonPrimitive(clock.instant().plus(TARGETS_LIFETIME).utc()))
            put("delegations", configuredDelegations())
        }.let(::JsonObject)
        val rotated = envelope(rotatedSigned, signers)
        return validateRotation(rootBytes, currentTargetsBytes, rotated)
    }

    fun validateRotation(
        rootBytes: ByteArray,
        currentTargetsBytes: ByteArray,
        candidateBytes: ByteArray,
    ): TufTargetsRenewalResult {
        val root = validateRoot(rootBytes)
        val current = validateTargets(
            root,
            currentTargetsBytes,
            requireFresh = false,
            allowExpired = true,
            requireConfiguredDelegations = false,
        )
        requireConfiguredOnlineKeysAreDistinctFromOffline(root)
        val candidate = validateTargets(root, candidateBytes, requireFresh = true)
        require(candidate.version == Math.addExact(current.version, 1L)) {
            "Targets rotation version must be exactly ${current.version + 1}"
        }
        require(candidate.signed.withoutRotationFields() == current.signed.withoutRotationFields()) {
            "Targets rotation may change only version, expires, and delegations"
        }
        val currentRoleKeys = delegationRoleKeyIds(current.signed["delegations"]!!.jsonObject)
        val candidateRoleKeys = delegationRoleKeyIds(candidate.signed["delegations"]!!.jsonObject)
        val changedRoles = currentRoleKeys.keys.filter { currentRoleKeys[it] != candidateRoleKeys[it] }
        require(changedRoles.isNotEmpty()) {
            "Targets rotation must replace at least one delegated online key"
        }
        val formerDelegatedKeyIds = currentRoleKeys.values.toSet()
        changedRoles.forEach { role ->
            require(candidateRoleKeys.getValue(role) !in formerDelegatedKeyIds) {
                "Targets rotation cannot reuse a formerly delegated key for $role"
            }
        }
        return TufTargetsRenewalResult(
            candidate.version,
            candidateBytes,
            root.targetsKeyIds,
            changedRoles.toSet(),
        )
    }

    fun resolveCurrent(
        storage: RegistryObjectStorage,
        requireOnlineTransaction: Boolean,
        allowExpiredTargets: Boolean = false,
        requireConfiguredDelegations: Boolean = true,
    ): TrustedTopLevelTargets {
        val rootBytes = storage.getMetadata("root.json") ?: storage.getMetadata("1.root.json")
            ?: error("Offline TUF bootstrap is missing root.json")
        val root = validateRoot(rootBytes)
        val timestampBytes = storage.getMetadata("timestamp.json")
        if (timestampBytes == null) {
            require(!requireOnlineTransaction) { "Online TUF transaction is missing timestamp.json" }
            val targetsBytes = storage.getMetadata("1.targets.json")
                ?: error("Offline TUF bootstrap is missing 1.targets.json")
            val targets = validateTargets(
                root,
                targetsBytes,
                requireFresh = false,
                allowExpired = allowExpiredTargets,
                requireConfiguredDelegations = requireConfiguredDelegations,
            )
            return TrustedTopLevelTargets(rootBytes, targetsBytes, targets.version)
        }

        val timestamp = validateOnlineEnvelope(timestampBytes, "timestamp", root.keys, root.timestampKeyId)
        val timestampMeta = timestamp.signed["meta"]?.jsonObject
            ?: error("Timestamp metadata is missing meta")
        require(timestampMeta.keys == setOf("snapshot.json")) { "Timestamp metadata references unexpected roles" }
        val (snapshotVersion, snapshotBytes) = referenced(storage, timestampMeta.getValue("snapshot.json").jsonObject, "snapshot")
        val snapshot = validateOnlineEnvelope(snapshotBytes, "snapshot", root.keys, root.snapshotKeyId, snapshotVersion)
        val snapshotMeta = snapshot.signed["meta"]?.jsonObject
            ?: error("Snapshot metadata is missing meta")
        require(snapshotMeta.keys == setOf("targets.json", "releases.json", "security.json")) {
            "Snapshot metadata references unexpected roles"
        }
        val (targetsVersion, targetsBytes) = referenced(storage, snapshotMeta.getValue("targets.json").jsonObject, "targets")
        val targets = validateTargets(
            root,
            targetsBytes,
            requireFresh = false,
            allowExpired = allowExpiredTargets,
            requireConfiguredDelegations = requireConfiguredDelegations,
        )
        require(targets.version == targetsVersion) { "Snapshot references the wrong targets version" }
        return TrustedTopLevelTargets(rootBytes, targetsBytes, targets.version)
    }

    private fun validateRoot(bytes: ByteArray): TargetsRootTrust {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope["signed"]!!.jsonObject
        require(signed.keys == ROOT_FIELDS) { "Root metadata fields are invalid" }
        validateIdentity(signed, "root")
        require(signed["version"]!!.jsonPrimitive.content.toLong() >= 1L) { "Root metadata version is invalid" }
        require(signed["consistent_snapshot"]?.jsonPrimitive?.content == "true") { "Root must enable consistent snapshots" }
        val remaining = Duration.between(clock.instant(), Instant.parse(signed["expires"]!!.jsonPrimitive.content))
        require(!remaining.isNegative && !remaining.isZero) { "Trusted root metadata is expired" }

        val keys = parseKeys(signed["keys"]!!.jsonObject)
        val roles = signed["roles"]!!.jsonObject
        require(roles.keys == setOf("root", "targets", "snapshot", "timestamp")) { "Root contains unexpected roles" }
        val rootRole = parseRole(roles, "root", 3, 2)
        val targetsRole = parseRole(roles, "targets", 2, 2)
        val snapshotRole = parseRole(roles, "snapshot", 1, 1)
        val timestampRole = parseRole(roles, "timestamp", 1, 1)
        require(keys.keys == (rootRole + targetsRole + snapshotRole + timestampRole).toSet()) {
            "Root contains unexpected or missing role keys"
        }
        require(snapshotRole.single() == tufKeyId(online.snapshot)) { "Root snapshot key does not match the configured online key" }
        require(timestampRole.single() == tufKeyId(online.timestamp)) { "Root timestamp key does not match the configured online key" }
        verifyThreshold(envelope, keys, rootRole.toSet(), 2)
        return TargetsRootTrust(keys, rootRole, targetsRole, snapshotRole.single(), timestampRole.single())
    }

    private fun validateTargets(
        root: TargetsRootTrust,
        bytes: ByteArray,
        requireFresh: Boolean,
        allowExpired: Boolean = false,
        requireConfiguredDelegations: Boolean = true,
    ): ValidatedTargets {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope["signed"]!!.jsonObject
        require(signed.keys == TARGETS_FIELDS) { "Top-level targets metadata fields are invalid" }
        validateIdentity(signed, "targets")
        val version = signed["version"]!!.jsonPrimitive.content.toLong()
        require(version >= 1L) { "Top-level targets version is invalid" }
        val remaining = Duration.between(clock.instant(), Instant.parse(signed["expires"]!!.jsonPrimitive.content))
        if (requireFresh) {
            require(!remaining.isNegative && !remaining.isZero && remaining <= TARGETS_LIFETIME) {
                "Targets renewal expiry must be in the future and no more than 30 days away"
            }
        } else if (!allowExpired) {
            require(!remaining.isNegative && !remaining.isZero) { "Trusted targets metadata is expired" }
        }
        require(signed["targets"]?.jsonObject?.isEmpty() == true) { "Offline top-level targets must not contain package targets" }
        requireExactTargetsSignatures(envelope, root.targetsKeyIds)
        verifyThreshold(envelope, root.keys, root.targetsKeyIds.toSet(), 2)
        if (requireConfiguredDelegations) {
            validateDelegations(signed["delegations"]!!.jsonObject)
        } else {
            validateDelegationShape(signed["delegations"]!!.jsonObject)
        }
        return ValidatedTargets(version, signed)
    }

    private fun validateDelegations(delegations: JsonObject) {
        val delegatedKeys = validateDelegationShape(delegations)
        val expected = mapOf(
            tufKeyId(online.releases) to online.releases,
            tufKeyId(online.security) to online.security,
        )
        require(delegatedKeys.keys == expected.keys && delegatedKeys.all { (id, key) -> key.contentEquals(expected[id]) }) {
            "Delegated keys do not match configured releases/security online keys"
        }
        val roles = delegations["roles"]!!.jsonArray.map(JsonElement::jsonObject)
            .associateBy { it["name"]!!.jsonPrimitive.content }
        mapOf(TufRole.SECURITY to online.security, TufRole.RELEASES to online.releases).forEach { (name, key) ->
            require(roles.getValue(name)["keyids"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf(tufKeyId(key))) {
                "$name delegation key is invalid"
            }
        }
    }

    private fun validateDelegationShape(delegations: JsonObject): Map<String, ByteArray> {
        require(delegations.keys == setOf("keys", "roles")) { "Targets delegations fields are invalid" }
        val delegatedKeys = parseKeys(delegations["keys"]!!.jsonObject)
        require(delegatedKeys.size == 2) { "Targets must contain exactly two delegated keys" }
        val roles = delegations["roles"]!!.jsonArray.map(JsonElement::jsonObject)
        require(roles.map { it["name"]!!.jsonPrimitive.content } == listOf(TufRole.SECURITY, TufRole.RELEASES)) {
            "Targets delegation order must be security then releases"
        }
        val byName = roles.associateBy { it["name"]!!.jsonPrimitive.content }
        require(byName.keys == setOf(TufRole.SECURITY, TufRole.RELEASES)) { "Targets contains unexpected delegated roles" }
        val referencedIds = mutableSetOf<String>()
        listOf(TufRole.SECURITY, TufRole.RELEASES).forEach { name ->
            val role = byName.getValue(name)
            require(role.keys == setOf("name", "keyids", "threshold", "terminating", "paths")) { "$name delegation fields are invalid" }
            val keyIds = role["keyids"]!!.jsonArray.map { it.jsonPrimitive.content }
            require(keyIds.size == 1 && keyIds.single() in delegatedKeys) { "$name delegation key is invalid" }
            referencedIds += keyIds.single()
            require(role["threshold"]!!.jsonPrimitive.content == "1") { "$name delegation threshold is invalid" }
            require(role["terminating"]!!.jsonPrimitive.content == "false") { "$name delegation termination policy is invalid" }
            require(role["paths"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf("packages/*/*/*/*/*")) { "$name delegation path is invalid" }
        }
        require(referencedIds == delegatedKeys.keys) { "Targets delegated roles must reference distinct configured keys" }
        return delegatedKeys
    }

    private fun configuredDelegations(): JsonObject = buildJsonObject {
        put("keys", JsonObject(mapOf(
            tufKeyId(online.releases) to tufKeyObject(online.releases),
            tufKeyId(online.security) to tufKeyObject(online.security),
        )))
        put("roles", buildJsonArray {
            add(delegatedRole(TufRole.SECURITY, tufKeyId(online.security)))
            add(delegatedRole(TufRole.RELEASES, tufKeyId(online.releases)))
        })
    }

    private fun delegationRoleKeyIds(delegations: JsonObject): Map<String, String> =
        delegations["roles"]!!.jsonArray
            .map(JsonElement::jsonObject)
            .associate { role ->
                role["name"]!!.jsonPrimitive.content to
                    role["keyids"]!!.jsonArray.single().jsonPrimitive.content
            }

    private fun requireConfiguredOnlineKeysAreDistinctFromOffline(root: TargetsRootTrust) {
        val delegated = setOf(tufKeyId(online.releases), tufKeyId(online.security))
        require(delegated.intersect(root.keys.keys).isEmpty()) {
            "Replacement delegated keys must be distinct from every root-bound role key"
        }
    }

    private fun validateOnlineEnvelope(
        bytes: ByteArray,
        type: String,
        keys: Map<String, ByteArray>,
        keyId: String,
        expectedVersion: Long? = null,
    ): ValidatedEnvelope {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope["signed"]!!.jsonObject
        validateIdentity(signed, type)
        val version = signed["version"]!!.jsonPrimitive.content.toLong()
        require(version >= 1L && (expectedVersion == null || version == expectedVersion)) { "$type metadata version is invalid" }
        val signatures = envelope["signatures"]!!.jsonArray.map { it.jsonObject["keyid"]!!.jsonPrimitive.content }
        require(signatures == listOf(keyId)) { "$type metadata must have exactly its configured online signature" }
        verifyThreshold(envelope, keys, setOf(keyId), 1)
        return ValidatedEnvelope(version, signed)
    }

    private fun validateIdentity(signed: JsonObject, type: String) {
        require(signed["_type"]?.jsonPrimitive?.content == type) { "Metadata role type is invalid" }
        require(signed["spec_version"]?.jsonPrimitive?.content == "1.0") { "TUF spec version is invalid" }
        require(signed["environment"]?.jsonPrimitive?.content == environment) { "Metadata environment is invalid" }
        require(signed["repository_id"]?.jsonPrimitive?.content == repositoryId) { "Metadata repository is invalid" }
        Instant.parse(signed["expires"]!!.jsonPrimitive.content)
    }

    private fun referenced(storage: RegistryObjectStorage, meta: JsonObject, role: String): Pair<Long, ByteArray> {
        require(meta.keys == setOf("version", "length", "hashes")) { "$role reference fields are invalid" }
        val version = meta["version"]!!.jsonPrimitive.content.toLong()
        val bytes = storage.getMetadata("$version.$role.json") ?: error("Metadata references missing $role metadata")
        require(meta["length"]!!.jsonPrimitive.content.toLong() == bytes.size.toLong()) { "$role metadata length is invalid" }
        require(meta["hashes"]!!.jsonObject.keys == setOf("sha256") &&
            meta["hashes"]!!.jsonObject["sha256"]!!.jsonPrimitive.content == sha256(bytes)) { "$role metadata digest is invalid" }
        return version to bytes
    }

    private fun requireExactTargetsSignatures(envelope: JsonObject, targetKeyIds: List<String>) {
        val signatures = envelope["signatures"]!!.jsonArray.map { signature ->
            val value = signature.jsonObject
            require(value.keys == setOf("keyid", "sig")) { "Targets signature fields are invalid" }
            value["keyid"]!!.jsonPrimitive.content
        }
        require(signatures.size == 2 && signatures.toSet() == targetKeyIds.toSet()) {
            "Top-level targets must carry exactly both offline signatures"
        }
    }

    private fun JsonObject.withoutRenewedFields(): JsonObject = JsonObject(filterKeys { it != "version" && it != "expires" })
    private fun JsonObject.withoutRotationFields(): JsonObject = JsonObject(
        filterKeys { it != "version" && it != "expires" && it != "delegations" },
    )

    private data class ValidatedTargets(val version: Long, val signed: JsonObject)
    private data class ValidatedEnvelope(val version: Long, val signed: JsonObject)

    private companion object {
        val TARGETS_LIFETIME: Duration = Duration.ofDays(30)
        val ROOT_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id", "consistent_snapshot", "keys", "roles")
        val TARGETS_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id", "targets", "delegations")
    }
}

/** Sequential root recovery verified against both the currently trusted and replacement thresholds. */
internal class TufRootRotationPolicy(
    private val environment: String,
    private val repositoryId: String,
    private val clock: Clock,
) {
    fun prepare(
        currentRootBytes: ByteArray,
        currentRootSigners: List<TufSigner>,
        nextRootPublicKeys: List<ByteArray>,
        nextRootSigners: List<TufSigner>,
    ): TufRootRotationResult {
        val current = validateRoot(currentRootBytes, requireFresh = false)
        requireThresholdSigners(
            currentRootSigners,
            current.rootKeyIds.toSet(),
            "Root rotation requires at least two current root signers",
        )
        require(nextRootPublicKeys.size == 3 && nextRootPublicKeys.all { it.size == 32 }) {
            "Root rotation requires exactly three replacement raw Ed25519 public keys"
        }
        val nextRootKeyIds = nextRootPublicKeys.map(::tufKeyId)
        require(nextRootKeyIds.toSet().size == 3) { "Replacement root keys must be distinct" }
        require(nextRootKeyIds.toSet().intersect(current.keys.keys).isEmpty()) {
            "Replacement root keys must be fresh and distinct from every currently trusted role key"
        }
        requireThresholdSigners(
            nextRootSigners,
            nextRootKeyIds.toSet(),
            "Root rotation requires at least two replacement root signers",
        )

        val currentRootIds = current.rootKeyIds.toSet()
        val rotatedKeys = current.signed["keys"]!!.jsonObject.toMutableMap().apply {
            currentRootIds.forEach { remove(it) }
            nextRootPublicKeys.forEach { key -> put(tufKeyId(key), tufKeyObject(key)) }
        }
        val rotatedRoles = current.signed["roles"]!!.jsonObject.toMutableMap().apply {
            put("root", role(nextRootKeyIds, 2))
        }
        val nextVersion = Math.addExact(current.version, 1L)
        val rotatedSigned = current.signed.toMutableMap().apply {
            put("version", JsonPrimitive(nextVersion))
            put("expires", JsonPrimitive(clock.instant().plus(ROOT_LIFETIME).utc()))
            put("keys", JsonObject(rotatedKeys))
            put("roles", JsonObject(rotatedRoles))
        }.let(::JsonObject)
        val rotated = envelope(
            rotatedSigned,
            (currentRootSigners + nextRootSigners).distinctBy { tufKeyId(it.publicKey) },
        )
        return validateRotation(currentRootBytes, rotated)
    }

    fun validateRotation(currentRootBytes: ByteArray, candidateBytes: ByteArray): TufRootRotationResult {
        return validateRotation(currentRootBytes, candidateBytes, requireCandidateFresh = true)
    }

    fun validateChain(rootVersions: List<ByteArray>): Long {
        require(rootVersions.isNotEmpty()) { "Root chain is empty" }
        val first = validateRoot(rootVersions.first(), requireFresh = rootVersions.size == 1)
        require(first.version == 1L) { "Root chain must begin at version one" }
        rootVersions.zipWithNext().forEachIndexed { index, (current, candidate) ->
            validateRotation(
                current,
                candidate,
                requireCandidateFresh = index == rootVersions.size - 2,
            )
        }
        return rootVersions.size.toLong()
    }

    private fun validateRotation(
        currentRootBytes: ByteArray,
        candidateBytes: ByteArray,
        requireCandidateFresh: Boolean,
    ): TufRootRotationResult {
        val current = validateRoot(currentRootBytes, requireFresh = false)
        val candidate = validateRoot(candidateBytes, requireFresh = requireCandidateFresh)
        require(candidate.version == Math.addExact(current.version, 1L)) {
            "Root rotation version must be exactly ${current.version + 1}"
        }
        require(candidate.rootKeyIds.toSet().intersect(current.keys.keys).isEmpty()) {
            "Replacement root keys must be fresh and distinct from every currently trusted role key"
        }
        require(candidate.targetsKeyIds == current.targetsKeyIds) {
            "Root rotation must preserve the targets role byte-logically"
        }
        require(candidate.snapshotKeyIds == current.snapshotKeyIds) {
            "Root rotation must preserve the snapshot role byte-logically"
        }
        require(candidate.timestampKeyIds == current.timestampKeyIds) {
            "Root rotation must preserve the timestamp role byte-logically"
        }
        val currentNonRootKeyIds = current.keys.keys - current.rootKeyIds.toSet()
        val candidateNonRootKeyIds = candidate.keys.keys - candidate.rootKeyIds.toSet()
        require(candidateNonRootKeyIds == currentNonRootKeyIds) {
            "Root rotation must preserve every non-root role key"
        }
        currentNonRootKeyIds.forEach { keyId ->
            require(candidate.signed["keys"]!!.jsonObject[keyId] == current.signed["keys"]!!.jsonObject[keyId]) {
                "Root rotation must preserve every non-root key object byte-logically"
            }
        }
        listOf("targets", "snapshot", "timestamp").forEach { roleName ->
            require(candidate.signed["roles"]!!.jsonObject[roleName] == current.signed["roles"]!!.jsonObject[roleName]) {
                "Root rotation must preserve the $roleName role byte-logically"
            }
        }
        require(normalizeRotation(candidate, current) == current.signed) {
            "Root rotation may change only version, expires, and root role keys"
        }
        requireDualThresholdSignatures(current, candidate, candidateBytes)
        return TufRootRotationResult(
            version = candidate.version,
            root = candidateBytes,
            previousRootKeyIds = current.rootKeyIds,
            nextRootKeyIds = candidate.rootKeyIds,
        )
    }

    private fun validateRoot(bytes: ByteArray, requireFresh: Boolean): ValidatedRoot {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope["signed"]!!.jsonObject
        require(signed.keys == ROOT_FIELDS) { "Root metadata fields are invalid" }
        require(signed["_type"]?.jsonPrimitive?.content == "root") { "Metadata role type is invalid" }
        require(signed["spec_version"]?.jsonPrimitive?.content == "1.0") { "TUF spec version is invalid" }
        require(signed["environment"]?.jsonPrimitive?.content == environment) { "Metadata environment is invalid" }
        require(signed["repository_id"]?.jsonPrimitive?.content == repositoryId) { "Metadata repository is invalid" }
        require(signed["consistent_snapshot"]?.jsonPrimitive?.content == "true") { "Root must enable consistent snapshots" }
        val version = signed["version"]!!.jsonPrimitive.content.toLong()
        require(version >= 1L) { "Root metadata version is invalid" }
        val remaining = Duration.between(clock.instant(), Instant.parse(signed["expires"]!!.jsonPrimitive.content))
        if (requireFresh) {
            require(!remaining.isNegative && !remaining.isZero && remaining <= ROOT_LIFETIME) {
                "Root rotation expiry must be in the future and no more than 365 days away"
            }
        }
        val keys = parseKeys(signed["keys"]!!.jsonObject)
        val roles = signed["roles"]!!.jsonObject
        require(roles.keys == setOf("root", "targets", "snapshot", "timestamp")) { "Root contains unexpected roles" }
        val rootRole = parseRole(roles, "root", 3, 2)
        val targetsRole = parseRole(roles, "targets", 2, 2)
        val snapshotRole = parseRole(roles, "snapshot", 1, 1)
        val timestampRole = parseRole(roles, "timestamp", 1, 1)
        val allRoleIds = rootRole + targetsRole + snapshotRole + timestampRole
        require(allRoleIds.toSet().size == allRoleIds.size) { "Every root-bound role key must be distinct" }
        require(keys.keys == allRoleIds.toSet()) { "Root contains unexpected or missing role keys" }
        verifyThreshold(envelope, keys, rootRole.toSet(), 2)
        return ValidatedRoot(version, signed, keys, rootRole, targetsRole, snapshotRole, timestampRole)
    }

    private fun requireThresholdSigners(signers: List<TufSigner>, allowed: Set<String>, message: String) {
        val ids = signers.map { tufKeyId(it.publicKey) }
        require(ids.toSet().size >= 2 && ids.size == ids.toSet().size && ids.all(allowed::contains)) { message }
    }

    private fun normalizeRotation(candidate: ValidatedRoot, current: ValidatedRoot): JsonObject {
        val keys = candidate.signed["keys"]!!.jsonObject.toMutableMap().apply {
            candidate.rootKeyIds.forEach { remove(it) }
            current.rootKeyIds.forEach { keyId -> put(keyId, current.signed["keys"]!!.jsonObject.getValue(keyId)) }
        }
        val roles = candidate.signed["roles"]!!.jsonObject.toMutableMap().apply {
            put("root", current.signed["roles"]!!.jsonObject.getValue("root"))
        }
        return JsonObject(candidate.signed.toMutableMap().apply {
            put("version", current.signed.getValue("version"))
            put("expires", current.signed.getValue("expires"))
            put("keys", JsonObject(keys))
            put("roles", JsonObject(roles))
        })
    }

    private fun requireDualThresholdSignatures(
        current: ValidatedRoot,
        candidate: ValidatedRoot,
        candidateBytes: ByteArray,
    ) {
        val envelope = parseCanonicalEnvelope(candidateBytes)
        val signatures = envelope["signatures"]!!.jsonArray.map { signature ->
            val value = signature.jsonObject
            require(value.keys == setOf("keyid", "sig")) { "Root signature fields are invalid" }
            value["keyid"]!!.jsonPrimitive.content
        }
        val allowed = current.rootKeyIds.toSet() + candidate.rootKeyIds.toSet()
        require(signatures.size == signatures.toSet().size && signatures.all(allowed::contains)) {
            "Root rotation signatures must come only from distinct current or replacement root keys"
        }
        verifyThreshold(envelope, current.keys, current.rootKeyIds.toSet(), 2)
        verifyThreshold(envelope, candidate.keys, candidate.rootKeyIds.toSet(), 2)
    }

    private data class ValidatedRoot(
        val version: Long,
        val signed: JsonObject,
        val keys: Map<String, ByteArray>,
        val rootKeyIds: List<String>,
        val targetsKeyIds: List<String>,
        val snapshotKeyIds: List<String>,
        val timestampKeyIds: List<String>,
    )

    private companion object {
        val ROOT_LIFETIME: Duration = Duration.ofDays(365)
        val ROOT_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id", "consistent_snapshot", "keys", "roles")
    }
}

/** Offline ceremony: root is 2-of-3 and top-level targets is 2-of-2. */
class TufBootstrapper(
    private val storage: RegistryObjectStorage,
    private val rootPublicKeys: List<ByteArray>,
    private val rootSigners: List<TufSigner>,
    private val targetsPublicKeys: List<ByteArray>,
    private val targetsSigners: List<TufSigner>,
    private val online: TufOnlineKeys,
    private val environment: String,
    private val repositoryId: String,
    private val clock: Clock,
) {
    init {
        require(rootPublicKeys.size == 3 && rootPublicKeys.all { it.size == 32 }) { "Root ceremony requires exactly three raw Ed25519 public keys" }
        require(targetsPublicKeys.size == 2 && targetsPublicKeys.all { it.size == 32 }) { "Targets ceremony requires exactly two raw Ed25519 public keys" }
        require(rootPublicKeys.map(::tufKeyId).toSet().size == 3) { "Root keys must be distinct" }
        require(targetsPublicKeys.map(::tufKeyId).toSet().size == 2) { "Targets keys must be distinct" }
        require(rootSigners.map { tufKeyId(it.publicKey) }.toSet().size >= 2 && rootSigners.all { signer -> rootPublicKeys.any(signer.publicKey::contentEquals) }) {
            "Root ceremony requires at least two matching private signers"
        }
        require(targetsSigners.map { tufKeyId(it.publicKey) }.toSet().size == 2 && targetsSigners.all { signer -> targetsPublicKeys.any(signer.publicKey::contentEquals) }) {
            "Targets ceremony requires both matching private signers"
        }
        val everyKey = rootPublicKeys + targetsPublicKeys + online.asMap().values
        require(everyKey.map(::tufKeyId).toSet().size == everyKey.size) { "Every TUF role key must be distinct" }
    }

    fun bootstrap(): TufBootstrapResult {
        val rootIds = rootPublicKeys.map(::tufKeyId)
        val targetIds = targetsPublicKeys.map(::tufKeyId)
        val rootKeys = (rootPublicKeys + targetsPublicKeys + listOf(online.snapshot, online.timestamp))
            .associate { tufKeyId(it) to tufKeyObject(it) }
        val rootSigned = common("root", 1, Duration.ofDays(365)).toMutableMap().apply {
            put("consistent_snapshot", JsonPrimitive(true))
            put("keys", JsonObject(rootKeys))
            put("roles", buildJsonObject {
                put("root", role(rootIds, 2))
                put("targets", role(targetIds, 2))
                put("snapshot", role(listOf(tufKeyId(online.snapshot)), 1))
                put("timestamp", role(listOf(tufKeyId(online.timestamp)), 1))
            })
        }.let(::JsonObject)
        val root = envelope(rootSigned, rootSigners.distinctBy { tufKeyId(it.publicKey) })

        val targetsSigned = common("targets", 1, Duration.ofDays(30)).toMutableMap().apply {
            put("targets", JsonObject(emptyMap()))
            put("delegations", buildJsonObject {
                put("keys", JsonObject(mapOf(
                    tufKeyId(online.releases) to tufKeyObject(online.releases),
                    tufKeyId(online.security) to tufKeyObject(online.security),
                )))
                put("roles", buildJsonArray {
                    add(delegatedRole(TufRole.SECURITY, tufKeyId(online.security)))
                    add(delegatedRole(TufRole.RELEASES, tufKeyId(online.releases)))
                })
            })
        }.let(::JsonObject)
        val targets = envelope(targetsSigned, targetsSigners.distinctBy { tufKeyId(it.publicKey) })
        persistInitialBootstrap(storage, root, targets)
        return TufBootstrapResult(root, targets, rootIds, targetIds)
    }

    private fun common(type: String, version: Long, lifetime: Duration): Map<String, JsonElement> = commonMetadata(type, version, lifetime, environment, repositoryId, clock)
}

/** Imports envelopes produced by a separate offline ceremony; no offline private key crosses this boundary. */
class TufBootstrapImporter(
    private val storage: RegistryObjectStorage,
    private val online: TufOnlineKeys,
    private val environment: String,
    private val repositoryId: String,
    private val registryOrigin: String,
    private val clock: Clock,
) {
    fun import(rootBytes: ByteArray, targetsBytes: ByteArray): TufBootstrapResult {
        require(registryOrigin == "https://seen.dev.yousef.codes/packages") { "Bootstrap envelope is only valid for the official development origin" }
        val rootEnvelope = parseCanonicalEnvelope(rootBytes)
        val rootSigned = rootEnvelope["signed"]!!.jsonObject
        validateCommon(rootSigned, "root", Duration.ofDays(365))
        require(rootSigned["consistent_snapshot"]?.jsonPrimitive?.content == "true") { "Root must enable consistent snapshots" }
        val rootKeys = parseKeys(rootSigned["keys"]!!.jsonObject)
        val roles = rootSigned["roles"]!!.jsonObject
        val rootRole = parseRole(roles, "root", 3, 2)
        val targetsRole = parseRole(roles, "targets", 2, 2)
        val snapshotRole = parseRole(roles, "snapshot", 1, 1)
        val timestampRole = parseRole(roles, "timestamp", 1, 1)
        require(rootKeys.keys == (rootRole + targetsRole + snapshotRole + timestampRole).toSet()) { "Root contains unexpected or missing role keys" }
        require(snapshotRole.single() == tufKeyId(online.snapshot)) { "Root snapshot key does not match configured KMS key" }
        require(timestampRole.single() == tufKeyId(online.timestamp)) { "Root timestamp key does not match configured KMS key" }
        verifyThreshold(rootEnvelope, rootKeys, rootRole.toSet(), 2)

        val targetsEnvelope = parseCanonicalEnvelope(targetsBytes)
        val targetsSigned = targetsEnvelope["signed"]!!.jsonObject
        validateCommon(targetsSigned, "targets", Duration.ofDays(30))
        require(targetsSigned["targets"]?.jsonObject?.isEmpty() == true) { "Offline top-level targets must not contain package targets" }
        verifyThreshold(targetsEnvelope, rootKeys, targetsRole.toSet(), 2)
        val delegations = targetsSigned["delegations"]!!.jsonObject
        val delegatedKeys = parseKeys(delegations["keys"]!!.jsonObject)
        val expectedDelegated = mapOf(
            tufKeyId(online.releases) to online.releases,
            tufKeyId(online.security) to online.security,
        )
        require(delegatedKeys.keys == expectedDelegated.keys && delegatedKeys.all { (id, key) -> key.contentEquals(expectedDelegated[id]) }) {
            "Delegated keys do not match configured releases/security KMS keys"
        }
        val delegatedRoleList = delegations["roles"]!!.jsonArray
        require(delegatedRoleList.map { it.jsonObject["name"]!!.jsonPrimitive.content } == listOf(TufRole.SECURITY, TufRole.RELEASES)) {
            "Targets delegation order must be security then releases"
        }
        val delegatedRoles = delegatedRoleList.associate { role -> role.jsonObject["name"]!!.jsonPrimitive.content to role.jsonObject }
        require(delegatedRoles.keys == setOf(TufRole.RELEASES, TufRole.SECURITY)) { "Targets delegations must contain only releases and security" }
        mapOf(TufRole.RELEASES to online.releases, TufRole.SECURITY to online.security).forEach { (name, publicKey) ->
            val role = requireNotNull(delegatedRoles[name])
            require(role["threshold"]?.jsonPrimitive?.content == "1" && role["terminating"]?.jsonPrimitive?.content == "false") { "$name delegation policy is invalid" }
            require(role["keyids"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf(tufKeyId(publicKey))) { "$name delegation key is invalid" }
            require(role["paths"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf("packages/*/*/*/*/*")) { "$name delegation path is invalid" }
        }

        persistInitialBootstrap(storage, rootBytes, targetsBytes)
        return TufBootstrapResult(rootBytes, targetsBytes, rootRole, targetsRole)
    }

    private fun validateCommon(signed: JsonObject, type: String, maximumLifetime: Duration) {
        require(signed["_type"]?.jsonPrimitive?.content == type) { "Metadata role type is invalid" }
        require(signed["spec_version"]?.jsonPrimitive?.content == "1.0") { "TUF spec version is invalid" }
        require(signed["version"]?.jsonPrimitive?.content == "1") { "Bootstrap metadata version must be one" }
        require(signed["environment"]?.jsonPrimitive?.content == environment) { "Metadata environment is invalid" }
        require(signed["repository_id"]?.jsonPrimitive?.content == repositoryId) { "Metadata repository is invalid" }
        val remaining = Duration.between(clock.instant(), Instant.parse(signed["expires"]!!.jsonPrimitive.content))
        require(!remaining.isNegative && !remaining.isZero && remaining <= maximumLifetime) { "$type expiry exceeds development policy" }
    }
}

/**
 * Persists immutable bootstrap envelopes first, then writes the root.json
 * publication pointer. Every write is conditional so retries can complete a
 * byte-identical partial ceremony without overwriting concurrent metadata.
 */
private fun persistInitialBootstrap(storage: RegistryObjectStorage, root: ByteArray, targets: ByteArray) {
    persistBootstrapImmutable(storage, "1.root.json", root)
    persistBootstrapImmutable(storage, "1.targets.json", targets)
    persistBootstrapPointer(storage, root)
}

private fun persistBootstrapImmutable(storage: RegistryObjectStorage, name: String, bytes: ByteArray) {
    if (storage.putMetadataIfAbsent(name, bytes)) return
    require(storage.getMetadata(name)?.contentEquals(bytes) == true) {
        "Existing $name differs; refusing bootstrap overwrite"
    }
}

private fun persistBootstrapPointer(storage: RegistryObjectStorage, root: ByteArray) {
    if (storage.replaceMetadataIfUnchanged("root.json", expected = null, bytes = root)) return
    require(storage.getMetadata("root.json")?.contentEquals(root) == true) {
        "Existing root.json differs; refusing bootstrap overwrite"
    }
}

class TufPublisher(
    private val repository: RegistryRepository,
    private val storage: RegistryObjectStorage,
    private val online: TufOnlineSigners,
    private val environment: String,
    private val repositoryId: String,
    private val registryOrigin: String,
    private val clock: Clock,
) {
    private val publicationHolder = "publisher-${UUID.randomUUID()}"
    private val targetsRenewalPolicy = TufTargetsRenewalPolicy(online.publicKeys(), environment, repositoryId, clock)
    private val rootRotationPolicy = TufRootRotationPolicy(environment, repositoryId, clock)

    fun requireBootstrap(): ByteArray = storage.getMetadata("root.json")
        ?: error("Offline TUF bootstrap is missing the published root.json pointer")

    /**
     * Reads metadata through the public publication boundary. A sequential root
     * written before a failed pointer CAS remains staged and undiscoverable until
     * the exact candidate is retried and root.json advances.
     */
    fun publicMetadata(filename: String): ByteArray? {
        val requestedRootVersion = VERSIONED_ROOT_FILENAME.matchEntire(filename)
            ?.groupValues
            ?.get(1)
            ?.toLong()
        if (requestedRootVersion != null) {
            val currentRootVersion = signedVersion(requireBootstrap())
            if (requestedRootVersion > currentRootVersion) return null
        }
        return storage.getMetadata(filename)
    }

    fun verifyStoredRootChain(): Long {
        val current = requireBootstrap()
        val currentVersion = signedVersion(current)
        require(currentVersion in 1..MAX_ROOT_CHAIN_LENGTH) { "Current root version is outside the supported chain bound" }
        val chain = (1L..currentVersion).map { version ->
            storage.getMetadata("$version.root.json")
                ?: error("Trusted root chain is missing $version.root.json")
        }
        require(chain.last().contentEquals(current)) { "root.json does not match its immutable current version" }
        return rootRotationPolicy.validateChain(chain)
    }

    @Synchronized
    fun ensureInitialTransaction(): Long = ensureFreshTransaction()

    /** Verifies the complete published chain without exercising any signing authority. */
    @Synchronized
    fun verifyFreshTransaction(): Long {
        requireBootstrap()
        val current = readOnlineState()
            ?: unavailableMetadata("Signed registry metadata is unavailable")
        if (!current.minimumExpiry.isAfter(clock.instant())) {
            unavailableMetadata("Signed registry metadata is expired")
        }
        return current.version
    }

    /** Always advances releases + snapshot + timestamp while retaining committed security byte-for-byte. */
    @Synchronized
    fun forceRefreshReleases(): Long = forceRefreshRole(TufRole.RELEASES, RefreshPolicy.STRICT)

    /** Always advances security + snapshot + timestamp while retaining committed releases byte-for-byte. */
    @Synchronized
    fun forceRefreshSecurity(): Long = forceRefreshRole(TufRole.SECURITY, RefreshPolicy.STRICT)

    /**
     * Recovers a transaction in which both delegated roles have already expired by
     * advancing releases while retaining the expired security envelope exactly.
     * The resulting transaction intentionally remains unready until a normal
     * security refresh completes it.
     */
    @Synchronized
    fun recoverExpiredReleases(): Long = forceRefreshRole(TufRole.RELEASES, RefreshPolicy.DUAL_EXPIRY_RECOVERY)

    /**
     * Symmetric recovery entry point. The resulting transaction intentionally
     * remains unready until a normal releases refresh completes it.
     */
    @Synchronized
    fun recoverExpiredSecurity(): Long = forceRefreshRole(TufRole.SECURITY, RefreshPolicy.DUAL_EXPIRY_RECOVERY)

    private fun forceRefreshRole(role: String, policy: RefreshPolicy): Long {
        require(role in setOf(TufRole.RELEASES, TufRole.SECURITY))
        val changingSigner = online.asMap().getValue(role)
        val retainedRole = if (role == TufRole.RELEASES) TufRole.SECURITY else TufRole.RELEASES
        require(changingSigner !is PublicKeyOnlyTufSigner) { "$role refresh requires its signing authority" }
        require(online.asMap().getValue(retainedRole) is PublicKeyOnlyTufSigner) {
            "$role refresh must not hold the $retainedRole signing authority"
        }
        requireBootstrap()
        val now = clock.instant()
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed $role metadata is being refreshed", true, 5)
        }
        return try {
            val allowedExpiredRetainedRole = when (policy) {
                RefreshPolicy.STRICT -> null
                RefreshPolicy.DUAL_EXPIRY_RECOVERY -> {
                    val current = readOnlineState()
                        ?: unavailableMetadata("Signed registry metadata is unavailable")
                    val recoveryNow = clock.instant()
                    require(!current.releasesExpiry.isAfter(recoveryNow) && !current.securityExpiry.isAfter(recoveryNow)) {
                        "Expired delegated metadata recovery requires both releases and security metadata to be expired"
                    }
                    retainedRole
                }
            }
            publishUnlocked(
                currentPublicReleases(),
                expectedTimestamp = expectedTimestamp,
                allowedExpiredRetainedRole = allowedExpiredRetainedRole,
            )
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    /**
     * Keeps every online role fresh without changing package lifecycle state. The
     * repository lease serializes refreshes across service instances; the local
     * monitor only avoids duplicate work inside one process.
     */
    @Synchronized
    fun ensureFreshTransaction(): Long {
        requireBootstrap()
        val now = clock.instant()
        // Capture the public pointer before attempting the lease. The final
        // generation-CAS fences this publisher even if the lease expires while
        // KMS or object storage is stalled.
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        val current = runCatching(::readOnlineState).getOrNull()
        if (current != null && current.minimumExpiry.isAfter(now.plus(REFRESH_WINDOW))) return current.version
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            if (current != null && current.minimumExpiry.isAfter(now)) return current.version
            throw RegistryException(503, "temporarily_unavailable", "Signed registry metadata is being refreshed", true, 5)
        }
        return try {
            val afterLease = runCatching(::readOnlineState).getOrNull()
            if (afterLease != null && afterLease.minimumExpiry.isAfter(now.plus(REFRESH_WINDOW))) {
                afterLease.version
            } else {
                publishUnlocked(currentPublicReleases(), expectedTimestamp = expectedTimestamp)
            }
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    /** Publishes a complete online transaction; offline root/targets are never rewritten. */
    @Synchronized
    fun publish(publicReleases: List<StoredRelease>): Long {
        requireBootstrap()
        val now = clock.instant()
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed registry metadata is being published", true, 5)
        }
        return try {
            // A promotion candidate is assembled before this lease is acquired.
            // Merge in releases committed by an earlier publisher so concurrent
            // additions cannot disappear from a later transaction.
            publishUnlocked(mergePublicReleases(publicReleases), expectedTimestamp = expectedTimestamp)
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    /**
     * Publishes a higher-priority security target for the exact immutable
     * release target. The releases role remains byte-for-byte bound to the
     * pre-quarantine available/yanked projection beneath this override.
    */
    @Synchronized
    fun publishSecurityQuarantine(release: StoredRelease, incidentId: String): SignedMetadataReference =
        publishSecurityChange(release, quarantine = true, incidentId = incidentId)

    /** Reasserts an existing durable incident after a failed reinstatement commit. */
    @Synchronized
    fun restoreSecurityQuarantine(release: StoredRelease): SignedMetadataReference =
        publishSecurityChange(
            release,
            quarantine = true,
            incidentId = durableSecurityIncidentId(release),
        )

    /** Removes only this release's security override, preserving every other incident. */
    @Synchronized
    fun publishReviewedSecurityReinstatement(release: StoredRelease): SignedMetadataReference =
        publishSecurityChange(release, quarantine = false, incidentId = null)

    /**
     * Imports one sequential offline targets envelope and immediately rotates the
     * online releases/security/snapshot/timestamp transaction to reference it.
     */
    @Synchronized
    fun importTargetsRenewal(candidate: ByteArray): TufTargetsRenewalImportResult {
        requireBootstrap()
        val now = clock.instant()
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed registry metadata is being published", true, 5)
        }
        return try {
            val current = targetsRenewalPolicy.resolveCurrent(
                storage,
                requireOnlineTransaction = true,
                allowExpiredTargets = true,
            )
            val renewed = targetsRenewalPolicy.validateRenewal(current.root, current.targets, candidate)
            val filename = "${renewed.version}.targets.json"
            val existing = storage.getMetadata(filename)
            if (existing == null) {
                if (!storage.putMetadataIfAbsent(filename, candidate)) {
                    require(storage.getMetadata(filename)?.contentEquals(candidate) == true) {
                        "Existing $filename differs; refusing targets overwrite"
                    }
                }
            } else {
                require(existing.contentEquals(candidate)) { "Existing $filename differs; refusing targets overwrite" }
            }
            TufTargetsRenewalImportResult(
                targetsVersion = renewed.version,
                onlineTransactionVersion = publishUnlocked(currentPublicReleases(), candidate, expectedTimestamp),
            )
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    /**
     * Replaces compromised releases/security delegation keys and commits a complete
     * transaction signed by the replacement online authorities before returning.
     */
    @Synchronized
    fun importTargetsRotation(candidate: ByteArray, affectedRole: String): TufTargetsRenewalImportResult {
        require(affectedRole in setOf(TufRole.RELEASES, TufRole.SECURITY)) {
            "Targets rotation affected role must be releases or security"
        }
        require(online.asMap().getValue(affectedRole) !is PublicKeyOnlyTufSigner) {
            "Targets rotation requires signing authority for the affected role"
        }
        requireBootstrap()
        val now = clock.instant()
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed registry metadata is being published", true, 5)
        }
        return try {
            val current = targetsRenewalPolicy.resolveCurrent(
                storage,
                requireOnlineTransaction = true,
                allowExpiredTargets = true,
                requireConfiguredDelegations = false,
            )
            val rotated = targetsRenewalPolicy.validateRotation(current.root, current.targets, candidate)
            require(rotated.changedDelegatedRoles == setOf(affectedRole)) {
                "Targets rotation must replace exactly the declared affected role"
            }
            persistImmutableMetadata("${rotated.version}.targets.json", candidate, "targets")
            TufTargetsRenewalImportResult(
                targetsVersion = rotated.version,
                onlineTransactionVersion = publishUnlocked(
                    currentPublicReleases(),
                    candidate,
                    expectedTimestamp,
                    recoverCompromisedDelegations = affectedRole == TufRole.RELEASES,
                ),
            )
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    /** Advances the mutable root pointer only after persisting its immutable sequential version. */
    @Synchronized
    fun importRootRotation(candidate: ByteArray): TufRootRotationImportResult {
        val now = clock.instant()
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed registry metadata is being published", true, 5)
        }
        return try {
            val current = requireBootstrap()
            val rotated = rootRotationPolicy.validateRotation(current, candidate)
            persistImmutableMetadata("${rotated.version}.root.json", candidate, "root")
            if (!storage.replaceMetadataIfUnchanged("root.json", current, candidate)) {
                throw RegistryException(
                    503,
                    "temporarily_unavailable",
                    "Trusted root changed during rotation",
                    retryable = true,
                    retryAfterSeconds = 5,
                )
            }
            TufRootRotationImportResult(rotated.version)
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    private fun persistImmutableMetadata(filename: String, bytes: ByteArray, role: String) {
        val existing = storage.getMetadata(filename)
        if (existing == null) {
            if (!storage.putMetadataIfAbsent(filename, bytes)) {
                require(storage.getMetadata(filename)?.contentEquals(bytes) == true) {
                    "Existing $filename differs; refusing $role overwrite"
                }
            }
        } else {
            require(existing.contentEquals(bytes)) { "Existing $filename differs; refusing $role overwrite" }
        }
    }

    private fun publishUnlocked(
        publicReleases: List<StoredRelease>,
        targetsOverride: ByteArray? = null,
        expectedTimestamp: ByteArray?,
        securityTargetsOverride: JsonObject? = null,
        recoverCompromisedDelegations: Boolean = false,
        allowedExpiredRetainedRole: String? = null,
    ): Long {
        requireBootstrap()
        require(allowedExpiredRetainedRole == null || allowedExpiredRetainedRole in setOf(TufRole.RELEASES, TufRole.SECURITY))
        if (allowedExpiredRetainedRole != null) {
            require(online.asMap().getValue(allowedExpiredRetainedRole) is PublicKeyOnlyTufSigner) {
                "Only a public-key-only retained role may remain expired during recovery"
            }
        }
        val currentTargets = if (targetsOverride == null) {
            targetsRenewalPolicy.resolveCurrent(storage, requireOnlineTransaction = false)
        } else {
            TrustedTopLevelTargets(requireBootstrap(), targetsOverride, signedVersion(targetsOverride))
        }
        val targets = currentTargets.targets
        val targetsVersion = currentTargets.version
        val version = repository.nextMetadataVersion()
        val (releasesVersion, releases, releaseTargets) = if (online.releases is PublicKeyOnlyTufSigner) {
            require(!recoverCompromisedDelegations) {
                "A public-key-only releases authority cannot recover its own compromised delegation"
            }
            retainedReleasesMetadata(
                if (allowedExpiredRetainedRole == TufRole.RELEASES) {
                    RetainedRoleExpiryPolicy.REQUIRE_ALREADY_EXPIRED
                } else {
                    RetainedRoleExpiryPolicy.REQUIRE_RETAIN_WINDOW
                },
            )
        } else {
            val targetsForRelease = releaseTargets(publicReleases, recoverCompromisedDelegations)
            val releasesSigned = commonMetadata("targets", version, Duration.ofDays(7), environment, repositoryId, clock).toMutableMap().apply {
                put("targets", targetsForRelease)
            }.let(::JsonObject)
            Triple(version, envelope(releasesSigned, listOf(online.releases)), targetsForRelease)
        }
        val (securityVersion, security) = if (online.security is PublicKeyOnlyTufSigner) {
            require(securityTargetsOverride == null) { "A public-key-only authority cannot change security metadata" }
            retainedSecurityMetadata(
                releaseTargets,
                if (allowedExpiredRetainedRole == TufRole.SECURITY) {
                    RetainedRoleExpiryPolicy.REQUIRE_ALREADY_EXPIRED
                } else {
                    RetainedRoleExpiryPolicy.REQUIRE_RETAIN_WINDOW
                },
            )
        } else {
            val securitySigned = commonMetadata("targets", version, Duration.ofHours(6), environment, repositoryId, clock).toMutableMap().apply {
                put("targets", securityTargetsOverride ?: securityTargetsForState(releaseTargets))
            }.let(::JsonObject)
            version to envelope(securitySigned, listOf(online.security))
        }
        // Stage every changed delegated envelope before asking the snapshot
        // authority to bind it. Versioned metadata is immutable and harmless
        // until the timestamp authority commits the public pointer.
        if (releasesVersion == version) {
            persistImmutableMetadata("$version.releases.json", releases, TufRole.RELEASES)
        }
        if (securityVersion == version) {
            persistImmutableMetadata("$version.security.json", security, TufRole.SECURITY)
        }

        val snapshotSigned = commonMetadata("snapshot", version, Duration.ofDays(1), environment, repositoryId, clock).toMutableMap().apply {
            put("meta", buildJsonObject {
                put("targets.json", fileMeta(targetsVersion, targets))
                put("releases.json", fileMeta(releasesVersion, releases))
                put("security.json", fileMeta(securityVersion, security))
            })
        }.let(::JsonObject)
        val snapshot = envelope(snapshotSigned, listOf(online.snapshot))
        persistImmutableMetadata("$version.snapshot.json", snapshot, TufRole.SNAPSHOT)

        val timestampSigned = commonMetadata("timestamp", version, Duration.ofHours(6), environment, repositoryId, clock).toMutableMap().apply {
            put("meta", buildJsonObject { put("snapshot.json", fileMeta(version, snapshot)) })
        }.let(::JsonObject)
        val timestamp = try {
            envelope(timestampSigned, listOf(online.timestamp))
        } catch (failure: Exception) {
            if (!online.timestamp.commitsTimestampPointer) throw failure
            recoverCommittedTimestamp(
                timestampSigned,
                online.timestamp.publicKey,
                online.timestamp.timestampCommitRecoveryTimeout,
            ) ?: throw failure
        }

        val committedTimestamp = if (online.timestamp.commitsTimestampPointer) {
            // The remote timestamp authority returns a signature only after its
            // own generation-CAS. Coordinators intentionally have no mutable
            // metadata write path in this mode.
            storage.getMetadata("timestamp.json")?.takeIf(timestamp::contentEquals)
        } else if (storage.replaceMetadataIfUnchanged("timestamp.json", expectedTimestamp, timestamp)) {
            timestamp
        } else {
            null
        }
        if (committedTimestamp == null) {
            throw RegistryException(
                503,
                "temporarily_unavailable",
                "Signed registry metadata changed during publication",
                retryable = true,
                retryAfterSeconds = 5,
            )
        }
        val committed = readOnlineState(validateRepositoryState = false)
        check(
            committed != null &&
                committed.version == version &&
                committed.isValidAfterPublication(clock.instant(), allowedExpiredRetainedRole),
        ) {
            "Committed TUF transaction failed complete-chain verification"
        }
        return version
    }

    /**
     * A timestamp signer may commit successfully and lose the HTTP response.
     * Recovery accepts only the exact candidate signed object with one valid
     * signature from the already pinned timestamp key.
     */
    private fun recoverCommittedTimestamp(
        candidateSigned: JsonObject,
        publicKey: ByteArray,
        timeout: Duration,
    ): ByteArray? {
        require(!timeout.isNegative && timeout <= PUBLICATION_LEASE)
        val deadlineNanos = System.nanoTime() + timeout.toNanos()
        while (true) {
            exactCommittedTimestamp(candidateSigned, publicKey)?.let { return it }
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) return null
            try {
                val sleepMillis = minOf(
                    TIMESTAMP_RECOVERY_POLL.toMillis(),
                    Duration.ofNanos(remainingNanos).toMillis().coerceAtLeast(1L),
                )
                Thread.sleep(sleepMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
    }

    private fun exactCommittedTimestamp(candidateSigned: JsonObject, publicKey: ByteArray): ByteArray? {
        val committed = runCatching { storage.getMetadata("timestamp.json") }.getOrNull() ?: return null
        val envelope = runCatching { parseCanonicalEnvelope(committed) }.getOrNull() ?: return null
        if (envelope["signed"] != candidateSigned) return null
        val keyId = tufKeyId(publicKey)
        val signatures = (envelope["signatures"] as? JsonArray)?.map { element ->
            (element as? JsonObject)?.takeIf { it.keys == setOf("keyid", "sig") }
                ?: return null
        } ?: return null
        if (signatures.size != 1 || signatures.single()["keyid"]?.jsonPrimitive?.content != keyId) return null
        if (runCatching { verifyThreshold(envelope, mapOf(keyId to publicKey), setOf(keyId), 1) }.isFailure) return null
        return committed
    }

    private fun publishSecurityChange(
        release: StoredRelease,
        quarantine: Boolean,
        incidentId: String?,
    ): SignedMetadataReference {
        requireBootstrap()
        if (online.security is PublicKeyOnlyTufSigner) {
            throw RegistryException(
                503,
                "temporarily_unavailable",
                "Security signing authority is unavailable",
                retryable = true,
                retryAfterSeconds = 30,
            )
        }
        requireSecuritySubject(release)
        val now = clock.instant()
        val expectedTimestamp = storage.getMetadata("timestamp.json")
        if (!repository.tryAcquireMetadataPublication(publicationHolder, now, now.plus(PUBLICATION_LEASE))) {
            throw RegistryException(503, "temporarily_unavailable", "Signed security metadata is being published", true, 5)
        }
        return try {
            val path = targetPath(release)
            val quarantineIncidentId = incidentId?.also(::requireIncidentId)
            if (quarantine) requireNotNull(quarantineIncidentId) {
                "Security quarantine requires an incident ID"
            }
            val releasesTargets = currentDelegatedTargets(TufRole.RELEASES)
                ?: unavailableMetadata("Signed releases metadata is unavailable")
            val incidentOverrides = if (quarantine) mapOf(path to requireNotNull(quarantineIncidentId)) else emptyMap()
            val securityTargets = securityTargetsForState(releasesTargets, incidentOverrides).toMutableMap()
            if (quarantine) {
                val releasesTarget = releasesTargets[path]
                    ?: unavailableMetadata("The release is absent from signed releases metadata")
                validateExactTarget(release, releasesTarget, setOf("available", "yanked"))
                securityTargets[path] = securityOverride(releasesTarget, requireNotNull(quarantineIncidentId))
            } else {
                securityTargets.remove(path)
                    ?: unavailableMetadata("The signed security override is unavailable")
            }
            val version = publishUnlocked(
                publicReleases = currentPublicReleases(),
                expectedTimestamp = expectedTimestamp,
                securityTargetsOverride = JsonObject(securityTargets),
            )
            signedSecurityReference(version)
        } finally {
            repository.releaseMetadataPublication(publicationHolder)
        }
    }

    private fun currentPublicReleases(): List<StoredRelease> = repository.listReleases().filter {
        resolverEligible(it, setOf("available", "yanked"))
    }

    private fun retainedReleasesMetadata(
        expiryPolicy: RetainedRoleExpiryPolicy,
    ): Triple<Long, ByteArray, JsonObject> {
        val timestamp = storage.getMetadata("timestamp.json")
            ?: throw RegistryException(503, "temporarily_unavailable", "Signed releases metadata is unavailable", true, 30)
        val timestampSigned = signedObject(timestamp, "timestamp", signingRole = TufRole.TIMESTAMP)
        val (_, snapshot) = referencedMetadata(
            timestampSigned["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject,
            "snapshot",
        )
        val snapshotSigned = signedObject(snapshot, "snapshot", signingRole = TufRole.SNAPSHOT)
        val (releasesVersion, releases) = referencedMetadata(
            snapshotSigned["meta"]!!.jsonObject["releases.json"]!!.jsonObject,
            TufRole.RELEASES,
        )
        val releasesSigned = signedObject(releases, "targets", releasesVersion, TufRole.RELEASES)
        val releaseTargets = releasesSigned["targets"]!!.jsonObject
        validateCurrentReleaseTargets(releaseTargets)
        val expires = Instant.parse(releasesSigned["expires"]!!.jsonPrimitive.content)
        when (expiryPolicy) {
            RetainedRoleExpiryPolicy.REQUIRE_RETAIN_WINDOW -> {
                if (!expires.isAfter(clock.instant().plus(RELEASES_RETAIN_WINDOW))) {
                    throw RegistryException(
                        503,
                        "temporarily_unavailable",
                        "Releases authority must refresh signed metadata before security publication",
                        retryable = true,
                        retryAfterSeconds = 300,
                    )
                }
            }
            RetainedRoleExpiryPolicy.REQUIRE_ALREADY_EXPIRED -> require(!expires.isAfter(clock.instant())) {
                "Recovery may retain releases metadata only after it has expired"
            }
        }
        return Triple(releasesVersion, releases, releaseTargets)
    }

    private fun retainedSecurityMetadata(
        releaseTargets: JsonObject,
        expiryPolicy: RetainedRoleExpiryPolicy,
    ): Pair<Long, ByteArray> {
        val timestamp = storage.getMetadata("timestamp.json")
            ?: throw RegistryException(503, "temporarily_unavailable", "Signed security metadata is unavailable", true, 30)
        val timestampSigned = signedObject(timestamp, "timestamp", signingRole = TufRole.TIMESTAMP)
        val (_, snapshot) = referencedMetadata(
            timestampSigned["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject,
            "snapshot",
        )
        val snapshotSigned = signedObject(snapshot, "snapshot", signingRole = TufRole.SNAPSHOT)
        val (securityVersion, security) = referencedMetadata(
            snapshotSigned["meta"]!!.jsonObject["security.json"]!!.jsonObject,
            "security",
        )
        val securitySigned = signedObject(security, "targets", securityVersion, TufRole.SECURITY)
        validateCurrentSecurityTargets(
            securitySigned["targets"]!!.jsonObject,
            releaseTargets,
        )
        val expires = Instant.parse(securitySigned["expires"]!!.jsonPrimitive.content)
        when (expiryPolicy) {
            RetainedRoleExpiryPolicy.REQUIRE_RETAIN_WINDOW -> {
                if (!expires.isAfter(clock.instant().plus(SECURITY_RETAIN_WINDOW))) {
                    throw RegistryException(
                        503,
                        "temporarily_unavailable",
                        "Security authority must refresh signed metadata before promotion",
                        retryable = true,
                        retryAfterSeconds = 300,
                    )
                }
            }
            RetainedRoleExpiryPolicy.REQUIRE_ALREADY_EXPIRED -> require(!expires.isAfter(clock.instant())) {
                "Recovery may retain security metadata only after it has expired"
            }
        }
        return securityVersion to security
    }

    private fun mergePublicReleases(additions: List<StoredRelease>): List<StoredRelease> =
        (currentPublicReleases() + additions.filter(::isExactActivationDraft))
            .associateBy { "${it.record.`package`}@${it.record.version}" }
            .values
            .sortedWith(compareBy<StoredRelease> { it.record.`package` }.thenBy { it.record.version })

    private fun isExactActivationDraft(candidate: StoredRelease): Boolean {
        val current = repository.getRelease(candidate.record.`package`, candidate.record.version) ?: return false
        val before = current.record
        val after = candidate.record
        return current.revision + 1 == candidate.revision &&
            current.ownerPrincipal == candidate.ownerPrincipal &&
            current.uploadId == candidate.uploadId &&
            current.uploadExpiresAt == candidate.uploadExpiresAt &&
            current.manifest == candidate.manifest &&
            current.source == candidate.source &&
            current.dependencies == candidate.dependencies &&
            current.review == candidate.review &&
            current.review.promotionAttemptId != null &&
            current.review.promotionInputSha256 != null &&
            before.state.lifecycle == "ready" &&
            before.state.visibility == "public" &&
            before.state.availability == "unavailable" &&
            before.state.retention == "retained" &&
            after.state.lifecycle == "active" &&
            after.state.visibility == before.state.visibility &&
            after.state.availability == "available" &&
            after.state.retention == before.state.retention &&
            after.`package` == before.`package` &&
            after.version == before.version &&
            after.archive == before.archive &&
            after.manifestSha256 == before.manifestSha256 &&
            after.capabilities == before.capabilities &&
            after.sourceProofId == before.sourceProofId &&
            after.verification == before.verification &&
            after.timestamps.copy(activatedAt = before.timestamps.activatedAt, updatedAt = before.timestamps.updatedAt) ==
                before.timestamps &&
            after.resolverMetadataVersion != null &&
            after.resolverMetadataVersion > 0 &&
            after.links.copy(download = before.links.download) == before.links &&
            resolverEligible(candidate, setOf("available"))
    }

    private fun releaseTargets(
        publicReleases: List<StoredRelease>,
        recoverCompromisedDelegations: Boolean = false,
    ): JsonObject {
        val targets = linkedMapOf<String, JsonElement>()
        val current = if (recoverCompromisedDelegations) null else currentDelegatedTargets(TufRole.RELEASES)
        repository.listReleases()
            .filter { resolverEligible(it, setOf("security-quarantined")) }
            .sortedWith(compareBy<StoredRelease> { it.record.`package` }.thenBy { it.record.version })
            .forEach { quarantined ->
                val path = targetPath(quarantined)
                val retained = if (recoverCompromisedDelegations) {
                    targetValue(quarantined, durablePriorAvailability(quarantined))
                } else {
                    current?.get(path)
                        ?: unavailableMetadata("A quarantined release is absent from signed releases metadata")
                }
                validateExactTarget(quarantined, retained, setOf("available", "yanked"))
                targets[path] = retained
            }
        publicReleases
            .filter { resolverEligible(it, setOf("available", "yanked")) }
            .sortedWith(compareBy<StoredRelease> { it.record.`package` }.thenBy { it.record.version })
            .forEach { release ->
                val (path, target) = targetFor(release)
                targets[path] = target
            }
        return JsonObject(targets)
    }

    private fun durablePriorAvailability(release: StoredRelease): String {
        val value = repository.listReviewArtifacts(
            packageIdentity = release.record.`package`,
            version = release.record.version,
            kind = SECURITY_INCIDENT_ARTIFACT,
        ).maxByOrNull(ReviewArtifact::sequence)
            ?.payload
            ?.get("prior_availability")
            ?.jsonPrimitive
            ?.content
        return value?.takeIf { it in setOf("available", "yanked") }
            ?: unavailableMetadata("Durable pre-quarantine availability is unavailable")
    }

    private fun currentDelegatedTargets(role: String): JsonObject? {
        val timestamp = storage.getMetadata("timestamp.json") ?: return null
        val timestampSigned = signedObject(timestamp, "timestamp", signingRole = TufRole.TIMESTAMP)
        val (_, snapshot) = referencedMetadata(
            timestampSigned["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject,
            "snapshot",
        )
        val snapshotSigned = signedObject(snapshot, "snapshot", signingRole = TufRole.SNAPSHOT)
        val (roleVersion, roleBytes) = referencedMetadata(
            snapshotSigned["meta"]!!.jsonObject["$role.json"]!!.jsonObject,
            role,
        )
        return signedObject(roleBytes, "targets", roleVersion, role)["targets"]?.jsonObject
            ?: unavailableMetadata("Signed $role metadata has no targets")
    }

    private fun resolverEligible(stored: StoredRelease, allowedAvailability: Set<String>): Boolean {
        val state = stored.record.state
        return state.visibility == "public" &&
            state.lifecycle == "active" &&
            state.retention == "retained" &&
            state.availability in allowedAvailability &&
            hasExactReviewedEvidence(stored)
    }

    private fun exactReviewedEvidence(stored: StoredRelease): ReviewedTargetEvidence? = runCatching {
        val release = stored.record
        val review = stored.review
        val pkg = repository.getPackage(release.`package`) ?: return@runCatching null
        if (
            review.validatedArchiveSha256 != release.archive.sha256 ||
            release.sourceProofId != review.secondSourceProofId ||
            release.verification.origin != "passed" ||
            release.verification.integrity != "passed" ||
            release.verification.source != "passed" ||
            release.verification.firstScan != "passed" ||
            release.verification.secondScan != "passed"
        ) return@runCatching null

        val firstProof = exactSourceProof(
            stored,
            pkg,
            review.firstSourceProofId ?: return@runCatching null,
            review.firstSourceProofSha256 ?: return@runCatching null,
        ) ?: return@runCatching null
        val firstScan = exactScan(
            stored,
            ReviewPhase.FIRST,
            review.firstScanAttestationId ?: return@runCatching null,
            review.firstScanAttestationSha256 ?: return@runCatching null,
            firstProof,
        ) ?: return@runCatching null
        val secondProof = exactSourceProof(
            stored,
            pkg,
            review.secondSourceProofId ?: return@runCatching null,
            review.secondSourceProofSha256 ?: return@runCatching null,
        ) ?: return@runCatching null
        val secondScan = exactScan(
            stored,
            ReviewPhase.SECOND,
            review.secondScanAttestationId ?: return@runCatching null,
            review.secondScanAttestationSha256 ?: return@runCatching null,
            secondProof,
        ) ?: return@runCatching null

        val chainIsExact = firstProof.previousProofId == null &&
            secondProof.proofId != firstProof.proofId &&
            secondProof.previousProofId == firstProof.proofId &&
            firstScan.previousAttestationId == null &&
            secondScan.previousAttestationId == firstScan.attestationId &&
            firstProof.sequence < firstScan.sequence &&
            firstScan.sequence < secondProof.sequence &&
            secondProof.sequence < secondScan.sequence &&
            release.verification.attestationSequence == secondScan.sequence
        if (!chainIsExact) return@runCatching null
        ReviewedTargetEvidence(
            sourceProof = secondProof,
            sourceProofSha256 = requireNotNull(review.secondSourceProofSha256),
            scanAttestation = secondScan,
            scanAttestationSha256 = requireNotNull(review.secondScanAttestationSha256),
        )
    }.getOrNull()

    private fun hasExactReviewedEvidence(stored: StoredRelease): Boolean = exactReviewedEvidence(stored) != null

    private fun exactSourceProof(
        release: StoredRelease,
        pkg: StoredPackage,
        id: String,
        digest: String,
    ): SourceProofRecord? {
        val artifact = repository.getReviewArtifact(id) ?: return null
        if (
            artifact.kind != SOURCE_PROOF_ARTIFACT ||
            artifact.packageIdentity != release.record.`package` ||
            artifact.version != release.record.version ||
            artifact.archiveSha256 != release.record.archive.sha256
        ) return null
        val proof = runCatching {
            RegistryJson.decodeFromJsonElement<SourceProofRecord>(artifact.payload)
        }.getOrNull() ?: return null
        return proof.takeIf {
            it.proofId == id &&
                it.sha256() == digest &&
                ReviewEvidenceValidator.sourceProofMatches(it, release, pkg)
        }
    }

    private fun exactScan(
        release: StoredRelease,
        phase: ReviewPhase,
        id: String,
        digest: String,
        proof: SourceProofRecord,
    ): ScanAttestationRecord? {
        val artifact = repository.getReviewArtifact(id) ?: return null
        if (
            artifact.kind != SCAN_ATTESTATION_ARTIFACT ||
            artifact.packageIdentity != release.record.`package` ||
            artifact.version != release.record.version ||
            artifact.archiveSha256 != release.record.archive.sha256
        ) return null
        val scan = runCatching {
            RegistryJson.decodeFromJsonElement<ScanAttestationRecord>(artifact.payload)
        }.getOrNull() ?: return null
        return scan.takeIf {
            it.attestationId == id &&
                it.sha256() == digest &&
                ReviewEvidenceValidator.scanMatches(it, release, phase, proof, requirePassed = true)
        }
    }

    private fun requireSecuritySubject(release: StoredRelease) {
        if (!resolverEligible(release, setOf("security-quarantined"))) {
            unavailableMetadata("Security metadata subject is not resolver-eligible")
        }
    }

    private fun validateExactTarget(
        release: StoredRelease,
        target: JsonElement,
        allowedAvailability: Set<String>,
    ) {
        if (!hasExactReviewedEvidence(release)) {
            unavailableMetadata("Signed target review evidence is unavailable")
        }
        val availability = runCatching {
            target.jsonObject["custom"]!!.jsonObject["availability"]!!.jsonPrimitive.content
        }.getOrElse { unavailableMetadata("Signed target availability is invalid") }
        if (availability !in allowedAvailability || target != targetValue(release, availability)) {
            unavailableMetadata("Signed target does not match the immutable reviewed release")
        }
    }

    private fun securityOverride(releasesTarget: JsonElement, incidentId: String): JsonElement {
        requireIncidentId(incidentId)
        val target = releasesTarget.jsonObject
        val custom = target["custom"]!!.jsonObject.toMutableMap().apply {
            remove("yank_reason")
            put("availability", JsonPrimitive("security-quarantined"))
            put("incident_id", JsonPrimitive(incidentId))
            put("security_action", JsonPrimitive("quarantine"))
        }
        return JsonObject(target.toMutableMap().apply { put("custom", JsonObject(custom)) })
    }

    private fun requireIncidentId(incidentId: String) {
        require(SECURITY_INCIDENT_ID.matches(incidentId)) { "Security incident ID is invalid" }
    }

    private fun durableSecurityIncidentId(release: StoredRelease): String {
        val incidentId = repository.listReviewArtifacts(
            packageIdentity = release.record.`package`,
            version = release.record.version,
            kind = SECURITY_INCIDENT_ARTIFACT,
        ).asSequence()
            .filter { it.archiveSha256 == release.record.archive.sha256 }
            .sortedByDescending(ReviewArtifact::sequence)
            .mapNotNull { artifact ->
                runCatching {
                    val action = artifact.payload["action"]!!.jsonObject
                    action["incident_id"]!!.jsonPrimitive.content.takeIf {
                        action["action"]!!.jsonPrimitive.content == "security-quarantined"
                    }
                }.getOrNull()
            }
            .firstOrNull()
            ?.takeIf(SECURITY_INCIDENT_ID::matches)
        return incidentId ?: unavailableMetadata("Durable security incident identity is unavailable")
    }

    private fun durableYankReason(release: StoredRelease): String {
        val action = repository.listReviewArtifacts(
            packageIdentity = release.record.`package`,
            version = release.record.version,
            kind = RELEASE_AVAILABILITY_ACTION_ARTIFACT,
        ).asSequence()
            .filter { it.archiveSha256 == release.record.archive.sha256 }
            .sortedByDescending(ReviewArtifact::sequence)
            .mapNotNull { artifact -> runCatching { artifact.payload["action"]!!.jsonObject }.getOrNull() }
            .firstOrNull {
                it["action"]?.jsonPrimitive?.content == "yanked" &&
                    it["resulting_availability"]?.jsonPrimitive?.content == "yanked"
            }
            ?: unavailableMetadata("Durable yank action is unavailable")
        return action["reason"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: DEFAULT_YANK_REASON
    }

    private fun signedSecurityReference(version: Long): SignedMetadataReference {
        val filename = "$version.security.json"
        val bytes = storage.getMetadata(filename)
            ?: unavailableMetadata("Published security metadata is unavailable")
        val signed = signedObject(bytes, "targets", version, TufRole.SECURITY)
        return SignedMetadataReference(
            environment = environment,
            role = TufRole.SECURITY,
            filename = filename,
            version = version,
            length = bytes.size.toLong(),
            sha256 = sha256(bytes),
            publishedAt = clock.instant().utc(),
            expiresAt = signed["expires"]!!.jsonPrimitive.content,
        )
    }

    private fun unavailableMetadata(message: String): Nothing = throw RegistryException(
        503,
        "temporarily_unavailable",
        message,
        retryable = true,
        retryAfterSeconds = 30,
    )

    private fun readOnlineState(validateRepositoryState: Boolean = true): OnlineState? {
        val timestamp = storage.getMetadata("timestamp.json") ?: return null
        // Validate the complete trust path, including offline root and current
        // top-level targets expiry, before treating online freshness as ready.
        targetsRenewalPolicy.resolveCurrent(storage, requireOnlineTransaction = true)
        val timestampSigned = signedObject(timestamp, "timestamp", signingRole = TufRole.TIMESTAMP)
        val version = timestampSigned["version"]!!.jsonPrimitive.content.toLong()
        val (snapshotVersion, snapshot) = referencedMetadata(
            timestampSigned["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject,
            "snapshot",
        )
        val snapshotSigned = signedObject(snapshot, "snapshot", snapshotVersion, TufRole.SNAPSHOT)
        val meta = snapshotSigned["meta"]!!.jsonObject
        val (targetsVersion, targets) = referencedMetadata(meta["targets.json"]!!.jsonObject, "targets")
        signedObject(targets, "targets", targetsVersion)
        val (releasesVersion, releasesBytes) = referencedMetadata(
            meta["${TufRole.RELEASES}.json"]!!.jsonObject,
            TufRole.RELEASES,
        )
        val releasesSigned = signedObject(releasesBytes, "targets", releasesVersion, TufRole.RELEASES)
        if (validateRepositoryState) {
            validateCurrentReleaseTargets(releasesSigned["targets"]!!.jsonObject)
        }
        val (securityVersion, securityBytes) = referencedMetadata(
            meta["${TufRole.SECURITY}.json"]!!.jsonObject,
            TufRole.SECURITY,
        )
        val securitySigned = signedObject(securityBytes, "targets", securityVersion, TufRole.SECURITY)
        if (validateRepositoryState) {
            validateCurrentSecurityTargets(
                securitySigned["targets"]!!.jsonObject,
                releasesSigned["targets"]!!.jsonObject,
            )
        }
        val releasesExpiry = expiry(releasesSigned)
        val securityExpiry = expiry(securitySigned)
        val snapshotExpiry = expiry(snapshotSigned)
        val timestampExpiry = expiry(timestampSigned)
        return OnlineState(
            version = version,
            releasesExpiry = releasesExpiry,
            securityExpiry = securityExpiry,
            snapshotExpiry = snapshotExpiry,
            timestampExpiry = timestampExpiry,
        )
    }

    private fun signedObject(
        bytes: ByteArray,
        expectedType: String,
        expectedVersion: Long? = null,
        signingRole: String? = null,
    ): JsonObject {
        val envelope = parseCanonicalEnvelope(bytes)
        val signed = envelope["signed"]!!.jsonObject
        require(signed["_type"]?.jsonPrimitive?.content == expectedType) { "Online metadata role type is invalid" }
        require(signed["environment"]?.jsonPrimitive?.content == environment) { "Online metadata environment is invalid" }
        require(signed["repository_id"]?.jsonPrimitive?.content == repositoryId) { "Online metadata repository is invalid" }
        if (expectedVersion != null) require(signed["version"]?.jsonPrimitive?.content?.toLong() == expectedVersion) {
            "Online metadata version is invalid"
        }
        if (signingRole != null) {
            val signer = online.asMap().getValue(signingRole)
            val keyId = tufKeyId(signer.publicKey)
            val signatures = envelope["signatures"]!!.jsonArray.map { signature ->
                val value = signature.jsonObject
                require(value.keys == setOf("keyid", "sig")) { "Online metadata signature fields are invalid" }
                value["keyid"]!!.jsonPrimitive.content
            }
            require(signatures == listOf(keyId)) { "$signingRole metadata must have exactly its configured online signature" }
            verifyThreshold(envelope, mapOf(keyId to signer.publicKey), setOf(keyId), 1)
        }
        return signed
    }

    private fun referencedMetadata(meta: JsonObject, role: String): Pair<Long, ByteArray> {
        val version = meta["version"]!!.jsonPrimitive.content.toLong()
        val bytes = storage.getMetadata("$version.$role.json") ?: error("Metadata references missing $role metadata")
        require(meta["length"]?.jsonPrimitive?.content?.toLong() == bytes.size.toLong()) { "$role metadata length is invalid" }
        require(meta["hashes"]!!.jsonObject["sha256"]?.jsonPrimitive?.content == sha256(bytes)) { "$role metadata digest is invalid" }
        return version to bytes
    }

    private fun expiry(signed: JsonObject): Instant = Instant.parse(signed["expires"]!!.jsonPrimitive.content)

    private fun validateCurrentReleaseTargets(targets: JsonObject) {
        val eligible = repository.listReleases().filter { release ->
            resolverEligible(release, setOf("available", "yanked", "security-quarantined"))
        }.associateBy(::targetPath)
        if (targets.keys != eligible.keys) {
            unavailableMetadata("Signed releases metadata contains stale or missing targets")
        }
        targets.forEach { (path, target) ->
            val release = eligible.getValue(path)
            val allowed = if (release.record.state.availability == "security-quarantined") {
                setOf("available", "yanked")
            } else {
                setOf(release.record.state.availability)
            }
            validateExactTarget(release, target, allowed)
        }
    }

    /**
     * Derives the complete deny role from durable release state. A missing deny
     * target is fail-open; a stale extra target keeps an approved release denied.
     * Both are rejected by reads and replaced by the next online transaction.
     */
    private fun securityTargetsForState(
        releaseTargets: JsonObject,
        incidentIdOverrides: Map<String, String> = emptyMap(),
    ): JsonObject {
        val targets = linkedMapOf<String, JsonElement>()
        repository.listReleases()
            .filter { resolverEligible(it, setOf("security-quarantined")) }
            .sortedWith(compareBy<StoredRelease> { it.record.`package` }.thenBy { it.record.version })
            .forEach { release ->
                val path = targetPath(release)
                val releasesTarget = releaseTargets[path]
                    ?: unavailableMetadata("A quarantined release is absent from signed releases metadata")
                validateExactTarget(release, releasesTarget, setOf("available", "yanked"))
                val incidentId = incidentIdOverrides[path] ?: durableSecurityIncidentId(release)
                targets[path] = securityOverride(releasesTarget, incidentId)
            }
        return JsonObject(targets)
    }

    private fun validateCurrentSecurityTargets(
        securityTargets: JsonObject,
        releaseTargets: JsonObject,
    ) {
        val expected = securityTargetsForState(releaseTargets)
        if (securityTargets != expected) {
            unavailableMetadata("Signed security metadata does not match durable quarantine state")
        }
    }

    private data class OnlineState(
        val version: Long,
        val releasesExpiry: Instant,
        val securityExpiry: Instant,
        val snapshotExpiry: Instant,
        val timestampExpiry: Instant,
    ) {
        val minimumExpiry: Instant
            get() = listOf(releasesExpiry, securityExpiry, snapshotExpiry, timestampExpiry).min()

        fun isValidAfterPublication(now: Instant, allowedExpiredRetainedRole: String?): Boolean {
            val releasesValid = if (allowedExpiredRetainedRole == TufRole.RELEASES) {
                !releasesExpiry.isAfter(now)
            } else {
                releasesExpiry.isAfter(now)
            }
            val securityValid = if (allowedExpiredRetainedRole == TufRole.SECURITY) {
                !securityExpiry.isAfter(now)
            } else {
                securityExpiry.isAfter(now)
            }
            return releasesValid &&
                securityValid &&
                snapshotExpiry.isAfter(now) &&
                timestampExpiry.isAfter(now)
        }
    }

    private enum class RefreshPolicy {
        STRICT,
        DUAL_EXPIRY_RECOVERY,
    }

    private enum class RetainedRoleExpiryPolicy {
        REQUIRE_RETAIN_WINDOW,
        REQUIRE_ALREADY_EXPIRED,
    }

    private fun targetPath(stored: StoredRelease): String {
        val release = stored.record
        val (_, name) = IdentityRules.requireIdentity(release.`package`)
        val filename = "$name-${release.version}.seenpkg.tgz"
        return "packages/${release.`package`}/${release.version}/${release.archive.sha256}/$filename"
    }

    private fun targetFor(stored: StoredRelease): Pair<String, JsonElement> =
        targetPath(stored) to targetValue(stored, stored.record.state.availability)

    private fun targetValue(stored: StoredRelease, availability: String): JsonElement {
        val release = stored.record
        val (owner, name) = IdentityRules.requireIdentity(release.`package`)
        val filename = "$name-${release.version}.seenpkg.tgz"
        val evidence = exactReviewedEvidence(stored)
            ?: unavailableMetadata("Reviewed release evidence is unavailable")
        val activatedAt = release.timestamps.activatedAt
            ?.also { runCatching { Instant.parse(it) }.getOrElse { unavailableMetadata("Release activation time is invalid") } }
            ?: unavailableMetadata("Release activation time is unavailable")
        val publisherPrincipal = stored.ownerPrincipal.takeIf(String::isNotBlank)
            ?: unavailableMetadata("Release publisher identity is unavailable")
        val sourceProof = evidence.sourceProof
        val scan = evidence.scanAttestation
        val blob = buildJsonObject {
            put("sha256", release.archive.sha256)
            put("length", release.archive.compressedBytes)
        }
        val sourceRepository = buildJsonObject {
            put("forge", sourceProof.repository.forge)
            put("repository_id", sourceProof.repository.repositoryId)
            put("canonical_url", sourceProof.repository.canonicalUrl)
        }
        val sourceCommit = buildJsonObject {
            put("algorithm", sourceProof.commit.algorithm)
            put("value", sourceProof.commit.value)
        }
        val review = buildJsonObject {
            put("result", scan.result.status)
            put("policy_version", scan.scan.policyVersion)
            put("source_proof_id", sourceProof.proofId)
            put("source_proof_sha256", evidence.sourceProofSha256)
            put("scan_attestation_id", scan.attestationId)
            put("scan_attestation_sha256", evidence.scanAttestationSha256)
            put("scanner_id", scan.scanner.id)
            put("scanner_version", scan.scanner.version)
            put("attestation_sequence", scan.sequence)
        }
        val registryAttestationSha256 = sha256(canonicalJson(buildJsonObject {
            put("subject", buildJsonObject {
                put("package", release.`package`)
                put("owner", owner)
                put("name", name)
                put("version", release.version)
                put("blob", blob)
                put("visibility", release.state.visibility)
            })
            put("publisher_principal", publisherPrincipal)
            put("registry_service_identity", RELEASE_PROMOTER_IDENTITY)
            put("source_repository", sourceRepository)
            put("source_commit", sourceCommit)
            put("review", review)
            put("activated_at", activatedAt)
        }))
        return buildJsonObject {
            put("length", release.archive.compressedBytes)
            put("hashes", buildJsonObject { put("sha256", release.archive.sha256) })
            put("custom", buildJsonObject {
                put("environment", environment)
                put("registry_origin", registryOrigin)
                put("package", release.`package`)
                put("owner", owner)
                put("name", name)
                put("version", release.version)
                put("archive_sha256", release.archive.sha256)
                put("archive_filename", filename)
                put("blob", blob)
                put("publisher_principal", publisherPrincipal)
                put("registry_service_identity", RELEASE_PROMOTER_IDENTITY)
                put("source_repository", sourceRepository)
                put("source_commit", sourceCommit)
                put("review", review)
                put("visibility", release.state.visibility)
                put("lifecycle", release.state.lifecycle)
                put("retention", release.state.retention)
                put("availability", availability)
                if (availability == "yanked") put("yank_reason", durableYankReason(stored))
                put("activated_at", activatedAt)
                put("source_proof_sha256", evidence.sourceProofSha256)
                put("registry_attestation_sha256", registryAttestationSha256)
                put("provenance_sha256", registryAttestationSha256)
                put("dependencies", RegistryJson.encodeToJsonElement(ListSerializer, stored.dependencies))
                put("capabilities", buildJsonArray { release.capabilities.forEach { add(JsonPrimitive(it)) } })
            })
        }
    }

    private companion object {
        val REFRESH_WINDOW: Duration = Duration.ofHours(2)
        val PUBLICATION_LEASE: Duration = Duration.ofMinutes(2)
        val TIMESTAMP_RECOVERY_POLL: Duration = Duration.ofMillis(100)
        val RELEASES_RETAIN_WINDOW: Duration = Duration.ofMinutes(5)
        val SECURITY_RETAIN_WINDOW: Duration = Duration.ofMinutes(5)
        const val RELEASE_PROMOTER_IDENTITY = "release-promoter"
        const val DEFAULT_YANK_REASON = "Release yanked by publisher"
        val SECURITY_INCIDENT_ID = Regex("^inc_[A-Za-z0-9_-]{8,96}$")
        val VERSIONED_ROOT_FILENAME = Regex("^([1-9][0-9]*)\\.root\\.json$")
        const val MAX_ROOT_CHAIN_LENGTH = 1024L
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(SignedDependency.serializer())
    }

    private data class ReviewedTargetEvidence(
        val sourceProof: SourceProofRecord,
        val sourceProofSha256: String,
        val scanAttestation: ScanAttestationRecord,
        val scanAttestationSha256: String,
    )
}

private fun commonMetadata(type: String, version: Long, lifetime: Duration, environment: String, repositoryId: String, clock: Clock): Map<String, JsonElement> = linkedMapOf(
    "_type" to JsonPrimitive(type),
    "spec_version" to JsonPrimitive("1.0"),
    "version" to JsonPrimitive(version),
    "expires" to JsonPrimitive(clock.instant().plus(lifetime).utc()),
    "environment" to JsonPrimitive(environment),
    "repository_id" to JsonPrimitive(repositoryId),
)

private fun role(keyIds: List<String>, threshold: Int): JsonObject = buildJsonObject {
    put("keyids", buildJsonArray { keyIds.forEach { add(JsonPrimitive(it)) } })
    put("threshold", threshold)
}

private fun delegatedRole(name: String, keyId: String): JsonObject = buildJsonObject {
    put("name", name)
    put("keyids", buildJsonArray { add(JsonPrimitive(keyId)) })
    put("threshold", 1)
    put("terminating", false)
    put("paths", buildJsonArray { add(JsonPrimitive("packages/*/*/*/*/*")) })
}

private fun fileMeta(version: Long, bytes: ByteArray): JsonObject = buildJsonObject {
    put("version", version)
    put("length", bytes.size)
    put("hashes", buildJsonObject { put("sha256", sha256(bytes)) })
}

private fun envelope(signed: JsonObject, signers: List<TufSigner>): ByteArray {
    val canonicalSigned = canonicalJson(signed)
    return canonicalJson(buildJsonObject {
        put("signatures", buildJsonArray {
            signers.sortedBy { tufKeyId(it.publicKey) }.forEach { signer ->
                add(buildJsonObject {
                    put("keyid", tufKeyId(signer.publicKey))
                    put("sig", signer.sign(canonicalSigned).toHex())
                })
            }
        })
        put("signed", signed)
    })
}

private fun tufKeyObject(publicKey: ByteArray): JsonObject = buildJsonObject {
    put("keytype", "ed25519")
    put("scheme", "ed25519")
    put("keyval", buildJsonObject { put("public", publicKey.toHex()) })
}

fun tufKeyId(publicKey: ByteArray): String = sha256(canonicalJson(tufKeyObject(publicKey)))

private fun signedVersion(metadata: ByteArray): Long = RegistryJson.parseToJsonElement(metadata.decodeToString())
    .jsonObject["signed"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()

internal fun parseCanonicalEnvelope(bytes: ByteArray): JsonObject {
    val envelope = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject
    require(envelope.keys == setOf("signatures", "signed")) { "TUF envelope fields are invalid" }
    require(canonicalJson(envelope).contentEquals(bytes)) { "TUF envelope is not canonical JSON" }
    require(envelope["signatures"] is JsonArray && envelope["signed"] is JsonObject) { "TUF envelope shape is invalid" }
    return envelope
}

internal fun parseKeys(keys: JsonObject): Map<String, ByteArray> = keys.mapValues { (keyId, element) ->
    val key = element.jsonObject
    require(key["keytype"]?.jsonPrimitive?.content == "ed25519" && key["scheme"]?.jsonPrimitive?.content == "ed25519") { "TUF key type is invalid" }
    val publicKey = key["keyval"]!!.jsonObject["public"]!!.jsonPrimitive.content.hexToBytes()
    require(publicKey.size == 32 && tufKeyId(publicKey) == keyId) { "TUF key id does not bind its public key" }
    publicKey
}

private fun parseRole(roles: JsonObject, name: String, expectedKeys: Int, expectedThreshold: Int): List<String> {
    val role = roles[name]?.jsonObject ?: error("Missing $name role")
    require(role.keys == setOf("keyids", "threshold")) { "$name role fields are invalid" }
    val ids = role["keyids"]!!.jsonArray.map { it.jsonPrimitive.content }
    require(ids.size == expectedKeys && ids.toSet().size == expectedKeys) { "$name role key count is invalid" }
    require(role["threshold"]!!.jsonPrimitive.content.toInt() == expectedThreshold) { "$name role threshold is invalid" }
    return ids
}

internal fun verifyThreshold(envelope: JsonObject, keys: Map<String, ByteArray>, allowedKeyIds: Set<String>, threshold: Int) {
    val canonicalSigned = canonicalJson(envelope["signed"]!!)
    val valid = envelope["signatures"]!!.jsonArray.map { it.jsonObject }.mapNotNull { signature ->
        val keyId = signature["keyid"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val key = keys[keyId]?.takeIf { keyId in allowedKeyIds } ?: return@mapNotNull null
        val raw = runCatching { signature["sig"]!!.jsonPrimitive.content.hexToBytes() }.getOrNull()?.takeIf { it.size == 64 } ?: return@mapNotNull null
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(key))
            update(canonicalSigned, 0, canonicalSigned.size)
        }
        keyId.takeIf { verifier.verifySignature(raw) }
    }.toSet()
    require(valid.size >= threshold) { "TUF signature threshold is not satisfied" }
}

fun canonicalJson(element: JsonElement): ByteArray = buildString { appendCanonical(element) }.toByteArray(Charsets.UTF_8)
private fun StringBuilder.appendCanonical(element: JsonElement) {
    when (element) {
        JsonNull -> append("null")
        is JsonArray -> {
            append('[')
            element.forEachIndexed { index, item -> if (index > 0) append(','); appendCanonical(item) }
            append(']')
        }
        is JsonObject -> {
            append('{')
            element.keys.sortedWith(::compareUtf8).forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(Json.encodeToString(key)).append(':')
                appendCanonical(requireNotNull(element[key]))
            }
            append('}')
        }
        is JsonPrimitive -> if (element.isString) append(Json.encodeToString(element.content)) else {
            val value = element.content
            require(value == "true" || value == "false" || value.matches(Regex("0|-?[1-9][0-9]*"))) { "TUF canonical JSON forbids non-integer numbers" }
            append(value)
        }
    }
}

private fun compareUtf8(left: String, right: String): Int {
    val a = left.toByteArray(Charsets.UTF_8)
    val b = right.toByteArray(Charsets.UTF_8)
    for (index in 0 until minOf(a.size, b.size)) {
        val compared = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff)
        if (compared != 0) return compared
    }
    return a.size.compareTo(b.size)
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it in "0123456789abcdef" })
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
fun Instant.utc(): String = toString()
