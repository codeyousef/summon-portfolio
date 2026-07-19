package codes.yousef.seen.registry

internal const val TUF_METADATA_EXPIRY_EVENT = "tuf_metadata_expiry_breach"
internal const val TUF_SIGNING_REJECTED_EVENT = "tuf_signing_rejected"

internal fun Throwable.tufMetadataExpiryEvent(
    environment: String,
    runtime: String,
): String? {
    require(environment == "development" || environment == "production")
    require(Regex("^[a-z0-9][a-z0-9-]{0,95}$").matches(runtime))
    val expired = generateSequence(this) { it.cause }
        .mapNotNull(Throwable::message)
        .any { it.contains("expired", ignoreCase = true) }
    if (!expired) return null
    val failure = this::class.simpleName
        ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
        ?.take(96)
        .orEmpty()
        .ifEmpty { "Throwable" }
    return "event=$TUF_METADATA_EXPIRY_EVENT environment=$environment runtime=$runtime failure=$failure"
}

internal fun tufSigningRejectedEvent(
    role: String,
    operation: String,
    reason: String,
): String {
    val safeRole = role.replace(Regex("[^a-z0-9_-]"), "_").take(32)
    val safeOperation = operation.replace(Regex("[^a-z0-9:_-]"), "_").take(64)
    val safeReason = reason.trim().replace(Regex("\\s+"), "_")
        .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
        .take(96)
    return "event=$TUF_SIGNING_REJECTED_EVENT role=$safeRole operation=$safeOperation reason=$safeReason"
}
