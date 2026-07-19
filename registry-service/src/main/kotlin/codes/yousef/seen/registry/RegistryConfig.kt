package codes.yousef.seen.registry

import java.net.URI
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
    val kmsOnlinePublicKeysHex: Map<String, String>,
    val remoteOnlineSignerTargets: Map<String, RemoteTufSignerTarget>,
    val trustAndSafetyToken: String? = null,
    val trustAndSafetyPrincipal: String = "",
    val securityToken: String? = null,
    val securityPrincipal: String = "",
    val serverMode: RegistryServerMode = RegistryServerMode.PUBLIC_API,
) {
    val signingOperation: TufSigningOperation?
        get() = when (serverMode) {
            RegistryServerMode.PUBLIC_API, RegistryServerMode.READ_ONLY_PUBLIC_API -> null
            RegistryServerMode.RELEASE_ACTIONS -> TufSigningOperation.RELEASE
            RegistryServerMode.SECURITY_ACTIONS -> TufSigningOperation.SECURITY
        }

    val configuredSigningRoles: Set<String>
        get() = serverMode.signingRoles

    init {
        require(environment == "development" || environment == "production") {
            "REGISTRY_ENVIRONMENT must be development or production"
        }
        require(environment != "production" || serverMode == RegistryServerMode.READ_ONLY_PUBLIC_API) {
            "Production permits only the credential-free read-only public API"
        }
        require(serverMode != RegistryServerMode.READ_ONLY_PUBLIC_API || environment == "production") {
            "The read-only public API is reserved for production"
        }
        val expectedRepositoryId = if (environment == "production") {
            "seen-prod-registry-v1"
        } else {
            "seen-dev-registry-v1"
        }
        val expectedRegistryOrigin = if (environment == "production") {
            "https://seen.yousef.codes/packages"
        } else {
            "https://seen.dev.yousef.codes/packages"
        }
        require(repositoryId == expectedRepositoryId && registryOrigin == expectedRegistryOrigin) {
            "Registry environment, repository ID, and origin must match the official deployment identity"
        }
        when (serverMode) {
            RegistryServerMode.PUBLIC_API -> {
                requirePublisherCredential()
                require(ownerAllowlist.isNotEmpty()) { "REGISTRY_OWNER_ALLOWLIST must not be empty" }
                require(publicDelay >= Duration.ofHours(72)) { "Development public delay must be at least 72 hours" }
                requireValidToken(requireNotNull(trustAndSafetyToken) {
                    "REGISTRY_TRUST_AND_SAFETY_TOKEN is required for report and appeal review routes"
                }, "REGISTRY_TRUST_AND_SAFETY_TOKEN")
                require(trustAndSafetyToken != writerToken) {
                    "Trust-and-safety and publisher credentials must be distinct"
                }
                require(trustAndSafetyPrincipal.isNotBlank() && trustAndSafetyPrincipal != writerPrincipal) {
                    "Trust-and-safety principal must be non-blank and independent from the publisher"
                }
                require(securityToken == null && securityPrincipal.isBlank()) {
                    "Public API must not receive the security action credential"
                }
            }
            RegistryServerMode.READ_ONLY_PUBLIC_API -> {
                require(
                    writerMode.isBlank() && writerToken.isBlank() && writerPrincipal.isBlank() &&
                        ownerAllowlist.isEmpty() && !writersEnabled && publicDelay.isZero
                ) { "Read-only public API must not receive publisher configuration" }
                require(
                    trustAndSafetyToken == null && trustAndSafetyPrincipal.isBlank() &&
                        securityToken == null && securityPrincipal.isBlank()
                ) { "Read-only public API must not receive enforcement credentials" }
            }
            RegistryServerMode.RELEASE_ACTIONS -> {
                requirePublisherCredential()
                require(ownerAllowlist.isEmpty() && !writersEnabled && publicDelay.isZero) {
                    "Release actions must not receive public writer policy configuration"
                }
                require(trustAndSafetyToken == null && trustAndSafetyPrincipal.isBlank() &&
                    securityToken == null && securityPrincipal.isBlank()
                ) { "Release actions must receive only the publisher credential" }
            }
            RegistryServerMode.SECURITY_ACTIONS -> {
                require(writerMode.isBlank() && writerToken.isBlank() && writerPrincipal.isBlank() &&
                    ownerAllowlist.isEmpty() && !writersEnabled && publicDelay.isZero
                ) { "Security actions must not receive publisher configuration" }
                require(trustAndSafetyToken == null && trustAndSafetyPrincipal.isBlank()) {
                    "Security actions must not receive trust-and-safety credentials"
                }
                requireValidToken(requireNotNull(securityToken) {
                    "REGISTRY_SECURITY_TOKEN is required for security actions"
                }, "REGISTRY_SECURITY_TOKEN")
                require(securityPrincipal.isNotBlank()) { "REGISTRY_SECURITY_PRINCIPAL must not be blank" }
            }
        }
        if (storageMode == "gcp") {
            require(!projectId.isNullOrBlank()) { "GOOGLE_CLOUD_PROJECT is required for GCP storage" }
            require(!metadataBucket.isNullOrBlank()) { "REGISTRY_METADATA_BUCKET is required" }
            val expectedFirestoreDatabase = if (environment == "production") {
                "seen-registry-prod"
            } else {
                "seen-registry-dev"
            }
            require(firestoreDatabase == expectedFirestoreDatabase) {
                "Registry environment and Firestore database must match the official deployment identity"
            }
            when (serverMode) {
                RegistryServerMode.PUBLIC_API -> require(!quarantineBucket.isNullOrBlank() && !publicBucket.isNullOrBlank()) {
                    "Public API requires quarantine and public buckets"
                }
                RegistryServerMode.READ_ONLY_PUBLIC_API -> require(
                    quarantineBucket == null && !publicBucket.isNullOrBlank(),
                ) { "Read-only public API requires only metadata and public buckets" }
                RegistryServerMode.RELEASE_ACTIONS, RegistryServerMode.SECURITY_ACTIONS -> require(
                    quarantineBucket == null && publicBucket == null,
                ) { "Action surfaces must receive only the metadata bucket" }
            }
            require(kmsOnlinePublicKeysHex.keys == TufRole.ONLINE.toSet()) {
                "All and only online TUF public keys are required"
            }
            val onlinePublicKeys = kmsOnlinePublicKeysHex.values.map(String::hexToBytes)
            require(
                onlinePublicKeys.all { it.size == 32 } &&
                    onlinePublicKeys.map(::tufKeyId).toSet().size == TufRole.ONLINE.size,
            ) { "Online TUF roles must use four distinct Ed25519 public keys" }
            require(remoteOnlineSignerTargets.keys == configuredSigningRoles) {
                "Runtime signer URLs must exactly match ${configuredSigningRoles.sorted().joinToString(", ").ifEmpty { "the empty verification-only role set" }}"
            }
            require((signingOperation == null) == configuredSigningRoles.isEmpty()) {
                "Runtime signing operation must exactly match its configured signer roles"
            }
            require(remoteOnlineSignerTargets.values.map { it.endpoint }.toSet().size == remoteOnlineSignerTargets.size) {
                "Online TUF roles must use distinct signer endpoints"
            }
            require(remoteOnlineSignerTargets.values.map { it.audience }.toSet().size == remoteOnlineSignerTargets.size) {
                "Online TUF roles must use distinct signer audiences"
            }
            require(localOnlineSigningKeysPkcs8Base64.isEmpty()) {
                "TUF private keys must never be supplied to a server runtime"
            }
        } else {
            require(storageMode == "memory") { "REGISTRY_STORAGE_MODE must be memory or gcp" }
            require(projectId == null && quarantineBucket == null && publicBucket == null && metadataBucket == null) {
                "Memory mode must not receive cloud project or bucket configuration"
            }
            require(remoteOnlineSignerTargets.isEmpty()) { "Memory mode cannot use remote signer endpoints" }
            require(localOnlineSigningKeysPkcs8Base64.keys == TufRole.ONLINE.toSet()) {
                "Memory mode requires distinct local online keys for all TUF roles"
            }
            require(localOnlineSigningKeysPkcs8Base64.values.toSet().size == TufRole.ONLINE.size) {
                "Memory mode requires distinct local online keys for all TUF roles"
            }
        }
    }

    private fun requirePublisherCredential() {
        require(writerMode == "opaque-dev") { "Only the temporary opaque-dev publisher mode is implemented" }
        requireValidToken(writerToken, "REGISTRY_WRITER_TOKEN")
        require(writerPrincipal.isNotBlank()) { "REGISTRY_WRITER_PRINCIPAL must not be blank" }
    }

    private fun requireValidToken(token: String, name: String) {
        require(token.toByteArray().size in 32..4096 && token.none(Char::isWhitespace)) {
            "$name must contain 32 to 4096 non-whitespace bytes"
        }
    }

    companion object {
        fun fromEnvironment(
            env: Map<String, String> = System.getenv(),
            serverModeOverride: RegistryServerMode? = null,
        ): RegistryConfig {
            rejectCoordinatorKeyVersions(env)
            val serverMode = serverModeOverride ?: RegistryServerMode.fromEnvironment(
                env["REGISTRY_SERVER_MODE"] ?: RegistryServerMode.PUBLIC_API.environmentValue,
            )
            val storageMode = env["REGISTRY_STORAGE_MODE"] ?: "gcp"
            rejectServerEnvironment(env, serverMode, storageMode)
            return RegistryConfig(
                environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
                repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
                registryOrigin = (env["REGISTRY_ORIGIN"] ?: "https://seen.dev.yousef.codes/packages").trimEnd('/'),
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                storageMode = storageMode,
                projectId = env["GOOGLE_CLOUD_PROJECT"],
                firestoreDatabase = env["REGISTRY_FIRESTORE_DATABASE"] ?: "seen-registry-dev",
                quarantineBucket = env["REGISTRY_QUARANTINE_BUCKET"],
                publicBucket = env["REGISTRY_PUBLIC_BUCKET"],
                metadataBucket = env["REGISTRY_METADATA_BUCKET"],
                objectPrefix = env["REGISTRY_OBJECT_PREFIX"]?.trim('/') ?: "v1",
                writerMode = env["REGISTRY_WRITER_MODE"].orEmpty().ifBlank {
                    if (serverMode in setOf(RegistryServerMode.PUBLIC_API, RegistryServerMode.RELEASE_ACTIONS)) {
                        "opaque-dev"
                    } else ""
                },
                writerToken = env["REGISTRY_WRITER_TOKEN"].orEmpty().trim(),
                writerPrincipal = env["REGISTRY_WRITER_PRINCIPAL"].orEmpty().ifBlank {
                    if (serverMode in setOf(RegistryServerMode.PUBLIC_API, RegistryServerMode.RELEASE_ACTIONS)) {
                        "internal-dev-publisher"
                    } else ""
                },
                ownerAllowlist = if (serverMode == RegistryServerMode.PUBLIC_API) {
                    env.csv("REGISTRY_OWNER_ALLOWLIST").toSet()
                } else emptySet(),
                writersEnabled = if (serverMode == RegistryServerMode.PUBLIC_API) {
                    env["REGISTRY_WRITERS_ENABLED"]?.toBooleanStrictOrNull() ?: false
                } else false,
                publicDelay = if (serverMode == RegistryServerMode.PUBLIC_API) {
                    Duration.ofSeconds(env["REGISTRY_PUBLIC_DELAY_SECONDS"]?.toLongOrNull() ?: 259_200L)
                } else Duration.ZERO,
                localOnlineSigningKeysPkcs8Base64 = TufRole.ONLINE.mapNotNull { role ->
                    env["REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64"]?.let { role to it }
                }.toMap(),
                kmsOnlinePublicKeysHex = TufRole.ONLINE.mapNotNull { role ->
                    env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]?.let { role to it }
                }.toMap(),
                remoteOnlineSignerTargets = env.remoteSignerTargets(),
                trustAndSafetyToken = env["REGISTRY_TRUST_AND_SAFETY_TOKEN"]?.trim()?.takeIf(String::isNotEmpty),
                trustAndSafetyPrincipal = if (serverMode == RegistryServerMode.PUBLIC_API) {
                    env["REGISTRY_TRUST_AND_SAFETY_PRINCIPAL"] ?: "registry-dev-reviewer"
                } else "",
                securityToken = env["REGISTRY_SECURITY_TOKEN"]?.trim()?.takeIf(String::isNotEmpty),
                securityPrincipal = if (serverMode == RegistryServerMode.SECURITY_ACTIONS) {
                    env["REGISTRY_SECURITY_PRINCIPAL"] ?: "registry-dev-security"
                } else "",
                serverMode = serverMode,
            )
        }
    }
}

data class RemoteTufSignerTarget(
    val endpoint: URI,
    val audience: String,
) {
    init {
        require(endpoint.isAbsolute && endpoint.scheme == "https" && endpoint.host != null) {
            "Remote signer URL must be an absolute HTTPS URI"
        }
        require(endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null) {
            "Remote signer URL cannot contain user info, a query, or a fragment"
        }
        require(endpoint.normalize().path == "/sign") { "Remote signer URL path must be /sign" }
        require(audience.isNotBlank() && audience.length <= 2048 && audience.none(Char::isISOControl) && audience.none(Char::isWhitespace)) {
            "Remote signer audience is invalid"
        }
    }

    companion object {
        fun defaultAudience(endpoint: URI): String = URI(
            endpoint.scheme,
            null,
            endpoint.host,
            endpoint.port,
            null,
            null,
            null,
        ).toASCIIString()
    }
}

enum class RegistryServerMode(
    val environmentValue: String,
    val argument: String,
) {
    PUBLIC_API("public-api", "serve-public-api"),
    READ_ONLY_PUBLIC_API("read-only-public-api", "serve-read-only-public-api"),
    RELEASE_ACTIONS("release-actions", "serve-release-actions"),
    SECURITY_ACTIONS("security-actions", "serve-security-actions"),
    ;

    val signingRoles: Set<String>
        get() = when (this) {
            PUBLIC_API, READ_ONLY_PUBLIC_API -> emptySet()
            RELEASE_ACTIONS -> setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
            SECURITY_ACTIONS -> setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
        }

    val exposesRegistryRoutes: Boolean
        get() = this == PUBLIC_API || this == READ_ONLY_PUBLIC_API

    val exposesRegistryMutations: Boolean
        get() = this == PUBLIC_API

    companion object {
        fun fromEnvironment(value: String): RegistryServerMode = entries.firstOrNull {
            it.environmentValue == value
        } ?: throw IllegalArgumentException(
            "REGISTRY_SERVER_MODE must be public-api, read-only-public-api, release-actions, or security-actions",
        )

        fun fromArgument(value: String): RegistryServerMode? = entries.firstOrNull { it.argument == value }
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

internal fun rejectCoordinatorKeyVersions(env: Map<String, String>) {
    val supplied = TufRole.ONLINE.filter { role -> env.containsKey("REGISTRY_KMS_${role.uppercase()}_KEY_VERSION") }
    require(supplied.isEmpty()) {
        "Coordinator and API runtimes must not receive KMS key versions; configure role-locked signer URLs instead"
    }
}

private fun rejectServerEnvironment(
    env: Map<String, String>,
    mode: RegistryServerMode,
    storageMode: String,
) {
    val allowedPublicKeys = TufRole.ONLINE.mapTo(mutableSetOf()) { role ->
        "REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"
    }
    val allowedRemoteSignerConfiguration = mode.signingRoles.flatMapTo(mutableSetOf()) { role ->
        val prefix = "REGISTRY_TUF_${role.uppercase()}_SIGNER"
        listOf("${prefix}_URL", "${prefix}_AUDIENCE")
    }
    val allowedMemoryPrivateKeys = if (storageMode == "memory") {
        TufRole.ONLINE.mapTo(mutableSetOf()) { role ->
            "REGISTRY_${role.uppercase()}_SIGNING_KEY_PKCS8_BASE64"
        }
    } else emptySet()
    val alwaysForbidden = env.keys.filter { name ->
        name.startsWith("REGISTRY_OFFLINE_") ||
            name.startsWith("REGISTRY_BOOTSTRAP_") ||
            name.startsWith("REGISTRY_TUF_SIGNER_") ||
            (name.startsWith("REGISTRY_TUF_") && name !in allowedRemoteSignerConfiguration) ||
            (name.startsWith("REGISTRY_KMS_") && name !in allowedPublicKeys) ||
            name.startsWith("REGISTRY_GITHUB_") ||
            name.startsWith("REGISTRY_GITLAB_") ||
            ((name.contains("PRIVATE_KEY") || name.contains("PKCS8")) && name !in allowedMemoryPrivateKeys)
    }
    require(alwaysForbidden.isEmpty()) {
        "Server runtime rejects maintenance, forge, signer-service, and private-key configuration: ${alwaysForbidden.sorted().joinToString(", ")}"
    }

    val surfaceForbidden = when (mode) {
        RegistryServerMode.PUBLIC_API -> setOf(
            "REGISTRY_SECURITY_TOKEN",
            "REGISTRY_SECURITY_PRINCIPAL",
        )
        RegistryServerMode.READ_ONLY_PUBLIC_API -> setOf(
            "REGISTRY_QUARANTINE_BUCKET",
            "REGISTRY_WRITER_MODE",
            "REGISTRY_WRITER_TOKEN",
            "REGISTRY_WRITER_PRINCIPAL",
            "REGISTRY_OWNER_ALLOWLIST",
            "REGISTRY_WRITERS_ENABLED",
            "REGISTRY_PUBLIC_DELAY_SECONDS",
            "REGISTRY_TRUST_AND_SAFETY_TOKEN",
            "REGISTRY_TRUST_AND_SAFETY_PRINCIPAL",
            "REGISTRY_SECURITY_TOKEN",
            "REGISTRY_SECURITY_PRINCIPAL",
        )
        RegistryServerMode.RELEASE_ACTIONS -> setOf(
            "REGISTRY_QUARANTINE_BUCKET",
            "REGISTRY_PUBLIC_BUCKET",
            "REGISTRY_OWNER_ALLOWLIST",
            "REGISTRY_WRITERS_ENABLED",
            "REGISTRY_PUBLIC_DELAY_SECONDS",
            "REGISTRY_TRUST_AND_SAFETY_TOKEN",
            "REGISTRY_TRUST_AND_SAFETY_PRINCIPAL",
            "REGISTRY_SECURITY_TOKEN",
            "REGISTRY_SECURITY_PRINCIPAL",
        )
        RegistryServerMode.SECURITY_ACTIONS -> setOf(
            "REGISTRY_QUARANTINE_BUCKET",
            "REGISTRY_PUBLIC_BUCKET",
            "REGISTRY_WRITER_MODE",
            "REGISTRY_WRITER_TOKEN",
            "REGISTRY_WRITER_PRINCIPAL",
            "REGISTRY_OWNER_ALLOWLIST",
            "REGISTRY_WRITERS_ENABLED",
            "REGISTRY_PUBLIC_DELAY_SECONDS",
            "REGISTRY_TRUST_AND_SAFETY_TOKEN",
            "REGISTRY_TRUST_AND_SAFETY_PRINCIPAL",
        )
    }.filter(env::containsKey)
    require(surfaceForbidden.isEmpty()) {
        "${mode.environmentValue} received unrelated configuration: ${surfaceForbidden.sorted().joinToString(", ")}"
    }
}

internal fun Map<String, String>.remoteSignerTargets(): Map<String, RemoteTufSignerTarget> = TufRole.ONLINE.mapNotNull { role ->
    val prefix = "REGISTRY_TUF_${role.uppercase()}_SIGNER"
    val url = this["${prefix}_URL"]
    val audience = this["${prefix}_AUDIENCE"]
    if (url == null) {
        require(audience == null) { "${prefix}_AUDIENCE requires ${prefix}_URL" }
        null
    } else {
        require(url.isNotBlank()) { "${prefix}_URL must not be blank" }
        val endpoint = runCatching { URI.create(url.trim()) }
            .getOrElse { throw IllegalArgumentException("${prefix}_URL is invalid", it) }
        role to RemoteTufSignerTarget(
            endpoint = endpoint,
            audience = audience?.trim()?.takeIf(String::isNotEmpty) ?: RemoteTufSignerTarget.defaultAudience(endpoint),
        )
    }
}.toMap()
