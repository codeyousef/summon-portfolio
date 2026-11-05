package code.yousef.firestore.migration

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import java.time.Clock
import java.time.Instant

class FirestoreMutationBackend(
    private val firestore: Firestore,
    private val clock: Clock = Clock.systemUTC(),
) : MutationBackend {
    override fun commit(
        envelope: MutationEnvelope,
        enqueueForMirror: Boolean,
    ): MutationReceipt {
        val receiptRef = firestore.collection(RECEIPTS_COLLECTION).document(envelope.id)
        val stateRef = firestore.collection(AGGREGATE_STATE_COLLECTION).document(aggregateStateId(envelope))
        val aggregateRef = firestore.collection(envelope.aggregateType).document(envelope.aggregateId)
        val outboxRef = firestore.collection(OUTBOX_COLLECTION).document(envelope.id)
        val recordedCommitTime = clock.instant()
        val fingerprint = envelopeFingerprint(envelope)

        return firestore.runTransaction { transaction ->
            val existingReceipt = transaction.get(receiptRef).get()
            if (existingReceipt.exists()) {
                if (existingReceipt.getString(FINGERPRINT_FIELD) != fingerprint) {
                    throw IdempotencyConflictException(envelope.id)
                }
                return@runTransaction existingReceipt.toMutationReceipt()
            }

            val state = transaction.get(stateRef).get()
            val aggregate = transaction.get(aggregateRef).get()
            // A document may predate revision tracking. Treat it as revision one so an
            // expected-revision-zero immutable create cannot overwrite legacy data.
            val currentRevision = state.getLong(REVISION_FIELD) ?: if (aggregate.exists()) 1L else 0L
            val currentEpochValue = state.getLong(AUTHORITY_EPOCH_FIELD) ?: 0L
            val newRevision = nextMutationRevision(
                envelope = envelope,
                currentRevision = currentRevision,
                currentEpoch = currentEpochValue.takeIf { it > 0 }?.let(::AuthorityEpoch),
            )
            when (envelope.operation) {
                MutationOperation.UPSERT -> transaction.set(aggregateRef, envelope.payload.toFirestoreMap())
                MutationOperation.MERGE -> transaction.set(
                    aggregateRef,
                    envelope.payload.toFirestoreMap(),
                    SetOptions.merge(),
                )
                MutationOperation.ACCUMULATE -> transaction.set(
                    aggregateRef,
                    accumulateDocument(aggregate, envelope),
                    SetOptions.merge(),
                )
                MutationOperation.DELETE -> transaction.delete(aggregateRef)
            }
            transaction.set(
                stateRef,
                mapOf(
                    AGGREGATE_TYPE_FIELD to envelope.aggregateType,
                    AGGREGATE_ID_FIELD to envelope.aggregateId,
                    REVISION_FIELD to newRevision,
                    AUTHORITY_EPOCH_FIELD to envelope.authorityEpoch.value,
                    LAST_MUTATION_ID_FIELD to envelope.id,
                    UPDATED_AT_FIELD to recordedCommitTime.toString(),
                ),
            )

            val mirrorState = if (enqueueForMirror) MirrorState.PENDING else MirrorState.NOT_REQUIRED
            val receipt = MutationReceipt(
                id = envelope.id,
                committedEpoch = envelope.authorityEpoch,
                newRevision = newRevision,
                firestoreCommitTime = recordedCommitTime,
                mirrorState = mirrorState,
            )
            transaction.set(receiptRef, receipt.toFirestoreMap(fingerprint))
            if (enqueueForMirror) {
                val mirrorEnvelope = envelope.copy(expectedRevision = newRevision - 1)
                transaction.set(outboxRef, mirrorEnvelope.toFirestoreMap(mirrorState))
            }
            receipt
        }.get()
    }

    override fun pending(limit: Int): List<MutationEnvelope> {
        require(limit in 1..1_000) { "Outbox query limit must be between 1 and 1000" }
        return firestore.collection(OUTBOX_COLLECTION)
            .whereEqualTo(MIRROR_STATE_FIELD, MirrorState.PENDING.name)
            .orderBy(AGGREGATE_TYPE_FIELD, Query.Direction.ASCENDING)
            .orderBy(AGGREGATE_ID_FIELD, Query.Direction.ASCENDING)
            .orderBy(EXPECTED_REVISION_FIELD, Query.Direction.ASCENDING)
            .limit(limit)
            .get()
            .get()
            .documents
            .map { it.toMutationEnvelope() }
    }

    override fun markMirrored(
        mutationId: String,
        targetReceipt: MutationReceipt,
    ): MutationReceipt {
        val receiptRef = firestore.collection(RECEIPTS_COLLECTION).document(mutationId)
        val outboxRef = firestore.collection(OUTBOX_COLLECTION).document(mutationId)
        val mirroredAt = clock.instant()

        return firestore.runTransaction { transaction ->
            val receiptDocument = transaction.get(receiptRef).get()
            require(receiptDocument.exists()) { "Missing source receipt for mutation $mutationId" }
            val currentReceipt = receiptDocument.toMutationReceipt()
            if (currentReceipt.mirrorState == MirrorState.MIRRORED) {
                return@runTransaction currentReceipt
            }

            val outboxDocument = transaction.get(outboxRef).get()
            require(outboxDocument.exists()) { "Missing source outbox entry for mutation $mutationId" }
            require(targetReceipt.id == mutationId) { "Target receipt does not match mutation $mutationId" }

            val mirroredReceipt = currentReceipt.copy(mirrorState = MirrorState.MIRRORED)
            transaction.update(
                receiptRef,
                mapOf(MIRROR_STATE_FIELD to MirrorState.MIRRORED.name),
            )
            transaction.update(
                outboxRef,
                mapOf(
                    MIRROR_STATE_FIELD to MirrorState.MIRRORED.name,
                    MIRRORED_AT_FIELD to mirroredAt.toString(),
                    TARGET_REVISION_FIELD to targetReceipt.newRevision,
                ),
            )
            mirroredReceipt
        }.get()
    }

    private fun DocumentSnapshot.toMutationReceipt(): MutationReceipt = MutationReceipt(
        id = id,
        committedEpoch = AuthorityEpoch(requireNotNull(getLong(COMMITTED_EPOCH_FIELD))),
        newRevision = requireNotNull(getLong(NEW_REVISION_FIELD)),
        firestoreCommitTime = Instant.parse(requireNotNull(getString(FIRESTORE_COMMIT_TIME_FIELD))),
        mirrorState = MirrorState.valueOf(requireNotNull(getString(MIRROR_STATE_FIELD))),
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toMutationEnvelope(): MutationEnvelope = MutationEnvelope(
        id = id,
        aggregateType = requireNotNull(getString(AGGREGATE_TYPE_FIELD)),
        aggregateId = requireNotNull(getString(AGGREGATE_ID_FIELD)),
        expectedRevision = requireNotNull(getLong(EXPECTED_REVISION_FIELD)),
        authorityEpoch = AuthorityEpoch(requireNotNull(getLong(AUTHORITY_EPOCH_FIELD))),
        occurredAt = Instant.parse(requireNotNull(getString(OCCURRED_AT_FIELD))),
        payloadSha256 = requireNotNull(getString(PAYLOAD_SHA_256_FIELD)),
        payload = get(PAYLOAD_FIELD) as? Map<String, Any?>
            ?: error("Outbox mutation $id has an invalid payload"),
        operation = MutationOperation.valueOf(requireNotNull(getString(OPERATION_FIELD))),
    )

    private fun MutationReceipt.toFirestoreMap(fingerprint: String): Map<String, Any> = mapOf(
        COMMITTED_EPOCH_FIELD to committedEpoch.value,
        NEW_REVISION_FIELD to newRevision,
        FIRESTORE_COMMIT_TIME_FIELD to firestoreCommitTime.toString(),
        MIRROR_STATE_FIELD to mirrorState.name,
        FINGERPRINT_FIELD to fingerprint,
    )

    private fun MutationEnvelope.toFirestoreMap(mirrorState: MirrorState): Map<String, Any> = mapOf(
        AGGREGATE_TYPE_FIELD to aggregateType,
        AGGREGATE_ID_FIELD to aggregateId,
        EXPECTED_REVISION_FIELD to expectedRevision,
        AUTHORITY_EPOCH_FIELD to authorityEpoch.value,
        OCCURRED_AT_FIELD to occurredAt.toString(),
        PAYLOAD_SHA_256_FIELD to payloadSha256,
        PAYLOAD_FIELD to payload,
        OPERATION_FIELD to operation.name,
        MIRROR_STATE_FIELD to mirrorState.name,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toFirestoreMap(): Map<String, Any> = this as Map<String, Any>

    private fun aggregateStateId(envelope: MutationEnvelope): String = MutationPayloadHash.sha256(
        mapOf(
            AGGREGATE_TYPE_FIELD to envelope.aggregateType,
            AGGREGATE_ID_FIELD to envelope.aggregateId,
        ),
    )

    private fun envelopeFingerprint(envelope: MutationEnvelope): String = MutationPayloadHash.sha256(
        mapOf(
            "id" to envelope.id,
            AGGREGATE_TYPE_FIELD to envelope.aggregateType,
            AGGREGATE_ID_FIELD to envelope.aggregateId,
            EXPECTED_REVISION_FIELD to envelope.expectedRevision,
            AUTHORITY_EPOCH_FIELD to envelope.authorityEpoch.value,
            OCCURRED_AT_FIELD to envelope.occurredAt.toString(),
            PAYLOAD_SHA_256_FIELD to envelope.payloadSha256,
            OPERATION_FIELD to envelope.operation.name,
        ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun accumulateDocument(snapshot: DocumentSnapshot, envelope: MutationEnvelope): Map<String, Any> {
        val dimensions = envelope.payload.getValue("dimensions") as Map<String, Any>
        val increments = envelope.payload.getValue("increments") as Map<String, Long>
        val current = snapshot.data.orEmpty()
        dimensions.forEach { (field, expected) ->
            val existing = current[field]
            require(existing == null || existing == expected) { "Accumulate dimension $field cannot change" }
        }
        return buildMap {
            putAll(dimensions)
            increments.forEach { (field, delta) ->
                val before = (current[field] as? Number)?.toLong() ?: 0L
                put(field, Math.addExact(before, delta))
            }
        }
    }

    companion object {
        const val RECEIPTS_COLLECTION = "_migration_receipts"
        const val OUTBOX_COLLECTION = "_migration_outbox"
        const val AGGREGATE_STATE_COLLECTION = "_migration_aggregate_state"

        private const val AGGREGATE_TYPE_FIELD = "aggregateType"
        private const val AGGREGATE_ID_FIELD = "aggregateId"
        private const val EXPECTED_REVISION_FIELD = "expectedRevision"
        private const val AUTHORITY_EPOCH_FIELD = "authorityEpoch"
        private const val OCCURRED_AT_FIELD = "occurredAt"
        private const val PAYLOAD_SHA_256_FIELD = "payloadSha256"
        private const val PAYLOAD_FIELD = "payload"
        private const val OPERATION_FIELD = "operation"
        private const val MIRROR_STATE_FIELD = "mirrorState"
        private const val FINGERPRINT_FIELD = "fingerprint"
        private const val REVISION_FIELD = "revision"
        private const val LAST_MUTATION_ID_FIELD = "lastMutationId"
        private const val UPDATED_AT_FIELD = "updatedAt"
        private const val COMMITTED_EPOCH_FIELD = "committedEpoch"
        private const val NEW_REVISION_FIELD = "newRevision"
        private const val FIRESTORE_COMMIT_TIME_FIELD = "firestoreCommitTime"
        private const val MIRRORED_AT_FIELD = "mirroredAt"
        private const val TARGET_REVISION_FIELD = "targetRevision"
    }
}
