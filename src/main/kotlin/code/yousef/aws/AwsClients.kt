package code.yousef.aws

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI
import java.time.Duration

/**
 * Lazily created, shared AWS SDK clients. Credentials always come from the SDK default
 * chain (the ECS task role in production); no static keys are read by the application.
 *
 * `AWS_REGION` selects the region (ECS sets it). `PORTFOLIO_AWS_ENDPOINT_URL` overrides
 * every endpoint and is intended only for local emulators such as DynamoDB Local.
 */
class AwsClients(environment: Map<String, String> = System.getenv()) : AutoCloseable {
    private val endpointOverride: URI? = environment["PORTFOLIO_AWS_ENDPOINT_URL"]?.trim()?.takeIf(String::isNotEmpty)
        ?.let { raw ->
            URI(raw).also { uri ->
                require(uri.scheme == "http" || uri.scheme == "https") { "PORTFOLIO_AWS_ENDPOINT_URL must be HTTP(S)" }
            }
        }
    private val region: Region = environment["AWS_REGION"]?.trim()?.takeIf(String::isNotEmpty)?.let(Region::of)
        ?: runCatching { DefaultAwsRegionProviderChain().region }.getOrElse { Region.EU_NORTH_1 }

    private val httpClientLazy = lazy {
        UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(3))
            .socketTimeout(Duration.ofSeconds(30))
            .build()
    }
    private val overrides = ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(60))
        .apiCallAttemptTimeout(Duration.ofSeconds(25))
        .build()

    private val httpClient get() = httpClientLazy.value

    private val dynamoDbLazy = lazy {
        DynamoDbClient.builder()
            .region(region)
            .httpClient(httpClient)
            .overrideConfiguration(overrides)
            .also { builder -> endpointOverride?.let(builder::endpointOverride) }
            .build()
    }

    val dynamoDb: DynamoDbClient get() = dynamoDbLazy.value

    private val s3Lazy = lazy {
        S3Client.builder()
            .region(region)
            .httpClient(httpClient)
            .overrideConfiguration(overrides)
            .also { builder ->
                endpointOverride?.let { builder.endpointOverride(it).forcePathStyle(true) }
            }
            .build()
    }

    val s3: S3Client get() = s3Lazy.value

    private val sqsLazy = lazy {
        SqsClient.builder()
            .region(region)
            .httpClient(httpClient)
            .overrideConfiguration(
                // Long polls hold the connection for up to 20 seconds.
                overrides.toBuilder().apiCallAttemptTimeout(Duration.ofSeconds(40)).build(),
            )
            .also { builder -> endpointOverride?.let(builder::endpointOverride) }
            .build()
    }

    val sqs: SqsClient get() = sqsLazy.value

    override fun close() {
        listOf(dynamoDbLazy, s3Lazy, sqsLazy, httpClientLazy).forEach { client ->
            if (client.isInitialized()) runCatching { (client.value as AutoCloseable).close() }
        }
    }
}
