package code.yousef.firestore.migration

import code.yousef.firestore.PortfolioFirestoreCollections
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Blob
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.GeoPoint
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * The exact top-level collection contract for the one-time Portfolio database copy.
 * Subcollections are intentionally unsupported: adding one requires an explicit migration.
 */
object PortfolioFirestoreMigrationCollections {
    const val PROOFS = "_portfolio_migration_proofs"

    val content: Set<String> = linkedSetOf(
        "projects",
        "services",
        "blog_posts",
        "testimonials",
        "hero",
        "contact_submissions",
        "photography_photos",
    )

    val migrationMetadata: Set<String> = linkedSetOf(
        FirestoreMutationBackend.RECEIPTS_COLLECTION,
        FirestoreMutationBackend.OUTBOX_COLLECTION,
        FirestoreMutationBackend.AGGREGATE_STATE_COLLECTION,
    )

    val all: Set<String> = linkedSetOf<String>().apply {
        addAll(content)
        addAll(PortfolioFirestoreCollections.all)
        addAll(migrationMetadata)
    }
}

data class FirestoreBackfillCollectionParity(
    val collection: String,
    val sourceCount: Int,
    val targetCount: Int,
    val sourceOnlyCount: Int,
    val targetOnlyCount: Int,
    val differingCount: Int,
    val sourceHash: String,
    val targetHash: String,
) {
    val ready: Boolean
        get() = sourceOnlyCount == 0 && targetOnlyCount == 0 && differingCount == 0 && sourceHash == targetHash
}

data class FirestoreBackfillParity(
    val collections: List<FirestoreBackfillCollectionParity>,
) {
    val sourceCount: Long = collections.sumOf { it.sourceCount.toLong() }
    val targetCount: Long = collections.sumOf { it.targetCount.toLong() }
    val sourceOnlyCount: Long = collections.sumOf { it.sourceOnlyCount.toLong() }
    val targetOnlyCount: Long = collections.sumOf { it.targetOnlyCount.toLong() }
    val differingCount: Long = collections.sumOf { it.differingCount.toLong() }
    val ready: Boolean = collections.all(FirestoreBackfillCollectionParity::ready)
}

data class FirestoreBackfillRepairResult(
    val writtenDocuments: Long,
    val parity: FirestoreBackfillParity,
)

data class FirestoreMigrationProof(
    val id: String,
    val data: Map<String, Any>,
)

data class FirestoreMigrationProofResult(
    val proof: FirestoreMigrationProof,
    val parity: FirestoreBackfillParity,
)

internal data class FirestoreBackfillDocument(
    val id: String,
    val data: Map<String, Any?>,
    val hash: String = FirestoreCanonicalHash.document(data),
)

internal data class FirestoreBackfillPlan(
    val writes: List<FirestoreBackfillDocument>,
    val sourceOnlyCount: Int,
    val targetOnlyCount: Int,
    val differingCount: Int,
)

internal fun planFirestoreBackfill(
    source: Map<String, FirestoreBackfillDocument>,
    target: Map<String, FirestoreBackfillDocument>,
): FirestoreBackfillPlan {
    val sourceIds = source.keys
    val targetIds = target.keys
    val sourceOnly = sourceIds - targetIds
    val targetOnly = targetIds - sourceIds
    val differing = (sourceIds intersect targetIds).filterTo(linkedSetOf()) { id ->
        source.getValue(id).hash != target.getValue(id).hash
    }
    return FirestoreBackfillPlan(
        writes = (sourceOnly + differing).sorted().map(source::getValue),
        sourceOnlyCount = sourceOnly.size,
        targetOnlyCount = targetOnly.size,
        differingCount = differing.size,
    )
}

class PortfolioFirestoreDevBackfill(
    private val source: Firestore,
    private val target: Firestore,
    private val collections: Set<String> = PortfolioFirestoreMigrationCollections.all,
    private val readPageSize: Int = 500,
    private val writeBatchSize: Int = 400,
) {
    init {
        require(collections.isNotEmpty()) { "At least one collection is required" }
        require(collections.all(COLLECTION_ID::matches)) { "Invalid Firestore collection in migration contract" }
        require(readPageSize in 1..1_000) { "Read page size must be between 1 and 1000" }
        require(writeBatchSize in 1..500) { "Write batch size must be between 1 and 500" }
    }

    fun inspect(): FirestoreBackfillParity = FirestoreBackfillParity(
        collections = collections.sorted().map { collection ->
            val sourceDocuments = loadCollection(source, collection)
            val targetDocuments = loadCollection(target, collection)
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

    /**
     * Repairs source-only and hash-different documents with full replacement writes.
     * It never deletes and fails closed if the target contains any target-only document.
     */
    fun repair(maxPasses: Int = 3): FirestoreBackfillRepairResult {
        require(maxPasses in 1..10) { "Repair passes must be between 1 and 10" }
        var written = 0L
        repeat(maxPasses) {
            val before = inspect()
            check(before.targetOnlyCount == 0L) {
                "Dev Firestore backfill refused: target contains ${before.targetOnlyCount} target-only documents"
            }
            if (before.ready) return FirestoreBackfillRepairResult(written, before)

            collections.sorted().forEach { collection ->
                val sourceDocuments = loadCollection(source, collection)
                val targetDocuments = loadCollection(target, collection)
                val plan = planFirestoreBackfill(sourceDocuments, targetDocuments)
                check(plan.targetOnlyCount == 0) {
                    "Dev Firestore backfill refused: $collection gained ${plan.targetOnlyCount} target-only documents"
                }
                plan.writes.chunked(writeBatchSize).forEach { documents ->
                    val batch = target.batch()
                    documents.forEach { document ->
                        batch.set(target.collection(collection).document(document.id), document.data)
                    }
                    batch.commit().get()
                    written = Math.addExact(written, documents.size.toLong())
                }
            }

            val after = inspect()
            check(after.targetOnlyCount == 0L) {
                "Dev Firestore backfill refused: target gained ${after.targetOnlyCount} target-only documents"
            }
            if (after.ready) return FirestoreBackfillRepairResult(written, after)
        }
        val finalParity = inspect()
        check(finalParity.ready) {
            "Dev Firestore backfill did not converge: sourceOnly=${finalParity.sourceOnlyCount}, " +
                "targetOnly=${finalParity.targetOnlyCount}, different=${finalParity.differingCount}"
        }
        return FirestoreBackfillRepairResult(written, finalParity)
    }

    fun writeProof(proofId: String): FirestoreMigrationProofResult {
        require(PROOF_ID.matches(proofId)) { "Migration proof ID is invalid" }
        val parity = inspect()
        check(parity.ready) {
            "Migration proof refused: sourceOnly=${parity.sourceOnlyCount}, " +
                "targetOnly=${parity.targetOnlyCount}, different=${parity.differingCount}"
        }
        val data = mapOf<String, Any>(
            "schemaVersion" to 1L,
            "proofId" to proofId,
            "sourceProject" to source.options.projectId,
            "sourceDatabase" to source.options.databaseId,
            "targetProject" to target.options.projectId,
            "targetDatabase" to target.options.databaseId,
            "sourceCount" to parity.sourceCount,
            "targetCount" to parity.targetCount,
            "sourceOnlyCount" to parity.sourceOnlyCount,
            "targetOnlyCount" to parity.targetOnlyCount,
            "differingCount" to parity.differingCount,
            "collectionContractHash" to FirestoreCanonicalHash.collectionContract(),
            "parityHash" to FirestoreCanonicalHash.parity(parity),
            "verifiedAt" to Instant.now().toString(),
            "ready" to true,
        )
        // Target first: a partial failure must never leave source claiming a proof
        // that the configured target does not also possess.
        target.collection(PortfolioFirestoreMigrationCollections.PROOFS).document(proofId).create(data).get()
        source.collection(PortfolioFirestoreMigrationCollections.PROOFS).document(proofId).create(data).get()
        return FirestoreMigrationProofResult(FirestoreMigrationProof(proofId, data), parity)
    }

    private fun loadCollection(
        firestore: Firestore,
        collection: String,
    ): LinkedHashMap<String, FirestoreBackfillDocument> {
        val result = linkedMapOf<String, FirestoreBackfillDocument>()
        var afterDocumentId: String? = null
        while (true) {
            var query = firestore.collection(collection)
                .orderBy(FieldPath.documentId())
                .limit(readPageSize)
            if (afterDocumentId != null) query = query.startAfter(afterDocumentId)
            val page = query.get().get().documents
            page.forEach { snapshot ->
                result[snapshot.id] = FirestoreBackfillDocument(snapshot.id, snapshot.data.orEmpty())
            }
            if (page.size < readPageSize) return result
            afterDocumentId = page.last().id
        }
    }

    companion object {
        private val COLLECTION_ID = Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$")
        private val PROOF_ID = Regex("[a-z0-9][a-z0-9._-]{2,127}")
    }
}

internal object FirestoreCanonicalHash {
    fun document(data: Map<String, Any?>): String = digest(canonical(data))

    fun collection(documents: Map<String, FirestoreBackfillDocument>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        documents.toSortedMap().forEach { (id, document) ->
            updateLengthPrefixed(digest, id)
            updateLengthPrefixed(digest, document.hash)
        }
        return digest.digest().toHex()
    }

    fun collectionContract(): String = digest(PortfolioFirestoreMigrationCollections.all.sorted().joinToString("\n"))

    fun parity(parity: FirestoreBackfillParity): String = digest(
        parity.collections.sortedBy(FirestoreBackfillCollectionParity::collection).joinToString("\n") { collection ->
            listOf(collection.collection, collection.sourceCount, collection.sourceHash).joinToString(":")
        },
    )

    private fun canonical(value: Any?): String = when (value) {
        null -> "n"
        is String -> tagged("s", value)
        is Char -> tagged("s", value.toString())
        is Boolean -> if (value) "b1" else "b0"
        is Byte, is Short, is Int, is Long -> tagged("i", value.toString())
        is Float -> tagged("f", canonicalDouble(value.toDouble()))
        is Double -> tagged("f", canonicalDouble(value))
        is BigDecimal -> tagged("d", value.stripTrailingZeros().toPlainString())
        is Timestamp -> "t${value.seconds}:${value.nanos}"
        is Instant -> tagged("t", value.toString())
        is Date -> tagged("t", value.time.toString())
        is GeoPoint -> "g${canonicalDouble(value.latitude)}:${canonicalDouble(value.longitude)}"
        is Blob -> tagged("x", Base64.getEncoder().encodeToString(value.toBytes()))
        is ByteArray -> tagged("x", Base64.getEncoder().encodeToString(value))
        is DocumentReference -> tagged("r", value.path)
        is Map<*, *> -> value.entries.map { (key, entryValue) ->
            require(key is String) { "Firestore document map keys must be strings" }
            key to entryValue
        }.sortedBy { it.first }.joinToString(prefix = "m[", postfix = "]", separator = "") { (key, entryValue) ->
            tagged("k", key) + canonical(entryValue)
        }
        is Iterable<*> -> value.joinToString(prefix = "a[", postfix = "]", separator = "", transform = ::canonical)
        is Array<*> -> value.joinToString(prefix = "a[", postfix = "]", separator = "", transform = ::canonical)
        else -> throw IllegalArgumentException(
            "Unsupported Firestore value type during parity hashing: ${value::class.qualifiedName}",
        )
    }

    private fun tagged(tag: String, value: String): String = "$tag${value.toByteArray(Charsets.UTF_8).size}:$value"

    private fun canonicalDouble(value: Double): String {
        require(value.isFinite()) { "Firestore parity hashing rejects non-finite numbers" }
        return java.lang.Double.toHexString(value)
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .toHex()

    private fun updateLengthPrefixed(digest: MessageDigest, value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8).size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(':'.code.toByte())
        digest.update(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

object PortfolioFirestoreDevBackfillCli {
    private const val PROJECT = "portfolio-476219"
    private const val SOURCE_DATABASE = "(default)"
    private const val TARGET_DATABASE = "portfolio-me-dev"
    private const val EXECUTION_CONFIRMATION = "portfolio-476219:(default)->portfolio-me-dev"

    @JvmStatic
    fun main(args: Array<String>) {
        val proofId = when {
            args.size == 2 && args[0] == "--write-proof" -> args[1]
            args.size == 1 && args[0].startsWith("--write-proof=") -> args[0].substringAfter('=')
            else -> null
        }
        val mode = when {
            args.isEmpty() || args.contentEquals(arrayOf("--dry-run")) -> "dry-run"
            args.contentEquals(arrayOf("--execute")) -> "execute"
            proofId != null -> "write-proof"
            else -> error("Usage: firestoreDevBackfill [--dry-run|--execute|--write-proof <unique-proof-id>]")
        }
        if (mode != "dry-run") {
            require(System.getenv("PORTFOLIO_DEV_FIRESTORE_BACKFILL_CONFIRM") == EXECUTION_CONFIRMATION) {
                "Execution requires exact PORTFOLIO_DEV_FIRESTORE_BACKFILL_CONFIRM"
            }
        }

        val credentials = GoogleCredentials.getApplicationDefault()
        val source = firestore(credentials, SOURCE_DATABASE)
        val target = firestore(credentials, TARGET_DATABASE)
        try {
            val backfill = PortfolioFirestoreDevBackfill(source, target)
            val proofResult = if (mode == "write-proof") backfill.writeProof(requireNotNull(proofId)) else null
            val result = when (mode) {
                "execute" -> backfill.repair()
                "write-proof" -> FirestoreBackfillRepairResult(0, requireNotNull(proofResult).parity)
                else -> FirestoreBackfillRepairResult(0, backfill.inspect())
            }
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
                "summary\tsource=${result.parity.sourceCount}\ttarget=${result.parity.targetCount}" +
                    "\tsourceOnly=${result.parity.sourceOnlyCount}\ttargetOnly=${result.parity.targetOnlyCount}" +
                    "\tdifferent=${result.parity.differingCount}\twritten=${result.writtenDocuments}" +
                    "\tready=${result.parity.ready}",
            )
            if (mode == "write-proof") {
                val proof = requireNotNull(proofResult).proof
                println("proof\tid=${proof.id}\tparityHash=${proof.data.getValue("parityHash")}\tready=true")
            }
            if (mode != "dry-run") check(result.parity.ready) { "Backfill execution ended without exact parity" }
        } finally {
            source.close()
            target.close()
        }
    }

    private fun firestore(credentials: GoogleCredentials, databaseId: String): Firestore = FirestoreOptions.newBuilder()
        .setProjectId(PROJECT)
        .setDatabaseId(databaseId)
        .setCredentials(credentials)
        .build()
        .service
}
