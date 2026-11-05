package code.yousef.portfolio.session

import codes.yousef.aether.core.session.DefaultSession
import codes.yousef.aether.core.session.InMemorySessionStore
import codes.yousef.aether.core.session.SerializableSession
import codes.yousef.aether.core.session.Session
import codes.yousef.aether.core.session.SessionConfig
import codes.yousef.aether.core.session.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.util.Collections
import java.util.WeakHashMap

private const val SESSION_PAYLOAD_KEY = "aether-session-v1"
private const val PORTFOLIO_SESSION_COOKIE = "admin_session"
private const val PORTFOLIO_SESSION_ENTROPY_BYTES = 24

/**
 * Aether interprets sessionIdLength as random-byte count before Base64URL
 * encoding. Twenty-four bytes therefore produce the edge contract's exact
 * 32-character identifier while retaining 192 bits of entropy.
 */
internal fun portfolioSessionConfig(secureCookie: Boolean = false): SessionConfig = SessionConfig(
    cookieName = PORTFOLIO_SESSION_COOKIE,
    secure = secureCookie,
    sessionIdLength = PORTFOLIO_SESSION_ENTROPY_BYTES,
)
private const val MAX_CAS_ATTEMPTS = 4

@Serializable
internal data class EdgeSessionRead(
    val exists: Boolean,
    val revision: Long,
    val expiresAt: Long,
    val values: Map<String, String>,
)

@Serializable
internal data class EdgeSessionWrite(
    val expectedRevision: Long,
    val values: Map<String, String>,
)

@Serializable
internal data class EdgeSessionCommitted(
    val revision: Long,
    val expiresAt: Long,
    val values: Map<String, String>,
)

internal sealed interface EdgeSessionWriteResult {
    data class Committed(val revision: Long) : EdgeSessionWriteResult
    data class Conflict(val currentRevision: Long) : EdgeSessionWriteResult
}

internal interface EdgeSessionTransport : AutoCloseable {
    suspend fun read(sessionId: String): EdgeSessionRead
    suspend fun compareAndSet(sessionId: String, update: EdgeSessionWrite): EdgeSessionWriteResult
    suspend fun delete(sessionId: String)

    override fun close() = Unit
}

internal class HttpEdgeSessionTransport(
    endpoint: String,
    private val bearerToken: String,
    private val client: HttpClient = defaultClient(),
    private val json: Json = sessionJson,
) : EdgeSessionTransport {
    private val baseUrl = validateEndpoint(endpoint)

    init {
        require(bearerToken.length >= 32) { "EDGE_ORIGIN_TOKEN must contain at least 32 characters" }
    }

    override suspend fun read(sessionId: String): EdgeSessionRead {
        val response = client.post("$baseUrl/v1/session/read") {
            authorize(sessionId)
        }
        require(response.status == HttpStatusCode.OK) {
            "Portfolio edge session read failed with HTTP ${response.status.value}"
        }
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun compareAndSet(
        sessionId: String,
        update: EdgeSessionWrite,
    ): EdgeSessionWriteResult {
        val response = client.post("$baseUrl/v1/session/compare-and-set") {
            authorize(sessionId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(update))
        }
        return when (response.status) {
            HttpStatusCode.OK -> EdgeSessionWriteResult.Committed(
                json.decodeFromString<EdgeSessionCommitted>(response.bodyAsText()).revision,
            )
            HttpStatusCode.Conflict -> EdgeSessionWriteResult.Conflict(
                json.decodeFromString<RevisionConflict>(response.bodyAsText()).currentRevision,
            )
            else -> error("Portfolio edge session write failed with HTTP ${response.status.value}")
        }
    }

    override suspend fun delete(sessionId: String) {
        val response = client.delete("$baseUrl/v1/session") {
            authorize(sessionId)
        }
        require(response.status == HttpStatusCode.NoContent) {
            "Portfolio edge session delete failed with HTTP ${response.status.value}"
        }
    }

    override fun close() = client.close()

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(sessionId: String) {
        require(sessionId.matches(SESSION_ID_PATTERN)) { "Invalid portfolio session id" }
        header("authorization", "Bearer $bearerToken")
        header("x-portfolio-session-id", sessionId)
        header("accept", ContentType.Application.Json.toString())
    }

    @Serializable
    private data class RevisionConflict(val currentRevision: Long)

    companion object {
        private val SESSION_ID_PATTERN = Regex("^[A-Za-z0-9_-]{32}$")

        private fun validateEndpoint(raw: String): String {
            val uri = URI(raw)
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
                "PORTFOLIO_EDGE_SESSION_BASE_URL must use HTTPS outside local tests"
            }
            require(uri.host != null && uri.rawQuery == null && uri.rawFragment == null) {
                "PORTFOLIO_EDGE_SESSION_BASE_URL must be an absolute URL without query or fragment"
            }
            return raw.trimEnd('/')
        }

        private fun defaultClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 2_000
                requestTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
            expectSuccess = false
        }

        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}

internal class CloudflareDurableObjectSessionStore(
    private val transport: EdgeSessionTransport,
    private val json: Json = sessionJson,
) : SessionStore, AutoCloseable {
    private data class LoadedState(
        val revision: Long,
        val server: SerializableSession,
        val local: SerializableSession,
    )

    private val loaded = Collections.synchronizedMap(WeakHashMap<Session, LoadedState>())

    override suspend fun get(sessionId: String): Session? {
        val remote = transport.read(sessionId)
        if (!remote.exists) return null
        val serialized = decodeSession(remote, sessionId)
        val session = DefaultSession.fromSerializable(serialized)
        loaded[session] = LoadedState(remote.revision, serialized, serialized)
        return session
    }

    override suspend fun save(session: Session) {
        if (session.isInvalidated) {
            delete(session.id)
            return
        }
        val requested = (session as? DefaultSession)?.toSerializable()
            ?: error("Cloudflare session storage requires Aether DefaultSession values")
        var state = loaded[session] ?: LoadedState(
            revision = 0,
            server = requested.copy(data = emptyMap()),
            local = requested.copy(data = emptyMap()),
        )
        val delta = sessionDelta(state.local.data, requested.data)

        repeat(MAX_CAS_ATTEMPTS) { attempt ->
            val candidate = mergeSession(state.server, requested, delta)
            val update = EdgeSessionWrite(
                expectedRevision = state.revision,
                values = mapOf(SESSION_PAYLOAD_KEY to json.encodeToString(SerializableSession.serializer(), candidate)),
            )
            when (val result = transport.compareAndSet(session.id, update)) {
                is EdgeSessionWriteResult.Committed -> {
                    loaded[session] = LoadedState(result.revision, candidate, requested)
                    return
                }
                is EdgeSessionWriteResult.Conflict -> {
                    if (attempt == MAX_CAS_ATTEMPTS - 1) {
                        error("Portfolio session remained conflicted after $MAX_CAS_ATTEMPTS attempts")
                    }
                    val latest = transport.read(session.id)
                    require(latest.revision == result.currentRevision) {
                        "Portfolio session revision changed while resolving a conflict"
                    }
                    val latestSession = if (latest.exists) decodeSession(latest, session.id) else requested.copy(data = emptyMap())
                    state = LoadedState(latest.revision, latestSession, state.local)
                }
            }
        }
    }

    override suspend fun delete(sessionId: String) = transport.delete(sessionId)

    override suspend fun exists(sessionId: String): Boolean = transport.read(sessionId).exists

    // Per-session Durable Objects expire themselves with alarms. The namespace
    // intentionally has no enumerable global session index.
    override suspend fun cleanup() = Unit

    override suspend fun count(): Long = 0

    override fun close() = transport.close()

    private fun decodeSession(remote: EdgeSessionRead, expectedId: String): SerializableSession {
        val payload = remote.values[SESSION_PAYLOAD_KEY]
            ?: error("Portfolio edge session exists without its Aether payload")
        val session = json.decodeFromString(SerializableSession.serializer(), payload)
        require(session.id == expectedId) { "Portfolio edge returned a mismatched session id" }
        return session
    }
}

internal data class SessionDelta(
    val removals: Set<String>,
    val updates: Map<String, JsonElement>,
)

internal fun sessionDelta(
    original: Map<String, JsonElement>,
    requested: Map<String, JsonElement>,
): SessionDelta = SessionDelta(
    removals = original.keys - requested.keys,
    updates = requested.filter { (key, value) -> original[key] != value },
)

internal fun mergeSession(
    server: SerializableSession,
    requested: SerializableSession,
    delta: SessionDelta,
): SerializableSession {
    val data = server.data.toMutableMap()
    delta.removals.forEach(data::remove)
    data.putAll(delta.updates)
    return requested.copy(
        createdAt = minOf(server.createdAt, requested.createdAt),
        lastAccessedAt = maxOf(server.lastAccessedAt, requested.lastAccessedAt),
        data = data,
    )
}

internal data class PortfolioSessionStoreResources(
    val store: SessionStore,
    val close: () -> Unit,
    /** True when the application itself issues the browser cookie over HTTPS (AWS). */
    val secureCookie: Boolean = false,
)

/** `PORTFOLIO_SESSION_BACKEND`: where Aether sessions are stored. */
internal enum class PortfolioSessionBackend(val environmentValue: String) {
    MEMORY("memory"),
    EDGE("edge"),
    DYNAMODB("dynamodb"),
}

internal object PortfolioSessionStoreFactory {
    fun fromEnvironment(
        environment: Map<String, String> = System.getenv(),
        dynamoDbClient: () -> software.amazon.awssdk.services.dynamodb.DynamoDbClient = {
            error("A DynamoDB client is required for DynamoDB sessions")
        },
    ): PortfolioSessionStoreResources {
        val edgeEnabled = environment["PORTFOLIO_EDGE_SESSIONS_ENABLED"]?.let { value ->
            requireNotNull(value.toBooleanStrictOrNull()) { "PORTFOLIO_EDGE_SESSIONS_ENABLED must be 'true' or 'false'" }
        } ?: false
        val backend = when (val raw = environment["PORTFOLIO_SESSION_BACKEND"]?.trim()?.lowercase()?.takeIf(String::isNotEmpty)) {
            null -> if (edgeEnabled) PortfolioSessionBackend.EDGE else PortfolioSessionBackend.MEMORY
            else -> PortfolioSessionBackend.entries.firstOrNull { it.environmentValue == raw }
                ?: error("PORTFOLIO_SESSION_BACKEND must be memory, edge or dynamodb")
        }
        require(!edgeEnabled || backend == PortfolioSessionBackend.EDGE) {
            "PORTFOLIO_EDGE_SESSIONS_ENABLED=true conflicts with PORTFOLIO_SESSION_BACKEND=${backend.environmentValue}"
        }
        val secureCookie = environment["PORTFOLIO_SESSION_COOKIE_SECURE"]?.let { value ->
            requireNotNull(value.toBooleanStrictOrNull()) { "PORTFOLIO_SESSION_COOKIE_SECURE must be 'true' or 'false'" }
        } ?: (backend == PortfolioSessionBackend.DYNAMODB)
        return when (backend) {
            PortfolioSessionBackend.MEMORY -> PortfolioSessionStoreResources(InMemorySessionStore(), close = {}, secureCookie)
            PortfolioSessionBackend.EDGE -> {
                val endpoint = environment["PORTFOLIO_EDGE_SESSION_BASE_URL"]
                    ?: error("PORTFOLIO_EDGE_SESSION_BASE_URL is required when edge sessions are enabled")
                val token = environment["EDGE_ORIGIN_TOKEN"]
                    ?: error("EDGE_ORIGIN_TOKEN is required when edge sessions are enabled")
                val store = CloudflareDurableObjectSessionStore(HttpEdgeSessionTransport(endpoint, token))
                PortfolioSessionStoreResources(store, store::close, secureCookie)
            }
            PortfolioSessionBackend.DYNAMODB -> {
                val table = environment["PORTFOLIO_DYNAMODB_SESSIONS_TABLE"]?.trim()?.takeIf(String::isNotEmpty)
                    ?: error("PORTFOLIO_SESSION_BACKEND=dynamodb requires PORTFOLIO_DYNAMODB_SESSIONS_TABLE")
                // The revision/merge protocol is identical to the Durable Object store;
                // only the transport differs. The shared DynamoDB client is closed by its owner.
                val store = CloudflareDurableObjectSessionStore(DynamoDbSessionTransport(dynamoDbClient(), table))
                PortfolioSessionStoreResources(store, close = {}, secureCookie)
            }
        }
    }
}

private val sessionJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}
