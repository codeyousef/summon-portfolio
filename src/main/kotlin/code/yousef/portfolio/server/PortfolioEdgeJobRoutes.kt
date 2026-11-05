package code.yousef.portfolio.server

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.MutationCoordinator
import code.yousef.portfolio.finops.FinOpsRollupBackfillRequest
import code.yousef.portfolio.finops.FinOpsRollupBackfillResult
import code.yousef.portfolio.finops.FinOpsRollupBackfillService
import code.yousef.portfolio.finops.FinOpsRollupParityReport
import code.yousef.portfolio.finops.FinOpsRollupReconcileRequest
import code.yousef.portfolio.photography.PhotographyAssetBackfillRequest
import code.yousef.portfolio.photography.PhotographyAssetBackfillException
import code.yousef.portfolio.photography.PhotographyAssetBackfillService
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.web.Router
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.time.Instant

private const val MAX_EDGE_JOB_BODY_BYTES = 160 * 1024
private const val REPLAY_BATCH_SIZE = 100
private const val MAX_REPLAY_BATCHES = 10
private const val MAX_PHOTOGRAPHY_BACKFILL_ASSETS = 25
private val edgeJobJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private val edgeJobId = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
private val edgeJobSha = Regex("^[a-f0-9]{64}$")
private val photographyBackfillId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
private val photographyContentTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "video/mp4",
    "video/webm",
    "video/quicktime",
)

@Serializable
internal data class PortfolioEdgeJobEnvelope(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val expectedRevision: Long,
    val authorityEpoch: Long,
    val occurredAt: String,
    val payloadSha256: String,
    val payload: JsonElement,
)

@Serializable
private data class PortfolioEdgeJobReceipt(
    val id: String,
    val committedEpoch: Long,
    val newRevision: Long,
    val firestoreCommitTime: String,
    val mirrorState: String,
)

@Serializable
internal data class PhotographyBackfillPayload(
    val assets: List<PhotographyBackfillAsset>,
)

@Serializable
internal data class PhotographyBackfillAsset(
    val photoId: String,
    val sourceStorageKey: String,
    val contentType: String,
    val expectedSha256: String,
    val expectedSizeBytes: Long,
)

@Serializable
private data class PhotographyBackfillAssetReceipt(
    val photoId: String,
    val sourceStorageKey: String,
    val contentAddressedStorageKey: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
)

@Serializable
private data class PhotographyBackfillJobReceipt(
    val id: String,
    val committedEpoch: Long,
    val newRevision: Long,
    val stagedAt: String,
    val mirrorState: String,
    val assets: List<PhotographyBackfillAssetReceipt>,
)

@Serializable
private data class FinOpsRollupBackfillJobReceipt(
    val id: String,
    val committedEpoch: Long,
    val newRevision: Long,
    val firestoreCommitTime: String,
    val mirrorState: String,
    val result: FinOpsRollupBackfillResult,
)

@Serializable
private data class FinOpsRollupReconciliationJobReceipt(
    val id: String,
    val committedEpoch: Long,
    val newRevision: Long,
    val firestoreCommitTime: String,
    val mirrorState: String,
    val report: FinOpsRollupParityReport,
)

internal fun Router.registerPortfolioEdgeJobRoutes(
    mutationCoordinator: MutationCoordinator?,
    photographyAssetBackfillService: PhotographyAssetBackfillService? = null,
    finOpsRollupBackfillService: FinOpsRollupBackfillService? = null,
    photographyMaxAssetBytes: Long = 15_728_640,
    environment: (String) -> String? = System::getenv,
) {
    post("/internal/edge/jobs/portfolio.migration.reconcile") { exchange ->
        exchange.handlePortfolioEdgeJob(
            jobType = "portfolio.migration.reconcile",
            mutationCoordinator = mutationCoordinator,
            photographyAssetBackfillService = photographyAssetBackfillService,
            photographyMaxAssetBytes = photographyMaxAssetBytes,
            environment = environment,
        )
    }
    post("/internal/edge/jobs/portfolio.photography.backfill") { exchange ->
        exchange.handlePortfolioEdgeJob(
            jobType = "portfolio.photography.backfill",
            mutationCoordinator = mutationCoordinator,
            photographyAssetBackfillService = photographyAssetBackfillService,
            photographyMaxAssetBytes = photographyMaxAssetBytes,
            environment = environment,
        )
    }
    post("/internal/edge/jobs/portfolio.finops.rollups.v2.backfill") { exchange ->
        exchange.handlePortfolioEdgeJob(
            jobType = "portfolio.finops.rollups.v2.backfill",
            mutationCoordinator = mutationCoordinator,
            photographyAssetBackfillService = photographyAssetBackfillService,
            finOpsRollupBackfillService = finOpsRollupBackfillService,
            photographyMaxAssetBytes = photographyMaxAssetBytes,
            environment = environment,
        )
    }
    post("/internal/edge/jobs/portfolio.finops.rollups.v2.reconcile") { exchange ->
        exchange.handlePortfolioEdgeJob(
            jobType = "portfolio.finops.rollups.v2.reconcile",
            mutationCoordinator = mutationCoordinator,
            photographyAssetBackfillService = photographyAssetBackfillService,
            finOpsRollupBackfillService = finOpsRollupBackfillService,
            photographyMaxAssetBytes = photographyMaxAssetBytes,
            environment = environment,
        )
    }
}

private suspend fun Exchange.handlePortfolioEdgeJob(
    jobType: String,
    mutationCoordinator: MutationCoordinator?,
    photographyAssetBackfillService: PhotographyAssetBackfillService?,
    finOpsRollupBackfillService: FinOpsRollupBackfillService? = null,
    photographyMaxAssetBytes: Long,
    environment: (String) -> String?,
) {
    val configuredToken = environment("EDGE_ORIGIN_TOKEN")?.trim()
    val supplied = request.headers.get("Authorization")
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.takeIf { it.length in 32..512 }
    if (
        configuredToken == null || configuredToken.length !in 32..512 || supplied == null ||
        !MessageDigest.isEqual(configuredToken.toByteArray(), supplied.toByteArray())
    ) {
        respondEdgeJobJson(401, mapOf("error" to "unauthorized"))
        return
    }
    val envelope = decodeAndValidateEdgeJobEnvelope()
    if (envelope == null || envelope.aggregateType != jobType) {
        respondEdgeJobJson(400, mapOf("error" to "invalid mutation envelope"))
        return
    }
    when (jobType) {
        "portfolio.migration.reconcile" -> runMigrationReconciliation(
            envelope = envelope,
            mutationCoordinator = mutationCoordinator,
            enabled = environment("PORTFOLIO_MIGRATION_EXECUTION_ENABLED") == "true",
        )
        "portfolio.photography.backfill" -> runPhotographyBackfill(
            envelope = envelope,
            service = photographyAssetBackfillService,
            maxAssetBytes = photographyMaxAssetBytes,
            enabled = environment("PHOTOGRAPHY_MIGRATION_EXECUTION_ENABLED") == "true",
        )
        "portfolio.finops.rollups.v2.backfill" -> runFinOpsRollupBackfill(
            envelope = envelope,
            service = finOpsRollupBackfillService,
            enabled = environment("FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED") == "true",
        )
        "portfolio.finops.rollups.v2.reconcile" -> runFinOpsRollupReconciliation(
            envelope = envelope,
            service = finOpsRollupBackfillService,
            enabled = environment("FINOPS_SHARDED_ROLLUP_BACKFILL_EXECUTION_ENABLED") == "true",
        )
    }
}

private suspend fun Exchange.runFinOpsRollupBackfill(
    envelope: PortfolioEdgeJobEnvelope,
    service: FinOpsRollupBackfillService?,
    enabled: Boolean,
) {
    if (!enabled || service == null) {
        respondEdgeJobJson(503, mapOf("error" to "FinOps rollup backfill execution is disabled"))
        return
    }
    if (envelope.authorityEpoch != 1L) {
        respondEdgeJobJson(409, mapOf("error" to "invalid FinOps rollup backfill authority"))
        return
    }
    val request = runCatching {
        edgeJobJson.decodeFromJsonElement(FinOpsRollupBackfillRequest.serializer(), envelope.payload)
    }.getOrNull()
    if (request == null) {
        respondEdgeJobJson(400, mapOf("error" to "invalid FinOps rollup backfill payload"))
        return
    }
    val result = runCatching { service.backfillPage(request) }.getOrElse {
        respondEdgeJobJson(503, mapOf("error" to "FinOps rollup backfill failed"))
        return
    }
    respondEdgeJobJson(
        200,
        FinOpsRollupBackfillJobReceipt(
            id = envelope.id,
            committedEpoch = envelope.authorityEpoch,
            newRevision = Math.addExact(envelope.expectedRevision, result.processed.toLong()),
            firestoreCommitTime = Instant.now().toString(),
            mirrorState = if (result.complete) "mirrored" else "pending",
            result = result,
        ),
    )
}

private suspend fun Exchange.runFinOpsRollupReconciliation(
    envelope: PortfolioEdgeJobEnvelope,
    service: FinOpsRollupBackfillService?,
    enabled: Boolean,
) {
    if (!enabled || service == null) {
        respondEdgeJobJson(503, mapOf("error" to "FinOps rollup backfill execution is disabled"))
        return
    }
    if (envelope.authorityEpoch != 1L || envelope.expectedRevision != 0L) {
        respondEdgeJobJson(409, mapOf("error" to "invalid FinOps rollup reconciliation authority"))
        return
    }
    val request = runCatching {
        edgeJobJson.decodeFromJsonElement(FinOpsRollupReconcileRequest.serializer(), envelope.payload)
    }.getOrNull()
    if (request == null) {
        respondEdgeJobJson(400, mapOf("error" to "invalid FinOps rollup reconciliation payload"))
        return
    }
    val report = runCatching { service.reconcile(request.from, request.toExclusive) }.getOrElse {
        respondEdgeJobJson(503, mapOf("error" to "FinOps rollup reconciliation failed"))
        return
    }
    respondEdgeJobJson(
        200,
        FinOpsRollupReconciliationJobReceipt(
            id = envelope.id,
            committedEpoch = envelope.authorityEpoch,
            newRevision = 0,
            firestoreCommitTime = Instant.now().toString(),
            mirrorState = if (report.ready) "mirrored" else "pending",
            report = report,
        ),
    )
}

private suspend fun Exchange.runMigrationReconciliation(
    envelope: PortfolioEdgeJobEnvelope,
    mutationCoordinator: MutationCoordinator?,
    enabled: Boolean,
) {
    if (!enabled) {
        respondEdgeJobJson(503, mapOf("error" to "migration execution is disabled"))
        return
    }
    val coordinator = mutationCoordinator
    if (coordinator == null || coordinator.writeMode == FirestoreWriteMode.SOURCE) {
        respondEdgeJobJson(409, mapOf("error" to "no migration mirror is configured"))
        return
    }
    val expectedEpoch = if (coordinator.writeMode == FirestoreWriteMode.TARGET) 2L else 1L
    if (envelope.authorityEpoch != expectedEpoch) {
        respondEdgeJobJson(409, mapOf("error" to "stale authority epoch"))
        return
    }

    var mirrored = 0
    repeat(MAX_REPLAY_BATCHES) {
        val batch = coordinator.replayPendingBatch(REPLAY_BATCH_SIZE)
        mirrored += batch.mirrored.size
        if (batch.mirrored.size != batch.attempted) {
            respondEdgeJobJson(503, mapOf("error" to "migration mirror remains pending"))
            return
        }
        if (batch.attempted < REPLAY_BATCH_SIZE) {
            respondEdgeJobJson(
                200,
                PortfolioEdgeJobReceipt(
                    id = envelope.id,
                    committedEpoch = envelope.authorityEpoch,
                    newRevision = envelope.expectedRevision + mirrored,
                    firestoreCommitTime = Instant.now().toString(),
                    mirrorState = "mirrored",
                ),
            )
            return
        }
    }
    respondEdgeJobJson(503, mapOf("error" to "migration reconciliation requires another pass"))
}

private suspend fun Exchange.runPhotographyBackfill(
    envelope: PortfolioEdgeJobEnvelope,
    service: PhotographyAssetBackfillService?,
    maxAssetBytes: Long,
    enabled: Boolean,
) {
    if (!enabled || service == null) {
        respondEdgeJobJson(503, mapOf("error" to "photography migration execution is disabled"))
        return
    }
    if (envelope.authorityEpoch != 1L || envelope.expectedRevision != 0L) {
        respondEdgeJobJson(409, mapOf("error" to "invalid photography staging authority"))
        return
    }
    val payload = decodeAndValidatePhotographyBackfillPayload(envelope.payload, maxAssetBytes)
    if (payload == null) {
        respondEdgeJobJson(400, mapOf("error" to "invalid photography backfill payload"))
        return
    }
    val receipts = runCatching {
        payload.assets.map { asset ->
            val receipt = service.stage(
                PhotographyAssetBackfillRequest(
                    photoId = asset.photoId,
                    sourceStorageKey = asset.sourceStorageKey,
                    contentType = asset.contentType,
                ),
            )
            require(receipt.sha256 == asset.expectedSha256 && receipt.sizeBytes == asset.expectedSizeBytes) {
                "photography staged asset does not match the approved inventory"
            }
            PhotographyBackfillAssetReceipt(
                photoId = receipt.photoId,
                sourceStorageKey = receipt.sourceStorageKey,
                contentAddressedStorageKey = receipt.contentAddressedStorageKey,
                sha256 = receipt.sha256,
                sizeBytes = receipt.sizeBytes,
                contentType = receipt.contentType,
            )
        }
    }.getOrElse { failure ->
        val stage = (failure as? PhotographyAssetBackfillException)?.stageCode ?: "unclassified"
        respondEdgeJobJson(503, mapOf("error" to "photography staging failed", "stage" to stage))
        return
    }
    respondEdgeJobJson(
        200,
        PhotographyBackfillJobReceipt(
            id = envelope.id,
            committedEpoch = envelope.authorityEpoch,
            newRevision = receipts.size.toLong(),
            stagedAt = Instant.now().toString(),
            mirrorState = "staged",
            assets = receipts,
        ),
    )
}

internal fun decodeAndValidatePhotographyBackfillPayload(
    payload: JsonElement,
    maxAssetBytes: Long,
): PhotographyBackfillPayload? {
    if (maxAssetBytes !in 1..Int.MAX_VALUE.toLong()) return null
    val decoded = runCatching {
        edgeJobJson.decodeFromJsonElement(PhotographyBackfillPayload.serializer(), payload)
    }.getOrNull() ?: return null
    if (decoded.assets.size !in 1..MAX_PHOTOGRAPHY_BACKFILL_ASSETS) return null
    if (decoded.assets.map { it.photoId }.toSet().size != decoded.assets.size) return null
    return decoded.takeIf { candidate ->
        candidate.assets.all { asset ->
            val keySegments = asset.sourceStorageKey.split('/')
            val fileName = keySegments.lastOrNull().orEmpty()
            photographyBackfillId.matches(asset.photoId) &&
                asset.sourceStorageKey.startsWith("photography/") &&
                asset.sourceStorageKey.length <= 512 &&
                asset.sourceStorageKey.none(Char::isISOControl) &&
                keySegments.none { it.isBlank() || it == "." || it == ".." } &&
                fileName.startsWith("${asset.photoId}.") &&
                asset.contentType in photographyContentTypes &&
                edgeJobSha.matches(asset.expectedSha256) &&
                asset.expectedSizeBytes in 1..maxAssetBytes
        }
    }
}

private suspend fun Exchange.decodeAndValidateEdgeJobEnvelope(): PortfolioEdgeJobEnvelope? {
    val declared = request.headers.get("Content-Length")?.toLongOrNull()
    if (declared != null && declared !in 0..MAX_EDGE_JOB_BODY_BYTES.toLong()) return null
    val body = request.bodyText()
    if (body.toByteArray(Charsets.UTF_8).size > MAX_EDGE_JOB_BODY_BYTES) return null
    val envelope = runCatching { edgeJobJson.decodeFromString<PortfolioEdgeJobEnvelope>(body) }.getOrNull()
        ?: return null
    if (
        !edgeJobId.matches(envelope.id) || !edgeJobId.matches(envelope.aggregateType) ||
        !edgeJobId.matches(envelope.aggregateId) || envelope.expectedRevision < 0 ||
        envelope.authorityEpoch !in 1..2 || runCatching { Instant.parse(envelope.occurredAt) }.isFailure ||
        !edgeJobSha.matches(envelope.payloadSha256)
    ) return null
    val actual = MessageDigest.getInstance("SHA-256")
        .digest(canonicalEdgeJson(envelope.payload).toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return envelope.takeIf { MessageDigest.isEqual(actual.toByteArray(), envelope.payloadSha256.toByteArray()) }
}

internal fun canonicalEdgeJson(value: JsonElement): String = when (value) {
    JsonNull -> "null"
    is JsonPrimitive -> if (value.isString) edgeJobJson.encodeToString(value.content) else value.content
    is JsonArray -> value.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::canonicalEdgeJson)
    is JsonObject -> value.entries.sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, entry) ->
            "${edgeJobJson.encodeToString(key)}:${canonicalEdgeJson(entry)}"
        }
}

private suspend inline fun <reified T> Exchange.respondEdgeJobJson(statusCode: Int, value: T) {
    respondBytes(
        statusCode,
        "application/json; charset=utf-8",
        edgeJobJson.encodeToString(value).toByteArray(Charsets.UTF_8),
    )
}
