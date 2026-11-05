package code.yousef.config

enum class FirestoreWriteMode(val environmentValue: String) {
    SOURCE("source"),
    DUAL("dual"),
    TARGET("target");

    companion object {
        fun parse(value: String?): FirestoreWriteMode {
            val normalized = value?.trim() ?: SOURCE.environmentValue
            return entries.firstOrNull { it.environmentValue == normalized }
                ?: throw IllegalArgumentException(
                    "FIRESTORE_WRITE_MODE must be one of: " +
                        entries.joinToString(separator = ", ") { it.environmentValue },
                )
        }
    }
}
