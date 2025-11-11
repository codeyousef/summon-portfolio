package code.yousef.portfolio.ssr

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SummonRenderLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
