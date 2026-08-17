package com.digihori.marketpanel.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    @Test
    fun roundTripsSettingsAndInstrumentOrder() {
        val original = MarketPanelSettings(
            rotationIntervalMillis = 30_000,
            updateIntervalMillis = 900_000,
            instruments = DefaultWatchInstruments.items.reversed(),
        )

        val restored = SettingsBackupJson.decode(SettingsBackupJson.encode(original))

        assertEquals(original.rotationIntervalMillis, restored.rotationIntervalMillis)
        assertEquals(original.updateIntervalMillis, restored.updateIntervalMillis)
        assertEquals(original.instruments, restored.instruments)
        assertTrue(restored.enabledMarkets.contains("VIX"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBackupFormat() {
        SettingsBackupJson.decode(
            """{"format":"something else","version":1,"rotationIntervalMillis":1000,"updateIntervalMillis":1000,"chartPeriod":"1y","autoStart":false,"keepScreenOn":true,"fullscreen":true,"instruments":[]}""",
        )
    }
}
