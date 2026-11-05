package code.yousef.firestore

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.FirestoreCanonicalHash
import code.yousef.firestore.migration.PortfolioFirestoreMigrationCollections
import com.google.cloud.firestore.Firestore
import java.time.Duration
import java.time.Instant

data class FirestoreCollectionParityDifference(
    val collection: String,
    val sourceOnlyDocumentIds: Set<String>,
    val targetOnlyDocumentIds: Set<String>,
    val differingDocumentIds: Set<String>,
) {
    val isEmpty: Boolean
        get() = sourceOnlyDocumentIds.isEmpty() &&
            targetOnlyDocumentIds.isEmpty() &&
            differingDocumentIds.isEmpty()
}

/**
 * Refuses a dual/target startup until a fresh, externally computed Portfolio migration
 * proof exists identically in the source and target databases.
 *
 * This is intentionally strict: silently starting against a partial target database could
 * replace admin credentials with defaults, lose a building user, or make a valid password
 * reset token or portfolio content disappear. The external backfill utility hashes the
 * complete allowlisted business/content and migration-metadata contract, then writes the
 * resulting immutable proof to both databases. Startup verifies only those two proof
 * documents, avoiding more than 200,000 document reads on every cold start.
 * Object migrations retain their own reconciliation gates.
 */
class PortfolioFirestoreMigrationGate(
    private val source: Firestore,
    private val target: Firestore,
    private val proofId: String,
) {
    fun verify(writeMode: FirestoreWriteMode) {
        if (writeMode == FirestoreWriteMode.SOURCE) return
        val sourceFuture = source.collection(PortfolioFirestoreMigrationCollections.PROOFS).document(proofId).get()
        val targetFuture = target.collection(PortfolioFirestoreMigrationCollections.PROOFS).document(proofId).get()
        val sourceProof = sourceFuture.get()
        val targetProof = targetFuture.get()
        check(sourceProof.exists() && targetProof.exists()) {
            "FIRESTORE_WRITE_MODE=${writeMode.environmentValue} refused: migration proof $proofId is missing"
        }
        verifyPortfolioMigrationProof(
            proofId = proofId,
            sourceProject = source.options.projectId,
            sourceDatabase = source.options.databaseId,
            targetProject = target.options.projectId,
            targetDatabase = target.options.databaseId,
            sourceProof = sourceProof.data.orEmpty(),
            targetProof = targetProof.data.orEmpty(),
            maximumAge = when (writeMode) {
                FirestoreWriteMode.DUAL -> DUAL_PROOF_MAX_AGE
                FirestoreWriteMode.TARGET -> TARGET_PROOF_MAX_AGE
                FirestoreWriteMode.SOURCE -> error("source mode does not require a migration proof")
            },
        )
    }

    companion object {
        internal val DUAL_PROOF_MAX_AGE: Duration = Duration.ofDays(7)
        internal val TARGET_PROOF_MAX_AGE: Duration = Duration.ofHours(24)

        internal fun compareCollection(
            collection: String,
            source: Map<String, Map<String, Any?>>,
            target: Map<String, Map<String, Any?>>,
        ): FirestoreCollectionParityDifference {
            val sourceIds = source.keys
            val targetIds = target.keys
            val sharedIds = sourceIds intersect targetIds
            return FirestoreCollectionParityDifference(
                collection = collection,
                sourceOnlyDocumentIds = sourceIds - targetIds,
                targetOnlyDocumentIds = targetIds - sourceIds,
                differingDocumentIds = sharedIds.filterTo(linkedSetOf()) { id ->
                    source.getValue(id) != target.getValue(id)
                },
            )
        }
    }
}

internal fun verifyPortfolioMigrationProof(
    proofId: String,
    sourceProject: String,
    sourceDatabase: String,
    targetProject: String,
    targetDatabase: String,
    sourceProof: Map<String, Any?>,
    targetProof: Map<String, Any?>,
    maximumAge: Duration,
    now: Instant = Instant.now(),
) {
    require(!maximumAge.isZero && !maximumAge.isNegative) { "Migration proof maximum age must be positive" }
    check(sourceProof == targetProof) { "Migration proof $proofId differs between source and target" }
    check(sourceProof["schemaVersion"] == 1L) { "Migration proof $proofId has an unsupported schema" }
    check(sourceProof["proofId"] == proofId) { "Migration proof identity mismatch" }
    check(sourceProof["sourceProject"] == sourceProject && sourceProof["sourceDatabase"] == sourceDatabase) {
        "Migration proof source boundary mismatch"
    }
    check(sourceProof["targetProject"] == targetProject && sourceProof["targetDatabase"] == targetDatabase) {
        "Migration proof target boundary mismatch"
    }
    check(sourceProof["ready"] == true) { "Migration proof $proofId is not ready" }
    val sourceCount = sourceProof["sourceCount"] as? Long
    val targetCount = sourceProof["targetCount"] as? Long
    check(sourceCount != null && sourceCount > 0 && sourceCount == targetCount) {
        "Migration proof count evidence is invalid"
    }
    check(sourceProof["sourceOnlyCount"] == 0L && sourceProof["targetOnlyCount"] == 0L && sourceProof["differingCount"] == 0L) {
        "Migration proof difference evidence is invalid"
    }
    check(sourceProof["collectionContractHash"] == FirestoreCanonicalHash.collectionContract()) {
        "Migration proof collection contract is stale"
    }
    check((sourceProof["parityHash"] as? String)?.matches(SHA_256) == true) {
        "Migration proof parity hash is invalid"
    }
    val verifiedAt = runCatching { Instant.parse(sourceProof["verifiedAt"] as? String) }
        .getOrElse { throw IllegalStateException("Migration proof verification time is invalid", it) }
    check(!verifiedAt.isAfter(now.plus(PROOF_CLOCK_SKEW))) { "Migration proof verification time is in the future" }
    check(!verifiedAt.isBefore(now.minus(maximumAge))) { "Migration proof $proofId is stale" }
}

private val SHA_256 = Regex("[0-9a-f]{64}")
private val PROOF_CLOCK_SKEW: Duration = Duration.ofMinutes(5)
