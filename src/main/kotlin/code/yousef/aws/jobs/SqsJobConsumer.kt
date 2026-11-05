package code.yousef.aws.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-polling consumer for `PORTFOLIO_SQS_JOBS_QUEUE_URL`.
 *
 * - One message at a time per task; jobs run on the IO dispatcher and are interruptible.
 * - While a job runs, a heartbeat extends both the SQS visibility timeout and the
 *   DynamoDB idempotency lease every third of [visibilityTimeoutSeconds].
 * - Success completes the lease and deletes the message. Failure abandons the lease and
 *   backs the message off exponentially; the queue's redrive policy (owned by infra) moves
 *   it to the dead-letter queue after `maxReceiveCount` receives. Malformed messages and
 *   reused idempotency keys are never deleted, so they also end in the DLQ.
 * - [stop] stops polling, waits up to [shutdownGraceMillis] for the in-flight job, then
 *   interrupts it, abandons its lease and makes the message visible again immediately.
 */
class SqsJobConsumer(
    private val sqs: SqsClient,
    private val queueUrl: String,
    private val leases: JobLeaseStore,
    private val runner: (PortfolioJobMessage) -> Unit,
    private val visibilityTimeoutSeconds: Int = 300,
    private val shutdownGraceMillis: Long = 25_000,
) {
    private val log = LoggerFactory.getLogger(SqsJobConsumer::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private var loop: Job? = null

    init {
        require(queueUrl.startsWith("https://") || queueUrl.startsWith("http://localhost")) { "Invalid SQS queue URL" }
        require(visibilityTimeoutSeconds in 30..43_200) { "SQS visibility timeout must be between 30 and 43200 seconds" }
    }

    fun start() {
        check(loop == null) { "SQS job consumer already started" }
        loop = scope.launch {
            log.info("SQS job consumer started")
            while (isActive && !stopping.get()) {
                val messages = try {
                    runInterruptible {
                        sqs.receiveMessage { request ->
                            request.queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(20)
                                .visibilityTimeout(visibilityTimeoutSeconds)
                                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                        }.messages()
                    }
                } catch (failure: Exception) {
                    if (stopping.get() || !isActive) break
                    log.warn("SQS receive failed: {}", failure.javaClass.simpleName)
                    delay(5_000)
                    continue
                }
                for (message in messages) process(message)
            }
            log.info("SQS job consumer stopped")
        }
    }

    suspend fun stop() {
        stopping.set(true)
        val running = loop ?: return
        if (withTimeoutOrNull(shutdownGraceMillis) { running.join() } == null) {
            log.warn("SQS job consumer did not finish within the shutdown grace period; interrupting")
            running.cancelAndJoin()
        }
        scope.cancel()
    }

    internal suspend fun process(message: Message) {
        val parsed = try {
            PortfolioJobMessage.parse(message.body())
        } catch (failure: Exception) {
            // Left undeleted: the redrive policy dead-letters it after maxReceiveCount.
            log.error("Rejected malformed job message {}: {}", message.messageId(), failure.message?.take(200))
            return
        }
        when (val claim = leases.claim(parsed.idempotencyKey, parsed.fingerprint)) {
            JobClaim.Complete -> {
                log.info("Job {} already completed; deleting duplicate message", parsed.job)
                delete(message)
            }
            JobClaim.KeyReused -> log.error("Job idempotency key reused with a different message for {}", parsed.job)
            is JobClaim.Leased -> changeVisibility(message, (claim.retryAfterMillis / 1_000).coerceIn(10, 43_200).toInt())
            is JobClaim.Claimed -> runClaimed(message, parsed, claim.claimToken)
        }
    }

    private suspend fun runClaimed(message: Message, parsed: PortfolioJobMessage, claimToken: String) {
        val key = parsed.idempotencyKey
        val heartbeat = scope.launch {
            while (isActive) {
                delay(visibilityTimeoutSeconds * 1_000L / 3)
                runCatching { changeVisibility(message, visibilityTimeoutSeconds) }
                    .onFailure { log.warn("SQS visibility extension failed: {}", it.javaClass.simpleName) }
                if (!runCatching { leases.extend(key, parsed.fingerprint, claimToken) }.getOrDefault(false)) {
                    log.warn("Job lease extension failed for {}", parsed.job)
                }
            }
        }
        var succeeded = false
        try {
            val started = System.nanoTime()
            runInterruptible(Dispatchers.IO) { runner(parsed) }
            succeeded = true
            log.info("Job {} completed in {} ms", parsed.job, (System.nanoTime() - started) / 1_000_000)
        } catch (failure: Throwable) {
            log.error("Job {} failed: {}", parsed.job, failure.javaClass.simpleName, failure)
            if (failure is kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) { release(message, parsed, claimToken, visibilitySeconds = 0) }
                throw failure
            }
        } finally {
            heartbeat.cancel()
        }
        withContext(NonCancellable) {
            if (succeeded) {
                if (!leases.complete(key, parsed.fingerprint, claimToken)) {
                    log.warn("Job lease for {} expired before completion; the job is idempotent", parsed.job)
                }
                delete(message)
            } else {
                val receives = message.attributes()[MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT]?.toIntOrNull() ?: 1
                release(message, parsed, claimToken, visibilitySeconds = minOf(300, 10 shl minOf(receives, 5)))
            }
        }
    }

    private fun release(message: Message, parsed: PortfolioJobMessage, claimToken: String, visibilitySeconds: Int) {
        runCatching { leases.abandon(parsed.idempotencyKey, parsed.fingerprint, claimToken) }
        runCatching { changeVisibility(message, visibilitySeconds) }
    }

    private fun delete(message: Message) {
        sqs.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
    }

    private fun changeVisibility(message: Message, seconds: Int) {
        sqs.changeMessageVisibility { request ->
            request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()).visibilityTimeout(seconds)
        }
    }
}
