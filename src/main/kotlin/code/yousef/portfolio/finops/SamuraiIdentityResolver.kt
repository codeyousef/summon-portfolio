package code.yousef.portfolio.finops

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val MAX_IDENTITY_RESPONSE_BYTES = 262_144
private val SAFE_SAMURAI_USER_ID = Regex("^[A-Za-z0-9][A-Za-z0-9@._:+-]{0,199}$")

@Serializable
data class SamuraiFinOpsIdentity(
    val userId: String,
    val displayName: String? = null,
    val email: String? = null,
) {
    init {
        require(userId.matches(SAFE_SAMURAI_USER_ID)) { "invalid Samurai identity user ID" }
        listOfNotNull(displayName, email).forEach { value ->
            require(value.length <= 320 && value.none(Char::isISOControl)) { "invalid Samurai identity field" }
        }
    }
}

fun interface SamuraiIdentityResolver {
    fun resolve(userIds: Set<String>): Map<String, SamuraiFinOpsIdentity>
}

object NoopSamuraiIdentityResolver : SamuraiIdentityResolver {
    override fun resolve(userIds: Set<String>): Map<String, SamuraiFinOpsIdentity> = emptyMap()
}

class HttpSamuraiIdentityResolver(
    endpoint: String,
    private val bearerToken: String,
    private val transport: SamuraiIdentityProjectionTransport = JdkSamuraiIdentityProjectionTransport(),
) : SamuraiIdentityResolver {
    private val endpoint = validateEndpoint(endpoint)
    private val json = Json { ignoreUnknownKeys = false }

    init {
        require(bearerToken.length in 32..512 && bearerToken.none(Char::isISOControl)) {
            "FINOPS_IDENTITY_READ_TOKEN must contain 32 to 512 non-control characters"
        }
    }

    override fun resolve(userIds: Set<String>): Map<String, SamuraiFinOpsIdentity> {
        val safeIds = userIds.filter(SAFE_SAMURAI_USER_ID::matches).distinct().sorted()
        if (safeIds.isEmpty()) return emptyMap()
        return safeIds.chunked(100).flatMap { batch ->
            val query = URLEncoder.encode(batch.joinToString(","), StandardCharsets.UTF_8)
            val response = transport.get(URI.create("$endpoint?ids=$query"), bearerToken)
            require(response.size <= MAX_IDENTITY_RESPONSE_BYTES) { "Samurai identity response is too large" }
            json.decodeFromString<List<SamuraiFinOpsIdentity>>(response.decodeToString()).also { identities ->
                val requested = batch.toSet()
                require(identities.size <= batch.size && identities.map { it.userId }.toSet().size == identities.size) {
                    "Samurai identity response contains duplicates or too many records"
                }
                require(identities.all { it.userId in requested }) {
                    "Samurai identity response contains an unrequested principal"
                }
            }
        }.associateBy(SamuraiFinOpsIdentity::userId)
    }

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        private fun validateEndpoint(raw: String): String {
            val uri = URI(raw.trim())
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
                "SAMURAI_FINOPS_IDENTITY_URL must use HTTPS outside local tests"
            }
            require(
                uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                    uri.path == "/internal/portfolio/finops/identities"
            ) { "SAMURAI_FINOPS_IDENTITY_URL must be the exact identity projection endpoint" }
            return uri.toString()
        }
    }
}

fun interface SamuraiIdentityProjectionTransport {
    fun get(uri: URI, bearerToken: String): ByteArray
}

private class JdkSamuraiIdentityProjectionTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : SamuraiIdentityProjectionTransport {
    override fun get(uri: URI, bearerToken: String): ByteArray {
        val response = client.send(
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("authorization", "Bearer $bearerToken")
                .header("accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { body ->
            require(response.statusCode() == 200) { "Samurai identity projection returned HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(MAX_IDENTITY_RESPONSE_BYTES + 1)
            require(bytes.size <= MAX_IDENTITY_RESPONSE_BYTES && body.read() == -1) {
                "Samurai identity response is too large"
            }
            require(response.headers().firstValue("content-type").orElse("").substringBefore(';').trim() == "application/json") {
                "Samurai identity response has an invalid content type"
            }
            return bytes
        }
    }
}
