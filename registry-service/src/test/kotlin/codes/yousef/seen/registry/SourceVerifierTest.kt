package codes.yousef.seen.registry

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourceVerifierTest {
    @Test
    fun `verifies github and gitlab source against immutable commit trees`() {
        SourceForge.entries.forEach { forge ->
            val snapshot = validSnapshot(forge)
            val inspected = mutableListOf<ForgeInspectionRequest>()
            SourceVerifier(
                client = ForgeSourceClient { request -> inspected += request; snapshot },
                clock = FIXED_CLOCK,
            ).use { verifier ->
                val result = verifier.verify(validInput(forge))

                assertEquals(listOf(ForgeInspectionRequest(forge, REPOSITORY_ID, REQUESTED_REF, INSTALLATION_ID)), inspected)
                assertEquals(forge, result.forge)
                assertEquals(COMMIT, result.resolvedCommit)
                assertEquals(PACKAGE_DIRECTORY, result.packageDirectory)
                assertEquals(ARCHIVE_SHA256, result.archiveSha256)
                assertEquals("MIT", result.licenseSpdx)
                assertEquals("$PACKAGE_DIRECTORY/LICENSE", result.licensePath)
                assertEquals(sha256(LICENSE_BYTES), result.licenseEvidenceSha256)
                assertEquals(VERIFIED_AT, result.verifiedAt)
                assertTrue(Regex("^[0-9a-f]{64}$").matches(result.treeEvidenceSha256))
                assertTrue(Regex("^[0-9a-f]{64}$").matches(result.archiveFileSetSha256))
                assertTrue(Regex("^[0-9a-f]{64}$").matches(result.proofSha256))
                assertNotEquals(result.treeEvidenceSha256, result.archiveFileSetSha256)
            }
        }
    }

    @Test
    fun `fails closed when a requested ref moves away from the expected commit`() {
        val failure = verifyFailure(validInput(), validSnapshot().copy(resolvedCommit = "d".repeat(40)))

        assertEquals(SourceVerificationFailure.MUTABLE_REF, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `rejects repository url id and installation identity mismatches`() {
        val repository = validSnapshot().repository
        val mismatches = listOf(
            validSnapshot().copy(repository = repository.copy(immutableRepositoryId = "repo-elsewhere")) to
                SourceVerificationFailure.REPOSITORY_MISMATCH,
            validSnapshot().copy(repository = repository.copy(canonicalUrl = "https://github.com/seen/elsewhere")) to
                SourceVerificationFailure.REPOSITORY_MISMATCH,
            validSnapshot().copy(repository = repository.copy(authenticatedInstallationIdentities = setOf("another-installation"))) to
                SourceVerificationFailure.INSTALLATION_MISMATCH,
        )

        mismatches.forEach { (snapshot, expected) ->
            val failure = verifyFailure(validInput(), snapshot)
            assertEquals(expected, failure.reason)
            assertStableFailure(failure)
        }
    }

    @Test
    fun `rejects ambiguous exact manifest locations`() {
        val snapshot = validSnapshot()
        val ambiguous = snapshot.copy(blobs = snapshot.blobs + ForgeTreeBlob(
            "examples/copy/Seen.toml",
            gitBlobObjectId(MANIFEST_BYTES),
        ))

        val failure = verifyFailure(validInput(), ambiguous)

        assertEquals(SourceVerificationFailure.MANIFEST_AMBIGUOUS, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `rejects archive content that differs from the immutable tree`() {
        val snapshot = validSnapshot()
        val mismatched = snapshot.copy(blobs = snapshot.blobs.map { blob ->
            if (blob.path.endsWith("src/main.seen")) blob.copy(objectId = gitBlobObjectId("different".encodeToByteArray()))
            else blob
        })

        val failure = verifyFailure(validInput(), mismatched)

        assertEquals(SourceVerificationFailure.PACKAGE_FILE_MISMATCH, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `requires archive files to equal the declared package file set`() {
        val undeclared = validInput().copy(declaredPackagePaths = setOf("Seen.toml"))

        val failure = verifyFailure(undeclared, validSnapshot())

        assertEquals(SourceVerificationFailure.ARCHIVE_FILE_SET_MISMATCH, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `fails closed when immutable source has no license evidence`() {
        val failure = verifyFailure(validInput(), validSnapshot().copy(licenses = emptyList()))

        assertEquals(SourceVerificationFailure.LICENSE_MISSING, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `rejects license evidence that contradicts the declared spdx identity`() {
        val snapshot = validSnapshot().copy(licenses = listOf(
            ForgeLicenseEvidence("$PACKAGE_DIRECTORY/LICENSE", LICENSE_BYTES, spdxId = "Apache-2.0"),
        ))

        val failure = verifyFailure(validInput(), snapshot)

        assertEquals(SourceVerificationFailure.LICENSE_MISMATCH, failure.reason)
        assertStableFailure(failure)
    }

    @Test
    fun `provider timeout and error both fail closed without leaking details`() {
        val timeout = SourceVerifier(
            client = ForgeSourceClient {
                Thread.sleep(5_000)
                validSnapshot()
            },
            clock = FIXED_CLOCK,
            verificationTimeout = Duration.ofMillis(25),
        ).use { verifier ->
            assertFailsWith<SourceVerificationException> { verifier.verify(validInput()) }
        }
        assertEquals(SourceVerificationFailure.FORGE_TIMEOUT, timeout.reason)
        assertStableFailure(timeout)

        val unavailable = SourceVerifier(
            client = ForgeSourceClient { throw IOException("provider-secret-response") },
            clock = FIXED_CLOCK,
        ).use { verifier ->
            assertFailsWith<SourceVerificationException> { verifier.verify(validInput()) }
        }
        assertEquals(SourceVerificationFailure.FORGE_UNAVAILABLE, unavailable.reason)
        assertStableFailure(unavailable)
        assertTrue(unavailable.message.orEmpty().contains("verification failed"))
        assertTrue(!unavailable.message.orEmpty().contains("provider-secret-response"))
    }

    @Test
    fun `github app provider accepts downloaded rsa pem and mints a repository scoped token`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.capture()
            synchronized(requests) { requests += request }
            when (request.path) {
                "/app/installations/2468013579" -> exchange.respond(
                    200,
                    """{"id":2468013579,"app_id":987654,"account":{"id":1,"login":"seen"}}""",
                )
                "/app/installations/2468013579/access_tokens" -> exchange.respond(
                    201,
                    """{"token":"ghs_fresh_installation_token","expires_at":"2026-07-17T02:02:03Z"}""",
                )
                else -> exchange.respond(404, "{}")
            }
        }
        server.start()
        try {
            // Both formats are accepted: generic PKCS#8 and GitHub's downloaded PKCS#1 RSA PEM.
            GithubAppInstallationTokenProvider(
                appId = "987654",
                privateKeyPem = pkcs8Pem(keyPair.private.encoded),
                githubApiBase = URI("http://127.0.0.1:${server.address.port}"),
                clock = FIXED_CLOCK,
            )
            val provider = GithubAppInstallationTokenProvider(
                appId = "987654",
                privateKeyPem = pkcs1Pem(keyPair.private as RSAPrivateCrtKey),
                githubApiBase = URI("http://127.0.0.1:${server.address.port}"),
                clock = FIXED_CLOCK,
            )

            val credential = provider.credential("123456789", "2468013579")

            assertEquals("ghs_fresh_installation_token", credential.token)
            assertEquals(setOf("2468013579"), credential.authenticatedInstallationIdentities)
            assertEquals(listOf("GET", "POST"), requests.map(CapturedRequest::method))
            assertTrue(requests.all { verifyJwt(it.authorization.removePrefix("Bearer "), keyPair.public.encoded) })
            assertTrue(requests[1].body.contains("\"repository_ids\":[123456789]"))
            assertTrue(requests[1].body.contains("\"contents\":\"read\""))
            assertTrue(requests.all { it.apiVersion == "2022-11-28" })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `github client resolves numeric repository then uses documented owner name routes`() {
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = exchange.capture()
            synchronized(requests) { requests += request }
            when (request.path) {
                "/repositories/123456789" -> exchange.respond(
                    200,
                    """{"id":123456789,"full_name":"seen/demo","html_url":"https://github.com/seen/demo"}""",
                )
                "/repos/seen/demo/commits/refs%2Ftags%2Fv1.2.3" -> exchange.respond(
                    200,
                    """{"sha":"${"a".repeat(40)}","commit":{"tree":{"sha":"${"b".repeat(40)}"}}}""",
                )
                "/repos/seen/demo/git/trees/${"b".repeat(40)}" -> exchange.respond(
                    200,
                    """{"truncated":false,"tree":[{"path":"Seen.toml","type":"blob","sha":"${"c".repeat(40)}"},{"path":"LICENSE","type":"blob","sha":"${"d".repeat(40)}"}]}""",
                )
                "/repos/seen/demo/license" -> exchange.respond(
                    200,
                    """{"content":"${Base64.getEncoder().encodeToString(LICENSE_BYTES)}","license":{"spdx_id":"MIT"},"html_url":"https://github.com/seen/demo/blob/${"a".repeat(40)}/LICENSE"}""",
                )
                else -> exchange.respond(404, "{}")
            }
        }
        server.start()
        try {
            val client = JdkHttpForgeSourceClient(
                tokenProvider = ForgeBearerTokenProvider { forge, repositoryId, installationId ->
                    assertEquals(SourceForge.GITHUB, forge)
                    assertEquals("123456789", repositoryId)
                    assertEquals("2468013579", installationId)
                    ForgeBearerCredential("ghs_per_execution", setOf(installationId))
                },
                githubApiBase = URI("http://127.0.0.1:${server.address.port}"),
            )

            val snapshot = client.inspect(ForgeInspectionRequest(
                SourceForge.GITHUB,
                "123456789",
                "refs/tags/v1.2.3",
                "2468013579",
            ))

            assertEquals("123456789", snapshot.repository.immutableRepositoryId)
            assertEquals(setOf("2468013579"), snapshot.repository.authenticatedInstallationIdentities)
            assertEquals("a".repeat(40), snapshot.resolvedCommit)
            assertEquals(listOf("Seen.toml", "LICENSE"), snapshot.blobs.map(ForgeTreeBlob::path))
            assertEquals(2, requests.count { it.path == "/repositories/123456789" })
            assertTrue(requests.none { it.path == "/installation" || it.path.startsWith("/repositories/123456789/") })
            assertTrue(requests.any { it.path == "/repos/seen/demo/commits/refs%2Ftags%2Fv1.2.3" })
            assertTrue(requests.all { it.authorization == "Bearer ghs_per_execution" })
        } finally {
            server.stop(0)
        }
    }

    private fun verifyFailure(
        input: SourceVerificationInput,
        snapshot: ForgeSourceSnapshot,
    ): SourceVerificationException = SourceVerifier(
        client = ForgeSourceClient { snapshot },
        clock = FIXED_CLOCK,
    ).use { verifier ->
        assertFailsWith { verifier.verify(input) }
    }

    private fun assertStableFailure(failure: SourceVerificationException) {
        assertEquals("source_proof_invalid", failure.code)
        assertEquals("Source proof verification failed", failure.message)
    }

    private fun validInput(forge: SourceForge = SourceForge.GITHUB) = SourceVerificationInput(
        forge = forge,
        repositoryId = REPOSITORY_ID,
        canonicalRepositoryUrl = repositoryUrl(forge),
        installationIdentity = INSTALLATION_ID,
        requestedRef = REQUESTED_REF,
        expectedCommit = COMMIT,
        archiveSha256 = ARCHIVE_SHA256,
        manifestBytes = MANIFEST_BYTES,
        archiveFiles = listOf(
            SourceArchiveFile("Seen.toml", MANIFEST_BYTES),
            SourceArchiveFile("src/main.seen", SOURCE_BYTES),
        ),
        declaredPackagePaths = linkedSetOf("Seen.toml", "src/main.seen"),
        licenseSpdx = "MIT",
    )

    private fun validSnapshot(forge: SourceForge = SourceForge.GITHUB): ForgeSourceSnapshot = ForgeSourceSnapshot(
        repository = ForgeRepositoryEvidence(
            forge = forge,
            immutableRepositoryId = REPOSITORY_ID,
            canonicalUrl = repositoryUrl(forge),
            authenticatedInstallationIdentities = setOf(INSTALLATION_ID, "seen-bot"),
        ),
        requestedRef = REQUESTED_REF,
        resolvedCommit = COMMIT,
        treeObjectId = if (forge == SourceForge.GITHUB) TREE_OBJECT_ID else null,
        blobs = listOf(
            ForgeTreeBlob("README.md", gitBlobObjectId("repository".encodeToByteArray())),
            ForgeTreeBlob("$PACKAGE_DIRECTORY/Seen.toml", gitBlobObjectId(MANIFEST_BYTES)),
            ForgeTreeBlob("$PACKAGE_DIRECTORY/src/main.seen", gitBlobObjectId(SOURCE_BYTES)),
            ForgeTreeBlob("$PACKAGE_DIRECTORY/LICENSE", gitBlobObjectId(LICENSE_BYTES)),
        ),
        licenses = listOf(
            ForgeLicenseEvidence(
                path = "$PACKAGE_DIRECTORY/LICENSE",
                bytes = LICENSE_BYTES,
                spdxId = if (forge == SourceForge.GITHUB) "MIT" else null,
                evidenceUrl = "${repositoryUrl(forge)}/blob/$COMMIT/$PACKAGE_DIRECTORY/LICENSE",
            ),
        ),
    )

    private fun repositoryUrl(forge: SourceForge): String = when (forge) {
        SourceForge.GITHUB -> "https://github.com/seen/demo"
        SourceForge.GITLAB -> "https://gitlab.com/seen/demo"
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String,
        val apiVersion: String?,
        val body: String,
    )

    private fun HttpExchange.capture(): CapturedRequest = CapturedRequest(
        method = requestMethod,
        path = requestURI.rawPath,
        authorization = requestHeaders.getFirst("Authorization").orEmpty(),
        apiVersion = requestHeaders.getFirst("X-GitHub-Api-Version"),
        body = requestBody.use { it.readAllBytes().decodeToString() },
    )

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.encodeToByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun pkcs8Pem(encoded: ByteArray): String = buildString {
        appendLine("-----BEGIN PRIVATE KEY-----")
        appendLine(Base64.getMimeEncoder(64, "\n".encodeToByteArray()).encodeToString(encoded))
        append("-----END PRIVATE KEY-----")
    }

    private fun pkcs1Pem(key: RSAPrivateCrtKey): String {
        val encoded = RSAPrivateKey(
            key.modulus,
            key.publicExponent,
            key.privateExponent,
            key.primeP,
            key.primeQ,
            key.primeExponentP,
            key.primeExponentQ,
            key.crtCoefficient,
        ).encoded
        return buildString {
            appendLine("-----BEGIN RSA PRIVATE KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".encodeToByteArray()).encodeToString(encoded))
            append("-----END RSA PRIVATE KEY-----")
        }
    }

    private fun verifyJwt(jwt: String, encodedPublicKey: ByteArray): Boolean {
        val parts = jwt.split('.')
        if (parts.size != 3) return false
        val publicKey = java.security.KeyFactory.getInstance("RSA")
            .generatePublic(java.security.spec.X509EncodedKeySpec(encodedPublicKey))
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            verify(Base64.getUrlDecoder().decode(parts[2]))
        }
        if (!valid) return false
        val claims = Base64.getUrlDecoder().decode(parts[1]).decodeToString()
        return claims.contains("\"iss\":\"987654\"") && claims.contains("\"exp\":") && claims.contains("\"iat\":")
    }

    private companion object {
        const val REPOSITORY_ID = "repo-123"
        const val INSTALLATION_ID = "installation-9"
        const val REQUESTED_REF = "refs/tags/v1.2.3"
        const val PACKAGE_DIRECTORY = "packages/demo"
        val COMMIT = "a".repeat(40)
        val TREE_OBJECT_ID = "b".repeat(40)
        val ARCHIVE_SHA256 = "c".repeat(64)
        val MANIFEST_BYTES = "manifest-version = 1\n[project]\nversion = \"1.2.3\"\n".encodeToByteArray()
        val SOURCE_BYTES = "fun main() = 42\n".encodeToByteArray()
        val LICENSE_BYTES = "MIT License\n\nPermission is hereby granted...\n".encodeToByteArray()
        val VERIFIED_AT: Instant = Instant.parse("2026-07-17T01:02:03Z")
        val FIXED_CLOCK: Clock = Clock.fixed(VERIFIED_AT, ZoneOffset.UTC)
    }
}
