package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object EnforcementRoles {
    const val PUBLISHER = "publisher"
    const val TRUST_AND_SAFETY = "registry-trust-and-safety"
    const val SECURITY = "registry-security"
}

data class EnforcementPrincipal(
    val principalId: String,
    val roles: Set<String>,
) {
    companion object {
        fun publisher(principal: WriterPrincipal): EnforcementPrincipal =
            EnforcementPrincipal(principal.subject, setOf(EnforcementRoles.PUBLISHER))
    }
}

@Serializable
data class EnforcementReleaseSubject(
    @SerialName("package") val packageIdentity: String,
    val version: String? = null,
)

@Serializable
data class SecurityReportCreateRequest(
    val subject: EnforcementReleaseSubject,
    val category: String,
    val summary: String,
    val details: String? = null,
    @SerialName("evidence_references") val evidenceReferences: List<String>? = null,
)

@Serializable
data class SecurityReportRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("report_id") val reportId: String,
    val subject: EnforcementReleaseSubject,
    val category: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class EnforcementAppealCreateRequest(
    @SerialName("requested_outcome") val requestedOutcome: String,
    val statement: String,
    @SerialName("evidence_references") val evidenceReferences: List<String> = emptyList(),
)

@Serializable
data class EnforcementAppealRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("appeal_id") val appealId: String,
    @SerialName("incident_id") val incidentId: String,
    val subject: EnforcementReleaseSubject,
    val status: String,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("latest_review_id") val latestReviewId: String? = null,
    @SerialName("audit_event_id") val auditEventId: String,
)

@Serializable
data class AppealReviewRequest(
    val decision: String,
    val rationale: String,
    @SerialName("emergency_waiver") val emergencyWaiver: Boolean,
    @SerialName("waiver_reason") val waiverReason: String? = null,
    @SerialName("waiver_expires_at") val waiverExpiresAt: String? = null,
)

@Serializable
data class AppealReviewRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("review_id") val reviewId: String,
    @SerialName("appeal_id") val appealId: String,
    val decision: String,
    @SerialName("reviewer_role") val reviewerRole: String = EnforcementRoles.TRUST_AND_SAFETY,
    @SerialName("actor_separation_verified") val actorSeparationVerified: Boolean = true,
    @SerialName("emergency_waiver") val emergencyWaiver: Boolean,
    @SerialName("waiver_expires_at") val waiverExpiresAt: String? = null,
    @SerialName("retrospective_review_required") val retrospectiveReviewRequired: Boolean,
    @SerialName("reviewed_at") val reviewedAt: String,
    @SerialName("audit_event_id") val auditEventId: String,
)

@Serializable
data class YankReleaseRequest(
    val reason: String? = null,
    @SerialName("advisory_url") val advisoryUrl: String? = null,
)

@Serializable
data class ReleaseAvailabilityActionRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("audit_event_id") val auditEventId: String,
    val action: String,
    val subject: EnforcementReleaseSubject,
    @SerialName("actor_principal_id") val actorPrincipalId: String,
    val reason: String? = null,
    @SerialName("advisory_url") val advisoryUrl: String? = null,
    @SerialName("prior_availability") val priorAvailability: String,
    @SerialName("resulting_availability") val resultingAvailability: String,
    @SerialName("effective_at") val effectiveAt: String,
)

@Serializable
data class SecurityQuarantineRequest(
    val reason: String,
    val severity: String,
    @SerialName("report_ids") val reportIds: List<String> = emptyList(),
    @SerialName("advisory_url") val advisoryUrl: String? = null,
)

@Serializable
data class ReviewedReinstatementRequest(
    @SerialName("incident_id") val incidentId: String,
    @SerialName("appeal_id") val appealId: String,
    @SerialName("review_id") val reviewId: String,
)

@Serializable
data class SignedMetadataReference(
    val environment: String,
    val role: String,
    val filename: String,
    val version: Long,
    val length: Long,
    val sha256: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class SecurityActionRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("incident_id") val incidentId: String,
    val action: String,
    val subject: EnforcementReleaseSubject,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("resolver_selection") val resolverSelection: String,
    @SerialName("download_policy") val downloadPolicy: String,
    @SerialName("review_id") val reviewId: String? = null,
    @SerialName("signed_metadata") val signedMetadata: SignedMetadataReference,
    @SerialName("audit_event_id") val auditEventId: String,
)
