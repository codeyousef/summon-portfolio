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

        val checkpoint = FifthWallCheckpointCodec.decode(
            exchange.request.cookies[FIFTH_WALL_CHECKPOINT_KEY]?.value
        )
        val soundMode = decodeFifthWallSoundMode(
            exchange.request.cookies[FIFTH_WALL_SOUND_MODE_KEY]?.value
        )
        return controllers.computeIfAbsent(sessionId) {
            FifthWallController(initialCheckpoint = checkpoint, initialSoundMode = soundMode)
        }
    }
}
