package codes.yousef.seen.registry

import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.jvm.createVertxExchangeWithBody
import codes.yousef.aether.core.pipeline.Pipeline
import io.vertx.core.Vertx
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.streams.WriteStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayOutputStream

/**
 * Aether-compatible server with one transport-level exception: archive bodies
 * are streamed to a private temporary file instead of aggregated in memory.
 */
class RegistryHttpServer(
    private val config: VertxServerConfig,
    private val pipeline: Pipeline,
    private val routes: RegistryRoutes,
    private val fallback: suspend (Exchange) -> Unit,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val vertx = Vertx.vertx()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: HttpServer? = null
    val actualPort: Int get() = requireNotNull(server) { "Registry server is not started" }.actualPort()

    fun start() {
        val options = HttpServerOptions()
            .setHost(config.host)
            .setPort(config.port)
            .setCompressionSupported(config.compressionSupported)
            .setDecompressionSupported(config.decompressionSupported)
            .setMaxHeaderSize(config.maxHeaderSize)
            .setMaxChunkSize(config.maxChunkSize)
            .setMaxInitialLineLength(config.maxInitialLineLength)
        server = vertx.createHttpServer(options)
            .requestHandler(::handle)
            .listen()
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS)
    }

    private fun handle(request: HttpServerRequest) {
        val match = if (request.method().name() == "PUT") ARCHIVE_PATH.matchEntire(request.path().orEmpty()) else null
        if (match != null) streamArchive(request, match.groupValues[1]) else bufferRequest(request)
    }

    private fun streamArchive(request: HttpServerRequest, uploadId: String) {
        val exchange = createVertxExchangeWithBody(request, ByteArray(0))
        val principal = try {
            routes.authorizeStreamingArchive(exchange)
        } catch (error: RegistryException) {
            reject(exchange, error)
            return
        }
        val contentType = request.getHeader("Content-Type")
        val contentEncoding = request.getHeader("Content-Encoding")
        val declaredLength = request.getHeader("Content-Length")?.toLongOrNull()
        when {
            contentType != "application/gzip" -> {
                reject(exchange, RegistryException(400, "invalid_request", "Archive Content-Type must be application/gzip"))
                return
            }
            !contentEncoding.isNullOrBlank() && contentEncoding != "identity" -> {
                reject(exchange, RegistryException(400, "invalid_request", "Archive Content-Encoding is not supported"))
                return
            }
            declaredLength == null || declaredLength <= 0 -> {
                reject(exchange, RegistryException(400, "invalid_request", "Archive Content-Length is required"))
                return
            }
            declaredLength > ArchivePolicy.MAX_COMPRESSED_BYTES -> {
                reject(exchange, RegistryException(413, "archive_too_large", "Archive exceeds the compressed byte limit"))
                return
            }
        }

        // Opening the private spool file is asynchronous. Pause before yielding
        // the event loop so a small request cannot finish before pipeTo installs
        // its end handler. Validation failures above can still be rejected
        // without leaving an unread request paused.
        request.pause()
        val temporary = try {
            Files.createTempFile("seen-registry-upload-", ".tgz").also { path ->
                runCatching {
                    Files.setPosixFilePermissions(path, setOf(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    ))
                }
            }
        } catch (error: Exception) {
            reject(exchange, RegistryException(503, "temporarily_unavailable", "Archive intake is temporarily unavailable", true, 30))
            return
        }
        vertx.fileSystem().open(
            temporary.toString(),
            OpenOptions().setWrite(true).setTruncateExisting(true).setCreate(false),
        ).onFailure {
            cleanup(temporary)
            reject(exchange, RegistryException(503, "temporarily_unavailable", "Archive intake is temporarily unavailable", true, 30))
        }.onSuccess { output ->
            val observed = DigestingArchiveWriteStream(output, ArchivePolicy.MAX_COMPRESSED_BYTES)
            request.pipeTo(observed).onComplete { result ->
                if (result.failed()) {
                    output.close().onComplete {
                        cleanup(temporary)
                        if (!request.response().headWritten()) {
                            reject(exchange, RegistryException(400, "invalid_request", "Archive upload was interrupted", true, 5))
                        }
                    }
                } else {
                    val actualLength = observed.byteCount
                    val actualDigest = observed.sha256()
                    if (actualLength != declaredLength || actualLength > ArchivePolicy.MAX_COMPRESSED_BYTES) {
                        cleanup(temporary)
                        reject(exchange, RegistryException(422, "digest_mismatch", "Archive bytes do not match the reservation"))
                    } else {
                        scope.launch {
                            try {
                                routes.completeStreamingArchive(
                                    exchange = exchange,
                                    uploadId = uploadId,
                                    digestHeader = request.getHeader("X-Seen-Archive-Sha256"),
                                    source = ReopenableArchiveSource {
                                        Files.newInputStream(temporary, StandardOpenOption.READ)
                                    },
                                    byteLength = actualLength,
                                    observedSha256 = actualDigest,
                                    principal = principal,
                                )
                            } finally {
                                cleanup(temporary)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bufferRequest(request: HttpServerRequest) {
        val exchange = createVertxExchangeWithBody(request, ByteArray(0))
        val accumulator = BoundedBody(config.maxRequestBodySize)
        val completed = AtomicBoolean(false)
        request.getHeader("Content-Length")?.toLongOrNull()?.let { declared ->
            try {
                accumulator.declareLength(declared)
            } catch (_: Exception) {
                completed.set(true)
                reject(exchange, RegistryException(413, "request_too_large", "Request body exceeds the limit"))
                return
            }
        }
        request.handler { chunk ->
            if (!completed.get()) {
                try {
                    accumulator.append(chunk)
                } catch (_: Exception) {
                    if (completed.compareAndSet(false, true)) {
                        request.pause()
                        reject(exchange, RegistryException(413, "request_too_large", "Request body exceeds the limit"))
                    }
                }
            }
        }
        request.endHandler {
            if (completed.compareAndSet(false, true)) {
                val bytes = try {
                    accumulator.finish()
                } catch (_: Exception) {
                    reject(exchange, RegistryException(400, "invalid_request", "Request body is incomplete"))
                    return@endHandler
                }
                execute(request, bytes)
            }
        }
        request.exceptionHandler { error ->
            if (completed.compareAndSet(false, true) && !request.response().headWritten()) {
                log.info("Registry client disconnected before request completion: {}", error.message)
                reject(exchange, RegistryException(400, "invalid_request", "Request body is incomplete"))
            }
        }
    }

    private fun execute(request: HttpServerRequest, body: ByteArray) {
        val exchange = createVertxExchangeWithBody(request, body)
        scope.launch {
            try {
                pipeline.execute(exchange, fallback)
            } catch (error: Exception) {
                if (error.isClosedChannelTransportFailure()) {
                    log.debug("Registry response transport closed after request completion: {}", error.javaClass.simpleName)
                    return@launch
                }
                log.error("Unhandled registry pipeline failure", error)
                if (!request.response().headWritten()) {
                    routes.rejectStreamingArchive(
                        exchange,
                        RegistryException(500, "internal_error", "The registry could not complete the request", true, 30),
                    )
                }
            }
        }
    }

    private fun reject(exchange: Exchange, error: RegistryException) {
        scope.launch {
            runCatching { routes.rejectStreamingArchive(exchange, error) }
                .onFailure { log.info("Registry response ended before error delivery: {}", it.message) }
        }
    }

    private fun cleanup(path: Path) {
        runCatching { Files.deleteIfExists(path) }
            .onFailure { log.warn("Could not remove temporary registry upload {}", path, it) }
    }

    override fun close() {
        scope.cancel()
        runCatching { server?.close()?.toCompletionStage()?.toCompletableFuture()?.get(30, TimeUnit.SECONDS) }
        runCatching { vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS) }
    }

    private companion object {
        val ARCHIVE_PATH = Regex("^/packages/api/v1/uploads/(upl_[A-Za-z0-9_-]{16,96})/archive$")
    }

    /**
     * Adds bounded byte counting and SHA-256 observation to Vert.x backpressure
     * without materializing the request body. The digest is finalized only
     * after the delegated stream has accepted its end signal.
     */
    private class DigestingArchiveWriteStream(
        private val delegate: WriteStream<Buffer>,
        private val maximumBytes: Long,
    ) : WriteStream<Buffer> {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var ended = false
        private var finalizedDigest: String? = null
        var byteCount: Long = 0
            private set

        override fun exceptionHandler(handler: Handler<Throwable>?): WriteStream<Buffer> {
            delegate.exceptionHandler(handler)
            return this
        }

        override fun write(data: Buffer): Future<Void> = runCatching {
            observe(data)
            delegate.write(data)
        }.getOrElse { Future.failedFuture(it) }

        override fun write(data: Buffer, handler: Handler<AsyncResult<Void>>?) {
            try {
                observe(data)
                delegate.write(data, handler)
            } catch (error: Throwable) {
                handler?.handle(Future.failedFuture(error))
            }
        }

        override fun end(handler: Handler<AsyncResult<Void>>?) {
            ended = true
            delegate.end(handler)
        }

        override fun setWriteQueueMaxSize(maxSize: Int): WriteStream<Buffer> {
            delegate.setWriteQueueMaxSize(maxSize)
            return this
        }

        override fun writeQueueFull(): Boolean = delegate.writeQueueFull()

        override fun drainHandler(handler: Handler<Void>?): WriteStream<Buffer> {
            delegate.drainHandler(handler)
            return this
        }

        fun sha256(): String {
            check(ended) { "Archive stream has not ended" }
            return finalizedDigest ?: digest.digest().joinToString("") { "%02x".format(it) }
                .also { finalizedDigest = it }
        }

        private fun observe(data: Buffer) {
            check(!ended) { "Archive stream is already closed" }
            val next = byteCount + data.length().toLong()
            require(next <= maximumBytes) { "Archive exceeds the compressed byte limit" }
            digest.update(data.bytes)
            byteCount = next
        }
    }

    private class BoundedBody(private val maximumBytes: Int) {
        private val output = ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024))
        private var declaredLength: Long? = null

        fun declareLength(value: Long) {
            require(value in 0..maximumBytes.toLong()) { "Request body is too large" }
            declaredLength = value
        }

        fun append(buffer: Buffer) {
            require(output.size().toLong() + buffer.length() <= maximumBytes) { "Request body is too large" }
            output.write(buffer.bytes)
        }

        fun finish(): ByteArray = output.toByteArray().also { bytes ->
            require(declaredLength == null || declaredLength == bytes.size.toLong()) { "Request body is incomplete" }
        }
    }
}
