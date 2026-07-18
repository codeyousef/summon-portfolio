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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistryFlowTest {
    @Test
    fun `upload completion remains quarantined until source proof and first scan succeed`() {
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
        val quarantined = service.completeUpload(reservation.upload.uploadId, CompleteUploadRequest(sha256(archive), archive.size.toLong()), principal)
        assertEquals("quarantined", quarantined.state.lifecycle)
        assertEquals(null, quarantined.timestamps.publicDelayStartedAt)
        assertEquals(null, quarantined.timestamps.publicDelayEndsAt)
        assertTrue(service.listPublicPackages().items.isEmpty())
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
    publicDelay = Duration.ofHours(72),
    localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.associateWith { "configured-for-direct-test-signer-$it" },
    kmsOnlinePublicKeysHex = emptyMap(),
    remoteOnlineSignerTargets = emptyMap(),
    trustAndSafetyToken = "r".repeat(32),
    trustAndSafetyPrincipal = "reviewer",
)

internal class MutableClock(private var value: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = value
    fun advance(duration: Duration) { value = value.plus(duration) }
}
