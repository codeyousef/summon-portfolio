package code.yousef.config

import java.net.URI
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneOffset

data class AppConfig(
    val projectId: String,
    val emulatorHost: String?,
    val port: Int,
    val useLocalStore: Boolean = false,
    val firestoreDatabaseId: String = DEFAULT_FIRESTORE_DATABASE_ID,
    val firestoreWriteMode: FirestoreWriteMode = FirestoreWriteMode.SOURCE,
    val firestoreMigrationProofId: String? = null,
    val firestoreServiceAccountJsonBase64: String? = null,
    val firestoreSeedOnStart: Boolean = false,
    val photographyUploadBucket: String? = null,
    val photographyUploadPrefix: String = "photography",
    val photographyUploadDir: Path = Path.of("storage/uploads/photography"),
    val photographyMaxUploadBytes: Long = 15_728_640,
    val photographyWriteMode: PhotographyAssetWriteMode = PhotographyAssetWriteMode.SOURCE,
    val photographyR2BaseUrl: String? = null,
    val photographyR2ReverseMirror: Boolean = false,
    val finOpsReceiptBaseUrl: String? = null,
    val finOpsReceiptMaxBytes: Long = 26_214_400,
    val finOpsCoverageStartAt: Long = 0L,
    val finOpsAllocationEntryProjectionsReady: Boolean = false,
    val finOpsShardedRollupWritesEnabled: Boolean = false,
    val finOpsShardedRollupsReady: Boolean = false,
    val registryPublicHost: String? = null,
    val registryUpstreamUrl: String? = null,
    val edgeOriginSecret: String? = null,
    val registryUpstreamIdentityEnabled: Boolean = true,
    val registryReleaseActionsUpstreamUrl: String? = null,
    val registrySecurityActionsUpstreamUrl: String? = null,
    val dataBackendSelection: PortfolioDataBackend = PortfolioDataBackend.FIRESTORE,
    val dynamoDbDocumentsTable: String? = null,
    val s3MediaBucket: String? = null,
    val s3MediaPrefix: String? = null,
    val s3DocsBucket: String? = null,
    val s3ReceiptsBucket: String? = null,
    val s3ReceiptsPrefix: String? = null,
    val cdnProxyEnabled: Boolean = false,
    val sqsJobsQueueUrl: String? = null,
    val sqsJobsVisibilityTimeoutSeconds: Int = 300,
) {
    /** The effective backend; the legacy `USE_LOCAL_STORE=true` flag selects [PortfolioDataBackend.LOCAL]. */
    val dataBackend: PortfolioDataBackend
        get() = if (useLocalStore) PortfolioDataBackend.LOCAL else dataBackendSelection

    init {
        require(!useLocalStore || dataBackendSelection != PortfolioDataBackend.DYNAMODB) {
            "USE_LOCAL_STORE=true conflicts with PORTFOLIO_DATA_BACKEND=dynamodb"
        }
        require(dataBackend != PortfolioDataBackend.DYNAMODB || !dynamoDbDocumentsTable.isNullOrBlank()) {
            "PORTFOLIO_DATA_BACKEND=dynamodb requires PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE"
        }
        require(dataBackend != PortfolioDataBackend.DYNAMODB || firestoreWriteMode == FirestoreWriteMode.SOURCE) {
            "FIRESTORE_WRITE_MODE dual/target replication is not supported with PORTFOLIO_DATA_BACKEND=dynamodb"
        }
        require(s3ReceiptsBucket == null || finOpsReceiptBaseUrl == null) {
            "Configure either PORTFOLIO_S3_RECEIPTS_BUCKET or FINOPS_RECEIPT_BASE_URL, not both"
        }
        require(!cdnProxyEnabled || s3MediaBucket != null || s3DocsBucket != null) {
            "PORTFOLIO_CDN_PROXY_ENABLED=true requires PORTFOLIO_S3_MEDIA_BUCKET or PORTFOLIO_S3_DOCS_BUCKET"
        }
        require(sqsJobsVisibilityTimeoutSeconds in 30..43_200) {
            "PORTFOLIO_SQS_JOBS_VISIBILITY_TIMEOUT_SECONDS must be between 30 and 43200"
        }
        sqsJobsQueueUrl?.let { url ->
            val uri = runCatching { URI(url) }.getOrNull()
            require(uri != null && uri.host != null && (uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_PHOTOGRAPHY_HOSTS))) {
                "PORTFOLIO_SQS_JOBS_QUEUE_URL must be an HTTPS SQS queue URL"
            }
        }
        validateFirestoreDatabaseSelection(firestoreDatabaseId, firestoreWriteMode, firestoreMigrationProofId)
        validatePhotographyStorageSelection(
            writeMode = photographyWriteMode,
            r2BaseUrl = photographyR2BaseUrl,
            s3MediaBucket = s3MediaBucket,
            reverseMirror = photographyR2ReverseMirror,
            sourceBucket = photographyUploadBucket,
        )
        validatePrivateEdgeUrl("FINOPS_RECEIPT_BASE_URL", finOpsReceiptBaseUrl)
        require(finOpsReceiptMaxBytes in 1..26_214_400L) {
            "FINOPS_RECEIPT_MAX_BYTES must be between 1 and 26214400"
        }
        require(finOpsCoverageStartAt >= 0L) { "FinOps coverage start cannot be negative" }
        require(!finOpsShardedRollupsReady || finOpsShardedRollupWritesEnabled) {
            "FINOPS_SHARDED_ROLLUPS_READY requires FINOPS_SHARDED_ROLLUP_WRITES_ENABLED=true"
        }
    }
}

fun loadAppConfig(): AppConfig = loadAppConfig(System.getenv())

internal fun loadAppConfig(env: Map<String, String>): AppConfig {
    val useLocalStore = env["USE_LOCAL_STORE"]?.toBoolean() ?: false
    val dataBackendSelection = PortfolioDataBackend.parse(env["PORTFOLIO_DATA_BACKEND"])
    require(!useLocalStore || dataBackendSelection == null || dataBackendSelection == PortfolioDataBackend.LOCAL) {
        "USE_LOCAL_STORE=true conflicts with PORTFOLIO_DATA_BACKEND=${dataBackendSelection?.environmentValue}"
    }
    val effectiveLocal = useLocalStore || dataBackendSelection == PortfolioDataBackend.LOCAL

    val projectId = env["GOOGLE_CLOUD_PROJECT"]
        ?: when {
            effectiveLocal -> "local-dev"
            dataBackendSelection == PortfolioDataBackend.DYNAMODB -> "aws"
            else -> error("Missing GOOGLE_CLOUD_PROJECT environment variable")
        }
        
    val emulatorHost = env["FIRESTORE_EMULATOR_HOST"]
    val firestoreWriteMode = FirestoreWriteMode.parse(env["FIRESTORE_WRITE_MODE"])
    val firestoreDatabaseId = env["FIRESTORE_DATABASE_ID"]
        ?.trim()
        ?.also { require(it.isNotEmpty()) { "FIRESTORE_DATABASE_ID cannot be blank" } }
        ?: DEFAULT_FIRESTORE_DATABASE_ID
    val firestoreMigrationProofId = env["FIRESTORE_MIGRATION_PROOF_ID"]
        ?.trim()
        ?.also { require(FIRESTORE_MIGRATION_PROOF_ID.matches(it)) { "FIRESTORE_MIGRATION_PROOF_ID is invalid" } }
    require(env["GOOGLE_SERVICE_ACCOUNT_JSON_B64"] == null) {
        "GOOGLE_SERVICE_ACCOUNT_JSON_B64 is unsupported; use FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64"
    }
    val firestoreServiceAccountJsonBase64 = env["FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64"]
        ?.trim()
        ?.also { require(it.isNotEmpty()) { "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 cannot be blank" } }
    val firestoreSeedOnStart = env["FIRESTORE_SEED_ON_START"]?.let { value ->
        requireNotNull(value.toBooleanStrictOrNull()) {
            "FIRESTORE_SEED_ON_START must be 'true' or 'false'"
        }
    } ?: false
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    val uploadBucket = env["PHOTOGRAPHY_UPLOAD_BUCKET"]?.trim()?.takeIf { it.isNotEmpty() }
    val uploadPrefix = env["PHOTOGRAPHY_UPLOAD_PREFIX"]?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: "photography"
    val uploadDir = Path.of(env["PHOTOGRAPHY_UPLOAD_DIR"] ?: "storage/uploads/photography")
    val maxUploadBytes = env["PHOTOGRAPHY_MAX_UPLOAD_BYTES"]?.toLongOrNull() ?: 15_728_640
    require(maxUploadBytes in 1..Int.MAX_VALUE.toLong()) {
        "PHOTOGRAPHY_MAX_UPLOAD_BYTES must be between 1 and ${Int.MAX_VALUE}"
    }
    val photographyWriteMode = PhotographyAssetWriteMode.parse(env["PHOTOGRAPHY_WRITE_MODE"])
    val photographyR2BaseUrl = env["PHOTOGRAPHY_R2_BASE_URL"]?.trim()?.takeIf { it.isNotEmpty() }
    val photographyR2ReverseMirror = env["PHOTOGRAPHY_R2_REVERSE_MIRROR"]?.let { value ->
        requireNotNull(value.toBooleanStrictOrNull()) {
            "PHOTOGRAPHY_R2_REVERSE_MIRROR must be 'true' or 'false'"
        }
    } ?: false
    val finOpsReceiptBaseUrl = env["FINOPS_RECEIPT_BASE_URL"]?.trim()?.takeIf { it.isNotEmpty() }
    val finOpsReceiptMaxBytes = env["FINOPS_RECEIPT_MAX_BYTES"]?.let { value ->
        requireNotNull(value.toLongOrNull()) { "FINOPS_RECEIPT_MAX_BYTES must be an integer" }
    } ?: 26_214_400L
    val finOpsCoverageStartAt = env["FINOPS_COVERAGE_START_DATE"]?.let { value ->
        runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrElse { throw IllegalArgumentException("FINOPS_COVERAGE_START_DATE must be YYYY-MM-DD", it) }
    } ?: 0L
    val finOpsAllocationEntryProjectionsReady = env["FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY"]?.let { value ->
        requireNotNull(value.toBooleanStrictOrNull()) {
            "FINOPS_ALLOCATION_ENTRY_PROJECTIONS_READY must be 'true' or 'false'"
        }
    } ?: false
    val finOpsShardedRollupWritesEnabled = env["FINOPS_SHARDED_ROLLUP_WRITES_ENABLED"]?.let { value ->
        requireNotNull(value.toBooleanStrictOrNull()) {
            "FINOPS_SHARDED_ROLLUP_WRITES_ENABLED must be 'true' or 'false'"
        }
    } ?: false
    val finOpsShardedRollupsReady = env["FINOPS_SHARDED_ROLLUPS_READY"]?.let { value ->
        requireNotNull(value.toBooleanStrictOrNull()) {
            "FINOPS_SHARDED_ROLLUPS_READY must be 'true' or 'false'"
        }
    } ?: false
    val edgeOriginSecret = env["PORTFOLIO_EDGE_ORIGIN_SECRET"]?.trim()?.takeIf { it.isNotEmpty() }
    val registryUpstreamUrl = env["SEEN_REGISTRY_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registryUpstreamIdentityEnabled = when (val value = env["SEEN_REGISTRY_UPSTREAM_IDENTITY"]?.trim()) {
        null, "", "google" -> true
        "none" -> false
        else -> throw IllegalArgumentException("SEEN_REGISTRY_UPSTREAM_IDENTITY must be 'google' or 'none': $value")
    }
    val registryPublicHost = env["SEEN_REGISTRY_PUBLIC_HOST"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registryReleaseActionsUpstreamUrl = env["SEEN_REGISTRY_RELEASE_ACTIONS_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registrySecurityActionsUpstreamUrl = env["SEEN_REGISTRY_SECURITY_ACTIONS_UPSTREAM_URL"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val registryBaseValues = listOf(registryPublicHost, registryUpstreamUrl)
    require(registryBaseValues.all { it == null } || registryBaseValues.all { it != null }) {
        "Registry routing requires both the public host and API upstream URL"
    }
    val registryActionValues = listOf(
        registryReleaseActionsUpstreamUrl,
        registrySecurityActionsUpstreamUrl,
    )
    require(registryActionValues.all { it == null } || registryActionValues.all { it != null }) {
        "Registry action routing requires both isolated action upstream URLs or neither"
    }
    require(registryBaseValues.all { it != null } || registryActionValues.all { it == null }) {
        "Registry action routing cannot be enabled without the public host and API upstream URL"
    }
    
    fun optional(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }
    fun strictBoolean(name: String): Boolean = env[name]?.let { value ->
        requireNotNull(value.trim().toBooleanStrictOrNull()) { "$name must be 'true' or 'false'" }
    } ?: false

    return AppConfig(
        projectId = projectId,
        emulatorHost = emulatorHost,
        port = port,
        useLocalStore = effectiveLocal,
        dataBackendSelection = dataBackendSelection ?: PortfolioDataBackend.FIRESTORE,
        dynamoDbDocumentsTable = optional("PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE"),
        s3MediaBucket = optional("PORTFOLIO_S3_MEDIA_BUCKET"),
        s3MediaPrefix = optional("PORTFOLIO_S3_MEDIA_PREFIX"),
        s3DocsBucket = optional("PORTFOLIO_S3_DOCS_BUCKET"),
        s3ReceiptsBucket = optional("PORTFOLIO_S3_RECEIPTS_BUCKET"),
        s3ReceiptsPrefix = optional("PORTFOLIO_S3_RECEIPTS_PREFIX"),
        cdnProxyEnabled = strictBoolean("PORTFOLIO_CDN_PROXY_ENABLED"),
        sqsJobsQueueUrl = optional("PORTFOLIO_SQS_JOBS_QUEUE_URL"),
        sqsJobsVisibilityTimeoutSeconds = optional("PORTFOLIO_SQS_JOBS_VISIBILITY_TIMEOUT_SECONDS")?.let {
            requireNotNull(it.toIntOrNull()) { "PORTFOLIO_SQS_JOBS_VISIBILITY_TIMEOUT_SECONDS must be an integer" }
        } ?: 300,
        firestoreDatabaseId = firestoreDatabaseId,
        firestoreWriteMode = firestoreWriteMode,
        firestoreMigrationProofId = firestoreMigrationProofId,
        firestoreServiceAccountJsonBase64 = firestoreServiceAccountJsonBase64,
        firestoreSeedOnStart = firestoreSeedOnStart,
        photographyUploadBucket = uploadBucket,
        photographyUploadPrefix = uploadPrefix,
        photographyUploadDir = uploadDir,
        photographyMaxUploadBytes = maxUploadBytes,
        photographyWriteMode = photographyWriteMode,
        photographyR2BaseUrl = photographyR2BaseUrl,
        photographyR2ReverseMirror = photographyR2ReverseMirror,
        finOpsReceiptBaseUrl = finOpsReceiptBaseUrl,
        finOpsReceiptMaxBytes = finOpsReceiptMaxBytes,
        finOpsCoverageStartAt = finOpsCoverageStartAt,
        finOpsAllocationEntryProjectionsReady = finOpsAllocationEntryProjectionsReady,
        finOpsShardedRollupWritesEnabled = finOpsShardedRollupWritesEnabled,
        finOpsShardedRollupsReady = finOpsShardedRollupsReady,
        registryPublicHost = registryPublicHost,
        registryUpstreamUrl = registryUpstreamUrl,
        edgeOriginSecret = edgeOriginSecret,
        registryUpstreamIdentityEnabled = registryUpstreamIdentityEnabled,
        registryReleaseActionsUpstreamUrl = registryReleaseActionsUpstreamUrl,
        registrySecurityActionsUpstreamUrl = registrySecurityActionsUpstreamUrl
    )
}

private fun validatePrivateEdgeUrl(name: String, value: String?) {
    value ?: return
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null &&
            uri.rawFragment == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_PHOTOGRAPHY_HOSTS)),
    ) { "$name must be an HTTPS origin/path without query or fragment" }
}

private fun validatePhotographyStorageSelection(
    writeMode: PhotographyAssetWriteMode,
    r2BaseUrl: String?,
    s3MediaBucket: String?,
    reverseMirror: Boolean,
    sourceBucket: String?,
) {
    require(r2BaseUrl == null || s3MediaBucket == null) {
        "Configure either PHOTOGRAPHY_R2_BASE_URL or PORTFOLIO_S3_MEDIA_BUCKET as the photography target, not both"
    }
    require(writeMode == PhotographyAssetWriteMode.SOURCE || r2BaseUrl != null || s3MediaBucket != null) {
        "PHOTOGRAPHY_WRITE_MODE=${writeMode.environmentValue} requires PHOTOGRAPHY_R2_BASE_URL or PORTFOLIO_S3_MEDIA_BUCKET"
    }
    r2BaseUrl?.let { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        require(
            uri != null &&
                uri.host != null &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_PHOTOGRAPHY_HOSTS)),
        ) {
            "PHOTOGRAPHY_R2_BASE_URL must be an HTTPS origin/path without query or fragment"
        }
    }
    require(writeMode != PhotographyAssetWriteMode.DUAL || sourceBucket != null) {
        "PHOTOGRAPHY_WRITE_MODE=dual requires a durable PHOTOGRAPHY_UPLOAD_BUCKET source"
    }
    require(!reverseMirror || writeMode == PhotographyAssetWriteMode.TARGET) {
        "PHOTOGRAPHY_R2_REVERSE_MIRROR may only be enabled in target mode"
    }
    require(!reverseMirror || sourceBucket != null) {
        "PHOTOGRAPHY_R2_REVERSE_MIRROR requires a durable PHOTOGRAPHY_UPLOAD_BUCKET source"
    }
}

private val LOCAL_PHOTOGRAPHY_HOSTS = setOf("localhost", "127.0.0.1", "::1")

const val DEFAULT_FIRESTORE_DATABASE_ID: String = "(default)"

private val NAMED_FIRESTORE_DATABASE_ID = Regex("[a-z][a-z0-9-]{2,61}[a-z0-9]")

private fun validateFirestoreDatabaseSelection(
    databaseId: String,
    writeMode: FirestoreWriteMode,
    migrationProofId: String?,
) {
    require(
        databaseId == DEFAULT_FIRESTORE_DATABASE_ID || NAMED_FIRESTORE_DATABASE_ID.matches(databaseId),
    ) {
        "FIRESTORE_DATABASE_ID must be '(default)' or a valid named Firestore database ID"
    }
    require(writeMode == FirestoreWriteMode.SOURCE || databaseId != DEFAULT_FIRESTORE_DATABASE_ID) {
        "FIRESTORE_WRITE_MODE=${writeMode.environmentValue} requires a named FIRESTORE_DATABASE_ID"
    }
    require(writeMode == FirestoreWriteMode.SOURCE || migrationProofId != null) {
        "FIRESTORE_WRITE_MODE=${writeMode.environmentValue} requires FIRESTORE_MIGRATION_PROOF_ID"
    }
}

private val FIRESTORE_MIGRATION_PROOF_ID = Regex("[a-z0-9][a-z0-9._-]{2,127}")
