package codes.yousef.seen.registry

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * AWS replacement for Google-signed OIDC caller tokens between a coordinator
 * and a role-locked signer service.
 *
 * The coordinator presents a short-lived SigV4 *presigned* STS
 * `GetCallerIdentity` URL whose signed headers include the signer's audience.
 * The signer forwards it, adding its own configured audience header, to the
 * pinned regional STS endpoint. STS validates the signature, so a valid answer
 * proves the caller's IAM role, and a token minted for another signer's
 * audience fails signature validation. This is the scheme used by the EKS
 * IAM authenticator; it needs no shared secret and no extra AWS resource, and
 * the coordinator role needs no IAM permission for GetCallerIdentity.
 */
internal object AwsStsCallerToken {
    const val AUDIENCE_HEADER = "x-seen-tuf-signer-audience"
    const val TOKEN_PREFIX = "seen-aws-sts-v1."
    val MAXIMUM_EXPIRY: Duration = Duration.ofSeconds(60)
    val MAXIMUM_CLOCK_SKEW: Duration = Duration.ofMinutes(5)

    fun stsHost(region: String): String {
        require(AWS_REGION_NAME.matches(region)) { "AWS_REGION is invalid" }
        return "sts.$region.amazonaws.com"
    }

    fun stsRequest(region: String, audience: String): SdkHttpRequest = SdkHttpRequest.builder()
        .method(SdkHttpMethod.GET)
        .protocol("https")
        .host(stsHost(region))
        .encodedPath("/")
        .putRawQueryParameter("Action", "GetCallerIdentity")
        .putRawQueryParameter("Version", "2011-06-15")
        .putHeader(AUDIENCE_HEADER, audience)
        .build()
}

/** Coordinator-side token provider: one presigned STS request per signing call. */
class AwsStsTufCallerTokenProvider internal constructor(
    private val target: RemoteTufSignerTarget,
    private val region: String,
    private val credentials: AwsCredentialsProvider,
    private val clock: Clock = Clock.systemUTC(),
) : RemoteTufTokenProvider {
    constructor(target: RemoteTufSignerTarget, region: String) :
        this(target, region, DefaultCredentialsProvider.builder().build())

    init {
        require(AWS_REGION_NAME.matches(region)) { "AWS_REGION is invalid" }
    }

    override fun accessToken(endpoint: URI): String {
        if (endpoint.normalize() != target.endpoint.normalize()) {
            throw RemoteTufSigningException("Remote signer authentication endpoint does not match its pinned target")
        }
        return try {
            val identity: AwsCredentialsIdentity = credentials.resolveCredentials()
            val signed = AwsV4HttpSigner.create().sign { request ->
                request.identity(identity)
                    .request(AwsStsCallerToken.stsRequest(region, target.audience))
                    .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, "sts")
                    .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                    .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
                    .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, AwsStsCallerToken.MAXIMUM_EXPIRY)
                    .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                    .putProperty(HttpSigner.SIGNING_CLOCK, clock)
            }
            AwsStsCallerToken.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signed.request().getUri().toASCIIString().toByteArray(Charsets.US_ASCII))
        } catch (failure: RemoteTufSigningException) {
            throw failure
        } catch (failure: Exception) {
            throw RemoteTufSigningException("Remote signer STS caller token creation failed", failure)
        }
    }
}

data class AwsStsCallerIdentityResponse(val statusCode: Int, val body: ByteArray)

fun interface AwsStsCallerIdentityTransport {
    fun get(uri: URI, audienceHeader: String, timeout: Duration): AwsStsCallerIdentityResponse
}

internal class JdkAwsStsCallerIdentityTransport : AwsStsCallerIdentityTransport {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun get(uri: URI, audienceHeader: String, timeout: Duration): AwsStsCallerIdentityResponse {
        val response = client.send(
            HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header(AwsStsCallerToken.AUDIENCE_HEADER, audienceHeader)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        val bytes = response.body().use { it.readNBytes(MAXIMUM_RESPONSE_BYTES + 1) }
        check(bytes.size <= MAXIMUM_RESPONSE_BYTES) { "STS response exceeds its size bound" }
        return AwsStsCallerIdentityResponse(response.statusCode(), bytes)
    }

    private companion object {
        const val MAXIMUM_RESPONSE_BYTES = 64 * 1024
    }
}

/**
 * Signer-side verifier. The verified identity is the caller's IAM role ARN
 * (`arn:<partition>:iam::<account>:role/<name>`), normalized from the STS
 * assumed-role ARN; callers configured in `REGISTRY_TUF_SIGNER_CALLER_BINDINGS`
 * must therefore be path-less role ARNs.
 */
class AwsStsTufSignerCallerTokenVerifier internal constructor(
    private val region: String,
    private val transport: AwsStsCallerIdentityTransport,
    private val timeout: Duration,
    private val clock: Clock = Clock.systemUTC(),
) : TufSignerCallerTokenVerifier {
    constructor(region: String, timeout: Duration) : this(region, JdkAwsStsCallerIdentityTransport(), timeout)

    private val host = AwsStsCallerToken.stsHost(region)

    override fun verify(token: String, audience: String): VerifiedTufSignerCaller {
        val uri = parseToken(token)
        val response = try {
            transport.get(uri, audience, timeout)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TufSignerCallerVerificationUnavailableException("STS caller verification was interrupted", failure)
        } catch (failure: Exception) {
            throw TufSignerCallerVerificationUnavailableException(
                "TUF signer caller identity verification is temporarily unavailable",
                failure,
            )
        }
        when {
            response.statusCode == 200 -> Unit
            response.statusCode in 400..499 -> rejectSigning("TUF signer caller identity token is invalid")
            else -> throw TufSignerCallerVerificationUnavailableException(
                "TUF signer caller identity verification is temporarily unavailable",
                IllegalStateException("STS returned HTTP ${response.statusCode}"),
            )
        }
        val arn = parseCallerArn(response.body)
        return VerifiedTufSignerCaller(roleArnFromAssumedRole(arn))
    }

    private fun parseToken(token: String): URI {
        if (!token.startsWith(AwsStsCallerToken.TOKEN_PREFIX) || token.length > MAXIMUM_TOKEN_CHARS) {
            rejectSigning("TUF signer caller identity token is invalid")
        }
        val decoded = runCatching {
            String(Base64.getUrlDecoder().decode(token.removePrefix(AwsStsCallerToken.TOKEN_PREFIX)), Charsets.US_ASCII)
        }.getOrElse { rejectSigning("TUF signer caller identity token is invalid") }
        val uri = runCatching { URI(decoded) }.getOrElse { rejectSigning("TUF signer caller identity token is invalid") }
        if (
            uri.scheme != "https" || uri.host != host || uri.port != -1 || uri.rawUserInfo != null ||
            uri.rawFragment != null || (uri.rawPath != "/" && uri.rawPath != "") || uri.rawQuery == null
        ) rejectSigning("TUF signer caller token does not target the pinned STS endpoint")

        val parameters = uri.rawQuery.split('&').map { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) rejectSigning("TUF signer caller token query is invalid")
            decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
        }
        val names = parameters.map { it.first }
        if (names.toSet().size != names.size) rejectSigning("TUF signer caller token repeats a query parameter")
        val values = parameters.toMap()
        if (!(names.toSet() - OPTIONAL_PARAMETERS).containsAll(REQUIRED_PARAMETERS) ||
            !REQUIRED_PARAMETERS.containsAll(names.toSet() - OPTIONAL_PARAMETERS)
        ) rejectSigning("TUF signer caller token has unexpected query parameters")
        if (values["Action"] != "GetCallerIdentity" || values["Version"] != "2011-06-15") {
            rejectSigning("TUF signer caller token is not a GetCallerIdentity request")
        }
        if (values["X-Amz-Algorithm"] != "AWS4-HMAC-SHA256") rejectSigning("TUF signer caller token algorithm is invalid")
        val credentialScope = values.getValue("X-Amz-Credential").split('/')
        if (credentialScope.size != 5 || credentialScope[2] != region || credentialScope[3] != "sts" ||
            credentialScope[4] != "aws4_request"
        ) rejectSigning("TUF signer caller token credential scope is invalid")
        val signedHeaders = values.getValue("X-Amz-SignedHeaders").split(';').toSet()
        if (signedHeaders != setOf("host", AwsStsCallerToken.AUDIENCE_HEADER)) {
            rejectSigning("TUF signer caller token must sign exactly host and the signer audience")
        }
        val expires = values.getValue("X-Amz-Expires").toLongOrNull()
        if (expires == null || expires !in 1..AwsStsCallerToken.MAXIMUM_EXPIRY.seconds) {
            rejectSigning("TUF signer caller token lifetime is invalid")
        }
        val signedAt = runCatching { Instant.from(AMZ_DATE.parse(values.getValue("X-Amz-Date"))) }
            .getOrElse { rejectSigning("TUF signer caller token date is invalid") }
        val now = clock.instant()
        if (signedAt.isAfter(now.plus(AwsStsCallerToken.MAXIMUM_CLOCK_SKEW)) ||
            !signedAt.plusSeconds(expires).plus(AwsStsCallerToken.MAXIMUM_CLOCK_SKEW).isAfter(now)
        ) rejectSigning("TUF signer caller token is expired")
        return uri
    }

    // StAX keeps the parse streaming and DTD-free; every element is located by
    // its exact path so duplicated or misplaced nodes fail closed.
    private fun parseCallerArn(body: ByteArray): String {
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
        }
        val path = ArrayDeque<String>()
        var root: String? = null
        var results = 0
        var arns = 0
        val arn = StringBuilder()
        runCatching {
            val reader = factory.createXMLStreamReader(body.inputStream())
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.DTD -> throw IllegalStateException("DTD is not allowed")
                        XMLStreamConstants.START_ELEMENT -> {
                            if (root == null) root = reader.localName
                            path.addLast(reader.localName)
                            when (path.toList()) {
                                RESULT_PATH -> results++
                                ARN_PATH -> arns++
                            }
                        }
                        XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                            if (path.toList() == ARN_PATH) arn.append(reader.text)
                        XMLStreamConstants.END_ELEMENT -> path.removeLast()
                    }
                }
            } finally {
                reader.close()
            }
        }.getOrElse { failure ->
            throw TufSignerCallerVerificationUnavailableException("STS response is not valid XML", failure)
        }
        if (root != RESPONSE_ELEMENT) {
            throw TufSignerCallerVerificationUnavailableException(
                "STS response is not a GetCallerIdentity response",
                IllegalStateException(root.toString()),
            )
        }
        requireSingle(results, "GetCallerIdentityResult")
        requireSingle(arns, "Arn")
        return arn.toString().trim()
    }

    private fun requireSingle(count: Int, name: String) {
        if (count != 1) {
            throw TufSignerCallerVerificationUnavailableException(
                "STS response is missing $name",
                IllegalStateException(name),
            )
        }
    }

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }
        .getOrElse { rejectSigning("TUF signer caller token query is invalid") }

    private companion object {
        const val MAXIMUM_TOKEN_CHARS = 8 * 1024
        val REQUIRED_PARAMETERS = setOf(
            "Action",
            "Version",
            "X-Amz-Algorithm",
            "X-Amz-Credential",
            "X-Amz-Date",
            "X-Amz-Expires",
            "X-Amz-SignedHeaders",
            "X-Amz-Signature",
        )
        val OPTIONAL_PARAMETERS = setOf("X-Amz-Security-Token")
        val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        const val RESPONSE_ELEMENT = "GetCallerIdentityResponse"
        val RESULT_PATH = listOf(RESPONSE_ELEMENT, "GetCallerIdentityResult")
        val ARN_PATH = RESULT_PATH + "Arn"
    }
}

/** `arn:aws:sts::123456789012:assumed-role/name/session` to `arn:aws:iam::123456789012:role/name`. */
internal fun roleArnFromAssumedRole(arn: String): String {
    val match = ASSUMED_ROLE_ARN.matchEntire(arn)
        ?: rejectSigning("TUF signer caller is not an assumed IAM role")
    val (partition, account, role) = match.destructured
    return "arn:$partition:iam::$account:role/$role"
}

internal val AWS_ROLE_ARN = Regex("^arn:aws(?:-[a-z]+)*:iam::[0-9]{12}:role/[A-Za-z0-9+.@_-]{1,64}$")
private val ASSUMED_ROLE_ARN =
    Regex("^arn:(aws(?:-[a-z]+)*):sts::([0-9]{12}):assumed-role/([A-Za-z0-9+.@_-]{1,64})/[A-Za-z0-9+=,.@_-]{2,64}$")
