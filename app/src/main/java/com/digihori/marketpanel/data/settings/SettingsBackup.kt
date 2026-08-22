package com.digihori.marketpanel.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val rotationIntervalMillis: Long,
    val updateIntervalMillis: Long,
    val dataRefreshMode: String = DataRefreshMode.CLOSE_ONLY.name,
    val chartPeriod: String,
    val autoStart: Boolean,
    val keepScreenOn: Boolean,
    val fullscreen: Boolean,
    val instruments: List<WatchInstrument>,
    val nightModeEnabled: Boolean = false,
    val nightStartMinutes: Int = 23 * 60,
    val nightEndMinutes: Int = 6 * 60,
) {
    fun validate() {
        require(format == FORMAT) { "MarketPanelのバックアップではありません" }
        require(version == VERSION) { "未対応のバックアップバージョンです: $version" }
        require(rotationIntervalMillis > 0 && updateIntervalMillis > 0) { "更新間隔が不正です" }
        require(nightStartMinutes in 0..1439 && nightEndMinutes in 0..1439) { "夜間モードの時刻が不正です" }
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
            dataRefreshMode = runCatching { DataRefreshMode.valueOf(dataRefreshMode) }
                .getOrDefault(DataRefreshMode.CLOSE_ONLY),
            chartPeriod = chartPeriod,
            enabledStocks = instruments.filter { it.enabled && it.assetType in MAIN_TYPES }.mapTo(mutableSetOf()) { it.symbol },
            enabledFunds = instruments.filter { it.enabled && it.assetType == AssetType.FUND_REFERENCE }.mapTo(mutableSetOf()) { it.id },
            enabledMarkets = instruments.filter { it.enabled && it.assetType == AssetType.MARKET_INDEX }.mapTo(mutableSetOf()) { it.symbol },
            autoStart = autoStart,
            keepScreenOn = keepScreenOn,
            fullscreen = fullscreen,
            nightModeEnabled = nightModeEnabled,
            nightStartMinutes = nightStartMinutes,
            nightEndMinutes = nightEndMinutes,
            instruments = instruments,
        )
    }

    companion object {
        const val FORMAT = "MarketPanel settings"
        const val VERSION = 1
        private val MAIN_TYPES = setOf(AssetType.US_STOCK, AssetType.US_ETF, AssetType.JAPAN_STOCK, AssetType.JAPAN_ETF)

        fun from(settings: MarketPanelSettings) = SettingsBackup(
            rotationIntervalMillis = settings.rotationIntervalMillis,
            updateIntervalMillis = settings.updateIntervalMillis,
            dataRefreshMode = settings.dataRefreshMode.name,
            chartPeriod = settings.chartPeriod,
            autoStart = settings.autoStart,
            keepScreenOn = settings.keepScreenOn,
            fullscreen = settings.fullscreen,
            instruments = settings.instruments,
            nightModeEnabled = settings.nightModeEnabled,
            nightStartMinutes = settings.nightStartMinutes,
            nightEndMinutes = settings.nightEndMinutes,
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
