package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.settings.DataRefreshMode
import java.util.Calendar
import java.util.TimeZone

internal object MarketRefreshPolicy {
    private const val FOUR_HOURS = 4 * 60 * 60 * 1_000L
    private val JST = TimeZone.getTimeZone("Asia/Tokyo")
    private val US_CLOSE_CHECK = listOf(8 * 60)
    private val FUND_CHECK = listOf(8 * 60 + 20)
    private val JAPAN_CLOSE_CHECKS = listOf(8 * 60, 15 * 60 + 45)
    private val JAPAN_INTRADAY_CHECKS = listOf(
        8 * 60,
        9 * 60 + 5,
        10 * 60 + 5,
        11 * 60 + 35,
        12 * 60 + 35,
        13 * 60 + 35,
        14 * 60 + 35,
        15 * 60 + 45,
    )

    fun usStockFresh(fetchedAt: Long, now: Long, mode: DataRefreshMode): Boolean = when (mode) {
        DataRefreshMode.DEBUG -> false
        DataRefreshMode.FOUR_HOURS -> within(fetchedAt, now, FOUR_HOURS)
        DataRefreshMode.CLOSE_ONLY, DataRefreshMode.JAPAN_INTRADAY -> afterLatestBoundary(fetchedAt, now, US_CLOSE_CHECK)
    }

    fun fundFresh(fetchedAt: Long, now: Long, mode: DataRefreshMode): Boolean = when (mode) {
        DataRefreshMode.DEBUG -> false
        DataRefreshMode.FOUR_HOURS -> within(fetchedAt, now, FOUR_HOURS)
        DataRefreshMode.CLOSE_ONLY, DataRefreshMode.JAPAN_INTRADAY -> afterLatestBoundary(fetchedAt, now, FUND_CHECK)
    }

    fun japanFresh(fetchedAt: Long, now: Long, mode: DataRefreshMode): Boolean = when (mode) {
        DataRefreshMode.DEBUG -> false
        DataRefreshMode.FOUR_HOURS -> within(fetchedAt, now, FOUR_HOURS)
        DataRefreshMode.CLOSE_ONLY -> afterLatestBoundary(fetchedAt, now, JAPAN_CLOSE_CHECKS)
        DataRefreshMode.JAPAN_INTRADAY -> afterLatestBoundary(fetchedAt, now, JAPAN_INTRADAY_CHECKS)
    }

    fun marketFresh(id: String, fetchedAt: Long, now: Long, mode: DataRefreshMode): Boolean = when {
        mode == DataRefreshMode.DEBUG -> false
        mode == DataRefreshMode.FOUR_HOURS || id == "USDJPY" -> within(fetchedAt, now, FOUR_HOURS)
        id == "NIKKEI225" -> japanFresh(fetchedAt, now, mode)
        else -> afterLatestBoundary(fetchedAt, now, US_CLOSE_CHECK)
    }

    private fun within(fetchedAt: Long, now: Long, lifetime: Long): Boolean =
        fetchedAt > 0L && now >= fetchedAt && now - fetchedAt < lifetime

    private fun afterLatestBoundary(fetchedAt: Long, now: Long, boundaryMinutes: List<Int>): Boolean =
        fetchedAt >= latestBoundary(now, boundaryMinutes)

    private fun latestBoundary(now: Long, boundaryMinutes: List<Int>): Long {
        val current = Calendar.getInstance(JST).apply { timeInMillis = now }
        val minuteOfDay = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE)
        val selected = boundaryMinutes.lastOrNull { it <= minuteOfDay }
        val boundary = Calendar.getInstance(JST).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (selected == null) add(Calendar.DAY_OF_YEAR, -1)
        }
        val minutes = selected ?: boundaryMinutes.last()
        boundary.add(Calendar.MINUTE, minutes)
        return boundary.timeInMillis
    }
}
