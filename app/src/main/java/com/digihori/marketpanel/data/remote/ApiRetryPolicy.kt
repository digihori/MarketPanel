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
            status == 400 || status == 404 || status == 429 -> nextLocalMidnight(now())
            failures == 1 -> now() + FIFTEEN_MINUTES_MILLIS
            failures == 2 -> now() + ONE_HOUR_MILLIS
            else -> nextLocalMidnight(now())
        }
        preferences.edit().putString(key, "$failures|$blockedUntil").apply()
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().apply()
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
        const val FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1_000L
        const val ONE_HOUR_MILLIS = 60 * 60 * 1_000L
    }
}

class ApiRequestSuppressedException(val retryAfterEpochMillis: Long) :
    IOException("API request suppressed until $retryAfterEpochMillis")
