package code.yousef.config

enum class PhotographyAssetWriteMode(val environmentValue: String) {
    SOURCE("source"),
    DUAL("dual"),
    TARGET("target");

    companion object {
        fun parse(value: String?): PhotographyAssetWriteMode {
            val normalized = value?.trim().orEmpty()
            if (normalized.isEmpty()) return SOURCE
            return entries.firstOrNull { it.environmentValue == normalized }
                ?: throw IllegalArgumentException(
                    "PHOTOGRAPHY_WRITE_MODE must be one of: ${entries.joinToString { it.environmentValue }}",
                )
        }
    }
}
