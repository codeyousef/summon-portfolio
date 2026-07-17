package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

class TufSignerServerConfigTest {
    private val authority = LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 81).toByte() })

    @Test
    fun `loads exactly one environment and role bound KMS authority`() {
        val config = TufSignerServerConfig.fromEnvironment(baseEnvironment())

        assertEquals("seen-tuf-releases-dev", config.cloudRunService)
        assertEquals("development", config.environment)
        assertEquals("seen-dev-registry-v1", config.repositoryId)
        assertEquals(TufRole.RELEASES, config.role)
        assertContentEquals(authority.publicKey, config.publicKey)
        assertEquals(Duration.ofDays(7), config.maximumExpiry)
        assertEquals(8080, config.port)
    }

    @Test
    fun `fails closed when any signer identity or authority field is absent`() {
        listOf(
            "K_SERVICE",
            "REGISTRY_ENVIRONMENT",
            "REGISTRY_REPOSITORY_ID",
            "REGISTRY_TUF_SIGNER_ROLE",
            "REGISTRY_TUF_SIGNER_KMS_KEY_VERSION",
            "REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX",
            "REGISTRY_TUF_SIGNER_METADATA_BUCKET",
            "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256",
            "REGISTRY_TUF_SIGNER_AUDIENCE",
            "REGISTRY_TUF_SIGNER_CALLER_BINDINGS",
        ).forEach { missing ->
            assertFailsWith<IllegalArgumentException>(missing) {
                TufSignerServerConfig.fromEnvironment(baseEnvironment() - missing)
            }
        }
    }

    @Test
    fun `rejects cross role cross environment and nonconcrete KMS bindings`() {
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(baseEnvironment() + ("REGISTRY_TUF_SIGNER_ROLE" to TufRole.SECURITY))
        }
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(baseEnvironment() + ("REGISTRY_ENVIRONMENT" to "production"))
        }
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(
                baseEnvironment() + ("REGISTRY_TUF_SIGNER_KMS_KEY_VERSION" to
                    "projects/seen-dev-123456/locations/us-central1/keyRings/seen-registry-dev/" +
                    "cryptoKeys/seen-registry-dev-releases/cryptoKeyVersions/latest"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(
                baseEnvironment() + ("REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX" to "A".repeat(64)),
            )
        }
    }

    @Test
    fun `rejects additional key secret and persistence configuration`() {
        listOf(
            "REGISTRY_KMS_SECURITY_KEY_VERSION" to "another-authority",
            "REGISTRY_SECURITY_SIGNING_KEY_PKCS8_BASE64" to "private-key",
            "REGISTRY_OFFLINE_ROOT_SIGNING_KEYS_PKCS8_BASE64" to "offline-key",
            "REGISTRY_WRITER_TOKEN" to "publisher-secret",
            "REGISTRY_METADATA_BUCKET" to "metadata-bucket",
            "GOOGLE_APPLICATION_CREDENTIALS" to "/secret/service-account.json",
        ).forEach { extra ->
            val failure = assertFailsWith<IllegalArgumentException>(extra.first) {
                TufSignerServerConfig.fromEnvironment(baseEnvironment() + extra)
            }
            assertTrue(!failure.message.orEmpty().contains(extra.second))
        }
    }

    @Test
    fun `enforces role specific expiry request and concurrency bounds`() {
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(
                baseEnvironment() + ("REGISTRY_TUF_SIGNER_MAX_EXPIRY_SECONDS" to Duration.ofDays(8).seconds.toString()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(baseEnvironment() + ("REGISTRY_TUF_SIGNER_MAX_REQUEST_BYTES" to "9000000"))
        }
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(baseEnvironment() + ("REGISTRY_TUF_SIGNER_MAX_CONCURRENCY" to "33"))
        }
    }

    private fun baseEnvironment(): Map<String, String> = mapOf(
        "K_SERVICE" to "seen-tuf-releases-dev",
        "REGISTRY_ENVIRONMENT" to "development",
        "REGISTRY_REPOSITORY_ID" to "seen-dev-registry-v1",
        "REGISTRY_TUF_SIGNER_ROLE" to TufRole.RELEASES,
        "REGISTRY_TUF_SIGNER_KMS_KEY_VERSION" to
            "projects/seen-dev-123456/locations/us-central1/keyRings/seen-registry-dev/" +
            "cryptoKeys/seen-registry-dev-releases/cryptoKeyVersions/1",
        "REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX" to authority.publicKey.hex(),
        "REGISTRY_TUF_SIGNER_METADATA_BUCKET" to "seen-registry-metadata-dev",
        "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256" to "a".repeat(64),
        "REGISTRY_TUF_SIGNER_AUDIENCE" to "https://seen-tuf-releases-dev.example.run.app",
        "REGISTRY_TUF_SIGNER_CALLER_BINDINGS" to
            "release=promoter@example.test,release=refresh@example.test,bootstrap=bootstrap@example.test",
    )
}

class TufSignerHttpServerTest {
    private val now = Instant.parse("2026-07-17T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `returns exactly one typed bounded signature after IAM envelope and state authorization`() {
        val authority = CountingSigner(LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 91).toByte() }))
        val guardCalls = AtomicInteger()
        val config = config(authority)
        TufSignerHttpServer.create(
            config,
            authority,
            TufSignerStatePolicyGuard { request ->
                assertEquals(TufRole.RELEASES, request.role)
                assertEquals(TufSigningOperation.RELEASE, request.operation)
                assertEquals("iam-validated-upstream-envelope", request.bearerToken)
                assertContentEquals(releasesMetadata(), request.canonicalSignedBytes)
                guardCalls.incrementAndGet()
            },
            clock,
        ).use { server ->
            server.start()
            val response = request(server.actualPort, releasesMetadata())

            assertEquals(200, response.statusCode())
            assertEquals(JdkRemoteTufHttpTransport.SIGNATURE_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null))
            assertEquals("64", response.headers().firstValue("Content-Length").orElse(null))
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null))
            assertEquals(64, response.body().size)
            assertContentEquals(authority.delegate.sign(releasesMetadata()), response.body())
            assertEquals(1, guardCalls.get())
            assertEquals(1, authority.calls.get())
        }
    }

    @Test
    fun `timestamp response is returned only after the state guard conditionally commits`() {
        val authority = CountingSigner(LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 97).toByte() }))
        val commits = AtomicInteger()
        val guard = object : TufTimestampCommitStatePolicyGuard {
            override fun authorize(request: TufSignerAuthorizationRequest) {
                assertEquals(TufRole.TIMESTAMP, request.role)
                assertTrue(request.commitDeadline?.isAfter(now) == true)
            }

            override fun commitTimestamp(
                request: TufSignerAuthorizationRequest,
                signature: ByteArray,
                publicKey: ByteArray,
            ) {
                assertContentEquals(authority.delegate.sign(request.canonicalSignedBytes), signature)
                assertContentEquals(authority.publicKey, publicKey)
                commits.incrementAndGet()
            }
        }
        TufSignerHttpServer.create(config(authority, role = TufRole.TIMESTAMP), authority, guard, clock).use { server ->
            server.start()
            val response = request(
                server.actualPort,
                timestampMetadata(),
                role = TufRole.TIMESTAMP,
            )

            assertEquals(200, response.statusCode())
            assertEquals(1, commits.get())
            assertEquals(1, authority.calls.get())
        }
    }

    @Test
    fun `isolates the configured role and rejects missing bearer or bad protocol headers before signing`() {
        val authority = CountingSigner(LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 92).toByte() }))
        val guardCalls = AtomicInteger()
        TufSignerHttpServer.create(
            config(authority),
            authority,
            TufSignerStatePolicyGuard { guardCalls.incrementAndGet() },
            clock,
        ).use { server ->
            server.start()
            val body = releasesMetadata()

            assertEquals(401, request(server.actualPort, body, authorization = null).statusCode())
            assertEquals(401, request(server.actualPort, body, authorization = "Basic opaque").statusCode())
            assertEquals(401, request(server.actualPort, body, authorization = "Bearer bad token").statusCode())
            assertEquals(400, request(server.actualPort, body, forwardedProto = "http").statusCode())
            assertEquals(415, request(server.actualPort, body, contentType = "application/json").statusCode())
            assertEquals(406, request(server.actualPort, body, accept = "application/octet-stream").statusCode())
            assertEquals(403, request(server.actualPort, body, role = TufRole.SECURITY).statusCode())
            assertEquals(403, request(server.actualPort, body, operation = "invalid").statusCode())
            assertEquals(
                403,
                request(server.actualPort, body, operation = TufSigningOperation.SECURITY.wireValue).statusCode(),
            )

            assertEquals(0, guardCalls.get())
            assertEquals(0, authority.calls.get())
        }
    }

    @Test
    fun `rejects noncanonical and oversized bodies without invoking KMS`() {
        val authority = CountingSigner(LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 93).toByte() }))
        val config = config(authority, maximumRequestBytes = 512)
        TufSignerHttpServer.create(
            config,
            authority,
            TufSignerStatePolicyGuard { Unit },
            clock,
        ).use { server ->
            server.start()
            val noncanonical = " ${releasesMetadata().decodeToString()}".encodeToByteArray()
            assertEquals(422, request(server.actualPort, noncanonical).statusCode())
            assertEquals(413, request(server.actualPort, ByteArray(513)).statusCode())
            assertEquals(0, authority.calls.get())
        }
    }

    @Test
    fun `rejects a runtime key that differs from the configured public key`() {
        val configured = LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 94).toByte() })
        val different = LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 95).toByte() })

        assertFailsWith<IllegalArgumentException> {
            TufSignerHttpServer.create(
                config(configured),
                different,
                TufSignerStatePolicyGuard { Unit },
                clock,
            )
        }
    }

    @Test
    fun `default production runtime guard fails closed for every role`() {
        TufRole.ONLINE.forEach { role ->
            val authority = LocalEd25519Signer.fromSeed(ByteArray(32) { index -> (index + role.length + 101).toByte() })
            assertFailsWith<IllegalStateException>(role) {
                FailClosedTufSignerStatePolicyGuardFactory.create(config(authority, role = role))
            }
        }
    }

    private fun config(
        signer: TufSigner,
        maximumRequestBytes: Int = RemoteTufSigner.DEFAULT_MAXIMUM_REQUEST_BYTES,
        role: String = TufRole.RELEASES,
    ): TufSignerServerConfig = TufSignerServerConfig(
        cloudRunService = "seen-tuf-$role-dev",
        environment = "development",
        repositoryId = "seen-dev-registry-v1",
        role = role,
        kmsKeyVersion = "projects/seen-dev-123456/locations/us-central1/keyRings/seen-registry-dev/" +
            "cryptoKeys/seen-registry-dev-$role/cryptoKeyVersions/1",
        publicKeyHex = signer.publicKey.hex(),
        port = 0,
        maximumRequestBytes = maximumRequestBytes,
    )

    private fun request(
        port: Int,
        body: ByteArray,
        authorization: String? = "Bearer iam-validated-upstream-envelope",
        forwardedProto: String = "https",
        contentType: String = JdkRemoteTufHttpTransport.SIGNED_METADATA_CONTENT_TYPE,
        accept: String = JdkRemoteTufHttpTransport.SIGNATURE_CONTENT_TYPE,
        role: String = TufRole.RELEASES,
        operation: String = TufSigningOperation.RELEASE.wireValue,
    ): HttpResponse<ByteArray> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port${TufSignerHttpServer.SIGN_PATH}"))
            .timeout(Duration.ofSeconds(5))
            .header("X-Forwarded-Proto", forwardedProto)
            .header("Content-Type", contentType)
            .header("Accept", accept)
            .header(JdkRemoteTufHttpTransport.ROLE_HEADER, role)
            .header(JdkRemoteTufHttpTransport.OPERATION_HEADER, operation)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        authorization?.let { builder.header("Authorization", it) }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private class CountingSigner(val delegate: TufSigner) : TufSigner {
        val calls = AtomicInteger()
        override val publicKey: ByteArray get() = delegate.publicKey
        override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
            calls.incrementAndGet()
            return delegate.sign(canonicalSignedBytes)
        }

        override fun close() = delegate.close()
    }
}

private fun releasesMetadata(): ByteArray = canonicalJson(buildJsonObject {
    put("_type", "targets")
    put("spec_version", "1.0")
    put("version", 1)
    put("expires", "2026-07-18T00:00:00Z")
    put("environment", "development")
    put("repository_id", "seen-dev-registry-v1")
    put("targets", buildJsonObject {})
})

private fun timestampMetadata(): ByteArray = canonicalJson(buildJsonObject {
    put("_type", "timestamp")
    put("spec_version", "1.0")
    put("version", 1)
    put("expires", "2026-07-17T06:00:00Z")
    put("environment", "development")
    put("repository_id", "seen-dev-registry-v1")
    put("meta", buildJsonObject {
        put("snapshot.json", buildJsonObject {
            put("version", 1)
            put("length", 1)
            put("hashes", buildJsonObject { put("sha256", "0".repeat(64)) })
        })
    })
})

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
