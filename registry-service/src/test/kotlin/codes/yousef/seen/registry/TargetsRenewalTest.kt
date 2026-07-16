package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TargetsRenewalTest {
    @Test
    fun `renewals are sequential replay safe and remain current across online publications`() {
        val fixture = renewalFixture()
        fixture.clock.advance(Duration.ofDays(1))
        val versionOne = fixture.storage.getMetadata("1.targets.json")!!
        val versionTwo = fixture.prepare(versionOne, fixture.clock)
        fixture.clock.advance(Duration.ofDays(1))
        val versionThree = fixture.prepare(versionTwo.targets, fixture.clock)

        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(versionThree.targets)
        }
        assertNull(fixture.storage.getMetadata("3.targets.json"))

        val importedTwo = fixture.publisher.importTargetsRenewal(versionTwo.targets)
        assertEquals(2, importedTwo.targetsVersion)
        assertContentEquals(versionTwo.targets, fixture.storage.getMetadata("2.targets.json"))
        assertEquals(2, referencedTargetsVersion(fixture.storage))
        assertCompleteOnlineTransaction(fixture.storage)

        val timestampAfterImport = fixture.storage.getMetadata("timestamp.json")!!
        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(versionTwo.targets)
        }
        assertContentEquals(timestampAfterImport, fixture.storage.getMetadata("timestamp.json"))

        fixture.publisher.publish(emptyList())
        assertEquals(2, referencedTargetsVersion(fixture.storage))

        val importedThree = fixture.publisher.importTargetsRenewal(versionThree.targets)
        assertEquals(3, importedThree.targetsVersion)
        assertContentEquals(versionThree.targets, fixture.storage.getMetadata("3.targets.json"))
        assertEquals(3, referencedTargetsVersion(fixture.storage))
        assertCompleteOnlineTransaction(fixture.storage)
    }

    @Test
    fun `offline ceremony preserves the exact targets policy and requires both signers`() {
        val fixture = renewalFixture()
        fixture.clock.advance(Duration.ofHours(1))
        val current = fixture.storage.getMetadata("1.targets.json")!!
        val renewed = fixture.prepare(current, fixture.clock)

        assertEquals(2, renewed.version)
        assertContentEquals(canonicalJson(RegistryJson.parseToJsonElement(renewed.targets.decodeToString())), renewed.targets)
        val currentSigned = signed(current)
        val renewedSigned = signed(renewed.targets)
        assertEquals(
            JsonObject(currentSigned.filterKeys { it != "version" && it != "expires" }),
            JsonObject(renewedSigned.filterKeys { it != "version" && it != "expires" }),
        )
        assertEquals(
            fixture.clock.instant().plus(Duration.ofDays(30)),
            Instant.parse(renewedSigned["expires"]!!.jsonPrimitive.content),
        )

        val oneSigner = fixture.renewalConfig.copy(
            targetsSigningKeysPkcs8Base64 = listOf(targetPkcs8(4)),
        )
        assertFailsWith<IllegalArgumentException> {
            prepareOfflineTargetsRenewal(
                Files.createTempDirectory("seen-targets-renewal-one-signer"),
                fixture.storage.getMetadata("1.root.json")!!,
                current,
                oneSigner,
                fixture.clock,
            )
        }
    }

    @Test
    fun `offline output is no-clobber while exact reruns are accepted`() {
        val fixture = renewalFixture()
        fixture.clock.advance(Duration.ofHours(1))
        val directory = Files.createTempDirectory("seen-targets-renewal-no-clobber")
        val root = fixture.storage.getMetadata("root.json")!!
        val current = fixture.storage.getMetadata("1.targets.json")!!
        val first = prepareOfflineTargetsRenewal(directory, root, current, fixture.renewalConfig, fixture.clock)

        val exactRerun = prepareOfflineTargetsRenewal(directory, root, current, fixture.renewalConfig, fixture.clock)
        assertContentEquals(first.targets, exactRerun.targets)
        assertContentEquals(first.targets, Files.readAllBytes(directory.resolve("2.targets.json")))

        val changedClock = Clock.fixed(fixture.clock.instant().plusSeconds(1), ZoneOffset.UTC)
        assertFailsWith<IllegalArgumentException> {
            prepareOfflineTargetsRenewal(directory, root, current, fixture.renewalConfig, changedClock)
        }
        assertContentEquals(first.targets, Files.readAllBytes(directory.resolve("2.targets.json")))
    }

    @Test
    fun `import rejects missing threshold and signed-byte tampering before persistence`() {
        val fixture = renewalFixture()
        fixture.clock.advance(Duration.ofHours(1))
        val renewed = fixture.prepare(fixture.storage.getMetadata("1.targets.json")!!, fixture.clock)
        val envelope = RegistryJson.parseToJsonElement(renewed.targets.decodeToString()).jsonObject

        val oneSignature = canonicalJson(JsonObject(envelope.toMutableMap().apply {
            put("signatures", JsonArray(envelope["signatures"]!!.let { it as JsonArray }.take(1)))
        }))
        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(oneSignature)
        }
        assertNull(fixture.storage.getMetadata("2.targets.json"))

        val tamperedSigned = envelope["signed"]!!.jsonObject.toMutableMap().apply {
            val expires = Instant.parse(getValue("expires").jsonPrimitive.content).minusSeconds(1)
            put("expires", JsonPrimitive(expires.toString()))
        }.let(::JsonObject)
        val tampered = canonicalJson(JsonObject(envelope.toMutableMap().apply {
            put("signed", tamperedSigned)
        }))
        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(tampered)
        }
        assertNull(fixture.storage.getMetadata("2.targets.json"))
    }

    @Test
    fun `import clock enforces future expiry no more than thirty days away`() {
        val fixture = renewalFixture()
        val initial = fixture.storage.getMetadata("1.targets.json")!!
        val futureCeremonyClock = Clock.fixed(fixture.clock.instant().plus(Duration.ofDays(1)), ZoneOffset.UTC)
        val tooFarAway = fixture.prepare(initial, futureCeremonyClock)
        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(tooFarAway.targets)
        }
        assertNull(fixture.storage.getMetadata("2.targets.json"))

        val expiresInThirtyDays = fixture.prepare(initial, fixture.clock)
        fixture.clock.advance(Duration.ofDays(30))
        assertFailsWith<IllegalArgumentException> {
            fixture.publisher.importTargetsRenewal(expiresInThirtyDays.targets)
        }
        assertNull(fixture.storage.getMetadata("2.targets.json"))
    }

    @Test
    fun `expired current targets can be recovered by a fresh sequential renewal`() {
        val fixture = renewalFixture()
        fixture.clock.advance(Duration.ofDays(31))
        val expired = fixture.storage.getMetadata("1.targets.json")!!
        val renewed = fixture.prepare(expired, fixture.clock)

        val imported = fixture.publisher.importTargetsRenewal(renewed.targets)

        assertEquals(2, imported.targetsVersion)
        assertEquals(2, referencedTargetsVersion(fixture.storage))
        assertContentEquals(renewed.targets, fixture.storage.getMetadata("2.targets.json"))
    }
}

private data class RenewalFixture(
    val clock: MutableClock,
    val storage: InMemoryRegistryObjectStorage,
    val publisher: TufPublisher,
    val renewalConfig: OfflineTargetsRenewalConfig,
) {
    fun prepare(currentTargets: ByteArray, ceremonyClock: Clock): TufTargetsRenewalResult = prepareOfflineTargetsRenewal(
        outputDirectory = Files.createTempDirectory("seen-targets-renewal"),
        trustedRoot = storage.getMetadata("root.json")!!,
        currentTargets = currentTargets,
        config = renewalConfig,
        clock = ceremonyClock,
    )
}

private fun renewalFixture(): RenewalFixture {
    val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
    val repository = InMemoryRegistryRepository()
    val storage = InMemoryRegistryObjectStorage()
    val online = testOnlineSigners()
    val rootSigners = (1..3).map(::testSigner)
    val targetsSigners = (4..5).map(::testSigner)
    TufBootstrapper(
        storage = storage,
        rootPublicKeys = rootSigners.map(TufSigner::publicKey),
        rootSigners = rootSigners,
        targetsPublicKeys = targetsSigners.map(TufSigner::publicKey),
        targetsSigners = targetsSigners,
        online = online.publicKeys(),
        environment = "development",
        repositoryId = "seen-dev-registry-v1",
        clock = clock,
    ).bootstrap()
    val publisher = TufPublisher(
        repository, storage, online, "development", "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages", clock,
    )
    publisher.ensureInitialTransaction()
    return RenewalFixture(
        clock,
        storage,
        publisher,
        OfflineTargetsRenewalConfig(
            environment = "development",
            repositoryId = "seen-dev-registry-v1",
            targetsSigningKeysPkcs8Base64 = listOf(targetPkcs8(4), targetPkcs8(5)),
            onlineKeys = online.publicKeys(),
        ),
    )
}

private fun referencedTargetsVersion(storage: InMemoryRegistryObjectStorage): Long {
    val timestamp = signed(storage.getMetadata("timestamp.json")!!)
    val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
    val snapshot = signed(storage.getMetadata("$snapshotVersion.snapshot.json")!!)
    return snapshot["meta"]!!.jsonObject["targets.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
}

private fun assertCompleteOnlineTransaction(storage: InMemoryRegistryObjectStorage) {
    val timestamp = signed(storage.getMetadata("timestamp.json")!!)
    val snapshotReference = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject
    val snapshotVersion = snapshotReference["version"]!!.jsonPrimitive.content.toLong()
    val snapshotBytes = assertNotNull(storage.getMetadata("$snapshotVersion.snapshot.json"))
    assertEquals(snapshotBytes.size.toLong(), snapshotReference["length"]!!.jsonPrimitive.content.toLong())
    assertEquals(sha256(snapshotBytes), snapshotReference["hashes"]!!.jsonObject["sha256"]!!.jsonPrimitive.content)
    val snapshot = signed(snapshotBytes)
    listOf("targets", "releases", "security").forEach { role ->
        val reference = snapshot["meta"]!!.jsonObject["$role.json"]!!.jsonObject
        val version = reference["version"]!!.jsonPrimitive.content.toLong()
        val bytes = assertNotNull(storage.getMetadata("$version.$role.json"))
        assertEquals(bytes.size.toLong(), reference["length"]!!.jsonPrimitive.content.toLong())
        assertEquals(sha256(bytes), reference["hashes"]!!.jsonObject["sha256"]!!.jsonPrimitive.content)
    }
}

private fun signed(bytes: ByteArray): JsonObject = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signed"]!!.jsonObject

private fun targetPkcs8(seedByte: Int): String {
    val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + seedByte).toByte() })
    return Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey).encoded)
}
