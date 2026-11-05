package codes.yousef.seen.registry

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AwsSignerIdentityTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val target = RemoteTufSignerTarget(
        URI("https://releases.signers.seen-registry-dev.internal/sign"),
        "https://releases.signers.seen-registry-dev.internal",
    )
    // Fixed, obviously fake credentials: SigV4 signing is a local HMAC.
    private val credentials = StaticCredentialsProvider.create(
        AwsSessionCredentials.create("ASIAEXAMPLEEXAMPLE00", "fake-secret-for-tests-only", "fake-session-token"),
    )

    private fun token(at: Clock = clock, forTarget: RemoteTufSignerTarget = target): String =
        AwsStsTufCallerTokenProvider(forTarget, "eu-north-1", credentials, at).accessToken(forTarget.endpoint)

    private fun verifier(transport: AwsStsCallerIdentityTransport) =
        AwsStsTufSignerCallerTokenVerifier("eu-north-1", transport, Duration.ofSeconds(5), clock)

    private val okTransport = AwsStsCallerIdentityTransport { _, _, _ ->
        AwsStsCallerIdentityResponse(200, stsResponse("arn:aws:sts::123456789012:assumed-role/seen-registry-dev-promoter/ecs-task-1"))
    }

    @Test
    fun `presigned token binds STS host action region audience and a short lifetime`() {
        val token = token()
        assertTrue(token.startsWith("seen-aws-sts-v1."))
        val uri = URI(String(Base64.getUrlDecoder().decode(token.removePrefix("seen-aws-sts-v1."))))
        assertEquals("sts.eu-north-1.amazonaws.com", uri.host)
        val query = uri.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("GetCallerIdentity", query["Action"])
        assertEquals("60", query["X-Amz-Expires"])
        assertEquals("host%3Bx-seen-tuf-signer-audience", query["X-Amz-SignedHeaders"])
        assertTrue(query.getValue("X-Amz-Credential").contains("%2Feu-north-1%2Fsts%2Faws4_request"))
        assertEquals("20261021T120000Z", query["X-Amz-Date"])
        assertFailsWith<RemoteTufSigningException> {
            AwsStsTufCallerTokenProvider(target, "eu-north-1", credentials, clock)
                .accessToken(URI("https://other.signers.seen-registry-dev.internal/sign"))
        }
    }

    @Test
    fun `verifier forwards the configured audience and normalizes the caller role`() {
        var forwarded: Pair<URI, String>? = null
        val caller = verifier { uri, audience, _ ->
            forwarded = uri to audience
            okTransport.get(uri, audience, Duration.ofSeconds(1))
        }.verify(token(), target.audience)
        assertEquals("arn:aws:iam::123456789012:role/seen-registry-dev-promoter", caller.email)
        assertEquals(target.audience, forwarded?.second)
        assertEquals("sts.eu-north-1.amazonaws.com", forwarded?.first?.host)
    }

    @Test
    fun `verifier rejects tampered expired foreign and non role tokens before or after STS`() {
        val good = token()
        val decoded = String(Base64.getUrlDecoder().decode(good.removePrefix("seen-aws-sts-v1.")))
        fun encode(value: String) = "seen-aws-sts-v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        listOf(
            "Bearer-not-ours",
            encode(decoded.replace("sts.eu-north-1.amazonaws.com", "sts.amazonaws.com")),
            encode(decoded.replace("sts.eu-north-1.amazonaws.com", "evil.example")),
            encode(decoded.replace("X-Amz-Expires=60", "X-Amz-Expires=900")),
            encode(decoded.replace("x-seen-tuf-signer-audience", "x-other")),
            encode("$decoded&Extra=1"),
            encode(decoded.replace("Action=GetCallerIdentity", "Action=AssumeRole")),
            token(Clock.fixed(now.minusSeconds(600), ZoneOffset.UTC)),
        ).forEach { candidate ->
            assertFailsWith<TufSigningRequestException>(candidate.take(80)) {
                verifier(okTransport).verify(candidate, target.audience)
            }
        }
        assertFailsWith<TufSigningRequestException> {
            verifier { _, _, _ -> AwsStsCallerIdentityResponse(403, "<ErrorResponse/>".toByteArray()) }
                .verify(good, target.audience)
        }
        assertFailsWith<TufSigningRequestException> {
            verifier { _, _, _ ->
                AwsStsCallerIdentityResponse(200, stsResponse("arn:aws:iam::123456789012:user/human"))
            }.verify(good, target.audience)
        }
        assertFailsWith<TufSignerCallerVerificationUnavailableException> {
            verifier { _, _, _ -> AwsStsCallerIdentityResponse(503, ByteArray(0)) }.verify(good, target.audience)
        }
        assertFailsWith<TufSignerCallerVerificationUnavailableException> {
            verifier { _, _, _ -> throw java.io.IOException("unreachable") }.verify(good, target.audience)
        }
        assertFailsWith<TufSignerCallerVerificationUnavailableException> {
            verifier { _, _, _ ->
                AwsStsCallerIdentityResponse(200, "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>".toByteArray())
            }.verify(good, target.audience)
        }
    }

    @Test
    fun `verifier fails closed on duplicated misplaced or foreign STS response elements`() {
        val good = token()
        val role = "arn:aws:sts::123456789012:assumed-role/seen-registry-dev-promoter/ecs-task-1"
        listOf(
            String(stsResponse(role)).replace("<Arn>", "<Arn>$role</Arn><Arn>"),
            String(stsResponse(role)).replace("</GetCallerIdentityResult>", "</GetCallerIdentityResult><GetCallerIdentityResult/>"),
            """<GetCallerIdentityResponse><Arn>$role</Arn></GetCallerIdentityResponse>""",
            """<AssumeRoleResponse><GetCallerIdentityResult><Arn>$role</Arn></GetCallerIdentityResult></AssumeRoleResponse>""",
        ).forEach { body ->
            assertFailsWith<TufSignerCallerVerificationUnavailableException>(body.take(80)) {
                verifier { _, _, _ -> AwsStsCallerIdentityResponse(200, body.toByteArray()) }.verify(good, target.audience)
            }
        }
    }

    @Test
    fun `assumed role normalization keeps partition account and role only`() {
        assertEquals(
            "arn:aws-us-gov:iam::123456789012:role/r",
            roleArnFromAssumedRole("arn:aws-us-gov:sts::123456789012:assumed-role/r/session-1"),
        )
        assertFailsWith<TufSigningRequestException> { roleArnFromAssumedRole("arn:aws:sts::123456789012:federated-user/x") }
    }

    @Test
    fun `AWS KMS signer configuration binds one role ARN region and caller roles`() {
        val config = TufSignerServerConfig.fromEnvironment(awsSignerEnvironment())
        assertEquals(TufSignerKeyProvider.AWS_KMS, config.keyProvider)
        assertEquals(KEY_ARN, config.awsKmsKeyArn)
        assertEquals("eu-north-1", config.awsRegion)
        assertEquals("", config.kmsKeyVersion)
        val guard = requireNotNull(config.stateGuardConfig)
        assertEquals(TufSignerKeyProvider.AWS_KMS, guard.callerIdentity)
        assertEquals(RegistryObjectStoreProvider.S3, guard.objectStoreConfig?.provider)
        assertEquals(
            setOf("arn:aws:iam::123456789012:role/seen-registry-dev-promoter"),
            guard.callerEmails.getValue(TufSigningOperation.RELEASE),
        )
    }

    @Test
    fun `AWS KMS signer configuration fails closed on foreign authority`() {
        val base = awsSignerEnvironment()
        listOf(
            base + ("REGISTRY_AWS_KMS_SNAPSHOT_KEY_ARN" to KEY_ARN.replace("0f1e", "1f1e")),
            base + ("REGISTRY_AWS_KMS_RELEASES_KEY_ARN" to "arn:aws:kms:eu-north-1:123456789012:alias/seen-releases"),
            base + ("REGISTRY_AWS_KMS_RELEASES_KEY_ARN" to KEY_ARN.replace("eu-north-1", "eu-west-1")),
            base + ("REGISTRY_TUF_SIGNER_KMS_KEY_VERSION" to "projects/p/locations/l/keyRings/k/cryptoKeys/c/cryptoKeyVersions/1"),
            base + ("AWS_ACCESS_KEY_ID" to "AKIAEXAMPLE"),
            base + ("REGISTRY_TUF_SIGNER_CALLER_BINDINGS" to "release=promoter@example.test"),
            base + ("REGISTRY_TUF_SIGNER_PROVIDER" to "azure"),
            base - "AWS_REGION",
            base - "REGISTRY_TUF_SIGNER_SERVICE_NAME",
            base + ("REGISTRY_TUF_SIGNER_R2_ACCESS_KEY_ID" to "static"),
        ).forEach { environment ->
            assertFailsWith<IllegalArgumentException>(environment.toString()) {
                TufSignerServerConfig.fromEnvironment(environment)
            }
        }
        // Cloud KMS signers reject AWS key identifiers.
        assertFailsWith<IllegalArgumentException> {
            TufSignerServerConfig.fromEnvironment(
                base - "REGISTRY_TUF_SIGNER_PROVIDER" - "REGISTRY_TUF_SIGNER_SERVICE_NAME" + mapOf(
                    "K_SERVICE" to "seen-tuf-releases-dev",
                    "REGISTRY_TUF_SIGNER_KMS_KEY_VERSION" to "projects/seen-dev-123456/locations/us-central1/" +
                        "keyRings/seen-registry-dev/cryptoKeys/seen-registry-dev-releases/cryptoKeyVersions/1",
                ),
            )
        }
    }

    @Test
    fun `coordinators in DynamoDB mode authenticate with STS and reject static bearers`() {
        val env = mapOf("REGISTRY_STORAGE_MODE" to "dynamodb", "AWS_REGION" to "eu-north-1")
        assertTrue(defaultRemoteTufTokenProvider(target, env::get) is AwsStsTufCallerTokenProvider)
        assertFailsWith<IllegalArgumentException> {
            defaultRemoteTufTokenProvider(target, (env + ("SEEN_SIGNER_CALL_TOKEN" to "x".repeat(40)))::get)
        }
        assertFailsWith<IllegalArgumentException> {
            defaultRemoteTufTokenProvider(target, mapOf("REGISTRY_STORAGE_MODE" to "dynamodb")::get)
        }
    }

    @Test
    fun `signer exposes only an unauthenticated empty liveness probe besides sign`() {
        val signer = LocalEd25519Signer.fromSeed(ByteArray(32) { (it + 17).toByte() })
        val config = TufSignerServerConfig(
            cloudRunService = "seen-tuf-releases-dev",
            environment = "development",
            repositoryId = "seen-dev-registry-v1",
            role = TufRole.RELEASES,
            kmsKeyVersion = "",
            publicKeyHex = signer.publicKey.joinToString("") { "%02x".format(it) },
            port = 0,
            keyProvider = TufSignerKeyProvider.AWS_KMS,
            awsKmsKeyArn = KEY_ARN,
            awsRegion = "eu-north-1",
        )
        TufSignerHttpServer.create(config, signer, TufSignerStatePolicyGuard { error("must not authorize") }).use { server ->
            server.start()
            val client = java.net.http.HttpClient.newHttpClient()
            fun get(path: String) = client.send(
                java.net.http.HttpRequest.newBuilder(URI("http://127.0.0.1:${server.actualPort}$path")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray(),
            )
            val health = get("/healthz")
            assertEquals(204, health.statusCode())
            assertEquals(0, health.body().size)
            assertEquals(404, get("/healthz/extra").statusCode())
            assertEquals(405, get("/sign").statusCode())
        }
    }

    private fun awsSignerEnvironment(): Map<String, String> = mapOf(
        "REGISTRY_TUF_SIGNER_PROVIDER" to "aws-kms",
        "REGISTRY_TUF_SIGNER_SERVICE_NAME" to "seen-tuf-releases-dev",
        "AWS_REGION" to "eu-north-1",
        "REGISTRY_ENVIRONMENT" to "development",
        "REGISTRY_REPOSITORY_ID" to "seen-dev-registry-v1",
        "REGISTRY_TUF_SIGNER_ROLE" to TufRole.RELEASES,
        "REGISTRY_AWS_KMS_RELEASES_KEY_ARN" to KEY_ARN,
        "REGISTRY_TUF_SIGNER_PUBLIC_KEY_HEX" to "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        "REGISTRY_TUF_SIGNER_OBJECT_STORE_PROVIDER" to "s3",
        "REGISTRY_TUF_SIGNER_METADATA_BUCKET" to "seen-registry-dev-metadata",
        "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256" to "a".repeat(64),
        "REGISTRY_TUF_SIGNER_AUDIENCE" to "https://releases.signers.seen-registry-dev.internal",
        "REGISTRY_TUF_SIGNER_CALLER_BINDINGS" to "release=arn:aws:iam::123456789012:role/seen-registry-dev-promoter",
    )

    private fun stsResponse(arn: String) = """
        <GetCallerIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
          <GetCallerIdentityResult>
            <Arn>$arn</Arn>
            <UserId>AROAEXAMPLE:ecs-task-1</UserId>
            <Account>123456789012</Account>
          </GetCallerIdentityResult>
          <ResponseMetadata><RequestId>00000000-0000-0000-0000-000000000000</RequestId></ResponseMetadata>
        </GetCallerIdentityResponse>
    """.trimIndent().toByteArray()

    private companion object {
        const val KEY_ARN = "arn:aws:kms:eu-north-1:123456789012:key/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
    }
}
