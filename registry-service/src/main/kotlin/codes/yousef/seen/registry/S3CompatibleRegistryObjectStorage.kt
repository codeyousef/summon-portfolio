package codes.yousef.seen.registry

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.time.Duration

internal data class S3CompatibleStoredObject(
    val bytes: ByteArray,
    val contentType: String?,
    val cacheControl: String?,
    val version: String,
    val sha256: String?,
)

/** Small boundary around the AWS SDK so object semantics can be tested offline. */
internal interface S3CompatibleObjectClient : AutoCloseable {
    fun create(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean

    fun put(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    )

    fun replace(
        bucket: String,
        key: String,
        expectedVersion: String?,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean

    fun get(bucket: String, key: String): S3CompatibleStoredObject?
    fun delete(bucket: String, key: String)

    /** Every key under [prefix]; used only by the data-copy parity report. */
    fun keys(bucket: String, prefix: String): List<String> =
        throw UnsupportedOperationException("Object listing is not supported by this client")
}

/**
 * Registry object storage over Cloudflare R2's S3-compatible endpoint or
 * native AWS S3.
 *
 * Both providers honour `If-None-Match: *` (create-only) and `If-Match: <etag>`
 * (compare-and-set) on PutObject, which are the only write primitives used for
 * immutable and pointer objects respectively. On AWS S3 the infrastructure
 * must additionally enable bucket versioning on the public and metadata
 * buckets so an operator-level overwrite or delete stays recoverable; see the
 * registry-service AWS operations notes.
 *
 * Public archives use SHA-256-addressed immutable keys. Versioned TUF objects
 * remain on their protocol-defined filenames and are create-only; root and
 * timestamp pointers retain their existing byte-checked compare-and-set
 * behavior.
 */
class S3CompatibleRegistryObjectStorage internal constructor(
    private val client: S3CompatibleObjectClient,
    private val buckets: RegistryBucketBindings,
    private val prefix: String,
) : RegistryObjectStorage {
    override fun putQuarantine(uploadId: String, bytes: ByteArray) {
        require(bytes.size.toLong() <= ArchivePolicy.MAX_COMPRESSED_BYTES) {
            "Quarantine object exceeds archive policy"
        }
        putQuarantineBytes(uploadId, bytes)
    }

    override fun putQuarantine(uploadId: String, source: ReopenableArchiveSource) {
        val bytes = source.openStream().use(::readBoundedArchive)
        putQuarantineBytes(uploadId, bytes)
    }

    private fun putQuarantineBytes(uploadId: String, bytes: ByteArray) {
        val bucket = buckets.require(RegistryBucketRole.QUARANTINE)
        val key = RegistryObjectKeys.quarantineUpload(prefix, uploadId)
        createExact(
            bucket = bucket,
            key = key,
            bytes = bytes,
            contentType = ARCHIVE_CONTENT_TYPE,
            cacheControl = QUARANTINE_CACHE_CONTROL,
            description = "Quarantine object",
        )
    }

    override fun openQuarantine(uploadId: String): InputStream? =
        getObject(RegistryBucketRole.QUARANTINE, RegistryObjectKeys.quarantineUpload(prefix, uploadId))
            ?.bytes
            ?.let(::ByteArrayInputStream)

    override fun getQuarantine(uploadId: String): ByteArray? = openQuarantine(uploadId)?.use(InputStream::readAllBytes)

    override fun deleteQuarantine(uploadId: String) {
        client.delete(
            buckets.require(RegistryBucketRole.QUARANTINE),
            RegistryObjectKeys.quarantineUpload(prefix, uploadId),
        )
    }

    override fun copyQuarantineToPublic(uploadId: String, digest: String, expectedBytes: Long) {
        require(expectedBytes in 0..ArchivePolicy.MAX_COMPRESSED_BYTES) {
            "Expected archive size is outside archive policy"
        }
        IdentityRules.requireDigest(digest, "archive digest")
        val quarantine = getObject(
            RegistryBucketRole.QUARANTINE,
            RegistryObjectKeys.quarantineUpload(prefix, uploadId),
        ) ?: throw FileNotFoundException("Quarantine object is missing")
        verifyObjectMetadata(
            quarantine,
            ARCHIVE_CONTENT_TYPE,
            QUARANTINE_CACHE_CONTROL,
            "Quarantine object",
        )
        verifyStoredObject(
            quarantine.bytes.inputStream(),
            quarantine.bytes.size.toLong(),
            expectedBytes,
            digest,
            "Quarantine object",
        )

        putPublicBlob(digest, quarantine.bytes)
        val public = publicObject(digest)
            ?: throw FileNotFoundException("Public object is missing after promotion")
        verifyPublicObject(public, expectedBytes, digest)
    }

    override fun putPublicBlob(digest: String, bytes: ByteArray) {
        IdentityRules.requireDigest(digest, "archive digest")
        require(sha256(bytes) == digest) { "Public object bytes do not match their content-addressed key" }
        createExact(
            bucket = buckets.require(RegistryBucketRole.PUBLIC),
            key = RegistryObjectKeys.contentAddressed(prefix, RegistryBucketRole.PUBLIC, digest),
            bytes = bytes,
            contentType = ARCHIVE_CONTENT_TYPE,
            cacheControl = PUBLIC_CACHE_CONTROL,
            description = "Public object",
        )
    }

    override fun getPublicBlob(digest: String): ByteArray? {
        IdentityRules.requireDigest(digest)
        val value = publicObject(digest) ?: return null
        check(value.sha256 == null || value.sha256 == digest) { "Public object digest metadata is invalid" }
        check(sha256(value.bytes) == digest) { "Public object bytes do not match their content-addressed key" }
        return value.bytes.copyOf()
    }

    private fun publicObject(digest: String): S3CompatibleStoredObject? = getObject(
        RegistryBucketRole.PUBLIC,
        RegistryObjectKeys.contentAddressed(prefix, RegistryBucketRole.PUBLIC, digest),
    )

    override fun putMetadata(filename: String, bytes: ByteArray) {
        val key = RegistryObjectKeys.metadata(prefix, filename)
        val cacheControl = metadataCacheControl(filename)
        if (isVersionedMetadata(filename)) {
            createExact(
                bucket = buckets.require(RegistryBucketRole.METADATA),
                key = key,
                bytes = bytes,
                contentType = METADATA_CONTENT_TYPE,
                cacheControl = cacheControl,
                description = "Versioned metadata",
            )
        } else {
            client.put(
                buckets.require(RegistryBucketRole.METADATA),
                key,
                bytes,
                METADATA_CONTENT_TYPE,
                cacheControl,
            )
        }
    }

    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean = client.create(
        bucket = buckets.require(RegistryBucketRole.METADATA),
        key = RegistryObjectKeys.metadata(prefix, filename),
        bytes = bytes,
        contentType = METADATA_CONTENT_TYPE,
        cacheControl = metadataCacheControl(filename),
    )

    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
        val bucket = buckets.require(RegistryBucketRole.METADATA)
        val key = RegistryObjectKeys.metadata(prefix, filename)
        val current = client.get(bucket, key)
        if (expected == null && current != null) return false
        if (expected != null && current?.bytes?.contentEquals(expected) != true) return false
        return client.replace(
            bucket = bucket,
            key = key,
            expectedVersion = current?.version,
            bytes = bytes,
            contentType = METADATA_CONTENT_TYPE,
            cacheControl = metadataCacheControl(filename),
        )
    }

    override fun getMetadata(filename: String): ByteArray? = getObject(
        RegistryBucketRole.METADATA,
        RegistryObjectKeys.metadata(prefix, filename),
    )?.bytes?.copyOf()

    override fun close() = client.close()

    private fun getObject(role: RegistryBucketRole, key: String): S3CompatibleStoredObject? =
        client.get(buckets.require(role), key)

    private fun createExact(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
        description: String,
    ) {
        if (client.create(bucket, key, bytes, contentType, cacheControl)) return
        val existing = client.get(bucket, key) ?: error("$description disappeared after create collision")
        check(existing.bytes.contentEquals(bytes)) { "$description content-address collision" }
        verifyObjectMetadata(existing, contentType, cacheControl, description)
    }

    private fun verifyPublicObject(value: S3CompatibleStoredObject, expectedBytes: Long, digest: String) {
        verifyObjectMetadata(value, ARCHIVE_CONTENT_TYPE, PUBLIC_CACHE_CONTROL, "Public object")
        check(value.sha256 == null || value.sha256 == digest) { "Public object digest metadata is invalid" }
        verifyStoredObject(
            value.bytes.inputStream(),
            value.bytes.size.toLong(),
            expectedBytes,
            digest,
            "Public object",
        )
    }

    companion object {
        fun create(config: RegistryObjectStoreConfig, prefix: String): S3CompatibleRegistryObjectStorage {
            require(config.provider in S3_API_PROVIDERS) {
                "S3-compatible storage requires the R2 or S3 provider"
            }
            return S3CompatibleRegistryObjectStorage(
                client = AwsSdkS3CompatibleObjectClient.create(config),
                buckets = config.buckets,
                prefix = prefix,
            )
        }
    }
}

internal class AwsSdkS3CompatibleObjectClient private constructor(
    private val client: S3Client,
) : S3CompatibleObjectClient {
    override fun create(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean = conditionalPut(bucket, key, bytes, contentType, cacheControl, "If-None-Match", "*")

    override fun put(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ) {
        client.putObject(putRequest(bucket, key, bytes, contentType, cacheControl), RequestBody.fromBytes(bytes))
    }

    override fun replace(
        bucket: String,
        key: String,
        expectedVersion: String?,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean = conditionalPut(
        bucket = bucket,
        key = key,
        bytes = bytes,
        contentType = contentType,
        cacheControl = cacheControl,
        conditionName = if (expectedVersion == null) "If-None-Match" else "If-Match",
        conditionValue = expectedVersion ?: "*",
    )

    private fun conditionalPut(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
        conditionName: String,
        conditionValue: String,
    ): Boolean = try {
        val request = putRequest(bucket, key, bytes, contentType, cacheControl)
            .toBuilder()
            .overrideConfiguration { override -> override.putHeader(conditionName, conditionValue) }
            .build()
        client.putObject(request, RequestBody.fromBytes(bytes))
        true
    } catch (failure: S3Exception) {
        if (failure.statusCode() == 412) false else throw failure
    }

    override fun get(bucket: String, key: String): S3CompatibleStoredObject? = try {
        val value = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
        val response = value.response()
        S3CompatibleStoredObject(
            bytes = value.asByteArray(),
            contentType = response.contentType(),
            cacheControl = response.cacheControl(),
            version = requireNotNull(response.eTag()) { "Object ETag is missing" },
            sha256 = response.metadata()[SHA256_METADATA],
        )
    } catch (_: NoSuchKeyException) {
        null
    } catch (failure: S3Exception) {
        if (failure.statusCode() == 404) null else throw failure
    }

    override fun delete(bucket: String, key: String) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    override fun keys(bucket: String, prefix: String): List<String> = client.listObjectsV2Paginator(
        ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build(),
    ).contents().map { it.key() }.sorted()

    override fun close() = client.close()

    private fun putRequest(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): PutObjectRequest = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .contentLength(bytes.size.toLong())
        .contentType(contentType)
        .cacheControl(cacheControl)
        .metadata(mapOf(SHA256_METADATA to sha256(bytes)))
        .build()

    companion object {
        fun create(
            config: RegistryObjectStoreConfig,
            requestTimeout: Duration? = null,
            disableRetries: Boolean = false,
        ): AwsSdkS3CompatibleObjectClient {
            require(config.provider in S3_API_PROVIDERS)
            val httpClient = UrlConnectionHttpClient.builder().apply {
                requestTimeout?.let { timeout ->
                    connectionTimeout(timeout)
                    socketTimeout(timeout)
                }
            }
            val builder = S3Client.builder()
                .region(Region.of(requireNotNull(config.region)))
                .httpClientBuilder(httpClient)
            when (config.provider) {
                RegistryObjectStoreProvider.R2 -> {
                    val credentials = requireNotNull(config.credentials)
                    builder
                        .endpointOverride(requireNotNull(config.endpoint))
                        .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(credentials.accessKeyId, credentials.secretAccessKey),
                        ))
                        .forcePathStyle(true)
                }
                RegistryObjectStoreProvider.S3 -> {
                    // Native S3 never receives static keys: the ECS task role
                    // (container credential provider) is the only authority.
                    require(config.credentials == null && config.endpoint == null)
                    builder.credentialsProvider(DefaultCredentialsProvider.builder().build())
                }
                RegistryObjectStoreProvider.GCS -> error("GCS is not an S3 API provider")
            }
            if (requestTimeout != null || disableRetries) {
                builder.overrideConfiguration { override ->
                    requestTimeout?.let { timeout ->
                        override.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout)
                    }
                    if (disableRetries) override.retryStrategy(DefaultRetryStrategy.doNotRetry())
                }
            }
            return AwsSdkS3CompatibleObjectClient(builder.build())
        }
    }
}

internal val S3_API_PROVIDERS: Set<RegistryObjectStoreProvider> =
    setOf(RegistryObjectStoreProvider.R2, RegistryObjectStoreProvider.S3)

private fun readBoundedArchive(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        require(total <= ArchivePolicy.MAX_COMPRESSED_BYTES) { "Quarantine object exceeds archive policy" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun verifyObjectMetadata(
    value: S3CompatibleStoredObject,
    expectedContentType: String,
    expectedCacheControl: String,
    description: String,
) {
    check(value.contentType == expectedContentType) { "$description content type is not promotion-safe" }
    check(value.cacheControl == expectedCacheControl) { "$description cache policy is not promotion-safe" }
}

private fun metadataCacheControl(filename: String): String =
    if (isVersionedMetadata(filename)) IMMUTABLE_METADATA_CACHE_CONTROL else MUTABLE_METADATA_CACHE_CONTROL

private const val ARCHIVE_CONTENT_TYPE = "application/gzip"
private const val METADATA_CONTENT_TYPE = "application/json"
private const val QUARANTINE_CACHE_CONTROL = "no-store"
private const val PUBLIC_CACHE_CONTROL = "public,max-age=31536000,immutable"
private const val IMMUTABLE_METADATA_CACHE_CONTROL = "public,max-age=31536000,immutable"
private const val MUTABLE_METADATA_CACHE_CONTROL = "public,max-age=300,must-revalidate"
private const val SHA256_METADATA = "sha256"
