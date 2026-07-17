package codes.yousef.seen.registry

import com.google.api.gax.rpc.AlreadyExistsException
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.SetOptions
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant
import com.google.api.core.ApiFuture
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.security.MessageDigest

@Serializable
data class StoredPackage(val record: PackageRecord, val ownerPrincipal: String)

@Serializable
data class StoredRelease(
    val record: ReleaseRecord,
    val ownerPrincipal: String,
    val uploadId: String,
    val uploadExpiresAt: String,
    val manifest: kotlinx.serialization.json.JsonObject,
    val source: SourceDeclaration,
    val dependencies: List<SignedDependency> = emptyList(),
    val review: ReviewEvidenceState = ReviewEvidenceState(),
    /** Monotonic compare-and-set token for lifecycle mutations. */
    val revision: Long = 0,
)

sealed interface ReleaseTransitionResult {
    data class Applied(val value: StoredRelease) : ReleaseTransitionResult
    data class Conflict(val current: StoredRelease) : ReleaseTransitionResult
    data object Missing : ReleaseTransitionResult
}

sealed interface PromotionActivationResult {
    data class Applied(val value: StoredRelease) : PromotionActivationResult
    data class Conflict(val current: StoredRelease) : PromotionActivationResult
    data object MissingRelease : PromotionActivationResult
    data object MissingPackage : PromotionActivationResult
    data object AuditCollision : PromotionActivationResult
}

private fun requireSameRelease(current: StoredRelease, updated: StoredRelease) {
    require(current.record.`package` == updated.record.`package`) { "Release package cannot change" }
    require(current.record.version == updated.record.version) { "Release version cannot change" }
    require(current.ownerPrincipal == updated.ownerPrincipal) { "Release owner cannot change" }
    require(current.uploadId == updated.uploadId) { "Release upload cannot change" }
    if (current.uploadExpiresAt != updated.uploadExpiresAt) {
        require(current.record.state.lifecycle == "reserved" && updated.record.state.lifecycle == "reserved") {
            "Release upload expiry can change only while reserved"
        }
        require(Instant.parse(updated.uploadExpiresAt).isAfter(Instant.parse(current.uploadExpiresAt))) {
            "Release upload expiry can only be extended"
        }
    }
    require(current.manifest == updated.manifest) { "Release manifest cannot change" }
    require(current.source == updated.source) { "Release source cannot change" }
    require(current.dependencies == updated.dependencies) { "Release dependencies cannot change" }
    require(current.record.archive.format == updated.record.archive.format) { "Release archive format cannot change" }
    require(current.record.archive.sha256 == updated.record.archive.sha256) { "Release archive digest cannot change" }
    require(current.record.archive.compressedBytes == updated.record.archive.compressedBytes) { "Release archive size cannot change" }
    require(current.record.manifestSha256 == updated.record.manifestSha256) { "Release manifest digest cannot change" }
    listOf(
        "validated archive" to (current.review.validatedArchiveSha256 to updated.review.validatedArchiveSha256),
        "first scan ID" to (current.review.firstScanAttestationId to updated.review.firstScanAttestationId),
        "first scan digest" to (current.review.firstScanAttestationSha256 to updated.review.firstScanAttestationSha256),
        "second scan ID" to (current.review.secondScanAttestationId to updated.review.secondScanAttestationId),
        "second scan digest" to (current.review.secondScanAttestationSha256 to updated.review.secondScanAttestationSha256),
        "promotion attempt" to (current.review.promotionAttemptId to updated.review.promotionAttemptId),
        "promotion input digest" to (current.review.promotionInputSha256 to updated.review.promotionInputSha256),
        "rejection code" to (current.review.rejectionCode to updated.review.rejectionCode),
        "rejection evidence" to (current.review.rejectionEvidenceId to updated.review.rejectionEvidenceId),
    ).forEach { (name, values) ->
        val (before, after) = values
        if (before != null) require(after == before) { "$name evidence is append-only" }
    }
    if (current.review.firstScanAttestationId != null) {
        require(current.review.firstSourceProofId == updated.review.firstSourceProofId) { "Reviewed first source proof cannot change" }
        require(current.review.firstSourceProofSha256 == updated.review.firstSourceProofSha256) { "Reviewed first source proof digest cannot change" }
    }
    if (current.review.secondScanAttestationId != null) {
        require(current.review.secondSourceProofId == updated.review.secondSourceProofId) { "Reviewed second source proof cannot change" }
        require(current.review.secondSourceProofSha256 == updated.review.secondSourceProofSha256) { "Reviewed second source proof digest cannot change" }
    }
}

private fun requireActivationCommit(release: StoredRelease, artifact: ReviewArtifact, json: Json) {
    require(release.record.state.lifecycle == "active") { "Promotion must commit an active release" }
    require(release.record.state.visibility == "public") { "Promotion must commit a public release" }
    require(release.record.state.availability == "available") { "Promotion must commit an available release" }
    require(release.record.resolverMetadataVersion != null) { "Promotion metadata version is missing" }
    val activatedAt = requireNotNull(release.record.timestamps.activatedAt) { "Promotion activation timestamp is missing" }
    val attemptId = requireNotNull(release.review.promotionAttemptId) { "Promotion attempt is missing" }

    require(artifact.kind == AUDIT_EVENT_ARTIFACT) { "Promotion artifact must be an audit event" }
    require(artifact.packageIdentity == release.record.`package`) { "Promotion audit package does not match release" }
    require(artifact.version == release.record.version) { "Promotion audit version does not match release" }
    require(artifact.archiveSha256 == release.record.archive.sha256) { "Promotion audit archive does not match release" }
    val event = json.decodeFromJsonElement<AuditEventRecord>(artifact.payload)
    require(event.contractVersion == 1) { "Promotion audit contract version is unsupported" }
    require(event.eventId == artifact.artifactId) { "Promotion audit ID does not match artifact" }
    require(event.sequence == artifact.sequence) { "Promotion audit sequence does not match artifact" }
    require(event.occurredAt == artifact.createdAt && event.occurredAt == activatedAt) {
        "Promotion audit timestamp does not match activation"
    }
    require(event.action == "promotion" && event.outcome == "activated") {
        "Promotion audit outcome is invalid"
    }
    require(event.actor == AuditActor("worker", "release-promoter")) { "Promotion audit actor is invalid" }
    require(event.subject == AuditSubject(
        release.record.`package`,
        release.record.version,
        release.record.archive.sha256,
    )) { "Promotion audit subject does not match release" }
    require(event.evidenceIds == listOf(attemptId)) { "Promotion audit evidence does not match attempt" }
    require(event.internalReason == null) { "Promotion activation audit cannot contain an internal failure reason" }
}

@Serializable
data class StoredIdempotencyResponse(
    val status: Int,
    val bodyBase64: String,
    val headers: Map<String, String>,
)

@Serializable
data class StoredIdempotency(
    val scope: String,
    val fingerprint: String,
    val attemptId: String,
    val createdAt: String,
    val processingExpiresAt: String,
    val expiresAt: String,
    val response: StoredIdempotencyResponse? = null,
)

/**
 * Append-only review, provenance, audit, and enforcement evidence.
 *
 * The typed public record is kept in [payload]. These binding fields are
 * duplicated outside the payload so workers can select evidence without
 * trusting or partially decoding package-controlled data.
 */
@Serializable
data class ReviewArtifact(
    val artifactId: String,
    val kind: String,
    val packageIdentity: String? = null,
    val version: String? = null,
    val archiveSha256: String? = null,
    val sequence: Long,
    val createdAt: String,
    val payload: JsonObject,
)

private fun ReviewArtifact.documentFields(json: Json): Map<String, Any?> = mapOf(
    "json" to json.encodeToString(this),
    "kind" to kind,
    "package" to packageIdentity,
    "version" to version,
    "sequence" to sequence,
    "created_at" to createdAt,
)

sealed interface IdempotencyBegin {
    data object Acquired : IdempotencyBegin
    data class Replay(val value: StoredIdempotency) : IdempotencyBegin
    data object InProgress : IdempotencyBegin
}

interface RegistryRepository : AutoCloseable {
    fun createPackage(value: StoredPackage): Boolean
    fun getPackage(identity: String): StoredPackage?
    fun savePackage(value: StoredPackage)
    fun listPackages(): List<StoredPackage>
    fun reserveRelease(value: StoredRelease): Boolean
    fun getRelease(identity: String, version: String): StoredRelease?
    fun findReleaseByUpload(uploadId: String): StoredRelease?
    fun transitionRelease(expectedRevision: Long, value: StoredRelease): ReleaseTransitionResult
    fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): PromotionActivationResult
    fun listReleases(identity: String? = null): List<StoredRelease>
    fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin
    fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean
    fun appendReviewArtifact(value: ReviewArtifact): Boolean
    fun getReviewArtifact(artifactId: String): ReviewArtifact?
    fun listReviewArtifacts(
        packageIdentity: String? = null,
        version: String? = null,
        kind: String? = null,
    ): List<ReviewArtifact>
    fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Boolean
    fun releaseMetadataPublication(holder: String)
    fun nextMetadataVersion(): Long
    override fun close() = Unit
}

class InMemoryRegistryRepository : RegistryRepository {
    private val stateLock = Any()
    private val packages = ConcurrentHashMap<String, StoredPackage>()
    private val releases = ConcurrentHashMap<String, StoredRelease>()
    private val uploads = ConcurrentHashMap<String, String>()
    private val metadataVersion = AtomicLong(0)
    private val idempotency = ConcurrentHashMap<String, StoredIdempotency>()
    private val reviewArtifacts = ConcurrentHashMap<String, ReviewArtifact>()
    private var metadataPublicationHolder: String? = null
    private var metadataPublicationExpiresAt: Instant? = null

    override fun createPackage(value: StoredPackage): Boolean = synchronized(stateLock) {
        packages.putIfAbsent(value.record.identity, value) == null
    }
    override fun getPackage(identity: String): StoredPackage? = synchronized(stateLock) { packages[identity] }
    override fun savePackage(value: StoredPackage) = synchronized(stateLock) {
        packages[value.record.identity] = value
    }
    override fun listPackages(): List<StoredPackage> = synchronized(stateLock) {
        packages.values.sortedBy { it.record.identity }
    }

    override fun reserveRelease(value: StoredRelease): Boolean = synchronized(stateLock) {
        val key = key(value.record.`package`, value.record.version)
        if (releases.containsKey(key)) return@synchronized false
        releases[key] = value
        uploads[value.uploadId] = key
        true
    }

    override fun getRelease(identity: String, version: String): StoredRelease? = synchronized(stateLock) {
        releases[key(identity, version)]
    }
    override fun findReleaseByUpload(uploadId: String): StoredRelease? = synchronized(stateLock) {
        uploads[uploadId]?.let(releases::get)
    }
    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): ReleaseTransitionResult = synchronized(stateLock) {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val key = key(value.record.`package`, value.record.version)
        val current = releases[key] ?: return@synchronized ReleaseTransitionResult.Missing
        if (current.revision != expectedRevision) return@synchronized ReleaseTransitionResult.Conflict(current)
        requireSameRelease(current, value)
        releases[key] = value
        uploads[value.uploadId] = key
        ReleaseTransitionResult.Applied(value)
    }
    override fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): PromotionActivationResult = synchronized(stateLock) {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val releaseKey = key(value.record.`package`, value.record.version)
        val current = releases[releaseKey] ?: return@synchronized PromotionActivationResult.MissingRelease
        if (current.revision != expectedRevision) {
            return@synchronized PromotionActivationResult.Conflict(current)
        }
        requireSameRelease(current, value)
        requireActivationCommit(value, activationAudit, RegistryJson)
        val storedPackage = packages[value.record.`package`]
            ?: return@synchronized PromotionActivationResult.MissingPackage
        if (reviewArtifacts.containsKey(activationAudit.artifactId)) {
            return@synchronized PromotionActivationResult.AuditCollision
        }
        require(storedPackage.ownerPrincipal == value.ownerPrincipal) { "Promotion package owner does not match release" }

        val updatedPackage = storedPackage.copy(record = storedPackage.record.copy(
            latestActiveVersion = value.record.version,
            updatedAt = requireNotNull(value.record.timestamps.activatedAt),
        ))
        reviewArtifacts[activationAudit.artifactId] = activationAudit
        packages[value.record.`package`] = updatedPackage
        releases[releaseKey] = value
        uploads[value.uploadId] = releaseKey
        PromotionActivationResult.Applied(value)
    }
    override fun listReleases(identity: String?): List<StoredRelease> = synchronized(stateLock) {
        releases.values
            .filter { identity == null || it.record.`package` == identity }
            .sortedWith(compareByDescending<StoredRelease> { it.record.timestamps.reservedAt }.thenBy { it.record.version })
    }
    override fun nextMetadataVersion(): Long = metadataVersion.incrementAndGet()

    override fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin = synchronized(idempotency) {
        val existing = idempotency[value.scope]
        if (existing == null || !Instant.parse(existing.expiresAt).isAfter(now)) {
            idempotency[value.scope] = value
            IdempotencyBegin.Acquired
        } else if (existing.fingerprint != value.fingerprint) {
            throw RegistryException(409, "idempotency_key_reused", "Idempotency-Key was already used for a different request")
        } else if (existing.response != null) {
            IdempotencyBegin.Replay(existing)
        } else if (!Instant.parse(existing.processingExpiresAt).isAfter(now)) {
            idempotency[value.scope] = value
            IdempotencyBegin.Acquired
        } else {
            IdempotencyBegin.InProgress
        }
    }

    override fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean = synchronized(idempotency) {
        val existing = idempotency[scope] ?: error("Idempotency reservation is missing")
        check(existing.fingerprint == fingerprint) { "Idempotency fingerprint changed" }
        if (existing.attemptId != attemptId) return@synchronized false
        idempotency[scope] = existing.copy(response = response)
        true
    }

    override fun appendReviewArtifact(value: ReviewArtifact): Boolean = synchronized(stateLock) {
        reviewArtifacts.putIfAbsent(value.artifactId, value) == null
    }

    override fun getReviewArtifact(artifactId: String): ReviewArtifact? = synchronized(stateLock) {
        reviewArtifacts[artifactId]
    }

    override fun listReviewArtifacts(packageIdentity: String?, version: String?, kind: String?): List<ReviewArtifact> =
        synchronized(stateLock) {
            reviewArtifacts.values.asSequence()
                .filter { packageIdentity == null || it.packageIdentity == packageIdentity }
                .filter { version == null || it.version == version }
                .filter { kind == null || it.kind == kind }
                .sortedWith(compareByDescending<ReviewArtifact> { it.sequence }.thenByDescending { it.createdAt })
                .toList()
        }

    override fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Boolean = synchronized(this) {
        val currentHolder = metadataPublicationHolder
        val currentExpiry = metadataPublicationExpiresAt
        if (currentHolder != null && currentHolder != holder && currentExpiry?.isAfter(now) == true) return@synchronized false
        metadataPublicationHolder = holder
        metadataPublicationExpiresAt = expiresAt
        true
    }

    override fun releaseMetadataPublication(holder: String) = synchronized(this) {
        if (metadataPublicationHolder == holder) {
            metadataPublicationHolder = null
            metadataPublicationExpiresAt = null
        }
    }

    private fun key(identity: String, version: String) = "$identity@$version"
}

/** Firestore adapter storing byte-stable JSON projections in an isolated database. */
class FirestoreRegistryRepository(
    private val firestore: Firestore,
    private val json: Json = RegistryJson,
) : RegistryRepository {
    private val packages = firestore.collection("seen_registry_packages_v1")
    private val releases = firestore.collection("seen_registry_releases_v1")
    private val metadata = firestore.collection("seen_registry_state_v1").document("metadata")
    private val idempotency = firestore.collection("seen_registry_idempotency_v1")
    private val reviewArtifacts = firestore.collection("seen_registry_review_artifacts_v1")

    override fun createPackage(value: StoredPackage): Boolean = create(packages.document(id(value.record.identity)), json.encodeToString(value))
    override fun getPackage(identity: String): StoredPackage? = read(packages.document(id(identity)))
    override fun savePackage(value: StoredPackage) { packages.document(id(value.record.identity)).set(mapOf("json" to json.encodeToString(value))).get() }
    override fun listPackages(): List<StoredPackage> = packages.get().get().documents.mapNotNull { document ->
        document.getString("json")?.let { json.decodeFromString<StoredPackage>(it) }
    }.sortedBy { it.record.identity }

    override fun reserveRelease(value: StoredRelease): Boolean = create(
        releases.document(id("${value.record.`package`}@${value.record.version}")),
        json.encodeToString(value),
    )

    override fun getRelease(identity: String, version: String): StoredRelease? = read(releases.document(id("$identity@$version")))
    override fun findReleaseByUpload(uploadId: String): StoredRelease? = releases.whereEqualTo("upload_id", uploadId).limit(1).get().get().documents
        .firstOrNull()?.getString("json")?.let { json.decodeFromString(it) }
    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): ReleaseTransitionResult {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val document = releases.document(id("${value.record.`package`}@${value.record.version}"))
        return awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document).get()
            val current = snapshot.takeIf { it.exists() }?.getString("json")
                ?.let { json.decodeFromString<StoredRelease>(it) }
                ?: return@runTransaction ReleaseTransitionResult.Missing
            if (current.revision != expectedRevision) {
                return@runTransaction ReleaseTransitionResult.Conflict(current)
            }
            requireSameRelease(current, value)
            transaction.set(document, mapOf("json" to json.encodeToString(value), "upload_id" to value.uploadId))
            ReleaseTransitionResult.Applied(value)
        })
    }
    override fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): PromotionActivationResult {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val releaseDocument = releases.document(id("${value.record.`package`}@${value.record.version}"))
        val packageDocument = packages.document(id(value.record.`package`))
        val auditDocument = reviewArtifacts.document(id(activationAudit.artifactId))
        return awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val releaseSnapshot = transaction.get(releaseDocument).get()
            val current = releaseSnapshot.takeIf { it.exists() }?.getString("json")
                ?.let { json.decodeFromString<StoredRelease>(it) }
                ?: return@runTransaction PromotionActivationResult.MissingRelease
            if (current.revision != expectedRevision) {
                return@runTransaction PromotionActivationResult.Conflict(current)
            }
            requireSameRelease(current, value)
            requireActivationCommit(value, activationAudit, json)

            // Firestore transactions require every read before the first write.
            // Reading the package and artifact documents here also makes a
            // concurrent catalog change or artifact creation retry this commit.
            val packageSnapshot = transaction.get(packageDocument).get()
            val storedPackage = packageSnapshot.takeIf { it.exists() }?.getString("json")
                ?.let { json.decodeFromString<StoredPackage>(it) }
                ?: return@runTransaction PromotionActivationResult.MissingPackage
            val auditSnapshot = transaction.get(auditDocument).get()
            if (auditSnapshot.exists()) return@runTransaction PromotionActivationResult.AuditCollision
            require(storedPackage.ownerPrincipal == value.ownerPrincipal) { "Promotion package owner does not match release" }

            val updatedPackage = storedPackage.copy(record = storedPackage.record.copy(
                latestActiveVersion = value.record.version,
                updatedAt = requireNotNull(value.record.timestamps.activatedAt),
            ))
            transaction.set(auditDocument, activationAudit.documentFields(json))
            transaction.set(packageDocument, mapOf("json" to json.encodeToString(updatedPackage)))
            transaction.set(releaseDocument, mapOf("json" to json.encodeToString(value), "upload_id" to value.uploadId))
            PromotionActivationResult.Applied(value)
        })
    }
    override fun listReleases(identity: String?): List<StoredRelease> = releases.get().get().documents.mapNotNull { document ->
        document.getString("json")?.let { json.decodeFromString<StoredRelease>(it) }
    }.filter { identity == null || it.record.`package` == identity }
        .sortedWith(compareByDescending<StoredRelease> { it.record.timestamps.reservedAt }.thenBy { it.record.version })

    override fun nextMetadataVersion(): Long = firestore.runTransaction { transaction ->
        val snapshot = transaction.get(metadata).get()
        val next = (snapshot.getLong("version") ?: 0L) + 1L
        transaction.set(metadata, mapOf("version" to next), SetOptions.merge())
        next
    }.get()

    override fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Boolean =
        awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(metadata).get()
            val currentHolder = snapshot.getString("publication_holder")
            val currentExpiry = snapshot.getString("publication_expires_at")?.let(Instant::parse)
            if (currentHolder != null && currentHolder != holder && currentExpiry?.isAfter(now) == true) {
                false
            } else {
                transaction.set(metadata, mapOf(
                    "publication_holder" to holder,
                    "publication_expires_at" to expiresAt.utc(),
                ), SetOptions.merge())
                true
            }
        })

    override fun releaseMetadataPublication(holder: String) {
        awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(metadata).get()
            if (snapshot.getString("publication_holder") == holder) {
                transaction.update(metadata, mapOf(
                    "publication_holder" to FieldValue.delete(),
                    "publication_expires_at" to FieldValue.delete(),
                ))
            }
            null
        })
    }

    override fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin {
        val document = idempotency.document(id(value.scope))
        return awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document).get()
            val existing = snapshot.takeIf { it.exists() }?.getString("json")?.let { json.decodeFromString<StoredIdempotency>(it) }
            when {
                existing == null || !Instant.parse(existing.expiresAt).isAfter(now) -> {
                    transaction.set(document, mapOf("json" to json.encodeToString(value), "expires_at" to value.expiresAt))
                    IdempotencyBegin.Acquired
                }
                existing.fingerprint != value.fingerprint -> throw RegistryException(
                    409, "idempotency_key_reused", "Idempotency-Key was already used for a different request",
                )
                existing.response != null -> IdempotencyBegin.Replay(existing)
                !Instant.parse(existing.processingExpiresAt).isAfter(now) -> {
                    transaction.set(document, mapOf("json" to json.encodeToString(value), "expires_at" to value.expiresAt))
                    IdempotencyBegin.Acquired
                }
                else -> IdempotencyBegin.InProgress
            }
        })
    }

    override fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean {
        val document = idempotency.document(id(scope))
        return awaitRegistryTransaction(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document).get()
            val existing = snapshot.takeIf { it.exists() }?.getString("json")?.let { json.decodeFromString<StoredIdempotency>(it) }
                ?: error("Idempotency reservation is missing")
            check(existing.fingerprint == fingerprint) { "Idempotency fingerprint changed" }
            if (existing.attemptId != attemptId) return@runTransaction false
            transaction.set(document, mapOf("json" to json.encodeToString(existing.copy(response = response)), "expires_at" to existing.expiresAt))
            true
        })
    }

    override fun appendReviewArtifact(value: ReviewArtifact): Boolean = try {
        reviewArtifacts.document(id(value.artifactId)).create(value.documentFields(json)).get()
        true
    } catch (error: Exception) {
        var cause: Throwable? = error
        while (cause != null && cause !is AlreadyExistsException) cause = cause.cause?.takeUnless { it === cause }
        if (cause is AlreadyExistsException) false else throw error
    }

    override fun getReviewArtifact(artifactId: String): ReviewArtifact? =
        read(reviewArtifacts.document(id(artifactId)))

    override fun listReviewArtifacts(packageIdentity: String?, version: String?, kind: String?): List<ReviewArtifact> =
        reviewArtifacts.get().get().documents.mapNotNull { document ->
            document.getString("json")?.let { json.decodeFromString<ReviewArtifact>(it) }
        }.asSequence()
            .filter { packageIdentity == null || it.packageIdentity == packageIdentity }
            .filter { version == null || it.version == version }
            .filter { kind == null || it.kind == kind }
            .sortedWith(compareByDescending<ReviewArtifact> { it.sequence }.thenByDescending { it.createdAt })
            .toList()

    override fun close() = firestore.close()

    private inline fun <reified T> read(document: com.google.cloud.firestore.DocumentReference): T? =
        document.get().get().takeIf { it.exists() }?.getString("json")?.let(json::decodeFromString)

    private fun create(document: com.google.cloud.firestore.DocumentReference, value: String): Boolean = try {
        document.create(mapOf("json" to value, "upload_id" to runCatching { json.decodeFromString<StoredRelease>(value).uploadId }.getOrNull())).get()
        true
    } catch (error: Exception) {
        var cause: Throwable? = error
        while (cause != null && cause !is AlreadyExistsException) cause = cause.cause?.takeUnless { it === cause }
        if (cause is AlreadyExistsException) false else throw error
    }

    private fun id(value: String): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun <T> awaitRegistryTransaction(future: ApiFuture<T>): T = try {
        future.get()
    } catch (error: Exception) {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is RegistryException) throw cause
            cause = cause.cause?.takeUnless { it === cause }
        }
        throw error
    }

    companion object {
        fun create(config: RegistryConfig): FirestoreRegistryRepository {
            return create(requireNotNull(config.projectId), config.firestoreDatabase)
        }

        fun create(projectId: String, databaseId: String): FirestoreRegistryRepository {
            val firestore = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setDatabaseId(databaseId)
                .build()
                .service
            return FirestoreRegistryRepository(firestore)
        }
    }
}

data class StoredObject(val bytes: ByteArray, val contentType: String)

private fun verifyStoredObject(
    input: InputStream,
    declaredBytes: Long,
    expectedBytes: Long,
    expectedDigest: String,
    description: String,
) {
    require(expectedBytes in 0..ArchivePolicy.MAX_COMPRESSED_BYTES) { "Expected archive size is outside archive policy" }
    IdentityRules.requireDigest(expectedDigest, "archive digest")
    check(declaredBytes == expectedBytes) {
        "$description size changed before promotion: expected $expectedBytes bytes, found $declaredBytes"
    }

    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var observedBytes = 0L
    input.use { source ->
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            observedBytes += read
            check(observedBytes <= expectedBytes) { "$description exceeded its declared size during promotion" }
            digest.update(buffer, 0, read)
        }
    }
    check(observedBytes == expectedBytes) {
        "$description size changed while being read: expected $expectedBytes bytes, found $observedBytes"
    }
    val observedDigest = digest.digest().joinToString("") { "%02x".format(it) }
    check(observedDigest == expectedDigest) { "$description digest changed before promotion" }
}

interface RegistryObjectStorage {
    fun putQuarantine(uploadId: String, bytes: ByteArray)
    fun putQuarantine(uploadId: String, source: ReopenableArchiveSource) {
        val bytes = source.openStream().use(InputStream::readAllBytes)
        require(bytes.size.toLong() <= ArchivePolicy.MAX_COMPRESSED_BYTES) { "Quarantine object exceeds archive policy" }
        putQuarantine(uploadId, bytes)
    }
    fun getQuarantine(uploadId: String): ByteArray?
    fun openQuarantine(uploadId: String): InputStream? = getQuarantine(uploadId)?.inputStream()
    fun quarantineSource(uploadId: String): ReopenableArchiveSource? {
        openQuarantine(uploadId)?.use { } ?: return null
        return ReopenableArchiveSource {
            openQuarantine(uploadId) ?: throw FileNotFoundException("Quarantine object disappeared")
        }
    }
    fun deleteQuarantine(uploadId: String)
    fun copyQuarantineToPublic(uploadId: String, digest: String, expectedBytes: Long) {
        val bytes = getQuarantine(uploadId) ?: throw FileNotFoundException("Quarantine object is missing")
        verifyStoredObject(bytes.inputStream(), bytes.size.toLong(), expectedBytes, digest, "Quarantine object")
        putPublicBlob(digest, bytes)
        val public = getPublicBlob(digest) ?: throw FileNotFoundException("Public object is missing after promotion")
        verifyStoredObject(public.inputStream(), public.size.toLong(), expectedBytes, digest, "Public object")
    }
    fun putPublicBlob(digest: String, bytes: ByteArray)
    fun getPublicBlob(digest: String): ByteArray?
    fun putMetadata(filename: String, bytes: ByteArray)
    fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean {
        if (getMetadata(filename) != null) return false
        putMetadata(filename, bytes)
        return true
    }
    /**
     * Replaces a mutable metadata pointer only when its bytes still match the
     * value observed before publication began. Implementations must make the
     * comparison and write atomic so an expired publisher cannot overwrite a
     * transaction committed by a newer lease holder.
     */
    fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean
    fun getMetadata(filename: String): ByteArray?
}

class InMemoryRegistryObjectStorage : RegistryObjectStorage {
    private val quarantine = ConcurrentHashMap<String, ByteArray>()
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    private val metadata = ConcurrentHashMap<String, ByteArray>()
    override fun putQuarantine(uploadId: String, bytes: ByteArray) {
        require(bytes.size.toLong() <= ArchivePolicy.MAX_COMPRESSED_BYTES) { "Quarantine object exceeds archive policy" }
        quarantine.putIfAbsent(uploadId, bytes.copyOf())
    }
    override fun putQuarantine(uploadId: String, source: ReopenableArchiveSource) {
        val bytes = source.openStream().use(InputStream::readAllBytes)
        require(bytes.size.toLong() <= ArchivePolicy.MAX_COMPRESSED_BYTES) { "Quarantine object exceeds archive policy" }
        quarantine.putIfAbsent(uploadId, bytes)
    }
    override fun getQuarantine(uploadId: String): ByteArray? = quarantine[uploadId]?.copyOf()
    override fun openQuarantine(uploadId: String): InputStream? = quarantine[uploadId]?.copyOf()?.inputStream()
    override fun deleteQuarantine(uploadId: String) { quarantine.remove(uploadId) }
    override fun putPublicBlob(digest: String, bytes: ByteArray) { blobs.putIfAbsent(digest, bytes.copyOf()) }
    override fun getPublicBlob(digest: String): ByteArray? = blobs[digest]?.copyOf()
    override fun putMetadata(filename: String, bytes: ByteArray) { metadata[filename] = bytes.copyOf() }
    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean = metadata.putIfAbsent(filename, bytes.copyOf()) == null
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean = synchronized(metadata) {
        val current = metadata[filename]
        val unchanged = if (expected == null) current == null else current?.contentEquals(expected) == true
        if (!unchanged) return@synchronized false
        metadata[filename] = bytes.copyOf()
        true
    }
    override fun getMetadata(filename: String): ByteArray? = metadata[filename]?.copyOf()
}

class GcsRegistryObjectStorage(
    private val storage: Storage,
    private val quarantineBucket: String,
    private val publicBucket: String,
    private val metadataBucket: String,
    private val prefix: String,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray) =
        putQuarantine(uploadId, ReopenableArchiveSource { ByteArrayInputStream(bytes) })

    override fun putQuarantine(uploadId: String, source: ReopenableArchiveSource) {
        val blobId = BlobId.of(quarantineBucket, "$prefix/quarantine/$uploadId")
        val info = BlobInfo.newBuilder(blobId)
            .setContentType("application/gzip")
            .setCacheControl("no-store")
            .build()
        try {
            storage.writer(info, Storage.BlobWriteOption.doesNotExist()).use { output ->
                source.openStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        require(total <= ArchivePolicy.MAX_COMPRESSED_BYTES) { "Quarantine object exceeds archive policy" }
                        output.write(ByteBuffer.wrap(buffer, 0, read))
                    }
                }
            }
        } catch (failure: StorageException) {
            if (failure.code != 412) throw failure
        }
    }
    override fun openQuarantine(uploadId: String): InputStream? =
        storage.get(BlobId.of(quarantineBucket, "$prefix/quarantine/$uploadId"))?.reader()?.let(Channels::newInputStream)
    override fun getQuarantine(uploadId: String): ByteArray? = openQuarantine(uploadId)?.use(InputStream::readAllBytes)
    override fun deleteQuarantine(uploadId: String) { storage.delete(BlobId.of(quarantineBucket, "$prefix/quarantine/$uploadId")) }
    override fun copyQuarantineToPublic(uploadId: String, digest: String, expectedBytes: Long) {
        require(expectedBytes in 0..ArchivePolicy.MAX_COMPRESSED_BYTES) { "Expected archive size is outside archive policy" }
        IdentityRules.requireDigest(digest, "archive digest")
        val sourceId = BlobId.of(quarantineBucket, "$prefix/quarantine/$uploadId")
        val source = storage.get(sourceId) ?: throw FileNotFoundException("Quarantine object is missing")
        verifyBlob(source, expectedBytes, digest, "Quarantine object", "application/gzip", "no-store")

        val targetId = BlobId.of(publicBucket, "$prefix/blobs/sha256/$digest")
        val target = BlobInfo.newBuilder(targetId)
            .setContentType("application/gzip")
            .setCacheControl("public,max-age=31536000,immutable")
            .build()
        storage.get(targetId)?.let { existing ->
            verifyPublicBlob(existing, expectedBytes, digest)
            return
        }

        val copied = try {
            storage.copy(Storage.CopyRequest.newBuilder()
                .setSource(sourceId)
                .setSourceOptions(Storage.BlobSourceOption.generationMatch(requireNotNull(source.generation)))
                .setTarget(target, Storage.BlobTargetOption.doesNotExist())
                .build())
                .result
        } catch (failure: StorageException) {
            if (failure.code != 412) throw failure
            val existing = storage.get(targetId) ?: throw failure
            verifyPublicBlob(existing, expectedBytes, digest)
            return
        }
        verifyPublicBlob(copied, expectedBytes, digest)
    }

    private fun verifyPublicBlob(blob: com.google.cloud.storage.Blob, expectedBytes: Long, digest: String) {
        verifyBlob(
            blob = blob,
            expectedBytes = expectedBytes,
            digest = digest,
            description = "Public object",
            expectedContentType = "application/gzip",
            expectedCacheControl = "public,max-age=31536000,immutable",
        )
    }

    private fun verifyBlob(
        blob: com.google.cloud.storage.Blob,
        expectedBytes: Long,
        digest: String,
        description: String,
        expectedContentType: String,
        expectedCacheControl: String,
    ) {
        check(blob.contentType == expectedContentType) { "$description content type is not promotion-safe" }
        check(blob.cacheControl == expectedCacheControl) { "$description cache policy is not promotion-safe" }
        val generation = requireNotNull(blob.generation) { "$description generation is missing" }
        val declaredBytes = requireNotNull(blob.size) { "$description size is missing" }
        val input = storage.reader(blob.blobId, Storage.BlobSourceOption.generationMatch(generation))
            .let(Channels::newInputStream)
        verifyStoredObject(input, declaredBytes, expectedBytes, digest, description)
    }
    override fun putPublicBlob(digest: String, bytes: ByteArray) = put(publicBucket, "$prefix/blobs/sha256/$digest", bytes, "application/gzip", "public,max-age=31536000,immutable")
    override fun getPublicBlob(digest: String): ByteArray? = get(publicBucket, "$prefix/blobs/sha256/$digest")
    override fun putMetadata(filename: String, bytes: ByteArray) = put(metadataBucket, "$prefix/metadata/$filename", bytes, "application/json", "public,max-age=300,must-revalidate")
    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean {
        val info = BlobInfo.newBuilder(BlobId.of(metadataBucket, "$prefix/metadata/$filename"))
            .setContentType("application/json")
            .setCacheControl("public,max-age=300,must-revalidate")
            .build()
        return try {
            storage.create(info, bytes, Storage.BlobTargetOption.doesNotExist())
            true
        } catch (failure: StorageException) {
            if (failure.code == 412) false else throw failure
        }
    }
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
        val blobId = BlobId.of(metadataBucket, "$prefix/metadata/$filename")
        val info = BlobInfo.newBuilder(blobId)
            .setContentType("application/json")
            .setCacheControl("public,max-age=300,must-revalidate")
            .build()
        val precondition = if (expected == null) {
            Storage.BlobTargetOption.doesNotExist()
        } else {
            val current = storage.get(blobId) ?: return false
            if (!current.getContent().contentEquals(expected)) return false
            Storage.BlobTargetOption.generationMatch(current.generation)
        }
        return try {
            storage.create(info, bytes, precondition)
            true
        } catch (failure: StorageException) {
            if (failure.code == 412) false else throw failure
        }
    }
    override fun getMetadata(filename: String): ByteArray? = get(metadataBucket, "$prefix/metadata/$filename")

    private fun put(bucket: String, name: String, bytes: ByteArray, contentType: String, cacheControl: String) {
        val info = BlobInfo.newBuilder(BlobId.of(bucket, name)).setContentType(contentType).setCacheControl(cacheControl).build()
        storage.create(info, bytes)
    }
    private fun get(bucket: String, name: String): ByteArray? = storage.get(BlobId.of(bucket, name))?.getContent()

    companion object {
        fun create(config: RegistryConfig): GcsRegistryObjectStorage = GcsRegistryObjectStorage(
            storage = StorageOptions.newBuilder().setProjectId(config.projectId).build().service,
            quarantineBucket = requireNotNull(config.quarantineBucket),
            publicBucket = requireNotNull(config.publicBucket),
            metadataBucket = requireNotNull(config.metadataBucket),
            prefix = config.objectPrefix,
        )

        fun create(
            projectId: String,
            quarantineBucket: String,
            publicBucket: String = quarantineBucket,
            metadataBucket: String = quarantineBucket,
            prefix: String,
        ): GcsRegistryObjectStorage = GcsRegistryObjectStorage(
            storage = StorageOptions.newBuilder().setProjectId(projectId).build().service,
            quarantineBucket = quarantineBucket,
            publicBucket = publicBucket,
            metadataBucket = metadataBucket,
            prefix = prefix,
        )
    }
}

val RegistryJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}
