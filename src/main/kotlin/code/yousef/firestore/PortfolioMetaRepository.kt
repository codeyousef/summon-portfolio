package code.yousef.firestore

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PortfolioMetaRepository(private val firestore: Firestore) {
    private val collection = firestore.collection("portfolio_meta")

    suspend fun putNow(id: String, data: Map<String, Any>) = withContext(Dispatchers.IO) {
        retry { collection.document(id).set(data).get() }
    }

    suspend fun getNow(id: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val snapshot = retry { collection.document(id).get().get() }
        if (snapshot.exists()) snapshot.data else null
    }
}
