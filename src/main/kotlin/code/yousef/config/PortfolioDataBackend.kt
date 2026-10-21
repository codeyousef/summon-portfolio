package code.yousef.config

/** `PORTFOLIO_DATA_BACKEND`: the authoritative store for Portfolio documents. */
enum class PortfolioDataBackend(val environmentValue: String) {
    FIRESTORE("firestore"),
    LOCAL("local"),
    DYNAMODB("dynamodb");

    companion object {
        fun parse(value: String?): PortfolioDataBackend? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.environmentValue == normalized }
                ?: throw IllegalArgumentException(
                    "PORTFOLIO_DATA_BACKEND must be one of: ${entries.joinToString { it.environmentValue }}",
                )
        }
    }
}
