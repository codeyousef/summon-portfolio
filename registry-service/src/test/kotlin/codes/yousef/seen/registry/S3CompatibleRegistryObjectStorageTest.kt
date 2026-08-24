package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class S3CompatibleRegistryObjectStorageTest {
    @Test
    fun `promotion creates and verifies a content addressed immutable public object`() {
        val client = FakeS3CompatibleObjectClient()
        val storage = storage(client)
        val archive = "reviewed archive".encodeToByteArray()
        val digest = sha256(archive)
        val uploadId = "upl_abcdefghijklmnop"

        storage.putQuarantine(uploadId, archive)
        storage.copyQuarantineToPublic(uploadId, digest, archive.size.toLong())
        storage.copyQuarantineToPublic(uploadId, digest, archive.size.toLong())

        assertContentEquals(archive, storage.getPublicBlob(digest))
        assertTrue(client.contains("seen-public", "v1/blobs/sha256/$digest"))
        assertEquals(
            "public,max-age=31536000,immutable",
            client.get("seen-public", "v1/blobs/sha256/$digest")?.cacheControl,
        )
        assertEquals(1, client.successfulCreates("seen-public", "v1/blobs/sha256/$digest"))
    }

    @Test
    fun `promotion refuses a conflicting immutable public object`() {
        val client = FakeS3CompatibleObjectClient()
        val storage = storage(client)
        val archive = "reviewed archive".encodeToByteArray()
        val digest = sha256(archive)
        val uploadId = "upl_abcdefghijklmnop"
        storage.putQuarantine(uploadId, archive)
        client.seed(
            bucket = "seen-public",
            key = "v1/blobs/sha256/$digest",
            bytes = "conflict".encodeToByteArray(),
            contentType = "application/gzip",
            cacheControl = "public,max-age=31536000,immutable",
        )

        assertFailsWith<IllegalStateException> {
            storage.copyQuarantineToPublic(uploadId, digest, archive.size.toLong())
        }
    }

    @Test
    fun `versioned metadata is create only and mutable pointers use versioned CAS`() {
        val client = FakeS3CompatibleObjectClient()
        val storage = storage(client)
        val versioned = "signed root v1".encodeToByteArray()
        val rootV1 = "root pointer v1".encodeToByteArray()
        val rootV2 = "root pointer v2".encodeToByteArray()

        assertTrue(storage.putMetadataIfAbsent("1.root.json", versioned))
        assertFalse(storage.putMetadataIfAbsent("1.root.json", "different".encodeToByteArray()))
        assertContentEquals(versioned, storage.getMetadata("1.root.json"))
        assertEquals(
            "public,max-age=31536000,immutable",
            client.get("seen-metadata", "v1/metadata/1.root.json")?.cacheControl,
        )

        assertTrue(storage.replaceMetadataIfUnchanged("root.json", null, rootV1))
        assertFalse(storage.replaceMetadataIfUnchanged("root.json", null, rootV2))
        assertFalse(storage.replaceMetadataIfUnchanged("root.json", "stale".encodeToByteArray(), rootV2))
        assertTrue(storage.replaceMetadataIfUnchanged("root.json", rootV1, rootV2))
        assertContentEquals(rootV2, storage.getMetadata("root.json"))
    }

    @Test
    fun `quarantine writes are create only bounded and removable`() {
        val client = FakeS3CompatibleObjectClient()
        val storage = storage(client)
        val uploadId = "upl_abcdefghijklmnop"
        val bytes = "archive".encodeToByteArray()

        storage.putQuarantine(uploadId, bytes)
        storage.putQuarantine(uploadId, bytes)
        assertContentEquals(bytes, storage.getQuarantine(uploadId))
        assertFailsWith<IllegalStateException> {
            storage.putQuarantine(uploadId, "other".encodeToByteArray())
        }

        storage.deleteQuarantine(uploadId)
        assertNull(storage.getQuarantine(uploadId))
    }

    @Test
    fun `timestamp signer metadata uses the same R2 bucket and opaque ETag CAS`() {
        val client = FakeS3CompatibleObjectClient()
        val storage = S3CompatibleTufSignerMetadataStore(
            client = client,
            bucket = "seen-metadata",
            prefix = "v1",
        )
        val root = "signed root".encodeToByteArray()
        val timestampV1 = "signed timestamp v1".encodeToByteArray()
        val timestampV2 = "signed timestamp v2".encodeToByteArray()
        client.seed(
            bucket = "seen-metadata",
            key = "v1/metadata/1.root.json",
            bytes = root,
            contentType = "application/json",
            cacheControl = "public,max-age=31536000,immutable",
        )

        assertContentEquals(root, storage.get("1.root.json")?.bytes)
        assertTrue(storage.commitTimestamp(null, timestampV1))
        val committedV1 = requireNotNull(storage.get("timestamp.json"))
        assertFalse(storage.commitTimestamp(null, timestampV2))
        assertFalse(storage.commitTimestamp("stale-etag", timestampV2))
        assertTrue(storage.commitTimestamp(committedV1.version, timestampV2))
        assertContentEquals(timestampV2, storage.get("timestamp.json")?.bytes)
        assertEquals(
            "public,max-age=300,must-revalidate",
            client.get("seen-metadata", "v1/metadata/timestamp.json")?.cacheControl,
        )
        assertFailsWith<TufSigningRequestException> { storage.commitTimestamp(null, byteArrayOf()) }

        storage.close()
        assertTrue(client.closed)
    }

    private fun storage(client: S3CompatibleObjectClient) = S3CompatibleRegistryObjectStorage(
        client = client,
        buckets = RegistryBucketBindings.of(
            RegistryBucketRole.QUARANTINE to "seen-quarantine",
            RegistryBucketRole.PUBLIC to "seen-public",
            RegistryBucketRole.METADATA to "seen-metadata",
        ),
        prefix = "v1",
    )
}

private class FakeS3CompatibleObjectClient : S3CompatibleObjectClient {
    private data class Entry(
        val bytes: ByteArray,
        val contentType: String,
        val cacheControl: String,
        val version: Long,
    )

    private val values = linkedMapOf<Pair<String, String>, Entry>()
    private val createCounts = mutableMapOf<Pair<String, String>, Int>()
    private var nextVersion = 1L
    var closed: Boolean = false
        private set

    override fun create(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean = synchronized(values) {
        val address = bucket to key
        if (values.containsKey(address)) return@synchronized false
        values[address] = Entry(bytes.copyOf(), contentType, cacheControl, nextVersion++)
        createCounts[address] = createCounts.getOrDefault(address, 0) + 1
        true
    }

    override fun put(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ) = synchronized(values) {
        values[bucket to key] = Entry(bytes.copyOf(), contentType, cacheControl, nextVersion++)
    }

    override fun replace(
        bucket: String,
        key: String,
        expectedVersion: String?,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ): Boolean = synchronized(values) {
        val address = bucket to key
        val current = values[address]
        if (expectedVersion == null && current != null) return@synchronized false
        if (expectedVersion != null && current?.version?.toString() != expectedVersion) return@synchronized false
        values[address] = Entry(bytes.copyOf(), contentType, cacheControl, nextVersion++)
        true
    }

    override fun get(bucket: String, key: String): S3CompatibleStoredObject? = synchronized(values) {
        values[bucket to key]?.let { entry ->
            S3CompatibleStoredObject(
                bytes = entry.bytes.copyOf(),
                contentType = entry.contentType,
                cacheControl = entry.cacheControl,
                version = entry.version.toString(),
                sha256 = sha256(entry.bytes),
            )
        }
    }

    override fun delete(bucket: String, key: String) {
        values.remove(bucket to key)
    }

    override fun close() {
        closed = true
    }

    fun contains(bucket: String, key: String): Boolean = values.containsKey(bucket to key)
    fun successfulCreates(bucket: String, key: String): Int = createCounts.getOrDefault(bucket to key, 0)

    fun seed(
        bucket: String,
        key: String,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String,
    ) {
        values[bucket to key] = Entry(bytes.copyOf(), contentType, cacheControl, nextVersion++)
    }
}
