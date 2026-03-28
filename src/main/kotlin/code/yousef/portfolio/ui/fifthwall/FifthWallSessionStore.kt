package code.yousef.portfolio.ui.fifthwall

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.session.session
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val FifthWallSessionKey = "fifth_wall_session_id"

internal class FifthWallSessionStore {
    private val controllers = ConcurrentHashMap<String, FifthWallController>()

    suspend fun controllerFor(exchange: Exchange): FifthWallController {
        val session = exchange.session() ?: throw IllegalStateException("Session middleware not installed")
        val sessionId = (session.get(FifthWallSessionKey) as? String)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { session.set(FifthWallSessionKey, it) }

        return controllers.computeIfAbsent(sessionId) { FifthWallController() }
    }
}
