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
    JAPAN_STOCK("日本株"),
    JAPAN_ETF("国内ETF"),
    MARKET_INDEX("指数・為替"),
    FUND_REFERENCE("国内投信参考"),
}

@Serializable
enum class InstrumentDataSource(val label: String) {
    TWELVE_DATA("Twelve Data"),
    REFERENCE_USD_JPY("参照ETF（外貨建て）"),
    YAHOO_FUND("国内投信基準価額"),
    YAHOO_JAPAN("日本株・国内ETF"),
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
        WatchInstrument("ref_all_country", "eMAXIS Slim 全世界株式（オール・カントリー）", "EMAXIS_ALL_COUNTRY", AssetType.FUND_REFERENCE, InstrumentDataSource.YAHOO_FUND),
        WatchInstrument("ref_fang", "iFreeNEXT FANG+インデックス", "IFREE_FANG_PLUS", AssetType.FUND_REFERENCE, InstrumentDataSource.YAHOO_FUND),
        WatchInstrument("ref_schd", "SBI・S・米国高配当株式ファンド（年4回決算型）", "SBI_S_SCHD_4X", AssetType.FUND_REFERENCE, InstrumentDataSource.YAHOO_FUND),
        WatchInstrument("ref_nikkei_hd", "Tracers 日経平均高配当株50インデックス（奇数月分配型）", "TRACERS_NIKKEI_HD50", AssetType.FUND_REFERENCE, InstrumentDataSource.YAHOO_FUND),
        WatchInstrument("ref_sp500", "eMAXIS Slim 米国株式（S&P500）", "EMAXIS_SP500", AssetType.FUND_REFERENCE, InstrumentDataSource.YAHOO_FUND),
        WatchInstrument("market_nikkei", "日経平均", "NIKKEI225", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_sp500", "S&P 500", "SP500", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_dow30", "NYダウ参考（DIA）", "DOW30", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_nasdaq100", "NASDAQ-100参考（QQQ）", "NASDAQ100", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_vix", "VIX指数", "VIX", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
        WatchInstrument("market_usdjpy", "米ドル／円", "USDJPY", AssetType.MARKET_INDEX, InstrumentDataSource.TWELVE_DATA),
    )
}

internal fun resolveMainInstrumentDisplayNames(
    instruments: List<WatchInstrument>,
    resolvedNames: Map<String, String>,
): List<WatchInstrument> = instruments.map { item ->
    val resolved = resolvedNames[item.symbol]?.trim().orEmpty()
    val canAutoUpdate = item.assetType == AssetType.US_STOCK || item.assetType == AssetType.US_ETF ||
        item.assetType == AssetType.JAPAN_STOCK || item.assetType == AssetType.JAPAN_ETF
    if (
        canAutoUpdate &&
        item.displayName.equals(item.symbol, ignoreCase = true) &&
        resolved.isNotBlank() &&
        !resolved.equals(item.symbol, ignoreCase = true)
    ) {
        item.copy(displayName = resolved)
    } else {
        item
    }
}
