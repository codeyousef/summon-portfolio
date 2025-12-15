@file:OptIn(kotlin.time.ExperimentalTime::class)
package code.yousef.portfolio.docs

import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.concurrent.TimeUnit

class DocsCache(
    cacheTtlSeconds: Long,
    maximumSize: Long = 2000
) {
    private val documents = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(maximumSize)
        .build<String, CachedDoc>()

    private val assets = Caffeine.newBuilder()
        .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(maximumSize)
        .build<String, CachedAsset>()

    @Volatile
    private var navTree: Pair<DocsNavTree, Instant>? = null

    fun getDocument(key: String): CachedDoc? = documents.getIfPresent(key)

    fun putDocument(key: String, repoPath: String, body: String, etag: String?, lastModified: Instant?): CachedDoc {
        val entry = CachedDoc(
            repoPath = repoPath,
            body = body,
            etag = etag,
            lastModified = lastModified,
            fetchedAt = Clock.System.now()
        )
        documents.put(key, entry)
        return entry
    }

    fun getAsset(key: String): CachedAsset? = assets.getIfPresent(key)

    fun putAsset(
        key: String,
        repoPath: String,
        bytes: ByteArray,
        contentType: String?,
        etag: String?,
        lastModified: Instant?
    ): CachedAsset {
        val entry = CachedAsset(
            repoPath = repoPath,
            bytes = bytes,
            contentType = contentType,
            etag = etag,
            lastModified = lastModified,
            fetchedAt = Clock.System.now()
        )
        assets.put(key, entry)
        return entry
    }

    fun invalidatePrefix(prefix: String) {
        documents.asMap().keys.filter { it.startsWith(prefix) }.forEach(documents::invalidate)
        assets.asMap().keys.filter { it.startsWith(prefix) }.forEach(assets::invalidate)
    }

    fun invalidateAll() {
        documents.invalidateAll()
        assets.invalidateAll()
        navTree = null
    }

    fun cacheNavTree(tree: DocsNavTree) {
        navTree = tree to Clock.System.now()
    }

    fun currentNavTree(): DocsNavTree? = navTree?.first
}
