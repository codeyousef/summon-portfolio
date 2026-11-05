package code.yousef.firestore.migration

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

@JvmInline
value class AuthorityEpoch(val value: Long) : Comparable<AuthorityEpoch> {
    init {
        require(value > 0) { "Authority epochs must be positive" }
    }

    override fun compareTo(other: AuthorityEpoch): Int = value.compareTo(other.value)

    companion object {
        val SOURCE = AuthorityEpoch(1)
        val TARGET = AuthorityEpoch(2)
    }
}

enum class MutationOperation {
    UPSERT,
    MERGE,
    ACCUMULATE,
    DELETE,
}

enum class MirrorState {
    NOT_REQUIRED,
    PENDING,
    MIRRORED,
}

data class MutationEnvelope(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val expectedRevision: Long,
    val authorityEpoch: AuthorityEpoch,
    val occurredAt: Instant,
    val payloadSha256: String,
    val payload: Map<String, Any?>,
    val operation: MutationOperation,
) {
    init {
        require(id.isNotBlank()) { "Mutation id cannot be blank" }
        require(aggregateType.isNotBlank()) { "Mutation aggregateType cannot be blank" }
        require(aggregateId.isNotBlank()) { "Mutation aggregateId cannot be blank" }
        require(expectedRevision >= ANY_REVISION) {
            "Mutation expectedRevision must be $ANY_REVISION or a non-negative revision"
        }
        require(SHA_256.matches(payloadSha256)) {
            "Mutation payloadSha256 must be a lowercase SHA-256 digest"
        }
        require(payloadSha256 == MutationPayloadHash.sha256(payload)) {
            "Mutation payload does not match payloadSha256"
        }
        require(operation != MutationOperation.DELETE || payload.isEmpty()) {
            "Delete mutations cannot contain a payload"
        }
        if (operation == MutationOperation.ACCUMULATE) validateAccumulatePayload(payload)
    }

    companion object {
        const val ANY_REVISION: Long = -1

        fun create(
            id: String,
            aggregateType: String,
            aggregateId: String,
            expectedRevision: Long = ANY_REVISION,
            authorityEpoch: AuthorityEpoch,
            occurredAt: Instant,
            payload: Map<String, Any?>,
            operation: MutationOperation,
        ): MutationEnvelope = MutationEnvelope(
            id = id,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            expectedRevision = expectedRevision,
            authorityEpoch = authorityEpoch,
            occurredAt = occurredAt,
            payloadSha256 = MutationPayloadHash.sha256(payload),
            payload = payload,
            operation = operation,
        )
    }
}

private fun validateAccumulatePayload(payload: Map<String, Any?>) {
    require(payload.keys == setOf("dimensions", "increments")) {
        "Accumulate mutations require only dimensions and increments"
    }
    val dimensions = payload["dimensions"] as? Map<*, *> ?: error("Accumulate dimensions must be a map")
    val increments = payload["increments"] as? Map<*, *> ?: error("Accumulate increments must be a map")
    require(dimensions.isNotEmpty() && increments.isNotEmpty()) { "Accumulate maps cannot be empty" }
    require(dimensions.all { (key, value) -> key is String && (value is String || value is Long) && key.isNotBlank() }) {
        "Accumulate dimensions must be stable String or Long fields"
    }
    require(increments.all { (key, value) -> key is String && value is Long && key.isNotBlank() }) {
        "Accumulate increments must be Long fields"
    }
}

data class MutationReceipt(
    val id: String,
    val committedEpoch: AuthorityEpoch,
    val newRevision: Long,
    val firestoreCommitTime: Instant,
    val mirrorState: MirrorState,
)

object MutationPayloadHash {
    fun sha256(payload: Map<String, Any?>): String {
        val bytes = canonicalValue(payload).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> quoted(value)
        is Char -> quoted(value.toString())
        is Boolean -> value.toString()
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> canonicalDecimal(value.toDouble())
        is Double -> canonicalDecimal(value)
        is BigDecimal -> value.stripTrailingZeros().toPlainString()
        is Instant -> quoted(value.toString())
        is Enum<*> -> quoted(value.name)
        is Map<*, *> -> value.entries
            .map { (key, entryValue) ->
                require(key is String) { "Mutation payload map keys must be strings" }
                key to entryValue
            }
            .sortedBy { it.first }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, entryValue) ->
                "${quoted(key)}:${canonicalValue(entryValue)}"
            }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalValue(it)
        }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalValue(it)
        }
        else -> throw IllegalArgumentException(
            "Unsupported mutation payload value type: ${value::class.qualifiedName}",
        )
    }

    private fun canonicalDecimal(value: Double): String {
        require(value.isFinite()) { "Mutation payload numbers must be finite" }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    private fun quoted(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

private val SHA_256 = Regex("[0-9a-f]{64}")
