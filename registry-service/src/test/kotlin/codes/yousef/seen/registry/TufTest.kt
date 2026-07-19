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
import kotlin.test.assertNull

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
    fun `production bootstrap imports only with the exact production identity`() {
        val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)
        val online = testOnlineSigners()
        val rootKeys = (1..3).map(::testSigner)
        val targetKeys = (4..5).map(::testSigner)
        val fixture = TufBootstrapper(
            InMemoryRegistryObjectStorage(), rootKeys.map(TufSigner::publicKey), rootKeys,
            targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
            "production", "seen-prod-registry-v1", clock,
        ).bootstrap()
        val importedStorage = InMemoryRegistryObjectStorage()

        TufBootstrapImporter(
            importedStorage, online.publicKeys(), "production", "seen-prod-registry-v1",
            "https://seen.yousef.codes/packages", clock,
        ).import(fixture.root, fixture.targets)

        assertContentEquals(fixture.root, importedStorage.getMetadata("1.root.json"))
        assertContentEquals(fixture.targets, importedStorage.getMetadata("1.targets.json"))

        assertFailsWith<IllegalArgumentException> {
            TufBootstrapImporter(
                InMemoryRegistryObjectStorage(), online.publicKeys(), "production", "seen-dev-registry-v1",
                "https://seen.yousef.codes/packages", clock,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TufBootstrapImporter(
                InMemoryRegistryObjectStorage(), online.publicKeys(), "production", "seen-prod-registry-v1",
                "https://seen.dev.yousef.codes/packages", clock,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TufBootstrapImporter(
                InMemoryRegistryObjectStorage(), online.publicKeys(), "staging", "seen-prod-registry-v1",
                "https://seen.yousef.codes/packages", clock,
            )
        }
    }

    @Test
    fun `offline import uses conditional writes only`() {
        val fixtureStorage = InMemoryRegistryObjectStorage()
        val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)
        val online = testOnlineSigners()
        val rootKeys = (1..3).map(::testSigner)
        val targetKeys = (4..5).map(::testSigner)
        val fixture = TufBootstrapper(
            fixtureStorage, rootKeys.map(TufSigner::publicKey), rootKeys,
            targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
            "development", "seen-dev-registry-v1", clock,
        ).bootstrap()
        val backing = InMemoryRegistryObjectStorage()
        val conditionalStorage = object : RegistryObjectStorage by backing {
            override fun putMetadata(filename: String, bytes: ByteArray): Nothing =
                error("Bootstrap must not use an unconditional metadata write for $filename")
        }

        TufBootstrapImporter(
            conditionalStorage, online.publicKeys(), "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ).import(fixture.root, fixture.targets)

        assertContentEquals(fixture.root, backing.getMetadata("1.root.json"))
        assertContentEquals(fixture.targets, backing.getMetadata("1.targets.json"))
        assertContentEquals(fixture.root, backing.getMetadata("root.json"))
    }

    @Test
    fun `byte-identical partial bootstrap resumes and finishes online metadata`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val online = testOnlineSigners()
        val rootKeys = (1..3).map(::testSigner)
        val targetKeys = (4..5).map(::testSigner)
        val fixture = TufBootstrapper(
            InMemoryRegistryObjectStorage(), rootKeys.map(TufSigner::publicKey), rootKeys,
            targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
            "development", "seen-dev-registry-v1", clock,
        ).bootstrap()
        val storage = InMemoryRegistryObjectStorage().apply {
            putMetadata("1.root.json", fixture.root)
            putMetadata("root.json", fixture.root)
        }

        val resumed = TufBootstrapImporter(
            storage, online.publicKeys(), "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ).import(fixture.root, fixture.targets)
        assertContentEquals(fixture.targets, storage.getMetadata("1.targets.json"))
        assertContentEquals(fixture.root, resumed.root)

        val publisher = TufPublisher(
            InMemoryRegistryRepository(), storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )
        assertEquals(1, publisher.ensureInitialTransaction())
        assertTrue(storage.getMetadata("timestamp.json") != null)
    }

    @Test
    fun `conflicting partial bootstrap fails without exposing root pointer`() {
        val fixtureStorage = InMemoryRegistryObjectStorage()
        val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)
        val online = testOnlineSigners()
        val rootKeys = (1..3).map(::testSigner)
        val targetKeys = (4..5).map(::testSigner)
        val fixture = TufBootstrapper(
            fixtureStorage, rootKeys.map(TufSigner::publicKey), rootKeys,
            targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
            "development", "seen-dev-registry-v1", clock,
        ).bootstrap()
        val storage = InMemoryRegistryObjectStorage().apply {
            putMetadata("1.targets.json", "conflicting-targets".encodeToByteArray())
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            TufBootstrapImporter(
                storage, online.publicKeys(), "development", "seen-dev-registry-v1",
                "https://seen.dev.yousef.codes/packages", clock,
            ).import(fixture.root, fixture.targets)
        }

        assertTrue(failure.message.orEmpty().contains("Existing 1.targets.json differs"))
        assertNull(storage.getMetadata("root.json"))

        val pointerConflict = "conflicting-root-pointer".encodeToByteArray()
        val pointerStorage = InMemoryRegistryObjectStorage().apply {
            putMetadata("root.json", pointerConflict)
        }
        val pointerFailure = assertFailsWith<IllegalArgumentException> {
            TufBootstrapImporter(
                pointerStorage, online.publicKeys(), "development", "seen-dev-registry-v1",
                "https://seen.dev.yousef.codes/packages", clock,
            ).import(fixture.root, fixture.targets)
        }
        assertTrue(pointerFailure.message.orEmpty().contains("Existing root.json differs"))
        assertContentEquals(pointerConflict, pointerStorage.getMetadata("root.json"))
    }

    @Test
    fun `immutable bootstrap root alone is not a published trust pointer`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val online = testOnlineSigners()
        val rootKeys = (1..3).map(::testSigner)
        val targetKeys = (4..5).map(::testSigner)
        val fixture = TufBootstrapper(
            InMemoryRegistryObjectStorage(), rootKeys.map(TufSigner::publicKey), rootKeys,
            targetKeys.map(TufSigner::publicKey), targetKeys, online.publicKeys(),
            "development", "seen-dev-registry-v1", clock,
        ).bootstrap()
        val storage = InMemoryRegistryObjectStorage().apply {
            putMetadata("1.root.json", fixture.root)
        }
        val publisher = TufPublisher(
            InMemoryRegistryRepository(), storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        assertFailsWith<IllegalStateException> { publisher.requireBootstrap() }
    }

    @Test
    fun `offline preparation writes canonical envelopes using public-only online material`() {
        val directory = Files.createTempDirectory("seen-offline-bootstrap-test")
        val online = testOnlineSigners().publicKeys()
        val config = OfflineBootstrapConfig(
            environment = "development",
            repositoryId = "seen-dev-registry-v1",
            rootPublicKeys = (1..3).map(::testSigner).map(TufSigner::publicKey),
            rootSigningKeysPkcs8Base64 = listOf(testPkcs8(1), testPkcs8(2)),
            targetsPublicKeys = (4..5).map(::testSigner).map(TufSigner::publicKey),
            targetsSigningKeysPkcs8Base64 = listOf(testPkcs8(4), testPkcs8(5)),
            onlineKeys = online,
        )
        val clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC)
        val result = prepareOfflineBootstrap(directory, config, clock)
        assertContentEquals(result.root, Files.readAllBytes(directory.resolve("1.root.json")))
        assertContentEquals(result.targets, Files.readAllBytes(directory.resolve("1.targets.json")))
        assertContentEquals(result.root, Files.readAllBytes(directory.resolve("root.json")))

        Files.delete(directory.resolve("1.targets.json"))
        val resumed = prepareOfflineBootstrap(directory, config, clock)
        assertContentEquals(result.root, resumed.root)
        assertContentEquals(result.targets, Files.readAllBytes(directory.resolve("1.targets.json")))
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
    fun `timestamp signer failure leaves only immutable staged metadata`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val backing = InMemoryRegistryObjectStorage()
        val timestampKey = testSigner(40)
        val online = TufOnlineSigners(
            testSigner(10),
            testSigner(20),
            testSigner(30),
            object : TufSigner {
                override val publicKey: ByteArray = timestampKey.publicKey
                override fun sign(canonicalSignedBytes: ByteArray): ByteArray =
                    error("timestamp signer is unavailable")
            },
        )
        bootstrap(backing, online, clock)
        val writes = mutableListOf<String>()
        val storage = object : RegistryObjectStorage by backing {
            override fun putMetadata(filename: String, bytes: ByteArray) {
                writes += "put:$filename"
                backing.putMetadata(filename, bytes)
            }

            override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean {
                writes += "create:$filename"
                return backing.putMetadataIfAbsent(filename, bytes)
            }

            override fun replaceMetadataIfUnchanged(
                filename: String,
                expected: ByteArray?,
                bytes: ByteArray,
            ): Boolean {
                writes += "replace:$filename"
                return backing.replaceMetadataIfUnchanged(filename, expected, bytes)
            }
        }
        val publisher = TufPublisher(
            repository, storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        val failure = assertFailsWith<IllegalStateException> { publisher.publish(emptyList()) }

        assertEquals("timestamp signer is unavailable", failure.message)
        assertEquals(
            listOf(
                "create:1.releases.json",
                "create:1.security.json",
                "create:1.snapshot.json",
            ),
            writes,
        )
        assertTrue(backing.getMetadata("1.releases.json") != null)
        assertTrue(backing.getMetadata("1.security.json") != null)
        assertTrue(backing.getMetadata("1.snapshot.json") != null)
        assertNull(backing.getMetadata("timestamp.json"))
    }

    @Test
    fun `byte-identical staged online transaction can be retried and committed`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repositoryState = InMemoryRegistryRepository()
        val repository = object : RegistryRepository by repositoryState {
            override fun nextMetadataVersion(): Long = 1L
        }
        val backing = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        bootstrap(backing, online, clock)
        var rejectFirstTimestamp = true
        var committed = false
        val postCommitReads = mutableSetOf<String>()
        val storage = object : RegistryObjectStorage by backing {
            override fun replaceMetadataIfUnchanged(
                filename: String,
                expected: ByteArray?,
                bytes: ByteArray,
            ): Boolean {
                if (filename == "timestamp.json" && rejectFirstTimestamp) {
                    rejectFirstTimestamp = false
                    return false
                }
                return backing.replaceMetadataIfUnchanged(filename, expected, bytes).also { replaced ->
                    if (filename == "timestamp.json" && replaced) committed = true
                }
            }

            override fun getMetadata(filename: String): ByteArray? {
                if (committed) postCommitReads += filename
                return backing.getMetadata(filename)
            }
        }
        val publisher = TufPublisher(
            repository, storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        val first = assertFailsWith<RegistryException> { publisher.publish(emptyList()) }
        assertEquals("temporarily_unavailable", first.code)
        assertNull(backing.getMetadata("timestamp.json"))
        val stagedReleases = backing.getMetadata("1.releases.json")!!
        val stagedSecurity = backing.getMetadata("1.security.json")!!
        val stagedSnapshot = backing.getMetadata("1.snapshot.json")!!

        assertEquals(1L, publisher.publish(emptyList()))
        assertContentEquals(stagedReleases, backing.getMetadata("1.releases.json"))
        assertContentEquals(stagedSecurity, backing.getMetadata("1.security.json"))
        assertContentEquals(stagedSnapshot, backing.getMetadata("1.snapshot.json"))
        assertEquals(1L, signed(backing.getMetadata("timestamp.json")!!)["version"]!!.jsonPrimitive.content.toLong())
        assertTrue(postCommitReads.containsAll(setOf(
            "root.json",
            "timestamp.json",
            "1.targets.json",
            "1.releases.json",
            "1.security.json",
            "1.snapshot.json",
        )))
    }

    @Test
    fun `conflicting immutable online metadata is rejected before timestamp commit`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repositoryState = InMemoryRegistryRepository()
        val repository = object : RegistryRepository by repositoryState {
            override fun nextMetadataVersion(): Long = 1L
        }
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        bootstrap(storage, online, clock)
        val collision = "conflicting releases metadata".encodeToByteArray()
        storage.putMetadata("1.releases.json", collision)
        val publisher = TufPublisher(
            repository, storage, online, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        val failure = assertFailsWith<IllegalArgumentException> { publisher.publish(emptyList()) }

        assertTrue(failure.message.orEmpty().contains("Existing 1.releases.json differs"))
        assertContentEquals(collision, storage.getMetadata("1.releases.json"))
        assertNull(storage.getMetadata("1.security.json"))
        assertNull(storage.getMetadata("1.snapshot.json"))
        assertNull(storage.getMetadata("timestamp.json"))
    }

    @Test
    fun `public key only runtime verifies a fresh chain without signing`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val authority = testOnlineSigners()
        bootstrap(storage, authority, clock)
        TufPublisher(
            repository, storage, authority, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ).ensureInitialTransaction()
        val verificationOnly = authority.publicKeys().let { keys ->
            TufOnlineSigners(
                PublicKeyOnlyTufSigner(keys.releases),
                PublicKeyOnlyTufSigner(keys.security),
                PublicKeyOnlyTufSigner(keys.snapshot),
                PublicKeyOnlyTufSigner(keys.timestamp),
            )
        }
        val verifier = TufPublisher(
            repository, storage, verificationOnly, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        assertEquals(1, verifier.verifyFreshTransaction())
        clock.advance(Duration.ofHours(6).plusSeconds(1))
        val failure = assertFailsWith<RegistryException> { verifier.verifyFreshTransaction() }
        assertEquals("temporarily_unavailable", failure.code)
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
    fun `promotion authority reuses security metadata without its signing key`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val authority = testOnlineSigners()
        bootstrap(storage, authority, clock)
        TufPublisher(
            repository, storage, authority, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ).ensureInitialTransaction()
        val promoter = TufOnlineSigners(
            authority.releases,
            PublicKeyOnlyTufSigner(authority.security.publicKey),
            authority.snapshot,
            authority.timestamp,
        )
        val publisher = TufPublisher(
            repository, storage, promoter, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        )

        assertEquals(2, publisher.publish(emptyList()))
        assertNull(storage.getMetadata("2.security.json"))
        val snapshot = signed(storage.getMetadata("2.snapshot.json")!!)
        assertEquals(1, snapshot["meta"]!!.jsonObject["security.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong())

        clock.advance(Duration.ofHours(5).plusMinutes(56))
        val failure = assertFailsWith<RegistryException> { publisher.publish(emptyList()) }
        assertEquals("temporarily_unavailable", failure.code)
    }

    @Test
    fun `dual expiry recovery advances releases first then completes with a strict security refresh`() {
        val fixture = splitAuthorityFixture()
        val initialSecurity = fixture.storage.getMetadata("1.security.json")!!
        fixture.clock.advance(Duration.ofDays(8))

        assertFailsWith<RegistryException> { fixture.releases.forceRefreshReleases() }
        assertFailsWith<RegistryException> { fixture.security.forceRefreshSecurity() }
        assertEquals(1L, signed(fixture.storage.getMetadata("timestamp.json")!!)["version"]!!.jsonPrimitive.content.toLong())

        val recoveryVersion = fixture.releases.recoverExpiredReleases()
        val recoveredReleases = fixture.storage.getMetadata("$recoveryVersion.releases.json")!!
        val recoveryMeta = signed(fixture.storage.getMetadata("$recoveryVersion.snapshot.json")!!)["meta"]!!.jsonObject
        assertReferenced(recoveryMeta["releases.json"]!!.jsonObject, recoveryVersion, recoveredReleases)
        assertReferenced(recoveryMeta["security.json"]!!.jsonObject, 1, initialSecurity)
        assertContentEquals(initialSecurity, fixture.storage.getMetadata("1.security.json"))
        assertNull(fixture.storage.getMetadata("$recoveryVersion.security.json"))

        val intermediate = assertFailsWith<RegistryException> { fixture.releases.verifyFreshTransaction() }
        assertEquals("temporarily_unavailable", intermediate.code)
        assertFailsWith<IllegalArgumentException> { fixture.security.recoverExpiredSecurity() }

        val completedVersion = fixture.security.forceRefreshSecurity()
        val completedSecurity = fixture.storage.getMetadata("$completedVersion.security.json")!!
        val completedMeta = signed(fixture.storage.getMetadata("$completedVersion.snapshot.json")!!)["meta"]!!.jsonObject
        assertReferenced(completedMeta["releases.json"]!!.jsonObject, recoveryVersion, recoveredReleases)
        assertReferenced(completedMeta["security.json"]!!.jsonObject, completedVersion, completedSecurity)
        assertNull(fixture.storage.getMetadata("$completedVersion.releases.json"))
        assertEquals(completedVersion, fixture.security.verifyFreshTransaction())
    }

    @Test
    fun `dual expiry recovery also supports security first and rejects fresh or partial expiry`() {
        val guarded = splitAuthorityFixture()
        assertFailsWith<IllegalArgumentException> { guarded.releases.recoverExpiredReleases() }
        assertFailsWith<IllegalArgumentException> { guarded.security.recoverExpiredSecurity() }
        guarded.clock.advance(Duration.ofHours(7))
        assertFailsWith<IllegalArgumentException> { guarded.releases.recoverExpiredReleases() }
        assertFailsWith<IllegalArgumentException> { guarded.security.recoverExpiredSecurity() }

        val fixture = splitAuthorityFixture()
        val initialReleases = fixture.storage.getMetadata("1.releases.json")!!
        fixture.clock.advance(Duration.ofDays(8))

        val recoveryVersion = fixture.security.recoverExpiredSecurity()
        val recoveredSecurity = fixture.storage.getMetadata("$recoveryVersion.security.json")!!
        val recoveryMeta = signed(fixture.storage.getMetadata("$recoveryVersion.snapshot.json")!!)["meta"]!!.jsonObject
        assertReferenced(recoveryMeta["releases.json"]!!.jsonObject, 1, initialReleases)
        assertReferenced(recoveryMeta["security.json"]!!.jsonObject, recoveryVersion, recoveredSecurity)
        assertContentEquals(initialReleases, fixture.storage.getMetadata("1.releases.json"))
        assertNull(fixture.storage.getMetadata("$recoveryVersion.releases.json"))
        assertFailsWith<RegistryException> { fixture.security.verifyFreshTransaction() }
        assertFailsWith<IllegalArgumentException> { fixture.releases.recoverExpiredReleases() }

        val completedVersion = fixture.releases.forceRefreshReleases()
        val completedReleases = fixture.storage.getMetadata("$completedVersion.releases.json")!!
        val completedMeta = signed(fixture.storage.getMetadata("$completedVersion.snapshot.json")!!)["meta"]!!.jsonObject
        assertReferenced(completedMeta["releases.json"]!!.jsonObject, completedVersion, completedReleases)
        assertReferenced(completedMeta["security.json"]!!.jsonObject, recoveryVersion, recoveredSecurity)
        assertNull(fixture.storage.getMetadata("$completedVersion.security.json"))
        assertEquals(completedVersion, fixture.releases.verifyFreshTransaction())
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

private data class SplitAuthorityFixture(
    val clock: MutableClock,
    val storage: InMemoryRegistryObjectStorage,
    val releases: TufPublisher,
    val security: TufPublisher,
)

private fun splitAuthorityFixture(): SplitAuthorityFixture {
    val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
    val repository = InMemoryRegistryRepository()
    val storage = InMemoryRegistryObjectStorage()
    val authority = testOnlineSigners()
    bootstrap(storage, authority, clock)
    TufPublisher(
        repository, storage, authority, "development", "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages", clock,
    ).ensureInitialTransaction()
    val releases = TufPublisher(
        repository,
        storage,
        TufOnlineSigners(
            authority.releases,
            PublicKeyOnlyTufSigner(authority.security.publicKey),
            authority.snapshot,
            authority.timestamp,
        ),
        "development",
        "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages",
        clock,
    )
    val security = TufPublisher(
        repository,
        storage,
        TufOnlineSigners(
            PublicKeyOnlyTufSigner(authority.releases.publicKey),
            authority.security,
            authority.snapshot,
            authority.timestamp,
        ),
        "development",
        "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages",
        clock,
    )
    return SplitAuthorityFixture(clock, storage, releases, security)
}

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
