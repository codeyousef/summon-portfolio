package code.yousef.portfolio.ai

import code.yousef.firestore.retry
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirestoreAiProgressStore(private val firestore: Firestore) : AiProgressStore {

    private val docRef = firestore.collection("ai_curriculum").document("progress")

    override suspend fun getProgress(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val snapshot = retry { docRef.get().get() }
        if (!snapshot.exists()) return@withContext emptyMap()
        snapshot.data?.mapValues { (_, v) -> v as? Boolean ?: false } ?: emptyMap()
    }

    override suspend fun updateProgress(subsectionId: String, completed: Boolean) {
        withContext(Dispatchers.IO) {
            retry {
                docRef.set(mapOf(subsectionId to completed), SetOptions.merge()).get()
            }
        }
    }
}
