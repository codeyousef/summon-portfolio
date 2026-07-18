package codes.yousef.seen.registry

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackageScannerTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `direct scanner binds evidence without claiming process isolation`() {
        val proof = proof()
        val attestation = PackageScanner(clock).scan(input(proof))
        assertEquals("passed", attestation.result.status)
        assertEquals("promotion-eligible", attestation.result.disposition)
        assertEquals(proof.proofId, attestation.subject.sourceProofId)
        assertEquals(proof.sha256(), attestation.subject.sourceProofSha256)
        assertEquals("process", attestation.scanner.networkAccess)
        assertEquals("process", attestation.scanner.secretAccess)
        assertEquals(false, attestation.scanner.isolated)
        assertEquals("process-memory", attestation.scanner.inputAccess)
        assertEquals(false, attestation.sandbox.rootless)
        assertEquals(false, attestation.sandbox.ephemeral)
    }

    @Test
    fun `malware hidden in a text asset and credential files fail closed`() {
        val proof = proof()
        val infected = input(proof).copy(files = input(proof).files + listOf(
            SourceArchiveFile("assets/readme.txt", "prefix EICAR-STANDARD-ANTIVIRUS-TEST-FILE suffix".encodeToByteArray()),
            SourceArchiveFile("docs/.env", "TOKEN=value".encodeToByteArray()),
        ))
        val result = PackageScanner(clock).scan(infected)
        assertEquals("failed", result.result.status)
        assertEquals("reject", result.result.disposition)
        assertEquals(setOf("malware-eicar", "credential-file"), result.result.findings.map { it.ruleId }.toSet())
    }

    @Test
    fun `source proof and archive substitutions are rejected before scanning`() {
        val proof = proof()
        assertFailsWith<IllegalArgumentException> {
            PackageScanner(clock).scan(input(proof).copy(archiveSha256 = "b".repeat(64)))
        }
        assertFailsWith<IllegalArgumentException> {
            PackageScanner(clock).scan(input(proof).copy(sourceProofSha256 = "c".repeat(64)))
        }
    }

    @Test
    fun `timeout fails closed without an attestation`() {
        var nanos = 0L
        val scanner = PackageScanner(clock, Duration.ofNanos(1), nanoTime = { nanos += 2; nanos })
        assertFailsWith<PackageScanTimeoutException> { scanner.scan(input(proof())) }
    }

    private fun input(proof: SourceProofRecord) = PackageScanInput(
        phase = ReviewPhase.FIRST,
        attestationId = "scn_0000000000000001",
        sequence = 2,
        packageIdentity = "seen/demo",
        version = "1.2.3",
        archiveSha256 = "a".repeat(64),
        sourceProof = proof,
        sourceProofSha256 = proof.sha256(),
        files = listOf(
            SourceArchiveFile("Seen.toml", "manifest-version = 1".encodeToByteArray()),
            SourceArchiveFile("src/main.seen", "fun main() {}".encodeToByteArray()),
        ),
    )

    private fun proof() = SourceProofRecord(
        proofId = "prf_0000000000000001",
        sequence = 1,
        packageIdentity = "seen/demo",
        version = "1.2.3",
        repository = SourceProofRepository("github", "123", "https://github.com/seen/demo", "456"),
        requestedRef = "refs/tags/v1.2.3",
        resolvedRef = "refs/tags/v1.2.3",
        commit = SourceProofCommit("sha1", "d".repeat(40)),
        archive = SourceProofArchive("e".repeat(64), "a".repeat(64)),
        license = SourceProofLicense("MIT", "f".repeat(64), true),
        status = "verified",
        checks = listOf("repository-identity", "installation-identity", "commit-resolution", "archive-digest", "license").mapIndexed { index, name ->
            SourceProofCheck(name, "passed", "2026-07-17T00:00:00Z", index.toString(16).padStart(64, '0'))
        },
        verifiedAt = "2026-07-17T00:00:00Z",
        verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
    )
}
