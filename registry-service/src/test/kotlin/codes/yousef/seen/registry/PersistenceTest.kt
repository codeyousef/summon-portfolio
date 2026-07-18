package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PersistenceTest {
    @Test
    fun `promotion rejects a quarantine object whose size differs from the release`() {
        val storage = InMemoryRegistryObjectStorage()
        val bytes = "reviewed archive".encodeToByteArray()
        val digest = sha256(bytes)
        storage.putQuarantine("upl_size_mismatch", bytes)

        assertFailsWith<IllegalStateException> {
            storage.copyQuarantineToPublic("upl_size_mismatch", digest, bytes.size.toLong() + 1)
        }

        assertNull(storage.getPublicBlob(digest))
    }

    @Test
    fun `promotion rejects a quarantine object whose digest differs from the release`() {
        val storage = InMemoryRegistryObjectStorage()
        val bytes = "reviewed archive".encodeToByteArray()
        val releaseDigest = sha256("different archive".encodeToByteArray())
        storage.putQuarantine("upl_digest_mismatch", bytes)

        assertFailsWith<IllegalStateException> {
            storage.copyQuarantineToPublic("upl_digest_mismatch", releaseDigest, bytes.size.toLong())
        }

        assertNull(storage.getPublicBlob(releaseDigest))
    }

    @Test
    fun `promotion rejects a preexisting public object unless it is exact`() {
        val storage = InMemoryRegistryObjectStorage()
        val bytes = "reviewed archive".encodeToByteArray()
        val digest = sha256(bytes)
        val conflicting = "wrong public object".encodeToByteArray()
        storage.putQuarantine("upl_public_conflict", bytes)
        storage.putPublicBlob(digest, conflicting)

        assertFailsWith<IllegalStateException> {
            storage.copyQuarantineToPublic("upl_public_conflict", digest, bytes.size.toLong())
        }

        assertContentEquals(conflicting, storage.getPublicBlob(digest))
    }

    @Test
    fun `promotion is idempotent when the preexisting public object is exact`() {
        val storage = InMemoryRegistryObjectStorage()
        val bytes = "reviewed archive".encodeToByteArray()
        val digest = sha256(bytes)
        storage.putQuarantine("upl_public_exact", bytes)
        storage.putPublicBlob(digest, bytes)

        storage.copyQuarantineToPublic("upl_public_exact", digest, bytes.size.toLong())

        assertContentEquals(bytes, storage.getPublicBlob(digest))
    }
}
