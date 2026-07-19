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
    val registryPublicHost: String? = null,
    val registryUpstreamUrl: String? = null,
    val registryReleaseActionsUpstreamUrl: String? = null,
    val registrySecurityActionsUpstreamUrl: String? = null
)

fun loadAppConfig(): AppConfig = loadAppConfig(System.getenv())

internal fun loadAppConfig(env: Map<String, String>): AppConfig {
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
    val registryPublicHost = env["SEEN_REGISTRY_PUBLIC_HOST"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registryReleaseActionsUpstreamUrl = env["SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registrySecurityActionsUpstreamUrl = env["SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registryBaseValues = listOf(registryPublicHost, registryUpstreamUrl)
    require(registryBaseValues.all { it == null } || registryBaseValues.all { it != null }) {
        "Registry routing requires both the public host and API upstream URL"
    }
    val registryActionValues = listOf(
        registryReleaseActionsUpstreamUrl,
        registrySecurityActionsUpstreamUrl,
    )
    require(registryActionValues.all { it == null } || registryActionValues.all { it != null }) {
        "Registry action routing requires both isolated action upstream URLs or neither"
    }
    require(registryBaseValues.all { it != null } || registryActionValues.all { it == null }) {
        "Registry action routing cannot be enabled without the public host and API upstream URL"
    }
    
    return AppConfig(
        projectId = projectId,
        emulatorHost = emulatorHost,
        port = port,
        useLocalStore = useLocalStore,
        photographyUploadBucket = uploadBucket,
        photographyUploadPrefix = uploadPrefix,
        photographyUploadDir = uploadDir,
        photographyMaxUploadBytes = maxUploadBytes,
        registryPublicHost = registryPublicHost,
        registryUpstreamUrl = registryUpstreamUrl,
        registryReleaseActionsUpstreamUrl = registryReleaseActionsUpstreamUrl,
        registrySecurityActionsUpstreamUrl = registrySecurityActionsUpstreamUrl
    )
}
