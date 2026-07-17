package codes.yousef.seen.registry

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions

/**
 * A metadata-bucket-only capability. It deliberately has no quarantine or
 * public-blob bucket names, so an action or maintenance process cannot reach
 * package bytes even if a code path accidentally asks it to.
 */
class GcsMetadataOnlyRegistryObjectStorage internal constructor(
    private val storage: Storage,
    private val metadataBucket: String,
    private val prefix: String,
    private val allowImmutableCreates: Boolean,
    private val allowRootPointerWrite: Boolean,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray): Nothing = unsupportedPackageStorage()
    override fun getQuarantine(uploadId: String): Nothing = unsupportedPackageStorage()
    override fun deleteQuarantine(uploadId: String): Nothing = unsupportedPackageStorage()
    override fun putPublicBlob(digest: String, bytes: ByteArray): Nothing = unsupportedPackageStorage()
    override fun getPublicBlob(digest: String): Nothing = unsupportedPackageStorage()

    override fun putMetadata(filename: String, bytes: ByteArray): Nothing =
        error("Unconditional metadata writes are not available to this runtime")

    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean {
        require(allowImmutableCreates) { "This runtime cannot create immutable metadata" }
        requireVersionedMetadata(filename)
        val info = metadataInfo(filename, immutable = true)
        return try {
            storage.create(info, bytes, Storage.BlobTargetOption.doesNotExist())
            true
        } catch (failure: StorageException) {
            if (failure.code == 412) false else throw failure
        }
    }

    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
        require(filename == ROOT_POINTER && allowRootPointerWrite) {
            "This runtime cannot replace the $filename metadata pointer"
        }
        val blobId = metadataId(filename)
        val info = metadataInfo(filename, immutable = false)
        val precondition = if (expected == null) {
            Storage.BlobTargetOption.doesNotExist()
        } else {
            val current = storage.get(blobId) ?: return false
            if (!current.getContent().contentEquals(expected)) return false
            Storage.BlobTargetOption.generationMatch(requireNotNull(current.generation))
        }
        return try {
            storage.create(info, bytes, precondition)
            true
        } catch (failure: StorageException) {
            if (failure.code == 412) false else throw failure
        }
    }

    override fun getMetadata(filename: String): ByteArray? {
        requireReadableMetadata(filename)
        return storage.get(metadataId(filename))?.getContent()
    }

    private fun metadataId(filename: String): BlobId =
        BlobId.of(metadataBucket, "$prefix/metadata/$filename")

    private fun metadataInfo(filename: String, immutable: Boolean): BlobInfo = BlobInfo.newBuilder(metadataId(filename))
        .setContentType("application/json")
        .setCacheControl(
            if (immutable) "public,max-age=31536000,immutable"
            else "public,max-age=300,must-revalidate",
        )
        .build()

    companion object {
        fun create(
            projectId: String,
            metadataBucket: String,
            prefix: String,
            allowImmutableCreates: Boolean,
            allowRootPointerWrite: Boolean,
        ): GcsMetadataOnlyRegistryObjectStorage = GcsMetadataOnlyRegistryObjectStorage(
            storage = StorageOptions.newBuilder().setProjectId(projectId).build().service,
            metadataBucket = metadataBucket,
            prefix = prefix,
            allowImmutableCreates = allowImmutableCreates,
            allowRootPointerWrite = allowRootPointerWrite,
        )
    }
}

/**
 * Applies the same capability boundary to memory storage. Timestamp pointer
 * writes are an explicit memory-only test convenience; deployed coordinators
 * rely on the isolated timestamp signer to commit that pointer.
 */
class RestrictedMetadataRegistryObjectStorage(
    private val delegate: RegistryObjectStorage = InMemoryRegistryObjectStorage(),
    private val allowImmutableCreates: Boolean,
    private val allowRootPointerWrite: Boolean,
    private val allowTimestampPointerWrite: Boolean = false,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray): Nothing = unsupportedPackageStorage()
    override fun getQuarantine(uploadId: String): Nothing = unsupportedPackageStorage()
    override fun deleteQuarantine(uploadId: String): Nothing = unsupportedPackageStorage()
    override fun putPublicBlob(digest: String, bytes: ByteArray): Nothing = unsupportedPackageStorage()
    override fun getPublicBlob(digest: String): Nothing = unsupportedPackageStorage()

    override fun putMetadata(filename: String, bytes: ByteArray): Nothing =
        error("Unconditional metadata writes are not available to this runtime")

    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean {
        require(allowImmutableCreates) { "This runtime cannot create immutable metadata" }
        requireVersionedMetadata(filename)
        return delegate.putMetadataIfAbsent(filename, bytes)
    }

    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
        val permitted = (filename == ROOT_POINTER && allowRootPointerWrite) ||
            (filename == TIMESTAMP_POINTER && allowTimestampPointerWrite)
        require(permitted) { "This runtime cannot replace the $filename metadata pointer" }
        return delegate.replaceMetadataIfUnchanged(filename, expected, bytes)
    }

    override fun getMetadata(filename: String): ByteArray? {
        requireReadableMetadata(filename)
        return delegate.getMetadata(filename)
    }
}

private fun unsupportedPackageStorage(): Nothing =
    error("Package object storage is not available to this metadata-only runtime")

private fun requireVersionedMetadata(filename: String) {
    require(VERSIONED_METADATA_FILENAME.matches(filename)) {
        "Only immutable versioned TUF metadata can be created"
    }
}

private fun requireReadableMetadata(filename: String) {
    require(filename == ROOT_POINTER || filename == TIMESTAMP_POINTER || VERSIONED_METADATA_FILENAME.matches(filename)) {
        "Unsupported metadata filename"
    }
}

private const val ROOT_POINTER = "root.json"
private const val TIMESTAMP_POINTER = "timestamp.json"
private val VERSIONED_METADATA_FILENAME =
    Regex("^[1-9][0-9]*\\.(root|targets|releases|security|snapshot)\\.json$")
