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
import java.io.ByteArrayOutputStream
import java.nio.channels.ClosedChannelException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class IdempotencyRoutesTest {
    @Test
    fun `closed channel after successful body write does not trigger a second response`() = runBlocking {
        val fixture = routeFixture()
        val response = DisconnectAfterCommitResponse()
        val exchange = RegistryTestExchange(
            RegistryTestRequest(HttpMethod.GET, "/health", Headers(emptyMap()), ByteArray(0)),
            response,
        )

        fixture.routes.router.handle(exchange)

        assertEquals(200, response.statusCode)
        assertEquals(1, response.writeCalls)
        assertEquals(1, response.endCalls)
        assertTrue(response.body.isNotEmpty())
    }

    @Test
    fun `takeover lease rejects stale completion ownership`() {
        val repository = InMemoryRegistryRepository()
        val started = Instant.parse("2026-07-16T00:00:00Z")
        fun pending(attempt: String, processingExpiry: Instant, fingerprint: String = "fingerprint") = StoredIdempotency(
            scope = "scope",
            fingerprint = fingerprint,
            attemptId = attempt,
            createdAt = started.utc(),
            processingExpiresAt = processingExpiry.utc(),
            expiresAt = started.plus(Duration.ofHours(24)).utc(),
        )
        assertIs<IdempotencyBegin.Acquired>(repository.beginIdempotency(pending("old", started.plusSeconds(60)), started))
        val takeoverTime = started.plusSeconds(61)
        assertIs<IdempotencyBegin.Acquired>(repository.beginIdempotency(pending("new", takeoverTime.plusSeconds(60)), takeoverTime))
        val response = StoredIdempotencyResponse(201, Base64.getEncoder().encodeToString("ok".encodeToByteArray()), emptyMap())
        assertFalse(repository.completeIdempotency("scope", "fingerprint", "old", response))
        assertTrue(repository.completeIdempotency("scope", "fingerprint", "new", response))
        assertIs<IdempotencyBegin.Replay>(repository.beginIdempotency(pending("third", takeoverTime.plusSeconds(120)), takeoverTime))
        assertEquals("idempotency_key_reused", assertFailsWith<RegistryException> {
            repository.beginIdempotency(pending("different", takeoverTime.plusSeconds(120), "changed"), takeoverTime)
        }.code)
    }

    @Test
    fun `expired processing lease recovers package created before response persistence`() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = FailOnceCompletionRepository()
        val fixture = routeFixture(clock, repository)
        val body = RegistryJson.encodeToString(
            CreatePackageRequest("seen/demo", "Demo", "https://github.com/seen/demo", "MIT"),
        ).encodeToByteArray()
        val first = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", body, "crash-create-key-00001")
        fixture.routes.router.handle(first)
        assertEquals(500, first.testResponse.statusCode)
        assertEquals("seen/demo", repository.getPackage("seen/demo")!!.record.identity)

        clock.advance(Duration.ofMinutes(3))
        val recovered = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", body, "crash-create-key-00001")
        fixture.routes.router.handle(recovered)
        assertEquals(201, recovered.testResponse.statusCode)
        assertEquals("false", recovered.header("Idempotency-Replayed"))
        val replay = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", body, "crash-create-key-00001")
        fixture.routes.router.handle(replay)
        assertEquals("true", replay.header("Idempotency-Replayed"))
        assertContentEquals(recovered.testResponse.body, replay.testResponse.body)
    }

    @Test
    fun `expired processing lease recovers and extends an orphaned release reservation`() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val repository = FailOnceCompletionRepository()
        val fixture = routeFixture(clock, repository)
        fixture.service.createPackage(CreatePackageRequest("seen/demo"), WriterPrincipal("publisher"))
        val archive = archiveOf("Seen.toml" to manifestToml(), "src/main.seen" to "fun main() {}".encodeToByteArray())
        val body = RegistryJson.encodeToString(reserveRequest(archive)).encodeToByteArray()
        val path = "/packages/api/v1/packages/seen/demo/releases"
        val first = fixture.exchange(HttpMethod.POST, path, body, "crash-reserve-key-0001")
        fixture.routes.router.handle(first)
        assertEquals(500, first.testResponse.statusCode)
        val orphaned = repository.getRelease("seen/demo", "1.2.3")!!

        clock.advance(Duration.ofHours(26))
        val recovered = fixture.exchange(HttpMethod.POST, path, body, "crash-reserve-key-0001")
        fixture.routes.router.handle(recovered)
        val reservation = RegistryJson.decodeFromString<ReleaseReservation>(recovered.testResponse.body.decodeToString())
        assertEquals(201, recovered.testResponse.statusCode)
        assertEquals(orphaned.uploadId, reservation.upload.uploadId)
        assertEquals(true, Instant.parse(reservation.upload.expiresAt).isAfter(Instant.parse(orphaned.uploadExpiresAt)))

        val mismatched = fixture.exchange(
            HttpMethod.POST,
            path,
            RegistryJson.encodeToString(reserveRequest(archive).copy(source = validSource(expectedCommit = "b".repeat(40)))).encodeToByteArray(),
            "different-reserve-key-01",
        )
        fixture.routes.router.handle(mismatched)
        assertEquals(409, mismatched.testResponse.statusCode)
    }

    @Test
    fun `unexpected mutation failure is stored instead of wedging the key`() = runBlocking {
        val delegate = InMemoryRegistryRepository()
        var attempts = 0
        val failing = object : RegistryRepository by delegate {
            override fun createPackage(value: StoredPackage): Boolean {
                attempts++
                error("synthetic storage failure")
            }
        }
        val fixture = routeFixture(repository = failing)
        val body = RegistryJson.encodeToString(CreatePackageRequest("seen/demo")).encodeToByteArray()
        val first = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", body, "failing-request-key-001")
        fixture.routes.router.handle(first)
        val replay = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", body, "failing-request-key-001")
        fixture.routes.router.handle(replay)

        assertEquals(500, first.testResponse.statusCode)
        assertEquals("false", first.header("Idempotency-Replayed"))
        assertEquals("true", replay.header("Idempotency-Replayed"))
        assertContentEquals(first.testResponse.body, replay.testResponse.body)
        assertEquals(1, attempts)
    }

    @Test
    fun `create and reserve retries replay the exact persisted response`() = runBlocking {
        val fixture = routeFixture()
        val createBody = RegistryJson.encodeToString(
            CreatePackageRequest("seen/demo", "Demo", "https://github.com/seen/demo", "MIT"),
        ).encodeToByteArray()
        val firstCreate = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", createBody, "create-request-key-0001")
        fixture.routes.router.handle(firstCreate)
        val replayCreate = fixture.exchange(HttpMethod.POST, "/packages/api/v1/packages", createBody, "create-request-key-0001")
        fixture.routes.router.handle(replayCreate)
        assertEquals(201, firstCreate.testResponse.statusCode)
        assertEquals("false", firstCreate.header("Idempotency-Replayed"))
        assertEquals("true", replayCreate.header("Idempotency-Replayed"))
        assertContentEquals(firstCreate.testResponse.body, replayCreate.testResponse.body)
        assertEquals(firstCreate.header("X-Request-Id"), replayCreate.header("X-Request-Id"))

        val archive = archiveOf("Seen.toml" to manifestToml(), "src/main.seen" to "fun main() {}".encodeToByteArray())
        val reserveBody = RegistryJson.encodeToString(reserveRequest(archive)).encodeToByteArray()
        val firstReserve = fixture.exchange(
            HttpMethod.POST, "/packages/api/v1/packages/seen/demo/releases", reserveBody, "reserve-request-key-001",
        )
        fixture.routes.router.handle(firstReserve)
        val replayReserve = fixture.exchange(
            HttpMethod.POST, "/packages/api/v1/packages/seen/demo/releases", reserveBody, "reserve-request-key-001",
        )
        fixture.routes.router.handle(replayReserve)
        assertEquals(201, firstReserve.testResponse.statusCode)
        assertEquals("true", replayReserve.header("Idempotency-Replayed"))
        assertContentEquals(firstReserve.testResponse.body, replayReserve.testResponse.body)
        val reservation = RegistryJson.decodeFromString<ReleaseReservation>(firstReserve.testResponse.body.decodeToString())
        assertNull(reservation.release.links.sourceProof)
        assertNull(reservation.release.links.download)
    }

    @Test
    fun `reserve replay stays usable through retention then exact recovery renews the upload`() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val fixture = routeFixture(clock)
        fixture.service.createPackage(CreatePackageRequest("seen/demo"), WriterPrincipal("publisher"))
        val archive = archiveOf("Seen.toml" to manifestToml(), "src/main.seen" to "fun main() {}".encodeToByteArray())
        val body = RegistryJson.encodeToString(reserveRequest(archive)).encodeToByteArray()
        val path = "/packages/api/v1/packages/seen/demo/releases"
        val key = "reserve-retention-key-01"

        val first = fixture.exchange(HttpMethod.POST, path, body, key)
        fixture.routes.router.handle(first)
        val original = RegistryJson.decodeFromString<ReleaseReservation>(first.testResponse.body.decodeToString())
        assertEquals(201, first.testResponse.statusCode)

        clock.advance(Duration.ofHours(2))
        val replay = fixture.exchange(HttpMethod.POST, path, body, key)
        fixture.routes.router.handle(replay)
        assertEquals(201, replay.testResponse.statusCode)
        assertEquals("true", replay.header("Idempotency-Replayed"))
        assertContentEquals(first.testResponse.body, replay.testResponse.body)
        assertTrue(Instant.parse(original.upload.expiresAt).isAfter(clock.instant()))

        clock.advance(Duration.ofHours(22).plusMinutes(1))
        val recovered = fixture.exchange(HttpMethod.POST, path, body, key)
        fixture.routes.router.handle(recovered)
        val renewed = RegistryJson.decodeFromString<ReleaseReservation>(recovered.testResponse.body.decodeToString())
        assertEquals(201, recovered.testResponse.statusCode)
        assertEquals("false", recovered.header("Idempotency-Replayed"))
        assertEquals(original.upload.uploadId, renewed.upload.uploadId)
        assertTrue(Instant.parse(renewed.upload.expiresAt).isAfter(Instant.parse(original.upload.expiresAt)))
        assertTrue(Instant.parse(renewed.upload.expiresAt).isAfter(clock.instant()))

        clock.advance(Duration.ofHours(1))
        val replayAfterOriginalExpiry = fixture.exchange(HttpMethod.POST, path, body, key)
        fixture.routes.router.handle(replayAfterOriginalExpiry)
        assertEquals(201, replayAfterOriginalExpiry.testResponse.statusCode)
        assertEquals("true", replayAfterOriginalExpiry.header("Idempotency-Replayed"))
        assertContentEquals(recovered.testResponse.body, replayAfterOriginalExpiry.testResponse.body)
        assertTrue(Instant.parse(renewed.upload.expiresAt).isAfter(clock.instant()))
    }

    @Test
    fun `lost completion response retries cannot reset the public delay`() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-16T00:00:00Z"))
        val fixture = routeFixture(clock)
        val principal = WriterPrincipal("publisher")
        val archive = archiveOf("Seen.toml" to manifestToml(), "src/main.seen" to "fun main() {}".encodeToByteArray())
        fixture.service.createPackage(CreatePackageRequest("seen/demo"), principal)
        val reservation = fixture.service.reserveRelease("seen/demo", reserveRequest(archive), principal)
        fixture.service.uploadArchive(reservation.upload.uploadId, sha256(archive), archive, principal)

        val path = "/packages/api/v1/uploads/${reservation.upload.uploadId}/complete"
        val completion = CompleteUploadRequest(sha256(archive), archive.size.toLong())
        val body = RegistryJson.encodeToString(completion).encodeToByteArray()
        val first = fixture.exchange(HttpMethod.POST, path, body, "complete-request-key-01")
        fixture.routes.router.handle(first)
        val firstRecord = RegistryJson.decodeFromString<ReleaseRecord>(first.testResponse.body.decodeToString())
        assertEquals(202, first.testResponse.statusCode)
        assertEquals("false", first.header("Idempotency-Replayed"))

        clock.advance(Duration.ofHours(2))
        val retriedPut = fixture.exchange(
            HttpMethod.PUT,
            "/packages/api/v1/uploads/${reservation.upload.uploadId}/archive",
            archive,
            archiveDigest = sha256(archive),
        )
        fixture.routes.router.handle(retriedPut)
        assertEquals(204, retriedPut.testResponse.statusCode)

        val replay = fixture.exchange(HttpMethod.POST, path, body, "complete-request-key-01")
        fixture.routes.router.handle(replay)
        assertEquals(202, replay.testResponse.statusCode)
        assertEquals("true", replay.header("Idempotency-Replayed"))
        assertContentEquals(first.testResponse.body, replay.testResponse.body)

        val alternateKey = fixture.exchange(HttpMethod.POST, path, body, "complete-request-key-02")
        fixture.routes.router.handle(alternateKey)
        val alternateRecord = RegistryJson.decodeFromString<ReleaseRecord>(alternateKey.testResponse.body.decodeToString())
        assertEquals(202, alternateKey.testResponse.statusCode)
        assertEquals(firstRecord.timestamps.publicDelayStartedAt, alternateRecord.timestamps.publicDelayStartedAt)
        assertEquals(firstRecord.timestamps.publicDelayEndsAt, alternateRecord.timestamps.publicDelayEndsAt)
        assertEquals("quarantined", fixture.service.getRelease("seen/demo", "1.2.3", principal).state.lifecycle)

        val changed = fixture.exchange(
            HttpMethod.POST,
            path,
            RegistryJson.encodeToString(completion.copy(compressedBytes = completion.compressedBytes + 1)).encodeToByteArray(),
            "complete-request-key-01",
        )
        fixture.routes.router.handle(changed)
        assertEquals(409, changed.testResponse.statusCode)
        assertEquals("false", changed.header("Idempotency-Replayed"))
        assertEquals(
            "idempotency_key_reused",
            RegistryJson.decodeFromString<ErrorEnvelope>(changed.testResponse.body.decodeToString()).error.code,
        )
        val stored = fixture.service.getRelease("seen/demo", "1.2.3", principal)
        assertEquals(firstRecord.timestamps.publicDelayStartedAt, stored.timestamps.publicDelayStartedAt)
        assertEquals(firstRecord.timestamps.publicDelayEndsAt, stored.timestamps.publicDelayEndsAt)
    }
}

private fun reserveRequest(archive: ByteArray) = ReserveReleaseRequest(
    version = "1.2.3",
    visibility = "public",
    archive = ArchiveReservation("tar+gzip", sha256(archive), archive.size.toLong()),
    manifestSha256 = sha256(manifestToml()),
    manifest = manifestJson(),
    source = SourceDeclaration("github", "seen-demo", "installation-1", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
)

private fun validSource(expectedCommit: String = "a".repeat(40)) =
    SourceDeclaration("github", "seen-demo", "installation-1", "refs/tags/v1.2.3", expectedCommit, "MIT")

private class FailOnceCompletionRepository(
    private val delegate: InMemoryRegistryRepository = InMemoryRegistryRepository(),
) : RegistryRepository by delegate {
    private var fail = true
    override fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean {
        if (fail) {
            fail = false
            error("synthetic crash after domain commit")
        }
        return delegate.completeIdempotency(scope, fingerprint, attemptId, response)
    }
}

private data class RouteFixture(
    val service: RegistryService,
    val routes: RegistryRoutes,
    val config: RegistryConfig,
) {
    fun exchange(
        method: HttpMethod,
        path: String,
        body: ByteArray,
        idempotencyKey: String? = null,
        archiveDigest: String? = null,
    ): RegistryTestExchange {
        val headers = buildMap {
            put("Authorization", listOf("Bearer ${config.writerToken}"))
            put("Content-Type", listOf(if (method == HttpMethod.PUT) "application/gzip" else "application/json"))
            idempotencyKey?.let { put("Idempotency-Key", listOf(it)) }
            archiveDigest?.let { put("X-Seen-Archive-Sha256", listOf(it)) }
        }
        return RegistryTestExchange(RegistryTestRequest(method, path, Headers(headers), body))
    }
}

private fun routeFixture(
    clock: MutableClock = MutableClock(Instant.parse("2026-07-16T00:00:00Z")),
    repository: RegistryRepository = InMemoryRegistryRepository(),
): RouteFixture {
    val storage = InMemoryRegistryObjectStorage()
    val online = testOnlineSigners()
    val config = testConfig()
    val rootSigners = (1..3).map(::testSigner)
    val targetsSigners = (4..5).map(::testSigner)
    TufBootstrapper(
        storage, rootSigners.map(TufSigner::publicKey), rootSigners,
        targetsSigners.map(TufSigner::publicKey), targetsSigners,
        online.publicKeys(), config.environment, config.repositoryId, clock,
    ).bootstrap()
    val tuf = TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock)
    tuf.ensureInitialTransaction()
    val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
    val auth = OpaqueDevWriterAuthenticator(config.writerToken, config.writerPrincipal, config.ownerAllowlist)
    return RouteFixture(service, RegistryRoutes(service, auth, clock), config)
}

private class RegistryTestExchange(
    override val request: Request,
    val testResponse: RegistryTestResponse = RegistryTestResponse(),
) : Exchange {
    override val response: Response = testResponse
    override val attributes = Attributes()
    fun header(name: String): String? = testResponse.headers.build()[name]
}

private class RegistryTestRequest(
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

private open class RegistryTestResponse : Response {
    override var statusCode: Int = 200
    override var statusMessage: String? = null
    override val headers = Headers.HeadersBuilder()
    override val cookies = mutableListOf<Cookie>()
    private val output = ByteArrayOutputStream()
    val body: ByteArray get() = output.toByteArray()
    var writeCalls: Int = 0
        private set
    override suspend fun write(data: ByteArray) {
        writeCalls++
        output.write(data)
    }
    open override suspend fun end() = Unit
}

private class DisconnectAfterCommitResponse : RegistryTestResponse() {
    var endCalls: Int = 0
        private set

    override suspend fun end() {
        endCalls++
        throw ClosedChannelException()
    }
}
