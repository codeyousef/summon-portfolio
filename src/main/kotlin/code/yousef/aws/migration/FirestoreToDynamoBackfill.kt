package code.yousef.aws.migration

import code.yousef.aws.AwsClients
import code.yousef.aws.dynamodb.DynamoDbPortfolioDocumentStore
import code.yousef.firestore.migration.FirestoreBackfillCollectionParity
import code.yousef.firestore.migration.FirestoreBackfillDocument
import code.yousef.firestore.migration.FirestoreBackfillParity
import code.yousef.firestore.migration.FirestoreCanonicalHash
import code.yousef.firestore.migration.PortfolioFirestoreDevBackfillCli
import code.yousef.firestore.migration.PortfolioFirestoreMigrationCollections
import code.yousef.firestore.migration.planFirestoreBackfill
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.security.MessageDigest

/** Read access to one side of the backfill, keyed by document id. */
internal fun interface BackfillCollectionReader {
    fun load(collection: String): LinkedHashMap<String, FirestoreBackfillDocument>
}

/** Read-only Firestore source: ordered document-id pagination, never a write. */
internal class FirestoreBackfillReader(
    private val firestore: Firestore,
    private val pageSize: Int = 500,
) : BackfillCollectionReader {
    override fun load(collection: String): LinkedHashMap<String, FirestoreBackfillDocument> {
        val result = linkedMapOf<String, FirestoreBackfillDocument>()
        var after: String? = null
        while (true) {
            var query = firestore.collection(collection).orderBy(FieldPath.documentId()).limit(pageSize)
            if (after != null) query = query.startAfter(after)
            val page = query.get().get().documents
            page.forEach { snapshot -> result[snapshot.id] = FirestoreBackfillDocument(snapshot.id, snapshot.data.orEmpty()) }
            if (page.size < pageSize) return result
            after = page.last().id
        }
    }
}

internal class DynamoBackfillReader(private val store: DynamoDbPortfolioDocumentStore) : BackfillCollectionReader {
    override fun load(collection: String): LinkedHashMap<String, FirestoreBackfillDocument> =
        store.listForParity(collection).toSortedMap().entries
            .associateTo(LinkedHashMap()) { (id, data) -> id to FirestoreBackfillDocument(id, data) }
}

data class DynamoBackfillResult(
    val writtenDocuments: Long,
    val parity: FirestoreBackfillParity,
    val sourceParityHash: String,
    val targetParityHash: String,
) {
    val ready: Boolean get() = parity.ready && sourceParityHash == targetParityHash
}

/**
 * Copies the exact Portfolio business-data contract
 * ([PortfolioFirestoreMigrationCollections.all]) from Firestore into the DynamoDB documents
 * table and proves parity with the same canonical hashing as the Firestore migration proof.
 *
 * Firestore is only ever read. DynamoDB receives full-document replacements for source-only
 * and hash-different documents; nothing is deleted and any DynamoDB-only document fails the
 * run closed. Replication metadata (`_migration_*`) is not business data and is not copied.
 */
class FirestoreToDynamoBackfill internal constructor(
    private val source: BackfillCollectionReader,
    private val target: BackfillCollectionReader,
    private val write: (collection: String, documentId: String, data: Map<String, Any?>) -> Unit,
    private val collections: Set<String> = PortfolioFirestoreMigrationCollections.all,
) {
    init {
        require(collections.isNotEmpty()) { "At least one collection is required" }
    }

    fun inspect(): DynamoBackfillResult = result(0L)

    fun execute(maxPasses: Int = 3): DynamoBackfillResult {
        require(maxPasses in 1..10)
        var written = 0L
        repeat(maxPasses) {
            collections.sorted().forEach { collection ->
                val sourceDocuments = source.load(collection)
                val targetDocuments = target.load(collection)
                val plan = planFirestoreBackfill(sourceDocuments, targetDocuments)
                check(plan.targetOnlyCount == 0) {
                    "DynamoDB backfill refused: $collection has ${plan.targetOnlyCount} DynamoDB-only documents"
                }
                plan.writes.forEach { document ->
                    write(collection, document.id, document.data)
                    written = Math.addExact(written, 1L)
                }
            }
            val after = result(written)
            if (after.ready) return after
        }
        val final = result(written)
        check(final.ready) {
            "DynamoDB backfill did not converge: sourceOnly=${final.parity.sourceOnlyCount}, " +
                "targetOnly=${final.parity.targetOnlyCount}, different=${final.parity.differingCount}"
        }
        return final
    }

    private fun result(written: Long): DynamoBackfillResult {
        val parity = FirestoreBackfillParity(
            collections.sorted().map { collection ->
                val sourceDocuments = source.load(collection)
                val targetDocuments = target.load(collection)
                val plan = planFirestoreBackfill(sourceDocuments, targetDocuments)
                FirestoreBackfillCollectionParity(
                    collection = collection,
                    sourceCount = sourceDocuments.size,
                    targetCount = targetDocuments.size,
                    sourceOnlyCount = plan.sourceOnlyCount,
                    targetOnlyCount = plan.targetOnlyCount,
                    differingCount = plan.differingCount,
                    sourceHash = FirestoreCanonicalHash.collection(sourceDocuments),
                    targetHash = FirestoreCanonicalHash.collection(targetDocuments),
                )
            },
        )
        return DynamoBackfillResult(
            writtenDocuments = written,
            parity = parity,
            sourceParityHash = parityHash(parity) { it.sourceCount to it.sourceHash },
            targetParityHash = parityHash(parity) { it.targetCount to it.targetHash },
        )
    }

    companion object {
        /** Same construction as `FirestoreCanonicalHash.parity`, computed independently per side. */
        internal fun parityHash(
            parity: FirestoreBackfillParity,
            side: (FirestoreBackfillCollectionParity) -> Pair<Int, String>,
        ): String {
            val text = parity.collections.sortedBy(FirestoreBackfillCollectionParity::collection).joinToString("\n") { collection ->
                val (count, hash) = side(collection)
                listOf(collection.collection, count, hash).joinToString(":")
            }
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

/**
 * `./gradlew firestoreToDynamoBackfill [-PbackfillArgs=--execute]`
 *
 * Dry-run by default. `--execute` additionally requires
 * `PORTFOLIO_DYNAMO_BACKFILL_CONFIRM=<project>:<database>-><table>` to match exactly.
 *
 * Environment: `PORTFOLIO_BACKFILL_FIRESTORE_PROJECT` (default `portfolio-476219`),
 * `PORTFOLIO_BACKFILL_FIRESTORE_DATABASE` (required, e.g. `(default)`),
 * `PORTFOLIO_BACKFILL_DYNAMODB_TABLE` (required), Google ADC or
 * `PORTFOLIO_DEV_FIRESTORE_ACCESS_TOKEN` for Firestore, and the AWS SDK default chain plus
 * `AWS_REGION` for DynamoDB.
 */
object FirestoreToDynamoBackfillCli {
    private const val CONFIRM_ENV = "PORTFOLIO_DYNAMO_BACKFILL_CONFIRM"

    @JvmStatic
    fun main(args: Array<String>) {
        val execute = when {
            args.isEmpty() || args.contentEquals(arrayOf("--dry-run")) -> false
            args.contentEquals(arrayOf("--execute")) -> true
            else -> error("Usage: firestoreToDynamoBackfill [--dry-run|--execute]")
        }
        val environment = System.getenv()
        val project = environment["PORTFOLIO_BACKFILL_FIRESTORE_PROJECT"]?.trim()?.takeIf(String::isNotEmpty) ?: "portfolio-476219"
        val database = requireNotNull(environment["PORTFOLIO_BACKFILL_FIRESTORE_DATABASE"]?.trim()?.takeIf(String::isNotEmpty)) {
            "PORTFOLIO_BACKFILL_FIRESTORE_DATABASE is required"
        }
        val table = requireNotNull(environment["PORTFOLIO_BACKFILL_DYNAMODB_TABLE"]?.trim()?.takeIf(String::isNotEmpty)) {
            "PORTFOLIO_BACKFILL_DYNAMODB_TABLE is required"
        }
        if (execute) {
            require(environment[CONFIRM_ENV] == "$project:$database->$table") { "Execution requires exact $CONFIRM_ENV" }
        }
        val firestore = FirestoreOptions.newBuilder()
            .setProjectId(project)
            .setDatabaseId(database)
            .setCredentials(PortfolioFirestoreDevBackfillCli.credentials())
            .build()
            .service
        AwsClients(environment).use { aws ->
            try {
                val store = DynamoDbPortfolioDocumentStore(aws.dynamoDb, table)
                val backfill = FirestoreToDynamoBackfill(
                    source = FirestoreBackfillReader(firestore),
                    target = DynamoBackfillReader(store),
                    write = store::replaceForBackfill,
                )
                val result = if (execute) backfill.execute() else backfill.inspect()
                result.parity.collections.forEach { parity ->
                    println(
                        listOf(
                            parity.collection,
                            "source=${parity.sourceCount}",
                            "target=${parity.targetCount}",
                            "sourceOnly=${parity.sourceOnlyCount}",
                            "targetOnly=${parity.targetOnlyCount}",
                            "different=${parity.differingCount}",
                            "sourceHash=${parity.sourceHash}",
                            "targetHash=${parity.targetHash}",
                            "ready=${parity.ready}",
                        ).joinToString("\t"),
                    )
                }
                println(
                    "summary\tmode=${if (execute) "execute" else "dry-run"}\tsource=${result.parity.sourceCount}" +
                        "\ttarget=${result.parity.targetCount}\tsourceOnly=${result.parity.sourceOnlyCount}" +
                        "\ttargetOnly=${result.parity.targetOnlyCount}\tdifferent=${result.parity.differingCount}" +
                        "\twritten=${result.writtenDocuments}\tsourceParityHash=${result.sourceParityHash}" +
                        "\ttargetParityHash=${result.targetParityHash}\tready=${result.ready}",
                )
                if (execute) check(result.ready) { "Backfill execution ended without exact parity" }
            } finally {
                firestore.close()
            }
        }
    }
}
