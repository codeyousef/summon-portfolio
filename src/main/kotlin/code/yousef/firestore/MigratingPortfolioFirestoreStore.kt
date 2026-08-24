package code.yousef.firestore

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.FirestoreMutationBackend
import code.yousef.firestore.migration.MutationCoordinator
import code.yousef.firestore.migration.RevisionConflictException
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Query

/** Every Firestore collection owned by Portfolio but not by [FirestoreContentStore]. */
object PortfolioFirestoreCollections {
    const val META = "portfolio_meta"
    const val ADMIN_SETTINGS = "admin_settings"
    const val BUILDING_USERS = "building_users"
    const val BUILDING_PASSWORD_RESET_TOKENS = "building_password_reset_tokens"
    const val BUILDINGS = "buildings"
    const val BUILDING_UNITS = "building_units"
    const val BUILDING_TENANTS = "building_tenants"
    const val BUILDING_LEASES = "building_leases"
    const val BUILDING_PAYMENTS = "building_payments"
    const val AI_CURRICULUM = "ai_curriculum"
    const val FINOPS_COST_ENTRIES = "finops_cost_entries"
    const val FINOPS_ALLOCATIONS = "finops_allocations"
    const val FINOPS_ALLOCATION_ENTRY_PROJECTIONS = "finops_allocation_entry_projections"
    const val FINOPS_RECEIPT_ATTACHMENTS = "finops_receipt_attachments"
    const val FINOPS_DAILY_ROLLUPS = "finops_daily_rollups"
    const val FINOPS_DAILY_ROLLUPS_V2 = "finops_daily_rollups_v2"
    const val FINOPS_USER_DAILY_ROLLUPS = "finops_user_daily_rollups"
    const val FINOPS_USER_DAILY_ROLLUPS_V2 = "finops_user_daily_rollups_v2"
    const val FINOPS_SAMURAI_USER_INDEX = "finops_samurai_user_index"
    const val FINOPS_IMPORT_RUNS = "finops_import_runs"
    const val FINOPS_RECONCILIATIONS = "finops_reconciliations"
    const val FINOPS_RECONCILIATIONS_V2 = "finops_reconciliations_v2"
    const val FINOPS_BUDGETS = "finops_budgets"
    const val FINOPS_FX_RATES = "finops_fx_rates"
    const val FINOPS_RECURRING_EXPENSES = "finops_recurring_expenses"
    const val FINOPS_AUDIT = "finops_audit"

    val all: Set<String> = linkedSetOf(
        META,
        ADMIN_SETTINGS,
        BUILDING_USERS,
        BUILDING_PASSWORD_RESET_TOKENS,
        BUILDINGS,
        BUILDING_UNITS,
        BUILDING_TENANTS,
        BUILDING_LEASES,
        BUILDING_PAYMENTS,
        AI_CURRICULUM,
        FINOPS_COST_ENTRIES,
        FINOPS_ALLOCATIONS,
        FINOPS_ALLOCATION_ENTRY_PROJECTIONS,
        FINOPS_RECEIPT_ATTACHMENTS,
        FINOPS_DAILY_ROLLUPS,
        FINOPS_DAILY_ROLLUPS_V2,
        FINOPS_USER_DAILY_ROLLUPS,
        FINOPS_USER_DAILY_ROLLUPS_V2,
        FINOPS_SAMURAI_USER_INDEX,
        FINOPS_IMPORT_RUNS,
        FINOPS_RECONCILIATIONS,
        FINOPS_RECONCILIATIONS_V2,
        FINOPS_BUDGETS,
        FINOPS_FX_RATES,
        FINOPS_RECURRING_EXPENSES,
        FINOPS_AUDIT,
    )
}

data class PortfolioFirestoreDocument(
    val id: String,
    val data: Map<String, Any?>,
)

data class PortfolioFirestorePage(
    val documents: List<PortfolioFirestoreDocument>,
    val hasMore: Boolean,
)

/**
 * The single migration-aware boundary for non-content Portfolio data.
 *
 * Reads always use the database selected as authority by [FirestoreDatabases]. Writes
 * always pass through the mutation coordinator, so dual mode mirrors source to target
 * and target mode reverse-mirrors target to source with the same durable outbox used by
 * portfolio content.
 */
interface PortfolioFirestoreStore {
    val writeMode: FirestoreWriteMode

    fun get(collection: String, documentId: String): PortfolioFirestoreDocument?
    fun getMany(collection: String, documentIds: Collection<String>): List<PortfolioFirestoreDocument> =
        documentIds.distinct().mapNotNull { documentId -> get(collection, documentId) }
    fun list(collection: String): List<PortfolioFirestoreDocument>
    fun whereEqualTo(
        collection: String,
        filters: Map<String, Any>,
    ): List<PortfolioFirestoreDocument>

    fun whereIn(
        collection: String,
        field: String,
        values: List<Any>,
    ): List<PortfolioFirestoreDocument> {
        require(values.isNotEmpty()) { "At least one value is required" }
        return list(collection).filter { it.data[field] in values }
    }

    fun whereLessThan(
        collection: String,
        field: String,
        value: Any,
    ): List<PortfolioFirestoreDocument>

    fun rangePage(
        collection: String,
        rangeField: String,
        fromInclusive: Long,
        toExclusive: Long,
        equalityFilters: Map<String, Any>,
        limit: Int,
        afterValue: Long? = null,
        afterDocumentId: String? = null,
        descending: Boolean = true,
    ): PortfolioFirestorePage = PortfolioFirestorePage(
        documents = list(collection)
            .filter { document ->
                val value = (document.data[rangeField] as? Number)?.toLong()
                value != null && value in fromInclusive until toExclusive &&
                    equalityFilters.all { (field, expected) -> document.data[field] == expected }
            }
            .sortedWith(if (descending) {
                compareByDescending<PortfolioFirestoreDocument> { (it.data[rangeField] as Number).toLong() }
                    .thenByDescending(PortfolioFirestoreDocument::id)
            } else {
                compareBy<PortfolioFirestoreDocument> { (it.data[rangeField] as Number).toLong() }
                    .thenBy(PortfolioFirestoreDocument::id)
            })
            .let { ordered ->
                if (afterValue == null || afterDocumentId == null) ordered
                else ordered.dropWhile { document ->
                    val value = (document.data[rangeField] as Number).toLong()
                    if (descending) {
                        value > afterValue || (value == afterValue && document.id >= afterDocumentId)
                    } else {
                        value < afterValue || (value == afterValue && document.id <= afterDocumentId)
                    }
                }
            }
            .take(limit + 1),
        hasMore = false,
    ).let { page ->
        PortfolioFirestorePage(page.documents.take(limit), page.documents.size > limit)
    }

    fun stringPage(
        collection: String,
        orderField: String,
        limit: Int,
        afterValue: String? = null,
        afterDocumentId: String? = null,
        descending: Boolean = false,
    ): PortfolioFirestorePage = PortfolioFirestorePage(
        documents = list(collection)
            .filter { it.data[orderField] is String }
            .sortedWith(if (descending) {
                compareByDescending<PortfolioFirestoreDocument> { it.data[orderField] as String }
                    .thenByDescending(PortfolioFirestoreDocument::id)
            } else {
                compareBy<PortfolioFirestoreDocument> { it.data[orderField] as String }
                    .thenBy(PortfolioFirestoreDocument::id)
            })
            .let { ordered ->
                if (afterValue == null || afterDocumentId == null) ordered
                else ordered.dropWhile { document ->
                    val value = document.data[orderField] as String
                    if (descending) {
                        value > afterValue || (value == afterValue && document.id >= afterDocumentId)
                    } else {
                        value < afterValue || (value == afterValue && document.id <= afterDocumentId)
                    }
                }
            }
            .take(limit + 1),
        hasMore = false,
    ).let { page ->
        PortfolioFirestorePage(page.documents.take(limit), page.documents.size > limit)
    }

    fun upsert(collection: String, documentId: String, data: Map<String, Any?>)
    fun insertIfAbsent(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        mutationId: String,
    ): Boolean {
        if (get(collection, documentId) != null) return false
        upsert(collection, documentId, data)
        return true
    }
    fun merge(collection: String, documentId: String, data: Map<String, Any?>)
    fun accumulate(
        collection: String,
        documentId: String,
        dimensions: Map<String, Any>,
        increments: Map<String, Long>,
        mutationId: String,
    ) {
        error("Atomic accumulation is not supported by this store")
    }
    fun delete(collection: String, documentId: String)
}

class MigratingPortfolioFirestoreStore(
    private val authority: Firestore,
    private val mutationCoordinator: MutationCoordinator,
) : PortfolioFirestoreStore {
    override val writeMode: FirestoreWriteMode = mutationCoordinator.writeMode

    override fun get(collection: String, documentId: String): PortfolioFirestoreDocument? {
        val snapshot = authority.collection(collection).document(documentId).get().get()
        return snapshot.takeIf { it.exists() }?.let {
            PortfolioFirestoreDocument(it.id, it.data.orEmpty())
        }
    }

    override fun getMany(collection: String, documentIds: Collection<String>): List<PortfolioFirestoreDocument> {
        val references = documentIds.distinct().map { documentId -> authority.collection(collection).document(documentId) }
        if (references.isEmpty()) return emptyList()
        return authority.getAll(*references.toTypedArray()).get()
            .filter { it.exists() }
            .map { PortfolioFirestoreDocument(it.id, it.data.orEmpty()) }
    }

    override fun list(collection: String): List<PortfolioFirestoreDocument> =
        authority.collection(collection).get().get().documents.map { snapshot ->
            PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty())
        }

    override fun whereEqualTo(
        collection: String,
        filters: Map<String, Any>,
    ): List<PortfolioFirestoreDocument> {
        require(filters.isNotEmpty()) { "At least one equality filter is required" }
        var query: Query = authority.collection(collection)
        filters.toSortedMap().forEach { (field, value) ->
            query = query.whereEqualTo(field, value)
        }
        return query.get().get().documents.map { snapshot ->
            PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty())
        }
    }

    override fun whereIn(
        collection: String,
        field: String,
        values: List<Any>,
    ): List<PortfolioFirestoreDocument> {
        require(values.size in 1..30) { "Firestore whereIn supports 1 to 30 values per query" }
        return authority.collection(collection)
            .whereIn(field, values)
            .get()
            .get()
            .documents
            .map { snapshot -> PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty()) }
    }

    override fun whereLessThan(
        collection: String,
        field: String,
        value: Any,
    ): List<PortfolioFirestoreDocument> = authority.collection(collection)
        .whereLessThan(field, value)
        .get()
        .get()
        .documents
        .map { snapshot -> PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty()) }

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
        var query: Query = authority.collection(collection)
            .whereGreaterThanOrEqualTo(rangeField, fromInclusive)
            .whereLessThan(rangeField, toExclusive)
        equalityFilters.toSortedMap().forEach { (field, value) -> query = query.whereEqualTo(field, value) }
        val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        query = query.orderBy(rangeField, direction)
            .orderBy(FieldPath.documentId(), direction)
        if (afterValue != null && afterDocumentId != null) query = query.startAfter(afterValue, afterDocumentId)
        val documents = query.limit(limit + 1).get().get().documents.map { snapshot ->
            PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty())
        }
        return PortfolioFirestorePage(documents.take(limit), documents.size > limit)
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
        val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        var query = authority.collection(collection)
            .orderBy(orderField, direction)
            .orderBy(FieldPath.documentId(), direction)
        if (afterValue != null && afterDocumentId != null) query = query.startAfter(afterValue, afterDocumentId)
        val documents = query.limit(limit + 1).get().get().documents.map { snapshot ->
            PortfolioFirestoreDocument(snapshot.id, snapshot.data.orEmpty())
        }
        return PortfolioFirestorePage(documents.take(limit), documents.size > limit)
    }

    override fun upsert(collection: String, documentId: String, data: Map<String, Any?>) {
        mutationCoordinator.apply(mutationCoordinator.newUpsert(collection, documentId, data))
    }

    override fun insertIfAbsent(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        mutationId: String,
    ): Boolean = try {
        mutationCoordinator.apply(
            mutationCoordinator.newUpsert(
                aggregateType = collection,
                aggregateId = documentId,
                payload = data,
                expectedRevision = 0L,
                id = mutationId,
            ),
        )
        true
    } catch (_: RevisionConflictException) {
        false
    }

    override fun merge(collection: String, documentId: String, data: Map<String, Any?>) {
        mutationCoordinator.apply(mutationCoordinator.newMerge(collection, documentId, data))
    }

    override fun accumulate(
        collection: String,
        documentId: String,
        dimensions: Map<String, Any>,
        increments: Map<String, Long>,
        mutationId: String,
    ) {
        mutationCoordinator.apply(
            mutationCoordinator.newAccumulate(collection, documentId, dimensions, increments, id = mutationId),
        )
    }

    override fun delete(collection: String, documentId: String) {
        mutationCoordinator.apply(mutationCoordinator.newDelete(collection, documentId))
    }
}

/** Compatibility path for source-only callers outside the application composition root. */
fun sourcePortfolioFirestoreStore(firestore: Firestore): PortfolioFirestoreStore =
    MigratingPortfolioFirestoreStore(
        authority = firestore,
        mutationCoordinator = MutationCoordinator(
            writeMode = FirestoreWriteMode.SOURCE,
            source = FirestoreMutationBackend(firestore),
            target = null,
        ),
    )
