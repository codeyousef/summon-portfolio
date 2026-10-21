package code.yousef.portfolio.server

import codes.yousef.aether.core.Exchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * `/health` (liveness) and `/health/ready` (readiness, used by the ALB target group).
 * Both are answered before host routing and sessions, so health checks never create
 * sessions or depend on the Host header. Readiness runs the optional backend probe at most
 * once per [cacheMillis] so frequent health checks do not translate into database load.
 */
class HealthEndpoints(
    private val readinessProbe: (() -> Unit)? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheMillis: Long = 5_000,
) {
    private val log = LoggerFactory.getLogger(HealthEndpoints::class.java)

    @Volatile
    private var lastProbe: Pair<Long, Boolean>? = null

    suspend fun handle(exchange: Exchange, next: suspend () -> Unit) {
        when (exchange.request.path) {
            "/health" -> respond(exchange, 200, """{"status":"ok"}""")
            "/health/ready" -> if (ready()) {
                respond(exchange, 200, """{"status":"ready"}""")
            } else {
                respond(exchange, 503, """{"status":"unavailable"}""")
            }
            else -> next()
        }
    }

    private suspend fun ready(): Boolean {
        val probe = readinessProbe ?: return true
        val now = clock.millis()
        lastProbe?.let { (checkedAt, result) -> if (now - checkedAt < cacheMillis) return result }
        val result = withContext(Dispatchers.IO) {
            runCatching(probe).onFailure { log.warn("Readiness probe failed: {}", it.javaClass.simpleName) }.isSuccess
        }
        lastProbe = now to result
        return result
    }

    private suspend fun respond(exchange: Exchange, status: Int, body: String) {
        exchange.response.setHeader("Cache-Control", "no-store")
        exchange.respondBytes(status, "application/json", body.toByteArray(Charsets.UTF_8))
    }
}
