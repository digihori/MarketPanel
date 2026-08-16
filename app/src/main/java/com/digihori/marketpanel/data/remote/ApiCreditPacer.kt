package com.digihori.marketpanel.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces Twelve Data requests so the Basic plan's rolling eight-credit/minute
 * allowance is not exhausted when the dashboard loads several cards at once.
 */
class ApiCreditPacer(
    private val millisPerCredit: Long = 8_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun acquire(credits: Int) {
        require(credits > 0)
        mutex.withLock {
            val waitMillis = (nextRequestAt - now()).coerceAtLeast(0L)
            if (waitMillis > 0) wait(waitMillis)
            val startedAt = now().coerceAtLeast(nextRequestAt)
            nextRequestAt = startedAt + credits * millisPerCredit
        }
    }
}
