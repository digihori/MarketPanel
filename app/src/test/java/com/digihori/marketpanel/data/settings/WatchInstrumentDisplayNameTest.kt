package com.digihori.marketpanel.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchInstrumentDisplayNameTest {
    @Test
    fun replacesSymbolPlaceholderWithResolvedName() {
        val item = WatchInstrument(
            id = "custom",
            displayName = "NVDA",
            symbol = "NVDA",
            assetType = AssetType.US_STOCK,
            dataSource = InstrumentDataSource.TWELVE_DATA,
        )

        val result = resolveMainInstrumentDisplayNames(listOf(item), mapOf("NVDA" to "NVIDIA Corporation"))

        assertEquals("NVIDIA Corporation", result.single().displayName)
    }

    @Test
    fun preservesUserDefinedDisplayName() {
        val item = WatchInstrument(
            id = "custom",
            displayName = "エヌビディア",
            symbol = "NVDA",
            assetType = AssetType.US_STOCK,
            dataSource = InstrumentDataSource.TWELVE_DATA,
        )

        val result = resolveMainInstrumentDisplayNames(listOf(item), mapOf("NVDA" to "NVIDIA Corporation"))

        assertEquals("エヌビディア", result.single().displayName)
    }
}
