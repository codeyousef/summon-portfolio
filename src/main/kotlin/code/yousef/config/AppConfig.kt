package code.yousef.config

data class AppConfig(
    val projectId: String,
    val emulatorHost: String?,
    val port: Int,
    val useLocalStore: Boolean = false
)

fun loadAppConfig(): AppConfig {
    val env = System.getenv()
    
    val useLocalStore = env["USE_LOCAL_STORE"]?.toBoolean() ?: false
    
    val projectId = env["GOOGLE_CLOUD_PROJECT"]
        ?: if (useLocalStore) "local-dev" else error("Missing GOOGLE_CLOUD_PROJECT environment variable")
        
    val emulatorHost = env["FIRESTORE_EMULATOR_HOST"]
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    
    return AppConfig(
        projectId = projectId,
        emulatorHost = emulatorHost,
        port = port,
        useLocalStore = useLocalStore
    )
}
