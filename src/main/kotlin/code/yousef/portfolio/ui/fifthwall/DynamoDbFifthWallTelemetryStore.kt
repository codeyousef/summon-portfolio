package code.yousef.portfolio.ui.fifthwall

import code.yousef.aws.dynamodb.DynamoDbPortfolioDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Durable telemetry in the DynamoDB documents table (collection [COLLECTION], one document
 * per telemetry session). Each merge is an atomic version-checked read-modify-write with the
 * same merge rules as [FileFifthWallTelemetryStore]. The session record is stored as its JSON
 * encoding so it round-trips exactly. DynamoDB items are capped at 400 KB, so only the most
 * recent [maxEventsPerSession] events are retained per session.
 */
internal class DynamoDbFifthWallTelemetryStore(
    private val store: DynamoDbPortfolioDocumentStore,
    private val maxEventsPerSession: Int = 1_000,
) : FifthWallTelemetryStore {
    private val compactJson = Json { encodeDefaults = true }

    override suspend fun merge(payload: FifthWallTelemetryPayload) {
        require(TELEMETRY_SESSION_ID.matches(payload.sessionId)) { "Invalid Fifth Wall telemetry session id" }
        withContext(Dispatchers.IO) {
            store.update(COLLECTION, payload.sessionId) { current -> mergedDocument(current, payload) }
        }
    }

    internal fun mergedDocument(current: Map<String, Any?>?, payload: FifthWallTelemetryPayload): Map<String, Any?> {
        val existing = (current?.get("record") as? String)?.let { encoded ->
            runCatching { FifthWallTelemetryJson.decodeFromString<FifthWallTelemetrySessionRecord>(encoded) }.getOrNull()
        }
        val mergedEvents = (existing?.events.orEmpty() + payload.events)
            .distinctBy { it.id }
            .sortedBy { it.timestampMs }
            .takeLast(maxEventsPerSession)
        val record = FifthWallTelemetrySessionRecord(
            sessionId = payload.sessionId,
            lastRevision = maxOf(existing?.lastRevision ?: 0, payload.revision),
            updatedAtMs = payload.summary.generatedAtMs,
            events = mergedEvents,
            summary = payload.summary,
        )
        return mapOf(
            "sessionId" to record.sessionId,
            "lastRevision" to record.lastRevision.toLong(),
            "updatedAtMs" to record.updatedAtMs,
            "record" to compactJson.encodeToString(record),
        )
    }

    companion object {
        const val COLLECTION = "fifth_wall_telemetry"
        private val TELEMETRY_SESSION_ID = Regex("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")
    }
}
