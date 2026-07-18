package codes.yousef.seen.registry

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceProofServiceTest {
    @Test
    fun `owner can read digest-bound append-only source proof while quarantined`() {
        val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val config = testConfig()
        val online = testOnlineSigners()
        val rootSigners = (1..3).map(::testSigner)
        val targetsSigners = (4..5).map(::testSigner)
        TufBootstrapper(
            storage,
            rootSigners.map(TufSigner::publicKey),
            rootSigners,
            targetsSigners.map(TufSigner::publicKey),
            targetsSigners,
            online.publicKeys(),
            config.environment,
            config.repositoryId,
            clock,
        ).bootstrap()
        val service = RegistryService(
            config,
            repository,
            storage,
            ArchiveValidator(),
            TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock),
            clock,
        )
        val principal = WriterPrincipal("publisher")
        val manifestBytes = manifestToml()
        val archive = archiveOf("Seen.toml" to manifestBytes, "src/main.seen" to "fun main() {}".encodeToByteArray())
        service.createPackage(CreatePackageRequest("seen/demo"), principal)
        val reservation = service.reserveRelease(
            "seen/demo",
            ReserveReleaseRequest(
                version = "1.2.3",
                visibility = "public",
                archive = ArchiveReservation("tar+gzip", sha256(archive), archive.size.toLong()),
                manifestSha256 = sha256(manifestBytes),
                manifest = manifestJson(),
                source = SourceDeclaration("github", "123", "456", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
            ),
            principal,
        )
        service.uploadArchive(reservation.upload.uploadId, sha256(archive), archive, principal)
        service.completeUpload(
            reservation.upload.uploadId,
            CompleteUploadRequest(sha256(archive), archive.size.toLong()),
            principal,
        )

        val proof = SourceProofRecord(
            proofId = "prf_0000000000000001",
            sequence = 1,
            packageIdentity = "seen/demo",
            version = "1.2.3",
            repository = SourceProofRepository("github", "123", "https://github.com/seen/demo", "456"),
            requestedRef = "refs/tags/v1.2.3",
            resolvedRef = "refs/tags/v1.2.3",
            commit = SourceProofCommit("sha1", "a".repeat(40)),
            archive = SourceProofArchive("b".repeat(64), sha256(archive)),
            license = SourceProofLicense("MIT", "c".repeat(64), true),
            status = "verified",
            checks = emptyList(),
            verifiedAt = "2026-07-17T00:00:00Z",
            verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
        )
        repository.appendReviewArtifact(proof.toArtifact())

        assertEquals(listOf(proof), service.listSourceProofs("seen/demo", "1.2.3", principal).items)
        assertEquals(proof, service.getSourceProof("seen/demo", "1.2.3", proof.proofId, principal))
        assertEquals(404, assertFailsWith<RegistryException> {
            service.listSourceProofs("seen/demo", "1.2.3")
        }.status)
    }
}
