package codes.yousef.seen.registry

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.time.Instant

/**
 * Narrows the production catalog's repository capability to the reads used by
 * public package, release, and provenance routes.
 */
class ReadOnlyRegistryRepository internal constructor(
    private val delegate: RegistryRepository,
) : RegistryRepository {
    override fun createPackage(value: StoredPackage): Nothing = rejectRepositoryMutation()
    override fun getPackage(identity: String): StoredPackage? = delegate.getPackage(identity)
        ?.let { publicProjection(it, publicReleases(identity)) }
    override fun savePackage(value: StoredPackage): Nothing = rejectRepositoryMutation()
    override fun listPackages(): List<StoredPackage> {
        val releasesByPackage = publicReleases().groupBy { it.record.`package` }
        return delegate.listPackages().mapNotNull { stored ->
            publicProjection(stored, releasesByPackage[stored.record.identity].orEmpty())
        }
    }
    override fun reserveRelease(value: StoredRelease): Nothing = rejectRepositoryMutation()
    override fun getRelease(identity: String, version: String): StoredRelease? =
        delegate.getRelease(identity, version)?.takeIf(StoredRelease::isPubliclyReadable)

    override fun findReleaseByUpload(uploadId: String): Nothing = rejectPrivateLookup()
    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): Nothing =
        rejectRepositoryMutation()

    override fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): Nothing = rejectRepositoryMutation()

    override fun listReleases(identity: String?): List<StoredRelease> = publicReleases(identity)
    override fun beginIdempotency(value: StoredIdempotency, now: Instant): Nothing = rejectRepositoryMutation()
    override fun completeIdempotency(
        scope: String,
        fingerprint: String,
        attemptId: String,
        response: StoredIdempotencyResponse,
    ): Nothing = rejectRepositoryMutation()

    override fun appendReviewArtifact(value: ReviewArtifact): Nothing = rejectRepositoryMutation()
    override fun getReviewArtifact(artifactId: String): ReviewArtifact? = delegate.getReviewArtifact(artifactId)
        ?.takeIf(::isPublicArtifact)
    override fun listReviewArtifacts(
        packageIdentity: String?,
        version: String?,
        kind: String?,
    ): List<ReviewArtifact> = delegate.listReviewArtifacts(packageIdentity, version, kind)
        .filter(::isPublicArtifact)

    override fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Nothing =
        rejectRepositoryMutation()

    override fun releaseMetadataPublication(holder: String): Nothing = rejectRepositoryMutation()
    override fun nextMetadataVersion(): Nothing = rejectRepositoryMutation()
    override fun close() = delegate.close()

    private fun publicReleases(identity: String? = null): List<StoredRelease> =
        delegate.listReleases(identity).filter(StoredRelease::isPubliclyReadable)

    private fun publicProjection(
        stored: StoredPackage,
        releases: List<StoredRelease>,
    ): StoredPackage? {
        val latest = releases.maxWithOrNull(
            compareBy<StoredRelease> {
                Instant.parse(requireNotNull(it.record.timestamps.activatedAt))
            }.thenBy { it.record.version },
        ) ?: return null
        return stored.copy(record = stored.record.copy(latestActiveVersion = latest.record.version))
    }

    private fun isPublicArtifact(artifact: ReviewArtifact): Boolean {
        val packageIdentity = artifact.packageIdentity ?: return false
        val version = artifact.version ?: return false
        return delegate.getRelease(packageIdentity, version)?.isPubliclyReadable() == true
    }
}

/**
 * Mutation-disabled full-state capability used only to verify that signed TUF
 * metadata matches durable registry state. Unlike the public catalog view, it
 * must retain quarantined releases and their evidence so deny metadata can be
 * validated without exposing those records through HTTP routes.
 */
internal class TufVerificationRegistryRepository(
    private val delegate: RegistryRepository,
) : RegistryRepository {
    override fun createPackage(value: StoredPackage): Nothing = rejectRepositoryMutation()
    override fun getPackage(identity: String): StoredPackage? = delegate.getPackage(identity)
    override fun savePackage(value: StoredPackage): Nothing = rejectRepositoryMutation()
    override fun listPackages(): List<StoredPackage> = delegate.listPackages()
    override fun reserveRelease(value: StoredRelease): Nothing = rejectRepositoryMutation()
    override fun getRelease(identity: String, version: String): StoredRelease? = delegate.getRelease(identity, version)
    override fun findReleaseByUpload(uploadId: String): Nothing = rejectPrivateLookup()
    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): Nothing = rejectRepositoryMutation()
    override fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): Nothing = rejectRepositoryMutation()

    override fun listReleases(identity: String?): List<StoredRelease> = delegate.listReleases(identity)
    override fun beginIdempotency(value: StoredIdempotency, now: Instant): Nothing = rejectRepositoryMutation()
    override fun completeIdempotency(
        scope: String,
        fingerprint: String,
        attemptId: String,
        response: StoredIdempotencyResponse,
    ): Nothing = rejectRepositoryMutation()

    override fun appendReviewArtifact(value: ReviewArtifact): Nothing = rejectRepositoryMutation()
    override fun getReviewArtifact(artifactId: String): ReviewArtifact? = delegate.getReviewArtifact(artifactId)
    override fun listReviewArtifacts(
        packageIdentity: String?,
        version: String?,
        kind: String?,
    ): List<ReviewArtifact> = delegate.listReviewArtifacts(packageIdentity, version, kind)

    override fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Nothing =
        rejectRepositoryMutation()

    override fun releaseMetadataPublication(holder: String): Nothing = rejectRepositoryMutation()
    override fun nextMetadataVersion(): Nothing = rejectRepositoryMutation()
}

/** Read-only package-byte and metadata capability used by memory-mode tests. */
class ReadOnlyRegistryObjectStorage internal constructor(
    private val delegate: RegistryObjectStorage,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun getQuarantine(uploadId: String): Nothing = rejectPrivateObjectAccess()
    override fun deleteQuarantine(uploadId: String): Nothing = rejectObjectMutation()
    override fun putPublicBlob(digest: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun getPublicBlob(digest: String): ByteArray? = delegate.getPublicBlob(digest)
    override fun putMetadata(filename: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Nothing =
        rejectObjectMutation()

    override fun getMetadata(filename: String): ByteArray? = delegate.getMetadata(filename)
}

/**
 * GCS reader that is configured with only the public-blob and metadata bucket
 * names. It has no quarantine bucket identifier and implements no write path.
 */
class GcsReadOnlyRegistryObjectStorage internal constructor(
    private val storage: Storage,
    private val publicBucket: String,
    private val metadataBucket: String,
    private val prefix: String,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun getQuarantine(uploadId: String): Nothing = rejectPrivateObjectAccess()
    override fun deleteQuarantine(uploadId: String): Nothing = rejectObjectMutation()
    override fun putPublicBlob(digest: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun getPublicBlob(digest: String): ByteArray? {
        IdentityRules.requireDigest(digest)
        return storage.get(BlobId.of(publicBucket, "$prefix/blobs/sha256/$digest"))?.getContent()
    }

    override fun putMetadata(filename: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Nothing = rejectObjectMutation()
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Nothing =
        rejectObjectMutation()

    override fun getMetadata(filename: String): ByteArray? {
        requireReadableMetadata(filename)
        return storage.get(BlobId.of(metadataBucket, "$prefix/metadata/$filename"))?.getContent()
    }

    companion object {
        fun create(config: RegistryConfig): GcsReadOnlyRegistryObjectStorage =
            GcsReadOnlyRegistryObjectStorage(
                storage = StorageOptions.newBuilder()
                    .setProjectId(requireNotNull(config.projectId))
                    .build()
                    .service,
                publicBucket = requireNotNull(config.publicBucket),
                metadataBucket = requireNotNull(config.metadataBucket),
                prefix = config.objectPrefix,
            )
    }
}

private fun rejectRepositoryMutation(): Nothing =
    error("Repository mutation is unavailable to the read-only registry")

private fun rejectPrivateLookup(): Nothing =
    error("Private release lookup is unavailable to the read-only registry")

private fun rejectObjectMutation(): Nothing =
    error("Object mutation is unavailable to the read-only registry")

private fun rejectPrivateObjectAccess(): Nothing =
    error("Quarantine objects are unavailable to the read-only registry")

private fun StoredRelease.isPubliclyReadable(): Boolean =
    record.state.lifecycle == "active" &&
        record.state.visibility == "public" &&
        record.state.availability in setOf("available", "yanked")
