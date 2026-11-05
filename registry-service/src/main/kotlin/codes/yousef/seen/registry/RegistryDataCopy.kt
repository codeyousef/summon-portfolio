package codes.yousef.seen.registry

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.util.Base64

/**
 * One-shot, read-only-source copy of a development registry from GCP
 * (Firestore + GCS) to AWS (DynamoDB + S3).
 *
 * - Dry-run unless [RegistryDataCopyConfig.confirmed]; a dry run never writes.
 * - Target writes are create-only; an existing different item or object is a
 *   conflict and is never overwritten. Identical existing data is accepted, so
 *   an interrupted copy can be re-run.
 * - Records keep their exact source JSON text; parity compares the SHA-256 of
 *   canonical JSON. Objects keep their bytes, content type and cache policy;
 *   parity compares SHA-256 of the bytes read back from S3.
 * - The source interface exposes only reads, and the GCP adapters call only
 *   get/list APIs.
 * - Only `development` is accepted, and every source and target identity is
 *   bound to the development deployment.
 */
data class RegistryDataCopyConfig(
    val environment: String,
    val projectId: String,
    val sourceFirestoreDatabase: String,
    val sourceBuckets: Map<RegistryBucketRole, String>,
    val targetBuckets: Map<RegistryBucketRole, String>,
    val objectPrefix: String,
    val awsRegion: String,
    val dynamoDbTablePrefix: String,
    val confirmed: Boolean,
) {
    init {
        require(environment == "development") {
            "The GCP to AWS data copy is limited to development to development"
        }
        require(sourceFirestoreDatabase == "seen-registry-dev") {
            "Source Firestore database must be the development registry database"
        }
        require(dynamoDbTablePrefix == DynamoDbRegistryTables.expectedPrefix(environment)) {
            "Target DynamoDB tables must be the development registry tables"
        }
        require(AWS_REGION_NAME.matches(awsRegion)) { "AWS_REGION is invalid" }
        require(PROJECT.matches(projectId)) { "GOOGLE_CLOUD_PROJECT is invalid" }
        require(sourceBuckets.keys == COPIED_ROLES && targetBuckets.keys == COPIED_ROLES) {
            "Source and target quarantine, public and metadata buckets are all required"
        }
        require(sourceBuckets.values.toSet().size == COPIED_ROLES.size && targetBuckets.values.toSet().size == COPIED_ROLES.size) {
            "Copied bucket roles must use distinct buckets"
        }
        require(sourceBuckets.values.none { PRODUCTION_MARKER.containsMatchIn(it) }) {
            "Source buckets must not be production buckets"
        }
        val targetPrefix = "${DynamoDbRegistryTables.expectedPrefix(environment)}-"
        require(targetBuckets.values.all { it.startsWith(targetPrefix) }) {
            "Target buckets must be development registry buckets ($targetPrefix*)"
        }
        // Validates name syntax through the shared S3 rules.
        RegistryObjectStoreConfig(
            provider = RegistryObjectStoreProvider.S3,
            buckets = RegistryBucketBindings(targetBuckets),
            region = awsRegion,
        )
        RegistryObjectKeys.metadata(objectPrefix, "root.json")
    }

    val repositoryId: String get() = "seen-dev-registry-v1"

    companion object {
        const val COMMAND = "copy-gcp-to-aws"
        const val CONFIRM_VARIABLE = "REGISTRY_COPY_CONFIRM"
        const val CONFIRM_VALUE = "copy-development-gcp-to-aws"

        val COPIED_ROLES: Set<RegistryBucketRole> =
            setOf(RegistryBucketRole.QUARANTINE, RegistryBucketRole.PUBLIC, RegistryBucketRole.METADATA)

        private val SOURCE_BUCKET_VARIABLES = COPIED_ROLES.associateWith { "REGISTRY_COPY_SOURCE_${it.name}_BUCKET" }

        /** The complete accepted REGISTRY_* surface; anything else is rejected. */
        internal val ALLOWED_REGISTRY_VARIABLES: Set<String> = setOf(
            "REGISTRY_ENVIRONMENT",
            "REGISTRY_COPY_SOURCE_FIRESTORE_DATABASE",
            "REGISTRY_OBJECT_PREFIX",
            "REGISTRY_DYNAMODB_TABLE_PREFIX",
            CONFIRM_VARIABLE,
        ) + SOURCE_BUCKET_VARIABLES.values + COPIED_ROLES.map(RegistryBucketRole::environmentName)

        private val PROJECT = Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")
        private val PRODUCTION_MARKER = Regex("(?:^|[-_.])prod(?:uction)?(?:$|[-_.])")

        fun fromEnvironment(env: Map<String, String> = System.getenv()): RegistryDataCopyConfig {
            val unexpected = env.keys.filter { it.startsWith("REGISTRY_") && it !in ALLOWED_REGISTRY_VARIABLES }.sorted()
            require(unexpected.isEmpty()) {
                "$COMMAND rejects unrelated registry configuration: ${unexpected.joinToString(", ")}"
            }
            val staticAws = setOf("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN").filter(env::containsKey)
            require(staticAws.isEmpty()) { "$COMMAND uses only the task role; remove ${staticAws.joinToString(", ")}" }
            fun required(name: String): String = env[name]?.trim()?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("$name is required for $COMMAND")
            val confirmation = env[CONFIRM_VARIABLE]
            require(confirmation == null || confirmation == CONFIRM_VALUE) {
                "$CONFIRM_VARIABLE must be exactly $CONFIRM_VALUE or absent for a dry run"
            }
            val environment = env["REGISTRY_ENVIRONMENT"] ?: "development"
            return RegistryDataCopyConfig(
                environment = environment,
                projectId = required("GOOGLE_CLOUD_PROJECT"),
                sourceFirestoreDatabase = required("REGISTRY_COPY_SOURCE_FIRESTORE_DATABASE"),
                sourceBuckets = SOURCE_BUCKET_VARIABLES.mapValues { (_, name) -> required(name) },
                targetBuckets = COPIED_ROLES.associateWith { required(it.environmentName) },
                objectPrefix = env["REGISTRY_OBJECT_PREFIX"]?.trim('/')?.ifEmpty { null } ?: "v1",
                awsRegion = required("AWS_REGION"),
                dynamoDbTablePrefix = env["REGISTRY_DYNAMODB_TABLE_PREFIX"]?.trim()
                    ?: DynamoDbRegistryTables.expectedPrefix(environment),
                confirmed = confirmation == CONFIRM_VALUE,
            )
        }
    }
}

data class SourceDocument(val id: String, val fields: Map<String, Any?>)

data class SourceObject(val key: String, val bytes: ByteArray, val contentType: String?, val cacheControl: String?)

/** Read-only view of the GCP registry. There is deliberately no write method. */
interface RegistryCopySource : AutoCloseable {
    fun documents(collection: String): List<SourceDocument>
    fun objectKeys(role: RegistryBucketRole, prefix: String): List<String>
    fun getObject(role: RegistryBucketRole, key: String): SourceObject?
    override fun close() = Unit
}

/** Firestore and GCS adapter that issues only document reads and object get/list calls. */
class GcpRegistryCopySource internal constructor(
    private val firestore: Firestore,
    private val storage: Storage,
    private val buckets: Map<RegistryBucketRole, String>,
) : RegistryCopySource {
    override fun documents(collection: String): List<SourceDocument> {
        require(collection in FIRESTORE_COLLECTIONS) { "Unexpected source collection" }
        return firestore.collection(collection).get().get().documents.map { SourceDocument(it.id, it.data) }
    }

    override fun objectKeys(role: RegistryBucketRole, prefix: String): List<String> =
        storage.list(bucket(role), Storage.BlobListOption.prefix(prefix)).iterateAll().map { it.name }.sorted()

    override fun getObject(role: RegistryBucketRole, key: String): SourceObject? {
        val blob = storage.get(bucket(role), key) ?: return null
        val size = requireNotNull(blob.size) { "Source object size is missing" }
        require(size <= MAXIMUM_COPIED_OBJECT_BYTES) { "Source object exceeds the copy size bound" }
        return SourceObject(key, blob.getContent(), blob.contentType, blob.cacheControl)
    }

    private fun bucket(role: RegistryBucketRole) = requireNotNull(buckets[role]) { "Source bucket is not configured" }

    override fun close() {
        firestore.close()
        storage.close()
    }

    companion object {
        fun create(config: RegistryDataCopyConfig): GcpRegistryCopySource {
            val credentials = FirestoreCredentials.fromEnvironment()
            val firestore = FirestoreOptions.newBuilder()
                .setProjectId(config.projectId)
                .setDatabaseId(config.sourceFirestoreDatabase)
                .apply { credentials?.let(::setCredentials) }
                .build()
                .service
            val storage = StorageOptions.newBuilder()
                .setProjectId(config.projectId)
                .apply { credentials?.let(::setCredentials) }
                .build()
                .service
            return GcpRegistryCopySource(firestore, storage, config.sourceBuckets)
        }
    }
}

enum class CopyOutcome { CREATED, ALREADY_IDENTICAL, WOULD_CREATE, SKIPPED, CONFLICT, MISMATCH }

data class CopySectionReport(
    val name: String,
    val source: Int,
    val outcomes: Map<CopyOutcome, Int>,
    val targetCount: Int?,
    val problems: List<String>,
)

data class RegistryDataCopyReport(val dryRun: Boolean, val sections: List<CopySectionReport>) {
    val conflicts: Int get() = sections.sumOf { it.outcomes[CopyOutcome.CONFLICT] ?: 0 }
    val mismatches: Int get() = sections.sumOf { it.outcomes[CopyOutcome.MISMATCH] ?: 0 }
    val pending: Int get() = sections.sumOf { it.outcomes[CopyOutcome.WOULD_CREATE] ?: 0 }

    /** Parity holds when every source record and object exists identically in AWS. */
    val parity: Boolean get() = conflicts == 0 && mismatches == 0 && pending == 0

    fun render(): String = buildString {
        appendLine("copy_mode=${if (dryRun) "dry-run" else "apply"}")
        sections.forEach { section ->
            append("section=${section.name} source=${section.source}")
            CopyOutcome.entries.forEach { outcome -> append(" ${outcome.name.lowercase()}=${section.outcomes[outcome] ?: 0}") }
            section.targetCount?.let { append(" target_total=$it") }
            appendLine()
            // Problems name only keys and hashes; never record contents.
            section.problems.forEach { appendLine("problem section=${section.name} $it") }
        }
        appendLine("conflicts=$conflicts mismatches=$mismatches pending=$pending parity=$parity")
    }
}

class RegistryDataCopy internal constructor(
    private val config: RegistryDataCopyConfig,
    private val source: RegistryCopySource,
    private val tables: DynamoDbRegistryTables,
    private val dynamo: DynamoTableClient,
    private val objects: S3CompatibleObjectClient,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = RegistryJson,
) {
    private val items = DynamoDbRegistryRepository(dynamo, tables, json)
    private val write = config.confirmed

    fun run(): RegistryDataCopyReport {
        requireDevelopmentSourceIdentity()
        requireNoLivePublicationLease()
        val sections = listOf(
            copyPackages(),
            copyReleases(),
            copyReviewArtifacts(),
            copyIdempotency(),
            copyState(),
        ) + RegistryDataCopyConfig.COPIED_ROLES.sortedBy { it.ordinal }.map(::copyBucket)
        return RegistryDataCopyReport(dryRun = !write, sections = sections)
    }

    /** A source whose trust root names another deployment is never copied. */
    private fun requireDevelopmentSourceIdentity() {
        val root = source.getObject(RegistryBucketRole.METADATA, RegistryObjectKeys.metadata(config.objectPrefix, "1.root.json"))
            ?: return
        val signed = runCatching { json.parseToJsonElement(root.bytes.decodeToString()).jsonObject.getValue("signed").jsonObject }
            .getOrElse { throw IllegalStateException("Source 1.root.json is not readable TUF metadata", it) }
        check(signed["environment"]?.jsonPrimitive?.content == config.environment &&
            signed["repository_id"]?.jsonPrimitive?.content == config.repositoryId) {
            "Source trust root does not belong to the development registry"
        }
    }

    private fun requireNoLivePublicationLease() {
        val state = source.documents(STATE_COLLECTION).firstOrNull { it.id == STATE_DOCUMENT } ?: return
        val holder = state.fields["publication_holder"] as? String ?: return
        val expiry = (state.fields["publication_expires_at"] as? String)?.let(Instant::parse) ?: return
        check(!expiry.isAfter(clock.instant())) {
            "Source metadata publication lease held by $holder is live; freeze publication before copying"
        }
    }

    private fun copyPackages() = recordSection("packages", PACKAGES_COLLECTION) { document, encoded ->
        val value = json.decodeFromString(StoredPackage.serializer(), encoded)
        requireDocumentId(document, value.record.identity)
        listOf(DynamoPut(tables.packages, items.packageItem(value, encoded), DynamoCondition.Absent(PK)))
    }

    private fun copyReleases() = recordSection("releases", RELEASES_COLLECTION) { document, encoded ->
        val value = json.decodeFromString(StoredRelease.serializer(), encoded)
        requireDocumentId(document, "${value.record.`package`}@${value.record.version}")
        check(document.fields["upload_id"] == value.uploadId) { "Source release upload ID field is inconsistent" }
        listOf(
            DynamoPut(tables.releases, items.releaseItem(value, encoded), DynamoCondition.Absent(PK)),
            DynamoPut(tables.releaseUploads, items.uploadItem(value), DynamoCondition.Absent(PK)),
        )
    }

    private fun copyReviewArtifacts() = recordSection("review_artifacts", REVIEW_ARTIFACTS_COLLECTION) { document, encoded ->
        val value = json.decodeFromString(ReviewArtifact.serializer(), encoded)
        requireDocumentId(document, value.artifactId)
        items.artifactPuts(value, encoded)
    }

    private fun copyIdempotency() = recordSection("idempotency", IDEMPOTENCY_COLLECTION) { document, encoded ->
        val value = json.decodeFromString(StoredIdempotency.serializer(), encoded)
        requireDocumentId(document, value.scope)
        listOf(DynamoPut(tables.idempotency, items.idempotencyItem(value, encoded), DynamoCondition.Absent(PK)))
    }

    /** Copies only the metadata version counter; the publication lease is transient and never copied. */
    private fun copyState(): CopySectionReport {
        val documents = source.documents(STATE_COLLECTION)
        val counts = linkedMapOf<CopyOutcome, Int>()
        val problems = mutableListOf<String>()
        documents.forEach { document ->
            if (document.id != STATE_DOCUMENT) {
                counts.bump(CopyOutcome.CONFLICT)
                problems += "unexpected_document=${document.id}"
                return@forEach
            }
            val version = (document.fields["version"] as? Number)?.toLong()
            if (version == null) {
                counts.bump(CopyOutcome.SKIPPED)
                return@forEach
            }
            val key = items.key(DynamoDbRegistryRepository.STATE_METADATA)
            val outcome = writeOrCompare(
                puts = listOf(DynamoPut(tables.state, items.stateItem(version), DynamoCondition.Absent(PK))),
                matches = { put ->
                    dynamo.get(put.table, key)?.get(DynamoDbRegistryRepository.METADATA_VERSION)?.n() == version.toString()
                },
            )
            counts.bump(outcome)
            if (outcome == CopyOutcome.CONFLICT || outcome == CopyOutcome.MISMATCH) problems += "state=metadata outcome=$outcome"
        }
        return CopySectionReport("state", documents.size, counts, dynamo.scan(tables.state).size, problems)
    }

    private fun recordSection(
        name: String,
        collection: String,
        plan: (SourceDocument, String) -> List<DynamoPut>,
    ): CopySectionReport {
        val documents = source.documents(collection)
        val counts = linkedMapOf<CopyOutcome, Int>()
        val problems = mutableListOf<String>()
        documents.forEach { document ->
            val encoded = document.fields["json"] as? String
            if (encoded == null) {
                counts.bump(CopyOutcome.CONFLICT)
                problems += "document=${document.id} reason=missing_json"
                return@forEach
            }
            val expectedHash = canonicalHash(encoded)
            val puts = plan(document, encoded)
            val outcome = writeOrCompare(puts) { put ->
                val stored = dynamo.get(put.table, keyOf(put)) ?: return@writeOrCompare false
                if (put.item.containsKey(DynamoDbRegistryRepository.JSON) || put.item.containsKey(DynamoDbRegistryRepository.JSON_GZIP)) {
                    canonicalHash(items.storedJson(stored)) == expectedHash
                } else {
                    put.item.all { (attribute, value) -> stored[attribute] == value }
                }
            }
            counts.bump(outcome)
            if (outcome == CopyOutcome.CONFLICT || outcome == CopyOutcome.MISMATCH) {
                problems += "document=${document.id} canonical_sha256=$expectedHash outcome=$outcome"
            }
        }
        val primaryTable = primaryTable(name)
        return CopySectionReport(name, documents.size, counts, dynamo.scan(primaryTable).size, problems)
    }

    /**
     * Create-only write of one logical record (all its items in one
     * transaction), followed by read-back verification. Existing items are
     * compared, never replaced.
     */
    private fun writeOrCompare(puts: List<DynamoPut>, matches: (DynamoPut) -> Boolean): CopyOutcome {
        val existing = puts.map { dynamo.get(it.table, keyOf(it)) != null }
        if (existing.all { it }) {
            return if (puts.all(matches)) CopyOutcome.ALREADY_IDENTICAL else CopyOutcome.CONFLICT
        }
        if (existing.any { it }) return CopyOutcome.CONFLICT
        if (!write) return CopyOutcome.WOULD_CREATE
        return when (dynamo.transactWrite(puts)) {
            DynamoTransactionResult.Applied ->
                if (puts.all(matches)) CopyOutcome.CREATED else CopyOutcome.MISMATCH
            is DynamoTransactionResult.Cancelled ->
                if (puts.all(matches)) CopyOutcome.ALREADY_IDENTICAL else CopyOutcome.CONFLICT
        }
    }

    private fun copyBucket(role: RegistryBucketRole): CopySectionReport {
        val prefix = "${config.objectPrefix}/"
        val bucket = config.targetBuckets.getValue(role)
        val keys = source.objectKeys(role, prefix)
        // Pointers last: a partial copy never exposes a pointer whose
        // referenced immutable metadata is not yet present.
        val pointers = POINTERS.map { RegistryObjectKeys.metadata(config.objectPrefix, it) }
        val ordered = keys.filterNot { it in pointers } + pointers.filter { it in keys }
        val counts = linkedMapOf<CopyOutcome, Int>()
        val problems = mutableListOf<String>()
        ordered.forEach { key ->
            val objectSource = source.getObject(role, key)
            if (objectSource == null) {
                counts.bump(CopyOutcome.CONFLICT)
                problems += "key=$key reason=source_disappeared"
                return@forEach
            }
            val digest = sha256(objectSource.bytes)
            val outcome = copyObject(role, bucket, objectSource, digest)
            counts.bump(outcome)
            if (outcome == CopyOutcome.CONFLICT || outcome == CopyOutcome.MISMATCH) {
                problems += "key=$key sha256=$digest outcome=$outcome"
            }
        }
        val targetTotal = runCatching { objects.keys(bucket, prefix).size }.getOrNull()
        return CopySectionReport("bucket_${role.name.lowercase()}", keys.size, counts, targetTotal, problems)
    }

    private fun copyObject(role: RegistryBucketRole, bucket: String, value: SourceObject, digest: String): CopyOutcome {
        if (role == RegistryBucketRole.PUBLIC) {
            // Public archives are content-addressed: the key names the digest.
            val expected = value.key.substringAfterLast('/')
            if (expected != digest) return CopyOutcome.MISMATCH
        }
        val contentType = value.contentType ?: return CopyOutcome.CONFLICT
        val cacheControl = value.cacheControl ?: return CopyOutcome.CONFLICT
        fun identical(): Boolean {
            val stored = objects.get(bucket, value.key) ?: return false
            return sha256(stored.bytes) == digest && (stored.sha256 == null || stored.sha256 == digest) &&
                stored.contentType == contentType && stored.cacheControl == cacheControl
        }
        if (objects.get(bucket, value.key) != null) {
            return if (identical()) CopyOutcome.ALREADY_IDENTICAL else CopyOutcome.CONFLICT
        }
        if (!write) return CopyOutcome.WOULD_CREATE
        val created = objects.create(bucket, value.key, value.bytes, contentType, cacheControl)
        return when {
            created && identical() -> CopyOutcome.CREATED
            created -> CopyOutcome.MISMATCH
            identical() -> CopyOutcome.ALREADY_IDENTICAL
            else -> CopyOutcome.CONFLICT
        }
    }

    private fun primaryTable(section: String): String = when (section) {
        "packages" -> tables.packages
        "releases" -> tables.releases
        "review_artifacts" -> tables.reviewArtifacts
        "idempotency" -> tables.idempotency
        else -> error("Unknown copy section")
    }

    private fun keyOf(put: DynamoPut): DynamoItem =
        requireNotNull(tables.keySchema[put.table]).associateWith { put.item.getValue(it) }

    private fun requireDocumentId(document: SourceDocument, logicalId: String) {
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(logicalId.toByteArray())
        check(document.id == expected) { "Source document ${document.id} does not match its record identity" }
    }

    private fun canonicalHash(encoded: String): String =
        sha256(canonicalJson(json.parseToJsonElement(encoded).also { check(it is JsonObject) }))

    private fun MutableMap<CopyOutcome, Int>.bump(outcome: CopyOutcome) {
        this[outcome] = (this[outcome] ?: 0) + 1
    }

    companion object {
        fun create(config: RegistryDataCopyConfig): Triple<RegistryDataCopy, AutoCloseable, AutoCloseable> {
            val tables = DynamoDbRegistryTables(config.dynamoDbTablePrefix)
            val source = GcpRegistryCopySource.create(config)
            val dynamo = AwsSdkDynamoTableClient.create(config.awsRegion, tables)
            val objects = AwsSdkS3CompatibleObjectClient.create(RegistryObjectStoreConfig(
                provider = RegistryObjectStoreProvider.S3,
                buckets = RegistryBucketBindings(config.targetBuckets),
                region = config.awsRegion,
            ))
            val copy = RegistryDataCopy(config, source, tables, dynamo, objects)
            return Triple(copy, source, AutoCloseable { dynamo.close(); objects.close() })
        }
    }
}

object RegistryDataCopyRuntime {
    /** Exit status is non-zero unless parity holds (apply) or the plan is conflict-free (dry run). */
    fun run(config: RegistryDataCopyConfig = RegistryDataCopyConfig.fromEnvironment()): RegistryDataCopyReport {
        val (copy, source, target) = RegistryDataCopy.create(config)
        val report = source.use { target.use { copy.run() } }
        print(report.render())
        check(report.conflicts == 0 && report.mismatches == 0) { "Data copy found conflicts or mismatches" }
        check(report.dryRun || report.parity) { "Data copy did not reach parity" }
        return report
    }
}

internal const val PACKAGES_COLLECTION = "seen_registry_packages_v1"
internal const val RELEASES_COLLECTION = "seen_registry_releases_v1"
internal const val STATE_COLLECTION = "seen_registry_state_v1"
internal const val IDEMPOTENCY_COLLECTION = "seen_registry_idempotency_v1"
internal const val REVIEW_ARTIFACTS_COLLECTION = "seen_registry_review_artifacts_v1"
private const val STATE_DOCUMENT = "metadata"
private val FIRESTORE_COLLECTIONS = setOf(
    PACKAGES_COLLECTION,
    RELEASES_COLLECTION,
    STATE_COLLECTION,
    IDEMPOTENCY_COLLECTION,
    REVIEW_ARTIFACTS_COLLECTION,
)
private val POINTERS = listOf("root.json", "timestamp.json")
private const val MAXIMUM_COPIED_OBJECT_BYTES = ArchivePolicy.MAX_COMPRESSED_BYTES
