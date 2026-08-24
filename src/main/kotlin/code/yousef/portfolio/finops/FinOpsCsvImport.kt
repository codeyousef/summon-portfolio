package code.yousef.portfolio.finops

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FinOpsCsvImportResult(
    val rows: Int,
    val inserted: Int,
    val duplicates: Int,
)

private val REQUIRED_CSV_COLUMNS = setOf(
    "source_record_id",
    "direction",
    "amount",
    "currency",
    "usd_micros",
    "incurred_at",
    "project",
    "environment",
    "vendor",
    "service",
    "status",
    "reconciliation_key",
)
private val OPTIONAL_CSV_COLUMNS = setOf("sku")

fun parseManualFinOpsCsv(value: String): List<ManualFinOpsEntryRequest> {
    require('\u0000' !in value) { "CSV cannot contain NUL bytes" }
    val rows = parseCsvRows(value)
    require(rows.isNotEmpty()) { "CSV is empty" }
    val headers = rows.first().map { it.trim().lowercase() }
    require(headers.size == headers.toSet().size) { "CSV headers must be unique" }
    require(headers.containsAll(REQUIRED_CSV_COLUMNS)) {
        "CSV is missing required columns: ${(REQUIRED_CSV_COLUMNS - headers.toSet()).sorted().joinToString()}"
    }
    require(headers.all { it in REQUIRED_CSV_COLUMNS || it in OPTIONAL_CSV_COLUMNS }) {
        "CSV contains unsupported columns"
    }
    require(rows.size <= 5_001) { "CSV supports at most 5000 data rows" }
    return rows.drop(1).filterNot { row -> row.all(String::isBlank) }.mapIndexed { index, row ->
        require(row.size == headers.size) { "CSV row ${index + 2} has the wrong number of columns" }
        val fields = headers.zip(row.map(String::trim)).toMap()
        val amount = MoneyAmount.parse(fields.required("currency"), fields.required("amount"))
        val request = ManualFinOpsEntryRequest(
            sourceRecordId = fields.required("source_record_id"),
            direction = enumValue<FinOpsDirection>(fields.required("direction")),
            amount = amount,
            usdMicros = fields.required("usd_micros").toLongOrNull()
                ?: throw IllegalArgumentException("CSV row ${index + 2} has invalid usd_micros"),
            incurredAt = parseCsvTimestamp(fields.required("incurred_at"), index + 2),
            project = fields.required("project"),
            environment = fields.required("environment"),
            vendor = fields.required("vendor"),
            service = fields.required("service"),
            sku = fields["sku"]?.takeIf(String::isNotBlank),
            status = enumValue<FinOpsStatus>(fields.required("status")),
            reconciliationKey = fields.required("reconciliation_key"),
            sourceHash = "0".repeat(64),
        )
        request.copy(sourceHash = csvRequestHash(request))
    }
}

private inline fun <reified T : Enum<T>> enumValue(value: String): T = runCatching {
    enumValueOf<T>(value.uppercase())
}.getOrElse { throw IllegalArgumentException("invalid ${T::class.simpleName} value '$value'") }

private fun parseCsvTimestamp(value: String, row: Int): Long = value.toLongOrNull()
    ?: runCatching { Instant.parse(value).toEpochMilli() }
        .getOrElse { throw IllegalArgumentException("CSV row $row has invalid incurred_at") }

private fun Map<String, String>.required(name: String): String = requireNotNull(this[name]?.takeIf(String::isNotBlank)) {
    "CSV field $name is required"
}

private fun csvRequestHash(request: ManualFinOpsEntryRequest): String = sha256Hex(
    listOf(
        request.sourceRecordId,
        request.direction.name,
        request.amount.currency,
        request.amount.minorUnits.toString(),
        request.amount.scale.toString(),
        request.usdMicros.toString(),
        request.incurredAt.toString(),
        request.project,
        request.environment,
        request.vendor,
        request.service,
        request.sku.orEmpty(),
        request.status.name,
        request.reconciliationKey,
    ).joinToString(separator = "") { field -> "${field.length}:$field" },
)

private fun parseCsvRows(value: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var afterQuote = false
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            quoted && char == '"' && index + 1 < value.length && value[index + 1] == '"' -> {
                field.append('"')
                index++
            }
            quoted && char == '"' -> {
                quoted = false
                afterQuote = true
            }
            quoted -> field.append(char)
            afterQuote && char == ',' -> {
                row += field.toString()
                field.clear()
                afterQuote = false
            }
            afterQuote && (char == '\n' || char == '\r') -> {
                row += field.toString()
                field.clear()
                rows += row
                row = mutableListOf()
                afterQuote = false
                if (char == '\r' && index + 1 < value.length && value[index + 1] == '\n') index++
            }
            afterQuote && char.isWhitespace() -> Unit
            afterQuote -> throw IllegalArgumentException("unexpected character after quoted CSV field")
            char == '"' && field.isEmpty() -> quoted = true
            char == '"' -> throw IllegalArgumentException("quote must begin a CSV field")
            char == ',' -> {
                row += field.toString()
                field.clear()
            }
            char == '\n' || char == '\r' -> {
                row += field.toString()
                field.clear()
                rows += row
                row = mutableListOf()
                if (char == '\r' && index + 1 < value.length && value[index + 1] == '\n') index++
            }
            else -> field.append(char)
        }
        index++
    }
    require(!quoted) { "unterminated quoted CSV field" }
    if (field.isNotEmpty() || row.isNotEmpty() || value.endsWith(',')) {
        row += field.toString()
        rows += row
    }
    return rows
}
