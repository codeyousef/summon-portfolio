package codes.yousef.seen.registry

import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
)

data class TufTargetsRenewalImportResult(
    val targetsVersion: Long,
    val onlineTransactionVersion: Long,
)

private data class TargetsRootTrust(
    val keys: Map<String, ByteArray>,
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

    fun resolveCurrent(
        storage: RegistryObjectStorage,
        requireOnlineTransaction: Boolean,
        allowExpiredTargets: Boolean = false,
    ): TrustedTopLevelTargets {
        val rootBytes = storage.getMetadata("root.json") ?: storage.getMetadata("1.root.json")
            ?: error("Offline TUF bootstrap is missing root.json")
        val root = validateRoot(rootBytes)
        val timestampBytes = storage.getMetadata("timestamp.json")
        if (timestampBytes == null) {
            require(!requireOnlineTransaction) { "Online TUF transaction is missing timestamp.json" }
            val targetsBytes = storage.getMetadata("1.targets.json")
                ?: error("Offline TUF bootstrap is missing 1.targets.json")
            val targets = validateTargets(root, targetsBytes, requireFresh = false, allowExpired = allowExpiredTargets)
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
        val targets = validateTargets(root, targetsBytes, requireFresh = false, allowExpired = allowExpiredTargets)
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
        return TargetsRootTrust(keys, targetsRole, snapshotRole.single(), timestampRole.single())
    }

    private fun validateTargets(
        root: TargetsRootTrust,
        bytes: ByteArray,
        requireFresh: Boolean,
        allowExpired: Boolean = false,
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
        validateDelegations(signed["delegations"]!!.jsonObject)
        return ValidatedTargets(version, signed)
    }

    private fun validateDelegations(delegations: JsonObject) {
        require(delegations.keys == setOf("keys", "roles")) { "Targets delegations fields are invalid" }
        val delegatedKeys = parseKeys(delegations["keys"]!!.jsonObject)
        val expected = mapOf(
            tufKeyId(online.releases) to online.releases,
            tufKeyId(online.security) to online.security,
        )
        require(delegatedKeys.keys == expected.keys && delegatedKeys.all { (id, key) -> key.contentEquals(expected[id]) }) {
            "Delegated keys do not match configured releases/security online keys"
        }
        val roles = delegations["roles"]!!.jsonArray.map(JsonElement::jsonObject)
        require(roles.map { it["name"]!!.jsonPrimitive.content } == listOf(TufRole.SECURITY, TufRole.RELEASES)) {
            "Targets delegation order must be security then releases"
        }
        val byName = roles.associateBy { it["name"]!!.jsonPrimitive.content }
        require(byName.keys == setOf(TufRole.SECURITY, TufRole.RELEASES)) { "Targets contains unexpected delegated roles" }
        mapOf(TufRole.SECURITY to online.security, TufRole.RELEASES to online.releases).forEach { (name, key) ->
            val role = byName.getValue(name)
            require(role.keys == setOf("name", "keyids", "threshold", "terminating", "paths")) { "$name delegation fields are invalid" }
            require(role["keyids"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf(tufKeyId(key))) { "$name delegation key is invalid" }
            require(role["threshold"]!!.jsonPrimitive.content == "1") { "$name delegation threshold is invalid" }
            require(role["terminating"]!!.jsonPrimitive.content == "false") { "$name delegation termination policy is invalid" }
            require(role["paths"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf("packages/*/*/*/*/*")) { "$name delegation path is invalid" }
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

    private data class ValidatedTargets(val version: Long, val signed: JsonObject)
    private data class ValidatedEnvelope(val version: Long, val signed: JsonObject)

    private companion object {
        val TARGETS_LIFETIME: Duration = Duration.ofDays(30)
        val ROOT_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id", "consistent_snapshot", "keys", "roles")
        val TARGETS_FIELDS = setOf("_type", "spec_version", "version", "expires", "environment", "repository_id", "targets", "delegations")
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
        val existingRoot = storage.getMetadata("1.root.json")
        val existingTargets = storage.getMetadata("1.targets.json")
        if (existingRoot != null || existingTargets != null) {
            require(existingRoot != null && existingTargets != null) { "Partial TUF bootstrap state exists; refusing to overwrite" }
            require(metadataKeyIds(existingRoot).containsAll(rootIds + targetIds + listOf(tufKeyId(online.snapshot), tufKeyId(online.timestamp)))) {
                "Existing root keys differ from the requested ceremony"
            }
            require(metadataKeyIds(existingTargets).containsAll(targetIds + listOf(tufKeyId(online.releases), tufKeyId(online.security)))) {
                "Existing targets keys differ from the requested ceremony"
            }
            return TufBootstrapResult(existingRoot, existingTargets, rootIds, targetIds)
        }

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
        storage.putMetadata("1.root.json", root)
        storage.putMetadata("root.json", root)
        storage.putMetadata("1.targets.json", targets)
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

        persistExact("1.root.json", rootBytes)
        persistExact("root.json", rootBytes)
        persistExact("1.targets.json", targetsBytes)
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

    private fun persistExact(name: String, bytes: ByteArray) {
        val existing = storage.getMetadata(name)
        require(existing == null || existing.contentEquals(bytes)) { "Existing $name differs; refusing bootstrap overwrite" }
        if (existing == null) storage.putMetadata(name, bytes)
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

    fun requireBootstrap(): ByteArray = storage.getMetadata("1.root.json")
        ?: error("Offline TUF bootstrap is missing 1.root.json")

    @Synchronized
    fun ensureInitialTransaction(): Long = ensureFreshTransaction()

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

    private fun publishUnlocked(
        publicReleases: List<StoredRelease>,
        targetsOverride: ByteArray? = null,
        expectedTimestamp: ByteArray?,
    ): Long {
        requireBootstrap()
        val currentTargets = if (targetsOverride == null) {
            targetsRenewalPolicy.resolveCurrent(storage, requireOnlineTransaction = false)
        } else {
            TrustedTopLevelTargets(requireBootstrap(), targetsOverride, signedVersion(targetsOverride))
        }
        val targets = currentTargets.targets
        val targetsVersion = currentTargets.version
        val version = repository.nextMetadataVersion()
        val releasesSigned = commonMetadata("targets", version, Duration.ofDays(7), environment, repositoryId, clock).toMutableMap().apply {
            put("targets", JsonObject(publicReleases.associate { targetFor(it) }))
        }.let(::JsonObject)
        val securitySigned = commonMetadata("targets", version, Duration.ofHours(6), environment, repositoryId, clock).toMutableMap().apply {
            put("targets", JsonObject(emptyMap()))
        }.let(::JsonObject)
        val releases = envelope(releasesSigned, listOf(online.releases))
        val security = envelope(securitySigned, listOf(online.security))
        val snapshotSigned = commonMetadata("snapshot", version, Duration.ofDays(1), environment, repositoryId, clock).toMutableMap().apply {
            put("meta", buildJsonObject {
                put("targets.json", fileMeta(targetsVersion, targets))
                put("releases.json", fileMeta(version, releases))
                put("security.json", fileMeta(version, security))
            })
        }.let(::JsonObject)
        val snapshot = envelope(snapshotSigned, listOf(online.snapshot))
        val timestampSigned = commonMetadata("timestamp", version, Duration.ofHours(6), environment, repositoryId, clock).toMutableMap().apply {
            put("meta", buildJsonObject { put("snapshot.json", fileMeta(version, snapshot)) })
        }.let(::JsonObject)
        val timestamp = envelope(timestampSigned, listOf(online.timestamp))

        storage.putMetadata("$version.releases.json", releases)
        storage.putMetadata("$version.security.json", security)
        storage.putMetadata("$version.snapshot.json", snapshot)
        if (!storage.replaceMetadataIfUnchanged("timestamp.json", expectedTimestamp, timestamp)) {
            throw RegistryException(
                503,
                "temporarily_unavailable",
                "Signed registry metadata changed during publication",
                retryable = true,
                retryAfterSeconds = 5,
            )
        }
        return version
    }

    private fun currentPublicReleases(): List<StoredRelease> = repository.listReleases().filter {
        it.record.state.visibility == "public" && it.record.state.availability == "available"
    }

    private fun mergePublicReleases(additions: List<StoredRelease>): List<StoredRelease> =
        (currentPublicReleases() + additions)
            .filter { it.record.state.visibility == "public" && it.record.state.availability == "available" }
            .associateBy { "${it.record.`package`}@${it.record.version}" }
            .values
            .sortedWith(compareBy<StoredRelease> { it.record.`package` }.thenBy { it.record.version })

    private fun readOnlineState(): OnlineState? {
        val timestamp = storage.getMetadata("timestamp.json") ?: return null
        // Validate the complete trust path, including offline root and current
        // top-level targets expiry, before treating online freshness as ready.
        targetsRenewalPolicy.resolveCurrent(storage, requireOnlineTransaction = true)
        val timestampSigned = signedObject(timestamp, "timestamp")
        val version = timestampSigned["version"]!!.jsonPrimitive.content.toLong()
        val (snapshotVersion, snapshot) = referencedMetadata(
            timestampSigned["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject,
            "snapshot",
        )
        val snapshotSigned = signedObject(snapshot, "snapshot", snapshotVersion)
        val meta = snapshotSigned["meta"]!!.jsonObject
        val (targetsVersion, targets) = referencedMetadata(meta["targets.json"]!!.jsonObject, "targets")
        signedObject(targets, "targets", targetsVersion)
        val roleExpiries = listOf(TufRole.RELEASES, TufRole.SECURITY).map { role ->
            val (roleVersion, roleBytes) = referencedMetadata(meta["$role.json"]!!.jsonObject, role)
            expiry(signedObject(roleBytes, "targets", roleVersion))
        }
        return OnlineState(
            version = version,
            minimumExpiry = (roleExpiries + expiry(snapshotSigned) + expiry(timestampSigned)).min(),
        )
    }

    private fun signedObject(bytes: ByteArray, expectedType: String, expectedVersion: Long? = null): JsonObject {
        val signed = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signed"]!!.jsonObject
        require(signed["_type"]?.jsonPrimitive?.content == expectedType) { "Online metadata role type is invalid" }
        require(signed["environment"]?.jsonPrimitive?.content == environment) { "Online metadata environment is invalid" }
        require(signed["repository_id"]?.jsonPrimitive?.content == repositoryId) { "Online metadata repository is invalid" }
        if (expectedVersion != null) require(signed["version"]?.jsonPrimitive?.content?.toLong() == expectedVersion) {
            "Online metadata version is invalid"
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

    private data class OnlineState(val version: Long, val minimumExpiry: Instant)

    private fun targetFor(stored: StoredRelease): Pair<String, JsonElement> {
        val release = stored.record
        val (_, name) = IdentityRules.requireIdentity(release.`package`)
        val filename = "$name-${release.version}.seenpkg.tgz"
        val path = "packages/${release.`package`}/${release.version}/${release.archive.sha256}/$filename"
        val sourceDigest = sha256(RegistryJson.encodeToString(stored.source).toByteArray())
        return path to buildJsonObject {
            put("length", release.archive.compressedBytes)
            put("hashes", buildJsonObject { put("sha256", release.archive.sha256) })
            put("custom", buildJsonObject {
                put("environment", environment)
                put("registry_origin", registryOrigin)
                put("package", release.`package`)
                put("version", release.version)
                put("archive_sha256", release.archive.sha256)
                put("archive_filename", filename)
                put("visibility", "public")
                put("lifecycle", release.state.lifecycle)
                put("retention", release.state.retention)
                put("availability", release.state.availability)
                put("source_proof_sha256", sourceDigest)
                put("provenance_sha256", sourceDigest)
                put("dependencies", RegistryJson.encodeToJsonElement(ListSerializer, stored.dependencies))
                put("capabilities", buildJsonArray { release.capabilities.forEach { add(JsonPrimitive(it)) } })
            })
        }
    }

    private companion object {
        val REFRESH_WINDOW: Duration = Duration.ofHours(2)
        val PUBLICATION_LEASE: Duration = Duration.ofMinutes(2)
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(SignedDependency.serializer())
    }
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

private fun metadataKeyIds(metadata: ByteArray): Set<String> {
    val signed = RegistryJson.parseToJsonElement(metadata.decodeToString()).jsonObject["signed"]!!.jsonObject
    val direct = signed["keys"]?.jsonObject?.keys.orEmpty()
    val delegated = signed["delegations"]?.jsonObject?.get("keys")?.jsonObject?.keys.orEmpty()
    return direct + delegated
}

private fun parseCanonicalEnvelope(bytes: ByteArray): JsonObject {
    val envelope = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject
    require(envelope.keys == setOf("signatures", "signed")) { "TUF envelope fields are invalid" }
    require(canonicalJson(envelope).contentEquals(bytes)) { "TUF envelope is not canonical JSON" }
    require(envelope["signatures"] is JsonArray && envelope["signed"] is JsonObject) { "TUF envelope shape is invalid" }
    return envelope
}

private fun parseKeys(keys: JsonObject): Map<String, ByteArray> = keys.mapValues { (keyId, element) ->
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

private fun verifyThreshold(envelope: JsonObject, keys: Map<String, ByteArray>, allowedKeyIds: Set<String>, threshold: Int) {
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
