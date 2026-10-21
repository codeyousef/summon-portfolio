package code.yousef.aws.dynamodb

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreDocument
import code.yousef.firestore.PortfolioFirestorePage
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.migration.IdempotencyConflictException
import code.yousef.firestore.migration.MutationPayloadHash
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes
import software.amazon.awssdk.services.dynamodb.model.Put
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import java.time.Clock
import java.util.HexFormat

/**
 * Single-table DynamoDB implementation of the generic Portfolio document store.
 *
 * Key schema (table `PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE`):
 * - `pk` (S, partition) = collection name, `sk` (S, sort) = document id
 * - `doc` (M) = the document, encoded by [DynamoDocumentCodec]
 * - `ver` (N) = optimistic-concurrency version, incremented by every write
 *
 * Sparse global secondary indexes (projection ALL):
 * - [ORDER_INDEX]: `opk` (S) = "<collection>|<field>", `osk` (S) = order-preserving
 *   encoding of the field value + "#" + document id. Serves `rangePage`/`stringPage`
 *   for the collections in [ORDER_FIELDS].
 * - [LOOKUP_INDEX]: `lpk` (S) = "<collection>|<field>|<value>", `sk` (S). Serves the
 *   equality/`whereIn` call sites listed in [LOOKUP_FIELDS].
 *
 * Index reads are eventually consistent (as are all DynamoDB GSIs); point reads and
 * collection listings use strongly consistent reads. Every other query shape falls back
 * to a consistent collection read with in-memory Firestore-equivalent filtering.
 *
 * `insertIfAbsent` is a conditional create. `accumulate` commits the document and a mutation
 * receipt in one `TransactWriteItems` call, mirroring the Firestore receipt transaction.
 * Receipt fingerprints are deterministic over the collection, id, operation and payload,
 * so replaying the same accumulation is a no-op and reusing an id for different content fails.
 */
class DynamoDbPortfolioDocumentStore(
    private val client: DynamoDbClient,
    val tableName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val maxConflictRetries: Int = 8,
) : PortfolioFirestoreStore {
    private val log = LoggerFactory.getLogger(DynamoDbPortfolioDocumentStore::class.java)

    init {
        require(TABLE_NAME.matches(tableName)) { "Invalid DynamoDB documents table name" }
        require(maxConflictRetries in 1..32)
    }

    /** DynamoDB is the sole authority: there is no mirror to replicate to. */
    override val writeMode: FirestoreWriteMode = FirestoreWriteMode.SOURCE

    override fun get(collection: String, documentId: String): PortfolioFirestoreDocument? =
        getItem(collection, documentId)?.toDocument()

    override fun getMany(collection: String, documentIds: Collection<String>): List<PortfolioFirestoreDocument> {
        val ids = documentIds.distinct()
        if (ids.isEmpty()) return emptyList()
        ids.forEach { requireKey(collection, it) }
        val found = HashMap<String, PortfolioFirestoreDocument>()
        ids.chunked(100).forEach { chunk ->
            var pending: Map<String, KeysAndAttributes> = mapOf(
                tableName to KeysAndAttributes.builder()
                    .keys(chunk.map { key(collection, it) })
                    .consistentRead(true)
                    .build(),
            )
            var attempt = 0
            while (pending.isNotEmpty()) {
                val response = client.batchGetItem(BatchGetItemRequest.builder().requestItems(pending).build())
                response.responses()[tableName].orEmpty().forEach { item ->
                    val document = item.toDocument()
                    found[document.id] = document
                }
                pending = response.unprocessedKeys().filterValues { it.hasKeys() && it.keys().isNotEmpty() }
                if (pending.isNotEmpty()) backoff(attempt++)
            }
        }
        // Preserve the caller's id order, matching Firestore getAll.
        return ids.mapNotNull(found::get)
    }

    override fun list(collection: String): List<PortfolioFirestoreDocument> {
        requireCollection(collection)
        return queryAll(
            QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(mapOf("#pk" to PK))
                .expressionAttributeValues(mapOf(":pk" to AttributeValue.fromS(collection)))
                .consistentRead(true)
                .build(),
        ).map { it.toDocument() }
    }

    override fun whereEqualTo(collection: String, filters: Map<String, Any>): List<PortfolioFirestoreDocument> {
        require(filters.isNotEmpty()) { "At least one equality filter is required" }
        val lookupField = LOOKUP_FIELDS[collection]
        val lookupValue = lookupField?.let(filters::get) as? String
        val candidates = if (lookupField != null && lookupValue != null) {
            lookup(collection, lookupField, lookupValue)
        } else {
            list(collection)
        }
        return candidates.filter { document -> filters.all { (field, value) -> firestoreEquals(document.data[field], value) } }
    }

    override fun whereIn(collection: String, field: String, values: List<Any>): List<PortfolioFirestoreDocument> {
        require(values.isNotEmpty()) { "At least one value is required" }
        if (LOOKUP_FIELDS[collection] == field && values.all { it is String }) {
            return values.distinct().flatMap { lookup(collection, field, it as String) }
                .distinctBy(PortfolioFirestoreDocument::id)
                .filter { document -> values.any { firestoreEquals(document.data[field], it) } }
        }
        return list(collection).filter { document -> values.any { firestoreEquals(document.data[field], it) } }
    }

    override fun whereLessThan(collection: String, field: String, value: Any): List<PortfolioFirestoreDocument> =
        list(collection).filter { document ->
            val comparison = firestoreCompare(document.data[field], value)
            comparison != null && comparison < 0
        }

    override fun rangePage(
        collection: String,
        rangeField: String,
        fromInclusive: Long,
        toExclusive: Long,
        equalityFilters: Map<String, Any>,
        limit: Int,
        afterValue: Long?,
        afterDocumentId: String?,
        descending: Boolean,
    ): PortfolioFirestorePage {
        require(limit in 1..500)
        require((afterValue == null) == (afterDocumentId == null)) { "Both cursor values are required" }
        if (ORDER_FIELDS[collection] != rangeField) {
            return super.rangePage(
                collection, rangeField, fromInclusive, toExclusive, equalityFilters, limit,
                afterValue, afterDocumentId, descending,
            )
        }
        if (toExclusive <= fromInclusive) return PortfolioFirestorePage(emptyList(), hasMore = false)
        var lower = orderKey(fromInclusive)
        var upper = orderKey(toExclusive - 1) + UPPER_SENTINEL
        val cursorKey = if (afterValue != null && afterDocumentId != null) {
            orderKey(afterValue) + SEPARATOR + afterDocumentId
        } else {
            null
        }
        if (cursorKey != null) {
            if (descending) {
                if (cursorKey < upper) upper = cursorKey
            } else if (cursorKey > lower) {
                lower = cursorKey
            }
            if (lower > upper) return PortfolioFirestorePage(emptyList(), hasMore = false)
        }
        return orderedPage(collection, rangeField, lower, upper, cursorKey, equalityFilters, limit, descending)
    }

    override fun stringPage(
        collection: String,
        orderField: String,
        limit: Int,
        afterValue: String?,
        afterDocumentId: String?,
        descending: Boolean,
    ): PortfolioFirestorePage {
        require(limit in 1..100)
        require((afterValue == null) == (afterDocumentId == null)) { "Both cursor values are required" }
        if (ORDER_FIELDS[collection] != orderField) {
            return super.stringPage(collection, orderField, limit, afterValue, afterDocumentId, descending)
        }
        var lower = STRING_TAG
        var upper = STRING_TAG + HEX_UPPER_BOUND
        val cursorKey = if (afterValue != null && afterDocumentId != null) {
            orderKey(afterValue) + SEPARATOR + afterDocumentId
        } else {
            null
        }
        if (cursorKey != null) {
            if (descending) upper = minOf(upper, cursorKey) else lower = maxOf(lower, cursorKey)
        }
        return orderedPage(collection, orderField, lower, upper, cursorKey, emptyMap(), limit, descending)
    }

    private fun orderedPage(
        collection: String,
        field: String,
        lower: String,
        upper: String,
        exclusiveCursor: String?,
        equalityFilters: Map<String, Any>,
        limit: Int,
        descending: Boolean,
    ): PortfolioFirestorePage {
        val matches = ArrayList<PortfolioFirestoreDocument>(limit + 1)
        var startKey: Map<String, AttributeValue>? = null
        do {
            val response = client.query(
                QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(ORDER_INDEX)
                    .keyConditionExpression("#opk = :opk AND #osk BETWEEN :lower AND :upper")
                    .expressionAttributeNames(mapOf("#opk" to OPK, "#osk" to OSK))
                    .expressionAttributeValues(
                        mapOf(
                            ":opk" to AttributeValue.fromS(orderPartition(collection, field)),
                            ":lower" to AttributeValue.fromS(lower),
                            ":upper" to AttributeValue.fromS(upper),
                        ),
                    )
                    .scanIndexForward(!descending)
                    .limit(maxOf(limit + 1, 100))
                    .exclusiveStartKey(startKey)
                    .build(),
            )
            for (item in response.items()) {
                if (exclusiveCursor != null && item[OSK]?.s() == exclusiveCursor) continue
                val document = item.toDocument()
                if (equalityFilters.all { (name, expected) -> firestoreEquals(document.data[name], expected) }) {
                    matches += document
                    if (matches.size > limit) break
                }
            }
            startKey = response.lastEvaluatedKey().takeIf { response.hasLastEvaluatedKey() && it.isNotEmpty() }
        } while (matches.size <= limit && startKey != null)
        return PortfolioFirestorePage(matches.take(limit), hasMore = matches.size > limit)
    }

    override fun upsert(collection: String, documentId: String, data: Map<String, Any?>) {
        requireKey(collection, documentId)
        val index = indexAttributes(collection, documentId, data)
        val names = linkedMapOf("#doc" to DOC, "#ver" to VER)
        val values = linkedMapOf(
            ":doc" to AttributeValue.fromM(DynamoDocumentCodec.encodeDocument(data)),
            ":zero" to AttributeValue.fromN("0"),
            ":one" to AttributeValue.fromN("1"),
        )
        val sets = mutableListOf("#doc = :doc", "#ver = if_not_exists(#ver, :zero) + :one")
        val removes = mutableListOf<String>()
        INDEX_ATTRIBUTES.forEach { attribute ->
            val placeholder = "#$attribute"
            names[placeholder] = attribute
            val value = index[attribute]
            if (value != null) {
                values[":$attribute"] = AttributeValue.fromS(value)
                sets += "$placeholder = :$attribute"
            } else {
                removes += placeholder
            }
        }
        val expression = buildString {
            append("SET ").append(sets.joinToString(", "))
            if (removes.isNotEmpty()) append(" REMOVE ").append(removes.joinToString(", "))
        }
        client.updateItem { request ->
            request.tableName(tableName)
                .key(key(collection, documentId))
                .updateExpression(expression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
        }
    }

    /**
     * Immutable create: a single conditional put. Every caller re-reads and compares the
     * existing document when this returns false, so a replay (same mutation) is reported as
     * "already present" and classified as a duplicate or conflict by the repository. Unlike
     * [accumulate], no receipt is needed because document existence is the idempotency key.
     */
    override fun insertIfAbsent(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        mutationId: String,
    ): Boolean {
        requireKey(collection, documentId)
        return try {
            client.putItem { request ->
                request.tableName(tableName)
                    .item(documentItem(collection, documentId, data, version = 1))
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(mapOf("#pk" to PK))
            }
            true
        } catch (_: ConditionalCheckFailedException) {
            false
        }
    }

    override fun merge(collection: String, documentId: String, data: Map<String, Any?>) {
        update(collection, documentId) { current -> deepMerge(current.orEmpty(), data) }
    }

    override fun accumulate(
        collection: String,
        documentId: String,
        dimensions: Map<String, Any>,
        increments: Map<String, Long>,
        mutationId: String,
    ) {
        requireKey(collection, documentId)
        require(dimensions.isNotEmpty() && increments.isNotEmpty()) { "Accumulate maps cannot be empty" }
        val fingerprint = fingerprint(
            "accumulate",
            collection,
            documentId,
            mapOf("dimensions" to dimensions, "increments" to increments),
        )
        repeat(maxConflictRetries) { attempt ->
            if (receiptFingerprint(mutationId)?.let { existing ->
                    if (existing != fingerprint) throw IdempotencyConflictException(mutationId)
                    true
                } == true
            ) {
                return
            }
            val current = getItem(collection, documentId)
            val currentData = current?.get(DOC)?.let { DynamoDocumentCodec.decodeDocument(it.m()) }.orEmpty()
            val currentVersion = current?.get(VER)?.n()?.toLong()
            val next = LinkedHashMap(currentData)
            dimensions.forEach { (field, expected) ->
                val existing = currentData[field]
                require(existing == null || existing == expected) { "Accumulate dimension $field cannot change" }
                next[field] = expected
            }
            increments.forEach { (field, delta) ->
                val before = (currentData[field] as? Number)?.toLong() ?: 0L
                next[field] = Math.addExact(before, delta)
            }
            try {
                client.transactWriteItems(
                    TransactWriteItemsRequest.builder().transactItems(
                        versionedPut(collection, documentId, next, currentVersion),
                        receiptWrite(mutationId, fingerprint, collection, documentId),
                    ).build(),
                )
                return
            } catch (cancelled: TransactionCanceledException) {
                if (!cancelled.conditionFailedAt(0) && !cancelled.conditionFailedAt(1)) throw cancelled
                backoff(attempt)
            }
        }
        error("Accumulate for $collection/$documentId remained conflicted after $maxConflictRetries attempts")
    }

    override fun delete(collection: String, documentId: String) {
        requireKey(collection, documentId)
        client.deleteItem { it.tableName(tableName).key(key(collection, documentId)) }
    }

    /**
     * Atomic read-modify-write guarded by the document version. [transform] receives the
     * current document (or null) and returns the replacement, or null to delete it.
     * It may run more than once under contention and must therefore be pure.
     */
    fun update(
        collection: String,
        documentId: String,
        transform: (Map<String, Any?>?) -> Map<String, Any?>?,
    ) {
        requireKey(collection, documentId)
        repeat(maxConflictRetries) { attempt ->
            val current = getItem(collection, documentId)
            val currentData = current?.get(DOC)?.let { DynamoDocumentCodec.decodeDocument(it.m()) }
            val currentVersion = current?.get(VER)?.n()?.toLong()
            val next = transform(currentData)
            try {
                if (next == null) {
                    if (current == null) return
                    client.deleteItem { request ->
                        request.tableName(tableName)
                            .key(key(collection, documentId))
                            .conditionExpression("#ver = :ver")
                            .expressionAttributeNames(mapOf("#ver" to VER))
                            .expressionAttributeValues(mapOf(":ver" to AttributeValue.fromN(currentVersion.toString())))
                    }
                } else {
                    val put = versionedPutModel(collection, documentId, next, currentVersion)
                    client.putItem { request ->
                        request.tableName(put.tableName())
                            .item(put.item())
                            .conditionExpression(put.conditionExpression())
                            .expressionAttributeNames(put.expressionAttributeNames())
                            .expressionAttributeValues(put.expressionAttributeValues().takeIf { it.isNotEmpty() })
                    }
                }
                return
            } catch (_: ConditionalCheckFailedException) {
                backoff(attempt)
            }
        }
        error("Update for $collection/$documentId remained conflicted after $maxConflictRetries attempts")
    }

    /** Lists every document id in [collection] with its raw encoded attributes, for parity checks. */
    fun listForParity(collection: String): Map<String, Map<String, Any?>> =
        list(collection).associate { it.id to it.data }

    /** Unconditional replacement used only by the operator backfill. */
    fun replaceForBackfill(collection: String, documentId: String, data: Map<String, Any?>) = upsert(collection, documentId, data)

    /** Cheap strongly consistent probe used by the readiness endpoint. */
    fun probe() {
        client.getItem { it.tableName(tableName).key(key(HEALTH_COLLECTION, "ready")).consistentRead(true) }
    }

    private fun lookup(collection: String, field: String, value: String): List<PortfolioFirestoreDocument> =
        queryAll(
            QueryRequest.builder()
                .tableName(tableName)
                .indexName(LOOKUP_INDEX)
                .keyConditionExpression("#lpk = :lpk")
                .expressionAttributeNames(mapOf("#lpk" to LPK))
                .expressionAttributeValues(mapOf(":lpk" to AttributeValue.fromS(lookupPartition(collection, field, value))))
                .build(),
        ).map { it.toDocument() }

    private fun queryAll(request: QueryRequest): List<Map<String, AttributeValue>> {
        val items = mutableListOf<Map<String, AttributeValue>>()
        var startKey: Map<String, AttributeValue>? = null
        do {
            val response = client.query(request.toBuilder().exclusiveStartKey(startKey).build())
            items += response.items()
            startKey = response.lastEvaluatedKey().takeIf { response.hasLastEvaluatedKey() && it.isNotEmpty() }
        } while (startKey != null)
        return items
    }

    private fun getItem(collection: String, documentId: String): Map<String, AttributeValue>? {
        requireKey(collection, documentId)
        val response = client.getItem { request ->
            request.tableName(tableName).key(key(collection, documentId)).consistentRead(true)
        }
        return response.item().takeIf { response.hasItem() && it.isNotEmpty() }
    }

    private fun versionedPut(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        currentVersion: Long?,
    ): TransactWriteItem = TransactWriteItem.builder().put(versionedPutModel(collection, documentId, data, currentVersion)).build()

    private fun versionedPutModel(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        currentVersion: Long?,
    ): Put {
        val builder = Put.builder()
            .tableName(tableName)
            .item(documentItem(collection, documentId, data, version = (currentVersion ?: 0L) + 1))
        return if (currentVersion == null) {
            builder.conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(mapOf("#pk" to PK))
                .build()
        } else {
            builder.conditionExpression("#ver = :ver")
                .expressionAttributeNames(mapOf("#ver" to VER))
                .expressionAttributeValues(mapOf(":ver" to AttributeValue.fromN(currentVersion.toString())))
                .build()
        }
    }

    private fun documentItem(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        version: Long,
    ): Map<String, AttributeValue> = buildMap {
        putAll(key(collection, documentId))
        put(DOC, AttributeValue.fromM(DynamoDocumentCodec.encodeDocument(data)))
        put(VER, AttributeValue.fromN(version.toString()))
        indexAttributes(collection, documentId, data).forEach { (name, value) -> put(name, AttributeValue.fromS(value)) }
    }

    private fun receiptWrite(
        mutationId: String,
        fingerprint: String,
        collection: String,
        documentId: String,
    ): TransactWriteItem {
        require(mutationId.isNotBlank() && mutationId.length <= 1024) { "Invalid mutation id" }
        return TransactWriteItem.builder().put(
            Put.builder()
                .tableName(tableName)
                .item(
                    mapOf(
                        PK to AttributeValue.fromS(RECEIPTS_COLLECTION),
                        SK to AttributeValue.fromS(mutationId),
                        FINGERPRINT to AttributeValue.fromS(fingerprint),
                        "collection" to AttributeValue.fromS(collection),
                        "documentId" to AttributeValue.fromS(documentId),
                        "committedAt" to AttributeValue.fromS(clock.instant().toString()),
                    ),
                )
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(mapOf("#pk" to PK))
                .build(),
        ).build()
    }

    private fun receiptFingerprint(mutationId: String): String? {
        val response = client.getItem { request ->
            request.tableName(tableName).key(key(RECEIPTS_COLLECTION, mutationId)).consistentRead(true)
        }
        return response.item().takeIf { response.hasItem() }?.get(FINGERPRINT)?.s()
    }

    private fun Map<String, AttributeValue>.toDocument(): PortfolioFirestoreDocument = PortfolioFirestoreDocument(
        id = requireNotNull(get(SK)?.s()) { "DynamoDB document item is missing its sort key" },
        data = get(DOC)?.m()?.let(DynamoDocumentCodec::decodeDocument).orEmpty(),
    )

    private fun backoff(attempt: Int) {
        Thread.sleep(minOf(1_000L, 20L shl minOf(attempt, 6)) + (0..20).random())
    }

    private fun TransactionCanceledException.conditionFailedAt(index: Int): Boolean =
        hasCancellationReasons() && cancellationReasons().getOrNull(index)?.code() == "ConditionalCheckFailed"

    private fun fingerprint(operation: String, collection: String, documentId: String, payload: Map<String, Any?>): String =
        MutationPayloadHash.sha256(
            mapOf(
                "operation" to operation,
                "collection" to collection,
                "documentId" to documentId,
                "payloadSha256" to MutationPayloadHash.sha256(payload),
            ),
        )

    companion object {
        const val PK = "pk"
        const val SK = "sk"
        const val DOC = "doc"
        const val VER = "ver"
        const val OPK = "opk"
        const val OSK = "osk"
        const val LPK = "lpk"
        const val FINGERPRINT = "fingerprint"
        const val ORDER_INDEX = "order-index"
        const val LOOKUP_INDEX = "lookup-index"
        const val RECEIPTS_COLLECTION = "_dynamo_mutation_receipts"
        const val HEALTH_COLLECTION = "_health"
        private val INDEX_ATTRIBUTES = listOf(OPK, OSK, LPK)
        private const val SEPARATOR = "#"
        private const val UPPER_SENTINEL = "$" // sorts directly after SEPARATOR
        private const val HEX_UPPER_BOUND = "g" // sorts after every lowercase hex digit
        private const val NUMBER_TAG = "n"
        private const val STRING_TAG = "s"
        private const val MAX_INDEX_KEY_BYTES = 1_024
        private val TABLE_NAME = Regex("^[A-Za-z0-9_.-]{3,255}$")
        private val COLLECTION = Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$")

        /** The single order field each collection is paged by (see FirestoreFinOpsRepository). */
        val ORDER_FIELDS: Map<String, String> = mapOf(
            PortfolioFirestoreCollections.FINOPS_COST_ENTRIES to "incurredAt",
            PortfolioFirestoreCollections.FINOPS_ALLOCATION_ENTRY_PROJECTIONS to "incurredAt",
            PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS to "dayStart",
            PortfolioFirestoreCollections.FINOPS_DAILY_ROLLUPS_V2 to "dayStart",
            PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS to "dayStart",
            PortfolioFirestoreCollections.FINOPS_USER_DAILY_ROLLUPS_V2 to "dayStart",
            PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS to "dayStart",
            PortfolioFirestoreCollections.FINOPS_RECONCILIATIONS_V2 to "dayStart",
            PortfolioFirestoreCollections.FINOPS_IMPORT_RUNS to "completedAt",
            PortfolioFirestoreCollections.FINOPS_SAMURAI_USER_INDEX to "userId",
        )

        /** Equality lookups served by the lookup index (string values only). */
        val LOOKUP_FIELDS: Map<String, String> = mapOf(
            PortfolioFirestoreCollections.FINOPS_ALLOCATIONS to "entryId",
            PortfolioFirestoreCollections.BUILDING_UNITS to "buildingId",
            PortfolioFirestoreCollections.BUILDING_LEASES to "unitId",
            PortfolioFirestoreCollections.BUILDING_PAYMENTS to "leaseId",
            PortfolioFirestoreCollections.BUILDING_PASSWORD_RESET_TOKENS to "username",
        )

        fun key(collection: String, documentId: String): Map<String, AttributeValue> = mapOf(
            PK to AttributeValue.fromS(collection),
            SK to AttributeValue.fromS(documentId),
        )

        internal fun orderPartition(collection: String, field: String): String = "$collection|$field"

        internal fun lookupPartition(collection: String, field: String, value: String): String =
            "$collection|$field|$value"

        /**
         * Order-preserving string encoding. Signed longs become 16 hex digits of the
         * sign-flipped value; strings become the hex of their UTF-8 bytes (Firestore's
         * string order). Hex digits all sort after [SEPARATOR], so a shorter string
         * still sorts before any longer string sharing its prefix.
         */
        internal fun orderKey(value: Long): String =
            NUMBER_TAG + java.lang.Long.toHexString(value xor Long.MIN_VALUE).padStart(16, '0')

        internal fun orderKey(value: String): String =
            STRING_TAG + HexFormat.of().formatHex(value.toByteArray(Charsets.UTF_8))

        internal fun indexAttributes(
            collection: String,
            documentId: String,
            data: Map<String, Any?>,
        ): Map<String, String> = buildMap {
            ORDER_FIELDS[collection]?.let { field ->
                val encoded = when (val value = data[field]) {
                    is Long, is Int, is Short, is Byte -> orderKey((value as Number).toLong())
                    is String -> orderKey(value)
                    else -> null
                }
                if (encoded != null) {
                    val sortKey = encoded + SEPARATOR + documentId
                    require(sortKey.toByteArray(Charsets.UTF_8).size <= MAX_INDEX_KEY_BYTES) {
                        "Order index key for $collection/$documentId is too long"
                    }
                    put(OPK, orderPartition(collection, field))
                    put(OSK, sortKey)
                }
            }
            LOOKUP_FIELDS[collection]?.let { field ->
                (data[field] as? String)?.let { value ->
                    val partition = lookupPartition(collection, field, value)
                    require(partition.toByteArray(Charsets.UTF_8).size <= MAX_INDEX_KEY_BYTES) {
                        "Lookup index key for $collection/$documentId is too long"
                    }
                    put(LPK, partition)
                }
            }
        }

        private fun requireCollection(collection: String) {
            require(COLLECTION.matches(collection)) { "Invalid document collection" }
        }

        private fun requireKey(collection: String, documentId: String) {
            requireCollection(collection)
            require(documentId.isNotEmpty() && documentId.toByteArray(Charsets.UTF_8).size <= 1_024) {
                "Invalid document id"
            }
        }
    }
}

/** Firestore set-with-merge semantics: nested maps merge field by field; other values replace. */
@Suppress("UNCHECKED_CAST")
internal fun deepMerge(current: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
    val result = LinkedHashMap(current)
    patch.forEach { (key, value) ->
        val existing = result[key]
        result[key] = if (value is Map<*, *> && existing is Map<*, *>) {
            deepMerge(existing as Map<String, Any?>, value as Map<String, Any?>)
        } else {
            value
        }
    }
    return result
}

/** Firestore equality: numbers compare numerically across integer/double representations. */
internal fun firestoreEquals(actual: Any?, expected: Any?): Boolean = when {
    actual is Number && expected is Number -> firestoreCompare(actual, expected) == 0
    else -> actual == expected
}

/** Firestore ordering within one type class; null when the values are not comparable. */
internal fun firestoreCompare(actual: Any?, expected: Any?): Int? = when {
    actual is Number && expected is Number -> {
        if ((actual is Double || actual is Float) || (expected is Double || expected is Float)) {
            actual.toDouble().compareTo(expected.toDouble())
        } else {
            actual.toLong().compareTo(expected.toLong())
        }
    }
    actual is String && expected is String -> compareUtf8(actual, expected)
    actual is Boolean && expected is Boolean -> actual.compareTo(expected)
    else -> null
}

private fun compareUtf8(left: String, right: String): Int {
    val a = left.toByteArray(Charsets.UTF_8)
    val b = right.toByteArray(Charsets.UTF_8)
    return java.util.Arrays.compareUnsigned(a, b)
}
