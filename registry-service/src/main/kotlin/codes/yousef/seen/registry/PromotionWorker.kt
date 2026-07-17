package codes.yousef.seen.registry

import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Clock
import java.time.Instant

fun interface ReleaseMetadataPublisher {
    fun publish(publicReleases: List<StoredRelease>): Long
}

/** Promotes only a release whose complete, immutable review chain revalidates. */
class PromotionReviewWorker(
    private val repository: RegistryRepository,
    private val storage: RegistryObjectStorage,
    private val inspector: ReviewArchiveInspector,
    private val metadata: ReleaseMetadataPublisher,
    private val stateMachine: ReviewStateMachine,
    private val clock: Clock,
    private val ids: ReviewIdGenerator = SecureReviewIdGenerator(),
) {
    fun runOnce(): ReviewWorkerOutcome {
        val candidate = repository.listReleases().firstOrNull {
            it.record.state.lifecycle == "ready" &&
                it.record.state.availability == "unavailable" &&
                it.record.state.visibility == "public"
        } ?: return ReviewWorkerOutcome.NO_WORK

        val claimed = candidate.review.promotionAttemptId?.let { candidate } ?: run {
            val requested = stateMachine.claimPromotion(candidate, ids.next("act_"), clock.instant())
            when (val transition = repository.transitionRelease(candidate.revision, requested)) {
                is ReleaseTransitionResult.Applied -> transition.value
                is ReleaseTransitionResult.Conflict -> return ReviewWorkerOutcome.CONFLICT
                ReleaseTransitionResult.Missing -> return ReviewWorkerOutcome.CONFLICT
            }
        }
        val attemptId = requireNotNull(claimed.review.promotionAttemptId)
        var publicationAttempted = false

        try {
            validateEvidence(claimed)
            val inspection = inspector.inspect(claimed)
            if (inspection.archiveSha256 != claimed.record.archive.sha256) {
                throw PromotionEvidenceException("promotion_archive_digest_mismatch")
            }
            storage.copyQuarantineToPublic(
                claimed.uploadId,
                claimed.record.archive.sha256,
                claimed.record.archive.compressedBytes,
            )

            // Metadata must describe the same active projection that is committed
            // below; no mutable release input is read after this point.
            val activatedAt = clock.instant()
            val draftActive = stateMachine.activate(claimed, attemptId, 1, activatedAt)
            val public = repository.listReleases().filter {
                it.record.state.visibility == "public" && it.record.state.availability in setOf("available", "yanked")
            }.filterNot {
                it.record.`package` == draftActive.record.`package` && it.record.version == draftActive.record.version
            } + draftActive
            publicationAttempted = true
            val metadataVersion = metadata.publish(public)
            val activated = stateMachine.activate(claimed, attemptId, metadataVersion, activatedAt)
            val activationAudit = auditEvent(
                activated,
                "promotion",
                "activated",
                listOf(attemptId),
                occurredAt = activatedAt,
            ).toArtifact()
            return when (val commit = repository.commitPromotionActivation(
                claimed.revision,
                activated,
                activationAudit,
            )) {
                is PromotionActivationResult.Applied -> {
                    runCatching { storage.deleteQuarantine(commit.value.uploadId) }
                    ReviewWorkerOutcome.APPLIED
                }
                is PromotionActivationResult.Conflict,
                PromotionActivationResult.MissingRelease -> {
                    reconcileMetadata()
                    ReviewWorkerOutcome.CONFLICT
                }
                PromotionActivationResult.MissingPackage,
                PromotionActivationResult.AuditCollision -> {
                    reconcileMetadata()
                    appendAudit(
                        claimed,
                        "promotion",
                        "retryable-failure",
                        listOf(attemptId),
                        if (commit == PromotionActivationResult.MissingPackage) {
                            "promotion_package_missing_at_commit"
                        } else {
                            "promotion_activation_audit_collision"
                        },
                    )
                    ReviewWorkerOutcome.RETRYABLE_FAILURE
                }
            }
        } catch (failure: PromotionEvidenceException) {
            if (publicationAttempted) reconcileMetadata()
            return reject(claimed, failure.code)
        } catch (failure: RegistryException) {
            if (publicationAttempted) reconcileMetadata()
            if (failure.code in setOf("archive_rejected", "digest_mismatch")) {
                return reject(claimed, "promotion_archive_invalid")
            }
            appendAudit(claimed, "promotion", "retryable-failure", listOf(attemptId), failure.code)
            return ReviewWorkerOutcome.RETRYABLE_FAILURE
        } catch (failure: Exception) {
            if (publicationAttempted) reconcileMetadata()
            appendAudit(claimed, "promotion", "retryable-failure", listOf(attemptId), "promotion_worker_error")
            return ReviewWorkerOutcome.RETRYABLE_FAILURE
        }
    }

    private fun validateEvidence(release: StoredRelease) {
        if (release.review.validatedArchiveSha256 != release.record.archive.sha256) invalid("promotion_archive_binding_missing")
        val pkg = repository.getPackage(release.record.`package`) ?: invalid("promotion_package_missing")
        val firstProof = sourceProof(
            release,
            pkg,
            requireId(release.review.firstSourceProofId),
            requireDigest(release.review.firstSourceProofSha256),
        )
        val firstScan = scan(
            release,
            ReviewPhase.FIRST,
            requireId(release.review.firstScanAttestationId),
            requireDigest(release.review.firstScanAttestationSha256),
            firstProof,
        )
        val secondProof = sourceProof(
            release,
            pkg,
            requireId(release.review.secondSourceProofId),
            requireDigest(release.review.secondSourceProofSha256),
        )
        if (secondProof.proofId == firstProof.proofId) invalid("promotion_fresh_source_proof_missing")
        val secondScan = scan(
            release,
            ReviewPhase.SECOND,
            requireId(release.review.secondScanAttestationId),
            requireDigest(release.review.secondScanAttestationSha256),
            secondProof,
        )
        if (
            firstProof.previousProofId != null ||
            secondProof.proofId == firstProof.proofId ||
            secondProof.previousProofId != firstProof.proofId ||
            firstScan.previousAttestationId != null ||
            secondScan.previousAttestationId != firstScan.attestationId ||
            firstProof.sequence >= firstScan.sequence ||
            firstScan.sequence >= secondProof.sequence ||
            secondProof.sequence >= secondScan.sequence ||
            release.record.verification.attestationSequence != secondScan.sequence
        ) invalid("promotion_evidence_chain_invalid")
        if (release.record.sourceProofId != secondProof.proofId) invalid("promotion_current_source_proof_mismatch")
    }

    private fun sourceProof(
        release: StoredRelease,
        pkg: StoredPackage,
        id: String,
        digest: String,
    ): SourceProofRecord {
        val artifact = repository.getReviewArtifact(id) ?: invalid("promotion_source_proof_missing")
        if (artifact.kind != SOURCE_PROOF_ARTIFACT) invalid("promotion_source_proof_invalid")
        val proof = runCatching { RegistryJson.decodeFromJsonElement<SourceProofRecord>(artifact.payload) }
            .getOrElse { invalid("promotion_source_proof_invalid") }
        if (proof.sha256() != digest || proof.proofId != id ||
            !ReviewEvidenceValidator.sourceProofMatches(proof, release, pkg)
        ) invalid("promotion_source_proof_mismatch")
        return proof
    }

    private fun scan(
        release: StoredRelease,
        phase: ReviewPhase,
        id: String,
        digest: String,
        proof: SourceProofRecord,
    ): ScanAttestationRecord {
        val artifact = repository.getReviewArtifact(id) ?: invalid("promotion_scan_attestation_missing")
        if (artifact.kind != SCAN_ATTESTATION_ARTIFACT) invalid("promotion_scan_attestation_invalid")
        val attestation = runCatching { RegistryJson.decodeFromJsonElement<ScanAttestationRecord>(artifact.payload) }
            .getOrElse { invalid("promotion_scan_attestation_invalid") }
        if (attestation.sha256() != digest || attestation.attestationId != id ||
            !ReviewEvidenceValidator.scanMatches(attestation, release, phase, proof, requirePassed = true)
        ) invalid("promotion_scan_attestation_mismatch")
        return attestation
    }

    private fun reject(current: StoredRelease, code: String): ReviewWorkerOutcome {
        val audit = appendAudit(current, "promotion", "rejected", listOfNotNull(current.review.promotionAttemptId), code)
        val rejected = stateMachine.rejectPromotion(current, code, audit.eventId, clock.instant())
        return when (repository.transitionRelease(current.revision, rejected)) {
            is ReleaseTransitionResult.Applied -> ReviewWorkerOutcome.REJECTED
            else -> ReviewWorkerOutcome.CONFLICT
        }
    }

    private fun appendAudit(
        release: StoredRelease,
        action: String,
        outcome: String,
        evidence: List<String>,
        reason: String? = null,
    ): AuditEventRecord {
        val event = auditEvent(release, action, outcome, evidence, reason)
        check(repository.appendReviewArtifact(event.toArtifact())) { "Audit event ID collision" }
        return event
    }

    private fun auditEvent(
        release: StoredRelease,
        action: String,
        outcome: String,
        evidence: List<String>,
        reason: String? = null,
        occurredAt: Instant = clock.instant(),
    ): AuditEventRecord = AuditEventRecord(
        eventId = ids.next("aud_"),
        sequence = (repository.listReviewArtifacts(release.record.`package`, release.record.version)
            .maxOfOrNull(ReviewArtifact::sequence) ?: 0L) + 1L,
        action = action,
        outcome = outcome,
        actor = AuditActor("worker", "release-promoter"),
        subject = AuditSubject(release.record.`package`, release.record.version, release.record.archive.sha256),
        evidenceIds = evidence,
        internalReason = reason,
        occurredAt = occurredAt.utc(),
    )

    private fun reconcileMetadata() {
        // Restore signed selection from committed state if a security/policy
        // transition won the CAS or the atomic repository commit could not run.
        runCatching {
            metadata.publish(repository.listReleases().filter {
                it.record.state.visibility == "public" &&
                    it.record.state.availability in setOf("available", "yanked")
            })
        }
    }

    private fun requireId(value: String?): String = value ?: invalid("promotion_evidence_id_missing")
    private fun requireDigest(value: String?): String = value ?: invalid("promotion_evidence_digest_missing")
    private fun invalid(code: String): Nothing = throw PromotionEvidenceException(code)
}

private class PromotionEvidenceException(val code: String) : RuntimeException(code)
