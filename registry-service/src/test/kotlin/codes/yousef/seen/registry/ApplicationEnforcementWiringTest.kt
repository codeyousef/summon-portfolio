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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationEnforcementWiringTest {
    @Test
    fun `resource pipeline serves registry and enforcement routes with configured actors`() = runBlocking {
        val writerToken = "w".repeat(32)
        val reviewerToken = "r".repeat(32)
        val config = testConfig().copy(
            writerToken = writerToken,
            trustAndSafetyToken = reviewerToken,
            trustAndSafetyPrincipal = "reviewer-principal",
            localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.mapIndexed { index, role ->
                role to signingKey(index + 40)
            }.toMap(),
        )
        val resources = RegistryResources.create(
            config,
            Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC),
        )
        try {
            assertEquals(emptySet(), resources.activeSigningRoles)
            assertTrue(resources.hasRegistryRoutes)
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

            val reviewerRead = exchange(
                HttpMethod.GET,
                "/packages/api/v1/reports/${report.reportId}",
                reviewerToken,
            )
            resources.routePipeline().execute(reviewerRead) { error("reviewer report route fell through") }
            assertEquals(200, reviewerRead.testResponse.statusCode)
        } finally {
            resources.close()
        }
    }

    @Test
    fun `server modes expose only their route surface and activate only their signing roles`() = runBlocking {
        val signingKeys = TufRole.ONLINE.mapIndexed { index, role ->
            role to signingKey(index + 80)
        }.toMap()
        val base = testConfig().copy(
            writerToken = "w".repeat(32),
            localOnlineSigningKeysPkcs8Base64 = signingKeys,
        )
        val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)

        assertSurface(
            RegistryResources.create(base.copy(
                serverMode = RegistryServerMode.PUBLIC_API,
            ), clock),
            expectedRoles = emptySet(),
            expectedRegistryRoutes = true,
            expectedEnforcementRoutes = true,
            expectedHandled = setOf(HEALTH_PATH, CATALOG_PATH, PACKAGE_CREATE_PATH, REPORT_PATH),
        )
        assertSurface(
            RegistryResources.create(base.copy(
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
            ), clock),
            expectedRoles = emptySet(),
            expectedRegistryRoutes = true,
            expectedEnforcementRoutes = false,
            expectedHandled = setOf(HEALTH_PATH, CATALOG_PATH),
        )
        assertSurface(
            RegistryResources.create(base.copy(
                serverMode = RegistryServerMode.RELEASE_ACTIONS,
                ownerAllowlist = emptySet(),
                writersEnabled = false,
                publicDelay = Duration.ZERO,
                trustAndSafetyToken = null,
                trustAndSafetyPrincipal = "",
            ), clock),
            expectedRoles = setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            expectedRegistryRoutes = false,
            expectedEnforcementRoutes = true,
            expectedHandled = setOf(HEALTH_PATH, YANK_PATH),
        )
        assertSurface(
            RegistryResources.create(base.copy(
                serverMode = RegistryServerMode.SECURITY_ACTIONS,
                writerMode = "",
                writerToken = "",
                writerPrincipal = "",
                ownerAllowlist = emptySet(),
                writersEnabled = false,
                publicDelay = Duration.ZERO,
                trustAndSafetyToken = null,
                trustAndSafetyPrincipal = "",
                securityToken = "s".repeat(32),
                securityPrincipal = "security-principal",
            ), clock),
            expectedRoles = setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP),
            expectedRegistryRoutes = false,
            expectedEnforcementRoutes = true,
            expectedHandled = setOf(HEALTH_PATH, SECURITY_PATH),
        )
    }

    private suspend fun assertSurface(
        resources: RegistryResources,
        expectedRoles: Set<String>,
        expectedRegistryRoutes: Boolean,
        expectedEnforcementRoutes: Boolean,
        expectedHandled: Set<String>,
    ) {
        try {
            assertEquals(expectedRoles, resources.activeSigningRoles)
            assertEquals(expectedRegistryRoutes, resources.hasRegistryRoutes)
            assertEquals(expectedEnforcementRoutes, resources.hasEnforcementRoutes)
            listOf(HEALTH_PATH, CATALOG_PATH, PACKAGE_CREATE_PATH, REPORT_PATH, YANK_PATH, SECURITY_PATH).forEach { path ->
                val exchange = WiringExchange(WiringRequest(
                    method = if (path in setOf(HEALTH_PATH, CATALOG_PATH)) HttpMethod.GET else HttpMethod.POST,
                    path = path,
                    headers = Headers.Empty,
                    body = ByteArray(0),
                ))
                var fellThrough = false
                resources.routePipeline().execute(exchange) { fellThrough = true }
                if (path in expectedHandled) {
                    assertFalse(fellThrough, "$path must be handled")
                    assertTrue(exchange.testResponse.statusCode in setOf(200, 401), "$path returned an unexpected status")
                } else {
                    assertTrue(fellThrough, "$path must not be exposed")
                }
            }
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

    private companion object {
        const val HEALTH_PATH = "/health"
        const val CATALOG_PATH = "/packages"
        const val PACKAGE_CREATE_PATH = "/packages/api/v1/packages"
        const val REPORT_PATH = "/packages/api/v1/reports"
        const val YANK_PATH = "/packages/api/v1/packages/seen/demo/releases/1.2.3/actions/yank"
        const val SECURITY_PATH =
            "/packages/api/v1/packages/seen/demo/releases/1.2.3/actions/security-quarantine"
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
