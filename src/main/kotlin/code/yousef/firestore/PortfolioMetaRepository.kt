package code.yousef.firestore

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PortfolioMetaRepository(
    private val store: PortfolioFirestoreStore,
) {
    constructor(firestore: Firestore) : this(sourcePortfolioFirestoreStore(firestore))

    private val collection = PortfolioFirestoreCollections.META

    suspend fun putNow(id: String, data: Map<String, Any>) = withContext(Dispatchers.IO) {
        retry { store.upsert(collection, id, data) }
    }

    suspend fun getNow(id: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        retry {
            store.get(collection, id)?.data?.mapNotNull { (key, value) ->
                value?.let { key to it }
            }?.toMap()
        }
    }
}
