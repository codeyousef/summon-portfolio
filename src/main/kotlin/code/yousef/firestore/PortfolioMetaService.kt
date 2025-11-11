package code.yousef.firestore

class PortfolioMetaService(
    private val repository: PortfolioMetaRepository
) {
    suspend fun touchHello(updatedAtMillis: Long = System.currentTimeMillis()) {
        repository.putNow(
            id = "hello",
            data = mapOf("updatedAt" to updatedAtMillis)
        )
    }

    suspend fun fetchHello(): Map<String, Any>? = repository.getNow("hello")
}
