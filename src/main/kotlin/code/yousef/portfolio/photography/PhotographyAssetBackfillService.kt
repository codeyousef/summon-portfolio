package code.yousef.portfolio.photography

internal data class PhotographyAssetBackfillRequest(
    val photoId: String,
    val sourceStorageKey: String,
    val contentType: String,
) {
    init {
        require(photoId.isNotBlank() && photoId.none(Char::isISOControl)) { "invalid photography id" }
        require(sourceStorageKey.isNotBlank() && sourceStorageKey.none(Char::isISOControl)) {
            "invalid photography source key"
        }
        require(contentType.isNotBlank() && contentType.none(Char::isISOControl)) {
            "invalid photography content type"
        }
    }
}

internal data class PhotographyAssetBackfillReceipt(
    val photoId: String,
    val sourceStorageKey: String,
    val contentAddressedStorageKey: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
)

internal class PhotographyAssetBackfillException(
    val stageCode: String,
    cause: Throwable,
) : IllegalArgumentException("photography staging failed at $stageCode", cause)

/**
 * Stages immutable media copies without changing Firestore authority.
 *
 * The content-addressed GCS copy is written first so source-mode reads remain
 * possible after a later document-key migration. R2 is written second through
 * its conditional-create transport. Both copies are reloaded and byte-checked
 * before a receipt is returned. The legacy source key is never deleted here.
 */
internal class PhotographyAssetBackfillService(
    private val source: PhotoAssetStore,
    private val target: PhotoAssetStore,
) {
    fun stage(request: PhotographyAssetBackfillRequest): PhotographyAssetBackfillReceipt {
        val sourceAsset = atStage("source-read") {
            requireNotNull(source.load(request.sourceStorageKey, request.contentType)) {
                "photography source asset is missing"
            }
        }
        val normalizedContentType = atStage("source-validate") {
            request.contentType.substringBefore(';').trim().lowercase().also { expected ->
                require(sourceAsset.contentType.substringBefore(';').trim().lowercase() == expected) {
                    "photography source content type does not match Firestore"
                }
            }
        }
        val (targetKey, parsedKey) = atStage("key-derive") {
            val filename = request.sourceStorageKey.substringAfterLast('/')
            val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
            require(extension.isNotBlank()) { "photography source key has no extension" }
            val key = target.keyFor(request.photoId, extension, sourceAsset.bytes)
            val parsed = requireNotNull(parseContentAddressedPhotoKey(key)) {
                "photography target did not produce a content-addressed key"
            }
            require(parsed.digest == sha256Hex(sourceAsset.bytes)) {
                "photography target key digest does not match source bytes"
            }
            key to parsed
        }
        val sourceSafetyKey = request.sourceStorageKey.substringBeforeLast('/') + "/" + targetKey

        atStage("source-write") { source.save(sourceSafetyKey, normalizedContentType, sourceAsset.bytes) }
        atStage("source-verify") {
            verifyCopy(source, sourceSafetyKey, normalizedContentType, sourceAsset.bytes, "source")
        }
        atStage("target-write") { target.save(targetKey, normalizedContentType, sourceAsset.bytes) }
        atStage("target-verify") { verifyCopy(target, targetKey, normalizedContentType, sourceAsset.bytes, "target") }

        return PhotographyAssetBackfillReceipt(
            photoId = request.photoId,
            sourceStorageKey = request.sourceStorageKey,
            contentAddressedStorageKey = targetKey,
            sha256 = parsedKey.digest,
            sizeBytes = sourceAsset.bytes.size.toLong(),
            contentType = normalizedContentType,
        )
    }

    private inline fun <T> atStage(stageCode: String, operation: () -> T): T = try {
        operation()
    } catch (failure: PhotographyAssetBackfillException) {
        throw failure
    } catch (failure: Throwable) {
        throw PhotographyAssetBackfillException(stageCode, failure)
    }

    private fun verifyCopy(
        store: PhotoAssetStore,
        storageKey: String,
        contentType: String,
        expectedBytes: ByteArray,
        role: String,
    ) {
        val stored = requireNotNull(store.load(storageKey, contentType)) {
            "photography $role copy is missing after write"
        }
        require(stored.contentType.substringBefore(';').trim().lowercase() == contentType) {
            "photography $role copy content type changed"
        }
        require(stored.bytes.contentEquals(expectedBytes) && sha256Hex(stored.bytes) == sha256Hex(expectedBytes)) {
            "photography $role copy failed byte verification"
        }
    }
}
