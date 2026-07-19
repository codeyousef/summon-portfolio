package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.security.MessageDigest
import org.slf4j.LoggerFactory

class RegistryService(
    private val config: RegistryConfig,
    private val repository: RegistryRepository,
    private val storage: RegistryObjectStorage,
    private val validator: ArchiveValidator,
    private val tuf: TufPublisher,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createPackage(request: CreatePackageRequest, principal: WriterPrincipal): PackageRecord {
        requireWritersEnabled()
        val (owner) = IdentityRules.requireIdentity(request.identity)
        requireTextLimits(request)
        val now = clock.instant().utc()
        val record = PackageRecord(
            identity = request.identity,
            description = request.description,
            repository = request.repository,
            licenseSpdx = request.licenseSpdx,
            createdAt = now,
            updatedAt = now,
            links = PackageLinks(
                self = "/packages/api/v1/packages/${request.identity}",
                releases = "/packages/api/v1/packages/${request.identity}/releases",
            ),
            ownerPrincipal = principal.subject,
        )
        if (!repository.createPackage(StoredPackage(record, principal.subject))) {
            val existing = repository.getPackage(request.identity)
            if (existing != null && existing.ownerPrincipal == principal.subject &&
                existing.record.description == request.description &&
                existing.record.repository == request.repository &&
                existing.record.licenseSpdx == request.licenseSpdx
            ) return existing.record
            throw RegistryException(409, "package_already_exists", "Package identity is already reserved")
        }
        return record
    }

    fun listPublicPackages(): PackagePage {
        val visiblePackages = repository.listReleases()
            .asSequence()
            .map(StoredRelease::record)
            .filter(ReleaseRecord::isPubliclyReadable)
            .map(ReleaseRecord::`package`)
            .toSet()
        return PackagePage(
            repository.listPackages()
                .map(StoredPackage::record)
                .filter { it.identity in visiblePackages },
        )
    }

    fun getPackage(identity: String, principal: WriterPrincipal? = null): PackageRecord {
        IdentityRules.requireIdentity(identity)
        val stored = repository.getPackage(identity) ?: notFound()
        if (principal?.subject != stored.ownerPrincipal && !hasPublicRelease(identity)) notFound()
        return stored.record
    }

    fun reserveRelease(identity: String, request: ReserveReleaseRequest, principal: WriterPrincipal): ReleaseReservation {
        requireWritersEnabled()
        IdentityRules.requireIdentity(identity)
        IdentityRules.requireVersion(request.version)
        IdentityRules.requireDigest(request.archive.sha256, "archive.sha256")
        IdentityRules.requireDigest(request.manifestSha256, "manifest_sha256")
        if (request.visibility != "public") {
            throw RegistryException(503, "writer_disabled", "Private package publishing is not enabled in the staged registry")
        }
        if (request.archive.format != "tar+gzip" || request.archive.compressedBytes !in 1..ArchivePolicy.MAX_COMPRESSED_BYTES) {
            throw RegistryException(400, "invalid_request", "Archive reservation is invalid")
        }
        validateReservedManifest(request.manifest, identity, request.version, request.visibility)
        request.source.requireValidForReservation()
        val capabilities = manifestCapabilities(request.manifest)
        val dependencies = manifestDependencies(request.manifest)
        val owner = repository.getPackage(identity) ?: notFound()
        if (owner.ownerPrincipal != principal.subject) throw RegistryException(403, "forbidden", "Publisher does not own this package")
        val now = clock.instant()
        val uploadId = "upl_${randomId()}"
        val expires = now.plus(UPLOAD_RESERVATION_LIFETIME)
        val record = ReleaseRecord(
            `package` = identity,
            version = request.version,
            archive = ArchiveStats(sha256 = request.archive.sha256, compressedBytes = request.archive.compressedBytes),
            manifestSha256 = request.manifestSha256,
            capabilities = capabilities,
            state = ReleaseState(lifecycle = "reserved", visibility = request.visibility, availability = "unavailable"),
            timestamps = ReleaseTimestamps(reservedAt = now.utc(), updatedAt = now.utc()),
            links = ReleaseLinks(
                self = "/packages/api/v1/packages/$identity/releases/${request.version}",
                `package` = "/packages/api/v1/packages/$identity",
            ),
            ownerPrincipal = principal.subject,
            uploadId = uploadId,
            uploadExpiresAt = expires.utc(),
        )
        val stored = StoredRelease(
            record = record,
            ownerPrincipal = principal.subject,
            uploadId = uploadId,
            uploadExpiresAt = expires.utc(),
            manifest = request.manifest,
            source = request.source,
            dependencies = dependencies,
        )
        if (!repository.reserveRelease(stored)) {
            return recoverReservation(identity, request, principal, capabilities, dependencies, now)
        }
        return reservationFor(stored)
    }

    fun uploadArchive(uploadId: String, digestHeader: String?, bytes: ByteArray, principal: WriterPrincipal) =
        uploadArchive(
            uploadId,
            digestHeader,
            ReopenableArchiveSource { bytes.inputStream() },
            bytes.size.toLong(),
            principal,
        )

    fun uploadArchive(
        uploadId: String,
        digestHeader: String?,
        source: ReopenableArchiveSource,
        declaredBytes: Long,
        principal: WriterPrincipal,
    ) = uploadArchive(
        uploadId = uploadId,
        digestHeader = digestHeader,
        source = source,
        declaredBytes = declaredBytes,
        principal = principal,
        observedUpload = null,
    )

    internal fun uploadStreamedArchive(
        uploadId: String,
        digestHeader: String?,
        source: ReopenableArchiveSource,
        observedBytes: Long,
        observedSha256: String,
        principal: WriterPrincipal,
    ) = uploadArchive(
        uploadId = uploadId,
        digestHeader = digestHeader,
        source = source,
        declaredBytes = observedBytes,
        principal = principal,
        observedUpload = observedBytes to observedSha256,
    )

    private fun uploadArchive(
        uploadId: String,
        digestHeader: String?,
        source: ReopenableArchiveSource,
        declaredBytes: Long,
        principal: WriterPrincipal,
        observedUpload: Pair<Long, String>?,
    ) {
        requireWritersEnabled()
        val release = ownedUpload(uploadId, principal)
        if (release.record.state.lifecycle == "reserved" && clock.instant().isAfter(Instant.parse(release.uploadExpiresAt))) notFound()
        if (digestHeader == null) throw RegistryException(400, "digest_required", "Archive digest header is required")
        IdentityRules.requireDigest(digestHeader)
        if (declaredBytes !in 1..ArchivePolicy.MAX_COMPRESSED_BYTES) {
            throw RegistryException(413, "archive_too_large", "Archive exceeds the compressed byte limit")
        }
        if (declaredBytes != release.record.archive.compressedBytes || digestHeader != release.record.archive.sha256) {
            throw RegistryException(422, "digest_mismatch", "Archive bytes do not match the reservation")
        }
        if (release.record.state.lifecycle in setOf("quarantined", "delayed", "ready", "active")) return
        if (release.record.state.lifecycle != "reserved") {
            throw RegistryException(409, "release_not_ready", "Release upload is not ready for archive storage", true, 5)
        }
        val observed = observedUpload ?: inspectArchiveUpload(source)
        IdentityRules.requireDigest(observed.second, "observed archive digest")
        if (observed.first != declaredBytes || observed.second != digestHeader) {
            throw RegistryException(422, "digest_mismatch", "Archive bytes do not match the reservation")
        }
        storage.putQuarantine(uploadId, source)
        val now = clock.instant().utc()
        val updated = release.copy(record = release.record.copy(
            state = release.record.state.copy(lifecycle = "quarantined"),
            timestamps = release.record.timestamps.copy(uploadedAt = now, quarantinedAt = now, updatedAt = now),
        ), revision = release.revision + 1)
        when (val transition = repository.transitionRelease(release.revision, updated)) {
            is ReleaseTransitionResult.Applied -> Unit
            is ReleaseTransitionResult.Conflict -> when (transition.current.record.state.lifecycle) {
                "quarantined", "delayed", "ready", "active" -> Unit
                else -> throw RegistryException(409, "release_not_ready", "Release upload is not ready for archive storage", true, 5)
            }
            ReleaseTransitionResult.Missing -> notFound()
        }
    }

    fun completeUpload(uploadId: String, request: CompleteUploadRequest, principal: WriterPrincipal): ReleaseRecord {
        requireWritersEnabled()
        val release = ownedUpload(uploadId, principal)
        IdentityRules.requireDigest(request.archiveSha256)
        if (request.archiveSha256 != release.record.archive.sha256 || request.compressedBytes != release.record.archive.compressedBytes) {
            throw RegistryException(422, "digest_mismatch", "Completion does not match the reservation")
        }
        if (release.record.state.lifecycle != "quarantined") {
            if (release.record.state.lifecycle in setOf(
                    "first-scanning", "delayed", "second-scanning", "ready", "active", "rejected",
                )
            ) return release.record
            throw RegistryException(409, "release_not_ready", "Release upload is not ready for completion", true, 5)
        }
        storage.quarantineSource(uploadId)
            ?: throw RegistryException(409, "release_not_ready", "Archive upload is not complete", true, 5)
        // Completion seals the object but cannot start the public delay. The
        // first independently persisted source proof and passed scan do that.
        return release.record
    }

    fun listReleases(identity: String, principal: WriterPrincipal? = null): ReleasePage {
        val pkg = repository.getPackage(identity) ?: notFound()
        val includePrivate = principal?.subject == pkg.ownerPrincipal
        val records = repository.listReleases(identity).map(StoredRelease::record).filter {
            includePrivate || it.isPubliclyReadable()
        }
        if (!includePrivate && records.isEmpty()) notFound()
        return ReleasePage(records)
    }

    fun getRelease(identity: String, version: String, principal: WriterPrincipal? = null): ReleaseRecord {
        val release = repository.getRelease(identity, version) ?: notFound()
        val ownerAccess = principal?.subject == release.ownerPrincipal
        if (!ownerAccess && !release.record.isPubliclyReadable()) notFound()
        return release.record
    }

    fun listSourceProofs(identity: String, version: String, principal: WriterPrincipal? = null): SourceProofPage {
        val release = getRelease(identity, version, principal)
        val proofs = repository.listReviewArtifacts(identity, version, SOURCE_PROOF_ARTIFACT)
            .asSequence()
            .mapNotNull { artifact ->
                runCatching { RegistryJson.decodeFromJsonElement<SourceProofRecord>(artifact.payload) }.getOrNull()
            }
            .filter { proof ->
                proof.packageIdentity == identity && proof.version == version &&
                    proof.archive.packageSha256 == release.archive.sha256
            }
            .take(101)
            .toList()
        return SourceProofPage(items = proofs.take(100), hasMore = proofs.size > 100)
    }

    fun getSourceProof(
        identity: String,
        version: String,
        proofId: String,
        principal: WriterPrincipal? = null,
    ): SourceProofRecord {
        if (!Regex("^prf_[A-Za-z0-9_-]{16,96}$").matches(proofId)) notFound()
        val release = getRelease(identity, version, principal)
        val artifact = repository.getReviewArtifact(proofId)
            ?.takeIf { it.kind == SOURCE_PROOF_ARTIFACT && it.packageIdentity == identity && it.version == version }
            ?: notFound()
        val proof = runCatching { RegistryJson.decodeFromJsonElement<SourceProofRecord>(artifact.payload) }
            .getOrElse { notFound() }
        if (proof.packageIdentity != identity || proof.version != version || proof.archive.packageSha256 != release.archive.sha256) {
            notFound()
        }
        return proof
    }

    fun metadata(filename: String): ByteArray {
        if (!Regex("^(?:[1-9][0-9]*\\.)?(?:root|targets|releases|security|snapshot)\\.json$|^timestamp\\.json$").matches(filename)) notFound()
        verifyFreshMetadata("service-metadata")
        return tuf.publicMetadata(filename) ?: notFound()
    }

    fun isReady(): Boolean = try {
        verifyFreshMetadata("service-readiness")
        storage.getMetadata("1.root.json") != null &&
            storage.getMetadata("1.targets.json") != null &&
            storage.getMetadata("timestamp.json") != null
    } catch (_: Exception) {
        false
    }

    fun beginIdempotency(value: StoredIdempotency, now: Instant): IdempotencyBegin = repository.beginIdempotency(value, now)

    fun completeIdempotency(scope: String, fingerprint: String, attemptId: String, response: StoredIdempotencyResponse): Boolean =
        repository.completeIdempotency(scope, fingerprint, attemptId, response)

    fun publicBlob(digest: String): ByteArray {
        IdentityRules.requireDigest(digest)
        val authorized = repository.listReleases().any {
            it.record.archive.sha256 == digest && it.record.isPubliclyReadable()
        }
        if (!authorized) notFound()
        return storage.getPublicBlob(digest) ?: notFound()
    }

    fun downloadRelease(identity: String, version: String, principal: WriterPrincipal? = null): Pair<ReleaseRecord, ByteArray> {
        val release = getRelease(identity, version, principal)
        if (release.state.visibility != "public" || release.state.availability !in setOf("available", "yanked")) notFound()
        val bytes = storage.getPublicBlob(release.archive.sha256) ?: notFound()
        if (bytes.size.toLong() != release.archive.compressedBytes || sha256(bytes) != release.archive.sha256) {
            throw RegistryException(503, "temporarily_unavailable", "Release archive is temporarily unavailable", true, 30)
        }
        return release to bytes
    }

    private fun ownedUpload(uploadId: String, principal: WriterPrincipal): StoredRelease {
        if (!Regex("^upl_[A-Za-z0-9_-]{16,96}$").matches(uploadId)) notFound()
        val stored = repository.findReleaseByUpload(uploadId) ?: notFound()
        if (stored.ownerPrincipal != principal.subject) notFound()
        return stored
    }

    private fun hasPublicRelease(identity: String): Boolean = repository.listReleases(identity)
        .any { it.record.isPubliclyReadable() }

    private fun verifyFreshMetadata(runtime: String) {
        try {
            tuf.verifyFreshTransaction()
        } catch (error: Exception) {
            error.tufMetadataExpiryEvent(config.environment, runtime)?.let(log::error)
            throw error
        }
    }

    private fun recoverReservation(
        identity: String,
        request: ReserveReleaseRequest,
        principal: WriterPrincipal,
        capabilities: List<String>,
        dependencies: List<SignedDependency>,
        now: Instant,
    ): ReleaseReservation {
        var current = repository.getRelease(identity, request.version)
            ?: throw RegistryException(409, "version_already_reserved", "Release version is already reserved")
        while (true) {
            val exact = current.ownerPrincipal == principal.subject &&
                current.record.state.lifecycle == "reserved" &&
                current.record.state.visibility == request.visibility &&
                current.record.archive.format == request.archive.format &&
                current.record.archive.sha256 == request.archive.sha256 &&
                current.record.archive.compressedBytes == request.archive.compressedBytes &&
                current.record.manifestSha256 == request.manifestSha256 &&
                current.record.capabilities == capabilities &&
                current.manifest == request.manifest &&
                current.source == request.source &&
                current.dependencies == dependencies
            if (!exact) throw RegistryException(409, "version_already_reserved", "Release version is already reserved")
            // A newly acquired idempotency entry can replay this response for
            // another full 24 hours. Renew before returning whenever the
            // current instruction would expire inside that new replay window.
            val minimumExpiry = now.plus(UPLOAD_RESERVATION_LIFETIME)
            if (!Instant.parse(current.uploadExpiresAt).isBefore(minimumExpiry)) return reservationFor(current)

            val renewedExpiry = minimumExpiry.utc()
            val renewed = current.copy(
                record = current.record.copy(
                    timestamps = current.record.timestamps.copy(updatedAt = now.utc()),
                    uploadExpiresAt = renewedExpiry,
                ),
                uploadExpiresAt = renewedExpiry,
                revision = current.revision + 1,
            )
            when (val transition = repository.transitionRelease(current.revision, renewed)) {
                is ReleaseTransitionResult.Applied -> return reservationFor(transition.value)
                is ReleaseTransitionResult.Conflict -> current = transition.current
                ReleaseTransitionResult.Missing -> throw RegistryException(409, "version_already_reserved", "Release version is already reserved")
            }
        }
    }

    private fun reservationFor(stored: StoredRelease): ReleaseReservation = ReleaseReservation(
        release = stored.record,
        upload = UploadInstruction(
            uploadId = stored.uploadId,
            path = "/packages/api/v1/uploads/${stored.uploadId}/archive",
            expiresAt = stored.uploadExpiresAt,
            requiredHeaders = UploadRequiredHeaders(
                contentLength = stored.record.archive.compressedBytes,
                archiveSha256 = stored.record.archive.sha256,
            ),
        ),
    )

    private fun validateReservedManifest(manifest: JsonObject, identity: String, version: String, visibility: String) {
        try {
            if (manifest["manifest-version"]?.jsonPrimitive?.content != "1") invalidRequest()
            val project = manifest["project"]?.jsonObject ?: invalidRequest()
            val pkg = manifest["package"]?.jsonObject ?: invalidRequest()
            if (project["version"]?.jsonPrimitive?.content != version || pkg["identity"]?.jsonPrimitive?.content != identity || pkg["visibility"]?.jsonPrimitive?.content != visibility) invalidRequest()
            listOf("capabilities", "include", "assets").forEach { field ->
                val values = pkg[field] as? JsonArray ?: invalidRequest()
                if (values.any { !it.jsonPrimitive.isString }) invalidRequest()
            }
            if (manifest["dependencies"] !is JsonObject) invalidRequest()
        } catch (error: RegistryException) {
            throw error
        } catch (_: Exception) {
            invalidRequest()
        }
    }

    private fun manifestCapabilities(manifest: JsonObject): List<String> = try {
        manifest["package"]!!.jsonObject["capabilities"]!!.jsonArray.map { it.jsonPrimitive.content }.also { capabilities ->
            if (capabilities.size != capabilities.toSet().size || capabilities.any { it !in CAPABILITIES }) invalidRequest()
        }
    } catch (error: RegistryException) {
        throw error
    } catch (_: Exception) {
        invalidRequest()
    }

    private fun manifestDependencies(manifest: JsonObject): List<SignedDependency> {
        try {
            val registries = manifest["registries"]?.jsonObject.orEmpty()
            return manifest["dependencies"]!!.jsonObject.entries.sortedBy(Map.Entry<String, *>::key).map { (alias, value) ->
                val dep = value.jsonObject
                val packageIdentity = dep["package"]?.jsonPrimitive?.contentOrNull ?: invalidRequest()
                IdentityRules.requireIdentity(packageIdentity)
                val requirement = dep["version"]?.jsonPrimitive?.contentOrNull ?: invalidRequest()
                val registryAlias = dep["registry"]?.jsonPrimitive?.contentOrNull
                val origin = registryAlias?.let { registries[it]?.jsonPrimitive?.contentOrNull ?: invalidRequest() } ?: config.registryOrigin
                val allow = dep["allow"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                if (allow.size != allow.toSet().size || allow.any { it !in CAPABILITIES }) invalidRequest()
                SignedDependency(alias, packageIdentity, origin, requirement, allow)
            }
        } catch (error: RegistryException) {
            throw error
        } catch (_: Exception) {
            invalidRequest()
        }
    }

    private fun requireTextLimits(request: CreatePackageRequest) {
        if ((request.description?.length ?: 0) > 512 || (request.licenseSpdx?.length ?: 0) > 128 || request.repository?.startsWith("https://") == false) invalidRequest()
    }

    private fun randomId(): String = ByteArray(18).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun inspectArchiveUpload(source: ReopenableArchiveSource): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        source.openStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > ArchivePolicy.MAX_COMPRESSED_BYTES) {
                    throw RegistryException(413, "archive_too_large", "Archive exceeds the compressed byte limit")
                }
                digest.update(buffer, 0, read)
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun requireWritersEnabled() {
        if (!config.writersEnabled) throw RegistryException(503, "writer_disabled", "Registry writers are disabled")
    }
    private fun invalidRequest(): Nothing = throw RegistryException(400, "invalid_request", "Request body is invalid")
    private fun notFound(): Nothing = throw RegistryException(404, "not_found", "Resource was not found")

    private companion object {
        // Idempotency responses are retained and replayed byte-for-byte for 24
        // hours. Keep their upload instruction usable for that entire window,
        // with one hour of clock/network margin before exact recovery renews it.
        val UPLOAD_RESERVATION_LIFETIME: Duration = Duration.ofHours(25)
        val CAPABILITIES = setOf("file", "network", "process", "environment", "dynamic-load", "ffi", "unsafe", "native-link", "macro")
    }
}

private fun ReleaseRecord.isPubliclyReadable(): Boolean =
    state.lifecycle == "active" &&
        state.visibility == "public" &&
        state.availability in setOf("available", "yanked")
