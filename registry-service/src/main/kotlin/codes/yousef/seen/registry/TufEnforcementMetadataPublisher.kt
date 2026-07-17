package codes.yousef.seen.registry

/** Production bridge from enforcement actions to durable TUF transactions. */
class TufEnforcementMetadataPublisher(
    private val repository: RegistryRepository,
    private val tuf: TufPublisher,
) : EnforcementMetadataPublisher {
    override fun publishReleaseAvailability(release: StoredRelease) {
        val current = repository.getRelease(release.record.`package`, release.record.version)
            ?: notFound()
        requireImmutableBinding(current, release)
        tuf.publish(listOf(release))
    }

    override fun publishSecurityQuarantine(
        subject: EnforcementReleaseSubject,
        request: SecurityQuarantineRequest,
    ): SignedMetadataReference {
        // The request is validated and durably represented by EnforcementService;
        // report IDs, rationale, and advisory details never enter public metadata.
        check(request.reason.isNotBlank())
        return tuf.publishSecurityQuarantine(release(subject))
    }

    override fun publishReviewedReinstatement(
        subject: EnforcementReleaseSubject,
        request: ReviewedReinstatementRequest,
    ): SignedMetadataReference {
        check(request.reviewId.isNotBlank())
        return tuf.publishReviewedSecurityReinstatement(release(subject))
    }

    override fun restoreSecurityQuarantine(subject: EnforcementReleaseSubject) {
        tuf.publishSecurityQuarantine(release(subject))
    }

    private fun release(subject: EnforcementReleaseSubject): StoredRelease {
        val version = subject.version ?: notFound()
        return repository.getRelease(subject.packageIdentity, version) ?: notFound()
    }

    private fun requireImmutableBinding(current: StoredRelease, candidate: StoredRelease) {
        if (
            current.ownerPrincipal != candidate.ownerPrincipal ||
            current.uploadId != candidate.uploadId ||
            current.record.`package` != candidate.record.`package` ||
            current.record.version != candidate.record.version ||
            current.record.archive.sha256 != candidate.record.archive.sha256 ||
            current.record.archive.compressedBytes != candidate.record.archive.compressedBytes ||
            current.record.manifestSha256 != candidate.record.manifestSha256 ||
            current.source != candidate.source ||
            current.dependencies != candidate.dependencies ||
            current.review != candidate.review
        ) {
            throw RegistryException(
                409,
                "state_transition_forbidden",
                "Release state transition is not allowed",
            )
        }
    }

    private fun notFound(): Nothing =
        throw RegistryException(404, "not_found", "Resource was not found")
}
