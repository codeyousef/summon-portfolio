package code.yousef.aws

import code.yousef.aws.dynamodb.DynamoDbPortfolioDocumentStore
import org.junit.Assume
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.Projection
import software.amazon.awssdk.services.dynamodb.model.ProjectionType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * DynamoDB Local in Docker for integration tests, started once per test JVM. Tests are
 * skipped (JUnit assumption) when Docker is unavailable, unless
 * `PORTFOLIO_REQUIRE_DYNAMODB_LOCAL=true`, in which case they fail instead.
 *
 * The table definitions here are the contract the infrastructure must provision.
 */
object DynamoDbLocal {
    private const val IMAGE = "amazon/dynamodb-local:2.5.2"

    private val started: Result<DynamoDbClient> by lazy { runCatching { start() } }

    fun client(): DynamoDbClient {
        val result = started
        if (result.isFailure && System.getenv("PORTFOLIO_REQUIRE_DYNAMODB_LOCAL") == "true") throw result.exceptionOrNull()!!
        Assume.assumeTrue("DynamoDB Local (Docker) is unavailable: ${result.exceptionOrNull()?.message}", result.isSuccess)
        return result.getOrThrow()
    }

    /** Documents table: pk/sk plus the order and lookup GSIs. */
    fun createDocumentsTable(client: DynamoDbClient = client()): String {
        val name = "documents-${UUID.randomUUID()}"
        fun key(name: String, type: KeyType) = KeySchemaElement.builder().attributeName(name).keyType(type).build()
        fun attribute(name: String) = AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build()
        client.createTable { request ->
            request.tableName(name)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    attribute(DynamoDbPortfolioDocumentStore.PK),
                    attribute(DynamoDbPortfolioDocumentStore.SK),
                    attribute(DynamoDbPortfolioDocumentStore.OPK),
                    attribute(DynamoDbPortfolioDocumentStore.OSK),
                    attribute(DynamoDbPortfolioDocumentStore.LPK),
                )
                .keySchema(key(DynamoDbPortfolioDocumentStore.PK, KeyType.HASH), key(DynamoDbPortfolioDocumentStore.SK, KeyType.RANGE))
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName(DynamoDbPortfolioDocumentStore.ORDER_INDEX)
                        .keySchema(key(DynamoDbPortfolioDocumentStore.OPK, KeyType.HASH), key(DynamoDbPortfolioDocumentStore.OSK, KeyType.RANGE))
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build(),
                    GlobalSecondaryIndex.builder()
                        .indexName(DynamoDbPortfolioDocumentStore.LOOKUP_INDEX)
                        .keySchema(key(DynamoDbPortfolioDocumentStore.LPK, KeyType.HASH), key(DynamoDbPortfolioDocumentStore.SK, KeyType.RANGE))
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build(),
                )
        }
        return name
    }

    /** Sessions/leases table: partition key `pk` only, TTL attribute `expires_at`. */
    fun createSessionsTable(client: DynamoDbClient = client()): String {
        val name = "sessions-${UUID.randomUUID()}"
        client.createTable { request ->
            request.tableName(name)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build())
        }
        return name
    }

    private fun start(): DynamoDbClient {
        val id = run("docker", "run", "-d", "--rm", "-p", "127.0.0.1::8000", IMAGE, "-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { run("docker", "rm", "-f", id) } })
        val port = run("docker", "port", id, "8000/tcp").lineSequence().first().substringAfterLast(':').trim().toInt()
        val client = DynamoDbClient.builder()
            .endpointOverride(URI("http://127.0.0.1:$port"))
            .region(Region.EU_NORTH_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .httpClient(UrlConnectionHttpClient.builder().socketTimeout(Duration.ofSeconds(30)).build())
            .build()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (true) {
            try {
                client.listTables()
                return client
            } catch (failure: Exception) {
                if (System.nanoTime() > deadline) throw IllegalStateException("DynamoDB Local did not start", failure)
                Thread.sleep(250)
            }
        }
    }

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "${command.take(2).joinToString(" ")} failed: ${output.take(300)}"
        }
        return output.trim()
    }
}
