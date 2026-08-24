package code.yousef.portfolio.finops

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinOpsReceiptStoreTest {
    @Test
    fun `HTTP store creates verifies and loads immutable receipt`() {
        val bytes = "invoice receipt".encodeToByteArray()
        val digest = receiptSha256Hex(bytes)
        val methods = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/internal/finops/v1/receipt") { exchange ->
            methods += exchange.requestMethod
            assertEquals("Bearer $TOKEN", exchange.requestHeaders.getFirst("authorization"))
            val storageKey = exchange.requestHeaders.getFirst("x-finops-receipt-key")
            assertTrue(
                storageKey.matches(Regex("^sha256/$digest/uploads/[a-f0-9]{32}/cloudflare-2026-08\\.pdf$")),
            )
            assertEquals(digest, exchange.requestHeaders.getFirst("x-finops-receipt-sha256"))
            when (exchange.requestMethod) {
                "PUT" -> {
                    assertEquals("*", exchange.requestHeaders.getFirst("if-none-match"))
                    assertContentEquals(bytes, exchange.requestBody.readBytes())
                    exchange.sendResponseHeaders(201, -1)
                }
                "HEAD" -> {
                    exchange.responseHeaders.add("content-length", bytes.size.toString())
                    exchange.responseHeaders.add("x-finops-receipt-sha256", digest)
                    exchange.sendResponseHeaders(200, -1)
                }
                "GET" -> {
                    exchange.responseHeaders.add("content-type", "application/pdf")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                else -> exchange.sendResponseHeaders(405, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            val store = HttpEdgeFinOpsReceiptStore(
                endpoint = "http://127.0.0.1:${server.address.port}/internal/finops",
                bearerToken = TOKEN,
                maxReceiptBytes = 1024,
            )
            val reference = store.save(
                "cloudflare-2026-08.pdf",
                "application/pdf; charset=binary",
                bytes,
                uploadId = "a".repeat(32),
            )
            assertEquals(digest, reference.sha256)
            assertEquals("a".repeat(32), reference.uploadId)
            store.requirePresent(reference)
            assertContentEquals(bytes, store.load(reference))
            assertEquals(listOf("PUT", "HEAD", "GET"), methods)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `receipt references and uploads reject unsafe metadata`() {
        assertFailsWith<IllegalArgumentException> {
            FinOpsReceiptReference("sha256/${"a".repeat(64)}/invoice.pdf", "b".repeat(64), "invoice.pdf", "application/pdf", 1)
        }
        val store = HttpEdgeFinOpsReceiptStore("http://127.0.0.1:1/internal/finops", TOKEN, 1024)
        assertFailsWith<IllegalArgumentException> { store.save("../invoice.pdf", "application/pdf", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { store.save("invoice.html", "text/html", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> {
            store.save("invoice.pdf", "application/pdf", byteArrayOf(1), uploadId = "not-safe")
        }
    }

    private companion object {
        const val TOKEN = "edge-origin-token-with-at-least-32-characters"
    }
}
