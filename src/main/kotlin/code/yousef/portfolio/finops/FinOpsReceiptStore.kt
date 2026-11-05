package code.yousef.portfolio.finops

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

interface FinOpsReceiptStore {
    fun save(filename: String, contentType: String, bytes: ByteArray, uploadId: String? = null): FinOpsReceiptReference
    fun load(reference: FinOpsReceiptReference): ByteArray
    fun requirePresent(reference: FinOpsReceiptReference)
}

class HttpEdgeFinOpsReceiptStore(
    endpoint: String,
    private val bearerToken: String,
    private val maxReceiptBytes: Long,
    private val client: HttpClient = defaultHttpClient(),
) : FinOpsReceiptStore {
    private val receiptEndpoint = validateEndpoint(endpoint).resolve("./v1/receipt")

    init {
        require(bearerToken.length >= 32 && bearerToken.none(Char::isISOControl)) {
            "EDGE_ORIGIN_TOKEN must contain at least 32 non-control characters"
        }
        require(maxReceiptBytes in 1..26_214_400L) { "FINOPS_RECEIPT_MAX_BYTES cannot exceed 25 MiB" }
    }

    override fun save(filename: String, contentType: String, bytes: ByteArray, uploadId: String?): FinOpsReceiptReference {
        val safeFilename = requireFilename(filename)
        val normalizedContentType = requireContentType(contentType)
        require(bytes.isNotEmpty() && bytes.size.toLong() <= maxReceiptBytes) {
            "receipt must contain between 1 and $maxReceiptBytes bytes"
        }
        val digest = receiptSha256Hex(bytes)
        val resolvedUploadId = uploadId ?: UUID.randomUUID().toString().replace("-", "")
        require(resolvedUploadId.matches(Regex("^[a-f0-9]{32}$"))) { "invalid receipt upload id" }
        val reference = FinOpsReceiptReference(
            storageKey = "sha256/$digest/uploads/$resolvedUploadId/$safeFilename",
            sha256 = digest,
            filename = safeFilename,
            contentType = normalizedContentType,
            size = bytes.size.toLong(),
            uploadId = resolvedUploadId,
        )
        val response = client.send(
            requestBuilder(reference)
                .header("content-type", normalizedContentType)
                .header("if-none-match", "*")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        require(response.statusCode() == 201 || response.statusCode() == 204) {
            "FinOps receipt write failed with HTTP ${response.statusCode()}"
        }
        return reference
    }

    override fun load(reference: FinOpsReceiptReference): ByteArray {
        val response = client.send(
            requestBuilder(reference).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { body ->
            require(response.statusCode() == 200) { "FinOps receipt read failed with HTTP ${response.statusCode()}" }
            val bytes = body.readNBytes(maxReceiptBytes.toInt())
            require(body.read() == -1 && bytes.size.toLong() == reference.size) {
                "FinOps receipt response size did not match its reference"
            }
            require(receiptSha256Hex(bytes) == reference.sha256) {
                "FinOps receipt response failed digest verification"
            }
            require(normalizeContentType(response.headers().firstValue("content-type").orElse("")) == reference.contentType) {
                "FinOps receipt response content type did not match its reference"
            }
            return bytes
        }
    }

    override fun requirePresent(reference: FinOpsReceiptReference) {
        val response = client.send(
            requestBuilder(reference).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        require(response.statusCode() == 200) { "FinOps receipt verification failed with HTTP ${response.statusCode()}" }
        require(response.headers().firstValueAsLong("content-length").orElse(-1L) == reference.size) {
            "FinOps receipt size did not match its reference"
        }
        require(response.headers().firstValue("x-finops-receipt-sha256").orElse("") == reference.sha256) {
            "FinOps receipt digest did not match its reference"
        }
    }

    private fun requestBuilder(reference: FinOpsReceiptReference): HttpRequest.Builder = HttpRequest
        .newBuilder(receiptEndpoint)
        .timeout(Duration.ofSeconds(30))
        .header("authorization", "Bearer $bearerToken")
        .header("accept", "application/json")
        .header("x-finops-receipt-key", reference.storageKey)
        .header("x-finops-receipt-sha256", reference.sha256)
        .header("x-finops-receipt-content-type", reference.contentType)

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        private fun validateEndpoint(raw: String): URI {
            val uri = URI(raw)
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
                "FINOPS_RECEIPT_BASE_URL must use HTTPS outside local tests"
            }
            require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "FINOPS_RECEIPT_BASE_URL must be an absolute URL without query or fragment"
            }
            return URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path.trimEnd('/') + "/", null, null)
        }

        private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }
}

fun receiptSha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

fun requireFinOpsReceiptFilename(value: String): String = value.trim().also(::requireFilename)

private fun requireFilename(value: String): String {
    require(value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,191}$"))) { "invalid receipt filename" }
    return value
}

fun normalizeFinOpsReceiptContentType(value: String): String = requireContentType(value)

private fun requireContentType(value: String): String = normalizeContentType(value).also {
    require(it in FINOPS_RECEIPT_CONTENT_TYPES) { "unsupported receipt content type" }
}

private fun normalizeContentType(value: String): String = value.substringBefore(';').trim().lowercase()
