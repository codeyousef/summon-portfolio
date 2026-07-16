package codes.yousef.seen.registry

import java.time.Duration

data class RegistryConfig(
    val environment: String,
    val repositoryId: String,
    val registryOrigin: String,
    val port: Int,
    val storageMode: String,
    val projectId: String?,
    val firestoreDatabase: String,
    val quarantineBucket: String?,
    val publicBucket: String?,
    val metadataBucket: String?,
    val objectPrefix: String,
    val writerMode: String,
    val writerToken: String,
    val writerPrincipal: String,
    val ownerAllowlist: Set<String>,
    val writersEnabled: Boolean,
    val publicDelay: Duration,
    val localOnlineSigningKeysPkcs8Base64: Map<String, String>,
    val kmsOnlineKeyVersions: Map<String, String>,
    val kmsOnlinePublicKeysHex: Map<String, String>,
    val offlineRootPublicKeysHex: List<String>,
    val offlineRootSigningKeysPkcs8Base64: List<String>,
    val offlineTargetsPublicKeysHex: List<String>,
    val offlineTargetsSigningKeysPkcs8Base64: List<String>,
    val bootstrapRootEnvelopeBase64: String?,
    val bootstrapTargetsEnvelopeBase64: String?,
    val trustAndSafetyToken: String? = null,
    val trustAndSafetyPrincipal: String = "registry-dev-reviewer",
    val securityToken: String? = null,
    val securityPrincipal: String = "registry-dev-security",
) {
    init {
        require(environment == "development") {
            "This staged service is development-only until Aether bearer validation is enabled"
        }
        require(writerMode == "opaque-dev") {
            "Only the explicitly temporary opaque-dev writer mode is implemented"
        }
        require(writerToken.toByteArray().size >= 32) { "REGISTRY_WRITER_TOKEN must contain at least 32 bytes" }
        require(writerToken.none(Char::isWhitespace)) { "REGISTRY_WRITER_TOKEN must not contain whitespace" }
        require(ownerAllowlist.isNotEmpty()) { "REGISTRY_OWNER_ALLOWLIST must not be empty" }
        require(publicDelay >= Duration.ofHours(72)) { "Development public delay must be at least 72 hours" }
        listOfNotNull(trustAndSafetyToken, securityToken).forEach { token ->
            require(token.toByteArray().size in 32..4096 && token.none(Char::isWhitespace)) {
                "Development enforcement tokens must contain 32 to 4096 non-whitespace bytes"
            }
            require(token != writerToken) { "Development enforcement tokens must differ from the publisher token" }
        }
        require(trustAndSafetyToken == null || securityToken == null || trustAndSafetyToken != securityToken) {
            "Development enforcement tokens must be distinct"
        }
        require(trustAndSafetyPrincipal.isNotBlank() && securityPrincipal.isNotBlank()) {
            "Development enforcement principal IDs must not be blank"
        }
        require(trustAndSafetyPrincipal != writerPrincipal && securityPrincipal != writerPrincipal &&
            trustAndSafetyPrincipal != securityPrincipal
        ) { "Development enforcement principal IDs must be distinct" }
        if (storageMode == "gcp") {
            require(!projectId.isNullOrBlank()) { "GOOGLE_CLOUD_PROJECT is required for GCP storage" }
            require(!quarantineBucket.isNullOrBlank()) { "REGISTRY_QUARANTINE_BUCKET is required" }
            require(!publicBucket.isNullOrBlank()) { "REGISTRY_PUBLIC_BUCKET is required" }
            require(!metadataBucket.isNullOrBlank()) { "REGISTRY_METADATA_BUCKET is required" }
            require(TufRole.ONLINE.all { it in kmsOnlineKeyVersions && it in kmsOnlinePublicKeysHex }) {
                "Distinct Cloud KMS key versions and public keys are required for releases, security, snapshot, and timestamp"
            }
            require(kmsOnlineKeyVersions.values.toSet().size == TufRole.ONLINE.size) { "Online TUF roles must use distinct KMS key versions" }
            require(kmsOnlinePublicKeysHex.values.toSet().size == TufRole.ONLINE.size) { "Online TUF roles must use distinct public keys" }
            require(offlineRootSigningKeysPkcs8Base64.isEmpty() && offlineTargetsSigningKeysPkcs8Base64.isEmpty()) {
                "Offline TUF private keys must never be supplied to GCP runtime configuration"
            }
        } else {
            require(storageMode == "memory") { "REGISTRY_STORAGE_MODE must be memory or gcp" }
            require(TufRole.ONLINE.all { it in localOnlineSigningKeysPkcs8Base64 }) {
                "Memory mode requires distinct local online keys for all TUF roles"
            }
        }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): RegistryConfig = RegistryConfig(
            environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
            repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
            registryOrigin = (env["REGISTRY_ORIGIN"] ?: "https://seen.dev.yousef.codes/packages").trimEnd('/'),
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            storageMode = env["REGISTRY_STORAGE_MODE"] ?: "gcp",
            projectId = env["GOOGLE_CLOUD_PROJECT"],
            firestoreDatabase = env["REGISTRY_FIRESTORE_DATABASE"] ?: "seen-registry-dev",
            quarantineBucket = env["REGISTRY_QUARANTINE_BUCKET"],
            publicBucket = env["REGISTRY_PUBLIC_BUCKET"],
            metadataBucket = env["REGISTRY_METADATA_BUCKET"],
            objectPrefix = env["REGISTRY_OBJECT_PREFIX"]?.trim('/') ?: "v1",
            writerMode = env["REGISTRY_WRITER_MODE"] ?: "opaque-dev",
            writerToken = env["REGISTRY_WRITER_TOKEN"].orEmpty().trim(),
            writerPrincipal = env["REGISTRY_WRITER_PRINCIPAL"] ?: "internal-dev-publisher",
            ownerAllowlist = env["REGISTRY_OWNER_ALLOWLIST"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet(),
            writersEnabled = env["REGISTRY_WRITERS_ENABLED"]?.toBooleanStrictOrNull() ?: false,
            publicDelay = Duration.ofSeconds(env["REGISTRY_PUBLIC_DELAY_SECONDS"]?.toLongOrNull() ?: 259_200L),
            localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.mapNotNull { role ->
                env["REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64"]?.let { role to it }
            }.toMap(),
            kmsOnlineKeyVersions = TufRole.ONLINE.mapNotNull { role ->
                env["REGISTRY_KMS_${role.uppercase()}_KEY_VERSION"]?.let { role to it }
            }.toMap(),
            kmsOnlinePublicKeysHex = TufRole.ONLINE.mapNotNull { role ->
                env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]?.let { role to it }
            }.toMap(),
            offlineRootPublicKeysHex = env.csv("REGISTRY_OFFLINE_ROOT_PUBLIC_KEYS_HEX"),
            offlineRootSigningKeysPkcs8Base64 = env.csv("REGISTRY_OFFLINE_ROOT_SIGNING_KEYS_PKCS8_BASE64"),
            offlineTargetsPublicKeysHex = env.csv("REGISTRY_OFFLINE_TARGETS_PUBLIC_KEYS_HEX"),
            offlineTargetsSigningKeysPkcs8Base64 = env.csv("REGISTRY_OFFLINE_TARGETS_SIGNING_KEYS_PKCS8_BASE64"),
            bootstrapRootEnvelopeBase64 = env["REGISTRY_BOOTSTRAP_ROOT_ENVELOPE_BASE64"],
            bootstrapTargetsEnvelopeBase64 = env["REGISTRY_BOOTSTRAP_TARGETS_ENVELOPE_BASE64"],
            trustAndSafetyToken = env["REGISTRY_TRUST_AND_SAFETY_TOKEN"]?.trim()?.takeIf(String::isNotEmpty),
            trustAndSafetyPrincipal = env["REGISTRY_TRUST_AND_SAFETY_PRINCIPAL"] ?: "registry-dev-reviewer",
            securityToken = env["REGISTRY_SECURITY_TOKEN"]?.trim()?.takeIf(String::isNotEmpty),
            securityPrincipal = env["REGISTRY_SECURITY_PRINCIPAL"] ?: "registry-dev-security",
        )
    }
}

object TufRole {
    const val RELEASES = "releases"
    const val SECURITY = "security"
    const val SNAPSHOT = "snapshot"
    const val TIMESTAMP = "timestamp"
    val ONLINE = listOf(RELEASES, SECURITY, SNAPSHOT, TIMESTAMP)
}

private fun Map<String, String>.csv(name: String): List<String> = this[name].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
