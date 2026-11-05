package code.yousef.aws.dynamodb

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.GeoPoint
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * Lossless mapping between Portfolio document values (the Firestore value model) and
 * DynamoDB attribute values.
 *
 * Strings, integers, booleans, nulls, maps and lists use their native DynamoDB types so
 * the table stays readable. DynamoDB numbers cannot distinguish `5` from `5.0` and do not
 * support NaN or signed zero, so every non-integer scalar is stored as a tagged binary
 * value instead. Application data never uses DynamoDB binary attributes directly, which
 * makes the tag space collision-free:
 *
 * - `0x01` + IEEE-754 big-endian double (exact, including -0.0 and non-finite values)
 * - `0x02` + seconds (int64) + nanos (int32) for Firestore timestamps
 * - `0x03` + raw bytes for Firestore blobs
 * - `0x04` + latitude/longitude doubles for Firestore geo points
 */
object DynamoDocumentCodec {
    private const val TAG_DOUBLE: Byte = 0x01
    private const val TAG_TIMESTAMP: Byte = 0x02
    private const val TAG_BLOB: Byte = 0x03
    private const val TAG_GEO_POINT: Byte = 0x04

    fun encodeDocument(data: Map<String, Any?>): Map<String, AttributeValue> =
        data.entries.associate { (key, value) ->
            require(key.isNotEmpty()) { "Document field names cannot be empty" }
            key to encode(value)
        }

    fun decodeDocument(attributes: Map<String, AttributeValue>): Map<String, Any?> =
        attributes.entries.associateTo(LinkedHashMap()) { (key, value) -> key to decode(value) }

    fun encode(value: Any?): AttributeValue = when (value) {
        null -> AttributeValue.fromNul(true)
        is String -> AttributeValue.fromS(value)
        is Char -> AttributeValue.fromS(value.toString())
        is Boolean -> AttributeValue.fromBool(value)
        is Byte, is Short, is Int, is Long -> AttributeValue.fromN(value.toString())
        is Float -> tagged(TAG_DOUBLE, ByteBuffer.allocate(8).putDouble(value.toDouble()).array())
        is Double -> tagged(TAG_DOUBLE, ByteBuffer.allocate(8).putDouble(value).array())
        is Timestamp -> tagged(
            TAG_TIMESTAMP,
            ByteBuffer.allocate(12).putLong(value.seconds).putInt(value.nanos).array(),
        )
        is Blob -> tagged(TAG_BLOB, value.toBytes())
        is ByteArray -> tagged(TAG_BLOB, value)
        is GeoPoint -> tagged(
            TAG_GEO_POINT,
            ByteBuffer.allocate(16).putDouble(value.latitude).putDouble(value.longitude).array(),
        )
        is Enum<*> -> AttributeValue.fromS(value.name)
        is Map<*, *> -> AttributeValue.fromM(
            value.entries.associate { (key, entry) ->
                require(key is String && key.isNotEmpty()) { "Document map keys must be non-empty strings" }
                key to encode(entry)
            },
        )
        is Iterable<*> -> AttributeValue.fromL(value.map(::encode))
        is Array<*> -> AttributeValue.fromL(value.map(::encode))
        else -> throw IllegalArgumentException(
            "Unsupported document value type for DynamoDB: ${value::class.qualifiedName}",
        )
    }

    fun decode(value: AttributeValue): Any? = when (value.type()) {
        AttributeValue.Type.S -> value.s()
        AttributeValue.Type.N -> decodeNumber(value.n())
        AttributeValue.Type.BOOL -> value.bool()
        AttributeValue.Type.NUL -> null
        AttributeValue.Type.M -> value.m().entries.associateTo(LinkedHashMap()) { (key, entry) -> key to decode(entry) }
        AttributeValue.Type.L -> value.l().mapTo(ArrayList(), ::decode)
        AttributeValue.Type.B -> decodeTagged(value.b().asByteArray())
        else -> throw IllegalArgumentException("Unsupported DynamoDB attribute type in a document: ${value.type()}")
    }

    private fun decodeNumber(text: String): Any {
        text.toLongOrNull()?.let { return it }
        // Only the codec writes numbers, and it writes integers only. Accept wider
        // integral numbers written by hand but never silently reinterpret a decimal.
        val decimal = BigDecimal(text)
        return runCatching { decimal.longValueExact() }.getOrElse {
            throw IllegalArgumentException("DynamoDB document number is not an exact 64-bit integer")
        }
    }

    private fun tagged(tag: Byte, payload: ByteArray): AttributeValue {
        val bytes = ByteArray(payload.size + 1)
        bytes[0] = tag
        payload.copyInto(bytes, destinationOffset = 1)
        return AttributeValue.fromB(SdkBytes.fromByteArrayUnsafe(bytes))
    }

    private fun decodeTagged(bytes: ByteArray): Any {
        require(bytes.isNotEmpty()) { "Tagged DynamoDB document value is empty" }
        val payload = ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        return when (bytes[0]) {
            TAG_DOUBLE -> {
                require(bytes.size == 9) { "Invalid tagged double" }
                payload.double
            }
            TAG_TIMESTAMP -> {
                require(bytes.size == 13) { "Invalid tagged timestamp" }
                Timestamp.ofTimeSecondsAndNanos(payload.long, payload.int)
            }
            TAG_BLOB -> Blob.fromBytes(bytes.copyOfRange(1, bytes.size))
            TAG_GEO_POINT -> {
                require(bytes.size == 17) { "Invalid tagged geo point" }
                GeoPoint(payload.double, payload.double)
            }
            else -> throw IllegalArgumentException("Unknown tagged DynamoDB document value ${bytes[0]}")
        }
    }
}
