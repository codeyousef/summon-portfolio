package codes.yousef.seen.registry

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegistryFlowTest {
    @Test
    fun `injected clock drives full reservation upload delay promotion and public fetch`() {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        val config = testConfig()
        val rootSigners = (1..3).map(::testSigner)
        val targetsSigners = (4..5).map(::testSigner)
        TufBootstrapper(
            storage, rootSigners.map(TufSigner::publicKey), rootSigners,
            targetsSigners.map(TufSigner::publicKey), targetsSigners,
            online.publicKeys(), config.environment, config.repositoryId, clock,
        ).bootstrap()
        val tuf = TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock)
        val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
        val principal = WriterPrincipal("publisher")
        val manifestBytes = manifestToml()
        val archive = archiveOf("Seen.toml" to manifestBytes, "src/main.seen" to "fun main() {}".encodeToByteArray())

        service.createPackage(CreatePackageRequest("seen/demo", "Demo package", "https://github.com/seen/demo", "MIT"), principal)
        val reservation = service.reserveRelease("seen/demo", ReserveReleaseRequest(
            version = "1.2.3",
            visibility = "public",
            archive = ArchiveReservation("tar+gzip", sha256(archive), archive.size.toLong()),
            manifestSha256 = sha256(manifestBytes),
            manifest = manifestJson(),
            source = SourceDeclaration("github", "123", "456", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
        ), principal)
        val uploadJson = RegistryJson.parseToJsonElement(RegistryJson.encodeToString(reservation.upload)).jsonObject
        assertEquals(false, uploadJson["required_headers"]!!.jsonObject["Content-Length"]!!.jsonPrimitive.isString)
        service.uploadArchive(reservation.upload.uploadId, sha256(archive), archive, principal)
        val delayed = service.completeUpload(reservation.upload.uploadId, CompleteUploadRequest(sha256(archive), archive.size.toLong()), principal)
        assertEquals("delayed", delayed.state.lifecycle)
        assertTrue(service.listPublicPackages().items.isEmpty())
        assertTrue(service.promoteDue().isEmpty())

        clock.advance(Duration.ofHours(72))
        val promoted = service.promoteDue().single()
        assertEquals("available", promoted.state.availability)
        assertNotNull(promoted.resolverMetadataVersion)
        assertEquals("1.2.3", service.listPublicPackages().items.single().latestActiveVersion)
        assertContentEquals(archive, service.publicBlob(sha256(archive)))
        assertTrue(service.metadata("timestamp.json").isNotEmpty())
        assertTrue(service.metadata("${promoted.resolverMetadataVersion}.releases.json").decodeToString().contains("seen/demo"))
    }
}

internal fun testConfig() = RegistryConfig(
    environment = "development",
    repositoryId = "seen-dev-registry-v1",
    registryOrigin = "https://seen.dev.yousef.codes/packages",
    port = 0,
    storageMode = "memory",
    projectId = null,
    firestoreDatabase = "test",
    quarantineBucket = null,
    publicBucket = null,
    metadataBucket = null,
    objectPrefix = "test",
    writerMode = "opaque-dev",
    writerToken = "x".repeat(32),
    writerPrincipal = "publisher",
    ownerAllowlist = setOf("seen"),
    writersEnabled = true,
    promotionMode = "test-static",
    publicDelay = Duration.ofHours(72),
    localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.associateWith { "configured-for-direct-test-signer-$it" },
    kmsOnlineKeyVersions = emptyMap(),
    kmsOnlinePublicKeysHex = emptyMap(),
    offlineRootPublicKeysHex = emptyList(),
    offlineRootSigningKeysPkcs8Base64 = emptyList(),
    offlineTargetsPublicKeysHex = emptyList(),
    offlineTargetsSigningKeysPkcs8Base64 = emptyList(),
    bootstrapRootEnvelopeBase64 = null,
    bootstrapTargetsEnvelopeBase64 = null,
)

internal class MutableClock(private var value: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = value
    fun advance(duration: Duration) { value = value.plus(duration) }
}
