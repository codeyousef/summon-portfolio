package codes.yousef.seen.registry

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IsolatedPackageScannerTest {
    @Test
    fun `fresh scanner subprocess returns truthful isolation claims`() {
        val attestation = IsolatedPackageScanEngine(
            ruleTimeout = Duration.ofSeconds(10),
            hardTimeout = Duration.ofSeconds(15),
        ).scan(input())

        assertEquals("passed", attestation.result.status)
        assertEquals(true, attestation.scanner.isolated)
        assertEquals("none", attestation.scanner.networkAccess)
        assertEquals("none", attestation.scanner.secretAccess)
        assertEquals("read-only", attestation.scanner.inputAccess)
        assertEquals(true, attestation.sandbox.rootless)
        assertEquals(true, attestation.sandbox.ephemeral)
        assertEquals("none", attestation.sandbox.networkAccess)
        assertEquals(1, attestation.sandbox.processLimit)
        assertEquals(15_000, attestation.sandbox.cpuLimitMillis)
        assertTrue(attestation.sandbox.memoryLimitBytes in 1..(384L * 1024 * 1024))
    }

    @Test
    fun `scanner sandbox clears environment and denies sensitive capabilities`() {
        val output = ScannerChildProcess(hardTimeout = Duration.ofSeconds(10)).execute(
            mainClass = ScannerSandboxProbeMain::class.java.name,
        ).decodeToString()
        val values = output.lineSequence().filter(String::isNotBlank).associate { line ->
            line.substringBefore('=') to line.substringAfter('=')
        }
        val probePath = Path.of(requireNotNull(values["path"]))
        try {
            assertEquals("true", values["environment_empty"])
            assertEquals("true", values["environment_denied"])
            assertEquals("true", values["network_denied"])
            assertEquals("true", values["exec_denied"])
            assertEquals("true", values["write_denied"])
            assertEquals("true", values["outside_read_denied"])
        } finally {
            Files.deleteIfExists(probePath)
        }
    }

    @Test
    fun `hard subprocess timeout kills scanner and returns no attestation`() {
        val scanner = IsolatedPackageScanEngine(
            ruleTimeout = Duration.ofMillis(100),
            hardTimeout = Duration.ofMillis(300),
            childMainClass = HangingScannerSubprocessMain::class.java.name,
        )

        assertFailsWith<PackageScanTimeoutException> { scanner.scan(input()) }
    }

    private fun input(): PackageScanInput {
        val proof = proof()
        return PackageScanInput(
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
    }

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
        checks = listOf(SourceProofCheck("archive-digest", "passed", "2026-07-17T00:00:00Z", "1".repeat(64))),
        verifiedAt = "2026-07-17T00:00:00Z",
        verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
    )
}

internal object ScannerSandboxProbeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty())
        val environmentEmpty = System.getenv().isEmpty()
        val probePath = Path.of("/tmp", "seen-scanner-probe-${ProcessHandle.current().pid()}")
        ScannerSandboxPolicy.install()

        fun denied(operation: () -> Unit): Boolean = try {
            operation()
            false
        } catch (_: SecurityException) {
            true
        } catch (_: Throwable) {
            false
        }

        val environmentDenied = denied { System.getenv("SCANNER_SHOULD_NOT_EXIST") }
        val networkDenied = denied {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 9), 100) }
        }
        val execDenied = denied { ProcessBuilder("/bin/true").start().waitFor() }
        val writeDenied = denied { Files.writeString(probePath, "sandbox escape") }
        val outsideReadDenied = denied { Files.readAllBytes(Path.of("/proc/self/environ")) }
        val result = buildString {
            appendLine("environment_empty=$environmentEmpty")
            appendLine("environment_denied=$environmentDenied")
            appendLine("network_denied=$networkDenied")
            appendLine("exec_denied=$execDenied")
            appendLine("write_denied=$writeDenied")
            appendLine("outside_read_denied=$outsideReadDenied")
            appendLine("path=$probePath")
        }
        System.out.write(result.encodeToByteArray())
        System.out.flush()
    }
}

internal object HangingScannerSubprocessMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Thread.sleep(Duration.ofMinutes(1).toMillis())
    }
}
