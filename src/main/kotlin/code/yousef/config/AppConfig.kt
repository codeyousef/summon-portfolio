package code.yousef.config

import io.ktor.server.application.Application

data class AppConfig(
    val projectId: String,
    val emulatorHost: String?,
    val port: Int
)

fun Application.loadAppConfig(): AppConfig {
    val env = System.getenv()
    val cfg = environment.config
    val projectId = env["GOOGLE_CLOUD_PROJECT"]
        ?: cfg.propertyOrNull("gcp.projectId")?.getString()
        ?: error("Missing GOOGLE_CLOUD_PROJECT and gcp.projectId fallback")
    val emulatorHost = env["FIRESTORE_EMULATOR_HOST"]
        ?: cfg.propertyOrNull("firestore.emulatorHost")?.getString()
    val port = env["PORT"]?.toIntOrNull()
        ?: cfg.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull()
        ?: 8080
    return AppConfig(
        projectId = projectId,
        emulatorHost = emulatorHost,
        port = port
    )
}
