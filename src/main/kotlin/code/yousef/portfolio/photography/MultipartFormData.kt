package code.yousef.portfolio.photography

data class MultipartFormData(
    val fields: Map<String, String>,
    val file: MultipartFilePart?
)

data class MultipartFilePart(
    val fieldName: String,
    val originalFilename: String?,
    val contentType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipartFilePart) return false
        return fieldName == other.fieldName &&
            originalFilename == other.originalFilename &&
            contentType == other.contentType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + (originalFilename?.hashCode() ?: 0)
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

fun extractMultipartBoundary(contentType: String): String? {
    val marker = "boundary="
    val index = contentType.indexOf(marker)
    if (index < 0) return null
    return contentType.substring(index + marker.length)
        .substringBefore(";")
        .trim()
        .removeSurrounding("\"")
        .takeIf { it.isNotBlank() }
}

fun parseMultipartFormData(body: ByteArray, boundary: String, fileFieldName: String): MultipartFormData {
    val boundaryBytes = "--$boundary".toByteArray(Charsets.US_ASCII)
    val headerSeparator = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    val fields = linkedMapOf<String, String>()
    var file: MultipartFilePart? = null
    var position = 0

    while (position < body.size) {
        val boundaryPosition = body.indexOf(boundaryBytes, position)
        if (boundaryPosition < 0) break

        var headerStart = boundaryPosition + boundaryBytes.size
        if (headerStart + 1 < body.size && body[headerStart] == '-'.code.toByte() && body[headerStart + 1] == '-'.code.toByte()) {
            break
        }
        if (headerStart + 1 < body.size && body[headerStart] == '\r'.code.toByte() && body[headerStart + 1] == '\n'.code.toByte()) {
            headerStart += 2
        }

        val headerEnd = body.indexOf(headerSeparator, headerStart)
        if (headerEnd < 0) break

        val headers = String(body.copyOfRange(headerStart, headerEnd), Charsets.ISO_8859_1)
            .split("\r\n")
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
            .toMap()

        val disposition = headers["content-disposition"].orEmpty()
        val name = disposition.parameter("name")
        val filename = disposition.parameter("filename")
        val contentType = headers["content-type"] ?: "application/octet-stream"
        val contentStart = headerEnd + headerSeparator.size
        val nextBoundary = body.indexOf(boundaryBytes, contentStart)
        if (nextBoundary < 0) break

        var contentEnd = nextBoundary
        if (contentEnd >= 2 && body[contentEnd - 2] == '\r'.code.toByte() && body[contentEnd - 1] == '\n'.code.toByte()) {
            contentEnd -= 2
        }
        val contentBytes = body.copyOfRange(contentStart, contentEnd)

        if (!name.isNullOrBlank()) {
            if (filename != null && name == fileFieldName) {
                file = MultipartFilePart(
                    fieldName = name,
                    originalFilename = filename.takeIf { it.isNotBlank() },
                    contentType = contentType,
                    bytes = contentBytes
                )
            } else if (filename == null) {
                fields[name] = String(contentBytes, Charsets.UTF_8)
            }
        }

        position = nextBoundary
    }

    return MultipartFormData(fields = fields, file = file)
}

private fun String.parameter(name: String): String? {
    val prefix = "$name="
    return split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.removeSurrounding("\"")
}

private fun ByteArray.indexOf(needle: ByteArray, fromIndex: Int = 0): Int {
    if (needle.isEmpty()) return fromIndex.coerceAtMost(size)
    if (needle.size > size) return -1
    outer@ for (index in fromIndex.coerceAtLeast(0)..(size - needle.size)) {
        for (needleIndex in needle.indices) {
            if (this[index + needleIndex] != needle[needleIndex]) continue@outer
        }
        return index
    }
    return -1
}
