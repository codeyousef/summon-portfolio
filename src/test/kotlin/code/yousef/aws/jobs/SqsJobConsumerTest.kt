package code.yousef.aws.jobs

import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqsJobConsumerTest {
    private class FakeSqs : SqsClient {
        val inbox = ConcurrentLinkedQueue<Message>()
        val deleted = CopyOnWriteArrayList<String>()
        val visibility = CopyOnWriteArrayList<Pair<String, Int>>()
        override fun serviceName() = "sqs"
        override fun close() = Unit
        override fun receiveMessage(request: ReceiveMessageRequest): ReceiveMessageResponse {
            val message = inbox.poll()
            if (message == null) Thread.sleep(20)
            return ReceiveMessageResponse.builder().messages(listOfNotNull(message)).build()
        }
        override fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse {
            deleted += request.receiptHandle()
            return DeleteMessageResponse.builder().build()
        }
        override fun changeMessageVisibility(request: ChangeMessageVisibilityRequest): ChangeMessageVisibilityResponse {
            visibility += request.receiptHandle() to request.visibilityTimeout()
            return ChangeMessageVisibilityResponse.builder().build()
        }
    }

    private fun message(handle: String, body: String, receives: Int = 1) = Message.builder()
        .messageId("id-$handle")
        .receiptHandle(handle)
        .body(body)
        .attributes(mapOf(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT to receives.toString()))
        .build()

    private val scheduled = """{"job":"finops.stripe","scheduledAt":"2026-10-21T05:21:00Z"}"""

    @Test
    fun `successful jobs complete their lease and delete the message`() = runBlocking {
        val sqs = FakeSqs()
        val leases = InMemoryJobLeaseStore()
        val ran = CopyOnWriteArrayList<String>()
        val consumer = SqsJobConsumer(sqs, QUEUE, leases, { ran += it.idempotencyKey })
        consumer.process(message("h1", scheduled))
        // A duplicate delivery (scheduler retry / at-least-once SQS) is acknowledged without rerunning.
        consumer.process(message("h2", scheduled))
        assertEquals(listOf("finops.stripe:2026-10-21T05:21:00Z"), ran)
        assertEquals(listOf("h1", "h2"), sqs.deleted)
    }

    @Test
    fun `failures release the lease and back off without deleting`() = runBlocking {
        val sqs = FakeSqs()
        val leases = InMemoryJobLeaseStore()
        var attempts = 0
        val consumer = SqsJobConsumer(sqs, QUEUE, leases, {
            attempts++
            if (attempts == 1) error("provider down")
        })
        consumer.process(message("h1", scheduled, receives = 3))
        assertTrue(sqs.deleted.isEmpty())
        assertEquals("h1" to 80, sqs.visibility.single())
        consumer.process(message("h2", scheduled, receives = 4))
        assertEquals(listOf("h2"), sqs.deleted)
        assertEquals(2, attempts)
    }

    @Test
    fun `malformed messages and reused keys are left for the dead-letter queue`() = runBlocking {
        val sqs = FakeSqs()
        val leases = InMemoryJobLeaseStore()
        val consumer = SqsJobConsumer(sqs, QUEUE, leases, { })
        consumer.process(message("bad", """{"job":"unknown"}"""))
        consumer.process(message("a", """{"job":"async.finops.ingest","id":"x1","payload":{"a":1}}"""))
        consumer.process(message("b", """{"job":"async.finops.ingest","id":"x1","payload":{"a":2}}"""))
        assertEquals(listOf("a"), sqs.deleted)
    }

    @Test
    fun `a job leased elsewhere is deferred until its lease expires`() = runBlocking {
        val sqs = FakeSqs()
        val leases = InMemoryJobLeaseStore()
        val parsed = PortfolioJobMessage.parse(scheduled)
        leases.claim(parsed.idempotencyKey, parsed.fingerprint)
        SqsJobConsumer(sqs, QUEUE, leases, { error("must not run") }).process(message("h1", scheduled))
        assertTrue(sqs.deleted.isEmpty())
        assertTrue(sqs.visibility.single().second in 290..300)
    }

    @Test
    fun `stop waits for the in-flight job and then the loop exits`() = runBlocking {
        val sqs = FakeSqs()
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val consumer = SqsJobConsumer(sqs, QUEUE, InMemoryJobLeaseStore(), {
            started.countDown()
            Thread.sleep(300)
            finished.countDown()
        }, shutdownGraceMillis = 5_000)
        sqs.inbox += message("h1", scheduled)
        consumer.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        consumer.stop()
        assertTrue(finished.await(0, TimeUnit.SECONDS))
        assertEquals(listOf("h1"), sqs.deleted)
    }

    @Test
    fun `job messages are strictly validated`() {
        val parsed = PortfolioJobMessage.parse(scheduled)
        assertEquals("finops.stripe", parsed.job)
        assertEquals(64, parsed.fingerprint.length)
        val async = PortfolioJobMessage.parse("""{"job":"async.finops.import-run","id":"run:1","payload":{}}""")
        assertEquals("async.finops.import-run:run:1", async.idempotencyKey)
        listOf(
            """{"job":"finops.stripe"}""",
            """{"job":"finops.stripe","scheduledAt":"yesterday"}""",
            """{"job":"finops.stripe","scheduledAt":"2026-10-21T05:21:00Z","extra":1}""",
            """{"job":"async.finops.ingest","id":"bad id","payload":{}}""",
            """{"job":"async.finops.ingest","id":"x","payload":[]}""",
            """[]""",
        ).forEach { body -> assertFailsWith<IllegalArgumentException>(body) { PortfolioJobMessage.parse(body) } }
    }

    private companion object {
        const val QUEUE = "https://sqs.eu-north-1.amazonaws.com/123456789012/portfolio-jobs"
    }
}
