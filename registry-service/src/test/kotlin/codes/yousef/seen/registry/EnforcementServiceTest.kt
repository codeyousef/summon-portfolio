package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnforcementServiceTest {
    @Test
    fun `security reports are append only and concealed from unrelated principals`() {
        val fixture = fixture()
        val reporter = principal("reporter")
        val report = fixture.service.createSecurityReport(
            SecurityReportCreateRequest(
                subject = subject(),
                category = "malicious-code",
                summary = "The release contains an unexpected credential collector.",
                details = "Observed only after installing the published release.",
                evidenceReferences = listOf("evd_0000000000000001"),
            ),
            reporter,
        )

        assertEquals("acknowledged", report.status)
        assertEquals(report, fixture.service.getSecurityReport(report.reportId, reporter))
        assertEquals(report, fixture.service.getSecurityReport(report.reportId, security("triage")))
        assertRegistryFailure(404, "not_found") {
            fixture.service.getSecurityReport(report.reportId, principal("unrelated"))
        }
        val artifact = assertNotNull(fixture.repository.getReviewArtifact(report.reportId))
        assertEquals(SECURITY_REPORT_ARTIFACT, artifact.kind)
        assertEquals(PACKAGE, artifact.packageIdentity)
        assertEquals(VERSION, artifact.version)
    }

    @Test
    fun `only the owner can yank and unyank and unyank cannot bypass quarantine`() {
        val fixture = fixture()

        assertRegistryFailure(403, "forbidden") {
            fixture.service.yankRelease(PACKAGE, VERSION, YankReleaseRequest("not mine"), publisher("outsider"))
        }
        assertEquals("available", fixture.release().record.state.availability)

        val yanked = fixture.service.yankRelease(
            PACKAGE,
            VERSION,
            YankReleaseRequest(
                reason = "The release was published prematurely.",
                advisoryUrl = "https://seen.dev/advisories/demo",
            ),
            OWNER,
        )
        assertEquals("yanked", yanked.state.availability)
        assertNotNull(yanked.timestamps.yankedAt)

        val unyanked = fixture.service.unyankRelease(PACKAGE, VERSION, OWNER)
        assertEquals("available", unyanked.state.availability)

        fixture.service.securityQuarantineRelease(
            PACKAGE,
            VERSION,
            quarantineRequest(),
            security("enforcer"),
            signedMetadata(),
        )
        assertRegistryFailure(409, "state_transition_forbidden") {
            fixture.service.unyankRelease(PACKAGE, VERSION, OWNER)
        }
        assertEquals("security-quarantined", fixture.release().record.state.availability)
    }

    @Test
    fun `reviewed reinstatement requires independently bound actors and preserves quarantine until authorized`() {
        val fixture = fixture()
        val originalEnforcer = security("enforcer")
        val reviewer = trustAndSafety("reviewer")
        val reinstater = security("reinstater")

        assertRegistryFailure(403, "forbidden") {
            fixture.service.securityQuarantineRelease(
                PACKAGE,
                VERSION,
                quarantineRequest(),
                mixedPublisherSecurity("mixed-enforcer"),
                signedMetadata(),
            )
        }
        assertEquals("available", fixture.release().record.state.availability)

        val incident = fixture.service.securityQuarantineRelease(
            PACKAGE,
            VERSION,
            quarantineRequest(),
            originalEnforcer,
            signedMetadata(),
        )
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val appeal = fixture.service.createEnforcementAppeal(
            incident.incidentId,
            EnforcementAppealCreateRequest(
                requestedOutcome = "reviewed-reinstatement",
                statement = "The compromised signing credential has been rotated and the release was audited.",
                evidenceReferences = listOf("evd_0000000000000002"),
            ),
            OWNER,
        )
        assertEquals("submitted", appeal.status)
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        assertRegistryFailure(403, "forbidden") {
            fixture.service.reviewEnforcementAppeal(
                appeal.appealId,
                approveReview(),
                EnforcementPrincipal(
                    originalEnforcer.principalId,
                    setOf(EnforcementRoles.TRUST_AND_SAFETY),
                ),
            )
        }
        assertRegistryFailure(403, "forbidden") {
            fixture.service.reviewEnforcementAppeal(
                appeal.appealId,
                approveReview(),
                EnforcementPrincipal(
                    OWNER.principalId,
                    setOf(EnforcementRoles.PUBLISHER, EnforcementRoles.TRUST_AND_SAFETY),
                ),
            )
        }

        val review = fixture.service.reviewEnforcementAppeal(appeal.appealId, approveReview(), reviewer)
        assertEquals("approve-reinstatement", review.decision)
        assertTrue(review.actorSeparationVerified)
        assertEquals("approved", fixture.service.getEnforcementAppeal(appeal.appealId, OWNER).status)
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val request = ReviewedReinstatementRequest(
            incidentId = incident.incidentId,
            appealId = appeal.appealId,
            reviewId = review.reviewId,
        )
        assertRegistryFailure(403, "forbidden") {
            fixture.service.reviewedReinstateRelease(
                PACKAGE,
                VERSION,
                request,
                mixedPublisherSecurity("mixed-reinstater"),
                signedMetadata(),
            )
        }
        assertRegistryFailure(403, "forbidden") {
            fixture.service.reviewedReinstateRelease(
                PACKAGE,
                VERSION,
                request,
                EnforcementPrincipal(reviewer.principalId, setOf(EnforcementRoles.SECURITY)),
                signedMetadata(),
            )
        }
        assertRegistryFailure(409, "state_transition_forbidden") {
            fixture.service.reviewedReinstateRelease(
                PACKAGE,
                VERSION,
                request.copy(reviewId = "rev_9999999999999999"),
                reinstater,
                signedMetadata(),
            )
        }
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val action = fixture.service.reviewedReinstateRelease(
            PACKAGE,
            VERSION,
            request,
            reinstater,
            signedMetadata(),
        )
        assertEquals("reviewed-reinstated", action.action)
        assertEquals(review.reviewId, action.reviewId)
        assertEquals("available", fixture.release().record.state.availability)
        assertNotNull(fixture.repository.getReviewArtifact(action.auditEventId))
    }

    @Test
    fun `reviewed reinstatement restores a previously yanked release only to yanked`() {
        val fixture = fixture(initialAvailability = "yanked")
        val incident = fixture.service.securityQuarantineRelease(
            PACKAGE,
            VERSION,
            quarantineRequest(),
            security("enforcer"),
            signedMetadata(),
        )
        val approval = fixture.approve(incident)

        fixture.service.reviewedReinstateRelease(
            PACKAGE,
            VERSION,
            approval.request,
            security("reinstater"),
            signedMetadata(),
        )

        assertEquals("yanked", fixture.release().record.state.availability)
    }

    @Test
    fun `security workflow storage collisions fail closed at both commit boundaries`() {
        val quarantineFixture = fixture(ids = ScriptedEnforcementIds(
            "inc_0000000000000999",
            "aud_0000000000000999",
        ))
        assertTrue(
            quarantineFixture.repository.appendReviewArtifact(
                collisionArtifact("inc_0000000000000999"),
            ),
        )

        val quarantineFailure = assertRegistryFailure(500, "internal_error") {
            quarantineFixture.service.securityQuarantineRelease(
                PACKAGE,
                VERSION,
                quarantineRequest(),
                security("enforcer"),
                signedMetadata(),
            )
        }
        assertTrue(quarantineFailure.retryable)
        assertEquals("security-quarantined", quarantineFixture.release().record.state.availability)

        val reinstatementFixture = fixture()
        val incident = reinstatementFixture.service.securityQuarantineRelease(
            PACKAGE,
            VERSION,
            quarantineRequest(),
            security("enforcer"),
            signedMetadata(),
        )
        val approval = reinstatementFixture.approve(incident)
        val collidingAuditId = "aud_0000000000000998"
        assertTrue(reinstatementFixture.repository.appendReviewArtifact(collisionArtifact(collidingAuditId)))
        val collisionService = EnforcementService(
            reinstatementFixture.repository,
            ENVIRONMENT,
            FIXED_CLOCK,
            ScriptedEnforcementIds(collidingAuditId),
        )
        var securityOverridePresent = true
        var restoreCalls = 0

        val reinstatementFailure = assertRegistryFailure(500, "internal_error") {
            collisionService.reviewedReinstateRelease(
                packageIdentity = PACKAGE,
                version = VERSION,
                request = approval.request,
                actor = security("reinstater"),
                publishSignedMetadata = {
                    securityOverridePresent = false
                    signedMetadata()
                },
                restoreSecurityQuarantine = {
                    restoreCalls++
                    securityOverridePresent = true
                },
            )
        }
        assertTrue(reinstatementFailure.retryable)
        assertEquals("security-quarantined", reinstatementFixture.release().record.state.availability)
        assertEquals(1, restoreCalls)
        assertTrue(securityOverridePresent)
    }

    private fun fixture(
        initialAvailability: String = "available",
        ids: EnforcementIdGenerator = CountingEnforcementIds(),
    ): Fixture {
        val repository = InMemoryRegistryRepository()
        assertTrue(repository.reserveRelease(activeRelease(initialAvailability)))
        return Fixture(repository, EnforcementService(repository, ENVIRONMENT, FIXED_CLOCK, ids))
    }

    private fun Fixture.approve(incident: SecurityActionRecord): Approval {
        val appeal = service.createEnforcementAppeal(
            incident.incidentId,
            EnforcementAppealCreateRequest(
                requestedOutcome = "reviewed-reinstatement",
                statement = "Independent remediation evidence is ready for review.",
            ),
            OWNER,
        )
        val review = service.reviewEnforcementAppeal(
            appeal.appealId,
            approveReview(),
            trustAndSafety("reviewer"),
        )
        return Approval(
            ReviewedReinstatementRequest(incident.incidentId, appeal.appealId, review.reviewId),
        )
    }

    private fun activeRelease(availability: String): StoredRelease = StoredRelease(
        record = ReleaseRecord(
            `package` = PACKAGE,
            version = VERSION,
            archive = ArchiveStats(sha256 = "a".repeat(64), compressedBytes = 128),
            manifestSha256 = "b".repeat(64),
            state = ReleaseState(
                lifecycle = "active",
                visibility = "public",
                availability = availability,
                retention = "retained",
            ),
            timestamps = ReleaseTimestamps(
                reservedAt = NOW,
                activatedAt = NOW,
                yankedAt = NOW.takeIf { availability == "yanked" },
                updatedAt = NOW,
            ),
            links = ReleaseLinks("/packages/$PACKAGE/releases/$VERSION", "/packages/$PACKAGE"),
        ),
        ownerPrincipal = OWNER.principalId,
        uploadId = "upl_0000000000000001",
        uploadExpiresAt = "2026-07-18T00:00:00Z",
        manifest = buildJsonObject { },
        source = SourceDeclaration(
            forge = "github",
            repositoryId = "seen-demo",
            installationId = "installation-1",
            requestedRef = "refs/tags/v$VERSION",
            expectedCommit = "c".repeat(40),
            licenseSpdx = "MIT",
        ),
    )

    private fun signedMetadata() = SignedMetadataReference(
        environment = ENVIRONMENT,
        role = "security",
        filename = "1.security.json",
        version = 1,
        length = 128,
        sha256 = "d".repeat(64),
        publishedAt = "2026-07-16T23:59:00Z",
        expiresAt = "2026-07-18T00:00:00Z",
    )

    private fun quarantineRequest() = SecurityQuarantineRequest(
        reason = "A security investigation requires immediate resolver denial.",
        severity = "critical",
        advisoryUrl = "https://seen.dev/security/incidents/demo",
    )

    private fun approveReview() = AppealReviewRequest(
        decision = "approve-reinstatement",
        rationale = "The submitted remediation and independent audit satisfy reinstatement policy.",
        emergencyWaiver = false,
    )

    private fun collisionArtifact(id: String) = ReviewArtifact(
        artifactId = id,
        kind = "collision",
        packageIdentity = PACKAGE,
        version = VERSION,
        archiveSha256 = "a".repeat(64),
        sequence = 1,
        createdAt = NOW,
        payload = buildJsonObject { },
    )

    private data class Fixture(
        val repository: InMemoryRegistryRepository,
        val service: EnforcementService,
    ) {
        fun release(): StoredRelease = repository.getRelease(PACKAGE, VERSION)!!
    }

    private data class Approval(val request: ReviewedReinstatementRequest)

    private class CountingEnforcementIds : EnforcementIdGenerator {
        private val sequence = AtomicLong()

        override fun next(prefix: String): String = prefix + sequence.incrementAndGet().toString().padStart(16, '0')
    }

    private class ScriptedEnforcementIds(vararg values: String) : EnforcementIdGenerator {
        private val values = ArrayDeque(values.toList())

        override fun next(prefix: String): String {
            val value = values.pollFirst() ?: error("No scripted enforcement ID remains for $prefix")
            check(value.startsWith(prefix)) { "Expected $prefix but scripted $value" }
            return value
        }
    }

    private companion object {
        const val ENVIRONMENT = "development"
        const val PACKAGE = "seen/demo"
        const val VERSION = "1.2.3"
        const val NOW = "2026-07-17T00:00:00Z"
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        val OWNER = publisher("owner")

        fun subject() = EnforcementReleaseSubject(PACKAGE, VERSION)

        fun principal(name: String) = EnforcementPrincipal("prn_${name.padEnd(16, '0')}", emptySet())

        fun publisher(name: String) = EnforcementPrincipal(
            "prn_${name.padEnd(16, '0')}",
            setOf(EnforcementRoles.PUBLISHER),
        )

        fun security(name: String) = EnforcementPrincipal(
            "prn_${name.padEnd(16, '0')}",
            setOf(EnforcementRoles.SECURITY),
        )

        fun trustAndSafety(name: String) = EnforcementPrincipal(
            "prn_${name.padEnd(16, '0')}",
            setOf(EnforcementRoles.TRUST_AND_SAFETY),
        )

        fun mixedPublisherSecurity(name: String) = EnforcementPrincipal(
            "prn_${name.padEnd(16, '0')}",
            setOf(EnforcementRoles.PUBLISHER, EnforcementRoles.SECURITY),
        )

        fun assertRegistryFailure(
            status: Int,
            code: String,
            block: () -> Unit,
        ): RegistryException {
            val error = assertFailsWith<RegistryException>(block = block)
            assertEquals(status, error.status)
            assertEquals(code, error.code)
            return error
        }
    }
}
