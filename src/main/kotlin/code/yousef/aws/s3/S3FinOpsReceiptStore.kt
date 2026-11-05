package code.yousef.aws.s3

import code.yousef.aws.s3.S3Objects.isConditionalConflict
import code.yousef.aws.s3.S3Objects.isPreconditionFailed
import code.yousef.portfolio.finops.FinOpsReceiptReference
import code.yousef.portfolio.finops.FinOpsReceiptStore
import code.yousef.portfolio.finops.normalizeFinOpsReceiptContentType
import code.yousef.portfolio.finops.receiptSha256Hex
import code.yousef.portfolio.finops.requireFinOpsReceiptFilename
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.util.UUID

/**
 * Private FinOps receipts in S3 with the R2 receipt key layout
 * `sha256/<digest>/uploads/<uploadId>/<filename>` (prefixed by `PORTFOLIO_S3_RECEIPTS_PREFIX`).
 * Objects are immutable conditional creates with an S3-verified SHA-256 checksum and the
 * same integrity metadata the edge receipt store recorded. The bucket is never public.
 */
class S3FinOpsReceiptStore(
    private val client: S3Client,
    private val bucket: String,
    prefix: String? = null,
    private val maxReceiptBytes: Long,
) : FinOpsReceiptStore {
    private val prefix = S3Objects.normalizePrefix(prefix)

    init {
        require(BUCKET.matches(bucket)) { "Invalid S3 receipts bucket name" }
        require(maxReceiptBytes in 1..26_214_400L) { "FINOPS_RECEIPT_MAX_BYTES cannot exceed 25 MiB" }
    }

    override fun save(filename: String, contentType: String, bytes: ByteArray, uploadId: String?): FinOpsReceiptReference {
        val safeFilename = requireFinOpsReceiptFilename(filename)
        require(safeFilename == filename) { "invalid receipt filename" }
        val normalizedContentType = normalizeFinOpsReceiptContentType(contentType)
        require(bytes.isNotEmpty() && bytes.size.toLong() <= maxReceiptBytes) {
            "receipt must contain between 1 and $maxReceiptBytes bytes"
        }
        val digest = receiptSha256Hex(bytes)
        val resolvedUploadId = uploadId ?: UUID.randomUUID().toString().replace("-", "")
        require(resolvedUploadId.matches(UPLOAD_ID)) { "invalid receipt upload id" }
        val reference = FinOpsReceiptReference(
            storageKey = "sha256/$digest/uploads/$resolvedUploadId/$safeFilename",
            sha256 = digest,
            filename = safeFilename,
            contentType = normalizedContentType,
            size = bytes.size.toLong(),
            uploadId = resolvedUploadId,
        )
        repeat(2) { attempt ->
            try {
                client.putObject(
                    { request ->
                        request.bucket(bucket)
                            .key(prefix + reference.storageKey)
                            .contentType(normalizedContentType)
                            .cacheControl("private, no-store")
                            .contentDisposition("attachment; filename=\"$safeFilename\"")
                            .metadata(
                                mapOf(
                                    "sha256" to digest,
                                    "contenttype" to normalizedContentType,
                                    "uploadid" to resolvedUploadId,
                                    "lifecycle" to "pending",
                                ),
                            )
                            .checksumSHA256(S3Objects.checksumSha256(digest))
                            .ifNoneMatch("*")
                    },
                    RequestBody.fromBytes(bytes),
                )
                return reference
            } catch (failure: S3Exception) {
                when {
                    failure.isPreconditionFailed() -> {
                        require(head(reference)?.matches(reference) == true) {
                            "Immutable receipt key already exists with different metadata"
                        }
                        return reference
                    }
                    failure.isConditionalConflict() && attempt == 0 -> Unit
                    else -> throw failure
                }
            }
        }
        error("FinOps receipt write remained conflicted")
    }

    override fun load(reference: FinOpsReceiptReference): ByteArray {
        val response = try {
            client.getObject { it.bucket(bucket).key(prefix + requireReferenceKey(reference)) }
        } catch (_: NoSuchKeyException) {
            throw NoSuchElementException("receipt not found")
        }
        require(response.response().matches(reference)) { "Stored receipt metadata failed integrity validation" }
        val bytes = S3Objects.readBounded(response, maxReceiptBytes)
        require(bytes.size.toLong() == reference.size) { "FinOps receipt response size did not match its reference" }
        require(receiptSha256Hex(bytes) == reference.sha256) { "FinOps receipt response failed digest verification" }
        return bytes
    }

    override fun requirePresent(reference: FinOpsReceiptReference) {
        val head = requireNotNull(head(reference)) { "FinOps receipt verification failed: not found" }
        require(head.matches(reference)) { "FinOps receipt metadata did not match its reference" }
    }

    private fun head(reference: FinOpsReceiptReference): HeadObjectResponse? = try {
        client.headObject { it.bucket(bucket).key(prefix + requireReferenceKey(reference)) }
    } catch (_: NoSuchKeyException) {
        null
    } catch (failure: S3Exception) {
        if (failure.statusCode() == 404) null else throw failure
    }

    private fun HeadObjectResponse.matches(reference: FinOpsReceiptReference): Boolean =
        contentLength() == reference.size &&
            metadata()["sha256"] == reference.sha256 &&
            metadata()["contenttype"] == reference.contentType &&
            contentType()?.substringBefore(';')?.trim()?.lowercase() == reference.contentType

    private fun software.amazon.awssdk.services.s3.model.GetObjectResponse.matches(reference: FinOpsReceiptReference): Boolean =
        contentLength() == reference.size &&
            metadata()["sha256"] == reference.sha256 &&
            contentType()?.substringBefore(';')?.trim()?.lowercase() == reference.contentType

    private fun requireReferenceKey(reference: FinOpsReceiptReference): String {
        require(RECEIPT_KEY.matches(reference.storageKey) && reference.storageKey.startsWith("sha256/${reference.sha256}/")) {
            "invalid receipt storage key"
        }
        return reference.storageKey
    }

    private companion object {
        val BUCKET = Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")
        val UPLOAD_ID = Regex("^[a-f0-9]{32}$")
        val RECEIPT_KEY = Regex("^sha256/[a-f0-9]{64}/(?:uploads/[a-f0-9]{32}/)?[A-Za-z0-9][A-Za-z0-9._-]{0,191}$")
    }
}
