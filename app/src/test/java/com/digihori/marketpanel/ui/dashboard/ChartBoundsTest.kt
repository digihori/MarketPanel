package com.digihori.marketpanel.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartBoundsTest {
    @Test
    fun addsTwentyPercentOfDataRangeAboveAndBelow() {
        val bounds = calculateChartBounds(listOf(100f, 150f, 200f))!!

        assertEquals(80f, bounds.minimum, 0.001f)
        assertEquals(220f, bounds.maximum, 0.001f)
    }

    @Test
    fun flatDataStillHasVisibleVerticalSpace() {
        val bounds = calculateChartBounds(listOf(200f, 200f))!!

        assertEquals(190f, bounds.minimum, 0.001f)
        assertEquals(210f, bounds.maximum, 0.001f)
    }

    @Test
    fun axisTicksUseReadableRoundedValues() {
        val ticks = calculateAxisTicks(ChartBounds(160.37f, 160.68f))

        assertEquals(3, ticks.size)
        assertEquals(160.4f, ticks[0], 0.001f)
        assertEquals(160.5f, ticks[1], 0.001f)
        assertEquals(160.6f, ticks[2], 0.001f)
    }

    @Test
    fun axisTicksScaleForLargerPrices() {
        val ticks = calculateAxisTicks(ChartBounds(80f, 220f))

        assertEquals(3, ticks.size)
        assertEquals(100f, ticks[0], 0.001f)
        assertEquals(150f, ticks[1], 0.001f)
        assertEquals(200f, ticks[2], 0.001f)
    }
}
