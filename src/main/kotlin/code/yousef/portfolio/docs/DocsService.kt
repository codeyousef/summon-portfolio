@file:OptIn(kotlin.time.ExperimentalTime::class)
package code.yousef.portfolio.docs

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Instant
import kotlin.time.Instant.Companion.fromEpochMilliseconds
import kotlin.time.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class DocsService(
    private val config: DocsConfig,
    private val cache: DocsCache,
    private val httpClient: HttpClient = defaultHttpClient(config),
) {

    private val logger = LoggerFactory.getLogger(DocsService::class.java)

    @Volatile
    private var navIndex = DocsNavIndex()

    suspend fun fetchDocument(repoPath: String, branchOverride: String? = null): FetchedDoc {
        val branch = branchOverride ?: config.defaultBranch
        val cacheKey = "$branch:$repoPath"
        val cached = cache.getDocument(cacheKey)

        val sourceResult = when (config.docsSource) {
            DocsSource.LOCAL -> fetchLocalDocument(repoPath)
            DocsSource.REMOTE -> fetchRemoteDocument(repoPath, branch, cacheKey, cached)
        }

        if (config.docsSource == DocsSource.LOCAL && (cached == null || cached.body != sourceResult.body)) {
            cache.putDocument(cacheKey, repoPath, sourceResult.body, sourceResult.etag, sourceResult.lastModified)
        }

        return sourceResult
    }

    suspend fun fetchAsset(repoPath: String, branchOverride: String? = null): FetchedAsset {
        val branch = branchOverride ?: config.defaultBranch
        val cacheKey = "asset:$branch:$repoPath"
        val cached = cache.getAsset(cacheKey)

        if (config.docsSource == DocsSource.LOCAL) {
            val path = localPathFor(repoPath)
            if (path.notExists() || path.isDirectory()) {
                throw AssetNotFound(repoPath)
            }
            val bytes = path.readBytes()
            val contentType = Files.probeContentType(path)
            val entry = cache.putAsset(cacheKey, repoPath, bytes, contentType, null, null)
            return FetchedAsset(repoPath, entry.bytes, contentType, null, null)
        }

        val url = config.rawFileUrl(branch, repoPath)
        val response = httpClient.get(url) {
            cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            cached?.lastModified?.let { header(HttpHeaders.IfModifiedSince, instantToHttpDate(it)) }
        }

        return when (response.status) {
            HttpStatusCode.NotModified -> {
                if (cached == null) throw AssetNotFound(repoPath)
                FetchedAsset(repoPath, cached.bytes, cached.contentType, cached.etag, cached.lastModified)
            }

            HttpStatusCode.OK -> {
                val bytes: ByteArray = response.body()
                val contentType = response.headers[HttpHeaders.ContentType]
                cache.putAsset(
                    cacheKey,
                    repoPath,
                    bytes,
                    contentType,
                    response.headers[HttpHeaders.ETag],
                    response.headers[HttpHeaders.LastModified]?.let(::httpDateToInstant)
                )
                FetchedAsset(
                    repoPath = repoPath,
                    bytes = bytes,
                    contentType = contentType,
                    etag = response.headers[HttpHeaders.ETag],
                    lastModified = response.headers[HttpHeaders.LastModified]?.let(::httpDateToInstant)
                )
            }

            HttpStatusCode.NotFound -> throw AssetNotFound(repoPath)
            else -> error("Unexpected response ${response.status} for asset $repoPath")
        }
    }

    fun recordNavEntry(path: String, meta: MarkdownMeta) {
        navIndex.record(path, meta)
        cache.cacheNavTree(navIndex.buildTree())
    }

    fun currentNavTree(): DocsNavTree = cache.currentNavTree() ?: navIndex.buildTree()

    fun neighborLinks(path: String): NeighborLinks {
        val flat = currentNavTree().flatten()
        val currentIndex = flat.indexOfFirst { it.path == path }
        if (currentIndex == -1) return NeighborLinks(null, null)
        val previous = flat.getOrNull(currentIndex - 1)?.let { NavLink(it.title, it.path) }
        val next = flat.getOrNull(currentIndex + 1)?.let { NavLink(it.title, it.path) }
        return NeighborLinks(previous, next)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    fun invalidateNavTree() {
        navIndex = DocsNavIndex()
        cache.cacheNavTree(DocsNavTree(emptyList()))
    }

    fun invalidatePrefix(prefix: String) {
        cache.invalidatePrefix(prefix)
    }

    private fun fetchLocalDocument(repoPath: String): FetchedDoc {
        val path = localPathFor(repoPath)
        if (path.notExists() || path.isDirectory()) {
            throw DocumentNotFound(repoPath)
        }
        val body = path.readText()
        return FetchedDoc(repoPath, body, etag = null, lastModified = null)
    }

    private suspend fun fetchRemoteDocument(
        repoPath: String,
        branch: String,
        cacheKey: String,
        cached: CachedDoc?
    ): FetchedDoc {
        val url = config.rawFileUrl(branch, repoPath)
        val response = httpClient.get(url) {
            cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            cached?.lastModified?.let { header(HttpHeaders.IfModifiedSince, instantToHttpDate(it)) }
        }

        return when (response.status) {
            HttpStatusCode.NotModified -> {
                if (cached == null) throw DocumentNotFound(repoPath)
                cached.toFetchedDoc()
            }

            HttpStatusCode.OK -> {
                val body: String = response.body()
                val etag = response.headers[HttpHeaders.ETag]
                val lastModified = response.headers[HttpHeaders.LastModified]?.let(::httpDateToInstant)
                cache.putDocument(cacheKey, repoPath, body, etag, lastModified)
                FetchedDoc(repoPath, body, etag, lastModified)
            }

            HttpStatusCode.NotFound -> throw DocumentNotFound(repoPath)
            else -> {
                val message = "Failed to fetch $repoPath (${response.status})"
                logger.error(message)
                throw IOException(message)
            }
        }
    }

    private fun CachedDoc.toFetchedDoc(): FetchedDoc =
        FetchedDoc(repoPath = repoPath, body = body, etag = etag, lastModified = lastModified)

    private fun localPathFor(repoPath: String): Path {
        val relative = repoPath.removePrefix(config.normalizedDocsRoot).trimStart('/')
        return config.localDocsRoot.resolve(relative.ifBlank { "README.md" })
    }

    private fun httpDateToInstant(value: String): Instant? =
        try {
            val parsed = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            fromEpochMilliseconds(parsed.toInstant().toEpochMilli())
        } catch (_: Throwable) {
            null
        }

    private fun instantToHttpDate(value: Instant): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(value.toJavaInstant().atOffset(ZoneOffset.UTC))

    class DocumentNotFound(val repoPath: String) : RuntimeException("Document not found: $repoPath")
    class AssetNotFound(val repoPath: String) : RuntimeException("Asset not found: $repoPath")

    companion object {
        private fun defaultHttpClient(config: DocsConfig): HttpClient =
            HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 10_000
                }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 2)
                    exponentialDelay()
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    header(HttpHeaders.UserAgent, "SummonDocs/1.0")
                    config.githubToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
            }
    }
}

@Serializable
data class GithubContent(
    val name: String,
    val path: String,
    val type: String,
    val download_url: String? = null
)
