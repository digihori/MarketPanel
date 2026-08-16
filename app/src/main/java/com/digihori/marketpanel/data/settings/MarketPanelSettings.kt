package com.digihori.marketpanel.data.settings

data class MarketPanelSettings(
    val rotationIntervalMillis: Long = 60_000L,
    val updateIntervalMillis: Long = 5 * 60_000L,
    val chartPeriod: String = "1年・週足",
    val enabledStocks: Set<String> = DEFAULT_STOCKS,
    val enabledFunds: Set<String> = DEFAULT_FUNDS,
    val enabledMarkets: Set<String> = DEFAULT_MARKETS,
    val autoStart: Boolean = false,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val instruments: List<WatchInstrument> = DefaultWatchInstruments.items,
) {
    companion object {
        val DEFAULT_STOCKS = setOf("IBM", "MCD", "SPCX", "JEPQ", "QQQI", "563A")
        val DEFAULT_FUNDS = setOf("EMAXIS_ALL_COUNTRY", "IFREE_FANG_PLUS", "SBI_S_SCHD_4X")
        val DEFAULT_MARKETS = setOf("NIKKEI225", "SP500", "USDJPY")
    }
}
