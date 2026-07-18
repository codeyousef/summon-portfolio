package code.yousef.portfolio.server

import codes.yousef.aether.core.Attributes
import codes.yousef.aether.core.Cookie
import codes.yousef.aether.core.Cookies
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.Headers
import codes.yousef.aether.core.HttpMethod
import codes.yousef.aether.core.Request
import codes.yousef.aether.core.Response
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryGatewayMiddlewareTest {
    @Test
    fun `proxies registry request without replacing publisher authorization`() = runBlocking {
        data class ObservedRequest(
            val method: String,
            val target: String,
            val host: String?,
            val authorization: String?,
            val serverlessAuthorization: String?,
            val cookie: String?,
            val forwardedFor: String?,
            val forwardedHost: String?,
            val body: ByteArray
        )

        val observed = CompletableFuture<ObservedRequest>()
        val executor = Executors.newSingleThreadExecutor()
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { request ->
                val target = buildString {
                    append(request.requestURI.rawPath)
                    request.requestURI.rawQuery?.let { append('?').append(it) }
                }
                observed.complete(
                    ObservedRequest(
                        method = request.requestMethod,
                        target = target,
                        host = request.requestHeaders.getFirst("Host"),
                        authorization = request.requestHeaders.getFirst("Authorization"),
                        serverlessAuthorization = request.requestHeaders.getFirst("X-Serverless-Authorization"),
                        cookie = request.requestHeaders.getFirst("Cookie"),
                        forwardedFor = request.requestHeaders.getFirst("X-Forwarded-For"),
                        forwardedHost = request.requestHeaders.getFirst("X-Forwarded-Host"),
                        body = request.requestBody.use { it.readBytes() }
                    )
                )
                val response = "published".encodeToByteArray()
                request.responseHeaders.set("Content-Type", "text/plain")
                request.responseHeaders.set("X-Registry-Test", "forwarded")
                request.sendResponseHeaders(201, response.size.toLong())
                request.responseBody.use { it.write(response) }
            }
            this.executor = executor
            start()
        }

        try {
            val requestBody = byteArrayOf(0, 1, 2, 3, 0x7f)
            val exchange = TestExchange(
                request = TestRequest(
                    method = HttpMethod.POST,
                    path = "/packages/api/v1/publish",
                    query = "dryRun=true",
                    headers = Headers.of(
                        // Vert.x exposes the HTTP/1 wire name in lowercase.
                        "host" to "seen.dev.yousef.codes",
                        "Authorization" to "Bearer publisher-token",
                        "X-Serverless-Authorization" to "Bearer attacker-token",
                        "Cookie" to "admin_session=portfolio-secret",
                        "X-Forwarded-For" to "attacker.invalid",
                        "X-Forwarded-Host" to "attacker.invalid",
                        "Content-Type" to "application/octet-stream"
                    ),
                    body = requestBody
                )
            )
            val upstreamUrl = "http://127.0.0.1:${upstream.address.port}"
            val gateway = RegistryGatewayMiddleware(
                publicHost = DEV_REGISTRY_PUBLIC_HOST,
                upstreamUrl = upstreamUrl,
                releaseActionsUpstreamUrl = upstreamUrl,
                securityActionsUpstreamUrl = upstreamUrl,
            )

            var calledNext = false
            gateway.handle(exchange) { calledNext = true }

            val upstreamRequest = observed.get(5, TimeUnit.SECONDS)
            assertFalse(calledNext)
            assertEquals("POST", upstreamRequest.method)
            assertEquals("/packages/api/v1/publish?dryRun=true", upstreamRequest.target)
            assertEquals("127.0.0.1:${upstream.address.port}", upstreamRequest.host)
            assertEquals("Bearer publisher-token", upstreamRequest.authorization)
            assertNull(upstreamRequest.serverlessAuthorization)
            assertNull(upstreamRequest.cookie)
            assertFalse(upstreamRequest.forwardedFor.orEmpty().contains("attacker.invalid"))
            assertFalse(upstreamRequest.forwardedHost.orEmpty().contains("attacker.invalid"))
            assertContentEquals(requestBody, upstreamRequest.body)
            assertEquals(201, exchange.testResponse.statusCode)
            assertEquals("forwarded", exchange.testResponse.headers.build()["X-Registry-Test"])
            assertEquals("published", exchange.testResponse.body.decodeToString())
            assertTrue(exchange.testResponse.ended)
        } finally {
            upstream.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `adds Cloud Run identity separately and only for the exact public route`() = runBlocking {
        val audiences = mutableListOf<String>()
        val forwarded = mutableListOf<Triple<String, String, String?>>()
        val gateway = RegistryGatewayMiddleware(
            publicHost = DEV_REGISTRY_PUBLIC_HOST,
            upstreamUrl = "https://registry-dev-abc-uc.a.run.app/",
            releaseActionsUpstreamUrl = "https://registry-release-actions.example.run.app",
            securityActionsUpstreamUrl = "https://registry-security-actions.example.run.app",
            identityTokenProvider = RegistryIdentityTokenProvider { audience ->
                audiences += audience
                "cloud-run-id-token"
            },
            proxyForwarder = RegistryProxyForwarder { exchange, upstreamUrl, identityToken ->
                forwarded += Triple(exchange.request.path, upstreamUrl, identityToken)
                exchange.respond(204, "")
            }
        )

        val matched = TestExchange(
            TestRequest(
                path = "/packages/api/v1/metadata/timestamp.json",
                headers = Headers.of("Host" to "SEEN.DEV.YOUSEF.CODES:443")
            )
        )
        var matchedNext = false
        gateway.handle(matched) { matchedNext = true }

        assertFalse(matchedNext)
        assertEquals(listOf("https://registry-dev-abc-uc.a.run.app"), audiences)
        assertEquals(
            listOf(
                Triple<String, String, String?>(
                    "/packages/api/v1/metadata/timestamp.json",
                    "https://registry-dev-abc-uc.a.run.app",
                    "cloud-run-id-token"
                )
            ),
            forwarded
        )

        val wrongHost = TestExchange(
            TestRequest(path = "/packages", headers = Headers.of("Host" to "seen.yousef.codes"))
        )
        val wrongPath = TestExchange(
            TestRequest(path = "/packages-admin", headers = Headers.of("Host" to "seen.dev.yousef.codes"))
        )
        var nextCalls = 0
        gateway.handle(wrongHost) { nextCalls++ }
        gateway.handle(wrongPath) { nextCalls++ }

        assertEquals(2, nextCalls)
        assertEquals(1, forwarded.size)
        assertEquals(1, audiences.size)
    }

    @Test
    fun `routes only exact release and security mutations to isolated action services`() = runBlocking {
        val audiences = mutableListOf<String>()
        val forwarded = mutableListOf<Pair<String, String>>()
        val gateway = RegistryGatewayMiddleware(
            publicHost = DEV_REGISTRY_PUBLIC_HOST,
            upstreamUrl = "https://registry-api.example.run.app",
            releaseActionsUpstreamUrl = "https://registry-release-actions.example.run.app",
            securityActionsUpstreamUrl = "https://registry-security-actions.example.run.app",
            identityTokenProvider = RegistryIdentityTokenProvider { audience ->
                audiences += audience
                "identity-for-$audience"
            },
            proxyForwarder = RegistryProxyForwarder { exchange, upstreamUrl, _ ->
                forwarded += exchange.request.path to upstreamUrl
                exchange.respond(204, "")
            }
        )

        val paths = listOf(
            "/packages/api/v1/packages/seen/demo/releases/1.0.0/actions/yank",
            "/packages/api/v1/packages/seen/demo/releases/1.0.0/actions/unyank",
            "/packages/api/v1/packages/seen/demo/releases/1.0.0/actions/security-quarantine",
            "/packages/api/v1/packages/seen/demo/releases/1.0.0/actions/security-reinstate",
            "/packages/api/v1/packages/seen/demo/releases/1.0.0",
            "/packages/api/v1/packages/seen/demo/releases/1.0.0/actions/yank/extra",
            "/packages/api/v1/metadata/timestamp.json"
        )
        paths.forEach { path ->
            gateway.handle(TestExchange(TestRequest(
                method = HttpMethod.POST,
                path = path,
                headers = Headers.of("Host" to "seen.dev.yousef.codes")
            ))) {
                error("registry paths must not fall through")
            }
        }

        gateway.handle(TestExchange(TestRequest(
            method = HttpMethod.GET,
            path = paths[0],
            headers = Headers.of("Host" to "seen.dev.yousef.codes")
        ))) { error("registry paths must not fall through") }

        assertEquals(
            listOf(
                paths[0] to "https://registry-release-actions.example.run.app",
                paths[1] to "https://registry-release-actions.example.run.app",
                paths[2] to "https://registry-security-actions.example.run.app",
                paths[3] to "https://registry-security-actions.example.run.app",
                paths[4] to "https://registry-api.example.run.app",
                paths[5] to "https://registry-api.example.run.app",
                paths[6] to "https://registry-api.example.run.app"
            ),
            forwarded.take(paths.size)
        )
        assertEquals(paths[0] to "https://registry-api.example.run.app", forwarded.last())
        assertEquals(forwarded.map { it.second }, audiences)
    }

    @Test
    fun `production hostname is accepted only by a separately configured gateway`() = runBlocking {
        val forwarded = mutableListOf<String>()
        val gateway = RegistryGatewayMiddleware(
            publicHost = "seen.yousef.codes",
            upstreamUrl = "https://registry-prod-api.example.run.app",
            releaseActionsUpstreamUrl = "https://registry-prod-release-actions.example.run.app",
            securityActionsUpstreamUrl = "https://registry-prod-security-actions.example.run.app",
            identityTokenProvider = RegistryIdentityTokenProvider { "identity" },
            proxyForwarder = RegistryProxyForwarder { _, upstreamUrl, _ -> forwarded += upstreamUrl }
        )

        var fallThrough = 0
        gateway.handle(
            TestExchange(TestRequest(
                path = "/packages",
                headers = Headers.of("Host" to DEV_REGISTRY_PUBLIC_HOST),
            ))
        ) { fallThrough++ }
        gateway.handle(
            TestExchange(TestRequest(
                path = "/packages",
                headers = Headers.of("Host" to "seen.yousef.codes"),
            ))
        ) { error("the exact production host must be proxied") }

        assertEquals(1, fallThrough)
        assertEquals(listOf("https://registry-prod-api.example.run.app"), forwarded)
    }

    @Test
    fun `returns a generic gateway error without leaking upstream details`() = runBlocking {
        val gateway = RegistryGatewayMiddleware(
            publicHost = DEV_REGISTRY_PUBLIC_HOST,
            upstreamUrl = "https://registry-dev-abc-uc.a.run.app",
            releaseActionsUpstreamUrl = "https://registry-release-actions.example.run.app",
            securityActionsUpstreamUrl = "https://registry-security-actions.example.run.app",
            identityTokenProvider = RegistryIdentityTokenProvider { "identity-token" },
            proxyForwarder = RegistryProxyForwarder { _, _, _ ->
                error("internal-service-name and secret-token")
            }
        )
        val exchange = TestExchange(
            TestRequest(
                path = "/packages/api/v1/blobs/sha256/abc",
                headers = Headers.of("Host" to "seen.dev.yousef.codes")
            )
        )

        gateway.handle(exchange) { error("matched requests must not fall through") }

        val body = exchange.testResponse.body.decodeToString()
        val envelope = Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject
        assertEquals(502, exchange.testResponse.statusCode)
        assertEquals("temporarily_unavailable", envelope["code"]!!.jsonPrimitive.content)
        assertEquals(true, envelope["retryable"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("30", envelope["retry_after_seconds"]!!.jsonPrimitive.content)
        assertTrue(envelope["request_id"]!!.jsonPrimitive.content.startsWith("req_"))
        assertFalse(body.contains("internal-service-name"))
        assertFalse(body.contains("secret-token"))
        assertEquals("no-store", exchange.testResponse.headers.build()["Cache-Control"])
        assertEquals("30", exchange.testResponse.headers.build()["Retry-After"])
        assertEquals(envelope["request_id"]!!.jsonPrimitive.content, exchange.testResponse.headers.build()["X-Request-Id"])
    }

    @Test
    fun `rejects insecure non-loopback upstream`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RegistryGatewayMiddleware(
                publicHost = DEV_REGISTRY_PUBLIC_HOST,
                upstreamUrl = "http://registry.internal.example",
                releaseActionsUpstreamUrl = "https://registry-release-actions.example.run.app",
                securityActionsUpstreamUrl = "https://registry-security-actions.example.run.app",
            )
        }
        assertTrue(error.message.orEmpty().contains("loopback"))
    }

    @Test
    fun `rejects wildcard URL and port shaped public hosts`() {
        listOf("*.yousef.codes", "https://seen.dev.yousef.codes", "seen.dev.yousef.codes:443").forEach { host ->
            val error = assertFailsWith<IllegalArgumentException> {
                RegistryGatewayMiddleware(
                    publicHost = host,
                    upstreamUrl = "https://registry-api.example.run.app",
                    releaseActionsUpstreamUrl = "https://registry-release-actions.example.run.app",
                    securityActionsUpstreamUrl = "https://registry-security-actions.example.run.app",
                )
            }
            assertTrue(error.message.orEmpty().contains("exact DNS hostname"))
        }
    }
}

private const val DEV_REGISTRY_PUBLIC_HOST = "seen.dev.yousef.codes"

private class TestExchange(
    override val request: Request,
    val testResponse: TestResponse = TestResponse()
) : Exchange {
    override val response: Response = testResponse
    override val attributes = Attributes()
}

private class TestRequest(
    override val method: HttpMethod = HttpMethod.GET,
    override val path: String,
    override val query: String? = null,
    override val headers: Headers = Headers.Empty,
    private val body: ByteArray = ByteArray(0)
) : Request {
    override val uri: String = if (query == null) path else "$path?$query"
    override val cookies: Cookies = Cookies.Empty

    override suspend fun bodyBytes(): ByteArray = body.copyOf()
}

private class TestResponse : Response {
    override var statusCode: Int = 200
    override var statusMessage: String? = null
    override val headers = Headers.HeadersBuilder()
    override val cookies = mutableListOf<Cookie>()
    private val output = ByteArrayOutputStream()
    var ended: Boolean = false
        private set

    val body: ByteArray
        get() = output.toByteArray()

    override suspend fun write(data: ByteArray) {
        output.write(data)
    }

    override suspend fun end() {
        ended = true
    }
}
