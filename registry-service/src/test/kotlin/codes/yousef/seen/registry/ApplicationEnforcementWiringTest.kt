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
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationEnforcementWiringTest {
    @Test
    fun `resource pipeline serves registry and enforcement routes with configured actors`() = runBlocking {
        val writerToken = "w".repeat(32)
        val securityToken = "s".repeat(32)
        val config = testConfig().copy(
            writerToken = writerToken,
            securityToken = securityToken,
            securityPrincipal = "security-principal",
            localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.mapIndexed { index, role ->
                role to signingKey(index + 40)
            }.toMap(),
        )
        val resources = RegistryResources.create(
            config,
            Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC),
        )
        try {
            val health = WiringExchange(WiringRequest(HttpMethod.GET, "/health", Headers.Empty, ByteArray(0)))
            resources.routePipeline().execute(health) { error("health route fell through") }
            assertEquals(200, health.testResponse.statusCode)

            val request = SecurityReportCreateRequest(
                subject = EnforcementReleaseSubject("seen/demo", "1.2.3"),
                category = "vulnerability",
                summary = "A reproducible security issue was observed.",
            )
            val reportExchange = exchange(
                HttpMethod.POST,
                "/packages/api/v1/reports",
                writerToken,
                RegistryJson.encodeToString(request).encodeToByteArray(),
                "application-wiring-key-01",
            )
            resources.routePipeline().execute(reportExchange) { error("report route fell through") }
            assertEquals(201, reportExchange.testResponse.statusCode)
            val report = RegistryJson.decodeFromString<SecurityReportRecord>(
                reportExchange.testResponse.body.decodeToString(),
            )

            val securityRead = exchange(
                HttpMethod.GET,
                "/packages/api/v1/reports/${report.reportId}",
                securityToken,
            )
            resources.routePipeline().execute(securityRead) { error("security report route fell through") }
            assertEquals(200, securityRead.testResponse.statusCode)
        } finally {
            resources.close()
        }
    }

    private fun exchange(
        method: HttpMethod,
        path: String,
        token: String,
        body: ByteArray = ByteArray(0),
        idempotencyKey: String? = null,
    ): WiringExchange {
        val headers = buildMap {
            put("Authorization", listOf("Bearer $token"))
            if (body.isNotEmpty()) put("Content-Type", listOf("application/json"))
            idempotencyKey?.let { put("Idempotency-Key", listOf(it)) }
        }
        return WiringExchange(WiringRequest(method, path, Headers(headers), body))
    }

    private fun signingKey(seed: Int): String {
        val key = Ed25519PrivateKeyParameters(ByteArray(32) { (it + seed).toByte() })
        return Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(key).encoded)
    }
}

private class WiringExchange(
    override val request: Request,
    val testResponse: WiringResponse = WiringResponse(),
) : Exchange {
    override val response: Response = testResponse
    override val attributes = Attributes()
}

private class WiringRequest(
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

private class WiringResponse : Response {
    override var statusCode: Int = 200
    override var statusMessage: String? = null
    override val headers = Headers.HeadersBuilder()
    override val cookies = mutableListOf<Cookie>()
    private val output = ByteArrayOutputStream()
    val body: ByteArray get() = output.toByteArray()
    override suspend fun write(data: ByteArray) { output.write(data) }
    override suspend fun end() = Unit
}
