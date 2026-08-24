package code.yousef.portfolio.photography

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.google.auth.oauth2.GoogleCredentials
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class PhotoAsset(
    val bytes: ByteArray,
    val contentType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoAsset) return false
        return bytes.contentEquals(other.bytes) && contentType == other.contentType
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
}

interface PhotoAssetStore {
    fun keyFor(photoId: String, extension: String): String
    fun keyFor(photoId: String, extension: String, bytes: ByteArray): String = keyFor(photoId, extension)
    fun save(storageKey: String, contentType: String, bytes: ByteArray)
    fun load(storageKey: String, contentType: String): PhotoAsset?
    fun delete(storageKey: String)
}

class LocalPhotoAssetStore(
    private val root: Path
) : PhotoAssetStore {

    override fun keyFor(photoId: String, extension: String): String = "$photoId.$extension"

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        val path = resolveStorageKey(storageKey)
        path.parent?.createDirectories()
        path.outputStream().use { it.write(bytes) }
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? {
        val path = resolveStorageKey(storageKey)
        if (!path.exists() || !Files.isRegularFile(path)) return null
        return path.inputStream().use { PhotoAsset(bytes = it.readBytes(), contentType = contentType) }
    }

    override fun delete(storageKey: String) {
        Files.deleteIfExists(resolveStorageKey(storageKey))
    }

    private fun resolveStorageKey(storageKey: String): Path {
        val normalized = storageKey.replace('\\', '/').trimStart('/')
        require(!normalized.contains("..")) { "Invalid storage key" }
        val path = root.resolve(normalized).normalize()
        require(path.startsWith(root.normalize())) { "Invalid storage key" }
        return path
    }
}

class GcsPhotoAssetStore(
    private val bucket: String,
    prefix: String,
    credentials: GoogleCredentials? = null,
    projectId: String? = null,
) : PhotoAssetStore {
    private val storage = StorageOptions.newBuilder()
        .also { builder ->
            credentials?.let(builder::setCredentials)
            projectId?.let(builder::setProjectId)
        }
        .build()
        .service
    private val normalizedPrefix = prefix.trim('/').takeIf { it.isNotEmpty() }

    override fun keyFor(photoId: String, extension: String): String =
        listOfNotNull(normalizedPrefix, "$photoId.$extension").joinToString("/")

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        val objectKey = gcsCandidateKeys(normalizedPrefix, storageKey).last()
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey))
            .setContentType(contentType)
            .build()
        storage.create(blobInfo, bytes)
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? {
        val blob = candidateKeys(storageKey)
            .firstNotNullOfOrNull { key -> storage.get(BlobId.of(bucket, key)) }
            ?: return null
        return PhotoAsset(bytes = blob.getContent(), contentType = blob.contentType ?: contentType)
    }

    override fun delete(storageKey: String) {
        gcsCandidateKeys(normalizedPrefix, storageKey).forEach { key ->
            storage.delete(BlobId.of(bucket, key))
        }
    }

    private fun candidateKeys(storageKey: String): List<String> {
        return gcsCandidateKeys(normalizedPrefix, storageKey)
    }
}

internal fun gcsCandidateKeys(prefix: String?, storageKey: String): List<String> {
    val normalized = storageKey.trim('/')
    require(normalized.isNotBlank() && !normalized.split('/').any { it == ".." }) { "Invalid GCS storage key" }
    require(prefix == null || (prefix.isNotBlank() && !prefix.split('/').any { it == ".." })) {
        "Invalid GCS storage prefix"
    }
    val prefixed = prefix
        ?.takeIf { !normalized.startsWith("$it/") }
        ?.let { "$it/$normalized" }
    return listOfNotNull(normalized, prefixed).distinct()
}
