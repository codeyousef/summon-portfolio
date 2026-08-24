package code.yousef.firestore.migration

import code.yousef.config.FirestoreWriteMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MutationCoordinatorTest {
    private val now = Instant.parse("2026-08-21T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `source mode commits only to source without an outbox entry`() {
        val source = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.SOURCE, source = source)
        val mutation = coordinator.newUpsert(
            aggregateType = "projects",
            aggregateId = "samurai",
            payload = mapOf("title" to "Samurai"),
            expectedRevision = 0,
            id = "mutation-source",
        )

        val receipt = coordinator.apply(mutation)

        assertEquals(AuthorityEpoch.SOURCE, mutation.authorityEpoch)
        assertEquals(1, receipt.newRevision)
        assertEquals(MirrorState.NOT_REQUIRED, receipt.mirrorState)
        assertEquals(1, source.revisionOf("projects", "samurai"))
        assertTrue(source.pending(10).isEmpty())
    }

    @Test
    fun `dual mode commits source first and mirrors idempotently`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.DUAL, source, target)
        val mutation = coordinator.newUpsert(
            aggregateType = "hero",
            aggregateId = "main",
            payload = mapOf("title" to mapOf("en" to "Hello", "ar" to "مرحبا")),
            expectedRevision = 0,
            id = "mutation-dual",
        )

        val firstReceipt = coordinator.apply(mutation)
        val duplicateReceipt = coordinator.apply(mutation)

        assertEquals(MirrorState.MIRRORED, firstReceipt.mirrorState)
        assertEquals(firstReceipt, duplicateReceipt)
        assertEquals(1, source.revisionOf("hero", "main"))
        assertEquals(1, target.revisionOf("hero", "main"))
        assertTrue(source.pending(10).isEmpty())
    }

    @Test
    fun `dual mode leaves a durable pending mutation and replay completes it`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock).apply { failNextCommit = true }
        val coordinator = coordinator(FirestoreWriteMode.DUAL, source, target)
        val mutation = coordinator.newDelete(
            aggregateType = "blog_posts",
            aggregateId = "old-post",
            expectedRevision = 0,
            id = "mutation-pending",
        )

        val pendingReceipt = coordinator.apply(mutation)
        val replayed = coordinator.replayPending()

        assertEquals(MirrorState.PENDING, pendingReceipt.mirrorState)
        assertEquals(1, replayed.size)
        assertEquals(MirrorState.MIRRORED, replayed.single().mirrorState)
        assertTrue(source.pending(10).isEmpty())
        assertEquals(1, target.revisionOf("blog_posts", "old-post"))
    }

    @Test
    fun `target mode advances the authority epoch and reverse mirrors source`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.TARGET, source, target)
        val mutation = coordinator.newUpsert(
            aggregateType = "services",
            aggregateId = "consulting",
            payload = mapOf("order" to 1),
            expectedRevision = 0,
            id = "mutation-target",
        )

        val receipt = coordinator.apply(mutation)

        assertEquals(AuthorityEpoch.TARGET, mutation.authorityEpoch)
        assertEquals(AuthorityEpoch.TARGET, receipt.committedEpoch)
        assertEquals(1, source.revisionOf("services", "consulting"))
        assertEquals(1, target.revisionOf("services", "consulting"))
        assertTrue(target.pending(10).isEmpty())
    }

    @Test
    fun `field merge is preserved through dual mirroring`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.DUAL, source, target)
        val mutation = coordinator.newMerge(
            aggregateType = "ai_curriculum",
            aggregateId = "progress",
            payload = mapOf("lesson-a" to true),
            id = "merge-progress",
        )

        val receipt = coordinator.apply(mutation)

        assertEquals(MutationOperation.MERGE, mutation.operation)
        assertEquals(MirrorState.MIRRORED, receipt.mirrorState)
        assertEquals(1, source.revisionOf("ai_curriculum", "progress"))
        assertEquals(1, target.revisionOf("ai_curriculum", "progress"))
    }

    @Test
    fun `atomic accumulation is replayable with stable dimensions and long deltas`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.DUAL, source, target)
        val mutation = coordinator.newAccumulate(
            aggregateType = "finops_daily_rollups",
            aggregateId = "day:one",
            dimensions = mapOf("dayStart" to 1_800_000_000_000L, "project" to "samurai"),
            increments = mapOf("finalizedSpendUsdMicros" to 125_000L, "entryCount" to 1L),
            id = "rollup-entry-one",
        )

        val receipt = coordinator.apply(mutation)

        assertEquals(MutationOperation.ACCUMULATE, mutation.operation)
        assertEquals(MirrorState.MIRRORED, receipt.mirrorState)
        assertEquals(1, source.revisionOf("finops_daily_rollups", "day:one"))
        assertEquals(1, target.revisionOf("finops_daily_rollups", "day:one"))
    }

    @Test
    fun `replay orders each aggregate by its authoritative revision`() {
        val source = FakeMutationBackend(clock)
        val target = FakeMutationBackend(clock).apply { remainingFailures = 2 }
        val coordinator = coordinator(FirestoreWriteMode.DUAL, source, target)
        coordinator.apply(
            coordinator.newUpsert("projects", "samurai", mapOf("order" to 1), id = "first"),
        )
        coordinator.apply(
            coordinator.newUpsert("projects", "samurai", mapOf("order" to 2), id = "second"),
        )

        source.reversePendingForTest()
        val replayed = coordinator.replayPending()

        assertEquals(listOf("first", "second"), replayed.map(MutationReceipt::id))
        assertEquals(2, target.revisionOf("projects", "samurai"))
        assertTrue(source.pending(10).isEmpty())
    }

    @Test
    fun `mutation IDs are idempotent and cannot be reused for different writes`() {
        val source = FakeMutationBackend(clock)
        val coordinator = coordinator(FirestoreWriteMode.SOURCE, source = source)
        val original = coordinator.newUpsert(
            aggregateType = "projects",
            aggregateId = "portfolio",
            payload = mapOf("featured" to true),
            expectedRevision = 0,
            id = "stable-id",
        )

        val first = coordinator.apply(original)
        val duplicate = coordinator.apply(original)
        val conflicting = coordinator.newUpsert(
            aggregateType = "projects",
            aggregateId = "portfolio",
            payload = mapOf("featured" to false),
            expectedRevision = 1,
            id = "stable-id",
        )

        assertEquals(first, duplicate)
        assertEquals(1, source.revisionOf("projects", "portfolio"))
        assertFailsWith<IdempotencyConflictException> { coordinator.apply(conflicting) }
    }

    @Test
    fun `revision policy rejects conflicts and stale authority epochs`() {
        val sourceMutation = MutationEnvelope.create(
            id = "source-change",
            aggregateType = "projects",
            aggregateId = "portfolio",
            expectedRevision = 4,
            authorityEpoch = AuthorityEpoch.SOURCE,
            occurredAt = now,
            payload = mapOf("order" to 2),
            operation = MutationOperation.UPSERT,
        )

        assertEquals(
            5,
            nextMutationRevision(sourceMutation, currentRevision = 4, currentEpoch = AuthorityEpoch.SOURCE),
        )
        assertFailsWith<RevisionConflictException> {
            nextMutationRevision(sourceMutation, currentRevision = 5, currentEpoch = AuthorityEpoch.SOURCE)
        }
        assertFailsWith<StaleAuthorityEpochException> {
            nextMutationRevision(sourceMutation, currentRevision = 4, currentEpoch = AuthorityEpoch.TARGET)
        }
    }

    @Test
    fun `payload hashing is deterministic across map insertion order`() {
        val first = linkedMapOf<String, Any?>(
            "nested" to linkedMapOf("ar" to "مرحبا", "en" to "Hello"),
            "order" to 1,
        )
        val second = linkedMapOf<String, Any?>(
            "order" to 1L,
            "nested" to linkedMapOf("en" to "Hello", "ar" to "مرحبا"),
        )

        assertEquals(MutationPayloadHash.sha256(first), MutationPayloadHash.sha256(second))
    }

    private fun coordinator(
        mode: FirestoreWriteMode,
        source: FakeMutationBackend? = null,
        target: FakeMutationBackend? = null,
    ) = MutationCoordinator(
        writeMode = mode,
        source = source,
        target = target,
        clock = clock,
        idFactory = { error("Tests provide stable mutation IDs") },
    )

    private class FakeMutationBackend(private val clock: Clock) : MutationBackend {
        private data class State(val revision: Long, val epoch: AuthorityEpoch)
        private data class StoredMutation(
            val envelope: MutationEnvelope,
            val receipt: MutationReceipt,
        )

        private val states = mutableMapOf<Pair<String, String>, State>()
        private val receipts = mutableMapOf<String, StoredMutation>()
        private val outbox = linkedMapOf<String, MutationEnvelope>()
        var remainingFailures: Int = 0
        var failNextCommit: Boolean
            get() = remainingFailures > 0
            set(value) {
                remainingFailures = if (value) 1 else 0
            }

        override fun commit(
            envelope: MutationEnvelope,
            enqueueForMirror: Boolean,
        ): MutationReceipt {
            if (remainingFailures > 0) {
                remainingFailures -= 1
                throw IllegalStateException("simulated target outage")
            }
            receipts[envelope.id]?.let { stored ->
                if (stored.envelope != envelope) {
                    throw IdempotencyConflictException(envelope.id)
                }
                return stored.receipt
            }

            val key = envelope.aggregateType to envelope.aggregateId
            val current = states[key]
            val revision = nextMutationRevision(
                envelope = envelope,
                currentRevision = current?.revision ?: 0,
                currentEpoch = current?.epoch,
            )
            states[key] = State(revision, envelope.authorityEpoch)
            val receipt = MutationReceipt(
                id = envelope.id,
                committedEpoch = envelope.authorityEpoch,
                newRevision = revision,
                firestoreCommitTime = clock.instant(),
                mirrorState = if (enqueueForMirror) MirrorState.PENDING else MirrorState.NOT_REQUIRED,
            )
            receipts[envelope.id] = StoredMutation(envelope, receipt)
            if (enqueueForMirror) {
                outbox[envelope.id] = envelope.copy(expectedRevision = revision - 1)
            }
            return receipt
        }

        override fun pending(limit: Int): List<MutationEnvelope> = outbox.values
            .sortedWith(compareBy(MutationEnvelope::aggregateType, MutationEnvelope::aggregateId, MutationEnvelope::expectedRevision))
            .take(limit)

        override fun markMirrored(
            mutationId: String,
            targetReceipt: MutationReceipt,
        ): MutationReceipt {
            require(targetReceipt.id == mutationId)
            val stored = requireNotNull(receipts[mutationId])
            val receipt = stored.receipt.copy(mirrorState = MirrorState.MIRRORED)
            receipts[mutationId] = stored.copy(receipt = receipt)
            outbox.remove(mutationId)
            return receipt
        }

        fun revisionOf(aggregateType: String, aggregateId: String): Long =
            states[aggregateType to aggregateId]?.revision ?: 0

        fun reversePendingForTest() {
            val reversed = outbox.entries.reversed()
            outbox.clear()
            reversed.forEach { (id, envelope) -> outbox[id] = envelope }
        }
    }
}
