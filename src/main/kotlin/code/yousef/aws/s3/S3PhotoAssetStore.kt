package code.yousef.aws.s3

import code.yousef.aws.s3.S3Objects.isConditionalConflict
import code.yousef.aws.s3.S3Objects.isNotFound
import code.yousef.aws.s3.S3Objects.isPreconditionFailed
import code.yousef.portfolio.photography.PhotoAsset
import code.yousef.portfolio.photography.PhotoAssetStore
import code.yousef.portfolio.photography.parseContentAddressedPhotoKey
import code.yousef.portfolio.photography.requirePhotoAssetContentType
import code.yousef.portfolio.photography.sha256Hex
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Content-addressed photography media in S3, with the same key contract as the R2 store:
 * `sha256/<digest>/<photoId>.<ext>`. The object key is `PORTFOLIO_S3_MEDIA_PREFIX` + that
 * storage key (no prefix by default, matching the R2 bucket layout), so CloudFront can
 * serve `/cdn/media/<storageKey>` by stripping `/cdn/media/`.
 *
 * Writes are conditional creates (`If-None-Match: *`) carrying an S3-verified SHA-256
 * checksum, so an object can never be replaced and S3 rejects corrupted uploads.
 */
class S3PhotoAssetStore(
    private val client: S3Client,
    private val bucket: String,
    prefix: String? = null,
    private val maxAssetBytes: Long,
) : PhotoAssetStore {
    private val prefix = S3Objects.normalizePrefix(prefix)

    init {
        require(BUCKET.matches(bucket)) { "Invalid S3 media bucket name" }
        require(maxAssetBytes in 1..Int.MAX_VALUE.toLong() - 1) { "Invalid maximum photography asset size" }
    }

    override fun keyFor(photoId: String, extension: String): String =
        error("S3 photography keys require asset bytes")

    override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String {
        require(PHOTO_ID.matches(photoId)) { "Invalid photography media id" }
        val normalizedExtension = extension.lowercase()
        require(PHOTO_EXTENSION.matches(normalizedExtension)) { "Invalid photography media extension" }
        return "sha256/${sha256Hex(bytes)}/$photoId.$normalizedExtension"
    }

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        val key = requireKey(storageKey)
        val normalizedContentType = requirePhotoAssetContentType(contentType)
        require(bytes.size.toLong() <= maxAssetBytes) { "Photography asset exceeds configured maximum size" }
        require(sha256Hex(bytes) == key.digest) { "Photography asset bytes do not match storage-key digest" }
        repeat(2) { attempt ->
            try {
                client.putObject(
                    { request ->
                        request.bucket(bucket)
                            .key(prefix + key.key)
                            .contentType(normalizedContentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .metadata(mapOf("sha256" to key.digest, "contenttype" to normalizedContentType))
                            .checksumSHA256(S3Objects.checksumSha256(key.digest))
                            .ifNoneMatch("*")
                    },
                    RequestBody.fromBytes(bytes),
                )
                return
            } catch (failure: S3Exception) {
                when {
                    failure.isPreconditionFailed() -> {
                        requireExistingMatches(key.key, key.digest, normalizedContentType)
                        return
                    }
                    failure.isConditionalConflict() && attempt == 0 -> Unit // concurrent create; retry once
                    else -> throw failure
                }
            }
        }
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? {
        val key = requireKey(storageKey)
        val expectedContentType = requirePhotoAssetContentType(contentType)
        val response = try {
            client.getObject { it.bucket(bucket).key(prefix + key.key) }
        } catch (_: NoSuchKeyException) {
            return null
        } catch (failure: S3Exception) {
            if (failure.isNotFound()) return null
            throw failure
        }
        val actualContentType = response.response().contentType()?.substringBefore(';')?.trim()?.lowercase()
        val bytes = S3Objects.readBounded(response, maxAssetBytes)
        require(sha256Hex(bytes) == key.digest) { "S3 photography object failed digest verification" }
        require(actualContentType == expectedContentType) {
            "S3 photography object content type did not match the authoritative record"
        }
        return PhotoAsset(bytes, expectedContentType)
    }

    override fun delete(storageKey: String) {
        val key = requireKey(storageKey)
        client.deleteObject { it.bucket(bucket).key(prefix + key.key) }
    }

    private fun requireExistingMatches(storageKey: String, digest: String, contentType: String) {
        val head = client.headObject { it.bucket(bucket).key(prefix + storageKey) }
        require(head.metadata()["sha256"] == digest && head.contentType()?.substringBefore(';')?.trim() == contentType) {
            "Immutable photography object already exists with different metadata"
        }
    }

    private fun requireKey(storageKey: String) = requireNotNull(parseContentAddressedPhotoKey(storageKey)) {
        "S3 photography storage key must be an immutable sha256 key"
    }

    private companion object {
        val BUCKET = Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")
        val PHOTO_ID = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
        val PHOTO_EXTENSION = Regex("^[a-z0-9]{1,10}$")
    }
}
