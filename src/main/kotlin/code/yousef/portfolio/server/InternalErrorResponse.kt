package code.yousef.portfolio.server

import codes.yousef.aether.core.Exchange
import org.slf4j.Logger
import java.util.UUID

internal fun portfolioDebugErrorsEnabled(
    environment: Map<String, String> = System.getenv(),
): Boolean {
    val requested = environment["PORTFOLIO_DEBUG_ERRORS"]?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
    val runtime = (environment["ENVIRONMENT"] ?: environment["APP_ENV"] ?: "local").trim().lowercase()
    return requested && runtime in setOf("local", "dev", "development", "test")
}

internal fun internalErrorBody(
    errorId: String,
    error: Throwable,
    debug: Boolean,
): String = if (debug) {
    "Internal Server Error\nReference: $errorId\n${error::class.simpleName}: ${error.message.orEmpty()}"
} else {
    "Internal Server Error\nReference: $errorId"
}

suspend fun Exchange.respondInternalServerError(
    logger: Logger,
    context: String,
    error: Throwable,
    debug: Boolean = portfolioDebugErrorsEnabled(),
) {
    val errorId = UUID.randomUUID().toString()
    logger.error("{} failed; errorId={}", context, errorId, error)
    val errorBytes = internalErrorBody(errorId, error, debug).toByteArray(Charsets.UTF_8)
    response.statusCode = 500
    response.setHeader("Content-Type", "text/plain; charset=utf-8")
    response.setHeader("Cache-Control", "no-store")
    response.setHeader("Content-Length", errorBytes.size.toString())
    response.write(errorBytes)
    response.end()
}
