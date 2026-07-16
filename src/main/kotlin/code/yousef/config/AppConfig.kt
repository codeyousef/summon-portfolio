package code.yousef.config

import java.nio.file.Path

data class AppConfig(
    val projectId: String,
    val emulatorHost: String?,
    val port: Int,
    val useLocalStore: Boolean = false,
    val photographyUploadBucket: String? = null,
    val photographyUploadPrefix: String = "photography",
    val photographyUploadDir: Path = Path.of("storage/uploads/photography"),
    val photographyMaxUploadBytes: Long = 15_728_640,
    val registryUpstreamUrl: String? = null
)

fun loadAppConfig(): AppConfig {
    val env = System.getenv()
    
    val useLocalStore = env["USE_LOCAL_STORE"]?.toBoolean() ?: false
    
    val projectId = env["GOOGLE_CLOUD_PROJECT"]
        ?: if (useLocalStore) "local-dev" else error("Missing GOOGLE_CLOUD_PROJECT environment variable")
        
    val emulatorHost = env["FIRESTORE_EMULATOR_HOST"]
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    val uploadBucket = env["PHOTOGRAPHY_UPLOAD_BUCKET"]?.trim()?.takeIf { it.isNotEmpty() }
    val uploadPrefix = env["PHOTOGRAPHY_UPLOAD_PREFIX"]?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: "photography"
    val uploadDir = Path.of(env["PHOTOGRAPHY_UPLOAD_DIR"] ?: "storage/uploads/photography")
    val maxUploadBytes = env["PHOTOGRAPHY_MAX_UPLOAD_BYTES"]?.toLongOrNull() ?: 15_728_640
    val registryUpstreamUrl = env["SEEN_REGISTRY_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    
    return AppConfig(
        projectId = projectId,
        emulatorHost = emulatorHost,
        port = port,
        useLocalStore = useLocalStore,
        photographyUploadBucket = uploadBucket,
        photographyUploadPrefix = uploadPrefix,
        photographyUploadDir = uploadDir,
        photographyMaxUploadBytes = maxUploadBytes,
        registryUpstreamUrl = registryUpstreamUrl
    )
}
