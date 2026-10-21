package code.yousef.aws.jobs

import code.yousef.aws.DynamoDbLocal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JobLeaseStoreTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `DynamoDB leases follow the Durable Object idempotency contract`() {
        val client = DynamoDbLocal.client()
        val clock = MutableClock(Instant.parse("2026-10-21T05:01:00Z"))
        assertLeaseContract(DynamoDbJobLeaseStore(client, DynamoDbLocal.createSessionsTable(client), clock), clock)
    }

    @Test
    fun `in-memory leases follow the same contract`() {
        val clock = MutableClock(Instant.parse("2026-10-21T05:01:00Z"))
        assertLeaseContract(InMemoryJobLeaseStore(clock), clock)
    }

    private fun assertLeaseContract(store: JobLeaseStore, clock: MutableClock) {
        val key = "finops.gcp-billing:2026-10-21T05:01:00Z"
        val fingerprint = "a".repeat(64)
        val claim = assertIs<JobClaim.Claimed>(store.claim(key, fingerprint))
        assertIs<JobClaim.Leased>(store.claim(key, fingerprint))
        assertEquals(JobClaim.KeyReused, store.claim(key, "b".repeat(64)))
        assertTrue(store.extend(key, fingerprint, claim.claimToken))
        assertFalse(store.complete(key, fingerprint, "wrong-token"))

        // An expired lease (crashed worker) can be reclaimed for the same work only.
        clock.now = clock.now.plusSeconds(301)
        val reclaimed = assertIs<JobClaim.Claimed>(store.claim(key, fingerprint))
        assertFalse(store.complete(key, fingerprint, claim.claimToken), "stale claim cannot complete")
        assertTrue(store.complete(key, fingerprint, reclaimed.claimToken))
        assertEquals(JobClaim.Complete, store.claim(key, fingerprint))
        assertFalse(store.abandon(key, fingerprint, reclaimed.claimToken))

        val other = "async.finops.ingest:id-1"
        val first = assertIs<JobClaim.Claimed>(store.claim(other, fingerprint))
        assertTrue(store.abandon(other, fingerprint, first.claimToken))
        assertIs<JobClaim.Claimed>(store.claim(other, fingerprint))
    }
}
