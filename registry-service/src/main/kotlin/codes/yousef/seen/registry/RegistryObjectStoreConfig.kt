package codes.yousef.seen.registry

import java.net.URI

enum class RegistryObjectStoreProvider(val environmentValue: String) {
    GCS("gcs"),
    R2("r2"),
    /**
     * Native AWS S3. Credentials come only from the default AWS provider chain
     * (the ECS task role); the region comes from `AWS_REGION`.
     */
    S3("s3"),
    ;

    companion object {
        fun fromEnvironment(value: String): RegistryObjectStoreProvider = entries.firstOrNull {
            it.environmentValue == value
        } ?: throw IllegalArgumentException("REGISTRY_OBJECT_STORE_PROVIDER must be gcs, r2, or s3")
    }
}

/**
 * Logical bucket roles are kept independent from provider-specific bucket
 * identifiers. A runtime receives only the roles it needs; migration and
 * backup tooling can request all six without widening an API or signer
 * workload's storage capability.
 */
enum class RegistryBucketRole(
    val environmentName: String,
    internal val contentNamespace: String,
) {
    QUARANTINE("REGISTRY_QUARANTINE_BUCKET", "quarantine/objects"),
    PUBLIC("REGISTRY_PUBLIC_BUCKET", "blobs"),
    METADATA("REGISTRY_METADATA_BUCKET", "metadata/objects"),
    PRIVATE("REGISTRY_PRIVATE_BUCKET", "private"),
    EVIDENCE("REGISTRY_EVIDENCE_BUCKET", "evidence"),
    BACKUP("REGISTRY_BACKUP_BUCKET", "backup"),
}

data class RegistryBucketBindings(
    private val values: Map<RegistryBucketRole, String>,
) {
    init {
        require(values.values.all { it.isNotBlank() }) { "Registry bucket names must not be blank" }
    }

    operator fun get(role: RegistryBucketRole): String? = values[role]

    fun require(role: RegistryBucketRole): String = requireNotNull(values[role]) {
        "${role.environmentName} is required"
    }

    val roles: Set<RegistryBucketRole> get() = values.keys.toSet()
    internal val names: Collection<String> get() = values.values

    companion object {
        fun of(vararg bindings: Pair<RegistryBucketRole, String?>): RegistryBucketBindings =
            RegistryBucketBindings(bindings.mapNotNull { (role, value) ->
                value?.takeIf(String::isNotBlank)?.let { role to it }
            }.toMap())
    }
}

/** Keeps R2 API credentials redacted from config dumps and exception output. */
class RegistryObjectStoreCredentials internal constructor(
    internal val accessKeyId: String,
    internal val secretAccessKey: String,
) {
    init {
        require(accessKeyId.isNotBlank() && accessKeyId.none(Char::isWhitespace)) {
            "REGISTRY_R2_ACCESS_KEY_ID must be non-blank and contain no whitespace"
        }
        require(secretAccessKey.isNotBlank() && secretAccessKey.none(Char::isWhitespace)) {
            "REGISTRY_R2_SECRET_ACCESS_KEY must be non-blank and contain no whitespace"
        }
    }

    override fun toString(): String = "RegistryObjectStoreCredentials([redacted])"
}

data class RegistryObjectStoreConfig(
    val provider: RegistryObjectStoreProvider,
    val buckets: RegistryBucketBindings,
    val endpoint: URI? = null,
    val region: String? = null,
    val credentials: RegistryObjectStoreCredentials? = null,
) {
    init {
        when (provider) {
            RegistryObjectStoreProvider.GCS -> require(endpoint == null && region == null && credentials == null) {
                "GCS object storage must not receive R2 endpoint, region, or credentials"
            }
            RegistryObjectStoreProvider.R2 -> {
                val r2Endpoint = requireNotNull(endpoint) { "REGISTRY_R2_ENDPOINT is required for R2 object storage" }
                require(
                    r2Endpoint.isAbsolute && r2Endpoint.scheme == "https" && r2Endpoint.host != null &&
                        r2Endpoint.rawUserInfo == null && r2Endpoint.rawQuery == null &&
                        r2Endpoint.rawFragment == null && r2Endpoint.path.orEmpty().trim('/').isEmpty(),
                ) { "REGISTRY_R2_ENDPOINT must be an origin-only absolute HTTPS URI" }
                require(r2Endpoint.host.endsWith(".r2.cloudflarestorage.com")) {
                    "REGISTRY_R2_ENDPOINT must use the Cloudflare R2 S3 endpoint"
                }
                require(region == "auto") { "REGISTRY_R2_REGION must be auto" }
                requireNotNull(credentials) { "R2 object storage credentials are required" }
                require(buckets.names.toSet().size == buckets.names.size) {
                    "R2 logical bucket roles must use distinct buckets"
                }
                buckets.names.forEach(::requireR2BucketName)
            }
            RegistryObjectStoreProvider.S3 -> {
                require(endpoint == null && credentials == null) {
                    "S3 object storage must use the default AWS credential chain and regional endpoint"
                }
                val awsRegion = requireNotNull(region) { "AWS_REGION is required for S3 object storage" }
                require(AWS_REGION_NAME.matches(awsRegion)) { "AWS_REGION is invalid" }
                require(buckets.names.toSet().size == buckets.names.size) {
                    "S3 logical bucket roles must use distinct buckets"
                }
                buckets.names.forEach(::requireS3BucketName)
            }
        }
    }

    fun requireRoles(expected: Set<RegistryBucketRole>): RegistryObjectStoreConfig {
        require(buckets.roles == expected) {
            val missing = (expected - buckets.roles).map(RegistryBucketRole::environmentName).sorted()
            val excess = (buckets.roles - expected).map(RegistryBucketRole::environmentName).sorted()
            "Object storage bucket capability mismatch; missing=${missing.joinToString(",").ifEmpty { "none" }} " +
                "excess=${excess.joinToString(",").ifEmpty { "none" }}"
        }
        return this
    }

    companion object {
        fun fromEnvironment(
            env: Map<String, String>,
            requiredRoles: Set<RegistryBucketRole>,
            defaultProvider: RegistryObjectStoreProvider = RegistryObjectStoreProvider.GCS,
        ): RegistryObjectStoreConfig {
            val provider = RegistryObjectStoreProvider.fromEnvironment(
                env["REGISTRY_OBJECT_STORE_PROVIDER"]?.trim()?.ifEmpty { null } ?: defaultProvider.environmentValue,
            )
            val supplied = RegistryBucketRole.entries.mapNotNull { role ->
                if (!env.containsKey(role.environmentName)) return@mapNotNull null
                val value = env.getValue(role.environmentName).trim()
                require(value.isNotEmpty()) { "${role.environmentName} must not be blank" }
                role to value
            }.toMap()
            val buckets = RegistryBucketBindings(supplied)
            val missing = requiredRoles - buckets.roles
            require(missing.isEmpty()) {
                "Missing object storage buckets: ${missing.map(RegistryBucketRole::environmentName).sorted().joinToString(", ")}"
            }
            val excess = buckets.roles - requiredRoles
            require(excess.isEmpty()) {
                "Runtime received unrelated object storage buckets: " +
                    excess.map(RegistryBucketRole::environmentName).sorted().joinToString(", ")
            }

            return when (provider) {
                RegistryObjectStoreProvider.GCS -> {
                    val forbidden = R2_ENVIRONMENT_NAMES.filter(env::containsKey)
                    require(forbidden.isEmpty()) {
                        "GCS object storage rejects R2 configuration: ${forbidden.sorted().joinToString(", ")}"
                    }
                    RegistryObjectStoreConfig(provider, buckets)
                }
                RegistryObjectStoreProvider.R2 -> {
                    val endpoint = env["REGISTRY_R2_ENDPOINT"]?.trim()?.takeIf(String::isNotEmpty)
                        ?.let(::parseR2Endpoint)
                        ?: throw IllegalArgumentException("REGISTRY_R2_ENDPOINT is required for R2 object storage")
                    val accessKeyId = env["REGISTRY_R2_ACCESS_KEY_ID"]?.trim()
                        ?: throw IllegalArgumentException("REGISTRY_R2_ACCESS_KEY_ID is required for R2 object storage")
                    val secretAccessKey = env["REGISTRY_R2_SECRET_ACCESS_KEY"]?.trim()
                        ?: throw IllegalArgumentException("REGISTRY_R2_SECRET_ACCESS_KEY is required for R2 object storage")
                    RegistryObjectStoreConfig(
                        provider = provider,
                        buckets = buckets,
                        endpoint = endpoint,
                        region = env["REGISTRY_R2_REGION"]?.trim()?.ifEmpty { null } ?: "auto",
                        credentials = RegistryObjectStoreCredentials(accessKeyId, secretAccessKey),
                    )
                }
                RegistryObjectStoreProvider.S3 -> {
                    val forbidden = R2_ENVIRONMENT_NAMES.filter(env::containsKey)
                    require(forbidden.isEmpty()) {
                        "S3 object storage rejects R2 endpoint and static credential configuration: " +
                            forbidden.sorted().joinToString(", ")
                    }
                    RegistryObjectStoreConfig(
                        provider = provider,
                        buckets = buckets,
                        region = env["AWS_REGION"]?.trim()?.ifEmpty { null }
                            ?: throw IllegalArgumentException("AWS_REGION is required for S3 object storage"),
                    )
                }
            }.requireRoles(requiredRoles)
        }

        fun legacyGcs(
            quarantineBucket: String? = null,
            publicBucket: String? = null,
            metadataBucket: String? = null,
        ): RegistryObjectStoreConfig = RegistryObjectStoreConfig(
            provider = RegistryObjectStoreProvider.GCS,
            buckets = RegistryBucketBindings.of(
                RegistryBucketRole.QUARANTINE to quarantineBucket,
                RegistryBucketRole.PUBLIC to publicBucket,
                RegistryBucketRole.METADATA to metadataBucket,
            ),
        )
    }
}

internal object RegistryObjectKeys {
    fun contentAddressed(prefix: String, role: RegistryBucketRole, digest: String): String {
        IdentityRules.requireDigest(digest, "object digest")
        return "${requireObjectPrefix(prefix)}/${role.contentNamespace}/sha256/$digest"
    }

    fun quarantineUpload(prefix: String, uploadId: String): String {
        require(UPLOAD_ID.matches(uploadId)) { "Invalid quarantine upload ID" }
        return "${requireObjectPrefix(prefix)}/quarantine/$uploadId"
    }

    fun metadata(prefix: String, filename: String): String {
        requireReadableMetadata(filename)
        return "${requireObjectPrefix(prefix)}/metadata/$filename"
    }
}

internal val REGISTRY_OBJECT_STORE_BUCKET_ENVIRONMENT_NAMES: Set<String> =
    RegistryBucketRole.entries.mapTo(mutableSetOf(), RegistryBucketRole::environmentName)

internal val REGISTRY_R2_ENVIRONMENT_NAMES: Set<String> = setOf(
    "REGISTRY_R2_ENDPOINT",
    "REGISTRY_R2_REGION",
    "REGISTRY_R2_ACCESS_KEY_ID",
    "REGISTRY_R2_SECRET_ACCESS_KEY",
)

private val R2_ENVIRONMENT_NAMES = REGISTRY_R2_ENVIRONMENT_NAMES
private val UPLOAD_ID = Regex("^upl_[A-Za-z0-9_-]{16,96}$")
private val OBJECT_PREFIX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9._/-]{0,254}[A-Za-z0-9])?$")
private val R2_BUCKET = Regex("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$")

private fun requireObjectPrefix(value: String): String {
    val prefix = value.trim('/')
    require(
        OBJECT_PREFIX.matches(prefix) && "//" !in prefix &&
            prefix.split('/').none { it == "." || it == ".." },
    ) { "REGISTRY_OBJECT_PREFIX is invalid" }
    return prefix
}

internal val AWS_REGION_NAME = Regex("^[a-z]{2}(?:-[a-z]+)+-[1-9][0-9]?$")
private val S3_BUCKET = Regex("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$")

/** Dots are rejected so virtual-hosted HTTPS certificate validation always applies. */
private fun requireS3BucketName(value: String) {
    require(
        value.length in 3..63 && S3_BUCKET.matches(value) &&
            !value.startsWith("xn--") && !value.startsWith("sthree-") && !value.endsWith("-s3alias") &&
            !value.endsWith("--ol-s3") && !value.endsWith("--x-s3"),
    ) { "S3 bucket name is invalid: $value" }
}

private fun requireR2BucketName(value: String) {
    require(value.length in 3..63 && R2_BUCKET.matches(value)) { "R2 bucket name is invalid: $value" }
}

private fun parseR2Endpoint(value: String): URI = runCatching { URI.create(value) }
    .getOrElse { throw IllegalArgumentException("REGISTRY_R2_ENDPOINT is invalid", it) }
