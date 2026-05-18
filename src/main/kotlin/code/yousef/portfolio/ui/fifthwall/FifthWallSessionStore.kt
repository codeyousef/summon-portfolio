package code.yousef.portfolio.ui.fifthwall

import codes.yousef.aether.core.Cookie
import codes.yousef.aether.core.Exchange
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val FifthWallSessionCookieName = "fifth_wall_session"
private const val FifthWallSessionMaxAgeSeconds = 60L * 60L

internal class FifthWallSessionStore {
    private val controllers = ConcurrentHashMap<String, FifthWallController>()

    fun controllerFor(exchange: Exchange): FifthWallController {
        val sessionId = exchange.request.cookies[FifthWallSessionCookieName]
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        exchange.response.setCookie(
            Cookie(
                name = FifthWallSessionCookieName,
                value = sessionId,
                path = "/",
                maxAge = FifthWallSessionMaxAgeSeconds,
                httpOnly = true,
                sameSite = Cookie.SameSite.LAX
            )
        )

        return controllers.computeIfAbsent(sessionId) { FifthWallController() }
    }
}
