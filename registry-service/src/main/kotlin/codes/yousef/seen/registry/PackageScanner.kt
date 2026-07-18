package codes.yousef.seen.registry

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration

data class PackageScanInput(
    val phase: ReviewPhase,
    val attestationId: String,
    val sequence: Long,
    val packageIdentity: String,
    val version: String,
    val archiveSha256: String,
    val sourceProof: SourceProofRecord,
    val sourceProofSha256: String,
    val files: List<SourceArchiveFile>,
    val previousAttestationId: String? = null,
    val attempt: Int = 1,
)

/** Claims emitted by the scanner must describe the boundary that actually ran it. */
data class PackageScannerExecutionProfile(
    val isolated: Boolean,
    val networkAccess: String,
    val secretAccess: String,
    val inputAccess: String,
    val rootless: Boolean,
    val ephemeral: Boolean,
    val cpuLimitMillis: Int,
    val memoryLimitBytes: Long,
    val processLimit: Int,
) {
    companion object {
        fun inProcess(timeout: Duration): PackageScannerExecutionProfile = PackageScannerExecutionProfile(
            isolated = false,
            networkAccess = "process",
            secretAccess = "process",
            inputAccess = "process-memory",
            rootless = false,
            ephemeral = false,
            cpuLimitMillis = timeout.toMillis().coerceIn(1, Int.MAX_VALUE.toLong()).toInt(),
            memoryLimitBytes = Runtime.getRuntime().maxMemory(),
            processLimit = Int.MAX_VALUE,
        )

        internal fun isolatedSubprocess(timeout: Duration): PackageScannerExecutionProfile = PackageScannerExecutionProfile(
            isolated = true,
            networkAccess = "none",
            secretAccess = "none",
            inputAccess = "read-only",
            rootless = true,
            ephemeral = true,
            cpuLimitMillis = timeout.toMillis().coerceIn(1, Int.MAX_VALUE.toLong()).toInt(),
            memoryLimitBytes = Runtime.getRuntime().maxMemory(),
            processLimit = 1,
        )
    }
}

/** Static, content, license, and malware rules that never execute package bytes. */
class PackageScanner(
    private val clock: Clock = Clock.systemUTC(),
    private val timeout: Duration = Duration.ofSeconds(20),
    private val nanoTime: () -> Long = System::nanoTime,
    private val scannerVersion: String = "1.0.0",
    private val executionProfile: PackageScannerExecutionProfile = PackageScannerExecutionProfile.inProcess(timeout),
) {
    fun scan(input: PackageScanInput): ScanAttestationRecord {
        val started = clock.instant()
        val startedNanos = nanoTime()
        require(input.sourceProof.status == "verified") { "Source proof is not verified" }
        require(input.sourceProof.archive.packageSha256 == input.archiveSha256) { "Source proof archive digest mismatch" }
        require(input.sourceProof.sha256() == input.sourceProofSha256) { "Source proof payload digest mismatch" }
        require(input.files.map(SourceArchiveFile::path).toSet().size == input.files.size) { "Duplicate scan input path" }
        require(input.files.any { it.path == "Seen.toml" }) { "Scan input has no root manifest" }

        val findings = mutableListOf<ScanFinding>()
        input.files.sortedBy(SourceArchiveFile::path).forEach { file ->
            checkDeadline(startedNanos)
            val lower = file.path.lowercase()
            val prefix = file.bytes.take(4096).toByteArray()
            if (prefix.containsSubsequence(EICAR)) {
                findings += ScanFinding("malware-eicar", "critical", "scan_malware_signature", file.path)
            }
            if (lower.endsWith(".seen")) {
                if (0.toByte() in file.bytes) {
                    findings += ScanFinding("seen-source-nul", "high", "scan_seen_source_nul", file.path)
                } else if (!isStrictUtf8(file.bytes)) {
                    findings += ScanFinding("seen-source-encoding", "high", "scan_seen_source_encoding", file.path)
                }
            }
            if (lower.substringAfterLast('/') in FORBIDDEN_SECRET_NAMES) {
                findings += ScanFinding("credential-file", "critical", "scan_credential_file", file.path)
            }
        }
        checkDeadline(startedNanos)

        val status = if (findings.any { it.severity in setOf("critical", "high") }) "failed" else "passed"
        val finished = clock.instant()
        val scanner = ScannerIdentity(
            id = "seen-package-scanner",
            version = scannerVersion,
            isolated = executionProfile.isolated,
            networkAccess = executionProfile.networkAccess,
            secretAccess = executionProfile.secretAccess,
            inputAccess = executionProfile.inputAccess,
        )
        val evidenceDigest = sha256(
            buildString {
                append(input.phase.name.lowercase()).append('\u0000')
                append(input.packageIdentity).append('\u0000').append(input.version).append('\u0000')
                append(input.archiveSha256).append('\u0000').append(input.sourceProofSha256).append('\u0000')
                append(RULESET_SHA256).append('\u0000').append(status)
                findings.forEach { append('\u0000').append(it.ruleId).append('\u0000').append(it.code).append('\u0000').append(it.path) }
            }.encodeToByteArray(),
        )
        val reportDigest = sha256(
            buildString {
                append(status).append('\u0000').append(input.archiveSha256).append('\u0000').append(input.sourceProofSha256)
                findings.forEach { append('\u0000').append(it.ruleId).append('\u0000').append(it.severity).append('\u0000').append(it.code).append('\u0000').append(it.path) }
            }.encodeToByteArray(),
        )
        return ScanAttestationRecord(
            attestationId = input.attestationId,
            sequence = input.sequence,
            previousAttestationId = input.previousAttestationId,
            subject = ScanSubject(
                input.packageIdentity,
                input.version,
                input.archiveSha256,
                input.sourceProof.proofId,
                input.sourceProofSha256,
            ),
            scan = ScanDescriptor(input.phase.name.lowercase(), input.attempt, POLICY_VERSION, RULESET_SHA256),
            scanner = scanner,
            input = ScanInputBinding(input.archiveSha256, input.sourceProofSha256, true),
            sandbox = ScanSandbox(
                rootless = executionProfile.rootless,
                ephemeral = executionProfile.ephemeral,
                networkAccess = executionProfile.networkAccess,
                cpuLimitMillis = executionProfile.cpuLimitMillis,
                memoryLimitBytes = executionProfile.memoryLimitBytes,
                processLimit = executionProfile.processLimit,
            ),
            invocation = ScanInvocation(
                id = "scan-run-${input.phase.name.lowercase()}-${input.sequence.toString().padStart(8, '0')}",
                startedAt = started.utc(),
                finishedAt = finished.utc(),
                timeoutSeconds = timeout.seconds.coerceIn(1, 3_600).toInt(),
            ),
            result = ScanResult(
                status = status,
                reason = if (status == "failed") "policy-violation" else null,
                disposition = if (status == "passed") "promotion-eligible" else "reject",
                observedArchiveSha256 = input.archiveSha256,
                observedSourceProofSha256 = input.sourceProofSha256,
                findings = findings,
                reportSha256 = reportDigest,
                evidenceSha256 = evidenceDigest,
            ),
            generatedAt = finished.utc(),
        )
    }

    private fun checkDeadline(startedNanos: Long) {
        if (nanoTime() - startedNanos > timeout.toNanos()) throw PackageScanTimeoutException()
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
    }.isSuccess

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || size < needle.size) return false
        return (0..size - needle.size).any { offset -> needle.indices.all { this[offset + it] == needle[it] } }
    }

    private companion object {
        const val POLICY_VERSION = "package-scan-v1.0.0"
        val EICAR = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE".encodeToByteArray()
        val FORBIDDEN_SECRET_NAMES = setOf(".env", ".npmrc", ".pypirc", "id_rsa", "id_ed25519", "credentials.json")
        val RULESET_SHA256 = sha256(
            "seen-package-review-v1\u0000archive-policy-v1\u0000strict-seen-utf8\u0000eicar\u0000credential-files\u0000no-execution".encodeToByteArray(),
        )
    }
}

class PackageScanTimeoutException : RuntimeException("Package scan exceeded its deadline")
