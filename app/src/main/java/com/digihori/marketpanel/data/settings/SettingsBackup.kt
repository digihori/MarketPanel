package com.digihori.marketpanel.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val rotationIntervalMillis: Long,
    val updateIntervalMillis: Long,
    val chartPeriod: String,
    val autoStart: Boolean,
    val keepScreenOn: Boolean,
    val fullscreen: Boolean,
    val instruments: List<WatchInstrument>,
) {
    fun validate() {
        require(format == FORMAT) { "MarketPanelのバックアップではありません" }
        require(version == VERSION) { "未対応のバックアップバージョンです: $version" }
        require(rotationIntervalMillis > 0 && updateIntervalMillis > 0) { "更新間隔が不正です" }
        require(instruments.map { it.id }.distinct().size == instruments.size) { "銘柄IDが重複しています" }
        require(instruments.all { it.id.isNotBlank() && it.displayName.isNotBlank() && it.symbol.isNotBlank() }) {
            "入力が不足している銘柄があります"
        }
    }

    fun toSettings(): MarketPanelSettings {
        validate()
        return MarketPanelSettings(
            rotationIntervalMillis = rotationIntervalMillis,
            updateIntervalMillis = updateIntervalMillis,
            chartPeriod = chartPeriod,
            enabledStocks = instruments.filter { it.enabled && it.assetType in MAIN_TYPES }.mapTo(mutableSetOf()) { it.symbol },
            enabledFunds = instruments.filter { it.enabled && it.assetType == AssetType.FUND_REFERENCE }.mapTo(mutableSetOf()) { it.id },
            enabledMarkets = instruments.filter { it.enabled && it.assetType == AssetType.MARKET_INDEX }.mapTo(mutableSetOf()) { it.symbol },
            autoStart = autoStart,
            keepScreenOn = keepScreenOn,
            fullscreen = fullscreen,
            instruments = instruments,
        )
    }

    companion object {
        const val FORMAT = "MarketPanel settings"
        const val VERSION = 1
        private val MAIN_TYPES = setOf(AssetType.US_STOCK, AssetType.US_ETF)

        fun from(settings: MarketPanelSettings) = SettingsBackup(
            rotationIntervalMillis = settings.rotationIntervalMillis,
            updateIntervalMillis = settings.updateIntervalMillis,
            chartPeriod = settings.chartPeriod,
            autoStart = settings.autoStart,
            keepScreenOn = settings.keepScreenOn,
            fullscreen = settings.fullscreen,
            instruments = settings.instruments,
        )
    }
}

object SettingsBackupJson {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(settings: MarketPanelSettings): String = json.encodeToString(SettingsBackup.from(settings))

    fun decode(text: String): MarketPanelSettings = json.decodeFromString<SettingsBackup>(text).toSettings()
}
