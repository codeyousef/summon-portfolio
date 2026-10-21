package codes.yousef.seen.registry

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.CancellationReason
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsSdkDynamoTableClientTest {
    private val tables = DynamoDbRegistryTables("seen-registry-dev")

    @Test
    fun `reads are strongly consistent and empty items are absent`() {
        val sdk = RecordingDynamoDbClient()
        val client = AwsSdkDynamoTableClient(sdk, tables)
        assertNull(client.get(tables.packages, mapOf(PK to stringValue("seen/demo"))))
        val request = sdk.gets.single()
        assertTrue(request.consistentRead())
        assertEquals(tables.packages, request.tableName())
    }

    @Test
    fun `conditional puts render expressions and map condition failures`() {
        val sdk = RecordingDynamoDbClient(putFailure = ConditionalCheckFailedException.builder()
            .item(mapOf(PK to stringValue("x"), "revision" to numberValue(3)))
            .build())
        val client = AwsSdkDynamoTableClient(sdk, tables)
        val result = client.put(DynamoPut(
            tables.releases,
            mapOf(PK to stringValue("x"), SK to stringValue("1.0.0")),
            DynamoCondition.Equals("revision", numberValue(2)),
        ))
        val failed = assertIs<DynamoWriteResult.ConditionFailed>(result)
        assertEquals("3", failed.current?.get("revision")?.n())
        val request = sdk.puts.single()
        assertEquals("#c0 = :c0", request.conditionExpression())
        assertEquals(mapOf("#c0" to "revision"), request.expressionAttributeNames())
        assertEquals(mapOf(":c0" to numberValue(2)), request.expressionAttributeValues())
        assertEquals(ReturnValuesOnConditionCheckFailure.ALL_OLD, request.returnValuesOnConditionCheckFailure())
    }

    @Test
    fun `updates combine set remove add and condition clauses`() {
        val sdk = RecordingDynamoDbClient()
        val client = AwsSdkDynamoTableClient(sdk, tables)
        client.update(DynamoUpdate(
            table = tables.state,
            key = mapOf(PK to stringValue("metadata")),
            set = mapOf("publication_holder" to stringValue("h")),
            remove = setOf("stale"),
            increment = mapOf("version" to 1),
            condition = DynamoCondition.Absent("publication_holder"),
        ))
        val request = sdk.updates.single()
        assertEquals("SET #u0 = :u0 REMOVE #u1 ADD #u2 :u1", request.updateExpression())
        assertEquals("attribute_not_exists(#u0)", request.conditionExpression())
        assertEquals(ReturnValue.ALL_NEW, request.returnValues())
    }

    @Test
    fun `transactions carry an idempotency token and report per item cancellation`() {
        val sdk = RecordingDynamoDbClient(transactionFailure = TransactionCanceledException.builder()
            .cancellationReasons(
                CancellationReason.builder().code("None").build(),
                CancellationReason.builder().code("ConditionalCheckFailed").build(),
            )
            .build())
        val client = AwsSdkDynamoTableClient(sdk, tables)
        val result = client.transactWrite(listOf(
            DynamoPut(tables.reviewArtifacts, mapOf(PK to stringValue("a")), DynamoCondition.Absent(PK)),
            DynamoPut(
                tables.reviewArtifactIndex,
                mapOf(PK to stringValue("p@v"), SK to stringValue("a")),
                DynamoCondition.Absent(PK),
            ),
        ))
        assertEquals(DynamoTransactionResult.Cancelled(setOf(1), conflicted = false), result)
        val request = sdk.transactions.single()
        assertNotNull(request.clientRequestToken())
        assertEquals("attribute_not_exists(#t00)", request.transactItems()[0].put().conditionExpression())
        assertEquals("attribute_not_exists(#t10)", request.transactItems()[1].put().conditionExpression())
    }

    private class RecordingDynamoDbClient(
        private val putFailure: ConditionalCheckFailedException? = null,
        private val transactionFailure: TransactionCanceledException? = null,
    ) : DynamoDbClient {
        val gets = mutableListOf<GetItemRequest>()
        val puts = mutableListOf<PutItemRequest>()
        val updates = mutableListOf<UpdateItemRequest>()
        val transactions = mutableListOf<TransactWriteItemsRequest>()

        override fun getItem(request: GetItemRequest): GetItemResponse {
            gets += request
            return GetItemResponse.builder().build()
        }

        override fun putItem(request: PutItemRequest): PutItemResponse {
            puts += request
            putFailure?.let { throw it }
            return PutItemResponse.builder().build()
        }

        override fun updateItem(request: UpdateItemRequest): UpdateItemResponse {
            updates += request
            return UpdateItemResponse.builder().attributes(request.key()).build()
        }

        override fun transactWriteItems(request: TransactWriteItemsRequest): TransactWriteItemsResponse {
            transactions += request
            transactionFailure?.let { throw it }
            return TransactWriteItemsResponse.builder().build()
        }

        override fun serviceName(): String = "dynamodb"
        override fun close() = Unit
    }
}
