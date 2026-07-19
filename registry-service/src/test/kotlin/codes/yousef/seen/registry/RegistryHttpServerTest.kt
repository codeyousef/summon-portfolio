package codes.yousef.seen.registry

import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.respondJson
import codes.yousef.aether.core.jvm.VertxServerConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistryHttpServerTest {
    @Test
    fun `read only transport does not intercept or retain archive uploads`() {
        val config = testConfig().copy(
            environment = "production",
            repositoryId = "seen-prod-registry-v1",
            registryOrigin = "https://seen.yousef.codes/packages",
            serverMode = RegistryServerMode.READ_ONLY_PUBLIC_API,
            writerMode = "",
            writerToken = "",
            writerPrincipal = "",
            ownerAllowlist = emptySet(),
            writersEnabled = false,
            publicDelay = Duration.ZERO,
            trustAndSafetyToken = null,
            trustAndSafetyPrincipal = "",
        )
        val clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC)
        val mutableRepository = InMemoryRegistryRepository()
        val mutableStorage = InMemoryRegistryObjectStorage()
        val repository = ReadOnlyRegistryRepository(mutableRepository)
        val storage = ReadOnlyRegistryObjectStorage(mutableStorage)
        val tuf = TufPublisher(
            repository,
            storage,
            testOnlineSigners(),
            config.environment,
            config.repositoryId,
            config.registryOrigin,
            clock,
        )
        val routes = RegistryRoutes(
            service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock),
            auth = null,
            clock = clock,
            catalogNavigationLinks = CatalogNavigationLinks.fromRegistryOrigin(config.registryOrigin),
            mutationsEnabled = false,
        )
        val pipeline = Pipeline().apply { use(routes.router.asMiddleware()) }
        RegistryHttpServer(
            VertxServerConfig(
                port = 0,
                decompressionSupported = false,
                maxRequestBodySize = ArchivePolicy.MAX_COMPRESSED_BYTES.toInt() + 1,
            ),
            pipeline,
            routes,
            streamingUploadsEnabled = false,
        ) { exchange ->
            exchange.respondJson(404, mapOf("error" to "not_found"), RegistryJson)
        }.use { server ->
            server.start()
            val uploadId = "upl_0123456789abcdef"
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:${server.actualPort}/packages/api/v1/uploads/$uploadId/archive"))
                    .header("Content-Type", "application/gzip")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray("not accepted".encodeToByteArray()))
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )

            assertEquals(404, response.statusCode())
            assertNull(mutableStorage.getQuarantine(uploadId))
        }
    }

    @Test
    fun `production transport streams an authenticated archive into quarantine`() {
        val fixture = fixture()
        fixture.server.use { server ->
            server.start()
            val response = fixture.upload(server.actualPort, fixture.archive)
            assertEquals(204, response.statusCode())
            assertContentEquals(fixture.archive, fixture.storage.getQuarantine(fixture.uploadId))
            assertEquals("quarantined", fixture.repository.findReleaseByUpload(fixture.uploadId)!!.record.state.lifecycle)
        }
    }

    @Test
    fun `digest substitution fails before an object is retained`() {
        val fixture = fixture()
        fixture.server.use { server ->
            server.start()
            val substituted = fixture.archive.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            val response = fixture.upload(server.actualPort, substituted)
            assertEquals(422, response.statusCode())
            assertNull(fixture.storage.getQuarantine(fixture.uploadId))
            assertEquals("reserved", fixture.repository.findReleaseByUpload(fixture.uploadId)!!.record.state.lifecycle)
        }
    }

    private fun fixture(): Fixture {
        val config = testConfig()
        val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        val tuf = TufPublisher(repository, storage, online, config.environment, config.repositoryId, config.registryOrigin, clock)
        val service = RegistryService(config, repository, storage, ArchiveValidator(), tuf, clock)
        val principal = WriterPrincipal("publisher")
        val archive = "streamed-archive-evidence".encodeToByteArray()
        service.createPackage(CreatePackageRequest("seen/demo", repository = "https://github.com/seen/demo"), principal)
        val reservation = service.reserveRelease("seen/demo", ReserveReleaseRequest(
            version = "1.2.3",
            visibility = "public",
            archive = ArchiveReservation("tar+gzip", sha256(archive), archive.size.toLong()),
            manifestSha256 = "a".repeat(64),
            manifest = manifestJson(),
            source = SourceDeclaration("github", "123", "456", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
        ), principal)
        val auth = OpaqueDevWriterAuthenticator(config.writerToken, config.writerPrincipal, config.ownerAllowlist)
        val routes = RegistryRoutes(service, auth, clock)
        val pipeline = Pipeline().apply { use(routes.router.asMiddleware()) }
        val server = RegistryHttpServer(
            VertxServerConfig(port = 0, decompressionSupported = false, maxRequestBodySize = ArchivePolicy.MAX_COMPRESSED_BYTES.toInt() + 1),
            pipeline,
            routes,
        ) { exchange ->
            exchange.respondJson(404, mapOf("error" to "not_found"), RegistryJson)
        }
        return Fixture(repository, storage, server, reservation.upload.uploadId, archive, config.writerToken)
    }

    private data class Fixture(
        val repository: InMemoryRegistryRepository,
        val storage: InMemoryRegistryObjectStorage,
        val server: RegistryHttpServer,
        val uploadId: String,
        val archive: ByteArray,
        val token: String,
    ) {
        fun upload(port: Int, bytes: ByteArray): HttpResponse<ByteArray> = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/packages/api/v1/uploads/$uploadId/archive"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/gzip")
                .header("X-Seen-Archive-Sha256", sha256(archive))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
    }
}
