package code.yousef.portfolio.photography

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
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
    prefix: String
) : PhotoAssetStore {
    private val storage = StorageOptions.getDefaultInstance().service
    private val normalizedPrefix = prefix.trim('/').takeIf { it.isNotEmpty() }

    override fun keyFor(photoId: String, extension: String): String =
        listOfNotNull(normalizedPrefix, "$photoId.$extension").joinToString("/")

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, storageKey))
            .setContentType(contentType)
            .build()
        storage.create(blobInfo, bytes)
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? {
        val blob = storage.get(BlobId.of(bucket, storageKey)) ?: return null
        return PhotoAsset(bytes = blob.getContent(), contentType = blob.contentType ?: contentType)
    }

    override fun delete(storageKey: String) {
        storage.delete(BlobId.of(bucket, storageKey))
    }
}
