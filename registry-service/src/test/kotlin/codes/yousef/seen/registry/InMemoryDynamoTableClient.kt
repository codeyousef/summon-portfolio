package codes.yousef.seen.registry

import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Offline DynamoDB semantics for repository tests: strongly consistent reads,
 * condition evaluation, and all-or-nothing TransactWriteItems with per-item
 * cancellation reasons. Conditions are evaluated by the same typed AST the AWS
 * adapter renders into expressions.
 */
internal class InMemoryDynamoTableClient(
    private val tables: DynamoDbRegistryTables = DynamoDbRegistryTables("seen-registry-dev"),
) : DynamoTableClient {
    private val lock = Any()
    private val data = tables.all.associateWith { linkedMapOf<List<AttributeValue>, DynamoItem>() }
    val transactionCalls = mutableListOf<List<DynamoPut>>()

    /** Optional hook run inside a transaction before evaluation, used to inject contention. */
    var beforeTransaction: ((List<DynamoPut>) -> Unit)? = null

    fun items(table: String): List<DynamoItem> = synchronized(lock) { table(table).values.toList() }

    fun rawPut(table: String, item: DynamoItem) = synchronized(lock) { table(table)[keyOf(table, item)] = item }

    override fun get(table: String, key: DynamoItem): DynamoItem? = synchronized(lock) {
        table(table)[keyOf(table, key)]
    }

    override fun put(put: DynamoPut): DynamoWriteResult = synchronized(lock) {
        val key = keyOf(put.table, put.item)
        val current = table(put.table)[key]
        if (put.condition != null && !evaluate(put.condition, current)) {
            return@synchronized DynamoWriteResult.ConditionFailed(current)
        }
        table(put.table)[key] = put.item
        DynamoWriteResult.Applied(put.item)
    }

    override fun update(update: DynamoUpdate): DynamoWriteResult = synchronized(lock) {
        val key = keyOf(update.table, update.key)
        val current = table(update.table)[key]
        if (update.condition != null && !evaluate(update.condition, current)) {
            return@synchronized DynamoWriteResult.ConditionFailed(current)
        }
        val next = LinkedHashMap(current ?: update.key)
        next.putAll(update.set)
        update.remove.forEach(next::remove)
        update.increment.forEach { (name, delta) ->
            val previous = next[name]?.n()?.toLong() ?: 0L
            next[name] = numberValue(previous + delta)
        }
        table(update.table)[key] = next
        DynamoWriteResult.Applied(next)
    }

    override fun query(table: String, partitionValue: String): List<DynamoItem> = synchronized(lock) {
        table(table).values.filter { it[PK]?.s() == partitionValue }
            .sortedBy { it[SK]?.s().orEmpty() }
    }

    override fun scan(table: String): List<DynamoItem> = synchronized(lock) { table(table).values.toList() }

    override fun transactWrite(puts: List<DynamoPut>): DynamoTransactionResult {
        beforeTransaction?.invoke(puts)
        return synchronized(lock) {
            transactionCalls += puts
            val keys = puts.map { it.table to keyOf(it.table, it.item) }
            require(keys.toSet().size == keys.size) { "DynamoDB transactions cannot touch one item twice" }
            val failed = puts.withIndex().filter { (_, put) ->
                put.condition != null && !evaluate(put.condition, table(put.table)[keyOf(put.table, put.item)])
            }.mapTo(mutableSetOf()) { it.index }
            if (failed.isNotEmpty()) return@synchronized DynamoTransactionResult.Cancelled(failed, conflicted = false)
            puts.forEach { table(it.table)[keyOf(it.table, it.item)] = it.item }
            DynamoTransactionResult.Applied
        }
    }

    private fun table(name: String) = requireNotNull(data[name]) { "Unknown DynamoDB table $name" }

    private fun keyOf(table: String, item: DynamoItem): List<AttributeValue> =
        requireNotNull(tables.keySchema[table]).map { attribute ->
            requireNotNull(item[attribute]) { "Item for $table is missing key attribute $attribute" }
        }

    private fun evaluate(condition: DynamoCondition, item: DynamoItem?): Boolean = when (condition) {
        is DynamoCondition.Absent -> item?.get(condition.attribute) == null
        is DynamoCondition.Equals -> item?.get(condition.attribute) == condition.value
        is DynamoCondition.AtMost -> item?.get(condition.attribute)?.n()?.toLong()?.let { it <= condition.value } == true
        is DynamoCondition.AnyOf -> condition.terms.any { evaluate(it, item) }
    }
}

internal fun dynamoRepository(client: InMemoryDynamoTableClient = InMemoryDynamoTableClient()) =
    DynamoDbRegistryRepository(client, DynamoDbRegistryTables("seen-registry-dev"))
