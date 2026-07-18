package code.yousef.portfolio.server

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.HttpMethod
import codes.yousef.aether.core.pipeline.Middleware
import codes.yousef.aether.core.proxy.CircuitBreakerConfig
import codes.yousef.aether.core.proxy.ProxyCircuitOpenException
import codes.yousef.aether.core.proxy.ProxyConfig
import codes.yousef.aether.core.proxy.ProxyException
import codes.yousef.aether.core.proxy.ProxyTimeoutException
import codes.yousef.aether.core.proxy.proxyTo
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.IdTokenCredentials
import com.google.auth.oauth2.IdTokenProvider
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

internal const val REGISTRY_GATEWAY_MAX_REQUEST_BYTES = 26_214_400

private const val REGISTRY_PUBLIC_PREFIX = "/packages"
private const val SERVERLESS_AUTHORIZATION = "X-Serverless-Authorization"
private const val GATEWAY_RETRY_AFTER_SECONDS = 30
private val REGISTRY_STRIPPED_REQUEST_HEADERS = listOf(
    // Keep Host in the case-insensitive removal set. Aether's final
    // preserveHostHeader=false cleanup does not catch a lowercase wire name.
    "Host",
    SERVERLESS_AUTHORIZATION,
    "Cookie",
    "Forwarded",
    "X-Forwarded-For",
    "X-Forwarded-Host",
    "X-Forwarded-Proto",
    "X-Forwarded-Prefix",
    "X-Real-IP"
)

internal fun interface RegistryIdentityTokenProvider {
    fun tokenForAudience(audience: String): String
}

internal class GoogleAdcRegistryIdentityTokenProvider(
    private val credentialsLoader: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() }
) : RegistryIdentityTokenProvider {
    private val credentials by lazy(LazyThreadSafetyMode.SYNCHRONIZED, credentialsLoader)
    private val audienceCredentials = ConcurrentHashMap<String, IdTokenCredentials>()

    override fun tokenForAudience(audience: String): String {
        val idTokenProvider = credentials as? IdTokenProvider
            ?: error("Application default credentials cannot mint identity tokens")
        val audienceCredential = audienceCredentials.computeIfAbsent(audience) {
            IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idTokenProvider)
                .setTargetAudience(audience)
                .build()
        }
        audienceCredential.refreshIfExpired()
        return requireNotNull(audienceCredential.accessToken?.tokenValue) {
            "Application default credentials returned an empty identity token"
        }
    }
}

internal fun interface RegistryProxyForwarder {
    suspend fun forward(exchange: Exchange, upstreamUrl: String, identityToken: String?)
}

private object AetherRegistryProxyForwarder : RegistryProxyForwarder {
    private val proxyConfig = ProxyConfig(
        connectTimeout = 10.seconds,
        requestTimeout = 60.seconds,
        idleTimeout = 30.seconds,
        maxRequestBodySize = REGISTRY_GATEWAY_MAX_REQUEST_BYTES.toLong(),
        circuitBreaker = CircuitBreakerConfig(
            failureThreshold = 3,
            resetTimeout = 30.seconds,
            successThreshold = 1
        ),
        preserveHostHeader = false,
        // The registry does not use caller IP/host headers for authorization.
        // Avoid inheriting or extending any spoofable forwarding chain.
        addForwardedHeaders = false,
        removeRequestHeaders = ProxyConfig.DEFAULT_HOP_BY_HOP_HEADERS + REGISTRY_STRIPPED_REQUEST_HEADERS
    )

    override suspend fun forward(exchange: Exchange, upstreamUrl: String, identityToken: String?) {
        exchange.proxyTo(upstreamUrl, proxyConfig) {
            // Preserve the public contract path, including the /packages prefix.
            rewritePath { it }
            // Keep the caller's Authorization header for registry authorization. Cloud Run
            // invocation credentials use the separate serverless authorization header.
            REGISTRY_STRIPPED_REQUEST_HEADERS.forEach(::removeHeader)
            identityToken?.let { header(SERVERLESS_AUTHORIZATION, "Bearer $it") }
        }
    }
}

internal class RegistryGatewayMiddleware(
    publicHost: String,
    upstreamUrl: String,
    releaseActionsUpstreamUrl: String,
    securityActionsUpstreamUrl: String,
    private val identityTokenProvider: RegistryIdentityTokenProvider = GoogleAdcRegistryIdentityTokenProvider(),
    private val proxyForwarder: RegistryProxyForwarder = AetherRegistryProxyForwarder
) {
    private data class Upstream(val url: String, val audience: String?)

    private val log = LoggerFactory.getLogger(RegistryGatewayMiddleware::class.java)
    private val random = SecureRandom()
    private val publicHost = parsePublicHost(publicHost)
    private val publicUpstream = parseUpstream(upstreamUrl)
    private val releaseActionsUpstream = parseUpstream(releaseActionsUpstreamUrl)
    private val securityActionsUpstream = parseUpstream(securityActionsUpstreamUrl)

    val middleware: Middleware = { exchange, next -> handle(exchange, next) }

    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        if (!isRegistryRequest(exchange)) {
            next()
            return
        }

        try {
            val upstream = selectUpstream(exchange.request.method, exchange.request.path)
            val identityToken = upstream.audience?.let(identityTokenProvider::tokenForAudience)
            proxyForwarder.forward(exchange, upstream.url, identityToken)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ProxyTimeoutException) {
            log.warn("Registry upstream timed out ({})", error::class.simpleName)
            respondGatewayError(exchange, 504)
        } catch (error: ProxyCircuitOpenException) {
            log.warn("Registry upstream is temporarily unavailable ({})", error::class.simpleName)
            respondGatewayError(exchange, 503)
        } catch (error: ProxyException) {
            log.warn("Registry proxy request failed ({})", error::class.simpleName)
            respondGatewayError(exchange, 502)
        } catch (error: Exception) {
            log.warn("Registry gateway request failed ({})", error::class.simpleName)
            respondGatewayError(exchange, 502)
        }
    }

    private fun isRegistryRequest(exchange: Exchange): Boolean {
        val host = exchange.request.headers["Host"]
            ?.trim()
            ?.substringBefore(':')
            ?.lowercase()
        val path = exchange.request.path
        return host == publicHost &&
            (path == REGISTRY_PUBLIC_PREFIX || path.startsWith("$REGISTRY_PUBLIC_PREFIX/"))
    }

    private fun selectUpstream(method: HttpMethod, path: String): Upstream = when {
        method == HttpMethod.POST && RELEASE_ACTION_PATH.matches(path) ->
            releaseActionsUpstream
        method == HttpMethod.POST && SECURITY_ACTION_PATH.matches(path) ->
            securityActionsUpstream
        else -> publicUpstream
    }

    private suspend fun respondGatewayError(exchange: Exchange, status: Int) {
        val requestId = "req_" + ByteArray(18).also(random::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val body = """{"error":{"code":"temporarily_unavailable","message":"The registry is temporarily unavailable","request_id":"$requestId","occurred_at":"${Instant.now()}","retryable":true,"retry_after_seconds":$GATEWAY_RETRY_AFTER_SECONDS,"details":{}}}"""
            .encodeToByteArray()
        exchange.response.statusCode = status
        exchange.response.setHeader("Content-Type", "application/json; charset=utf-8")
        exchange.response.setHeader("Cache-Control", "no-store")
        exchange.response.setHeader("X-Request-Id", requestId)
        exchange.response.setHeader("Retry-After", GATEWAY_RETRY_AFTER_SECONDS.toString())
        exchange.response.setHeader("Content-Length", body.size.toString())
        exchange.response.write(body)
        exchange.response.end()
    }

    private fun parseUpstream(rawUrl: String): Upstream {
        val uri = runCatching { URI.create(rawUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid registry upstream URL", it) }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") {
            "Registry upstream URL must use HTTPS, or HTTP for loopback development"
        }
        require(!uri.rawAuthority.isNullOrBlank() && uri.userInfo == null) {
            "Registry upstream URL must contain a host and must not contain user info"
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Registry upstream URL must not contain a path"
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Registry upstream URL must not contain a query or fragment"
        }

        val isLoopback = uri.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
        require(scheme == "https" || isLoopback) {
            "Plain HTTP registry upstreams are only allowed on loopback hosts"
        }

        val origin = "$scheme://${uri.rawAuthority}"
        return Upstream(
            url = origin,
            audience = if (scheme == "https") origin else null
        )
    }

    private fun parsePublicHost(rawHost: String): String {
        val normalized = rawHost.trim().lowercase()
        require(PUBLIC_HOST.matches(normalized)) {
            "Registry public host must be one exact DNS hostname"
        }
        return normalized
    }

    private companion object {
        val PUBLIC_HOST = Regex(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$"
        )
        val RELEASE_ACTION_PATH = Regex(
            "^/packages/api/v1/packages/[^/]+/[^/]+/releases/[^/]+/actions/(?:yank|unyank)$"
        )
        val SECURITY_ACTION_PATH = Regex(
            "^/packages/api/v1/packages/[^/]+/[^/]+/releases/[^/]+/actions/(?:security-quarantine|security-reinstate)$"
        )
    }
}
