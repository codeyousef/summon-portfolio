package code.yousef.firestore

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import kotlin.math.min

suspend fun <T> retry(
    attempts: Int = 3,
    baseDelayMs: Long = 100,
    maxDelayMs: Long = 1_000,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    var delayMs = baseDelayMs
    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            lastError = t
            val status = (t as? StatusRuntimeException)?.status?.code
            val transient = status == Status.Code.UNAVAILABLE ||
                status == Status.Code.DEADLINE_EXCEEDED ||
                status == Status.Code.ABORTED
            if (!transient || attempt == attempts - 1) throw t
            delay(delayMs)
            delayMs = min(maxDelayMs, delayMs * 2)
        }
    }
    throw lastError ?: IllegalStateException("retry failed without throwable")
}
