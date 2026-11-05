package code.yousef.aws.s3

import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory S3 with the semantics the adapters depend on: conditional create
 * (`If-None-Match: *` -> 412), SHA-256 checksum verification (-> 400), ETags and
 * conditional GET/HEAD (`If-None-Match` -> 304), and NoSuchKey/404 for missing keys.
 */
class FakeS3Client : S3Client {
    data class StoredObject(
        val bytes: ByteArray,
        val contentType: String?,
        val metadata: Map<String, String>,
        val cacheControl: String?,
        val contentDisposition: String?,
        val eTag: String,
    )

    val objects = ConcurrentHashMap<Pair<String, String>, StoredObject>()
    val putRequests = mutableListOf<PutObjectRequest>()

    override fun serviceName(): String = "s3"
    override fun close() = Unit

    override fun putObject(request: PutObjectRequest, body: RequestBody): PutObjectResponse {
        putRequests += request
        val bytes = body.contentStreamProvider().newStream().use { it.readAllBytes() }
        request.checksumSHA256()?.let { expected ->
            val actual = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
            if (actual != expected) throw error(400, "BadDigest")
        }
        val key = request.bucket() to request.key()
        val eTag = "\"" + MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } + "\""
        val stored = StoredObject(bytes, request.contentType(), request.metadata(), request.cacheControl(), request.contentDisposition(), eTag)
        if (request.ifNoneMatch() == "*") {
            if (objects.putIfAbsent(key, stored) != null) throw error(412, "PreconditionFailed")
        } else {
            objects[key] = stored
        }
        return PutObjectResponse.builder().eTag(eTag).build()
    }

    override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> {
        val stored = objects[request.bucket() to request.key()] ?: throw NoSuchKeyException.builder().statusCode(404).message("missing").build()
        if (request.ifNoneMatch() != null && request.ifNoneMatch() == stored.eTag) throw error(304, "NotModified")
        val response = GetObjectResponse.builder()
            .contentType(stored.contentType)
            .contentLength(stored.bytes.size.toLong())
            .metadata(stored.metadata)
            .eTag(stored.eTag)
            .build()
        return ResponseInputStream(response, AbortableInputStream.create(ByteArrayInputStream(stored.bytes)))
    }

    override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
        val stored = objects[request.bucket() to request.key()] ?: throw NoSuchKeyException.builder().statusCode(404).message("missing").build()
        if (request.ifNoneMatch() != null && request.ifNoneMatch() == stored.eTag) throw error(304, "NotModified")
        return HeadObjectResponse.builder()
            .contentType(stored.contentType)
            .contentLength(stored.bytes.size.toLong())
            .metadata(stored.metadata)
            .eTag(stored.eTag)
            .build()
    }

    override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse {
        objects.remove(request.bucket() to request.key())
        return DeleteObjectResponse.builder().build()
    }

    private fun error(status: Int, code: String): S3Exception = S3Exception.builder()
        .statusCode(status)
        .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
        .message(code)
        .build() as S3Exception
}
