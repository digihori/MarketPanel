package com.digihori.marketpanel.data.remote

import android.content.Context
import retrofit2.HttpException
import java.io.IOException
import java.util.Calendar

class ApiRetryPolicy(
    context: Context,
    private val log: ApiCreditLog,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun requireAllowed(key: String, path: String) {
        val state = read(key)
        if (state.blockedUntil <= now()) return
        log.append(ApiCreditLogEntry(now(), "SKIP", path, 0, "POLICY-BLOCK", 0))
        throw ApiRequestSuppressedException(state.blockedUntil)
    }

    @Synchronized
    fun recordSuccess(key: String) {
        preferences.edit().remove(key).apply()
    }

    @Synchronized
    fun recordFailure(key: String, error: Throwable) {
        val status = (error as? HttpException)?.code()
        val old = read(key)
        val failures = old.consecutiveFailures + 1
        val blockedUntil = when {
            status == 400 || status == 404 -> nextLocalMidnight(now())
            failures >= MAX_CONSECUTIVE_FAILURES -> now() + SIX_HOURS_MILLIS
            else -> 0L
        }
        preferences.edit().putString(key, "$failures|$blockedUntil").apply()
    }

    private fun read(key: String): FailureState {
        val parts = preferences.getString(key, null)?.split('|').orEmpty()
        return FailureState(
            consecutiveFailures = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            blockedUntil = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
        )
    }

    private fun nextLocalMidnight(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private data class FailureState(val consecutiveFailures: Int, val blockedUntil: Long)

    private companion object {
        const val PREFERENCES_NAME = "api_retry_policy"
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val SIX_HOURS_MILLIS = 6 * 60 * 60 * 1_000L
    }
}

class ApiRequestSuppressedException(val retryAfterEpochMillis: Long) :
    IOException("API request suppressed until $retryAfterEpochMillis")
