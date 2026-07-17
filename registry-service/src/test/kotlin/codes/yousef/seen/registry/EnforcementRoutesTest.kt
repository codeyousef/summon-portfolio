package codes.yousef.seen.registry

import codes.yousef.aether.core.Attributes
import codes.yousef.aether.core.Cookie
import codes.yousef.aether.core.Cookies
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.Headers
import codes.yousef.aether.core.HttpMethod
import codes.yousef.aether.core.Request
import codes.yousef.aether.core.Response
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnforcementRoutesTest {
    @Test
    fun `opaque credentials reject role mixing and malformed authorization`() {
        assertFailsWith<IllegalArgumentException> {
            OpaqueEnforcementAuthenticator(listOf(
                OpaqueEnforcementCredential(
                    OWNER_TOKEN,
                    principal(OWNER_ID, EnforcementRoles.PUBLISHER, EnforcementRoles.SECURITY),
                ),
            ))
        }

        val auth = authenticator()
        val error = assertFailsWith<RegistryException> { auth.authenticate("bearer $OWNER_TOKEN") }
        assertEquals(401, error.status)
        assertEquals("unauthenticated", error.code)
    }

    @Test
    fun `metadata publication failures preserve the denying availability state`() {
        fun service(initialAvailability: String): Pair<InMemoryRegistryRepository, EnforcementService> {
            val repository = InMemoryRegistryRepository()
            assertTrue(repository.reserveRelease(activeRelease(initialAvailability)))
            return repository to EnforcementService(repository, "development", FIXED_CLOCK)
        }

        val (yankRepository, yankService) = service("available")
        assertFailsWith<IllegalStateException> {
            yankService.yankRelease(
                PACKAGE,
                VERSION,
                YankReleaseRequest("Immediate withdrawal"),
                principal(OWNER_ID, EnforcementRoles.PUBLISHER),
            ) { error("metadata unavailable") }
        }
        assertEquals("yanked", yankRepository.getRelease(PACKAGE, VERSION)!!.record.state.availability)

        val (unyankRepository, unyankService) = service("yanked")
        assertFailsWith<IllegalStateException> {
            unyankService.unyankRelease(
                PACKAGE,
                VERSION,
                principal(OWNER_ID, EnforcementRoles.PUBLISHER),
            ) { error("metadata unavailable") }
        }
        assertEquals("yanked", unyankRepository.getRelease(PACKAGE, VERSION)!!.record.state.availability)

        val (quarantineRepository, quarantineService) = service("available")
        assertFailsWith<IllegalStateException> {
            quarantineService.securityQuarantineRelease(
                PACKAGE,
                VERSION,
                SecurityQuarantineRequest("Immediate security review", "critical"),
                principal(ENFORCER_ID, EnforcementRoles.SECURITY),
            ) { error("metadata unavailable") }
        }
        assertEquals(
            "security-quarantined",
            quarantineRepository.getRelease(PACKAGE, VERSION)!!.record.state.availability,
        )
    }

    @Test
    fun `contract routes preserve idempotency conceal reports and enforce independent actors`() = runBlocking {
        val fixture = fixture()

        val reportRequest = SecurityReportCreateRequest(
            subject = SUBJECT,
            category = "malicious-code",
            summary = "The release contains an unexpected credential collector.",
            evidenceReferences = listOf("evd_0000000000000001"),
        )
        val reportBody = RegistryJson.encodeToString(reportRequest).encodeToByteArray()
        val reportExchange = fixture.post(
            "/packages/api/v1/reports",
            REPORTER_TOKEN,
            "report-create-key-0001",
            reportBody,
        )
        fixture.handle(reportExchange)
        val report = reportExchange.decode<SecurityReportRecord>()
        assertEquals(201, reportExchange.testResponse.statusCode)
        assertEquals("false", reportExchange.header("Idempotency-Replayed"))
        assertEquals("/packages/api/v1/reports/${report.reportId}", reportExchange.header("Location"))

        val replayedReport = fixture.post(
            "/packages/api/v1/reports",
            REPORTER_TOKEN,
            "report-create-key-0001",
            reportBody,
        )
        fixture.handle(replayedReport)
        assertEquals("true", replayedReport.header("Idempotency-Replayed"))
        assertContentEquals(reportExchange.testResponse.body, replayedReport.testResponse.body)

        val authorizedReport = fixture.get("/packages/api/v1/reports/${report.reportId}", REPORTER_TOKEN)
        fixture.handle(authorizedReport)
        assertEquals(200, authorizedReport.testResponse.statusCode)
        assertNotNull(authorizedReport.header("ETag"))
        assertEquals("private,no-store", authorizedReport.header("Cache-Control"))

        val concealedReport = fixture.get("/packages/api/v1/reports/${report.reportId}", OUTSIDER_TOKEN)
        fixture.handle(concealedReport)
        assertEquals(404, concealedReport.testResponse.statusCode)
        assertEquals("not_found", concealedReport.decode<ErrorEnvelope>().error.code)

        val yank = fixture.post(
            "$RELEASE_PATH/actions/yank",
            OWNER_TOKEN,
            "release-yank-key-0001",
            RegistryJson.encodeToString(YankReleaseRequest("Published prematurely")).encodeToByteArray(),
        )
        fixture.handle(yank)
        assertEquals(200, yank.testResponse.statusCode)
        assertEquals("yanked", yank.decode<ReleaseRecord>().state.availability)
        assertEquals(listOf("yanked"), fixture.metadata.releaseAvailabilities)

        val replayedYank = fixture.post(
            "$RELEASE_PATH/actions/yank",
            OWNER_TOKEN,
            "release-yank-key-0001",
            RegistryJson.encodeToString(YankReleaseRequest("Published prematurely")).encodeToByteArray(),
        )
        fixture.handle(replayedYank)
        assertEquals("true", replayedYank.header("Idempotency-Replayed"))
        assertEquals(listOf("yanked"), fixture.metadata.releaseAvailabilities)

        val unyank = fixture.post(
            "$RELEASE_PATH/actions/unyank",
            OWNER_TOKEN,
            "release-unyank-key-001",
        )
        fixture.handle(unyank)
        assertEquals(200, unyank.testResponse.statusCode)
        assertEquals("available", unyank.decode<ReleaseRecord>().state.availability)
        assertEquals(listOf("yanked", "available"), fixture.metadata.releaseAvailabilities)

        val quarantineRequest = SecurityQuarantineRequest(
            reason = "A security investigation requires immediate resolver denial.",
            severity = "critical",
            reportIds = listOf(report.reportId),
        )
        val quarantineBody = RegistryJson.encodeToString(quarantineRequest).encodeToByteArray()
        val publisherQuarantine = fixture.post(
            "$RELEASE_PATH/actions/security-quarantine",
            OWNER_TOKEN,
            "publisher-quarantine-01",
            quarantineBody,
        )
        fixture.handle(publisherQuarantine)
        assertEquals(403, publisherQuarantine.testResponse.statusCode)
        assertEquals(0, fixture.metadata.quarantineCalls)
        assertEquals("available", fixture.release().record.state.availability)

        val quarantine = fixture.post(
            "$RELEASE_PATH/actions/security-quarantine",
            ENFORCER_TOKEN,
            "security-quarantine-001",
            quarantineBody,
        )
        fixture.handle(quarantine)
        val incident = quarantine.decode<SecurityActionRecord>()
        assertEquals(200, quarantine.testResponse.statusCode)
        assertEquals("security-quarantined", incident.action)
        assertEquals(1, fixture.metadata.quarantineCalls)
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val invalidReinstatement = fixture.post(
            "$RELEASE_PATH/actions/security-reinstate",
            REINSTATER_TOKEN,
            "invalid-reinstate-key-01",
            RegistryJson.encodeToString(ReviewedReinstatementRequest(
                "inc_0000000000000099",
                "apl_0000000000000099",
                "rev_0000000000000099",
            )).encodeToByteArray(),
        )
        fixture.handle(invalidReinstatement)
        assertEquals(404, invalidReinstatement.testResponse.statusCode)
        assertEquals(0, fixture.metadata.reinstatementCalls)

        val appealRequest = EnforcementAppealCreateRequest(
            requestedOutcome = "reviewed-reinstatement",
            statement = "The compromised credential was rotated and the release was independently audited.",
        )
        val appeal = fixture.post(
            "/packages/api/v1/incidents/${incident.incidentId}/appeals",
            OWNER_TOKEN,
            "appeal-create-key-0001",
            RegistryJson.encodeToString(appealRequest).encodeToByteArray(),
        )
        fixture.handle(appeal)
        val appealRecord = appeal.decode<EnforcementAppealRecord>()
        assertEquals(201, appeal.testResponse.statusCode)
        assertEquals("/packages/api/v1/appeals/${appealRecord.appealId}", appeal.header("Location"))
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val reviewRequest = AppealReviewRequest(
            decision = "approve-reinstatement",
            rationale = "Independent remediation evidence satisfies the reinstatement policy.",
            emergencyWaiver = false,
        )
        val sameEnforcerReview = fixture.post(
            "/packages/api/v1/appeals/${appealRecord.appealId}/reviews",
            ENFORCER_REVIEW_TOKEN,
            "same-enforcer-review-01",
            RegistryJson.encodeToString(reviewRequest).encodeToByteArray(),
        )
        fixture.handle(sameEnforcerReview)
        assertEquals(403, sameEnforcerReview.testResponse.statusCode)

        val review = fixture.post(
            "/packages/api/v1/appeals/${appealRecord.appealId}/reviews",
            REVIEWER_TOKEN,
            "appeal-review-key-0001",
            RegistryJson.encodeToString(reviewRequest).encodeToByteArray(),
        )
        fixture.handle(review)
        val reviewRecord = review.decode<AppealReviewRecord>()
        assertEquals(201, review.testResponse.statusCode)
        assertTrue(reviewRecord.actorSeparationVerified)
        assertEquals("security-quarantined", fixture.release().record.state.availability)

        val reinstatementRequest = ReviewedReinstatementRequest(
            incidentId = incident.incidentId,
            appealId = appealRecord.appealId,
            reviewId = reviewRecord.reviewId,
        )
        val sameReviewerReinstatement = fixture.post(
            "$RELEASE_PATH/actions/security-reinstate",
            REVIEWER_SECURITY_TOKEN,
            "same-reviewer-reinstate",
            RegistryJson.encodeToString(reinstatementRequest).encodeToByteArray(),
        )
        fixture.handle(sameReviewerReinstatement)
        assertEquals(403, sameReviewerReinstatement.testResponse.statusCode)
        assertEquals(0, fixture.metadata.reinstatementCalls)

        val reinstatement = fixture.post(
            "$RELEASE_PATH/actions/security-reinstate",
            REINSTATER_TOKEN,
            "security-reinstate-0001",
            RegistryJson.encodeToString(reinstatementRequest).encodeToByteArray(),
        )
        fixture.handle(reinstatement)
        val reinstatementRecord = reinstatement.decode<SecurityActionRecord>()
        assertEquals(200, reinstatement.testResponse.statusCode)
        assertEquals("reviewed-reinstated", reinstatementRecord.action)
        assertEquals(1, fixture.metadata.reinstatementCalls)
        assertEquals("available", fixture.release().record.state.availability)
    }

    private fun fixture(): Fixture {
        val repository = InMemoryRegistryRepository()
        assertTrue(repository.reserveRelease(activeRelease()))
        val metadata = RecordingMetadataPublisher()
        val service = EnforcementService(repository, "development", FIXED_CLOCK)
        return Fixture(
            repository,
            EnforcementRoutes(service, repository, authenticator(), metadata, FIXED_CLOCK),
            metadata,
        )
    }

    private fun activeRelease(availability: String = "available"): StoredRelease = StoredRelease(
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
        ownerPrincipal = OWNER_ID,
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

    private class RecordingMetadataPublisher : EnforcementMetadataPublisher {
        val releaseAvailabilities = mutableListOf<String>()
        var quarantineCalls = 0
        var reinstatementCalls = 0

        override fun publishReleaseAvailability(release: StoredRelease) {
            releaseAvailabilities += release.record.state.availability
        }

        override fun publishSecurityQuarantine(
            subject: EnforcementReleaseSubject,
            request: SecurityQuarantineRequest,
        ): SignedMetadataReference {
            assertEquals(SUBJECT, subject)
            assertEquals("critical", request.severity)
            quarantineCalls++
            return metadata(quarantineCalls + reinstatementCalls)
        }

        override fun publishReviewedReinstatement(
            subject: EnforcementReleaseSubject,
            request: ReviewedReinstatementRequest,
        ): SignedMetadataReference {
            assertEquals(SUBJECT, subject)
            assertTrue(request.reviewId.startsWith("rev_"))
            reinstatementCalls++
            return metadata(quarantineCalls + reinstatementCalls)
        }

        override fun restoreSecurityQuarantine(subject: EnforcementReleaseSubject) {
            assertEquals(SUBJECT, subject)
            quarantineCalls++
        }

        private fun metadata(version: Int) = SignedMetadataReference(
            environment = "development",
            role = "security",
            filename = "$version.security.json",
            version = version.toLong(),
            length = 128,
            sha256 = "d".repeat(64),
            publishedAt = "2026-07-16T23:59:00Z",
            expiresAt = "2026-07-18T00:00:00Z",
        )
    }

    private data class Fixture(
        val repository: InMemoryRegistryRepository,
        val routes: EnforcementRoutes,
        val metadata: RecordingMetadataPublisher,
    ) {
        suspend fun handle(exchange: EnforcementTestExchange) = routes.router.handle(exchange)

        fun release(): StoredRelease = requireNotNull(repository.getRelease(PACKAGE, VERSION))

        fun post(
            path: String,
            token: String,
            idempotencyKey: String,
            body: ByteArray = ByteArray(0),
        ) = exchange(HttpMethod.POST, path, token, body, idempotencyKey)

        fun get(path: String, token: String) = exchange(HttpMethod.GET, path, token)

        private fun exchange(
            method: HttpMethod,
            path: String,
            token: String,
            body: ByteArray = ByteArray(0),
            idempotencyKey: String? = null,
        ): EnforcementTestExchange {
            val headers = buildMap {
                put("Authorization", listOf("Bearer $token"))
                if (body.isNotEmpty()) put("Content-Type", listOf("application/json"))
                idempotencyKey?.let { put("Idempotency-Key", listOf(it)) }
            }
            return EnforcementTestExchange(
                EnforcementTestRequest(method, path, Headers(headers), body),
            )
        }
    }

    private companion object {
        const val PACKAGE = "seen/demo"
        const val VERSION = "1.2.3"
        const val RELEASE_PATH = "/packages/api/v1/packages/seen/demo/releases/1.2.3"
        const val NOW = "2026-07-17T00:00:00Z"
        const val OWNER_ID = "prn_owner_000000000001"
        const val REPORTER_ID = "prn_reporter_00000001"
        const val OUTSIDER_ID = "prn_outsider_00000001"
        const val ENFORCER_ID = "prn_enforcer_00000001"
        const val REVIEWER_ID = "prn_reviewer_00000001"
        const val REINSTATER_ID = "prn_reinstater_000001"

        val OWNER_TOKEN = "o".repeat(32)
        val REPORTER_TOKEN = "p".repeat(32)
        val OUTSIDER_TOKEN = "x".repeat(32)
        val ENFORCER_TOKEN = "e".repeat(32)
        val ENFORCER_REVIEW_TOKEN = "f".repeat(32)
        val REVIEWER_TOKEN = "r".repeat(32)
        val REVIEWER_SECURITY_TOKEN = "s".repeat(32)
        val REINSTATER_TOKEN = "i".repeat(32)
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        val SUBJECT = EnforcementReleaseSubject(PACKAGE, VERSION)

        fun principal(id: String, vararg roles: String) = EnforcementPrincipal(id, roles.toSet())

        fun authenticator() = OpaqueEnforcementAuthenticator(listOf(
            OpaqueEnforcementCredential(OWNER_TOKEN, principal(OWNER_ID, EnforcementRoles.PUBLISHER)),
            OpaqueEnforcementCredential(REPORTER_TOKEN, principal(REPORTER_ID)),
            OpaqueEnforcementCredential(OUTSIDER_TOKEN, principal(OUTSIDER_ID)),
            OpaqueEnforcementCredential(ENFORCER_TOKEN, principal(ENFORCER_ID, EnforcementRoles.SECURITY)),
            OpaqueEnforcementCredential(
                ENFORCER_REVIEW_TOKEN,
                principal(ENFORCER_ID, EnforcementRoles.TRUST_AND_SAFETY),
            ),
            OpaqueEnforcementCredential(REVIEWER_TOKEN, principal(REVIEWER_ID, EnforcementRoles.TRUST_AND_SAFETY)),
            OpaqueEnforcementCredential(
                REVIEWER_SECURITY_TOKEN,
                principal(REVIEWER_ID, EnforcementRoles.SECURITY),
            ),
            OpaqueEnforcementCredential(REINSTATER_TOKEN, principal(REINSTATER_ID, EnforcementRoles.SECURITY)),
        ))
    }
}

private class EnforcementTestExchange(
    override val request: Request,
    val testResponse: EnforcementTestResponse = EnforcementTestResponse(),
) : Exchange {
    override val response: Response = testResponse
    override val attributes = Attributes()

    fun header(name: String): String? = testResponse.headers.build()[name]

    inline fun <reified Value> decode(): Value =
        RegistryJson.decodeFromString(testResponse.body.decodeToString())
}

private class EnforcementTestRequest(
    override val method: HttpMethod,
    override val path: String,
    override val headers: Headers,
    private val body: ByteArray,
) : Request {
    override val uri: String = path
    override val query: String? = null
    override val cookies: Cookies = Cookies.Empty

    override suspend fun bodyBytes(): ByteArray = body.copyOf()
}

private class EnforcementTestResponse : Response {
    override var statusCode: Int = 200
    override var statusMessage: String? = null
    override val headers = Headers.HeadersBuilder()
    override val cookies = mutableListOf<Cookie>()
    private val output = ByteArrayOutputStream()
    val body: ByteArray get() = output.toByteArray()

    override suspend fun write(data: ByteArray) {
        output.write(data)
    }

    override suspend fun end() = Unit
}
