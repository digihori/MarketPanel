package com.digihori.marketpanel.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPanelSettingsTest {
    @Test
    fun defaultsAreSuitableForAnAlwaysOnMonitor() {
        val settings = MarketPanelSettings()

        assertEquals(60_000L, settings.rotationIntervalMillis)
        assertEquals(300_000L, settings.updateIntervalMillis)
        assertTrue(settings.keepScreenOn)
        assertTrue(settings.fullscreen)
        assertTrue(settings.enabledStocks.isNotEmpty())
        assertTrue(settings.enabledMarkets.isNotEmpty())
        assertTrue(settings.instruments.any { it.assetType == AssetType.FUND_REFERENCE })
        assertTrue(settings.instruments.any { it.dataSource == InstrumentDataSource.REFERENCE_USD_JPY })
    }
}
