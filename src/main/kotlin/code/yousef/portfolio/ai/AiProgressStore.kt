package code.yousef.portfolio.ai

interface AiProgressStore {
    suspend fun getProgress(): Map<String, Boolean>
    suspend fun updateProgress(subsectionId: String, completed: Boolean)
}
