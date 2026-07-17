package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteTufSignerTest {
    private val endpoint = URI("https://release-signer.example.test/sign")

    @Test
    fun `posts canonical signed bytes with bounded authenticated role request and verifies response`() {
        val authority = testRemoteAuthority(31)
        val signed = metadata(TufRole.RELEASES)
        lateinit var captured: RemoteTufHttpRequest
        val remote = RemoteTufSigner(
            endpoint = endpoint,
            role = TufRole.RELEASES,
            operation = TufSigningOperation.RELEASE,
            publicKey = authority.publicKey,
            tokenProvider = RemoteTufTokenProvider { requested ->
                assertEquals(endpoint, requested)
                "short-lived-token"
            },
            transport = RemoteTufHttpTransport { request ->
                captured = request
                RemoteTufHttpResponse(200, authority.sign(request.body))
            },
            requestTimeout = Duration.ofSeconds(4),
        )

        val signature = remote.sign(signed)

        assertContentEquals(authority.sign(signed), signature)
        assertEquals(endpoint, captured.endpoint)
        assertEquals(TufRole.RELEASES, captured.role)
        assertEquals(TufSigningOperation.RELEASE.wireValue, captured.operation)
        assertEquals("Bearer short-lived-token", captured.authorization)
        assertContentEquals(signed, captured.body)
        assertEquals(Duration.ofSeconds(4), captured.timeout)
        assertEquals(RemoteTufSigner.DEFAULT_MAXIMUM_RESPONSE_BYTES, captured.maximumResponseBytes)
    }

    @Test
    fun `rejects a signature that does not match the pinned public key`() {
        val pinned = testRemoteAuthority(32)
        val wrong = testRemoteAuthority(33)
        val remote = remote(pinned) { request -> RemoteTufHttpResponse(200, wrong.sign(request.body)) }

        val failure = assertFailsWith<RemoteTufSigningException> { remote.sign(metadata(TufRole.RELEASES)) }

        assertTrue(failure.message.orEmpty().contains("does not match the pinned key"))
    }

    @Test
    fun `bounds response bodies and reports status without exposing error content`() {
        val authority = testRemoteAuthority(34)
        val oversized = remote(authority) {
            RemoteTufHttpResponse(200, ByteArray(RemoteTufSigner.DEFAULT_MAXIMUM_RESPONSE_BYTES + 1))
        }
        assertTrue(
            assertFailsWith<RemoteTufSigningException> { oversized.sign(metadata(TufRole.RELEASES)) }
                .message.orEmpty().contains("size limit"),
        )

        val errorText = "upstream secret diagnostic"
        val failed = remote(authority) { RemoteTufHttpResponse(503, errorText.encodeToByteArray()) }
        val failure = assertFailsWith<RemoteTufSigningException> { failed.sign(metadata(TufRole.RELEASES)) }
        assertEquals("Remote signer returned HTTP 503", failure.message)
        assertTrue(!failure.message.orEmpty().contains(errorText))
    }

    @Test
    fun `rejects noncanonical or oversized requests before authentication and transport`() {
        val authority = testRemoteAuthority(35)
        val tokens = AtomicInteger()
        val requests = AtomicInteger()
        val remote = RemoteTufSigner(
            endpoint = endpoint,
            role = TufRole.RELEASES,
            operation = TufSigningOperation.RELEASE,
            publicKey = authority.publicKey,
            tokenProvider = RemoteTufTokenProvider { tokens.incrementAndGet(); "token" },
            transport = RemoteTufHttpTransport { requests.incrementAndGet(); RemoteTufHttpResponse(200, ByteArray(64)) },
            maximumRequestBytes = 256,
        )

        assertFailsWith<TufSigningRequestException> {
            remote.sign(" ${metadata(TufRole.RELEASES).decodeToString()}".encodeToByteArray())
        }
        assertFailsWith<TufSigningRequestException> {
            remote.sign(canonicalJson(buildJsonObject { put("padding", "x".repeat(300)) }))
        }
        assertEquals(0, tokens.get())
        assertEquals(0, requests.get())
    }

    @Test
    fun `constructor enforces secure endpoint and bounded timeout`() {
        val authority = testRemoteAuthority(36)
        val token = RemoteTufTokenProvider { "token" }
        val transport = RemoteTufHttpTransport { RemoteTufHttpResponse(500, byteArrayOf()) }

        assertFailsWith<IllegalArgumentException> {
            RemoteTufSigner(
                URI("http://signer.example.test/sign"),
                TufRole.RELEASES,
                TufSigningOperation.RELEASE,
                authority.publicKey,
                token,
                transport,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteTufSigner(
                endpoint,
                TufRole.RELEASES,
                TufSigningOperation.RELEASE,
                authority.publicKey,
                token,
                transport,
                Duration.ofSeconds(31),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteTufSigner(
                endpoint,
                TufRole.RELEASES,
                TufSigningOperation.SECURITY,
                authority.publicKey,
                token,
                transport,
            )
        }
    }

    private fun remote(
        authority: TufSigner,
        response: (RemoteTufHttpRequest) -> RemoteTufHttpResponse,
    ) = RemoteTufSigner(
        endpoint = endpoint,
        role = TufRole.RELEASES,
        operation = TufSigningOperation.RELEASE,
        publicKey = authority.publicKey,
        tokenProvider = RemoteTufTokenProvider { "token" },
        transport = RemoteTufHttpTransport(response),
    )
}

class RoleLockedTufSigningServiceTest {
    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `each online role signs one valid bounded request with its sole configured signer`() {
        TufRole.ONLINE.forEachIndexed { index, role ->
            val signer = CountingSigner(testRemoteAuthority(40 + index))
            val service = service(role, signer)
            val body = metadata(role)

            assertContentEquals(signer.delegate.sign(body), service.sign(role, body))
            assertEquals(1, signer.calls.get(), role)
        }
    }

    @Test
    fun `rejects wrong role environment and repository before invoking signer`() {
        val signer = CountingSigner(testRemoteAuthority(45))
        val service = service(TufRole.RELEASES, signer)

        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.SECURITY, metadata(TufRole.RELEASES))
        }
        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.RELEASES, metadata(TufRole.RELEASES, environment = "production"))
        }
        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.RELEASES, metadata(TufRole.RELEASES, repositoryId = "seen-prod-registry-v1"))
        }
        assertEquals(0, signer.calls.get())
    }

    @Test
    fun `rejects noncanonical metadata and metadata for a different TUF type`() {
        val signer = CountingSigner(testRemoteAuthority(46))
        val service = service(TufRole.SNAPSHOT, signer)

        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.SNAPSHOT, "\n${metadata(TufRole.SNAPSHOT).decodeToString()}".encodeToByteArray())
        }
        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.SNAPSHOT, metadata(TufRole.TIMESTAMP))
        }
        assertEquals(0, signer.calls.get())
    }

    @Test
    fun `enforces configured expiry and version bounds`() {
        val signer = CountingSigner(testRemoteAuthority(47))
        val service = service(TufRole.TIMESTAMP, signer)

        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.TIMESTAMP, metadata(TufRole.TIMESTAMP, version = 10))
        }
        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.TIMESTAMP, metadata(TufRole.TIMESTAMP, expires = now.plus(Duration.ofHours(3))))
        }
        assertFailsWith<TufSigningRequestException> {
            service.sign(TufRole.TIMESTAMP, metadata(TufRole.TIMESTAMP, expires = now))
        }
        assertEquals(0, signer.calls.get())
    }

    @Test
    fun `separates releases targets from security quarantine targets`() {
        val releaseSigner = CountingSigner(testRemoteAuthority(48))
        val securitySigner = CountingSigner(testRemoteAuthority(49))
        val releaseService = service(TufRole.RELEASES, releaseSigner)
        val securityService = service(TufRole.SECURITY, securitySigner)

        assertFailsWith<TufSigningRequestException> {
            releaseService.sign(TufRole.RELEASES, metadata(TufRole.RELEASES, targets = securityTarget()))
        }
        assertFailsWith<TufSigningRequestException> {
            securityService.sign(TufRole.SECURITY, metadata(TufRole.SECURITY, targets = releaseTarget()))
        }
        assertEquals(0, releaseSigner.calls.get())
        assertEquals(0, securitySigner.calls.get())
    }

    @Test
    fun `rejects an invalid signature from the configured role signer`() {
        val authority = testRemoteAuthority(50)
        val invalid = object : TufSigner {
            override val publicKey = authority.publicKey
            override fun sign(canonicalSignedBytes: ByteArray) = ByteArray(64)
        }
        val service = service(TufRole.RELEASES, invalid)

        assertFailsWith<RemoteTufSigningException> {
            service.sign(TufRole.RELEASES, metadata(TufRole.RELEASES))
        }
    }

    private fun service(role: String, signer: TufSigner) = RoleLockedTufSigningService(
        policy = RoleLockedTufSigningPolicy(
            role = role,
            environment = "development",
            repositoryId = "seen-dev-registry-v1",
            acceptedVersions = 7L..9L,
            maximumExpiry = Duration.ofHours(2),
        ),
        signer = signer,
        clock = clock,
    )

    private class CountingSigner(val delegate: TufSigner) : TufSigner {
        val calls = AtomicInteger()
        override val publicKey: ByteArray get() = delegate.publicKey
        override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
            calls.incrementAndGet()
            return delegate.sign(canonicalSignedBytes)
        }
    }
}

private fun metadata(
    role: String,
    environment: String = "development",
    repositoryId: String = "seen-dev-registry-v1",
    version: Long = 8,
    expires: Instant = Instant.parse("2026-07-17T01:00:00Z"),
    targets: JsonObject = JsonObject(emptyMap()),
): ByteArray {
    val type = when (role) {
        TufRole.RELEASES, TufRole.SECURITY -> "targets"
        TufRole.SNAPSHOT -> "snapshot"
        TufRole.TIMESTAMP -> "timestamp"
        else -> error("Unsupported test role")
    }
    return canonicalJson(buildJsonObject {
        put("_type", type)
        put("spec_version", "1.0")
        put("version", version)
        put("expires", expires.toString())
        put("environment", environment)
        put("repository_id", repositoryId)
        when (role) {
            TufRole.RELEASES, TufRole.SECURITY -> put("targets", targets)
            TufRole.SNAPSHOT -> put("meta", JsonObject(mapOf(
                "targets.json" to fileReference(1),
                "releases.json" to fileReference(version),
                "security.json" to fileReference(version),
            )))
            TufRole.TIMESTAMP -> put("meta", JsonObject(mapOf("snapshot.json" to fileReference(version))))
        }
    })
}

private fun fileReference(version: Long): JsonElement = buildJsonObject {
    put("version", version)
    put("length", 128)
    put("hashes", buildJsonObject { put("sha256", "a".repeat(64)) })
}

private fun releaseTarget(): JsonObject = targetWithCustom(buildJsonObject {
    put("availability", "available")
})

private fun securityTarget(): JsonObject = targetWithCustom(buildJsonObject {
    put("availability", "security-quarantined")
    put("incident_id", "inc_12345678")
    put("security_action", "quarantine")
})

private fun targetWithCustom(custom: JsonObject): JsonObject = JsonObject(mapOf(
    "packages/seen/demo/1.2.3/${"b".repeat(64)}/demo-1.2.3.seenpkg.tgz" to buildJsonObject {
        put("length", 128)
        put("hashes", buildJsonObject { put("sha256", "b".repeat(64)) })
        put("custom", custom)
    },
))

private fun testRemoteAuthority(seed: Int): TufSigner =
    LocalEd25519Signer.fromSeed(ByteArray(32) { (seed + it).toByte() })
