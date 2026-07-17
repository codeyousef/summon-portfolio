package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64

fun interface EnforcementIdGenerator {
    fun next(prefix: String): String
}

class SecureEnforcementIdGenerator(
    private val random: SecureRandom = SecureRandom(),
) : EnforcementIdGenerator {
    override fun next(prefix: String): String = prefix + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(18).also(random::nextBytes))
}

/**
 * Repository-backed enforcement workflow with append-only evidence.
 *
 * Availability restoration is deliberately ordered after its authorization
 * artifact is durable. Security quarantine is ordered in the opposite
 * direction: denial commits first, so any evidence-store failure leaves the
 * release unavailable and requires operator repair rather than failing open.
 */
class EnforcementService(
    private val repository: RegistryRepository,
    private val environment: String,
    private val clock: Clock = Clock.systemUTC(),
    private val ids: EnforcementIdGenerator = SecureEnforcementIdGenerator(),
) {
    init {
        require(environment in setOf("development", "production"))
    }

    fun createSecurityReport(
        request: SecurityReportCreateRequest,
        actor: EnforcementPrincipal,
    ): SecurityReportRecord {
        requireAuthenticated(actor)
        validateSubject(request.subject)
        requireValue(request.category in REPORT_CATEGORIES)
        requireText(request.summary, 512)
        request.details?.let { requireTextLength(it, 10_000) }
        request.evidenceReferences?.let { evidence ->
            requireValue(evidence.isNotEmpty())
            validateEvidenceReferences(evidence)
        }
        val now = clock.instant().utc()
        val record = SecurityReportRecord(
            reportId = newId("rpt_"),
            subject = request.subject,
            category = request.category,
            status = "acknowledged",
            createdAt = now,
            acknowledgedAt = now,
            updatedAt = now,
        )
        append(
            artifactId = record.reportId,
            kind = SECURITY_REPORT_ARTIFACT,
            subject = record.subject,
            createdAt = now,
            payload = SecurityReportArtifactPayload(record, actor.principalId, request),
        )
        return record
    }

    fun getSecurityReport(reportId: String, actor: EnforcementPrincipal): SecurityReportRecord {
        requireAuthenticated(actor)
        val payload = reportPayload(reportId) ?: hiddenNotFound()
        val allowed = actor.principalId == payload.reporterPrincipalId ||
            actor.roles.any { it == EnforcementRoles.TRUST_AND_SAFETY || it == EnforcementRoles.SECURITY }
        if (!allowed) hiddenNotFound()
        return payload.record
    }

    fun createEnforcementAppeal(
        incidentId: String,
        request: EnforcementAppealCreateRequest,
        actor: EnforcementPrincipal,
    ): EnforcementAppealRecord {
        requireAuthenticated(actor)
        requireWireId(incidentId, "inc_")
        requireValue(request.requestedOutcome == "reviewed-reinstatement")
        requireText(request.statement, 10_000)
        validateEvidenceReferences(request.evidenceReferences)
        val incident = incidentPayload(incidentId) ?: hiddenNotFound()
        val release = release(incident.action.subject)
        requireOwner(release, actor)
        requireState(release.record.state.availability == "security-quarantined")
        val now = clock.instant().utc()
        val record = EnforcementAppealRecord(
            appealId = newId("apl_"),
            incidentId = incidentId,
            subject = incident.action.subject,
            status = "submitted",
            submittedAt = now,
            auditEventId = newId("aud_"),
        )
        append(
            artifactId = record.appealId,
            kind = ENFORCEMENT_APPEAL_ARTIFACT,
            subject = record.subject,
            createdAt = now,
            payload = EnforcementAppealArtifactPayload(record, actor.principalId, request),
        )
        return record
    }

    fun getEnforcementAppeal(appealId: String, actor: EnforcementPrincipal): EnforcementAppealRecord {
        requireAuthenticated(actor)
        val payload = appealPayload(appealId) ?: hiddenNotFound()
        val release = repository.getRelease(
            payload.record.subject.packageIdentity,
            payload.record.subject.version ?: hiddenNotFound(),
        )
        val allowed = actor.principalId == payload.appellantPrincipalId ||
            actor.principalId == release?.ownerPrincipal ||
            actor.roles.any { it == EnforcementRoles.TRUST_AND_SAFETY || it == EnforcementRoles.SECURITY }
        if (!allowed) hiddenNotFound()
        return latestAppeal(payload)
    }

    fun reviewEnforcementAppeal(
        appealId: String,
        request: AppealReviewRequest,
        actor: EnforcementPrincipal,
    ): AppealReviewRecord {
        requireIndependentRole(actor, EnforcementRoles.TRUST_AND_SAFETY)
        requireWireId(appealId, "apl_")
        validateReviewRequest(request)
        val appeal = appealPayload(appealId) ?: hiddenNotFound()
        val currentAppeal = latestAppeal(appeal)
        requireState(currentAppeal.status in setOf("submitted", "information-requested"))
        val incident = incidentPayload(currentAppeal.incidentId) ?: hiddenNotFound()
        val release = release(currentAppeal.subject)
        if (
            actor.principalId == appeal.appellantPrincipalId ||
            actor.principalId == incident.enforcerPrincipalId ||
            actor.principalId == release.ownerPrincipal
        ) {
            forbidden()
        }
        val nowInstant = clock.instant()
        val now = nowInstant.utc()
        val waiverExpiry = request.waiverExpiresAt?.let(::parseTimestamp)
        if (request.emergencyWaiver && (waiverExpiry == null || !waiverExpiry.isAfter(nowInstant))) {
            invalidRequest()
        }
        val review = AppealReviewRecord(
            reviewId = newId("rev_"),
            appealId = appealId,
            decision = request.decision,
            emergencyWaiver = request.emergencyWaiver,
            waiverExpiresAt = request.waiverExpiresAt,
            retrospectiveReviewRequired = request.emergencyWaiver,
            reviewedAt = now,
            auditEventId = newId("aud_"),
        )
        val resultingStatus = when (request.decision) {
            "uphold" -> "denied"
            "approve-reinstatement" -> "approved"
            else -> "information-requested"
        }
        val resultingAppeal = currentAppeal.copy(
            status = resultingStatus,
            decidedAt = now.takeUnless { resultingStatus == "information-requested" },
            latestReviewId = review.reviewId,
            auditEventId = review.auditEventId,
        )
        append(
            artifactId = review.reviewId,
            kind = APPEAL_REVIEW_ARTIFACT,
            subject = currentAppeal.subject,
            createdAt = now,
            payload = AppealReviewArtifactPayload(
                review = review,
                resultingAppeal = resultingAppeal,
                reviewerPrincipalId = actor.principalId,
                request = request,
            ),
        )
        return review
    }

    fun yankRelease(
        packageIdentity: String,
        version: String,
        request: YankReleaseRequest,
        actor: EnforcementPrincipal,
    ): ReleaseRecord = yankRelease(packageIdentity, version, request, actor) { }

    fun yankRelease(
        packageIdentity: String,
        version: String,
        request: YankReleaseRequest,
        actor: EnforcementPrincipal,
        publishSignedAvailability: (StoredRelease) -> Unit,
    ): ReleaseRecord {
        validateYankRequest(request)
        val current = release(packageIdentity, version)
        requireOwner(current, actor)
        requireState(
            current.record.state.lifecycle == "active" &&
                current.record.state.retention == "retained" &&
                current.record.state.availability == "available",
        )
        val now = clock.instant().utc()
        val action = ReleaseAvailabilityActionRecord(
            auditEventId = newId("aud_"),
            action = "yanked",
            subject = current.subject(),
            actorPrincipalId = actor.principalId,
            reason = request.reason,
            advisoryUrl = request.advisoryUrl,
            priorAvailability = "available",
            resultingAvailability = "yanked",
            effectiveAt = now,
        )
        val updated = current.withAvailability(
            availability = "yanked",
            updatedAt = now,
            yankedAt = now,
        )
        val applied = transition(current, updated)
        append(
            artifactId = action.auditEventId,
            kind = RELEASE_AVAILABILITY_ACTION_ARTIFACT,
            subject = action.subject,
            createdAt = now,
            payload = ReleaseAvailabilityArtifactPayload(action),
        )
        publishSignedAvailability(applied)
        return applied.record
    }

    fun unyankRelease(
        packageIdentity: String,
        version: String,
        actor: EnforcementPrincipal,
    ): ReleaseRecord = unyankRelease(packageIdentity, version, actor) { }

    fun unyankRelease(
        packageIdentity: String,
        version: String,
        actor: EnforcementPrincipal,
        publishSignedAvailability: (StoredRelease) -> Unit,
    ): ReleaseRecord {
        val current = release(packageIdentity, version)
        requireOwner(current, actor)
        requireState(
            current.record.state.lifecycle == "active" &&
                current.record.state.retention == "retained" &&
                current.record.state.availability == "yanked",
        )
        val now = clock.instant().utc()
        val action = ReleaseAvailabilityActionRecord(
            auditEventId = newId("aud_"),
            action = "unyanked",
            subject = current.subject(),
            actorPrincipalId = actor.principalId,
            priorAvailability = "yanked",
            resultingAvailability = "available",
            effectiveAt = now,
        )
        append(
            artifactId = action.auditEventId,
            kind = RELEASE_AVAILABILITY_ACTION_ARTIFACT,
            subject = action.subject,
            createdAt = now,
            payload = ReleaseAvailabilityArtifactPayload(action),
        )
        val updated = current.withAvailability("available", updatedAt = now)
        val applied = transition(current, updated)
        try {
            publishSignedAvailability(applied)
        } catch (failure: Exception) {
            // A failed signed publication must not leave direct downloads more
            // permissive than resolver metadata. Best-effort CAS rollback keeps
            // the release yanked; a competing security transition is also a
            // denying state and therefore does not need to be overwritten.
            repository.transitionRelease(
                applied.revision,
                applied.withAvailability(
                    availability = "yanked",
                    updatedAt = clock.instant().utc(),
                    yankedAt = current.record.timestamps.yankedAt,
                ),
            )
            throw failure
        }
        return applied.record
    }

    fun securityQuarantineRelease(
        packageIdentity: String,
        version: String,
        request: SecurityQuarantineRequest,
        actor: EnforcementPrincipal,
        signedMetadata: SignedMetadataReference,
    ): SecurityActionRecord = securityQuarantineRelease(packageIdentity, version, request, actor) { signedMetadata }

    fun securityQuarantineRelease(
        packageIdentity: String,
        version: String,
        request: SecurityQuarantineRequest,
        actor: EnforcementPrincipal,
        publishSignedMetadata: (incidentId: String) -> SignedMetadataReference,
    ): SecurityActionRecord {
        requireIndependentRole(actor, EnforcementRoles.SECURITY)
        validateQuarantineRequest(request)
        val current = release(packageIdentity, version)
        if (actor.principalId == current.ownerPrincipal) forbidden()
        requireState(
            current.record.state.lifecycle == "active" &&
                current.record.state.retention == "retained" &&
                current.record.state.availability in setOf("available", "yanked"),
        )
        validateReportBindings(request.reportIds, current.subject())
        val now = clock.instant().utc()
        val incidentId = newId("inc_")
        transition(
            current,
            current.withAvailability(
                availability = "security-quarantined",
                updatedAt = now,
                securityQuarantinedAt = now,
            ),
        )
        val signedMetadata = publishSignedMetadata(incidentId)
        validateSignedMetadata(signedMetadata)
        val action = SecurityActionRecord(
            incidentId = incidentId,
            action = "security-quarantined",
            subject = current.subject(),
            effectiveAt = now,
            resolverSelection = "deny",
            downloadPolicy = "deny",
            reviewId = null,
            signedMetadata = signedMetadata,
            auditEventId = newId("aud_"),
        )
        append(
            artifactId = action.incidentId,
            kind = SECURITY_INCIDENT_ARTIFACT,
            subject = action.subject,
            createdAt = now,
            payload = SecurityIncidentArtifactPayload(
                action = action,
                request = request,
                enforcerPrincipalId = actor.principalId,
                priorAvailability = current.record.state.availability,
            ),
        )
        return action
    }

    fun reviewedReinstateRelease(
        packageIdentity: String,
        version: String,
        request: ReviewedReinstatementRequest,
        actor: EnforcementPrincipal,
        signedMetadata: SignedMetadataReference,
    ): SecurityActionRecord = reviewedReinstateRelease(
        packageIdentity = packageIdentity,
        version = version,
        request = request,
        actor = actor,
        publishSignedMetadata = { signedMetadata },
        restoreSecurityQuarantine = { },
    )

    fun reviewedReinstateRelease(
        packageIdentity: String,
        version: String,
        request: ReviewedReinstatementRequest,
        actor: EnforcementPrincipal,
        publishSignedMetadata: () -> SignedMetadataReference,
        restoreSecurityQuarantine: () -> Unit = { },
    ): SecurityActionRecord {
        requireIndependentRole(actor, EnforcementRoles.SECURITY)
        requireWireId(request.incidentId, "inc_")
        requireWireId(request.appealId, "apl_")
        requireWireId(request.reviewId, "rev_")
        val current = release(packageIdentity, version)
        if (actor.principalId == current.ownerPrincipal) forbidden()
        requireState(
            current.record.state.lifecycle == "active" &&
                current.record.state.retention == "retained" &&
                current.record.state.availability == "security-quarantined",
        )
        val incident = incidentPayload(request.incidentId) ?: hiddenNotFound()
        requireBinding(incident.action.subject == current.subject())
        val appeal = appealPayload(request.appealId) ?: hiddenNotFound()
        val latestAppeal = latestAppeal(appeal)
        requireBinding(
            latestAppeal.incidentId == request.incidentId &&
                latestAppeal.subject == current.subject() &&
                latestAppeal.status == "approved" &&
                latestAppeal.latestReviewId == request.reviewId,
        )
        val review = reviewPayload(request.reviewId) ?: hiddenNotFound()
        requireBinding(
            review.review.appealId == request.appealId &&
                review.review.decision == "approve-reinstatement" &&
                review.resultingAppeal == latestAppeal,
        )
        if (actor.principalId == review.reviewerPrincipalId) forbidden()
        val restoredAvailability = when (incident.priorAvailability) {
            "available" -> "available"
            "yanked" -> "yanked"
            else -> throw RegistryException(
                409,
                "state_transition_forbidden",
                "Release state transition is not allowed",
            )
        }
        try {
            val signedMetadata = publishSignedMetadata()
            validateSignedMetadata(signedMetadata)
            val now = clock.instant().utc()
            val action = SecurityActionRecord(
                incidentId = request.incidentId,
                action = "reviewed-reinstated",
                subject = current.subject(),
                effectiveAt = now,
                resolverSelection = "restore-if-release-policy-allows",
                downloadPolicy = "restore-if-authorized",
                reviewId = request.reviewId,
                signedMetadata = signedMetadata,
                auditEventId = newId("aud_"),
            )
            append(
                artifactId = action.auditEventId,
                kind = SECURITY_ACTION_ARTIFACT,
                subject = action.subject,
                createdAt = now,
                payload = SecurityActionArtifactPayload(
                    action = action,
                    securityPrincipalId = actor.principalId,
                    appealId = request.appealId,
                ),
            )
            transition(
                current,
                current.withAvailability(restoredAvailability, updatedAt = now),
            )
            return action
        } catch (failure: Exception) {
            // TUF's public pointer can commit before the append-only action and
            // release CAS. Reassert the deny override when either durable commit
            // fails; metadata refresh independently reconciles the same invariant.
            runCatching(restoreSecurityQuarantine).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun latestAppeal(original: EnforcementAppealArtifactPayload): EnforcementAppealRecord =
        repository.listReviewArtifacts(
            packageIdentity = original.record.subject.packageIdentity,
            version = original.record.subject.version,
            kind = APPEAL_REVIEW_ARTIFACT,
        ).asSequence()
            .mapNotNull { runCatching { decode<AppealReviewArtifactPayload>(it) }.getOrNull() }
            .filter { it.review.appealId == original.record.appealId }
            .maxByOrNull { review ->
                repository.getReviewArtifact(review.review.reviewId)?.sequence ?: Long.MIN_VALUE
            }
            ?.resultingAppeal
            ?: original.record

    private fun reportPayload(reportId: String): SecurityReportArtifactPayload? =
        repository.getReviewArtifact(reportId)
            ?.takeIf { it.kind == SECURITY_REPORT_ARTIFACT }
            ?.let(::decode)

    private fun appealPayload(appealId: String): EnforcementAppealArtifactPayload? =
        repository.getReviewArtifact(appealId)
            ?.takeIf { it.kind == ENFORCEMENT_APPEAL_ARTIFACT }
            ?.let(::decode)

    private fun reviewPayload(reviewId: String): AppealReviewArtifactPayload? =
        repository.getReviewArtifact(reviewId)
            ?.takeIf { it.kind == APPEAL_REVIEW_ARTIFACT }
            ?.let(::decode)

    private fun incidentPayload(incidentId: String): SecurityIncidentArtifactPayload? =
        repository.getReviewArtifact(incidentId)
            ?.takeIf { it.kind == SECURITY_INCIDENT_ARTIFACT }
            ?.let(::decode)

    private inline fun <reified T> decode(artifact: ReviewArtifact): T =
        RegistryJson.decodeFromJsonElement(artifact.payload)

    private inline fun <reified T> append(
        artifactId: String,
        kind: String,
        subject: EnforcementReleaseSubject,
        createdAt: String,
        payload: T,
    ) {
        val artifact = ReviewArtifact(
            artifactId = artifactId,
            kind = kind,
            packageIdentity = subject.packageIdentity,
            version = subject.version,
            archiveSha256 = subject.version?.let {
                repository.getRelease(subject.packageIdentity, it)?.record?.archive?.sha256
            },
            sequence = nextSequence(subject),
            createdAt = createdAt,
            payload = RegistryJson.encodeToJsonElement(payload).jsonObject,
        )
        if (!repository.appendReviewArtifact(artifact)) {
            throw RegistryException(
                500,
                "internal_error",
                "Registry operation failed",
                retryable = true,
            )
        }
    }

    private fun nextSequence(subject: EnforcementReleaseSubject): Long =
        (repository.listReviewArtifacts(subject.packageIdentity, subject.version)
            .maxOfOrNull(ReviewArtifact::sequence) ?: 0L) + 1L

    private fun release(subject: EnforcementReleaseSubject): StoredRelease =
        release(subject.packageIdentity, subject.version ?: hiddenNotFound())

    private fun release(packageIdentity: String, version: String): StoredRelease {
        IdentityRules.requireIdentity(packageIdentity)
        IdentityRules.requireVersion(version)
        return repository.getRelease(packageIdentity, version) ?: hiddenNotFound()
    }

    private fun transition(current: StoredRelease, updated: StoredRelease): StoredRelease =
        when (val result = repository.transitionRelease(current.revision, updated)) {
            is ReleaseTransitionResult.Applied -> result.value
            is ReleaseTransitionResult.Conflict -> throw RegistryException(
                409,
                "state_transition_forbidden",
                "Release state transition is not allowed",
            )
            ReleaseTransitionResult.Missing -> hiddenNotFound()
        }

    private fun StoredRelease.withAvailability(
        availability: String,
        updatedAt: String,
        yankedAt: String? = record.timestamps.yankedAt,
        securityQuarantinedAt: String? = record.timestamps.securityQuarantinedAt,
    ): StoredRelease = copy(
        record = record.copy(
            state = record.state.copy(availability = availability),
            timestamps = record.timestamps.copy(
                yankedAt = yankedAt,
                securityQuarantinedAt = securityQuarantinedAt,
                updatedAt = updatedAt,
            ),
        ),
        revision = revision + 1,
    )

    private fun StoredRelease.subject() = EnforcementReleaseSubject(record.`package`, record.version)

    private fun validateSubject(subject: EnforcementReleaseSubject) {
        IdentityRules.requireIdentity(subject.packageIdentity)
        subject.version?.let(IdentityRules::requireVersion)
    }

    private fun validateEvidenceReferences(values: List<String>) {
        requireValue(values.size <= 32 && values.size == values.toSet().size)
        values.forEach { requireWireId(it, "evd_") }
    }

    private fun validateReviewRequest(request: AppealReviewRequest) {
        requireValue(request.decision in REVIEW_DECISIONS)
        requireText(request.rationale, 10_000)
        if (request.emergencyWaiver) {
            requireText(request.waiverReason ?: invalidRequest(), 2_000)
            request.waiverExpiresAt ?: invalidRequest()
        } else if (request.waiverReason != null || request.waiverExpiresAt != null) {
            invalidRequest()
        }
    }

    private fun validateYankRequest(request: YankReleaseRequest) {
        request.reason?.let { requireTextLength(it, 512) }
        request.advisoryUrl?.let(::requireHttpsUrl)
    }

    private fun validateQuarantineRequest(request: SecurityQuarantineRequest) {
        requireText(request.reason, 2_000)
        requireValue(request.severity in setOf("critical", "high", "moderate"))
        requireValue(request.reportIds.size <= 32 && request.reportIds.size == request.reportIds.toSet().size)
        request.reportIds.forEach { requireWireId(it, "rpt_") }
        request.advisoryUrl?.let(::requireHttpsUrl)
    }

    private fun validateReportBindings(reportIds: List<String>, subject: EnforcementReleaseSubject) {
        for (reportId in reportIds) {
            val report = reportPayload(reportId) ?: hiddenNotFound()
            requireBinding(
                report.record.subject.packageIdentity == subject.packageIdentity &&
                    (report.record.subject.version == null || report.record.subject.version == subject.version),
            )
        }
    }

    private fun validateSignedMetadata(reference: SignedMetadataReference) {
        requireValue(
            reference.environment == environment &&
                reference.role == "security" &&
                SECURITY_METADATA_FILENAME.matches(reference.filename) &&
                reference.version >= 1 &&
                reference.length >= 1,
        )
        IdentityRules.requireDigest(reference.sha256, "signed metadata digest")
        val now = clock.instant()
        val publishedAt = parseTimestamp(reference.publishedAt)
        val expiresAt = parseTimestamp(reference.expiresAt)
        requireValue(!publishedAt.isAfter(now) && expiresAt.isAfter(now) && expiresAt.isAfter(publishedAt))
    }

    private fun requireOwner(release: StoredRelease, actor: EnforcementPrincipal) {
        requireAuthenticated(actor)
        if (
            actor.principalId != release.ownerPrincipal ||
            EnforcementRoles.PUBLISHER !in actor.roles
        ) {
            forbidden()
        }
    }

    private fun requireIndependentRole(actor: EnforcementPrincipal, role: String) {
        requireAuthenticated(actor)
        if (role !in actor.roles || EnforcementRoles.PUBLISHER in actor.roles) forbidden()
    }

    private fun requireAuthenticated(actor: EnforcementPrincipal) {
        if (actor.principalId.isBlank()) {
            throw RegistryException(401, "unauthenticated", "Authentication is required")
        }
    }

    private fun requireWireId(value: String, prefix: String) {
        if (!Regex("^${Regex.escape(prefix)}[A-Za-z0-9_-]{16,96}$").matches(value)) invalidRequest()
    }

    private fun newId(prefix: String): String = ids.next(prefix).also { id ->
        check(Regex("^${Regex.escape(prefix)}[A-Za-z0-9_-]{16,96}$").matches(id)) {
            "Enforcement ID generator returned an invalid $prefix identifier"
        }
    }

    private fun parseTimestamp(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrElse { invalidRequest() }

    private fun requireHttpsUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { invalidRequest() }
        requireValue(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
    }

    private fun requireText(value: String, maximum: Int) {
        requireValue(value.isNotBlank() && value.length <= maximum)
    }

    private fun requireTextLength(value: String, maximum: Int) {
        requireValue(value.length <= maximum)
    }

    private fun requireValue(value: Boolean) {
        if (!value) invalidRequest()
    }

    private fun requireBinding(value: Boolean) {
        if (!value) {
            throw RegistryException(
                409,
                "state_transition_forbidden",
                "Release state transition is not allowed",
            )
        }
    }

    private fun requireState(value: Boolean) = requireBinding(value)

    private fun forbidden(): Nothing =
        throw RegistryException(403, "forbidden", "Operation is not authorized")

    private fun invalidRequest(): Nothing =
        throw RegistryException(400, "invalid_request", "Request body is invalid")

    private fun hiddenNotFound(): Nothing =
        throw RegistryException(404, "not_found", "Resource not found")

    private companion object {
        val REPORT_CATEGORIES = setOf(
            "malicious-code",
            "typosquatting",
            "credential-compromise",
            "provenance-mismatch",
            "vulnerability",
            "policy-violation",
            "other",
        )
        val REVIEW_DECISIONS = setOf(
            "uphold",
            "approve-reinstatement",
            "request-more-information",
        )
        val SECURITY_METADATA_FILENAME = Regex("^[1-9][0-9]*\\.security\\.json$")
    }
}

@Serializable
private data class SecurityReportArtifactPayload(
    val record: SecurityReportRecord,
    @SerialName("reporter_principal_id") val reporterPrincipalId: String,
    val request: SecurityReportCreateRequest,
)

@Serializable
private data class EnforcementAppealArtifactPayload(
    val record: EnforcementAppealRecord,
    @SerialName("appellant_principal_id") val appellantPrincipalId: String,
    val request: EnforcementAppealCreateRequest,
)

@Serializable
private data class AppealReviewArtifactPayload(
    val review: AppealReviewRecord,
    @SerialName("resulting_appeal") val resultingAppeal: EnforcementAppealRecord,
    @SerialName("reviewer_principal_id") val reviewerPrincipalId: String,
    val request: AppealReviewRequest,
)

@Serializable
private data class ReleaseAvailabilityArtifactPayload(
    val action: ReleaseAvailabilityActionRecord,
)

@Serializable
private data class SecurityIncidentArtifactPayload(
    val action: SecurityActionRecord,
    val request: SecurityQuarantineRequest,
    @SerialName("enforcer_principal_id") val enforcerPrincipalId: String,
    @SerialName("prior_availability") val priorAvailability: String,
)

@Serializable
private data class SecurityActionArtifactPayload(
    val action: SecurityActionRecord,
    @SerialName("security_principal_id") val securityPrincipalId: String,
    @SerialName("appeal_id") val appealId: String,
)

const val SECURITY_REPORT_ARTIFACT = "security-report"
const val ENFORCEMENT_APPEAL_ARTIFACT = "enforcement-appeal"
const val APPEAL_REVIEW_ARTIFACT = "appeal-review"
const val RELEASE_AVAILABILITY_ACTION_ARTIFACT = "release-availability-action"
const val SECURITY_INCIDENT_ARTIFACT = "security-incident"
const val SECURITY_ACTION_ARTIFACT = "security-action"
