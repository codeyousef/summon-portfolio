package code.yousef

import code.yousef.config.loadAppConfig
import code.yousef.firestore.FirestoreProvider
import code.yousef.firestore.PortfolioMetaRepository
import code.yousef.firestore.PortfolioMetaService
import code.yousef.portfolio.admin.auth.AdminSession
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

fun main(args: Array<String>) {
    System.setProperty("ktor.deployment.host", "0.0.0.0")
    System.getenv("PORT")?.let { System.setProperty("ktor.deployment.port", it) }
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val appConfig = loadAppConfig()
    log.info(
        "Starting Summon Portfolio with projectId={}, emulator={}, port={}",
        appConfig.projectId,
        appConfig.emulatorHost ?: "OFF",
        appConfig.port
    )

    val firestore = FirestoreProvider.create(appConfig)
    val portfolioMetaService = PortfolioMetaService(PortfolioMetaRepository(firestore))

    environment.monitor.subscribe(ApplicationStopped) {
        runCatching { firestore.close() }
            .onFailure { throwable -> log.warn("Failed to close Firestore", throwable) }
    }

    install(DefaultHeaders)
    install(CallLogging)
    install(AutoHeadResponse)
    install(Compression) {
        gzip()
        deflate()
    }
    install(CachingHeaders)
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        })
    }
    install(Sessions) {
        cookie<AdminSession>("admin_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 60L * 60L * 8L
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val errorId = UUID.randomUUID().toString()
            val message = cause.message ?: "We hit a snag while rendering this page."
            this@module.log.error("Unhandled server error id=$errorId", cause)
            if (call.prefersHtml()) {
                call.respondText(
                    buildPrettyErrorPage(
                        status = HttpStatusCode.InternalServerError,
                        headline = "We hit a snag",
                        message = message,
                        errorId = errorId
                    ),
                    ContentType.Text.Html,
                    status = HttpStatusCode.InternalServerError
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        ok = false,
                        message = message,
                        errorId = errorId
                    )
                )
            }
        }
    }
    configureRouting(appConfig, portfolioMetaService)
}

@Serializable
private data class ErrorResponse(
    val ok: Boolean,
    val message: String,
    val errorId: String
)

private fun ApplicationCall.prefersHtml(): Boolean {
    val acceptHeader = request.headers[HttpHeaders.Accept]?.lowercase()?.trim()
    if (acceptHeader.isNullOrBlank()) return true
    return acceptHeader.contains("text/html") || acceptHeader.contains("*/*")
}

private fun buildPrettyErrorPage(
    status: HttpStatusCode,
    headline: String,
    message: String,
    errorId: String?
): String {
    val safeHeadline = headline.htmlEscape()
    val safeMessage = message.htmlEscape()
    val referenceBlock = errorId?.let {
        """
        <div class="error-ref">
          Reference: <code>${it.htmlEscape()}</code>
        </div>
        """.trimIndent()
    }.orEmpty()
    val supportHref = "mailto:hello@yousef.codes?subject=Summon%20support%20${errorId ?: status.value}"
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>${status.value} · Summon</title>
          <style>
            :root {
              color-scheme: dark;
              font-family: "Inter", "SF Pro Display", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              background: radial-gradient(circle at top, #1b1c2d, #0b0c12 55%);
              color: #f3f4ff;
            }
            body {
              margin: 0;
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              padding: 32px 16px;
              background-image:
                radial-gradient(circle at 20% 20%, rgba(255,59,106,0.35), transparent 45%),
                radial-gradient(circle at 80% 0%, rgba(62,130,255,0.25), transparent 55%);
            }
            .error-card {
              max-width: 520px;
              width: 100%;
              border-radius: 32px;
              padding: 40px;
              background: rgba(11, 12, 19, 0.85);
              border: 1px solid rgba(255,255,255,0.08);
              box-shadow: 0 30px 80px rgba(10,12,18,0.65);
            }
            .eyebrow {
              letter-spacing: 0.3em;
              font-size: 0.75rem;
              text-transform: uppercase;
              color: rgba(243,244,255,0.6);
              margin-bottom: 12px;
            }
            h1 {
              margin: 0 0 16px;
              font-size: clamp(2rem, 4vw, 2.8rem);
              line-height: 1.1;
            }
            p {
              margin: 0 0 20px;
              color: rgba(243,244,255,0.82);
              line-height: 1.6;
            }
            .error-ref {
              font-size: 0.85rem;
              color: rgba(243,244,255,0.6);
              margin-bottom: 24px;
            }
            code {
              background: rgba(255,255,255,0.08);
              padding: 2px 8px;
              border-radius: 999px;
              font-size: 0.78rem;
            }
            .actions {
              display: flex;
              flex-wrap: wrap;
              gap: 12px;
            }
            .btn {
              padding: 12px 20px;
              border-radius: 999px;
              text-decoration: none;
              font-weight: 600;
              transition: transform 140ms ease, box-shadow 140ms ease;
            }
            .btn-primary {
              background: linear-gradient(120deg,#ff5b8d,#ff784c);
              color: #0b0c12;
              box-shadow: 0 10px 30px rgba(255,91,141,0.35);
            }
            .btn-primary:hover {
              transform: translateY(-1px);
              box-shadow: 0 18px 40px rgba(255,91,141,0.45);
            }
            .btn-ghost {
              border: 1px solid rgba(255,255,255,0.25);
              color: rgba(243,244,255,0.9);
            }
            .btn-ghost:hover {
              border-color: rgba(255,255,255,0.4);
            }
          </style>
        </head>
        <body>
          <main class="error-card">
            <p class="eyebrow">Error ${status.value}</p>
            <h1>$safeHeadline</h1>
            <p>$safeMessage</p>
            $referenceBlock
            <div class="actions">
              <a class="btn btn-primary" href="/">Back to safety</a>
              <a class="btn btn-ghost" href="$supportHref">Contact support</a>
            </div>
          </main>
        </body>
        </html>
    """.trimIndent()
}

private fun String.htmlEscape(): String =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
