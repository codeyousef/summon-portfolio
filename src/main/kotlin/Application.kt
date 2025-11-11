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
import kotlinx.serialization.json.Json

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
            this@module.log.error("Unhandled server error", cause)
            call.respondText("Something went wrong", status = HttpStatusCode.InternalServerError)
        }
    }
    configureRouting(appConfig, portfolioMetaService)
}
