package com.digihori.marketpanel.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModeScheduleTest {
    @Test fun overnightScheduleCrossesMidnight() {
        assertTrue(isNightModeScheduled(23 * 60, 23 * 60, 6 * 60))
        assertTrue(isNightModeScheduled(5 * 60 + 59, 23 * 60, 6 * 60))
        assertFalse(isNightModeScheduled(12 * 60, 23 * 60, 6 * 60))
    }

    @Test fun daytimeScheduleUsesStartInclusiveEndExclusive() {
        assertTrue(isNightModeScheduled(9 * 60, 9 * 60, 17 * 60))
        assertFalse(isNightModeScheduled(17 * 60, 9 * 60, 17 * 60))
    }

    @Test fun equalTimesDisableSchedule() {
        assertFalse(isNightModeScheduled(0, 8 * 60, 8 * 60))
    }
}
