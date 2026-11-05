package code.yousef.firestore.migration

import code.yousef.config.FirestoreWriteMode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

interface MutationBackend {
    /**
     * Commits the aggregate mutation, revision state, idempotency receipt, and,
     * when requested, a pending outbox entry as one atomic operation.
     */
    fun commit(envelope: MutationEnvelope, enqueueForMirror: Boolean): MutationReceipt

    /** Loads pending source mutations for an idempotent asynchronous replay. */
    fun pending(limit: Int): List<MutationEnvelope>

    /** Marks an already committed source outbox entry as mirrored. */
    fun markMirrored(mutationId: String, targetReceipt: MutationReceipt): MutationReceipt
}

data class MutationReplayBatch(
    val attempted: Int,
    val mirrored: List<MutationReceipt>,
)

class MutationCoordinator(
    val writeMode: FirestoreWriteMode,
    private val source: MutationBackend?,
    private val target: MutationBackend?,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(MutationCoordinator::class.java)

    init {
        require(writeMode != FirestoreWriteMode.SOURCE || source != null) {
            "Source write mode requires a source mutation backend"
        }
        require(writeMode != FirestoreWriteMode.DUAL || (source != null && target != null)) {
            "Dual write mode requires source and target mutation backends"
        }
        require(writeMode != FirestoreWriteMode.TARGET || (source != null && target != null)) {
            "Target write mode requires target authority and source reverse-mirror backends"
        }
    }

    fun newUpsert(
        aggregateType: String,
        aggregateId: String,
        payload: Map<String, Any?>,
        expectedRevision: Long = MutationEnvelope.ANY_REVISION,
        id: String = idFactory(),
        occurredAt: Instant = clock.instant(),
    ): MutationEnvelope = MutationEnvelope.create(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedRevision = expectedRevision,
        authorityEpoch = activeAuthorityEpoch(),
        occurredAt = occurredAt,
        payload = payload,
        operation = MutationOperation.UPSERT,
    )

    fun newDelete(
        aggregateType: String,
        aggregateId: String,
        expectedRevision: Long = MutationEnvelope.ANY_REVISION,
        id: String = idFactory(),
        occurredAt: Instant = clock.instant(),
    ): MutationEnvelope = MutationEnvelope.create(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedRevision = expectedRevision,
        authorityEpoch = activeAuthorityEpoch(),
        occurredAt = occurredAt,
        payload = emptyMap(),
        operation = MutationOperation.DELETE,
    )

    /**
     * Applies a field-level Firestore merge on both the active authority and its mirror.
     * This keeps independently updated map fields (for example curriculum progress or
     * one-time-token state) from being lost to a read/replace race during migration.
     */
    fun newMerge(
        aggregateType: String,
        aggregateId: String,
        payload: Map<String, Any?>,
        expectedRevision: Long = MutationEnvelope.ANY_REVISION,
        id: String = idFactory(),
        occurredAt: Instant = clock.instant(),
    ): MutationEnvelope = MutationEnvelope.create(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedRevision = expectedRevision,
        authorityEpoch = activeAuthorityEpoch(),
        occurredAt = occurredAt,
        payload = payload,
        operation = MutationOperation.MERGE,
    )

    fun newAccumulate(
        aggregateType: String,
        aggregateId: String,
        dimensions: Map<String, Any>,
        increments: Map<String, Long>,
        id: String = idFactory(),
        occurredAt: Instant = clock.instant(),
    ): MutationEnvelope = MutationEnvelope.create(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedRevision = MutationEnvelope.ANY_REVISION,
        authorityEpoch = activeAuthorityEpoch(),
        occurredAt = occurredAt,
        payload = mapOf("dimensions" to dimensions, "increments" to increments),
        operation = MutationOperation.ACCUMULATE,
    )

    fun apply(envelope: MutationEnvelope): MutationReceipt {
        require(envelope.authorityEpoch == activeAuthorityEpoch()) {
            "Mutation authority epoch ${envelope.authorityEpoch.value} does not match " +
                "${writeMode.environmentValue} mode epoch ${activeAuthorityEpoch().value}"
        }

        return when (writeMode) {
            FirestoreWriteMode.SOURCE -> requireNotNull(source).commit(
                envelope = envelope,
                enqueueForMirror = false,
            )
            FirestoreWriteMode.DUAL, FirestoreWriteMode.TARGET -> applyReplicated(envelope)
        }
    }

    fun replayPending(limit: Int = 100): List<MutationReceipt> {
        return replayPendingBatch(limit).mirrored
    }

    fun replayPendingBatch(limit: Int = 100): MutationReplayBatch {
        require(writeMode != FirestoreWriteMode.SOURCE) {
            "Outbox replay is available only while a mirror is configured"
        }
        require(limit in 1..1_000) { "Outbox replay limit must be between 1 and 1000" }

        val (authorityBackend, mirrorBackend) = replicationBackends()
        val pending = authorityBackend.pending(limit)
        val mirrored = pending.mapNotNull { envelope ->
            try {
                val mirrorReceipt = mirrorBackend.commit(envelope, enqueueForMirror = false)
                authorityBackend.markMirrored(envelope.id, mirrorReceipt)
            } catch (failure: Exception) {
                log.warn(
                    "Firestore outbox replay remains pending for mutation {} on {}/{}: {}",
                    envelope.id,
                    envelope.aggregateType,
                    envelope.aggregateId,
                    failure.javaClass.simpleName,
                )
                null
            }
        }
        return MutationReplayBatch(attempted = pending.size, mirrored = mirrored)
    }

    private fun applyReplicated(envelope: MutationEnvelope): MutationReceipt {
        val (authorityBackend, mirrorBackend) = replicationBackends()
        val authorityReceipt = authorityBackend.commit(envelope, enqueueForMirror = true)
        if (authorityReceipt.mirrorState == MirrorState.MIRRORED) {
            return authorityReceipt
        }
        val mirrorEnvelope = envelope.copy(expectedRevision = authorityReceipt.newRevision - 1)

        return try {
            val mirrorReceipt = mirrorBackend.commit(mirrorEnvelope, enqueueForMirror = false)
            authorityBackend.markMirrored(envelope.id, mirrorReceipt)
        } catch (failure: Exception) {
            log.warn(
                "Firestore mirror remains pending for mutation {} on {}/{}: {}",
                envelope.id,
                envelope.aggregateType,
                envelope.aggregateId,
                failure.javaClass.simpleName,
            )
            authorityReceipt.copy(mirrorState = MirrorState.PENDING)
        }
    }

    private fun replicationBackends(): Pair<MutationBackend, MutationBackend> = when (writeMode) {
        FirestoreWriteMode.SOURCE -> error("Source mode does not replicate")
        FirestoreWriteMode.DUAL -> requireNotNull(source) to requireNotNull(target)
        FirestoreWriteMode.TARGET -> requireNotNull(target) to requireNotNull(source)
    }

    private fun activeAuthorityEpoch(): AuthorityEpoch = when (writeMode) {
        FirestoreWriteMode.SOURCE, FirestoreWriteMode.DUAL -> AuthorityEpoch.SOURCE
        FirestoreWriteMode.TARGET -> AuthorityEpoch.TARGET
    }
}

class RevisionConflictException(
    aggregateType: String,
    aggregateId: String,
    expected: Long,
    actual: Long,
) : IllegalStateException(
    "Revision conflict for $aggregateType/$aggregateId: expected $expected, found $actual",
)

class StaleAuthorityEpochException(
    aggregateType: String,
    aggregateId: String,
    incoming: AuthorityEpoch,
    current: AuthorityEpoch,
) : IllegalStateException(
    "Stale authority epoch for $aggregateType/$aggregateId: " +
        "incoming ${incoming.value}, current ${current.value}",
)

class IdempotencyConflictException(mutationId: String) : IllegalStateException(
    "Mutation id $mutationId was already used for a different mutation",
)

internal fun nextMutationRevision(
    envelope: MutationEnvelope,
    currentRevision: Long,
    currentEpoch: AuthorityEpoch?,
): Long {
    require(currentRevision >= 0) { "Current mutation revision cannot be negative" }
    if (currentEpoch != null && currentEpoch > envelope.authorityEpoch) {
        throw StaleAuthorityEpochException(
            aggregateType = envelope.aggregateType,
            aggregateId = envelope.aggregateId,
            incoming = envelope.authorityEpoch,
            current = currentEpoch,
        )
    }
    if (
        envelope.expectedRevision != MutationEnvelope.ANY_REVISION &&
        envelope.expectedRevision != currentRevision
    ) {
        throw RevisionConflictException(
            aggregateType = envelope.aggregateType,
            aggregateId = envelope.aggregateId,
            expected = envelope.expectedRevision,
            actual = currentRevision,
        )
    }
    return currentRevision + 1
}
