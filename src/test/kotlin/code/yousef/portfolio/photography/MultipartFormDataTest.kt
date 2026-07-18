package code.yousef.portfolio.photography

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultipartFormDataTest {

    @Test
    fun `parses fields and binary file bytes`() {
        val boundary = "Boundary123"
        val fileBytes = byteArrayOf(0, 1, 2, -1, 10, 13)
        val body = multipartBody(
            boundary,
            textPart("title", "Window light"),
            textPart("altText", "A quiet window"),
            filePart("photo", "window.jpg", "image/jpeg", fileBytes)
        )

        val parsed = parseMultipartFormData(body, boundary, "photo")

        assertEquals("Window light", parsed.fields["title"])
        assertEquals("A quiet window", parsed.fields["altText"])
        assertEquals("photo", parsed.file?.fieldName)
        assertEquals("window.jpg", parsed.file?.originalFilename)
        assertEquals("image/jpeg", parsed.file?.contentType)
        assertContentEquals(fileBytes, parsed.file?.bytes)
    }

    @Test
    fun `returns null boundary when content type has none`() {
        assertNull(extractMultipartBoundary("multipart/form-data"))
    }

    @Test
    fun `returns no file when file part is missing`() {
        val boundary = "Boundary456"
        val body = multipartBody(boundary, textPart("title", "No file"))

        val parsed = parseMultipartFormData(body, boundary, "photo")

        assertEquals("No file", parsed.fields["title"])
        assertNull(parsed.file)
    }

    @Test
    fun `extracts quoted boundary`() {
        assertEquals("abc-123", extractMultipartBoundary("""multipart/form-data; boundary="abc-123""""))
    }

    private fun multipartBody(boundary: String, vararg parts: ByteArray): ByteArray {
        val end = "--$boundary--\r\n".toByteArray(Charsets.US_ASCII)
        return parts.fold(ByteArray(0)) { acc, part ->
            acc + "--$boundary\r\n".toByteArray(Charsets.US_ASCII) + part
        } + end
    }

    private fun textPart(name: String, value: String): ByteArray =
        "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
            .toByteArray(Charsets.UTF_8)

    private fun filePart(name: String, filename: String, contentType: String, bytes: ByteArray): ByteArray =
        "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\nContent-Type: $contentType\r\n\r\n"
            .toByteArray(Charsets.ISO_8859_1) + bytes + "\r\n".toByteArray(Charsets.US_ASCII)
}
