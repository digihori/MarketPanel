package com.digihori.marketpanel.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class WatchInstrument(
    val id: String,
    val displayName: String,
    val symbol: String,
    val assetType: AssetType,
    val dataSource: InstrumentDataSource,
    val enabled: Boolean = true,
)

@Serializable
enum class AssetType(val label: String) {
    US_STOCK("米国株"),
    US_ETF("米国ETF"),
    MARKET_INDEX("指数・為替"),
    FUND_REFERENCE("国内投信参考"),
}

@Serializable
enum class InstrumentDataSource(val label: String) {
    TWELVE_DATA("Twelve Data"),
    REFERENCE_USD_JPY("参考指標（円換算）"),
    DEMO("デモ／手動"),
}

object DefaultWatchInstruments {
    val items = listOf(
        WatchInstrument("stock_ibm", "IBM", "IBM", AssetType.US_STOCK, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("stock_mcd", "McDonald’s", "MCD", AssetType.US_STOCK, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("stock_spcx", "SpaceX", "SPCX", AssetType.US_STOCK, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("etf_jepq", "JEPQ", "JEPQ", AssetType.US_ETF, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("etf_qqqi", "QQQI", "QQQI", AssetType.US_ETF, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("etf_563a", "563A 国内ETF", "563A", AssetType.US_ETF, InstrumentDataSource.DEMO),
        WatchInstrument("ref_all_country", "オルカン参考", "ACWI", AssetType.FUND_REFERENCE, InstrumentDataSource.REFERENCE_USD_JPY),
        WatchInstrument("ref_fang", "FANG+参考", "FNGS", AssetType.FUND_REFERENCE, InstrumentDataSource.REFERENCE_USD_JPY),
        WatchInstrument("ref_schd", "SBI・S・米国高配当参考", "SCHD", AssetType.FUND_REFERENCE, InstrumentDataSource.REFERENCE_USD_JPY),
        WatchInstrument("ref_nikkei_hd", "Tracers 日経高配当50参考", "NIKKEI225", AssetType.FUND_REFERENCE, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("ref_sp500", "eMAXIS Slim S&P500参考", "VOO", AssetType.FUND_REFERENCE, InstrumentDataSource.REFERENCE_USD_JPY),
        WatchInstrument("market_nikkei", "日経平均", "NIKKEI225", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_sp500", "S&P 500", "SP500", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_usdjpy", "米ドル／円", "USDJPY", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
    )
}
