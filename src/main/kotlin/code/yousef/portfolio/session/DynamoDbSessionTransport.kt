package code.yousef.portfolio.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import java.time.Clock

/**
 * DynamoDB implementation of the session revision contract formerly served by the
 * per-session Durable Object (`/v1/session/read|compare-and-set`, `DELETE /v1/session`).
 *
 * Table `PORTFOLIO_DYNAMODB_SESSIONS_TABLE`: partition key `pk` (S) = "session:<id>",
 * `revision` (N), `values` (M of S), `expires_at` (N, epoch seconds, DynamoDB TTL).
 * DynamoDB deletes expired items lazily, so every read treats `expires_at <= now` as absent.
 * Each successful write slides the expiry to now + [ttlSeconds], exactly as the Durable
 * Object did, and the compare-and-set is a single conditional put.
 */
internal class DynamoDbSessionTransport(
    private val client: DynamoDbClient,
    private val tableName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val ttlSeconds: Long = SESSION_TTL_SECONDS,
) : EdgeSessionTransport {
    init {
        require(tableName.matches(Regex("^[A-Za-z0-9_.-]{3,255}$"))) { "Invalid DynamoDB sessions table name" }
        require(ttlSeconds in 60..30L * 86_400) { "Session TTL is out of range" }
    }

    override suspend fun read(sessionId: String): EdgeSessionRead = withContext(Dispatchers.IO) {
        val nowSeconds = clock.instant().epochSecond
        // A forged or legacy cookie is simply an unknown session, never a server error.
        if (!SESSION_ID.matches(sessionId)) return@withContext decode(null, nowSeconds)
        val item = client.getItem { request ->
            request.tableName(tableName).key(sessionKey(sessionId)).consistentRead(true)
        }.takeIf { it.hasItem() }?.item()
        decode(item, nowSeconds)
    }

    override suspend fun compareAndSet(
        sessionId: String,
        update: EdgeSessionWrite,
    ): EdgeSessionWriteResult = withContext(Dispatchers.IO) {
        require(update.expectedRevision >= 0) { "Session revision cannot be negative" }
        require(update.values.size <= MAX_VALUES) { "Session contains too many values" }
        val encodedBytes = update.values.entries.sumOf { (key, value) ->
            key.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
        }
        require(encodedBytes <= MAX_SESSION_BYTES) { "Session exceeds $MAX_SESSION_BYTES bytes" }
        val nowSeconds = clock.instant().epochSecond
        val nextRevision = update.expectedRevision + 1
        val condition = if (update.expectedRevision == 0L) {
            "attribute_not_exists(#pk) OR #expires <= :now"
        } else {
            "#revision = :expected AND #expires > :now"
        }
        val values = buildMap {
            put(":now", AttributeValue.fromN(nowSeconds.toString()))
            if (update.expectedRevision != 0L) put(":expected", AttributeValue.fromN(update.expectedRevision.toString()))
        }
        val names = buildMap {
            put("#expires", EXPIRES_AT)
            if (update.expectedRevision == 0L) put("#pk", PK) else put("#revision", REVISION)
        }
        try {
            client.putItem { request ->
                request.tableName(tableName)
                    .item(
                        sessionKey(sessionId) + mapOf(
                            REVISION to AttributeValue.fromN(nextRevision.toString()),
                            VALUES to AttributeValue.fromM(update.values.mapValues { AttributeValue.fromS(it.value) }),
                            EXPIRES_AT to AttributeValue.fromN((nowSeconds + ttlSeconds).toString()),
                        ),
                    )
                    .conditionExpression(condition)
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            }
            EdgeSessionWriteResult.Committed(nextRevision)
        } catch (failure: ConditionalCheckFailedException) {
            val current = failure.item().takeIf { failure.hasItem() }
            EdgeSessionWriteResult.Conflict(decode(current, nowSeconds).revision)
        }
    }

    override suspend fun delete(sessionId: String) {
        withContext(Dispatchers.IO) {
            client.deleteItem { it.tableName(tableName).key(sessionKey(sessionId)) }
        }
    }

    private fun decode(item: Map<String, AttributeValue>?, nowSeconds: Long): EdgeSessionRead {
        val expiresAt = item?.get(EXPIRES_AT)?.n()?.toLongOrNull()
        if (item == null || item.isEmpty() || expiresAt == null || expiresAt <= nowSeconds) {
            return EdgeSessionRead(
                exists = false,
                revision = 0,
                expiresAt = (nowSeconds + ttlSeconds) * 1_000,
                values = emptyMap(),
            )
        }
        return EdgeSessionRead(
            exists = true,
            revision = requireNotNull(item[REVISION]?.n()?.toLongOrNull()) { "Session item is missing its revision" },
            expiresAt = expiresAt * 1_000,
            values = item[VALUES]?.m().orEmpty().mapValues { (_, value) -> requireNotNull(value.s()) },
        )
    }

    private fun sessionKey(sessionId: String): Map<String, AttributeValue> {
        require(SESSION_ID.matches(sessionId)) { "Invalid portfolio session id" }
        return mapOf(PK to AttributeValue.fromS("session:$sessionId"))
    }

    companion object {
        const val PK = "pk"
        const val REVISION = "revision"
        const val VALUES = "values"
        const val EXPIRES_AT = "expires_at"
        const val SESSION_TTL_SECONDS = 24L * 60 * 60
        private const val MAX_SESSION_BYTES = 24 * 1024
        private const val MAX_VALUES = 32
        private val SESSION_ID = Regex("^[A-Za-z0-9_-]{32}$")
    }
}
