package com.digihori.marketpanel.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.marketPanelDataStore by preferencesDataStore(name = "market_panel_settings")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun load(): MarketPanelSettings = context.marketPanelDataStore.data
        .map { preferences ->
            val legacyStocks = preferences[ENABLED_STOCKS] ?: MarketPanelSettings.DEFAULT_STOCKS
            val legacyMarkets = preferences[ENABLED_MARKETS] ?: MarketPanelSettings.DEFAULT_MARKETS
            val decodedInstruments = preferences[INSTRUMENTS]?.let { encoded ->
                runCatching { json.decodeFromString<List<WatchInstrument>>(encoded) }.getOrNull()
            }
            val migratedInstruments = if (decodedInstruments == null) {
                DefaultWatchInstruments.items
            } else if ((preferences[INSTRUMENTS_VERSION] ?: 0) < CURRENT_INSTRUMENTS_VERSION) {
                val withAddedDefaults = decodedInstruments + DefaultWatchInstruments.items.filter { default ->
                    default.id in MIGRATION_ADDED_IDS && decodedInstruments.none { it.id == default.id }
                }
                withAddedDefaults.map { item ->
                    if (item.id == "stock_spcx" && item.dataSource == InstrumentDataSource.DEMO) {
                        item.copy(dataSource = InstrumentDataSource.TWELVE_DATA)
                    } else item
                }
            } else {
                decodedInstruments
            }
            MarketPanelSettings(
                rotationIntervalMillis = preferences[ROTATION_INTERVAL] ?: 60_000L,
                updateIntervalMillis = preferences[UPDATE_INTERVAL] ?: 5 * 60_000L,
                chartPeriod = preferences[CHART_PERIOD] ?: "1年・週足",
                enabledStocks = legacyStocks,
                enabledFunds = preferences[ENABLED_FUNDS] ?: MarketPanelSettings.DEFAULT_FUNDS,
                enabledMarkets = legacyMarkets,
                autoStart = preferences[AUTO_START] ?: false,
                keepScreenOn = preferences[KEEP_SCREEN_ON] ?: true,
                fullscreen = preferences[FULLSCREEN] ?: true,
                instruments = migratedInstruments.map { item ->
                    when (item.assetType) {
                        AssetType.US_STOCK, AssetType.US_ETF -> if (decodedInstruments == null) item.copy(enabled = item.symbol in legacyStocks) else item
                        AssetType.MARKET_INDEX -> if (decodedInstruments == null) item.copy(enabled = item.symbol in legacyMarkets) else item
                        AssetType.FUND_REFERENCE -> item
                    }
                },
            )
        }
        .first()

    suspend fun save(settings: MarketPanelSettings) {
        context.marketPanelDataStore.edit { preferences ->
            preferences[ROTATION_INTERVAL] = settings.rotationIntervalMillis
            preferences[UPDATE_INTERVAL] = settings.updateIntervalMillis
            preferences[CHART_PERIOD] = settings.chartPeriod
            preferences[ENABLED_STOCKS] = settings.enabledStocks
            preferences[ENABLED_FUNDS] = settings.enabledFunds
            preferences[ENABLED_MARKETS] = settings.enabledMarkets
            preferences[AUTO_START] = settings.autoStart
            preferences[KEEP_SCREEN_ON] = settings.keepScreenOn
            preferences[FULLSCREEN] = settings.fullscreen
            preferences[INSTRUMENTS] = json.encodeToString(settings.instruments)
            preferences[INSTRUMENTS_VERSION] = CURRENT_INSTRUMENTS_VERSION
        }
    }

    private companion object {
        val ROTATION_INTERVAL = longPreferencesKey("rotation_interval_millis")
        val UPDATE_INTERVAL = longPreferencesKey("update_interval_millis")
        val CHART_PERIOD = stringPreferencesKey("chart_period")
        val ENABLED_STOCKS = stringSetPreferencesKey("enabled_stocks")
        val ENABLED_FUNDS = stringSetPreferencesKey("enabled_funds")
        val ENABLED_MARKETS = stringSetPreferencesKey("enabled_markets")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val FULLSCREEN = booleanPreferencesKey("fullscreen")
        val INSTRUMENTS = stringPreferencesKey("watch_instruments")
        val INSTRUMENTS_VERSION = intPreferencesKey("watch_instruments_version")
        const val CURRENT_INSTRUMENTS_VERSION = 4
        val MIGRATION_ADDED_IDS = setOf(
            "ref_nikkei_hd",
            "ref_sp500",
            "market_dow30",
            "market_nasdaq100",
            "market_vix",
        )
    }
}
