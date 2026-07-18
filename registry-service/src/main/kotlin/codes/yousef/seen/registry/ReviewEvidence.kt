package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import java.net.URI
import java.time.Instant
import java.util.Locale

@Serializable
data class SourceProofRepository(
    val forge: String,
    @SerialName("repository_id") val repositoryId: String,
    @SerialName("canonical_url") val canonicalUrl: String,
    @SerialName("installation_id") val installationId: String,
)

@Serializable
data class SourceProofCommit(val algorithm: String, val value: String)

@Serializable
data class SourceProofArchive(
    @SerialName("forge_sha256") val forgeSha256: String,
    @SerialName("package_sha256") val packageSha256: String,
)

@Serializable
data class SourceProofLicense(
    @SerialName("declared_spdx") val declaredSpdx: String,
    @SerialName("files_sha256") val filesSha256: String,
    val compatible: Boolean,
)

@Serializable
data class SourceProofCheck(
    val name: String,
    val status: String,
    @SerialName("checked_at") val checkedAt: String,
    @SerialName("evidence_sha256") val evidenceSha256: String,
)

@Serializable
data class SourceProofVerifier(val service: String, @SerialName("policy_version") val policyVersion: String)

@Serializable
data class SourceProofRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("proof_id") val proofId: String,
    val sequence: Long,
    @SerialName("previous_proof_id") val previousProofId: String? = null,
    @SerialName("package") val packageIdentity: String,
    val version: String,
    val repository: SourceProofRepository,
    @SerialName("requested_ref") val requestedRef: String,
    @SerialName("resolved_ref") val resolvedRef: String,
    val commit: SourceProofCommit,
    val archive: SourceProofArchive,
    val license: SourceProofLicense,
    val status: String,
    val checks: List<SourceProofCheck>,
    @SerialName("verified_at") val verifiedAt: String,
    val verifier: SourceProofVerifier,
)

@Serializable
data class SourceProofPage(
    val items: List<SourceProofRecord>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ScanSubject(
    @SerialName("package") val packageIdentity: String,
    val version: String,
    @SerialName("archive_sha256") val archiveSha256: String,
    @SerialName("source_proof_id") val sourceProofId: String,
    @SerialName("source_proof_sha256") val sourceProofSha256: String,
)

@Serializable
data class ScanDescriptor(
    val phase: String,
    val attempt: Int,
    @SerialName("policy_version") val policyVersion: String,
    @SerialName("ruleset_sha256") val rulesetSha256: String,
)

@Serializable
data class ScannerIdentity(
    val id: String,
    val version: String,
    val isolated: Boolean,
    @SerialName("network_access") val networkAccess: String,
    @SerialName("secret_access") val secretAccess: String,
    @SerialName("input_access") val inputAccess: String,
)

@Serializable
data class ScanFinding(
    @SerialName("rule_id") val ruleId: String,
    val severity: String,
    val code: String,
    val path: String? = null,
)

@Serializable
data class ScanInputBinding(
    @SerialName("archive_sha256") val archiveSha256: String,
    @SerialName("source_proof_sha256") val sourceProofSha256: String,
    @SerialName("read_only") val readOnly: Boolean,
)

@Serializable
data class ScanSandbox(
    val rootless: Boolean,
    val ephemeral: Boolean,
    @SerialName("network_access") val networkAccess: String,
    @SerialName("cpu_limit_millis") val cpuLimitMillis: Int,
    @SerialName("memory_limit_bytes") val memoryLimitBytes: Long,
    @SerialName("process_limit") val processLimit: Int,
)

@Serializable
data class ScanInvocation(
    val id: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("finished_at") val finishedAt: String,
    @SerialName("timeout_seconds") val timeoutSeconds: Int,
)

@Serializable
data class ScanResult(
    val status: String,
    val reason: String? = null,
    val disposition: String,
    @SerialName("observed_archive_sha256") val observedArchiveSha256: String,
    @SerialName("observed_source_proof_sha256") val observedSourceProofSha256: String,
    val findings: List<ScanFinding>,
    @SerialName("report_sha256") val reportSha256: String? = null,
    @SerialName("evidence_sha256") val evidenceSha256: String,
)

@Serializable
data class ScanAttestationRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("attestation_type") val attestationType: String = "seen-package-scan",
    @SerialName("attestation_id") val attestationId: String,
    val sequence: Long,
    @SerialName("previous_attestation_id") val previousAttestationId: String? = null,
    val subject: ScanSubject,
    val scan: ScanDescriptor,
    val scanner: ScannerIdentity,
    val input: ScanInputBinding,
    val sandbox: ScanSandbox,
    val invocation: ScanInvocation,
    val result: ScanResult,
    @SerialName("generated_at") val generatedAt: String,
)

@Serializable
data class AuditActor(val type: String, val id: String)

@Serializable
data class AuditSubject(
    @SerialName("package") val packageIdentity: String,
    val version: String,
    @SerialName("archive_sha256") val archiveSha256: String,
)

@Serializable
data class AuditEventRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("event_id") val eventId: String,
    val sequence: Long,
    val action: String,
    val outcome: String,
    val actor: AuditActor,
    val subject: AuditSubject? = null,
    @SerialName("evidence_ids") val evidenceIds: List<String> = emptyList(),
    @SerialName("internal_reason") val internalReason: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

fun SourceVerificationResult.toSourceProofRecord(
    proofId: String,
    sequence: Long,
    previousProofId: String?,
    packageIdentity: String,
    version: String,
): SourceProofRecord {
    val checkedAt = verifiedAt.utc()
    fun check(name: String, vararg values: String) = SourceProofCheck(
        name = name,
        status = "passed",
        checkedAt = checkedAt,
        evidenceSha256 = sha256(values.joinToString("\u0000").encodeToByteArray()),
    )
    return SourceProofRecord(
        proofId = proofId,
        sequence = sequence,
        previousProofId = previousProofId,
        packageIdentity = packageIdentity,
        version = version,
        repository = SourceProofRepository(
            forge = forge.wireName,
            repositoryId = repositoryId,
            canonicalUrl = canonicalRepositoryUrl,
            installationId = installationIdentity,
        ),
        requestedRef = requestedRef,
        resolvedRef = requestedRef,
        commit = SourceProofCommit(if (resolvedCommit.length == 40) "sha1" else "sha256", resolvedCommit),
        archive = SourceProofArchive(treeEvidenceSha256, archiveSha256),
        license = SourceProofLicense(licenseSpdx, licenseEvidenceSha256, true),
        status = "verified",
        checks = listOf(
            check("repository-identity", repositoryId, canonicalRepositoryUrl),
            check("installation-identity", installationIdentity),
            check("commit-resolution", requestedRef, resolvedCommit),
            check("archive-digest", treeEvidenceSha256, archiveFileSetSha256, archiveSha256),
            check("license", licenseSpdx, licensePath, licenseEvidenceSha256),
        ),
        verifiedAt = checkedAt,
        verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
    )
}

fun SourceProofRecord.sha256(): String = sha256(canonicalJson(RegistryJson.encodeToJsonElement(this)))

fun ScanAttestationRecord.sha256(): String = sha256(canonicalJson(RegistryJson.encodeToJsonElement(this)))

fun SourceProofRecord.toArtifact(createdAt: String = verifiedAt): ReviewArtifact = ReviewArtifact(
    artifactId = proofId,
    kind = SOURCE_PROOF_ARTIFACT,
    packageIdentity = packageIdentity,
    version = version,
    archiveSha256 = archive.packageSha256,
    sequence = sequence,
    createdAt = createdAt,
    payload = RegistryJson.encodeToJsonElement(this).let { it as kotlinx.serialization.json.JsonObject },
)

fun ScanAttestationRecord.toArtifact(): ReviewArtifact = ReviewArtifact(
    artifactId = attestationId,
    kind = SCAN_ATTESTATION_ARTIFACT,
    packageIdentity = subject.packageIdentity,
    version = subject.version,
    archiveSha256 = subject.archiveSha256,
    sequence = sequence,
    createdAt = generatedAt,
    payload = RegistryJson.encodeToJsonElement(this).let { it as kotlinx.serialization.json.JsonObject },
)

fun AuditEventRecord.toArtifact(): ReviewArtifact = ReviewArtifact(
    artifactId = eventId,
    kind = AUDIT_EVENT_ARTIFACT,
    packageIdentity = subject?.packageIdentity,
    version = subject?.version,
    archiveSha256 = subject?.archiveSha256,
    sequence = sequence,
    createdAt = occurredAt,
    payload = RegistryJson.encodeToJsonElement(this).let { it as kotlinx.serialization.json.JsonObject },
)

/**
 * One canonical validator for evidence accepted at worker ingress and again at
 * promotion. Persisted JSON is never trusted merely because it has the right
 * artifact ID: every immutable release, source, time, and isolation binding is
 * rechecked against repository state.
 */
internal object ReviewEvidenceValidator {
    private val digest = Regex("^[0-9a-f]{64}$")
    private val proofId = Regex("^prf_[A-Za-z0-9_-]{16,96}$")
    private val scanId = Regex("^scn_[A-Za-z0-9_-]{16,96}$")
    private val policyVersion = Regex("^[a-z0-9][a-z0-9.-]{0,63}$")
    private val scannerId = Regex("^[a-z0-9][a-z0-9-]{2,63}$")
    private val scannerVersion = Regex("^[1-9][0-9]*\\.[0-9]+\\.[0-9]+$")
    private val invocationId = Regex("^scan-run-[a-z0-9-]{8,96}$")
    private val ruleId = Regex("^[a-z0-9][a-z0-9.-]{1,95}$")
    private val findingCode = Regex("^[a-z0-9][a-z0-9_]{2,95}$")
    private val utcTimestamp = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z$")
    private val expectedChecks = setOf(
        "repository-identity",
        "installation-identity",
        "commit-resolution",
        "archive-digest",
        "license",
    )

    fun sourceResultMatches(
        result: SourceVerificationResult,
        release: StoredRelease,
        pkg: StoredPackage,
        observedAt: Instant,
    ): Boolean = runCatching {
        val repositoryUrl = pkg.record.repository ?: return@runCatching false
        result.forge.wireName == release.source.forge &&
            result.repositoryId == release.source.repositoryId &&
            canonicalRepositoryUrl(result.canonicalRepositoryUrl) == canonicalRepositoryUrl(repositoryUrl) &&
            result.installationIdentity == release.source.installationId &&
            result.requestedRef == release.source.requestedRef &&
            result.resolvedCommit == release.source.expectedCommit &&
            result.archiveSha256 == release.record.archive.sha256 &&
            result.licenseSpdx == release.source.licenseSpdx &&
            result.verifiedAt <= observedAt.plusSeconds(5) &&
            listOf(
                result.treeEvidenceSha256,
                result.archiveFileSetSha256,
                result.licenseEvidenceSha256,
                result.proofSha256,
            ).all(digest::matches)
    }.getOrDefault(false)

    fun sourceProofMatches(
        proof: SourceProofRecord,
        release: StoredRelease,
        pkg: StoredPackage,
    ): Boolean = runCatching {
        val repositoryUrl = pkg.record.repository ?: return@runCatching false
        val verifiedAt = parseUtc(proof.verifiedAt) ?: return@runCatching false
        val expectedAlgorithm = if (release.source.expectedCommit.length == 40) "sha1" else "sha256"
        proof.contractVersion == 1 &&
            proofId.matches(proof.proofId) && proof.sequence >= 1 &&
            (proof.previousProofId == null || proofId.matches(proof.previousProofId)) &&
            proof.packageIdentity == release.record.`package` && proof.version == release.record.version &&
            proof.repository.forge == release.source.forge &&
            proof.repository.repositoryId == release.source.repositoryId &&
            canonicalRepositoryUrl(proof.repository.canonicalUrl) == canonicalRepositoryUrl(repositoryUrl) &&
            proof.repository.installationId == release.source.installationId &&
            proof.requestedRef == release.source.requestedRef && proof.resolvedRef == release.source.requestedRef &&
            proof.commit.algorithm == expectedAlgorithm && proof.commit.value == release.source.expectedCommit &&
            proof.archive.packageSha256 == release.record.archive.sha256 && digest.matches(proof.archive.forgeSha256) &&
            proof.license.declaredSpdx == release.source.licenseSpdx &&
            digest.matches(proof.license.filesSha256) && proof.license.compatible &&
            proof.status == "verified" && proof.verifier.service == "source-verifier" &&
            proof.verifier.policyVersion == "source-proof-v1" &&
            proof.checks.size == expectedChecks.size && proof.checks.map { it.name }.toSet() == expectedChecks &&
            proof.checks.all { check ->
                check.status == "passed" && digest.matches(check.evidenceSha256) &&
                    parseUtc(check.checkedAt)?.let { !it.isAfter(verifiedAt) } == true
            }
    }.getOrDefault(false)

    fun scanMatches(
        attestation: ScanAttestationRecord,
        release: StoredRelease,
        phase: ReviewPhase,
        proof: SourceProofRecord,
        requirePassed: Boolean = false,
    ): Boolean = runCatching {
        val startedAt = parseUtc(attestation.invocation.startedAt) ?: return@runCatching false
        val finishedAt = parseUtc(attestation.invocation.finishedAt) ?: return@runCatching false
        val generatedAt = parseUtc(attestation.generatedAt) ?: return@runCatching false
        val resultShapeValid = when (attestation.result.status) {
            "passed" -> attestation.result.reason == null &&
                attestation.result.disposition == "promotion-eligible" &&
                attestation.result.reportSha256?.let(digest::matches) == true
            "failed" -> attestation.result.reason == "policy-violation" &&
                attestation.result.disposition == "reject" &&
                attestation.result.reportSha256?.let(digest::matches) == true
            "error" -> attestation.result.reason in setOf("scanner-inconclusive", "scanner-crash") &&
                attestation.result.disposition == "retry-unavailable" && attestation.result.reportSha256 == null
            "timeout" -> attestation.result.reason == "scanner-timeout" &&
                attestation.result.disposition == "retry-unavailable" && attestation.result.reportSha256 == null
            else -> false
        }
        val findingsValid = attestation.result.findings.size <= 256 &&
            attestation.result.findings.distinct().size == attestation.result.findings.size &&
            attestation.result.findings.all { finding ->
                ruleId.matches(finding.ruleId) &&
                    finding.severity in setOf("info", "low", "medium", "high", "critical") &&
                    findingCode.matches(finding.code) &&
                    (finding.path == null || validFindingPath(finding.path))
            }
        attestation.contractVersion == 1 && attestation.attestationType == "seen-package-scan" &&
            scanId.matches(attestation.attestationId) && attestation.sequence >= 1 &&
            (attestation.previousAttestationId == null || scanId.matches(attestation.previousAttestationId)) &&
            attestation.subject.packageIdentity == release.record.`package` &&
            attestation.subject.version == release.record.version &&
            attestation.subject.archiveSha256 == release.record.archive.sha256 &&
            attestation.subject.sourceProofId == proof.proofId &&
            attestation.subject.sourceProofSha256 == proof.sha256() &&
            attestation.scan.phase == phase.name.lowercase() && attestation.scan.attempt >= 1 &&
            policyVersion.matches(attestation.scan.policyVersion) && digest.matches(attestation.scan.rulesetSha256) &&
            scannerId.matches(attestation.scanner.id) && scannerVersion.matches(attestation.scanner.version) &&
            attestation.scanner.isolated && attestation.scanner.networkAccess == "none" &&
            attestation.scanner.secretAccess == "none" && attestation.scanner.inputAccess == "read-only" &&
            attestation.input.archiveSha256 == release.record.archive.sha256 &&
            attestation.input.sourceProofSha256 == proof.sha256() && attestation.input.readOnly &&
            attestation.sandbox.rootless && attestation.sandbox.ephemeral &&
            attestation.sandbox.networkAccess == "none" && attestation.sandbox.cpuLimitMillis >= 1 &&
            attestation.sandbox.memoryLimitBytes >= 1_048_576 && attestation.sandbox.processLimit >= 1 &&
            invocationId.matches(attestation.invocation.id) &&
            attestation.invocation.timeoutSeconds in 1..3_600 && !finishedAt.isBefore(startedAt) &&
            !generatedAt.isBefore(finishedAt) &&
            attestation.result.observedArchiveSha256 == release.record.archive.sha256 &&
            attestation.result.observedSourceProofSha256 == proof.sha256() &&
            digest.matches(attestation.result.evidenceSha256) && resultShapeValid && findingsValid &&
            (!requirePassed || attestation.result.status == "passed")
    }.getOrDefault(false)

    private fun parseUtc(value: String): Instant? = value.takeIf(utcTimestamp::matches)?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    }

    private fun validFindingPath(value: String): Boolean = value.length <= 240 && value.isNotBlank() &&
        !value.startsWith('/') && '\\' !in value && value.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun canonicalRepositoryUrl(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
        require(uri.userInfo == null && uri.query == null && uri.fragment == null)
        val path = uri.path.trimEnd('/').removeSuffix(".git")
        require(path.isNotBlank() && path != "/")
        return URI("https", null, uri.host.lowercase(Locale.ROOT), uri.port, path, null, null).toASCIIString()
    }
}

const val SOURCE_PROOF_ARTIFACT = "source-proof"
const val SCAN_ATTESTATION_ARTIFACT = "scan-attestation"
const val AUDIT_EVENT_ARTIFACT = "audit-event"
