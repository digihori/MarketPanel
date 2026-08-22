package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.settings.DataRefreshMode
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRefreshPolicyTest {
    @Test
    fun usCloseBecomesDueAtEightJapanTime() {
        val fetched = jst(2026, 8, 19, 8, 5)

        assertTrue(MarketRefreshPolicy.usStockFresh(fetched, jst(2026, 8, 20, 7, 59), DataRefreshMode.CLOSE_ONLY))
        assertFalse(MarketRefreshPolicy.usStockFresh(fetched, jst(2026, 8, 20, 8, 0), DataRefreshMode.CLOSE_ONLY))
    }

    @Test
    fun fundBecomesDueAtEightTwenty() {
        val fetched = jst(2026, 8, 19, 8, 30)

        assertTrue(MarketRefreshPolicy.fundFresh(fetched, jst(2026, 8, 20, 8, 19), DataRefreshMode.CLOSE_ONLY))
        assertFalse(MarketRefreshPolicy.fundFresh(fetched, jst(2026, 8, 20, 8, 20), DataRefreshMode.CLOSE_ONLY))
    }

    @Test
    fun japanCloseModeRefreshesAtEightAndFifteenFortyFive() {
        val morning = jst(2026, 8, 20, 8, 2)

        assertTrue(MarketRefreshPolicy.japanFresh(morning, jst(2026, 8, 20, 15, 44), DataRefreshMode.CLOSE_ONLY))
        assertFalse(MarketRefreshPolicy.japanFresh(morning, jst(2026, 8, 20, 15, 45), DataRefreshMode.CLOSE_ONLY))
    }

    @Test
    fun usdJpyUsesFourHourFreshness() {
        val fetched = jst(2026, 8, 20, 8, 0)

        assertTrue(MarketRefreshPolicy.marketFresh("USDJPY", fetched, jst(2026, 8, 20, 11, 59), DataRefreshMode.CLOSE_ONLY))
        assertFalse(MarketRefreshPolicy.marketFresh("USDJPY", fetched, jst(2026, 8, 20, 12, 0), DataRefreshMode.CLOSE_ONLY))
    }

    private fun jst(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis
}
