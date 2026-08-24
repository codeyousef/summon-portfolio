package code.yousef.portfolio.photography

import code.yousef.config.AppConfig
import code.yousef.config.PhotographyAssetWriteMode
import code.yousef.firestore.FirestoreProvider
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

private val CONTENT_ADDRESSED_PHOTO_KEY =
    Regex("^sha256/([a-f0-9]{64})/([A-Za-z0-9][A-Za-z0-9._-]{0,191})$")
private val PHOTO_ID = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
private val PHOTO_EXTENSION = Regex("^[a-z0-9]{1,10}$")
private val ALLOWED_PHOTO_CONTENT_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "video/mp4",
    "video/webm",
    "video/quicktime",
)

internal data class ContentAddressedPhotoKey(
    val key: String,
    val digest: String,
    val filename: String,
)

internal fun parseContentAddressedPhotoKey(storageKey: String): ContentAddressedPhotoKey? {
    val match = CONTENT_ADDRESSED_PHOTO_KEY.matchEntire(storageKey) ?: return null
    return ContentAddressedPhotoKey(
        key = storageKey,
        digest = match.groupValues[1],
        filename = match.groupValues[2],
    )
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

internal interface EdgePhotoAssetTransport {
    fun put(storageKey: String, digest: String, contentType: String, bytes: ByteArray)
    fun get(storageKey: String, digest: String, expectedContentType: String): PhotoAsset?
    fun delete(storageKey: String, digest: String)
}

internal class HttpEdgePhotoAssetTransport(
    endpoint: String,
    private val bearerToken: String,
    private val maxAssetBytes: Long,
    private val client: HttpClient = defaultHttpClient(),
) : EdgePhotoAssetTransport {
    private val assetEndpoint = validateEndpoint(endpoint).resolve("./v1/asset")

    init {
        require(bearerToken.length >= 32 && bearerToken.none(Char::isISOControl)) {
            "EDGE_ORIGIN_TOKEN must contain at least 32 non-control characters"
        }
        require(maxAssetBytes in 1..Int.MAX_VALUE.toLong()) {
            "PHOTOGRAPHY_MAX_UPLOAD_BYTES must fit in a JVM byte array"
        }
    }

    override fun put(storageKey: String, digest: String, contentType: String, bytes: ByteArray) {
        require(bytes.size.toLong() <= maxAssetBytes) { "Photography asset exceeds configured maximum size" }
        val request = requestBuilder(storageKey, digest)
            .header("content-type", contentType)
            .header("if-none-match", "*")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        require(response.statusCode() == 201 || response.statusCode() == 204) {
            "Portfolio R2 media write failed with HTTP ${response.statusCode()}"
        }
    }

    override fun get(storageKey: String, digest: String, expectedContentType: String): PhotoAsset? {
        val request = requestBuilder(storageKey, digest)
            .header("x-portfolio-media-content-type", expectedContentType)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() == 404) return null
            require(response.statusCode() == 200) {
                "Portfolio R2 media read failed with HTTP ${response.statusCode()}"
            }
            val bytes = body.readNBytes(maxAssetBytes.toInt())
            require(body.read() == -1) {
                "Portfolio R2 media response exceeds configured maximum size"
            }
            val responseDigest = response.headers().firstValue("x-portfolio-media-sha256").orElse("")
            require(responseDigest == digest && sha256Hex(bytes) == digest) {
                "Portfolio R2 media response failed digest verification"
            }
            val responseContentType = normalizeContentType(
                response.headers().firstValue("content-type").orElse(""),
            )
            require(responseContentType == normalizeContentType(expectedContentType)) {
                "Portfolio R2 media response content type did not match the authoritative record"
            }
            return PhotoAsset(bytes, responseContentType)
        }
    }

    override fun delete(storageKey: String, digest: String) {
        val response = client.send(
            requestBuilder(storageKey, digest).DELETE().build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        require(response.statusCode() == 204) {
            "Portfolio R2 media delete failed with HTTP ${response.statusCode()}"
        }
    }

    private fun requestBuilder(storageKey: String, digest: String): HttpRequest.Builder =
        HttpRequest.newBuilder(assetEndpoint)
            .timeout(Duration.ofSeconds(10))
            .header("authorization", "Bearer $bearerToken")
            .header("accept", "application/json")
            .header("x-portfolio-media-key", storageKey)
            .header("x-portfolio-media-sha256", digest)

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        private fun validateEndpoint(raw: String): URI {
            val uri = URI(raw)
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
                "PHOTOGRAPHY_R2_BASE_URL must use HTTPS outside local tests"
            }
            require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "PHOTOGRAPHY_R2_BASE_URL must be an absolute URL without query or fragment"
            }
            val normalizedPath = uri.path.trimEnd('/') + "/"
            return URI(uri.scheme, uri.userInfo, uri.host, uri.port, normalizedPath, null, null)
        }

        private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}

class CloudflareR2PhotoAssetStore internal constructor(
    private val transport: EdgePhotoAssetTransport,
) : PhotoAssetStore {
    override fun keyFor(photoId: String, extension: String): String =
        error("Cloudflare R2 photography keys require asset bytes")

    override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String {
        require(PHOTO_ID.matches(photoId)) { "Invalid photography media id" }
        val normalizedExtension = extension.lowercase()
        require(PHOTO_EXTENSION.matches(normalizedExtension)) { "Invalid photography media extension" }
        return "sha256/${sha256Hex(bytes)}/$photoId.$normalizedExtension"
    }

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        val key = requireContentAddressedKey(storageKey)
        val normalizedContentType = requireContentType(contentType)
        require(sha256Hex(bytes) == key.digest) { "Photography asset bytes do not match storage-key digest" }
        transport.put(key.key, key.digest, normalizedContentType, bytes)
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? {
        val key = requireContentAddressedKey(storageKey)
        return transport.get(key.key, key.digest, requireContentType(contentType))
    }

    override fun delete(storageKey: String) {
        val key = requireContentAddressedKey(storageKey)
        transport.delete(key.key, key.digest)
    }

    private fun requireContentAddressedKey(storageKey: String): ContentAddressedPhotoKey =
        requireNotNull(parseContentAddressedPhotoKey(storageKey)) {
            "R2 photography storage key must be an immutable sha256 key"
        }
}

internal class MigratingPhotoAssetStore(
    private val source: PhotoAssetStore,
    private val target: PhotoAssetStore,
    private val mode: PhotographyAssetWriteMode,
    private val reverseMirror: Boolean,
) : PhotoAssetStore {
    private val log = LoggerFactory.getLogger(MigratingPhotoAssetStore::class.java)

    init {
        require(mode != PhotographyAssetWriteMode.SOURCE || !reverseMirror)
        require(mode == PhotographyAssetWriteMode.TARGET || !reverseMirror)
    }

    override fun keyFor(photoId: String, extension: String): String = when (mode) {
        PhotographyAssetWriteMode.SOURCE -> source.keyFor(photoId, extension)
        PhotographyAssetWriteMode.DUAL,
        PhotographyAssetWriteMode.TARGET -> target.keyFor(photoId, extension)
    }

    override fun keyFor(photoId: String, extension: String, bytes: ByteArray): String = when (mode) {
        PhotographyAssetWriteMode.SOURCE -> source.keyFor(photoId, extension, bytes)
        PhotographyAssetWriteMode.DUAL,
        PhotographyAssetWriteMode.TARGET -> target.keyFor(photoId, extension, bytes)
    }

    override fun save(storageKey: String, contentType: String, bytes: ByteArray) {
        when (mode) {
            PhotographyAssetWriteMode.SOURCE -> source.save(storageKey, contentType, bytes)
            PhotographyAssetWriteMode.DUAL -> {
                target.save(storageKey, contentType, bytes)
                source.save(storageKey, contentType, bytes)
            }
            PhotographyAssetWriteMode.TARGET -> {
                target.save(storageKey, contentType, bytes)
                if (reverseMirror) source.save(storageKey, contentType, bytes)
            }
        }
    }

    override fun load(storageKey: String, contentType: String): PhotoAsset? = when (mode) {
        PhotographyAssetWriteMode.SOURCE -> source.load(storageKey, contentType)
        PhotographyAssetWriteMode.DUAL -> source.load(storageKey, contentType)?.also { asset ->
            if (parseContentAddressedPhotoKey(storageKey) != null) {
                runCatching { target.save(storageKey, asset.contentType, asset.bytes) }
                    .onFailure { error -> log.warn("Could not repair R2 photography mirror for {}", storageKey, error) }
            }
        }
        PhotographyAssetWriteMode.TARGET -> target.load(storageKey, contentType)
    }

    override fun delete(storageKey: String) {
        when (mode) {
            PhotographyAssetWriteMode.SOURCE -> source.delete(storageKey)
            PhotographyAssetWriteMode.DUAL -> {
                val operations = buildList {
                    add { source.delete(storageKey) }
                    if (parseContentAddressedPhotoKey(storageKey) != null) add { target.delete(storageKey) }
                }
                runAll(operations)
            }
            PhotographyAssetWriteMode.TARGET -> {
                val operations = buildList {
                    add { target.delete(storageKey) }
                    if (reverseMirror) add { source.delete(storageKey) }
                }
                runAll(operations)
            }
        }
    }

    private fun runAll(operations: List<() -> Unit>) {
        val failures = operations.mapNotNull { operation -> runCatching(operation).exceptionOrNull() }
        if (failures.isNotEmpty()) {
            val first = failures.first()
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }
}

internal object PortfolioPhotoAssetStoreFactory {
    fun fromEnvironment(
        appConfig: AppConfig,
        environment: Map<String, String> = System.getenv(),
    ): PhotoAssetStore {
        val gcsCredentials = appConfig.gcsCredentials()
        val source = appConfig.photographyUploadBucket?.let { bucket ->
            GcsPhotoAssetStore(
                bucket = bucket,
                prefix = appConfig.photographyUploadPrefix,
                credentials = gcsCredentials,
                projectId = appConfig.projectId,
            )
        } ?: LocalPhotoAssetStore(appConfig.photographyUploadDir)
        if (appConfig.photographyWriteMode == PhotographyAssetWriteMode.SOURCE) return source

        val token = environment["EDGE_ORIGIN_TOKEN"]
            ?: error("EDGE_ORIGIN_TOKEN is required for R2 photography storage")
        val endpoint = requireNotNull(appConfig.photographyR2BaseUrl)
        val target = CloudflareR2PhotoAssetStore(
            HttpEdgePhotoAssetTransport(
                endpoint = endpoint,
                bearerToken = token,
                maxAssetBytes = appConfig.photographyMaxUploadBytes,
            ),
        )
        return MigratingPhotoAssetStore(
            source = source,
            target = target,
            mode = appConfig.photographyWriteMode,
            reverseMirror = appConfig.photographyR2ReverseMirror,
        )
    }

    fun backfillFromEnvironment(
        appConfig: AppConfig,
        environment: Map<String, String> = System.getenv(),
    ): PhotographyAssetBackfillService? {
        if (environment["PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED"] != "true") return null
        require(appConfig.photographyWriteMode == PhotographyAssetWriteMode.SOURCE) {
            "Photography backfill requires source-authoritative asset mode"
        }
        val bucket = requireNotNull(appConfig.photographyUploadBucket) {
            "Photography backfill requires PHOTOGRAPHY_UPLOAD_BUCKET"
        }
        val token = requireNotNull(environment["EDGE_ORIGIN_TOKEN"]?.trim()?.takeIf(String::isNotEmpty)) {
            "Photography backfill requires EDGE_ORIGIN_TOKEN"
        }
        val endpoint = requireNotNull(appConfig.photographyR2BaseUrl) {
            "Photography backfill requires PHOTOGRAPHY_R2_BASE_URL"
        }
        return PhotographyAssetBackfillService(
            source = GcsPhotoAssetStore(
                bucket = bucket,
                prefix = appConfig.photographyUploadPrefix,
                credentials = appConfig.gcsCredentials(),
                projectId = appConfig.projectId,
            ),
            target = CloudflareR2PhotoAssetStore(
                HttpEdgePhotoAssetTransport(
                    endpoint = endpoint,
                    bearerToken = token,
                    maxAssetBytes = appConfig.photographyMaxUploadBytes,
                ),
            ),
        )
    }

    private fun AppConfig.gcsCredentials(): GoogleCredentials? =
        firestoreServiceAccountJsonBase64?.let { encoded ->
            FirestoreProvider.decodeServiceAccountCredentials(encoded, projectId)
        }
}

private fun requireContentType(contentType: String): String {
    val normalized = normalizeContentType(contentType)
    require(normalized in ALLOWED_PHOTO_CONTENT_TYPES) { "Unsupported photography asset content type" }
    return normalized
}

private fun normalizeContentType(contentType: String): String =
    contentType.substringBefore(';').trim().lowercase()
