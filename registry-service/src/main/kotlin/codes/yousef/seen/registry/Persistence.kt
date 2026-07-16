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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant
import com.google.api.core.ApiFuture

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
    /** Monotonic compare-and-set token for lifecycle mutations. */
    val revision: Long = 0,
)

sealed interface ReleaseTransitionResult {
    data class Applied(val value: StoredRelease) : ReleaseTransitionResult
    data class Conflict(val current: StoredRelease) : ReleaseTransitionResult
    data object Missing : ReleaseTransitionResult
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
    fun listReleases(identity: String? = null): List<StoredRelease>
    fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin
    fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean
    fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Boolean
    fun releaseMetadataPublication(holder: String)
    fun nextMetadataVersion(): Long
    override fun close() = Unit
}

class InMemoryRegistryRepository : RegistryRepository {
    private val packages = ConcurrentHashMap<String, StoredPackage>()
    private val releases = ConcurrentHashMap<String, StoredRelease>()
    private val uploads = ConcurrentHashMap<String, String>()
    private val metadataVersion = AtomicLong(0)
    private val idempotency = ConcurrentHashMap<String, StoredIdempotency>()
    private var metadataPublicationHolder: String? = null
    private var metadataPublicationExpiresAt: Instant? = null

    override fun createPackage(value: StoredPackage): Boolean = packages.putIfAbsent(value.record.identity, value) == null
    override fun getPackage(identity: String): StoredPackage? = packages[identity]
    override fun savePackage(value: StoredPackage) { packages[value.record.identity] = value }
    override fun listPackages(): List<StoredPackage> = packages.values.sortedBy { it.record.identity }

    override fun reserveRelease(value: StoredRelease): Boolean = synchronized(releases) {
        val key = key(value.record.`package`, value.record.version)
        if (releases.containsKey(key)) return@synchronized false
        releases[key] = value
        uploads[value.uploadId] = key
        true
    }

    override fun getRelease(identity: String, version: String): StoredRelease? = releases[key(identity, version)]
    override fun findReleaseByUpload(uploadId: String): StoredRelease? = uploads[uploadId]?.let(releases::get)
    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): ReleaseTransitionResult = synchronized(releases) {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val key = key(value.record.`package`, value.record.version)
        val current = releases[key] ?: return@synchronized ReleaseTransitionResult.Missing
        if (current.revision != expectedRevision) return@synchronized ReleaseTransitionResult.Conflict(current)
        requireSameRelease(current, value)
        releases[key] = value
        uploads[value.uploadId] = key
        ReleaseTransitionResult.Applied(value)
    }
    override fun listReleases(identity: String?): List<StoredRelease> = releases.values
        .filter { identity == null || it.record.`package` == identity }
        .sortedWith(compareByDescending<StoredRelease> { it.record.timestamps.reservedAt }.thenBy { it.record.version })
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
            val firestore = FirestoreOptions.newBuilder()
                .setProjectId(config.projectId)
                .setDatabaseId(config.firestoreDatabase)
                .build()
                .service
            return FirestoreRegistryRepository(firestore)
        }
    }
}

data class StoredObject(val bytes: ByteArray, val contentType: String)

interface RegistryObjectStorage {
    fun putQuarantine(uploadId: String, bytes: ByteArray)
    fun getQuarantine(uploadId: String): ByteArray?
    fun deleteQuarantine(uploadId: String)
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
    override fun putQuarantine(uploadId: String, bytes: ByteArray) { quarantine[uploadId] = bytes.copyOf() }
    override fun getQuarantine(uploadId: String): ByteArray? = quarantine[uploadId]?.copyOf()
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
    override fun putQuarantine(uploadId: String, bytes: ByteArray) = put(quarantineBucket, "$prefix/quarantine/$uploadId", bytes, "application/gzip", "no-store")
    override fun getQuarantine(uploadId: String): ByteArray? = get(quarantineBucket, "$prefix/quarantine/$uploadId")
    override fun deleteQuarantine(uploadId: String) { storage.delete(BlobId.of(quarantineBucket, "$prefix/quarantine/$uploadId")) }
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
    }
}

val RegistryJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}
