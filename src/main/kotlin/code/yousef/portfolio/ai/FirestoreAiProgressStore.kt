package code.yousef.portfolio.ai

import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.retry
import code.yousef.firestore.sourcePortfolioFirestoreStore
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirestoreAiProgressStore(
    private val store: PortfolioFirestoreStore,
) : AiProgressStore {
    constructor(firestore: Firestore) : this(sourcePortfolioFirestoreStore(firestore))

    private val collection = PortfolioFirestoreCollections.AI_CURRICULUM
    private val documentId = "progress"

    override suspend fun getProgress(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val document = retry { store.get(collection, documentId) }
            ?: return@withContext emptyMap()
        document.data.mapValues { (_, value) -> value as? Boolean ?: false }
    }

    override suspend fun updateProgress(subsectionId: String, completed: Boolean) {
        withContext(Dispatchers.IO) {
            retry {
                store.merge(collection, documentId, mapOf(subsectionId to completed))
            }
        }
    }
}
