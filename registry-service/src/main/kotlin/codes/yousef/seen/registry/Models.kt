package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreatePackageRequest(
    val identity: String,
    val description: String? = null,
    val repository: String? = null,
    @SerialName("license_spdx") val licenseSpdx: String? = null,
)

@Serializable
data class PackageRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    val identity: String,
    @SerialName("namespace_status") val namespaceStatus: String = "active",
    val description: String? = null,
    val repository: String? = null,
    @SerialName("license_spdx") val licenseSpdx: String? = null,
    @SerialName("latest_active_version") val latestActiveVersion: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val links: PackageLinks,
    @Transient val ownerPrincipal: String? = null,
)

@Serializable
data class PackageLinks(val self: String, val releases: String)

@Serializable
data class ArchiveReservation(
    val format: String,
    val sha256: String,
    @SerialName("compressed_bytes") val compressedBytes: Long,
)

@Serializable
data class SourceDeclaration(
    val forge: String,
    @SerialName("repository_id") val repositoryId: String,
    @SerialName("installation_id") val installationId: String,
    @SerialName("requested_ref") val requestedRef: String,
    @SerialName("expected_commit") val expectedCommit: String,
    @SerialName("license_spdx") val licenseSpdx: String,
)

internal fun SourceDeclaration.requireValidForReservation() {
    val validLengths = repositoryId.length in 1..128 &&
        installationId.length in 1..128 &&
        requestedRef.length in 1..255 &&
        licenseSpdx.length in 1..128
    if (forge !in setOf("github", "gitlab") || !validLengths || !Regex("^(?:[0-9a-f]{40}|[0-9a-f]{64})$").matches(expectedCommit)) {
        throw RegistryException(400, "invalid_request", "Request body is invalid")
    }
}

@Serializable
data class ReserveReleaseRequest(
    val version: String,
    val visibility: String,
    val archive: ArchiveReservation,
    @SerialName("manifest_sha256") val manifestSha256: String,
    val manifest: JsonObject,
    val source: SourceDeclaration,
)

@Serializable
data class CompleteUploadRequest(
    @SerialName("archive_sha256") val archiveSha256: String,
    @SerialName("compressed_bytes") val compressedBytes: Long,
)

@Serializable
data class ArchiveStats(
    val format: String = "tar+gzip",
    val sha256: String,
    @SerialName("compressed_bytes") val compressedBytes: Long,
    @SerialName("expanded_bytes") val expandedBytes: Long? = null,
    @SerialName("entry_count") val entryCount: Int? = null,
    @SerialName("largest_regular_file_bytes") val largestRegularFileBytes: Long? = null,
    @SerialName("longest_path_bytes") val longestPathBytes: Int? = null,
    @SerialName("maximum_path_depth") val maximumPathDepth: Int? = null,
)

@Serializable
data class ReleaseState(
    val lifecycle: String = "reserved",
    val visibility: String,
    val availability: String = "unavailable",
    val retention: String = "retained",
)

@Serializable
data class ReleaseVerification(
    val origin: String = "pending",
    val integrity: String = "pending",
    val source: String = "pending",
    @SerialName("first_scan") val firstScan: String = "not-required",
    @SerialName("second_scan") val secondScan: String = "not-required",
    @SerialName("attestation_sequence") val attestationSequence: Long = 0,
)

@Serializable
data class ReleaseTimestamps(
    @SerialName("reserved_at") val reservedAt: String,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
    @SerialName("quarantined_at") val quarantinedAt: String? = null,
    @SerialName("public_delay_started_at") val publicDelayStartedAt: String? = null,
    @SerialName("public_delay_ends_at") val publicDelayEndsAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("activated_at") val activatedAt: String? = null,
    @SerialName("yanked_at") val yankedAt: String? = null,
    @SerialName("security_quarantined_at") val securityQuarantinedAt: String? = null,
    @SerialName("soft_deleted_at") val softDeletedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ReleaseLinks(
    val self: String,
    val `package`: String,
    @SerialName("source_proof") val sourceProof: String? = null,
    val download: String? = null,
)

@Serializable
data class ReleaseRecord(
    @SerialName("contract_version") val contractVersion: Int = 1,
    val `package`: String,
    val version: String,
    val archive: ArchiveStats,
    @SerialName("manifest_sha256") val manifestSha256: String,
    val capabilities: List<String> = emptyList(),
    val state: ReleaseState,
    @SerialName("source_proof_id") val sourceProofId: String? = null,
    val verification: ReleaseVerification = ReleaseVerification(),
    val timestamps: ReleaseTimestamps,
    @SerialName("resolver_metadata_version") val resolverMetadataVersion: Long? = null,
    val links: ReleaseLinks,
    @Transient val ownerPrincipal: String? = null,
    @Transient val uploadId: String? = null,
    @Transient val uploadExpiresAt: String? = null,
)

@Serializable
data class SignedDependency(
    val alias: String,
    val `package`: String,
    @SerialName("registry_origin") val registryOrigin: String,
    val requirement: String,
    val allow: List<String> = emptyList(),
)

@Serializable
data class UploadInstruction(
    @SerialName("upload_id") val uploadId: String,
    val method: String = "PUT",
    val path: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("maximum_bytes") val maximumBytes: Long = ArchivePolicy.MAX_COMPRESSED_BYTES,
    @SerialName("required_headers") val requiredHeaders: UploadRequiredHeaders,
)

@Serializable
data class UploadRequiredHeaders(
    @SerialName("Content-Type") val contentType: String = "application/gzip",
    @SerialName("Content-Length") val contentLength: Long,
    @SerialName("X-Seen-Archive-Sha256") val archiveSha256: String,
)

@Serializable
data class ReleaseReservation(val release: ReleaseRecord, val upload: UploadInstruction)

@Serializable
data class PackagePage(
    val items: List<PackageRecord>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ReleasePage(
    val items: List<ReleaseRecord>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ErrorEnvelope(val error: ApiError)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("occurred_at") val occurredAt: String,
    val retryable: Boolean,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
    val details: JsonObject = JsonObject(emptyMap()),
)

class RegistryException(
    val status: Int,
    val code: String,
    val publicMessage: String,
    val retryable: Boolean = false,
    val retryAfterSeconds: Int? = null,
) : RuntimeException(publicMessage)

object IdentityRules {
    private val segment = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val exactVersion = Regex("^(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
    private val digest = Regex("^[0-9a-f]{64}$")

    fun requireIdentity(identity: String): Pair<String, String> {
        val parts = identity.split('/')
        if (parts.size != 2 || parts.any { !segment.matches(it) }) {
            throw RegistryException(400, "invalid_package_identity", "Package identity is invalid")
        }
        return parts[0] to parts[1]
    }

    fun requireVersion(version: String) {
        if (version.length > 128 || !exactVersion.matches(version)) {
            throw RegistryException(400, "invalid_version", "Release version is invalid")
        }
    }

    fun requireDigest(value: String, field: String = "digest") {
        if (!digest.matches(value)) {
            throw RegistryException(400, "invalid_request", "$field must be a lowercase SHA-256 digest")
        }
    }
}
