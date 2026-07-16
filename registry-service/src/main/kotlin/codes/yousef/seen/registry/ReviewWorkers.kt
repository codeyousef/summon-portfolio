package codes.yousef.seen.registry

import kotlinx.serialization.json.decodeFromJsonElement
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64

data class ReviewArchiveInspection(
    val archiveSha256: String,
    val manifestBytes: ByteArray,
    val files: List<SourceArchiveFile>,
    val declaredPackagePaths: Set<String> = files.mapTo(linkedSetOf(), SourceArchiveFile::path),
    val expandedBytes: Long? = null,
    val entryCount: Int? = null,
    val largestRegularFileBytes: Long? = null,
    val longestPathBytes: Int? = null,
    val maximumPathDepth: Int? = null,
)

fun interface ReviewArchiveInspector {
    fun inspect(release: StoredRelease): ReviewArchiveInspection
}

class StorageReviewArchiveInspector(
    private val storage: RegistryObjectStorage,
    private val validator: ArchiveValidator,
) : ReviewArchiveInspector {
    override fun inspect(release: StoredRelease): ReviewArchiveInspection {
        val source = storage.quarantineSource(release.uploadId)
            ?: throw RegistryException(409, "release_not_ready", "Archive upload is not complete", true, 5)
        val validation = validator.validate(
            source = source,
            expectedArchiveSha256 = release.record.archive.sha256,
            expectedManifestSha256 = release.record.manifestSha256,
            expectedIdentity = release.record.`package`,
            expectedVersion = release.record.version,
            reservedManifest = release.manifest,
        )
        return ReviewArchiveInspection(
            archiveSha256 = validation.archiveSha256,
            manifestBytes = validation.manifestBytes,
            files = validation.archiveFiles,
            declaredPackagePaths = validation.declaredPackagePaths,
            expandedBytes = validation.expandedBytes,
            entryCount = validation.entryCount,
            largestRegularFileBytes = validation.largestRegularFileBytes,
            longestPathBytes = validation.longestPathBytes,
            maximumPathDepth = validation.maximumPathDepth,
        )
    }
}

fun interface SourceProofEngine {
    fun verify(input: SourceVerificationInput): SourceVerificationResult
}

fun interface PackageScanEngine {
    fun scan(input: PackageScanInput): ScanAttestationRecord?
}

class DefaultSourceProofEngine(private val verifier: SourceVerifier) : SourceProofEngine {
    override fun verify(input: SourceVerificationInput): SourceVerificationResult = verifier.verify(input)
}

class DefaultPackageScanEngine(private val scanner: PackageScanner) : PackageScanEngine {
    override fun scan(input: PackageScanInput): ScanAttestationRecord = scanner.scan(input)
}

fun interface ReviewIdGenerator {
    fun next(prefix: String): String
}

class SecureReviewIdGenerator(private val random: SecureRandom = SecureRandom()) : ReviewIdGenerator {
    override fun next(prefix: String): String = prefix + ByteArray(18).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
}

enum class ReviewWorkerOutcome { NO_WORK, APPLIED, RETRYABLE_FAILURE, REJECTED, CONFLICT }

class SourceReviewWorker(
    private val repository: RegistryRepository,
    private val inspector: ReviewArchiveInspector,
    private val sourceProofEngine: SourceProofEngine,
    private val stateMachine: ReviewStateMachine,
    private val clock: Clock,
    private val ids: ReviewIdGenerator = SecureReviewIdGenerator(),
) {
    fun runOnce(): ReviewWorkerOutcome {
        val now = clock.instant()
        recoverExpiredClaim(now)?.let { return it }
        val candidate = repository.listReleases().asSequence().filter { release ->
            release.record.state.lifecycle == "quarantined" ||
                (release.record.state.lifecycle == "delayed" &&
                    release.record.verification.firstScan == "passed" &&
                    release.review.firstScanAttestationId != null &&
                    release.record.timestamps.publicDelayEndsAt
                    ?.let(Instant::parse)?.let { !it.isAfter(now) } == true)
        }.minWithOrNull(compareBy<StoredRelease> {
            runCatching { Instant.parse(it.record.timestamps.updatedAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.record.`package` }.thenBy { it.record.version }) ?: return ReviewWorkerOutcome.NO_WORK
        val phase = if (candidate.record.state.lifecycle == "quarantined") ReviewPhase.FIRST else ReviewPhase.SECOND
        val claimed = stateMachine.claimSourceVerification(candidate, phase, now)
        val appliedClaim = when (val transition = repository.transitionRelease(candidate.revision, claimed)) {
            is ReleaseTransitionResult.Applied -> transition.value
            is ReleaseTransitionResult.Conflict -> return ReviewWorkerOutcome.CONFLICT
            ReleaseTransitionResult.Missing -> return ReviewWorkerOutcome.CONFLICT
        }

        return try {
            val pkg = repository.getPackage(appliedClaim.record.`package`)
                ?: return reject(appliedClaim, phase, "source_package_missing", "package record is missing")
            val repositoryUrl = pkg.record.repository
                ?: return reject(appliedClaim, phase, "source_repository_missing", "canonical repository URL is missing")
            val inspection = inspector.inspect(appliedClaim)
            if (inspection.archiveSha256 != appliedClaim.record.archive.sha256) {
                return reject(appliedClaim, phase, "source_archive_digest_mismatch", "archive inspection digest differs")
            }
            val result = sourceProofEngine.verify(SourceVerificationInput(
                forge = SourceForge.fromWireName(appliedClaim.source.forge),
                repositoryId = appliedClaim.source.repositoryId,
                canonicalRepositoryUrl = repositoryUrl,
                installationIdentity = appliedClaim.source.installationId,
                requestedRef = appliedClaim.source.requestedRef,
                expectedCommit = appliedClaim.source.expectedCommit,
                archiveSha256 = inspection.archiveSha256,
                manifestBytes = inspection.manifestBytes,
                archiveFiles = inspection.files,
                declaredPackagePaths = inspection.declaredPackagePaths,
                licenseSpdx = appliedClaim.source.licenseSpdx,
            ))
            if (!ReviewEvidenceValidator.sourceResultMatches(result, appliedClaim, pkg, clock.instant())) {
                return reject(appliedClaim, phase, "source_result_binding_mismatch", "source verifier returned mismatched evidence")
            }
            val sequence = nextSequence(appliedClaim)
            val previous = if (phase == ReviewPhase.FIRST) {
                null
            } else {
                requireNotNull(appliedClaim.review.firstSourceProofId) { "First source proof binding is missing" }
            }
            val proof = result.toSourceProofRecord(
                proofId = ids.next("prf_"),
                sequence = sequence,
                previousProofId = previous,
                packageIdentity = appliedClaim.record.`package`,
                version = appliedClaim.record.version,
            )
            if (!ReviewEvidenceValidator.sourceProofMatches(proof, appliedClaim, pkg)) {
                return reject(appliedClaim, phase, "source_proof_record_invalid", "source proof failed canonical validation")
            }
            check(repository.appendReviewArtifact(proof.toArtifact())) { "Source proof ID collision" }
            val reviewed = stateMachine.recordVerifiedSource(
                appliedClaim,
                VerifiedSourceEvidence(phase, proof.sequence, proof.proofId, proof.sha256(), inspection.archiveSha256),
                clock.instant(),
            )
            val updated = reviewed.copy(record = reviewed.record.copy(
                archive = reviewed.record.archive.copy(
                    expandedBytes = inspection.expandedBytes,
                    entryCount = inspection.entryCount,
                    largestRegularFileBytes = inspection.largestRegularFileBytes,
                    longestPathBytes = inspection.longestPathBytes,
                    maximumPathDepth = inspection.maximumPathDepth,
                ),
            ))
            when (repository.transitionRelease(appliedClaim.revision, updated)) {
                is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.APPLIED
                else -> ReviewWorkerOutcome.CONFLICT
            }
        } catch (error: SourceVerificationException) {
            if (error.reason in setOf(SourceVerificationFailure.FORGE_TIMEOUT, SourceVerificationFailure.FORGE_UNAVAILABLE)) {
                retry(appliedClaim, phase, "source_${error.reason.name.lowercase()}")
            } else {
                reject(appliedClaim, phase, "source_${error.reason.name.lowercase()}", error.reason.name)
            }
        } catch (error: RegistryException) {
            if (error.code == "archive_rejected" || error.code == "digest_mismatch") {
                reject(appliedClaim, phase, error.code, error.publicMessage)
            } else {
                retry(appliedClaim, phase, "source_worker_registry_error")
            }
        } catch (_: Exception) {
            retry(appliedClaim, phase, "source_worker_error")
        }
    }

    private fun recoverExpiredClaim(now: Instant): ReviewWorkerOutcome? {
        val candidate = repository.listReleases().asSequence().mapNotNull { release ->
            val phase = release.scanningPhase() ?: return@mapNotNull null
            if (stateMachine.isExpiredReviewClaim(release, phase, ReviewClaimKind.SOURCE, now)) {
                release to phase
            } else {
                null
            }
        }.minWithOrNull(compareBy<Pair<StoredRelease, ReviewPhase>> {
            runCatching { Instant.parse(it.first.record.timestamps.updatedAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.first.record.`package` }.thenBy { it.first.record.version }) ?: return null
        val (release, phase) = candidate
        appendAudit(release, "source-verification", "retryable-failure", REVIEW_CLAIM_LEASE_EXPIRED)
        val recovered = stateMachine.recoverExpiredReviewClaim(
            release,
            phase,
            ReviewClaimKind.SOURCE,
            now,
        )
        return when (repository.transitionRelease(release.revision, recovered)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.RETRYABLE_FAILURE
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun retry(current: StoredRelease, phase: ReviewPhase, reason: String): ReviewWorkerOutcome {
        appendAudit(current, "source-verification", "retryable-failure", reason)
        val updated = stateMachine.recordRetryableReviewFailure(current, phase, clock.instant())
        return when (repository.transitionRelease(current.revision, updated)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.RETRYABLE_FAILURE
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun reject(current: StoredRelease, phase: ReviewPhase, code: String, reason: String): ReviewWorkerOutcome {
        val audit = appendAudit(current, "source-verification", "rejected", reason)
        val stableCode = code.lowercase().replace(Regex("[^a-z0-9_]+"), "_").take(96).ifBlank { "source_proof_invalid" }
        val updated = stateMachine.reject(current, phase, stableCode, audit.eventId, clock.instant())
        return when (repository.transitionRelease(current.revision, updated)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.REJECTED
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun appendAudit(current: StoredRelease, action: String, outcome: String, reason: String): AuditEventRecord {
        val event = AuditEventRecord(
            eventId = ids.next("aud_"),
            sequence = nextSequence(current),
            action = action,
            outcome = outcome,
            actor = AuditActor("worker", "source-verifier"),
            subject = current.auditSubject(),
            internalReason = reason.take(512),
            occurredAt = clock.instant().utc(),
        )
        check(repository.appendReviewArtifact(event.toArtifact())) { "Audit event ID collision" }
        return event
    }

    private fun nextSequence(release: StoredRelease): Long =
        (repository.listReviewArtifacts(release.record.`package`, release.record.version).maxOfOrNull { it.sequence } ?: 0L) + 1L
}

class ScanReviewWorker(
    private val repository: RegistryRepository,
    private val inspector: ReviewArchiveInspector,
    private val scanner: PackageScanEngine,
    private val stateMachine: ReviewStateMachine,
    private val clock: Clock,
    private val ids: ReviewIdGenerator = SecureReviewIdGenerator(),
) {
    fun runOnce(): ReviewWorkerOutcome {
        val now = clock.instant()
        recoverExpiredClaim(now)?.let { return it }
        val candidate = repository.listReleases().asSequence().filter { release ->
            if (release.review.activeReviewClaim != null) return@filter false
            when (release.record.state.lifecycle) {
                "first-scanning" -> release.review.firstSourceProofId != null &&
                    release.record.sourceProofId == release.review.firstSourceProofId &&
                    release.record.verification.source == "passed" &&
                    release.record.verification.firstScan == "pending"
                "second-scanning" -> release.review.secondSourceProofId != null &&
                    release.record.sourceProofId == release.review.secondSourceProofId &&
                    release.record.verification.source == "passed" &&
                    release.record.verification.secondScan == "pending"
                else -> false
            }
        }.minWithOrNull(compareBy<StoredRelease> {
            runCatching { Instant.parse(it.record.timestamps.updatedAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.record.`package` }.thenBy { it.record.version }) ?: return ReviewWorkerOutcome.NO_WORK
        val phase = if (candidate.record.state.lifecycle == "first-scanning") ReviewPhase.FIRST else ReviewPhase.SECOND
        val requestedClaim = stateMachine.claimPackageScan(candidate, phase, now)
        val claimed = when (val transition = repository.transitionRelease(candidate.revision, requestedClaim)) {
            is ReleaseTransitionResult.Applied -> transition.value
            is ReleaseTransitionResult.Conflict -> return ReviewWorkerOutcome.CONFLICT
            ReleaseTransitionResult.Missing -> return ReviewWorkerOutcome.CONFLICT
        }
        val proofId = if (phase == ReviewPhase.FIRST) claimed.review.firstSourceProofId else claimed.review.secondSourceProofId
        val proofSha = if (phase == ReviewPhase.FIRST) claimed.review.firstSourceProofSha256 else claimed.review.secondSourceProofSha256
        if (proofId == null || proofSha == null) return retry(claimed, phase, "source_proof_binding_missing")

        return try {
            val proofArtifact = repository.getReviewArtifact(proofId)
                ?: return retry(claimed, phase, "source_proof_record_missing")
            if (proofArtifact.kind != SOURCE_PROOF_ARTIFACT) return reject(claimed, phase, "source_proof_record_invalid")
            val proof = RegistryJson.decodeFromJsonElement<SourceProofRecord>(proofArtifact.payload)
            val pkg = repository.getPackage(claimed.record.`package`)
                ?: return reject(claimed, phase, "source_package_missing")
            if (proof.sha256() != proofSha || !ReviewEvidenceValidator.sourceProofMatches(proof, claimed, pkg)) {
                return reject(claimed, phase, "source_proof_digest_mismatch")
            }
            val inspection = inspector.inspect(claimed)
            if (inspection.archiveSha256 != claimed.record.archive.sha256) {
                return reject(claimed, phase, "scanner_archive_digest_mismatch")
            }
            val sequence = nextSequence(claimed)
            val previousAttestationId = if (phase == ReviewPhase.FIRST) null else claimed.review.firstScanAttestationId
            val attestation = scanner.scan(PackageScanInput(
                phase = phase,
                attestationId = ids.next("scn_"),
                sequence = sequence,
                packageIdentity = claimed.record.`package`,
                version = claimed.record.version,
                archiveSha256 = inspection.archiveSha256,
                sourceProof = proof,
                sourceProofSha256 = proofSha,
                files = inspection.files,
                previousAttestationId = previousAttestationId,
            )) ?: return retry(claimed, phase, "scanner_result_missing")
            if (attestation.subject.sourceProofId != proofId || attestation.subject.sourceProofSha256 != proofSha ||
                attestation.sequence != sequence || attestation.previousAttestationId != previousAttestationId ||
                !ReviewEvidenceValidator.scanMatches(attestation, claimed, phase, proof)
            ) {
                return reject(claimed, phase, "scanner_evidence_mismatch")
            }
            check(repository.appendReviewArtifact(attestation.toArtifact())) { "Scan attestation ID collision" }
            when (attestation.result.status) {
                "passed" -> {
                    val updated = stateMachine.recordPassedScan(
                        claimed,
                        PassedScanEvidence(
                            phase,
                            attestation.sequence,
                            attestation.attestationId,
                            attestation.sha256(),
                            claimed.record.archive.sha256,
                            proofId,
                            proofSha,
                        ),
                        clock.instant(),
                    )
                    when (repository.transitionRelease(claimed.revision, updated)) {
                        is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.APPLIED
                        else -> ReviewWorkerOutcome.CONFLICT
                    }
                }
                "failed" -> reject(claimed, phase, "scan_policy_failed", listOf(attestation.attestationId))
                "error", "timeout" -> retry(claimed, phase, "scanner_${attestation.result.status}", listOf(attestation.attestationId))
                else -> retry(claimed, phase, "scanner_result_invalid", listOf(attestation.attestationId))
            }
        } catch (_: PackageScanTimeoutException) {
            retry(claimed, phase, "scanner_timeout")
        } catch (_: Exception) {
            retry(claimed, phase, "scanner_error")
        }
    }

    private fun recoverExpiredClaim(now: Instant): ReviewWorkerOutcome? {
        val candidate = repository.listReleases().asSequence().mapNotNull { release ->
            val phase = release.scanningPhase() ?: return@mapNotNull null
            if (stateMachine.isExpiredReviewClaim(release, phase, ReviewClaimKind.SCAN, now)) {
                release to phase
            } else {
                null
            }
        }.minWithOrNull(compareBy<Pair<StoredRelease, ReviewPhase>> {
            runCatching { Instant.parse(it.first.record.timestamps.updatedAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.first.record.`package` }.thenBy { it.first.record.version }) ?: return null
        val (release, phase) = candidate
        appendAudit(release, "package-scan", "retryable-failure", REVIEW_CLAIM_LEASE_EXPIRED, emptyList())
        val recovered = stateMachine.recoverExpiredReviewClaim(
            release,
            phase,
            ReviewClaimKind.SCAN,
            now,
        )
        return when (repository.transitionRelease(release.revision, recovered)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.RETRYABLE_FAILURE
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun retry(current: StoredRelease, phase: ReviewPhase, reason: String, evidence: List<String> = emptyList()): ReviewWorkerOutcome {
        appendAudit(current, "package-scan", "retryable-failure", reason, evidence)
        val updated = stateMachine.recordRetryableReviewFailure(current, phase, clock.instant())
        return when (repository.transitionRelease(current.revision, updated)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.RETRYABLE_FAILURE
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun reject(current: StoredRelease, phase: ReviewPhase, reason: String, evidence: List<String> = emptyList()): ReviewWorkerOutcome {
        val audit = appendAudit(current, "package-scan", "rejected", reason, evidence)
        val updated = stateMachine.reject(current, phase, reason, audit.eventId, clock.instant())
        return when (repository.transitionRelease(current.revision, updated)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.REJECTED
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun appendAudit(
        current: StoredRelease,
        action: String,
        outcome: String,
        reason: String,
        evidence: List<String>,
    ): AuditEventRecord {
        val event = AuditEventRecord(
            eventId = ids.next("aud_"),
            sequence = nextSequence(current),
            action = action,
            outcome = outcome,
            actor = AuditActor("worker", "package-scanner"),
            subject = current.auditSubject(),
            evidenceIds = evidence,
            internalReason = reason,
            occurredAt = clock.instant().utc(),
        )
        check(repository.appendReviewArtifact(event.toArtifact())) { "Audit event ID collision" }
        return event
    }

    private fun nextSequence(release: StoredRelease): Long =
        (repository.listReviewArtifacts(release.record.`package`, release.record.version).maxOfOrNull { it.sequence } ?: 0L) + 1L
}


private fun StoredRelease.auditSubject() = AuditSubject(record.`package`, record.version, record.archive.sha256)

private fun StoredRelease.scanningPhase(): ReviewPhase? = when (record.state.lifecycle) {
    "first-scanning" -> ReviewPhase.FIRST
    "second-scanning" -> ReviewPhase.SECOND
    else -> null
}

private const val REVIEW_CLAIM_LEASE_EXPIRED = "review_claim_lease_expired"
