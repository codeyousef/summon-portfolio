package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustRotationTest {
    @Test
    fun `targets rotation replaces delegation keys and immediately publishes only replacement signatures`() {
        val fixture = trustRotationFixture()
        val currentTargets = fixture.storage.getMetadata("1.targets.json")!!
        val oldTimestamp = fixture.storage.getMetadata("timestamp.json")!!
        fixture.clock.advance(Duration.ofHours(1))
        val publisher = fixture.rotationPublisher(TufRole.RELEASES)
        val rotated = fixture.prepareTargetsRotation(currentTargets, fixture.rotationKeys(TufRole.RELEASES))

        val result = publisher.importTargetsRotation(rotated.targets, TufRole.RELEASES)

        assertEquals(2, result.targetsVersion)
        assertEquals(2, rotationReferencedTargetsVersion(fixture.storage))
        assertContentEquals(rotated.targets, fixture.storage.getMetadata("2.targets.json"))
        val delegations = rotationSigned(rotated.targets)["delegations"]!!.jsonObject
        val delegatedIds = delegations["keys"]!!.jsonObject.keys
        assertEquals(
            setOf(
                tufKeyId(fixture.replacementOnline.releases.publicKey),
                tufKeyId(fixture.originalOnline.security.publicKey),
            ),
            delegatedIds,
        )
        assertEquals(
            tufKeyId(fixture.replacementOnline.releases.publicKey),
            rotationReferencedOnlineSignature(fixture.storage, TufRole.RELEASES),
        )
        assertEquals(
            tufKeyId(fixture.originalOnline.security.publicKey),
            rotationReferencedOnlineSignature(fixture.storage, TufRole.SECURITY),
        )

        val replacementTimestamp = fixture.storage.getMetadata("timestamp.json")!!
        assertTrue(fixture.storage.replaceMetadataIfUnchanged("timestamp.json", replacementTimestamp, oldTimestamp))
        assertFailsWith<IllegalArgumentException> { publisher.ensureFreshTransaction() }
        assertTrue(fixture.storage.replaceMetadataIfUnchanged("timestamp.json", oldTimestamp, replacementTimestamp))
        assertEquals(result.onlineTransactionVersion, publisher.ensureFreshTransaction())
    }

    @Test
    fun `security rotation retains releases and requires no releases signing authority`() {
        val fixture = trustRotationFixture()
        fixture.clock.advance(Duration.ofHours(1))
        val currentTargets = fixture.storage.getMetadata("1.targets.json")!!
        val publisher = fixture.rotationPublisher(TufRole.SECURITY)
        val rotated = fixture.prepareTargetsRotation(currentTargets, fixture.rotationKeys(TufRole.SECURITY))

        val result = publisher.importTargetsRotation(rotated.targets, TufRole.SECURITY)

        assertEquals(2, result.targetsVersion)
        assertNull(fixture.storage.getMetadata("${result.onlineTransactionVersion}.releases.json"))
        assertEquals(
            tufKeyId(fixture.originalOnline.releases.publicKey),
            rotationReferencedOnlineSignature(fixture.storage, TufRole.RELEASES),
        )
        assertEquals(
            tufKeyId(fixture.replacementOnline.security.publicKey),
            rotationReferencedOnlineSignature(fixture.storage, TufRole.SECURITY),
        )
        assertEquals(result.onlineTransactionVersion, publisher.ensureFreshTransaction())
    }

    @Test
    fun `targets rotation is sequential threshold protected and cannot pass as a normal renewal`() {
        val fixture = trustRotationFixture()
        fixture.clock.advance(Duration.ofHours(1))
        val current = fixture.storage.getMetadata("1.targets.json")!!
        val swappedFormerKeys = TufOnlineKeys(
            fixture.originalOnline.security.publicKey,
            fixture.originalOnline.releases.publicKey,
            fixture.originalOnline.snapshot.publicKey,
            fixture.originalOnline.timestamp.publicKey,
        )
        assertFailsWith<IllegalArgumentException> {
            fixture.prepareTargetsRotation(current, swappedFormerKeys)
        }
        val reusedFormerSecurityKey = TufOnlineKeys(
            fixture.originalOnline.security.publicKey,
            fixture.replacementOnline.security.publicKey,
            fixture.originalOnline.snapshot.publicKey,
            fixture.originalOnline.timestamp.publicKey,
        )
        assertFailsWith<IllegalArgumentException> {
            fixture.prepareTargetsRotation(current, reusedFormerSecurityKey)
        }
        val publisher = fixture.rotationPublisher(TufRole.RELEASES)
        val rotated = fixture.prepareTargetsRotation(current, fixture.rotationKeys(TufRole.RELEASES))
        val envelope = RegistryJson.parseToJsonElement(rotated.targets.decodeToString()).jsonObject
        val oneSignature = canonicalJson(JsonObject(envelope.toMutableMap().apply {
            put("signatures", JsonArray(envelope["signatures"]!!.jsonArray.take(1)))
        }))

        assertFailsWith<IllegalArgumentException> {
            publisher.importTargetsRotation(oneSignature, TufRole.RELEASES)
        }
        assertNull(fixture.storage.getMetadata("2.targets.json"))

        publisher.importTargetsRotation(rotated.targets, TufRole.RELEASES)
        val furtherReplacement = TufOnlineKeys(
            testSigner(80).publicKey,
            testSigner(90).publicKey,
            fixture.replacementOnline.snapshot.publicKey,
            fixture.replacementOnline.timestamp.publicKey,
        )
        val delegationChange = fixture.prepareTargetsRotation(rotated.targets, furtherReplacement)
        assertFailsWith<IllegalArgumentException> {
            fixture.replacementPublisher.importTargetsRenewal(delegationChange.targets)
        }
        assertNull(fixture.storage.getMetadata("3.targets.json"))
        val timestamp = fixture.storage.getMetadata("timestamp.json")!!
        assertFailsWith<IllegalArgumentException> {
            publisher.importTargetsRotation(rotated.targets, TufRole.RELEASES)
        }
        assertContentEquals(timestamp, fixture.storage.getMetadata("timestamp.json"))
    }

    @Test
    fun `root rotation preserves non-root trust and requires both old and new thresholds`() {
        val fixture = trustRotationFixture()
        fixture.clock.advance(Duration.ofDays(1))
        val current = fixture.storage.getMetadata("root.json")!!
        val output = Files.createTempDirectory("seen-root-rotation")
        val config = rootRotationConfig()
        val rotated = prepareOfflineRootRotation(output, current, config, fixture.clock)

        assertEquals(2, rotated.version)
        assertContentEquals(rotated.root, Files.readAllBytes(output.resolve("2.root.json")))
        val currentSigned = rotationSigned(current)
        val rotatedSigned = rotationSigned(rotated.root)
        listOf("targets", "snapshot", "timestamp").forEach { role ->
            assertEquals(
                currentSigned["roles"]!!.jsonObject[role],
                rotatedSigned["roles"]!!.jsonObject[role],
            )
            currentSigned["roles"]!!.jsonObject[role]!!.jsonObject["keyids"]!!.jsonArray.forEach { keyId ->
                val id = keyId.jsonPrimitive.content
                assertEquals(currentSigned["keys"]!!.jsonObject[id], rotatedSigned["keys"]!!.jsonObject[id])
            }
        }
        assertEquals(4, rotationSignatureIds(rotated.root).size)
        assertEquals(2, rotationSignatureIds(rotated.root).intersect(rotated.previousRootKeyIds.toSet()).size)
        assertEquals(2, rotationSignatureIds(rotated.root).intersect(rotated.nextRootKeyIds.toSet()).size)

        val missingOldThreshold = rotationWithSignatures(
            rotated.root,
            rotationSignatureIds(rotated.root).filterNot { it == rotated.previousRootKeyIds.first() },
        )
        assertFailsWith<IllegalArgumentException> { fixture.originalPublisher.importRootRotation(missingOldThreshold) }
        val missingNewThreshold = rotationWithSignatures(
            rotated.root,
            rotationSignatureIds(rotated.root).filterNot { it == rotated.nextRootKeyIds.first() },
        )
        assertFailsWith<IllegalArgumentException> { fixture.originalPublisher.importRootRotation(missingNewThreshold) }
        assertContentEquals(current, fixture.storage.getMetadata("root.json"))

        val imported = fixture.originalPublisher.importRootRotation(rotated.root)
        assertEquals(2, imported.rootVersion)
        assertContentEquals(current, fixture.storage.getMetadata("1.root.json"))
        assertContentEquals(rotated.root, fixture.storage.getMetadata("2.root.json"))
        assertContentEquals(rotated.root, fixture.storage.getMetadata("root.json"))
        assertFailsWith<IllegalArgumentException> { fixture.originalPublisher.importRootRotation(rotated.root) }
        assertEquals(2, fixture.originalPublisher.verifyStoredRootChain())
        assertEquals(2, fixture.originalPublisher.ensureFreshTransaction())
    }

    @Test
    fun `root rotation offline output is idempotent no-clobber and import rejects tamper and CAS loss`() {
        val fixture = trustRotationFixture()
        fixture.clock.advance(Duration.ofDays(1))
        val current = fixture.storage.getMetadata("root.json")!!
        val output = Files.createTempDirectory("seen-root-rotation-no-clobber")
        val config = rootRotationConfig()
        val first = prepareOfflineRootRotation(output, current, config, fixture.clock)
        val exact = prepareOfflineRootRotation(output, current, config, fixture.clock)
        assertContentEquals(first.root, exact.root)

        val changedClock = Clock.fixed(fixture.clock.instant().plusSeconds(1), ZoneOffset.UTC)
        assertFailsWith<IllegalArgumentException> {
            prepareOfflineRootRotation(output, current, config, changedClock)
        }
        assertContentEquals(first.root, Files.readAllBytes(output.resolve("2.root.json")))

        val envelope = RegistryJson.parseToJsonElement(first.root.decodeToString()).jsonObject
        val tamperedSigned = envelope["signed"]!!.jsonObject.toMutableMap().apply {
            put("expires", kotlinx.serialization.json.JsonPrimitive(fixture.clock.instant().plus(Duration.ofDays(364)).toString()))
        }.let(::JsonObject)
        val tampered = canonicalJson(JsonObject(envelope.toMutableMap().apply { put("signed", tamperedSigned) }))
        assertFailsWith<IllegalArgumentException> { fixture.originalPublisher.importRootRotation(tampered) }
        assertNull(fixture.storage.getMetadata("2.root.json"))

        val rejectingStorage = object : RegistryObjectStorage by fixture.storage {
            override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean =
                if (filename == "root.json") false else fixture.storage.replaceMetadataIfUnchanged(filename, expected, bytes)
        }
        val casPublisher = TufPublisher(
            fixture.repository,
            rejectingStorage,
            fixture.originalOnline,
            "development",
            "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages",
            fixture.clock,
        )
        val failure = assertFailsWith<RegistryException> { casPublisher.importRootRotation(first.root) }
        assertEquals("temporarily_unavailable", failure.code)
        assertContentEquals(current, fixture.storage.getMetadata("root.json"))
        assertContentEquals(first.root, fixture.storage.getMetadata("2.root.json"))
        assertNull(casPublisher.publicMetadata("2.root.json"))

        val repaired = fixture.originalPublisher.importRootRotation(first.root)
        assertEquals(2, repaired.rootVersion)
        assertContentEquals(first.root, fixture.originalPublisher.publicMetadata("2.root.json"))
        assertEquals(2, fixture.originalPublisher.verifyStoredRootChain())
    }
}

private data class TrustRotationFixture(
    val clock: MutableClock,
    val repository: InMemoryRegistryRepository,
    val storage: InMemoryRegistryObjectStorage,
    val originalOnline: TufOnlineSigners,
    val replacementOnline: TufOnlineSigners,
    val originalPublisher: TufPublisher,
    val replacementPublisher: TufPublisher,
) {
    fun rotationKeys(role: String): TufOnlineKeys = when (role) {
        TufRole.RELEASES -> TufOnlineKeys(
            replacementOnline.releases.publicKey,
            originalOnline.security.publicKey,
            originalOnline.snapshot.publicKey,
            originalOnline.timestamp.publicKey,
        )
        TufRole.SECURITY -> TufOnlineKeys(
            originalOnline.releases.publicKey,
            replacementOnline.security.publicKey,
            originalOnline.snapshot.publicKey,
            originalOnline.timestamp.publicKey,
        )
        else -> error("Unsupported rotation role $role")
    }

    fun rotationPublisher(role: String): TufPublisher {
        val signers = when (role) {
            TufRole.RELEASES -> TufOnlineSigners(
                replacementOnline.releases,
                PublicKeyOnlyTufSigner(originalOnline.security.publicKey),
                originalOnline.snapshot,
                originalOnline.timestamp,
            )
            TufRole.SECURITY -> TufOnlineSigners(
                PublicKeyOnlyTufSigner(originalOnline.releases.publicKey),
                replacementOnline.security,
                originalOnline.snapshot,
                originalOnline.timestamp,
            )
            else -> error("Unsupported rotation role $role")
        }
        return TufPublisher(
            repository,
            storage,
            signers,
            "development",
            "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages",
            clock,
        )
    }

    fun prepareTargetsRotation(current: ByteArray, onlineKeys: TufOnlineKeys): TufTargetsRenewalResult =
        prepareOfflineTargetsRotation(
            Files.createTempDirectory("seen-targets-rotation"),
            storage.getMetadata("root.json")!!,
            current,
            OfflineTargetsRotationConfig(
                "development",
                "seen-dev-registry-v1",
                listOf(rotationPkcs8(4), rotationPkcs8(5)),
                onlineKeys,
            ),
            clock,
        )
}

private fun trustRotationFixture(): TrustRotationFixture {
    val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
    val repository = InMemoryRegistryRepository()
    val storage = InMemoryRegistryObjectStorage()
    val original = TufOnlineSigners(testSigner(10), testSigner(20), testSigner(30), testSigner(40))
    val replacement = TufOnlineSigners(testSigner(50), testSigner(60), testSigner(30), testSigner(40))
    val roots = (1..3).map(::testSigner)
    val targets = (4..5).map(::testSigner)
    TufBootstrapper(
        storage,
        roots.map(TufSigner::publicKey),
        roots,
        targets.map(TufSigner::publicKey),
        targets,
        original.publicKeys(),
        "development",
        "seen-dev-registry-v1",
        clock,
    ).bootstrap()
    val originalPublisher = TufPublisher(
        repository, storage, original, "development", "seen-dev-registry-v1",
        "https://seen.dev.yousef.codes/packages", clock,
    )
    originalPublisher.ensureInitialTransaction()
    return TrustRotationFixture(
        clock,
        repository,
        storage,
        original,
        replacement,
        originalPublisher,
        TufPublisher(
            repository, storage, replacement, "development", "seen-dev-registry-v1",
            "https://seen.dev.yousef.codes/packages", clock,
        ),
    )
}

private fun rootRotationConfig() = OfflineRootRotationConfig(
    environment = "development",
    repositoryId = "seen-dev-registry-v1",
    currentRootSigningKeysPkcs8Base64 = listOf(rotationPkcs8(1), rotationPkcs8(2)),
    nextRootPublicKeys = (70..72).map(::testSigner).map(TufSigner::publicKey),
    nextRootSigningKeysPkcs8Base64 = listOf(rotationPkcs8(70), rotationPkcs8(71)),
)

private fun rotationReferencedTargetsVersion(storage: RegistryObjectStorage): Long {
    val timestamp = rotationSigned(storage.getMetadata("timestamp.json")!!)
    val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
    val snapshot = rotationSigned(storage.getMetadata("$snapshotVersion.snapshot.json")!!)
    return snapshot["meta"]!!.jsonObject["targets.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
}

private fun rotationReferencedOnlineSignature(storage: RegistryObjectStorage, role: String): String {
    val timestamp = rotationSigned(storage.getMetadata("timestamp.json")!!)
    val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
    val snapshot = rotationSigned(storage.getMetadata("$snapshotVersion.snapshot.json")!!)
    val roleVersion = snapshot["meta"]!!.jsonObject["$role.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
    val roleEnvelope = RegistryJson.parseToJsonElement(storage.getMetadata("$roleVersion.$role.json")!!.decodeToString()).jsonObject
    return roleEnvelope["signatures"]!!.jsonArray.single().jsonObject["keyid"]!!.jsonPrimitive.content
}

private fun rotationSigned(bytes: ByteArray): JsonObject =
    RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signed"]!!.jsonObject

private fun rotationSignatureIds(bytes: ByteArray): Set<String> =
    RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signatures"]!!.jsonArray
        .map { it.jsonObject["keyid"]!!.jsonPrimitive.content }
        .toSet()

private fun rotationWithSignatures(bytes: ByteArray, retainedIds: Collection<String>): ByteArray {
    val envelope = RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject
    return canonicalJson(JsonObject(envelope.toMutableMap().apply {
        put("signatures", JsonArray(envelope["signatures"]!!.jsonArray.filter {
            it.jsonObject["keyid"]!!.jsonPrimitive.content in retainedIds
        }))
    }))
}

private fun rotationPkcs8(seedByte: Int): String {
    val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + seedByte).toByte() })
    return Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey).encoded)
}
