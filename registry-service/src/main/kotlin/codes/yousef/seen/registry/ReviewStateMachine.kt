package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable
data class ReviewEvidenceState(
    @SerialName("validated_archive_sha256") val validatedArchiveSha256: String? = null,
    @SerialName("first_source_proof_id") val firstSourceProofId: String? = null,
    @SerialName("first_source_proof_sha256") val firstSourceProofSha256: String? = null,
    @SerialName("first_scan_attestation_id") val firstScanAttestationId: String? = null,
    @SerialName("first_scan_attestation_sha256") val firstScanAttestationSha256: String? = null,
    @SerialName("second_source_proof_id") val secondSourceProofId: String? = null,
    @SerialName("second_source_proof_sha256") val secondSourceProofSha256: String? = null,
    @SerialName("second_scan_attestation_id") val secondScanAttestationId: String? = null,
    @SerialName("second_scan_attestation_sha256") val secondScanAttestationSha256: String? = null,
    @SerialName("promotion_attempt_id") val promotionAttemptId: String? = null,
    @SerialName("promotion_input_sha256") val promotionInputSha256: String? = null,
    @SerialName("rejection_code") val rejectionCode: String? = null,
    @SerialName("rejection_evidence_id") val rejectionEvidenceId: String? = null,
    /** Internal lease owner. This is persisted with [StoredRelease], not exposed by the release contract. */
    @SerialName("active_review_claim") val activeReviewClaim: String? = null,
)

data class VerifiedSourceEvidence(
    val phase: ReviewPhase,
    val sequence: Long,
    val proofId: String,
    val proofSha256: String,
    val archiveSha256: String,
)

data class PassedScanEvidence(
    val phase: ReviewPhase,
    val sequence: Long,
    val attestationId: String,
    val attestationSha256: String,
    val archiveSha256: String,
    val sourceProofId: String,
    val sourceProofSha256: String,
)

enum class ReviewPhase { FIRST, SECOND }

enum class ReviewClaimKind { SOURCE, SCAN }

/**
 * Pure, fail-closed implementation of the release review lifecycle.
 *
 * Persistence callers must commit the returned value with
 * [RegistryRepository.transitionRelease]. Every returned snapshot advances the
 * compare-and-set revision exactly once, so stale workers cannot overwrite a
 * newer review, rejection, quarantine, or activation decision.
 */
class ReviewStateMachine(
    private val publicDelay: Duration,
    private val reviewClaimLease: Duration = Duration.ofMinutes(10),
) {
    init {
        require(publicDelay >= Duration.ofHours(72)) { "Public review delay must be at least 72 hours" }
        require(!reviewClaimLease.isZero && !reviewClaimLease.isNegative) { "Review claim lease must be positive" }
        require(reviewClaimLease <= Duration.ofHours(1)) { "Review claim lease must not exceed one hour" }
    }

    fun claimSourceVerification(current: StoredRelease, phase: ReviewPhase, now: Instant): StoredRelease = when (phase) {
        ReviewPhase.FIRST -> {
            require(current.record.state.lifecycle == "quarantined") { "First source verification requires quarantined state" }
            require(current.review.activeReviewClaim == null) { "Release review is already claimed" }
            current.next(
                record = current.record.copy(
                    state = current.record.state.copy(lifecycle = "first-scanning"),
                    verification = current.record.verification.copy(
                        origin = "pending",
                        integrity = "pending",
                        source = "pending",
                        firstScan = "pending",
                    ),
                    timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                ),
                review = current.review.copy(activeReviewClaim = claimName(phase, ReviewClaimKind.SOURCE)),
            )
        }

        ReviewPhase.SECOND -> {
            require(current.record.state.visibility == "public") { "Second source verification is public-only" }
            require(current.record.state.lifecycle == "delayed") { "Second source verification requires delayed state" }
            require(current.record.verification.firstScan == "passed") { "First scan evidence is missing" }
            require(current.review.activeReviewClaim == null) { "Release review is already claimed" }
            val delayEnds = current.record.timestamps.publicDelayEndsAt?.let(Instant::parse)
                ?: error("Public delay deadline is missing")
            require(!delayEnds.isAfter(now)) { "Public review delay has not elapsed" }
            current.next(
                record = current.record.copy(
                    state = current.record.state.copy(lifecycle = "second-scanning"),
                    verification = current.record.verification.copy(
                        origin = "pending",
                        integrity = "pending",
                        source = "pending",
                        secondScan = "pending",
                    ),
                    timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                ),
                review = current.review.copy(activeReviewClaim = claimName(phase, ReviewClaimKind.SOURCE)),
            )
        }
    }

    fun recordVerifiedSource(current: StoredRelease, evidence: VerifiedSourceEvidence, now: Instant): StoredRelease {
        requireDigestBinding(current, evidence.archiveSha256)
        requireEvidenceSequence(current, evidence.sequence)
        requireEvidenceId(evidence.proofId, "prf_")
        IdentityRules.requireDigest(evidence.proofSha256, "source proof digest")
        requireClaim(current, evidence.phase, ReviewClaimKind.SOURCE)
        return when (evidence.phase) {
            ReviewPhase.FIRST -> {
                require(current.record.state.lifecycle == "first-scanning") { "First source proof requires first-scanning state" }
                require(current.review.promotionAttemptId == null) { "Release is already being promoted" }
                current.next(
                    record = current.record.copy(
                        state = current.record.state.copy(lifecycle = "first-scanning"),
                        sourceProofId = evidence.proofId,
                        verification = current.record.verification.copy(
                            origin = "passed",
                            integrity = "pending",
                            source = "passed",
                            firstScan = "pending",
                            attestationSequence = evidence.sequence,
                        ),
                        timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                        links = current.record.links.copy(sourceProof = sourceProofPath(current, evidence.proofId)),
                    ),
                    review = current.review.copy(
                        validatedArchiveSha256 = evidence.archiveSha256,
                        firstSourceProofId = evidence.proofId,
                        firstSourceProofSha256 = evidence.proofSha256,
                        rejectionCode = null,
                        rejectionEvidenceId = null,
                        activeReviewClaim = null,
                    ),
                )
            }

            ReviewPhase.SECOND -> {
                require(current.record.state.visibility == "public") { "Second source proof is public-only" }
                require(current.record.state.lifecycle == "second-scanning") { "Second source proof requires second-scanning state" }
                require(current.record.verification.firstScan == "passed") { "First scan evidence is missing" }
                require(evidence.proofId != current.review.firstSourceProofId) { "Second source proof must be a fresh record" }
                current.next(
                    record = current.record.copy(
                        state = current.record.state.copy(lifecycle = "second-scanning"),
                        sourceProofId = evidence.proofId,
                        verification = current.record.verification.copy(
                            origin = "passed",
                            integrity = "pending",
                            source = "passed",
                            secondScan = "pending",
                            attestationSequence = evidence.sequence,
                        ),
                        timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                        links = current.record.links.copy(sourceProof = sourceProofPath(current, evidence.proofId)),
                    ),
                    review = current.review.copy(
                        validatedArchiveSha256 = evidence.archiveSha256,
                        secondSourceProofId = evidence.proofId,
                        secondSourceProofSha256 = evidence.proofSha256,
                        activeReviewClaim = null,
                    ),
                )
            }
        }
    }

    fun claimPackageScan(current: StoredRelease, phase: ReviewPhase, now: Instant): StoredRelease {
        val expectedLifecycle = if (phase == ReviewPhase.FIRST) "first-scanning" else "second-scanning"
        require(current.record.state.lifecycle == expectedLifecycle) { "Package scan requires $expectedLifecycle state" }
        require(current.review.activeReviewClaim == null) { "Release review is already claimed" }
        require(current.record.verification.origin == "passed" && current.record.verification.source == "passed") {
            "Package scan requires verified source evidence"
        }
        val proofId = if (phase == ReviewPhase.FIRST) current.review.firstSourceProofId else current.review.secondSourceProofId
        val proofSha256 = if (phase == ReviewPhase.FIRST) current.review.firstSourceProofSha256 else current.review.secondSourceProofSha256
        require(proofId != null && proofSha256 != null && current.record.sourceProofId == proofId) {
            "Package scan source proof binding is missing"
        }
        val scanStatus = if (phase == ReviewPhase.FIRST) {
            current.record.verification.firstScan
        } else {
            require(current.record.verification.firstScan == "passed") { "First scan evidence is missing" }
            current.record.verification.secondScan
        }
        require(scanStatus == "pending") { "Package scan is not pending" }
        return current.next(
            record = current.record.copy(timestamps = current.record.timestamps.copy(updatedAt = now.utc())),
            review = current.review.copy(activeReviewClaim = claimName(phase, ReviewClaimKind.SCAN)),
        )
    }

    fun recordPassedScan(current: StoredRelease, evidence: PassedScanEvidence, now: Instant): StoredRelease {
        requireDigestBinding(current, evidence.archiveSha256)
        requireEvidenceSequence(current, evidence.sequence)
        requireEvidenceId(evidence.attestationId, "scn_")
        IdentityRules.requireDigest(evidence.attestationSha256, "scan attestation digest")
        requireClaim(current, evidence.phase, ReviewClaimKind.SCAN)
        return when (evidence.phase) {
            ReviewPhase.FIRST -> {
                require(current.record.state.lifecycle == "first-scanning") { "First scan requires first-scanning state" }
                requireSourceBinding(current.review.firstSourceProofId, current.review.firstSourceProofSha256, evidence)
                val public = current.record.state.visibility == "public"
                val delayEnds = if (public) now.plus(publicDelay) else null
                current.next(
                    record = current.record.copy(
                        state = current.record.state.copy(lifecycle = if (public) "delayed" else "ready"),
                        verification = current.record.verification.copy(
                            integrity = "passed",
                            firstScan = "passed",
                            secondScan = if (public) "pending" else "not-required",
                            attestationSequence = evidence.sequence,
                        ),
                        timestamps = current.record.timestamps.copy(
                            publicDelayStartedAt = if (public) now.utc() else null,
                            publicDelayEndsAt = delayEnds?.utc(),
                            readyAt = if (public) null else now.utc(),
                            updatedAt = now.utc(),
                        ),
                    ),
                    review = current.review.copy(
                        firstScanAttestationId = evidence.attestationId,
                        firstScanAttestationSha256 = evidence.attestationSha256,
                        activeReviewClaim = null,
                    ),
                )
            }

            ReviewPhase.SECOND -> {
                require(current.record.state.visibility == "public") { "Second scan is public-only" }
                require(current.record.state.lifecycle == "second-scanning") { "Second scan requires second-scanning state" }
                requireSourceBinding(current.review.secondSourceProofId, current.review.secondSourceProofSha256, evidence)
                current.next(
                    record = current.record.copy(
                        state = current.record.state.copy(lifecycle = "ready"),
                        verification = current.record.verification.copy(
                            integrity = "passed",
                            secondScan = "passed",
                            attestationSequence = evidence.sequence,
                        ),
                        timestamps = current.record.timestamps.copy(readyAt = now.utc(), updatedAt = now.utc()),
                    ),
                    review = current.review.copy(
                        secondScanAttestationId = evidence.attestationId,
                        secondScanAttestationSha256 = evidence.attestationSha256,
                        activeReviewClaim = null,
                    ),
                )
            }
        }
    }

    fun recordRetryableReviewFailure(current: StoredRelease, phase: ReviewPhase, now: Instant): StoredRelease = when (phase) {
        ReviewPhase.FIRST -> {
            require(current.record.state.lifecycle == "first-scanning") { "First review retry requires first-scanning state" }
            requirePhaseClaim(current, phase)
            current.next(
                record = current.record.copy(
                    state = current.record.state.copy(lifecycle = "quarantined"),
                    sourceProofId = null,
                    verification = current.record.verification.copy(
                        origin = "pending",
                        integrity = "pending",
                        source = "pending",
                        firstScan = "inconclusive",
                    ),
                    timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                    links = current.record.links.copy(sourceProof = null),
                ),
                review = current.review.copy(activeReviewClaim = null),
            )
        }

        ReviewPhase.SECOND -> {
            require(current.record.state.lifecycle == "second-scanning") { "Second review retry requires second-scanning state" }
            requirePhaseClaim(current, phase)
            current.next(
                record = current.record.copy(
                    state = current.record.state.copy(lifecycle = "delayed"),
                    sourceProofId = current.review.firstSourceProofId,
                    verification = current.record.verification.copy(
                        origin = "passed",
                        integrity = "passed",
                        source = "passed",
                        secondScan = "inconclusive",
                    ),
                    timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                    links = current.record.links.copy(
                        sourceProof = current.review.firstSourceProofId?.let { sourceProofPath(current, it) },
                    ),
                ),
                review = current.review.copy(activeReviewClaim = null),
            )
        }
    }

    fun isExpiredReviewClaim(
        current: StoredRelease,
        phase: ReviewPhase,
        kind: ReviewClaimKind,
        now: Instant,
    ): Boolean {
        val expectedLifecycle = if (phase == ReviewPhase.FIRST) "first-scanning" else "second-scanning"
        if (current.record.state.lifecycle != expectedLifecycle || !hasClaim(current, phase, kind)) return false
        val updatedAt = runCatching { Instant.parse(current.record.timestamps.updatedAt) }.getOrNull() ?: return false
        return !now.isBefore(updatedAt.plus(reviewClaimLease))
    }

    fun recoverExpiredReviewClaim(
        current: StoredRelease,
        phase: ReviewPhase,
        kind: ReviewClaimKind,
        now: Instant,
    ): StoredRelease {
        require(isExpiredReviewClaim(current, phase, kind, now)) { "Review claim lease has not expired" }
        return recordRetryableReviewFailure(current, phase, now)
    }

    fun reject(current: StoredRelease, phase: ReviewPhase, code: String, evidenceId: String, now: Instant): StoredRelease {
        require(code.matches(Regex("^[a-z0-9_]{3,96}$"))) { "Rejection code is invalid" }
        requireEvidenceId(evidenceId, "aud_")
        val expected = if (phase == ReviewPhase.FIRST) "first-scanning" else "second-scanning"
        require(current.record.state.lifecycle == expected) { "Rejection is not valid from ${current.record.state.lifecycle}" }
        requirePhaseClaim(current, phase)
        return current.next(
            record = current.record.copy(
                state = current.record.state.copy(lifecycle = "rejected", availability = "unavailable"),
                verification = current.record.verification.copy(
                    integrity = "failed",
                    firstScan = if (phase == ReviewPhase.FIRST) "failed" else current.record.verification.firstScan,
                    secondScan = if (phase == ReviewPhase.SECOND) "failed" else current.record.verification.secondScan,
                ),
                timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
            ),
            review = current.review.copy(
                rejectionCode = code,
                rejectionEvidenceId = evidenceId,
                activeReviewClaim = null,
            ),
        )
    }

    fun claimPromotion(current: StoredRelease, attemptId: String, now: Instant): StoredRelease {
        requireEvidenceId(attemptId, "act_")
        require(current.record.state.lifecycle == "ready" && current.record.state.availability == "unavailable") {
            "Only an unavailable ready release may be promoted"
        }
        require(current.review.promotionAttemptId == null) { "Promotion is already claimed" }
        requireCompleteEvidence(current)
        val inputDigest = promotionInputDigest(current)
        return current.next(
            record = current.record.copy(timestamps = current.record.timestamps.copy(updatedAt = now.utc())),
            review = current.review.copy(promotionAttemptId = attemptId, promotionInputSha256 = inputDigest),
        )
    }

    fun activate(current: StoredRelease, attemptId: String, metadataVersion: Long, now: Instant): StoredRelease {
        require(metadataVersion > 0) { "Metadata version must be positive" }
        require(current.review.promotionAttemptId == attemptId) { "Promotion claim does not match" }
        require(current.review.promotionInputSha256 == promotionInputDigest(current)) { "Reviewed promotion inputs changed" }
        requireCompleteEvidence(current)
        return current.next(
            record = current.record.copy(
                state = current.record.state.copy(lifecycle = "active", availability = "available"),
                resolverMetadataVersion = metadataVersion,
                timestamps = current.record.timestamps.copy(activatedAt = now.utc(), updatedAt = now.utc()),
                links = current.record.links.copy(
                    download = "/packages/api/v1/packages/${current.record.`package`}/releases/${current.record.version}/download",
                ),
            ),
            review = current.review,
        )
    }

    fun rejectPromotion(current: StoredRelease, code: String, evidenceId: String, now: Instant): StoredRelease {
        require(code.matches(Regex("^[a-z0-9_]{3,96}$"))) { "Rejection code is invalid" }
        requireEvidenceId(evidenceId, "aud_")
        require(current.record.state.lifecycle == "ready" && current.record.state.availability == "unavailable") {
            "Promotion rejection requires an unavailable ready release"
        }
        return current.next(
            record = current.record.copy(
                state = current.record.state.copy(lifecycle = "rejected"),
                timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
            ),
            review = current.review.copy(rejectionCode = code, rejectionEvidenceId = evidenceId),
        )
    }

    private fun requireCompleteEvidence(current: StoredRelease) {
        require(current.review.validatedArchiveSha256 == current.record.archive.sha256) { "Validated archive binding is missing" }
        require(current.record.verification.origin == "passed" && current.record.verification.integrity == "passed" && current.record.verification.source == "passed") {
            "Origin, integrity, and source verification must pass"
        }
        require(current.record.verification.firstScan == "passed" && current.review.firstScanAttestationId != null) {
            "First scan evidence is missing"
        }
        if (current.record.state.visibility == "public") {
            require(current.record.verification.secondScan == "passed" && current.review.secondScanAttestationId != null) {
                "Second scan evidence is missing"
            }
            require(current.review.secondSourceProofId == current.record.sourceProofId) { "Fresh source proof is not current" }
        }
    }

    private fun requireDigestBinding(current: StoredRelease, digest: String) {
        IdentityRules.requireDigest(digest, "review archive digest")
        require(digest == current.record.archive.sha256) { "Review archive digest does not match the release" }
    }

    private fun requireEvidenceSequence(current: StoredRelease, sequence: Long) {
        require(sequence > current.record.verification.attestationSequence) {
            "Review evidence sequence must advance"
        }
    }

    private fun requireClaim(current: StoredRelease, phase: ReviewPhase, kind: ReviewClaimKind) {
        require(hasClaim(current, phase, kind)) { "Review claim does not match" }
    }

    private fun requirePhaseClaim(current: StoredRelease, phase: ReviewPhase) {
        require(
            hasClaim(current, phase, ReviewClaimKind.SOURCE) ||
                hasClaim(current, phase, ReviewClaimKind.SCAN),
        ) { "Review claim does not match" }
    }

    private fun hasClaim(current: StoredRelease, phase: ReviewPhase, kind: ReviewClaimKind): Boolean {
        if (current.review.activeReviewClaim == claimName(phase, kind)) return true
        if (current.review.activeReviewClaim != null || kind != ReviewClaimKind.SOURCE) return false
        // Compatibility for claims persisted by the pre-lease worker. A completed
        // source proof has source=passed and is a handoff, not an active claim.
        return current.record.verification.source == "pending"
    }

    private fun claimName(phase: ReviewPhase, kind: ReviewClaimKind): String =
        "${phase.name.lowercase()}-${kind.name.lowercase()}"

    private fun requireSourceBinding(expectedId: String?, expectedDigest: String?, evidence: PassedScanEvidence) {
        require(expectedId != null && expectedDigest != null) { "Source proof binding is missing" }
        require(evidence.sourceProofId == expectedId && evidence.sourceProofSha256 == expectedDigest) {
            "Scan source proof binding does not match"
        }
    }

    private fun requireEvidenceId(value: String, prefix: String) {
        require(Regex("^${Regex.escape(prefix)}[A-Za-z0-9_-]{16,96}$").matches(value)) { "Evidence identifier is invalid" }
    }

    private fun sourceProofPath(current: StoredRelease, proofId: String): String =
        "/packages/api/v1/packages/${current.record.`package`}/releases/${current.record.version}/source-proofs/$proofId"

    private fun promotionInputDigest(current: StoredRelease): String = sha256(
        listOf(
            current.record.`package`,
            current.record.version,
            current.record.archive.sha256,
            current.record.manifestSha256,
            current.review.firstSourceProofSha256.orEmpty(),
            current.review.firstScanAttestationSha256.orEmpty(),
            current.review.secondSourceProofSha256.orEmpty(),
            current.review.secondScanAttestationSha256.orEmpty(),
        ).joinToString("\u0000").encodeToByteArray(),
    )

    private fun StoredRelease.next(record: ReleaseRecord, review: ReviewEvidenceState): StoredRelease =
        copy(record = record, review = review, revision = revision + 1)
}
