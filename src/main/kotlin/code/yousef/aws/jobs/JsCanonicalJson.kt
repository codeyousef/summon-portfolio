package code.yousef.aws.jobs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Byte-for-byte port of the edge worker's `canonicalJson` (cloudflare/portfolio-edge/src/contracts.ts).
 *
 * The FinOps connectors derive immutable entry ids, source hashes and import-run ids from
 * these strings, so the Kotlin port must reproduce the TypeScript output exactly or replays
 * after the migration would conflict with records the Worker already wrote:
 *
 * - object keys sorted with `String.prototype.localeCompare` (ICU root collation),
 * - strings and keys escaped like `JSON.stringify`,
 * - numbers formatted like ECMAScript `Number.prototype.toString`.
 */
object JsCanonicalJson {
    fun stringify(value: JsonElement): String = buildString { appendCanonical(value) }

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun StringBuilder.appendCanonical(value: JsonElement) {
        when (value) {
            JsonNull -> append("null")
            is JsonPrimitive -> when {
                value.isString -> appendJsString(value.content)
                value.content == "true" || value.content == "false" -> append(value.content)
                else -> append(jsNumber(value.content.toDouble()))
            }
            is JsonArray -> {
                append('[')
                value.forEachIndexed { index, element ->
                    if (index > 0) append(',')
                    appendCanonical(element)
                }
                append(']')
            }
            is JsonObject -> {
                append('{')
                value.entries.sortedWith { left, right -> localeCompare(left.key, right.key) }
                    .forEachIndexed { index, (key, element) ->
                        if (index > 0) append(',')
                        appendJsString(key)
                        append(':')
                        appendCanonical(element)
                    }
                append('}')
            }
        }
    }

    /** `JSON.stringify(string)`, including well-formed lone-surrogate escapes. */
    internal fun StringBuilder.appendJsString(value: String) {
        append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\b' -> append("\\b")
                character == '\u000C' -> append("\\f")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\t' -> append("\\t")
                character.code < 0x20 -> append("\\u").append(character.code.toString(16).padStart(4, '0'))
                character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                    append(character).append(value[index + 1])
                    index++
                }
                character.isSurrogate() -> append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else -> append(character)
            }
            index++
        }
        append('"')
    }

    /** ECMAScript Number::toString(10) for a finite double. */
    fun jsNumber(value: Double): String {
        require(value.isFinite()) { "payload contains a non-finite number" }
        if (value == 0.0) return "0"
        val decimal = shortestDecimal(Math.abs(value))
        val digits = decimal.unscaledValue().toString()
        val k = digits.length
        val n = k - decimal.scale()
        val body = when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val exponent = n - 1
                val sign = if (exponent < 0) "-" else "+"
                val mantissa = if (k == 1) digits else digits[0] + "." + digits.substring(1)
                "${mantissa}e$sign${Math.abs(exponent)}"
            }
        }
        return if (value < 0) "-$body" else body
    }

    /**
     * The decimal with the fewest significant digits that round-trips to [value], choosing the
     * closest such decimal (ECMAScript Number::toString step 5). Java's Double.toString keeps a
     * second digit in some cases (for example 4.9E-324), so it is not used directly.
     */
    private fun shortestDecimal(value: Double): BigDecimal {
        val exact = BigDecimal(value)
        for (precision in 1..17) {
            val candidate = exact.round(java.math.MathContext(precision, java.math.RoundingMode.HALF_EVEN))
            if (candidate.toDouble() == value) return candidate.stripTrailingZeros()
        }
        return BigDecimal(java.lang.Double.toString(value)).stripTrailingZeros()
    }

    /**
     * ICU root-collation order (what V8's default `localeCompare` uses) for ASCII text:
     * punctuation and symbols, then digits, then letters compared case-insensitively, with
     * lowercase before uppercase as the tertiary tie-break. Non-ASCII characters sort after
     * ASCII letters by code point, which is exact for every key the connectors hash.
     */
    fun localeCompare(left: String, right: String): Int {
        val primary = comparePrimary(left, right)
        if (primary != 0) return primary
        val length = minOf(left.length, right.length)
        for (index in 0 until length) {
            val a = left[index]
            val b = right[index]
            if (a != b && a.lowercaseChar() == b.lowercaseChar()) return if (a.isLowerCase()) -1 else 1
        }
        return left.compareTo(right)
    }

    private fun comparePrimary(left: String, right: String): Int {
        val length = minOf(left.length, right.length)
        for (index in 0 until length) {
            val difference = primaryWeight(left[index]).compareTo(primaryWeight(right[index]))
            if (difference != 0) return difference
        }
        return left.length.compareTo(right.length)
    }

    private const val ASCII_SYMBOL_ORDER = "\t\n\u000B\u000C\r _-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$0123456789"

    private fun primaryWeight(character: Char): Int {
        val symbol = ASCII_SYMBOL_ORDER.indexOf(character)
        if (symbol >= 0) return symbol
        if (character in 'a'..'z') return 100 + (character - 'a')
        if (character in 'A'..'Z') return 100 + (character - 'A')
        return 1_000 + character.code
    }
}
