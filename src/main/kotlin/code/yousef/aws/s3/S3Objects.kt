package code.yousef.aws.s3

import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.InputStream
import java.util.Base64
import java.util.HexFormat

/** Shared S3 helpers: key prefixes, bounded reads and SHA-256 checksum encoding. */
internal object S3Objects {
    private val PREFIX = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$")

    /** Normalizes an optional key prefix to "" or "segment/.../". */
    fun normalizePrefix(raw: String?): String {
        val trimmed = raw?.trim()?.trim('/')?.takeIf(String::isNotEmpty) ?: return ""
        require(PREFIX.matches(trimmed) && trimmed.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid S3 key prefix"
        }
        return "$trimmed/"
    }

    fun checksumSha256(hexDigest: String): String = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hexDigest))

    fun readBounded(stream: InputStream, maximumBytes: Long): ByteArray = stream.use { input ->
        require(maximumBytes in 1..Int.MAX_VALUE.toLong() - 1)
        val bytes = input.readNBytes(maximumBytes.toInt())
        require(input.read() == -1) { "S3 object exceeds the configured maximum size" }
        bytes
    }

    fun S3Exception.isPreconditionFailed(): Boolean = statusCode() == 412
    fun S3Exception.isNotFound(): Boolean = statusCode() == 404
    fun S3Exception.isConditionalConflict(): Boolean = statusCode() == 409
}
