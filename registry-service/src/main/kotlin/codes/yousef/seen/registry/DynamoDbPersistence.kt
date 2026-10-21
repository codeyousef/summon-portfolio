package codes.yousef.seen.registry

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.Put
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * DynamoDB table names for one registry deployment identity.
 *
 * Each Firestore collection maps to its own table so IAM can grant a runtime
 * only the tables it touches. Two auxiliary tables replace Firestore's
 * strongly consistent field queries, because DynamoDB global secondary indexes
 * are only eventually consistent:
 *
 * - `release-uploads` maps an upload ID to its release key;
 * - `review-artifact-index` stores each package/version-bound artifact under a
 *   `package@version` partition so evidence listing is a consistent Query.
 *
 * Both are written in the same TransactWriteItems call as their primary item.
 */
data class DynamoDbRegistryTables(val prefix: String) {
    init {
        require(TABLE_PREFIX.matches(prefix)) { "REGISTRY_DYNAMODB_TABLE_PREFIX is invalid" }
    }

    val packages: String get() = "$prefix-packages-v1"
    val releases: String get() = "$prefix-releases-v1"
    val releaseUploads: String get() = "$prefix-release-uploads-v1"
    val state: String get() = "$prefix-state-v1"
    val idempotency: String get() = "$prefix-idempotency-v1"
    val reviewArtifacts: String get() = "$prefix-review-artifacts-v1"
    val reviewArtifactIndex: String get() = "$prefix-review-artifact-index-v1"

    val all: List<String> get() = listOf(
        packages,
        releases,
        releaseUploads,
        state,
        idempotency,
        reviewArtifacts,
        reviewArtifactIndex,
    )

    /** Key schema per table, shared by the AWS adapter, the test fake, and the infra contract. */
    internal val keySchema: Map<String, List<String>> get() = mapOf(
        packages to listOf(PK),
        releases to listOf(PK, SK),
        releaseUploads to listOf(PK),
        state to listOf(PK),
        idempotency to listOf(PK),
        reviewArtifacts to listOf(PK),
        reviewArtifactIndex to listOf(PK, SK),
    )

    companion object {
        private val TABLE_PREFIX = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,200}$")

        /** The only accepted prefix for each deployment identity. */
        fun expectedPrefix(environment: String): String = when (environment) {
            "development" -> "seen-registry-dev"
            "production" -> "seen-registry-prod"
            else -> throw IllegalArgumentException("REGISTRY_ENVIRONMENT must be development or production")
        }
    }
}

internal const val PK = "pk"
internal const val SK = "sk"

internal typealias DynamoItem = Map<String, AttributeValue>

/** Small typed condition language rendered to DynamoDB expressions and evaluated by test fakes. */
internal sealed interface DynamoCondition {
    data class Absent(val attribute: String) : DynamoCondition
    data class Equals(val attribute: String, val value: AttributeValue) : DynamoCondition
    data class AtMost(val attribute: String, val value: Long) : DynamoCondition
    data class AnyOf(val terms: List<DynamoCondition>) : DynamoCondition
}

internal data class DynamoPut(
    val table: String,
    val item: DynamoItem,
    val condition: DynamoCondition? = null,
)

internal data class DynamoUpdate(
    val table: String,
    val key: DynamoItem,
    val set: DynamoItem = emptyMap(),
    val remove: Set<String> = emptySet(),
    val increment: Map<String, Long> = emptyMap(),
    val condition: DynamoCondition? = null,
)

internal sealed interface DynamoWriteResult {
    data class Applied(val item: DynamoItem?) : DynamoWriteResult
    data class ConditionFailed(val current: DynamoItem?) : DynamoWriteResult
}

internal sealed interface DynamoTransactionResult {
    data object Applied : DynamoTransactionResult
    /** [conditionFailed] holds zero-based indexes of puts whose condition failed. */
    data class Cancelled(val conditionFailed: Set<Int>, val conflicted: Boolean) : DynamoTransactionResult
}

/**
 * Narrow boundary over DynamoDB. Every read is strongly consistent; every
 * conditional write returns the current item on condition failure.
 */
internal interface DynamoTableClient : AutoCloseable {
    fun get(table: String, key: DynamoItem): DynamoItem?
    fun put(put: DynamoPut): DynamoWriteResult
    fun update(update: DynamoUpdate): DynamoWriteResult
    fun query(table: String, partitionValue: String): List<DynamoItem>
    fun scan(table: String): List<DynamoItem>
    fun transactWrite(puts: List<DynamoPut>): DynamoTransactionResult
    override fun close() = Unit
}

internal data class RenderedDynamoCondition(
    val expression: String,
    val names: Map<String, String>,
    val values: Map<String, AttributeValue>,
)

internal class DynamoExpressionBuilder(private val namespace: String = "") {
    val names = linkedMapOf<String, String>()
    val values = linkedMapOf<String, AttributeValue>()

    fun name(attribute: String): String {
        names.entries.firstOrNull { it.value == attribute }?.let { return it.key }
        val placeholder = "#$namespace${names.size}"
        names[placeholder] = attribute
        return placeholder
    }

    fun value(value: AttributeValue): String {
        val placeholder = ":$namespace${values.size}"
        values[placeholder] = value
        return placeholder
    }

    fun render(condition: DynamoCondition): String = when (condition) {
        is DynamoCondition.Absent -> "attribute_not_exists(${name(condition.attribute)})"
        is DynamoCondition.Equals -> "${name(condition.attribute)} = ${value(condition.value)}"
        is DynamoCondition.AtMost -> "${name(condition.attribute)} <= ${value(numberValue(condition.value))}"
        is DynamoCondition.AnyOf -> {
            require(condition.terms.isNotEmpty()) { "Empty DynamoDB disjunction" }
            condition.terms.joinToString(" OR ", "(", ")") { render(it) }
        }
    }

    companion object {
        fun condition(condition: DynamoCondition): RenderedDynamoCondition {
            val builder = DynamoExpressionBuilder("c")
            val expression = builder.render(condition)
            return RenderedDynamoCondition(expression, builder.names, builder.values)
        }
    }
}

/** AWS SDK adapter. Uses the default credential chain (ECS task role) and the configured region only. */
internal class AwsSdkDynamoTableClient(
    private val client: DynamoDbClient,
    private val tables: DynamoDbRegistryTables,
) : DynamoTableClient {
    override fun get(table: String, key: DynamoItem): DynamoItem? {
        val response = client.getItem(
            GetItemRequest.builder().tableName(table).key(key).consistentRead(true).build(),
        )
        return response.item()?.takeIf { response.hasItem() && it.isNotEmpty() }
    }

    override fun put(put: DynamoPut): DynamoWriteResult = try {
        val builder = PutItemRequest.builder().tableName(put.table).item(put.item)
        put.condition?.let { condition ->
            val rendered = DynamoExpressionBuilder.condition(condition)
            builder.conditionExpression(rendered.expression)
                .expressionAttributeNames(rendered.names)
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            if (rendered.values.isNotEmpty()) builder.expressionAttributeValues(rendered.values)
        }
        client.putItem(builder.build())
        DynamoWriteResult.Applied(put.item)
    } catch (failure: ConditionalCheckFailedException) {
        DynamoWriteResult.ConditionFailed(failure.item()?.takeIf { failure.hasItem() && it.isNotEmpty() })
    }

    override fun update(update: DynamoUpdate): DynamoWriteResult = try {
        val expression = DynamoExpressionBuilder("u")
        val clauses = mutableListOf<String>()
        if (update.set.isNotEmpty()) {
            clauses += update.set.entries.joinToString(", ", "SET ") { (name, value) ->
                "${expression.name(name)} = ${expression.value(value)}"
            }
        }
        if (update.remove.isNotEmpty()) {
            clauses += update.remove.joinToString(", ", "REMOVE ") { expression.name(it) }
        }
        if (update.increment.isNotEmpty()) {
            clauses += update.increment.entries.joinToString(", ", "ADD ") { (name, delta) ->
                "${expression.name(name)} ${expression.value(numberValue(delta))}"
            }
        }
        require(clauses.isNotEmpty()) { "DynamoDB update has no action" }
        val conditionExpression = update.condition?.let(expression::render)
        val builder = UpdateItemRequest.builder()
            .tableName(update.table)
            .key(update.key)
            .updateExpression(clauses.joinToString(" "))
            .expressionAttributeNames(expression.names)
            .returnValues(ReturnValue.ALL_NEW)
        if (expression.values.isNotEmpty()) builder.expressionAttributeValues(expression.values)
        conditionExpression?.let {
            builder.conditionExpression(it)
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
        }
        DynamoWriteResult.Applied(client.updateItem(builder.build()).attributes())
    } catch (failure: ConditionalCheckFailedException) {
        DynamoWriteResult.ConditionFailed(failure.item()?.takeIf { failure.hasItem() && it.isNotEmpty() })
    }

    override fun query(table: String, partitionValue: String): List<DynamoItem> = client.queryPaginator(
        QueryRequest.builder()
            .tableName(table)
            .consistentRead(true)
            .keyConditionExpression("#pk = :pk")
            .expressionAttributeNames(mapOf("#pk" to PK))
            .expressionAttributeValues(mapOf(":pk" to stringValue(partitionValue)))
            .build(),
    ).items().toList()

    override fun scan(table: String): List<DynamoItem> = client.scanPaginator(
        ScanRequest.builder().tableName(table).consistentRead(true).build(),
    ).items().toList()

    override fun transactWrite(puts: List<DynamoPut>): DynamoTransactionResult {
        require(puts.size in 1..100) { "DynamoDB transaction size is invalid" }
        val items = puts.mapIndexed { index, put ->
            val builder = Put.builder().tableName(put.table).item(put.item)
            put.condition?.let { condition ->
                val expression = DynamoExpressionBuilder("t$index")
                builder.conditionExpression(expression.render(condition))
                    .expressionAttributeNames(expression.names)
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.NONE)
                if (expression.values.isNotEmpty()) builder.expressionAttributeValues(expression.values)
            }
            TransactWriteItem.builder().put(builder.build()).build()
        }
        return try {
            // One token per logical attempt: SDK-level retries of this request
            // are idempotent for ten minutes instead of failing their own
            // conditions after an ambiguous first success.
            client.transactWriteItems(
                TransactWriteItemsRequest.builder()
                    .transactItems(items)
                    .clientRequestToken(UUID.randomUUID().toString())
                    .build(),
            )
            DynamoTransactionResult.Applied
        } catch (failure: TransactionCanceledException) {
            val reasons = if (failure.hasCancellationReasons()) failure.cancellationReasons() else emptyList()
            DynamoTransactionResult.Cancelled(
                conditionFailed = reasons.withIndex()
                    .filter { it.value.code() == "ConditionalCheckFailed" }
                    .mapTo(mutableSetOf()) { it.index },
                conflicted = reasons.any { it.code() == "TransactionConflict" },
            )
        }
    }

    override fun close() = client.close()

    companion object {
        fun create(region: String, tables: DynamoDbRegistryTables): AwsSdkDynamoTableClient {
            require(AWS_REGION_NAME.matches(region)) { "AWS_REGION is invalid" }
            val client = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build()
            return AwsSdkDynamoTableClient(client, tables)
        }
    }
}

/**
 * DynamoDB adapter with the same transactional semantics as
 * [FirestoreRegistryRepository]:
 *
 * - package, release, upload-pointer and evidence creation are
 *   `attribute_not_exists` create-only writes;
 * - release lifecycle transitions are compare-and-set on `revision`;
 * - promotion activation commits release, package and audit evidence in one
 *   TransactWriteItems call guarded by revision, package ETag and evidence
 *   absence, re-evaluating from fresh reads on contention like a Firestore
 *   transaction retry;
 * - idempotency and lease records are compare-and-set on a per-write token or
 *   lease expiry.
 */
class DynamoDbRegistryRepository internal constructor(
    private val client: DynamoTableClient,
    private val tables: DynamoDbRegistryTables,
    private val json: Json = RegistryJson,
    private val maximumAttempts: Int = 8,
) : RegistryRepository {
    init {
        require(maximumAttempts in 1..64)
    }

    override fun createPackage(value: StoredPackage): Boolean =
        client.put(DynamoPut(tables.packages, packageItem(value), DynamoCondition.Absent(PK))) is DynamoWriteResult.Applied

    override fun getPackage(identity: String): StoredPackage? =
        client.get(tables.packages, key(identity))?.let { decode<StoredPackage>(it) }

    override fun savePackage(value: StoredPackage) {
        client.put(DynamoPut(tables.packages, packageItem(value)))
    }

    override fun listPackages(): List<StoredPackage> = client.scan(tables.packages)
        .map { decode<StoredPackage>(it) }
        .sortedBy { it.record.identity }

    override fun reserveRelease(value: StoredRelease): Boolean {
        repeat(maximumAttempts) {
            val result = client.transactWrite(listOf(
                DynamoPut(tables.releases, releaseItem(value), DynamoCondition.Absent(PK)),
                DynamoPut(tables.releaseUploads, uploadItem(value), DynamoCondition.Absent(PK)),
            ))
            when (result) {
                DynamoTransactionResult.Applied -> return true
                is DynamoTransactionResult.Cancelled -> {
                    if (0 in result.conditionFailed) return false
                    check(1 !in result.conditionFailed) { "Release upload ID is already bound to another release" }
                }
            }
        }
        throw IllegalStateException("DynamoDB release reservation did not converge")
    }

    override fun getRelease(identity: String, version: String): StoredRelease? =
        client.get(tables.releases, key(identity, version))?.let { decode<StoredRelease>(it) }

    override fun findReleaseByUpload(uploadId: String): StoredRelease? {
        val pointer = client.get(tables.releaseUploads, key(uploadId)) ?: return null
        val release = getRelease(pointer.string("release_package"), pointer.string("release_version"))
            ?: return null
        check(release.uploadId == uploadId) { "Release upload pointer is inconsistent" }
        return release
    }

    override fun transitionRelease(expectedRevision: Long, value: StoredRelease): ReleaseTransitionResult {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        val current = getRelease(value.record.`package`, value.record.version)
            ?: return ReleaseTransitionResult.Missing
        if (current.revision != expectedRevision) return ReleaseTransitionResult.Conflict(current)
        requireSameRelease(current, value)
        return when (val result = client.put(DynamoPut(
            tables.releases,
            releaseItem(value),
            DynamoCondition.Equals(REVISION, numberValue(expectedRevision)),
        ))) {
            is DynamoWriteResult.Applied -> ReleaseTransitionResult.Applied(value)
            is DynamoWriteResult.ConditionFailed -> result.current
                ?.let { ReleaseTransitionResult.Conflict(decode(it)) }
                ?: ReleaseTransitionResult.Missing
        }
    }

    override fun commitPromotionActivation(
        expectedRevision: Long,
        value: StoredRelease,
        activationAudit: ReviewArtifact,
    ): PromotionActivationResult {
        require(value.revision == expectedRevision + 1) { "Release revision must advance exactly once" }
        repeat(maximumAttempts) {
            val current = getRelease(value.record.`package`, value.record.version)
                ?: return PromotionActivationResult.MissingRelease
            if (current.revision != expectedRevision) return PromotionActivationResult.Conflict(current)
            requireSameRelease(current, value)
            requireActivationCommit(value, activationAudit, json)

            val packageItem = client.get(tables.packages, key(value.record.`package`))
                ?: return PromotionActivationResult.MissingPackage
            val storedPackage = decode<StoredPackage>(packageItem)
            if (client.get(tables.reviewArtifacts, key(activationAudit.artifactId)) != null) {
                return PromotionActivationResult.AuditCollision
            }
            require(storedPackage.ownerPrincipal == value.ownerPrincipal) { "Promotion package owner does not match release" }

            val updatedPackage = storedPackage.copy(record = storedPackage.record.copy(
                latestActiveVersion = value.record.version,
                updatedAt = requireNotNull(value.record.timestamps.activatedAt),
            ))
            val puts = buildList {
                add(DynamoPut(
                    tables.releases,
                    releaseItem(value),
                    DynamoCondition.Equals(REVISION, numberValue(expectedRevision)),
                ))
                add(DynamoPut(
                    tables.packages,
                    packageItem(updatedPackage),
                    DynamoCondition.Equals(ETAG, packageItem.getValue(ETAG)),
                ))
                addAll(artifactPuts(activationAudit))
            }
            when (client.transactWrite(puts)) {
                DynamoTransactionResult.Applied -> return PromotionActivationResult.Applied(value)
                // Any failed guard or conflict re-evaluates from fresh reads,
                // which then yields Conflict, MissingPackage, or AuditCollision.
                is DynamoTransactionResult.Cancelled -> Unit
            }
        }
        throw IllegalStateException("DynamoDB promotion activation did not converge")
    }

    override fun listReleases(identity: String?): List<StoredRelease> =
        (if (identity == null) client.scan(tables.releases) else client.query(tables.releases, identity))
            .map { decode<StoredRelease>(it) }
            .filter { identity == null || it.record.`package` == identity }
            .sortedWith(compareByDescending<StoredRelease> { it.record.timestamps.reservedAt }.thenBy { it.record.version })

    override fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin {
        repeat(maximumAttempts) {
            val item = client.get(tables.idempotency, key(value.scope))
            val existing = item?.let { decode<StoredIdempotency>(it) }
            val acquire = when {
                existing == null || !Instant.parse(existing.expiresAt).isAfter(now) -> true
                existing.fingerprint != value.fingerprint -> throw RegistryException(
                    409, "idempotency_key_reused", "Idempotency-Key was already used for a different request",
                )
                existing.response != null -> return IdempotencyBegin.Replay(existing)
                !Instant.parse(existing.processingExpiresAt).isAfter(now) -> true
                else -> return IdempotencyBegin.InProgress
            }
            check(acquire)
            val condition = item?.let { DynamoCondition.Equals(WRITE_TOKEN, it.getValue(WRITE_TOKEN)) }
                ?: DynamoCondition.Absent(PK)
            if (client.put(DynamoPut(tables.idempotency, idempotencyItem(value), condition)) is DynamoWriteResult.Applied) {
                return IdempotencyBegin.Acquired
            }
        }
        throw IllegalStateException("DynamoDB idempotency reservation did not converge")
    }

    override fun completeIdempotency(
        scope: String,
        fingerprint: String,
        attemptId: String,
        response: StoredIdempotencyResponse,
    ): Boolean {
        repeat(maximumAttempts) {
            val item = client.get(tables.idempotency, key(scope)) ?: error("Idempotency reservation is missing")
            val existing = decode<StoredIdempotency>(item)
            check(existing.fingerprint == fingerprint) { "Idempotency fingerprint changed" }
            if (existing.attemptId != attemptId) return false
            val updated = idempotencyItem(existing.copy(response = response))
            val condition = DynamoCondition.Equals(WRITE_TOKEN, item.getValue(WRITE_TOKEN))
            if (client.put(DynamoPut(tables.idempotency, updated, condition)) is DynamoWriteResult.Applied) return true
        }
        throw IllegalStateException("DynamoDB idempotency completion did not converge")
    }

    override fun appendReviewArtifact(value: ReviewArtifact): Boolean {
        repeat(maximumAttempts) {
            when (val result = client.transactWrite(artifactPuts(value))) {
                DynamoTransactionResult.Applied -> return true
                is DynamoTransactionResult.Cancelled -> {
                    if (0 in result.conditionFailed) return false
                    check(result.conditionFailed.isEmpty()) { "Review artifact index is inconsistent" }
                }
            }
        }
        throw IllegalStateException("DynamoDB review artifact append did not converge")
    }

    override fun getReviewArtifact(artifactId: String): ReviewArtifact? =
        client.get(tables.reviewArtifacts, key(artifactId))?.let { decode<ReviewArtifact>(it) }

    override fun listReviewArtifacts(packageIdentity: String?, version: String?, kind: String?): List<ReviewArtifact> {
        val items = if (packageIdentity != null && version != null) {
            client.query(tables.reviewArtifactIndex, artifactPartition(packageIdentity, version))
        } else {
            client.scan(tables.reviewArtifacts)
        }
        return items.asSequence()
            .map { decode<ReviewArtifact>(it) }
            .filter { packageIdentity == null || it.packageIdentity == packageIdentity }
            .filter { version == null || it.version == version }
            .filter { kind == null || it.kind == kind }
            .sortedWith(compareByDescending<ReviewArtifact> { it.sequence }.thenByDescending { it.createdAt })
            .toList()
    }

    override fun tryAcquireMetadataPublication(holder: String, now: Instant, expiresAt: Instant): Boolean {
        val result = client.update(DynamoUpdate(
            table = tables.state,
            key = key(STATE_METADATA),
            set = mapOf(
                PUBLICATION_HOLDER to stringValue(holder),
                PUBLICATION_EXPIRES_AT to stringValue(expiresAt.utc()),
                PUBLICATION_EXPIRES_AT_NANOS to numberValue(expiresAt.epochNanos()),
            ),
            // Mirrors Firestore: refuse only while another holder's lease is unexpired.
            condition = DynamoCondition.AnyOf(listOf(
                DynamoCondition.Absent(PUBLICATION_HOLDER),
                DynamoCondition.Equals(PUBLICATION_HOLDER, stringValue(holder)),
                DynamoCondition.Absent(PUBLICATION_EXPIRES_AT_NANOS),
                DynamoCondition.AtMost(PUBLICATION_EXPIRES_AT_NANOS, now.epochNanos()),
            )),
        ))
        return result is DynamoWriteResult.Applied
    }

    override fun releaseMetadataPublication(holder: String) {
        client.update(DynamoUpdate(
            table = tables.state,
            key = key(STATE_METADATA),
            remove = setOf(PUBLICATION_HOLDER, PUBLICATION_EXPIRES_AT, PUBLICATION_EXPIRES_AT_NANOS),
            condition = DynamoCondition.Equals(PUBLICATION_HOLDER, stringValue(holder)),
        ))
    }

    override fun nextMetadataVersion(): Long {
        val result = client.update(DynamoUpdate(
            table = tables.state,
            key = key(STATE_METADATA),
            increment = mapOf(METADATA_VERSION to 1L),
        ))
        val item = (result as? DynamoWriteResult.Applied)?.item ?: error("DynamoDB metadata version update failed")
        return requireNotNull(item[METADATA_VERSION]?.n()) { "DynamoDB metadata version is missing" }.toLong()
    }

    override fun close() = client.close()

    private fun packageItem(value: StoredPackage): DynamoItem {
        val encoded = json.encodeToString(value)
        return key(value.record.identity) + payload(encoded) + mapOf(ETAG to stringValue(sha256(encoded.toByteArray())))
    }

    private fun releaseItem(value: StoredRelease): DynamoItem =
        key(value.record.`package`, value.record.version) + payload(json.encodeToString(value)) + mapOf(
            UPLOAD_ID to stringValue(value.uploadId),
            REVISION to numberValue(value.revision),
        )

    private fun uploadItem(value: StoredRelease): DynamoItem = key(value.uploadId) + mapOf(
        "release_package" to stringValue(value.record.`package`),
        "release_version" to stringValue(value.record.version),
    )

    private fun idempotencyItem(value: StoredIdempotency): DynamoItem =
        key(value.scope) + payload(json.encodeToString(value)) + mapOf(
            "expires_at" to stringValue(value.expiresAt),
            // DynamoDB TTL deletes lazily; the record is already treated as
            // absent after expiresAt, so this only bounds table growth.
            TTL to numberValue(Instant.parse(value.expiresAt).epochSecond + TTL_GRACE_SECONDS),
            WRITE_TOKEN to stringValue(UUID.randomUUID().toString()),
        )

    private fun artifactAttributes(value: ReviewArtifact): DynamoItem = buildMap {
        putAll(payload(json.encodeToString(value)))
        put("kind", stringValue(value.kind))
        value.packageIdentity?.let { put("package", stringValue(it)) }
        value.version?.let { put("version", stringValue(it)) }
        put("sequence", numberValue(value.sequence))
        put("created_at", stringValue(value.createdAt))
    }

    private fun artifactPuts(value: ReviewArtifact): List<DynamoPut> {
        val attributes = artifactAttributes(value)
        return buildList {
            add(DynamoPut(tables.reviewArtifacts, key(value.artifactId) + attributes, DynamoCondition.Absent(PK)))
            val packageIdentity = value.packageIdentity
            val version = value.version
            if (packageIdentity != null && version != null) {
                add(DynamoPut(
                    tables.reviewArtifactIndex,
                    key(artifactPartition(packageIdentity, version), value.artifactId) + attributes,
                    DynamoCondition.Absent(PK),
                ))
            }
        }
    }

    private fun payload(encoded: String): DynamoItem {
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAXIMUM_INLINE_JSON_BYTES) return mapOf(JSON to stringValue(encoded))
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
        }.toByteArray()
        require(compressed.size <= MAXIMUM_COMPRESSED_JSON_BYTES) {
            "Registry record exceeds the DynamoDB item size bound"
        }
        return mapOf(JSON_GZIP to AttributeValue.fromB(SdkBytes.fromByteArray(compressed)))
    }

    private inline fun <reified T> decode(item: DynamoItem): T {
        val inline = item[JSON]?.s()
        val encoded = inline ?: item[JSON_GZIP]?.b()?.asByteArray()?.let { compressed ->
            GZIPInputStream(compressed.inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAXIMUM_DECOMPRESSED_JSON_BYTES) { "Registry record exceeds its decoded size bound" }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8)
            }
        } ?: error("DynamoDB registry record has no JSON payload")
        return json.decodeFromString(encoded)
    }

    private fun key(partition: String): DynamoItem = mapOf(PK to stringValue(partition))
    private fun key(partition: String, sort: String): DynamoItem =
        mapOf(PK to stringValue(partition), SK to stringValue(sort))

    private fun DynamoItem.string(name: String): String =
        requireNotNull(this[name]?.s()) { "DynamoDB attribute $name is missing" }

    companion object {
        fun create(region: String, tablePrefix: String): DynamoDbRegistryRepository {
            val tables = DynamoDbRegistryTables(tablePrefix)
            return DynamoDbRegistryRepository(AwsSdkDynamoTableClient.create(region, tables), tables)
        }

        internal const val JSON = "json"
        internal const val JSON_GZIP = "json_gz"
        internal const val ETAG = "etag"
        internal const val REVISION = "revision"
        internal const val UPLOAD_ID = "upload_id"
        internal const val WRITE_TOKEN = "write_token"
        internal const val TTL = "ttl"
        internal const val STATE_METADATA = "metadata"
        internal const val METADATA_VERSION = "version"
        internal const val PUBLICATION_HOLDER = "publication_holder"
        internal const val PUBLICATION_EXPIRES_AT = "publication_expires_at"
        internal const val PUBLICATION_EXPIRES_AT_NANOS = "publication_expires_at_epoch_nanos"
        internal const val MAXIMUM_INLINE_JSON_BYTES = 256 * 1024
        internal const val MAXIMUM_COMPRESSED_JSON_BYTES = 320 * 1024
        private const val MAXIMUM_DECOMPRESSED_JSON_BYTES = 16L * 1024 * 1024
        private const val TTL_GRACE_SECONDS = 86_400L

        internal fun artifactPartition(packageIdentity: String, version: String) = "$packageIdentity@$version"
    }
}

internal fun stringValue(value: String): AttributeValue = AttributeValue.fromS(value)
internal fun numberValue(value: Long): AttributeValue = AttributeValue.fromN(value.toString())

private fun Instant.epochNanos(): Long = Math.addExact(Math.multiplyExact(epochSecond, 1_000_000_000L), nano.toLong())
