package codes.yousef.seen.registry

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TufTest {
    @Test
    fun `canonical json sorts keys and local signature verifies`() {
        val value = RegistryJson.parseToJsonElement("""{"z":1,"a":"x","nested":{"b":false,"a":null}}""")
        assertEquals("""{"a":"x","nested":{"a":null,"b":false},"z":1}""", canonicalJson(value).decodeToString())
        val signer = LocalEd25519Signer.fromSeed(ByteArray(32) { it.toByte() })
        val bytes = canonicalJson(value)
        val signature = signer.sign(bytes)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(signer.publicKey))
            update(bytes, 0, bytes.size)
        }
        assertTrue(verifier.verifySignature(signature))
    }

    @Test
    fun `bootstrap stores threshold root and targets with distinct online roles`() {
        val storage = InMemoryRegistryObjectStorage()
        val rootKeys = (1..3).map(::testSigner)
        val rootSigners = rootKeys.take(2)
        val targetSigners = (4..5).map(::testSigner)
        val online = testOnlineSigners()
        val result = TufBootstrapper(
            storage, rootKeys.map(TufSigner::publicKey), rootSigners,
            targetSigners.map(TufSigner::publicKey), targetSigners, online.publicKeys(),
            "development", "seen-dev-registry-v1",
            Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC),
        ).bootstrap()
        assertContentEquals(result.root, storage.getMetadata("1.root.json"))
        assertContentEquals(result.targets, storage.getMetadata("1.targets.json"))
        val envelope = RegistryJson.parseToJsonElement(result.root.decodeToString()).jsonObject
        assertEquals("root", envelope["signed"]!!.jsonObject["_type"]!!.jsonPrimitive.content)
        assertEquals(2, envelope["signatures"]!!.jsonArray.size)
        assertEquals(2, envelope["signed"]!!.jsonObject["roles"]!!.jsonObject["root"]!!.jsonObject["threshold"]!!.jsonPrimitive.content.toInt())
        val targets = RegistryJson.parseToJsonElement(result.targets.decodeToString()).jsonObject
        assertEquals(2, targets["signatures"]!!.jsonArray.size)
        assertEquals(listOf("security", "releases"), targets["signed"]!!.jsonObject["delegations"]!!.jsonObject["roles"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })

        val importedStorage = InMemoryRegistryObjectStorage()
        val imported = TufBootstrapImporter(
            importedStorage, online.publicKeys(), "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages",
            Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC),
        ).import(result.root, result.targets)
        assertContentEquals(result.root, imported.root)
        assertContentEquals(result.targets, importedStorage.getMetadata("1.targets.json"))
    }

    @Test
    fun `offline preparation writes canonical envelopes using public-only online material`() {
        val directory = Files.createTempDirectory("seen-offline-bootstrap-test")
        val online = testOnlineSigners().publicKeys()
        val result = prepareOfflineBootstrap(
            directory,
            OfflineBootstrapConfig(
                environment = "development",
                repositoryId = "seen-dev-registry-v1",
                rootPublicKeys = (1..3).map(::testSigner).map(TufSigner::publicKey),
                rootSigningKeysPkcs8Base64 = listOf(testPkcs8(1), testPkcs8(2)),
                targetsPublicKeys = (4..5).map(::testSigner).map(TufSigner::publicKey),
                targetsSigningKeysPkcs8Base64 = listOf(testPkcs8(4), testPkcs8(5)),
                onlineKeys = online,
            ),
            Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC),
        )
        assertContentEquals(result.root, Files.readAllBytes(directory.resolve("1.root.json")))
        assertContentEquals(result.targets, Files.readAllBytes(directory.resolve("1.targets.json")))
        assertContentEquals(result.root, Files.readAllBytes(directory.resolve("root.json")))
    }

    @Test
    fun `bootstrap publishes one complete empty online transaction`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        bootstrap(storage, online, clock)
        val publisher = TufPublisher(
            repository, storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        assertEquals(1, publisher.ensureInitialTransaction())
        val firstTimestamp = storage.getMetadata("timestamp.json")!!
        assertEquals(1, publisher.ensureInitialTransaction())
        assertContentEquals(firstTimestamp, storage.getMetadata("timestamp.json"))

        val timestamp = signed(firstTimestamp)
        val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
        val snapshotBytes = storage.getMetadata("$snapshotVersion.snapshot.json")!!
        assertReferenced(timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject, snapshotVersion, snapshotBytes)
        val snapshot = signed(snapshotBytes)
        listOf("releases", "security").forEach { role ->
            val meta = snapshot["meta"]!!.jsonObject["$role.json"]!!.jsonObject
            val version = meta["version"]!!.jsonPrimitive.content.toLong()
            val bytes = storage.getMetadata("$version.$role.json")!!
            assertReferenced(meta, version, bytes)
            assertEquals(emptySet(), signed(bytes)["targets"]!!.jsonObject.keys)
        }
    }

    @Test
    fun `freshness renewal survives restart and refreshes before online expiry`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        bootstrap(storage, online, clock)
        fun publisher() = TufPublisher(
            repository, storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        assertEquals(1, publisher().ensureFreshTransaction())
        clock.advance(Duration.ofHours(3))
        assertEquals(1, publisher().ensureFreshTransaction())
        clock.advance(Duration.ofMinutes(61))
        assertEquals(2, publisher().ensureFreshTransaction())
        val renewed = storage.getMetadata("timestamp.json")!!
        assertEquals(2, signed(renewed)["version"]!!.jsonPrimitive.content.toLong())

        assertEquals(2, publisher().ensureFreshTransaction())
        clock.advance(Duration.ofHours(2).plusMinutes(30))
        assertEquals(2, publisher().ensureFreshTransaction())
        assertEquals("2026-07-16T10:01:00Z", signed(renewed)["expires"]!!.jsonPrimitive.content)
    }

    @Test
    fun `expired publisher is fenced from replacing a newer timestamp transaction`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val backing = InMemoryRegistryObjectStorage()
        val bootstrapOnline = testOnlineSigners()
        bootstrap(backing, bootstrapOnline, clock)
        TufPublisher(
            repository, backing, bootstrapOnline, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ).ensureInitialTransaction()

        val compareCalls = AtomicInteger()
        val staleCommitEntered = CountDownLatch(1)
        val releaseStaleCommit = CountDownLatch(1)
        val storage = object : RegistryObjectStorage by backing {
            override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
                if (filename == "timestamp.json" && compareCalls.incrementAndGet() == 1) {
                    staleCommitEntered.countDown()
                    check(releaseStaleCommit.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release stale publication" }
                }
                return backing.replaceMetadataIfUnchanged(filename, expected, bytes)
            }
        }
        fun publisher() = TufPublisher(
            repository, storage, testOnlineSigners(), "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        val executor = Executors.newSingleThreadExecutor()
        try {
            val stale = executor.submit<Long> { publisher().publish(emptyList()) }
            assertTrue(staleCommitEntered.await(5, TimeUnit.SECONDS), "Stale publisher never reached timestamp commit")

            clock.advance(Duration.ofMinutes(3))
            assertEquals(3L, publisher().publish(emptyList()))
            assertEquals(3L, signed(backing.getMetadata("timestamp.json")!!)["version"]!!.jsonPrimitive.content.toLong())

            releaseStaleCommit.countDown()
            val failure = assertFailsWith<ExecutionException> { stale.get(5, TimeUnit.SECONDS) }
            assertTrue(failure.cause is RegistryException)
            assertEquals(3L, signed(backing.getMetadata("timestamp.json")!!)["version"]!!.jsonPrimitive.content.toLong())
        } finally {
            releaseStaleCommit.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `expired offline targets or root fail readiness and online refresh`() {
        val expiredTargets = publisherFixture()
        expiredTargets.clock.advance(Duration.ofDays(31))
        val targetsService = RegistryService(
            testConfig(), expiredTargets.repository, expiredTargets.storage, ArchiveValidator(),
            expiredTargets.publisher, expiredTargets.clock,
        )
        assertFalse(targetsService.isReady())
        val targetsFailure = assertFailsWith<IllegalArgumentException> {
            expiredTargets.publisher.ensureFreshTransaction()
        }
        assertTrue(targetsFailure.message.orEmpty().contains("targets metadata is expired"))

        val expiredRoot = publisherFixture()
        expiredRoot.clock.advance(Duration.ofDays(366))
        val rootService = RegistryService(
            testConfig(), expiredRoot.repository, expiredRoot.storage, ArchiveValidator(),
            expiredRoot.publisher, expiredRoot.clock,
        )
        assertFalse(rootService.isReady())
        val rootFailure = assertFailsWith<IllegalArgumentException> {
            expiredRoot.publisher.ensureFreshTransaction()
        }
        assertTrue(rootFailure.message.orEmpty().contains("root metadata is expired"))
    }
}

private data class PublisherFixture(
    val clock: MutableClock,
    val repository: InMemoryRegistryRepository,
    val storage: InMemoryRegistryObjectStorage,
    val publisher: TufPublisher,
)

private fun publisherFixture(): PublisherFixture {
    val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
    val repository = InMemoryRegistryRepository()
    val storage = InMemoryRegistryObjectStorage()
    val online = testOnlineSigners()
    bootstrap(storage, online, clock)
    val publisher = TufPublisher(
        repository, storage, online, "development", "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages", clock,
    )
    publisher.ensureInitialTransaction()
    return PublisherFixture(clock, repository, storage, publisher)
}

private fun bootstrap(storage: InMemoryRegistryObjectStorage, online: TufOnlineSigners, clock: Clock) {
    val rootKeys = (1..3).map(::testSigner)
    val targetKeys = (4..5).map(::testSigner)
    TufBootstrapper(
        storage, rootKeys.map(TufSigner::publicKey), rootKeys,
        targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
        "development", "seen-dev-registry-v1", clock,
    ).bootstrap()
}

private fun signed(bytes: ByteArray) = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signed"]!!.jsonObject

private fun assertReferenced(meta: kotlinx.serialization.json.JsonObject, version: Long, bytes: ByteArray) {
    assertEquals(version, meta["version"]!!.jsonPrimitive.content.toLong())
    assertEquals(bytes.size.toLong(), meta["length"]!!.jsonPrimitive.content.toLong())
    assertEquals(sha256(bytes), meta["hashes"]!!.jsonObject["sha256"]!!.jsonPrimitive.content)
}

internal fun testSigner(seedByte: Int): LocalEd25519Signer = LocalEd25519Signer.fromSeed(ByteArray(32) { (it + seedByte).toByte() })

internal fun testOnlineSigners() = TufOnlineSigners(testSigner(10), testSigner(20), testSigner(30), testSigner(40))

private fun testPkcs8(seedByte: Int): String {
    val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + seedByte).toByte() })
    return Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey).encoded)
}
