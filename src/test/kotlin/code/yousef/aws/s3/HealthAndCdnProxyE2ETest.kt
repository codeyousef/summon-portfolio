package code.yousef.aws.s3

import code.yousef.portfolio.server.HealthEndpoints
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.core.sync.RequestBody
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthAndCdnProxyE2ETest {
    private lateinit var server: VertxServer
    private lateinit var baseUrl: String
    private val s3 = FakeS3Client()
    private val healthy = AtomicBoolean(true)
    private val client = HttpClient.newHttpClient()

    @BeforeTest
    fun start() {
        s3.putObject({ it.bucket("media").key("sha256/${"a".repeat(64)}/p.jpg").contentType("image/jpeg") }, RequestBody.fromBytes(MEDIA))
        s3.putObject({ it.bucket("docs").key("guide/index.html").contentType("text/html") }, RequestBody.fromString("<h1>docs</h1>"))
        val health = HealthEndpoints(readinessProbe = { check(healthy.get()) }, cacheMillis = 0)
        val proxy = S3CdnProxy(s3, mediaBucket = "media", docsBucket = "docs")
        val port = ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"
        server = VertxServer(
            VertxServerConfig(port = port),
            Pipeline().apply {
                use(health::handle)
                use(proxy::handle)
            },
        ) { exchange -> exchange.notFound("Route not found") }
        runBlocking { server.start() }
    }

    @AfterTest
    fun stop() {
        runBlocking { server.stop() }
    }

    @Test
    fun `health endpoints report liveness and readiness without a host header dependency`() {
        assertEquals(200, get("/health").statusCode())
        val ready = get("/health/ready")
        assertEquals(200, ready.statusCode())
        assertEquals("no-store", ready.headers().firstValue("cache-control").orElse(""))
        healthy.set(false)
        assertEquals(503, get("/health/ready").statusCode())
        assertEquals(200, get("/health").statusCode())
    }

    @Test
    fun `CDN proxy streams objects with conditional requests`() {
        val path = "/cdn/media/sha256/${"a".repeat(64)}/p.jpg"
        val response = get(path)
        assertEquals(200, response.statusCode())
        assertContentEquals(MEDIA, response.body())
        assertEquals("image/jpeg", response.headers().firstValue("content-type").orElse(""))
        assertEquals("nosniff", response.headers().firstValue("x-content-type-options").orElse(""))
        val etag = response.headers().firstValue("etag").orElseThrow()
        assertEquals(304, get(path, mapOf("If-None-Match" to etag)).statusCode())

        val head = client.send(
            HttpRequest.newBuilder(URI("$baseUrl$path")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        assertEquals(200, head.statusCode())
        assertEquals(MEDIA.size.toString(), head.headers().firstValue("content-length").orElse(""))

        val docs = get("/cdn/docs/guide/index.html")
        assertEquals(200, docs.statusCode())
        assertTrue(docs.headers().firstValue("content-security-policy").orElse("").startsWith("default-src 'none'"))

        assertEquals(404, get("/cdn/media/sha256/${"b".repeat(64)}/missing.jpg").statusCode())
        assertEquals(404, get("/cdn/media/").statusCode())
        assertEquals(404, get("/cdn/media/a/%2e%2e/b").statusCode())
        assertEquals(404, get("/cdn/docs/a//b").statusCode())
        val post = client.send(
            HttpRequest.newBuilder(URI("$baseUrl$path")).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        assertEquals(405, post.statusCode())
        assertEquals(404, get("/other").statusCode())
    }

    private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<ByteArray> {
        val builder = HttpRequest.newBuilder(URI("$baseUrl$path")).GET()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private companion object {
        val MEDIA = ByteArray(200_000) { (it % 251).toByte() }
    }
}
