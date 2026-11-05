package code.yousef.aws.dynamodb

import code.yousef.firestore.migration.FirestoreCanonicalHash
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.GeoPoint
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamoDocumentCodecTest {
    @Test
    fun `random documents round trip exactly including numeric type`() {
        val random = Random(20261021)
        repeat(500) {
            val document = randomMap(random, depth = 0)
            val decoded = DynamoDocumentCodec.decodeDocument(DynamoDocumentCodec.encodeDocument(document))
            assertEquals(normalized(document), decoded)
            assertEquals(FirestoreCanonicalHash.document(document), FirestoreCanonicalHash.document(decoded))
        }
    }

    @Test
    fun `edge values keep their exact representation`() {
        val document = mapOf(
            "long" to Long.MIN_VALUE,
            "max" to Long.MAX_VALUE,
            "int" to 7,
            "whole double" to 5.0,
            "negative zero" to -0.0,
            "nan" to Double.NaN,
            "infinity" to Double.NEGATIVE_INFINITY,
            "tiny" to Double.MIN_VALUE,
            "empty string" to "",
            "empty map" to emptyMap<String, Any?>(),
            "empty list" to emptyList<Any?>(),
            "null" to null,
            "timestamp" to Timestamp.ofTimeSecondsAndNanos(1_792_000_000, 123_456_789),
            "blob" to Blob.fromBytes(byteArrayOf(0, 1, 2, -1)),
            "geo" to GeoPoint(59.33, 18.06),
            "unicode" to "\u0000 é 😀",
            "nested" to listOf(mapOf("a" to listOf(1L, 2.5, null))),
        )
        val decoded = DynamoDocumentCodec.decodeDocument(DynamoDocumentCodec.encodeDocument(document))
        assertEquals(7L, decoded["int"])
        assertEquals(5.0, decoded["whole double"])
        assertTrue(decoded["whole double"] is Double)
        assertEquals(-0.0, decoded["negative zero"])
        assertTrue(1.0 / (decoded["negative zero"] as Double) < 0)
        assertTrue((decoded["nan"] as Double).isNaN())
        assertEquals(Long.MIN_VALUE, decoded["long"])
        assertEquals(document["timestamp"], decoded["timestamp"])
        assertEquals(document["blob"], decoded["blob"])
        assertEquals(document["geo"], decoded["geo"])
        assertEquals(document["unicode"], decoded["unicode"])
        assertEquals(listOf(mapOf("a" to listOf(1L, 2.5, null))), decoded["nested"])
        assertTrue(decoded.containsKey("null") && decoded["null"] == null)
    }

    @Test
    fun `integers are native numbers and doubles are tagged binary`() {
        assertEquals("42", DynamoDocumentCodec.encode(42L).n())
        assertEquals(AttributeValue.Type.B, DynamoDocumentCodec.encode(4.2).type())
        assertEquals(AttributeValue.Type.S, DynamoDocumentCodec.encode("4.2").type())
    }

    @Test
    fun `unsupported values and decimal numbers fail closed`() {
        assertFailsWith<IllegalArgumentException> { DynamoDocumentCodec.encode(java.time.Instant.EPOCH) }
        assertFailsWith<IllegalArgumentException> { DynamoDocumentCodec.encodeDocument(mapOf("" to 1)) }
        assertFailsWith<IllegalArgumentException> { DynamoDocumentCodec.decode(AttributeValue.fromN("1.5")) }
    }

    @Test
    fun `order keys preserve signed long and UTF-8 string order`() {
        val random = Random(7)
        val longs = List(2_000) { random.nextLong() } + listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)
        assertEquals(longs.sorted(), longs.sortedBy { DynamoDbPortfolioDocumentStore.orderKey(it) })
        val strings = List(2_000) { randomString(random) } + listOf("", "a", "ab", "a b", "a#", "~", "é", "😀", "￿")
        val utf8 = Comparator<String> { a, b -> java.util.Arrays.compareUnsigned(a.toByteArray(), b.toByteArray()) }
        assertEquals(strings.sortedWith(utf8), strings.sortedBy { DynamoDbPortfolioDocumentStore.orderKey(it) })
        // A value prefix sorts before its extensions even with the "#<id>" suffix appended.
        assertTrue(DynamoDbPortfolioDocumentStore.orderKey("a") + "#zzz" < DynamoDbPortfolioDocumentStore.orderKey("a ") + "#a")
    }

    private fun normalized(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Map<*, *> -> value.entries.associate { (k, v) -> k to normalized(v) }
        is List<*> -> value.map(::normalized)
        else -> value
    }

    private fun randomMap(random: Random, depth: Int): Map<String, Any?> =
        (0 until random.nextInt(0, 6)).associate { "f${random.nextInt(1000)}_${randomString(random).take(4)}" to randomValue(random, depth) }

    private fun randomValue(random: Random, depth: Int): Any? = when (random.nextInt(if (depth > 3) 8 else 10)) {
        0 -> null
        1 -> random.nextBoolean()
        2 -> random.nextLong()
        3 -> random.nextInt()
        4 -> random.nextDouble(-1e12, 1e12)
        5 -> randomString(random)
        6 -> Timestamp.ofTimeSecondsAndNanos(random.nextLong(0, 253_402_300_799), random.nextInt(0, 999_999_999))
        7 -> Blob.fromBytes(random.nextBytes(random.nextInt(0, 16)))
        8 -> randomMap(random, depth + 1)
        else -> List(random.nextInt(0, 4)) { randomValue(random, depth + 1) }
    }

    private fun randomString(random: Random): String = buildString {
        repeat(random.nextInt(0, 12)) {
            append(
                when (random.nextInt(4)) {
                    0 -> ('a'..'z').random(random)
                    1 -> ('A'..'Z').random(random)
                    2 -> listOf('#', '$', ' ', '_', '-', '|').random(random)
                    else -> listOf("é", "ß", "😀", "中").random(random)
                },
            )
        }
    }
}
