package com.digihori.marketpanel.ui.dashboard

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.ProgressBar
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import com.digihori.marketpanel.R
import com.digihori.marketpanel.BuildConfig
import com.digihori.marketpanel.MarketPanelApplication
import com.digihori.marketpanel.data.DemoMarketData
import com.digihori.marketpanel.data.repository.MarketRepository
import com.digihori.marketpanel.data.repository.ApiUsageRepository
import com.digihori.marketpanel.data.repository.LoadBatch
import com.digihori.marketpanel.data.repository.LoadedValue
import com.digihori.marketpanel.data.settings.SettingsStore
import com.digihori.marketpanel.data.settings.AssetType
import com.digihori.marketpanel.data.settings.DataRefreshMode
import com.digihori.marketpanel.data.settings.MarketPanelSettings
import com.digihori.marketpanel.data.settings.InstrumentDataSource
import com.digihori.marketpanel.data.settings.WatchInstrument
import com.digihori.marketpanel.data.settings.isNightModeScheduled
import com.digihori.marketpanel.data.settings.resolveMainInstrumentDisplayNames
import com.digihori.marketpanel.rotation.RotationController
import com.digihori.marketpanel.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class DashboardActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsStore: SettingsStore
    private lateinit var marketRepository: MarketRepository
    private lateinit var apiUsageRepository: ApiUsageRepository
    private lateinit var stockRotation: RotationController<DemoMarketData.StockPanels>
    private lateinit var fundRotation: RotationController<DemoMarketData.MarketPanel>
    private lateinit var marketRotation: RotationController<DemoMarketData.MarketPanel>
    private lateinit var mainPanel: MarketPanelView
    private lateinit var intradayPanel: MarketPanelView
    private lateinit var marketPanel: MarketPanelView
    private lateinit var apiUsageText: TextView
    private lateinit var rotationProgress: ProgressBar
    private lateinit var nightOverlay: View
    private var rotationProgressAnimator: ObjectAnimator? = null
    private var started = false
    private var fullscreenEnabled = true
    private var stockIndex = 0
    private var fundIndex = 0
    private var marketIndex = 0
    private var currentRotationIntervalMillis = 60_000L
    private var apiUsageDisplay = if (BuildConfig.USE_DEMO_DATA) "API DEMO" else "API -- / 800"
    private var batteryDisplay = "BAT --%"
    private var batteryReceiverRegistered = false
    private var configurationJob: Job? = null
    private var refreshJob: Job? = null
    private var nightModeJob: Job? = null
    private var nightModeActive = false
    private var nightPreviewUntilMillis = 0L
    private var normalScreenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private val stockPanelsById = linkedMapOf<String, DemoMarketData.StockPanels>()
    private val fundPanelsById = linkedMapOf<String, DemoMarketData.MarketPanel>()
    private val marketPanelsById = linkedMapOf<String, DemoMarketData.MarketPanel>()
    private val marketDisplayNamesById = mutableMapOf<String, String>()
    private val resolvedStockNamesBySymbol = mutableMapOf<String, String>()
    private val persistedAutoNameSymbols = mutableSetOf<String>()
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            batteryDisplay = buildString {
                append("BAT ")
                append(percent?.let { "$it%" } ?: "--%")
                if (charging) append(" • 充電中")
            }
            updateHeader()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_dashboard)

        settingsStore = SettingsStore(applicationContext)
        marketRepository = (application as MarketPanelApplication).container.marketRepository
        apiUsageRepository = (application as MarketPanelApplication).container.apiUsageRepository
        mainPanel = findViewById(R.id.mainPanel)
        intradayPanel = findViewById(R.id.intradayPanel)
        marketPanel = findViewById(R.id.marketPanel)
        apiUsageText = findViewById(R.id.apiUsageText)
        updateHeader()
        rotationProgress = findViewById(R.id.rotationProgress)
        nightOverlay = findViewById(R.id.nightOverlay)
        nightOverlay.setOnClickListener {
            if (nightModeActive) {
                nightPreviewUntilMillis = System.currentTimeMillis() + NIGHT_PREVIEW_MILLIS
                updateNightVisual()
            }
        }
        stockIndex = savedInstanceState?.getInt(KEY_STOCK_INDEX) ?: 0
        fundIndex = savedInstanceState?.getInt(KEY_FUND_INDEX) ?: 0
        marketIndex = savedInstanceState?.getInt(KEY_MARKET_INDEX) ?: 0

        val openSettings = View.OnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        findViewById<View>(android.R.id.content).setOnLongClickListener(openSettings)
        mainPanel.setOnLongClickListener(openSettings)
        intradayPanel.setOnLongClickListener(openSettings)
        marketPanel.setOnLongClickListener(openSettings)
        enterImmersiveMode()
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiverRegistered = true
        configurationJob?.cancel()
        configurationJob = scope.launch {
            configureRotations()
        }
        nightModeJob?.cancel()
        nightModeJob = scope.launch { monitorNightMode() }
    }

    override fun onStop() {
        started = false
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }
        configurationJob?.cancel()
        refreshJob?.cancel()
        nightModeJob?.cancel()
        if (::stockRotation.isInitialized) stockRotation.stop()
        if (::fundRotation.isInitialized) fundRotation.stop()
        if (::marketRotation.isInitialized) marketRotation.stop()
        rotationProgressAnimator?.cancel()
        leaveNightMode(restartDashboard = false)
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_STOCK_INDEX, if (::stockRotation.isInitialized) stockRotation.currentIndex else stockIndex)
        outState.putInt(KEY_FUND_INDEX, if (::fundRotation.isInitialized) fundRotation.currentIndex else fundIndex)
        outState.putInt(KEY_MARKET_INDEX, if (::marketRotation.isInitialized) marketRotation.currentIndex else marketIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreenEnabled) enterImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private suspend fun configureRotations() {
        val settings = settingsStore.load()
        if (!started) return
        currentRotationIntervalMillis = settings.rotationIntervalMillis
        marketRepository.setRefreshMode(settings.dataRefreshMode)
        rotationProgressAnimator?.cancel()
        rotationProgressAnimator = null
        rotationProgress.progress = 0
        applySystemSettings(settings)

        if (isNightModeNow(settings)) {
            enterNightMode()
            return
        }

        if (::stockRotation.isInitialized) stockRotation.stop()
        if (::fundRotation.isInitialized) fundRotation.stop()
        if (::marketRotation.isInitialized) marketRotation.stop()

        val enabled = settings.instruments.filter { it.enabled }
        val stocks = enabled.filter { it.assetType == AssetType.US_STOCK || it.assetType == AssetType.US_ETF }
        val japanStocks = enabled.filter { it.assetType == AssetType.JAPAN_STOCK || it.assetType == AssetType.JAPAN_ETF }
        val funds = enabled.filter { it.assetType == AssetType.FUND_REFERENCE }
        val markets = enabled.filter { it.assetType == AssetType.MARKET_INDEX }
        val stockIds = stocks.map { it.symbol }
        val japanStockIds = japanStocks.map { it.symbol }
        val marketIds = markets.map { it.symbol }
        resolvedStockNamesBySymbol.clear()
        persistedAutoNameSymbols.clear()
        marketDisplayNamesById.clear()
        marketDisplayNamesById.putAll(markets.associate { it.symbol to it.displayName })
        stockPanelsById.keys.retainAll((stockIds + japanStockIds).toSet())
        fundPanelsById.keys.retainAll(funds.map { it.id }.toSet())
        marketPanelsById.keys.retainAll(marketIds.toSet())
        restartAllRotations(stockIds, japanStockIds, funds, marketIds, settings.rotationIntervalMillis)
        mainPanel.showStatus("読み込み中…")
        intradayPanel.showStatus("国内投信 • 読み込み中…")
        marketPanel.showStatus("読み込み中…")
        coroutineScope {
            listOf(
                async { loadStocksProgressively(stockIds, japanStockIds, settings.rotationIntervalMillis) },
                async { loadJapanStocksProgressively(japanStocks, stockIds, japanStockIds, settings.rotationIntervalMillis) },
                async { loadFundsProgressively(funds, settings.rotationIntervalMillis) },
                async { loadMarketsProgressively(marketIds, settings.rotationIntervalMillis) },
            ).awaitAll()
        }
        if (!started) return
        restartAllRotations(stockIds, japanStockIds, funds, marketIds, settings.rotationIntervalMillis)
        persistResolvedStockNames(settings)
        apiUsageDisplay = apiUsageRepository.displayText()
        updateHeader()

        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                delay(nextRefreshDelayMillis(settings.dataRefreshMode))
                refreshDisplayedData(settings, stockIds, japanStockIds, marketIds)
            }
        }
    }

    private suspend fun monitorNightMode() {
        while (started) {
            val settings = settingsStore.load()
            val scheduled = isNightModeNow(settings)
            when {
                scheduled && !nightModeActive -> {
                    configurationJob?.cancel()
                    enterNightMode()
                }
                !scheduled && nightModeActive -> leaveNightMode(restartDashboard = true)
                nightModeActive -> updateNightVisual()
            }
            delay(NIGHT_CHECK_INTERVAL_MILLIS)
        }
    }

    private fun isNightModeNow(settings: MarketPanelSettings): Boolean {
        if (!settings.nightModeEnabled) return false
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return isNightModeScheduled(nowMinutes, settings.nightStartMinutes, settings.nightEndMinutes)
    }

    private fun enterNightMode() {
        if (nightModeActive) return
        nightModeActive = true
        nightPreviewUntilMillis = 0L
        normalScreenBrightness = window.attributes.screenBrightness
        refreshJob?.cancel()
        if (::stockRotation.isInitialized) stockRotation.stop()
        if (::fundRotation.isInitialized) fundRotation.stop()
        if (::marketRotation.isInitialized) marketRotation.stop()
        rotationProgressAnimator?.cancel()
        rotationProgress.progress = 0
        updateNightVisual()
    }

    private fun leaveNightMode(restartDashboard: Boolean) {
        if (!nightModeActive) return
        nightModeActive = false
        nightPreviewUntilMillis = 0L
        nightOverlay.visibility = View.GONE
        setScreenBrightness(normalScreenBrightness)
        if (restartDashboard && started) {
            configurationJob?.cancel()
            configurationJob = scope.launch { configureRotations() }
        }
    }

    private fun updateNightVisual() {
        if (!nightModeActive) return
        val previewing = System.currentTimeMillis() < nightPreviewUntilMillis
        nightOverlay.visibility = if (previewing) View.GONE else View.VISIBLE
        setScreenBrightness(if (previewing) normalScreenBrightness else NIGHT_SCREEN_BRIGHTNESS)
    }

    private fun setScreenBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private suspend fun loadFundsProgressively(
        instruments: List<WatchInstrument>,
        intervalMillis: Long,
        forceRefresh: Boolean = false,
    ) {
        var loaded = 0
        var failed = 0
        val pending = coroutineScope {
            instruments.map { instrument ->
                async { instrument to fetchFundPanel(instrument, forceRefresh) }
            }
        }
        pending.forEach { deferred ->
            val (instrument, panel) = deferred.await()
            if (!started) return
            panel?.let {
                fundPanelsById[instrument.id] = it
                loaded++
                applyFunds(instruments.mapNotNull { fundPanelsById[it.id] }, intervalMillis)
            } ?: run { failed++ }
            intradayPanel.showStatus(
                "取得中 ${loaded + failed}/${instruments.size} • 成功$loaded • 失敗$failed",
                failed > 0,
            )
        }
        if (instruments.isNotEmpty()) {
            val result = LoadBatch(instruments.mapNotNull { instrument ->
                fundPanelsById[instrument.id]?.let {
                    LoadedValue(it, com.digihori.marketpanel.data.repository.DataOrigin.CACHE_FRESH, System.currentTimeMillis())
                }
            }, failed)
            showDataStatus(
                intradayPanel,
                "${result.statusText()} • 表示可能$loaded/${instruments.size}",
                result.isErrorStatus(),
            )
        }
    }

    private suspend fun fetchFundPanel(
        instrument: WatchInstrument,
        forceRefresh: Boolean,
    ): DemoMarketData.MarketPanel? = when {
        instrument.dataSource == InstrumentDataSource.YAHOO_FUND ->
            marketRepository.getFunds(listOf(instrument.symbol), forceRefresh)
                .items.firstOrNull()?.let { it.value.toActualFundPanel(instrument, it.checkedAtEpochMillis) }
                ?: loadFundReferenceFallback(instrument, forceRefresh)
        instrument.symbol in MARKET_INDICATOR_IDS ->
            marketRepository.getMarkets(listOf(instrument.symbol), forceRefresh)
                .items.firstOrNull()?.let { it.value.toFundPanel(instrument, it.checkedAtEpochMillis) }
        else ->
            marketRepository.getStocks(listOf(instrument.symbol), "1y", forceRefresh)
                .items.firstOrNull()?.let { it.value.toFundPanel(instrument, it.checkedAtEpochMillis) }
    }

    private suspend fun loadFundReferenceFallback(
        instrument: WatchInstrument,
        forceRefresh: Boolean,
    ): DemoMarketData.MarketPanel? {
        val reference = FUND_REFERENCE_FALLBACKS[instrument.symbol] ?: return null
        val fallbackInstrument = instrument.copy(symbol = reference)
        return if (reference in MARKET_INDICATOR_IDS) {
            marketRepository.getMarkets(listOf(reference), forceRefresh)
                .items.firstOrNull()?.let { it.value.toFundPanel(fallbackInstrument, it.checkedAtEpochMillis) }
        } else {
            marketRepository.getStocks(listOf(reference), "1y", forceRefresh)
                .items.firstOrNull()?.let { it.value.toFundPanel(fallbackInstrument, it.checkedAtEpochMillis) }
        }
    }

    private suspend fun loadStocksProgressively(
        stockIds: List<String>,
        japanStockIds: List<String>,
        intervalMillis: Long,
        forceRefresh: Boolean = false,
    ) {
        val loaded = mutableListOf<LoadedValue<com.digihori.marketpanel.domain.model.StockSnapshot>>()
        var failed = 0
        stockIds.forEach { symbol ->
            val batch = marketRepository.getStocks(listOf(symbol), "1y", forceRefresh)
            if (!started) return
            loaded += batch.items
            failed += batch.failedCount
            batch.items.firstOrNull()?.let { result ->
                resolvedStockNamesBySymbol[symbol] = result.value.quote.name
                stockPanelsById[symbol] = result.value.toPanels(result.checkedAtEpochMillis)
                applyStocks(
                    interleaveMainPanels(
                        stockIds.mapNotNull(stockPanelsById::get),
                        japanStockIds.mapNotNull(stockPanelsById::get),
                    ),
                    intervalMillis,
                )
            }
            mainPanel.showStatus(
                "取得中 ${loaded.size + failed}/${stockIds.size} • 成功${loaded.size} • 失敗$failed",
                failed > 0,
            )
        }
        val result = LoadBatch(loaded, failed)
        showDataStatus(
            mainPanel,
            "${result.statusText()} • 表示可能${loaded.size}/${stockIds.size}",
            result.isErrorStatus(),
        )
    }

    private suspend fun loadJapanStocksProgressively(
        instruments: List<WatchInstrument>,
        stockIds: List<String>,
        japanStockIds: List<String>,
        intervalMillis: Long,
    ) = coroutineScope {
        val results = instruments.map { instrument ->
            async {
                instrument to marketRepository.getJapanStocks(listOf(instrument.symbol))
                    .items.firstOrNull()
            }
        }.awaitAll()
        results.forEach { (instrument, loaded) ->
            if (!started) return@coroutineScope
            loaded?.let {
                resolvedStockNamesBySymbol[instrument.symbol] = it.value.quote.name
                stockPanelsById[instrument.symbol] =
                    it.value.toJapanStockPanels(instrument.displayName, it.checkedAtEpochMillis)
            }
        }
        if (started) {
            applyStocks(
                interleaveMainPanels(
                    stockIds.mapNotNull(stockPanelsById::get),
                    japanStockIds.mapNotNull(stockPanelsById::get),
                ),
                intervalMillis,
            )
        }
    }

    private suspend fun loadMarketsProgressively(
        marketIds: List<String>,
        intervalMillis: Long,
        forceRefresh: Boolean = false,
    ) {
        val loaded = mutableListOf<LoadedValue<com.digihori.marketpanel.domain.model.MarketSnapshot>>()
        var failed = 0
        marketIds.forEach { id ->
            val batch = marketRepository.getMarkets(listOf(id), forceRefresh)
            if (!started) return
            loaded += batch.items
            failed += batch.failedCount
            batch.items.firstOrNull()?.let { result ->
                marketPanelsById[id] = result.value.toPanelData(marketDisplayNamesById[id], result.checkedAtEpochMillis)
                val panels = marketIds.mapNotNull(marketPanelsById::get)
                applyMarkets(panels, intervalMillis)
            }
            marketPanel.showStatus(
                "取得中 ${loaded.size + failed}/${marketIds.size} • 成功${loaded.size} • 失敗$failed",
                failed > 0,
            )
        }
        val result = LoadBatch(loaded, failed)
        showDataStatus(
            marketPanel,
            "${result.statusText()} • 表示可能${loaded.size}/${marketIds.size}",
            result.isErrorStatus(),
        )
    }

    private fun applyStocks(stocks: List<DemoMarketData.StockPanels>, intervalMillis: Long) {
        if (stocks.isEmpty()) return
        if (::stockRotation.isInitialized) {
            stockRotation.updateInterval(intervalMillis)
            stockRotation.updateItems(stocks)
            stockRotation.start()
        } else {
            stockRotation = RotationController(
                items = stocks,
                intervalMillis = intervalMillis,
                initialIndex = stockIndex,
                onCycleStarted = { startRotationProgress() },
            ) { stock, index ->
                stockIndex = index
                mainPanel.submit(stock.main)
            }
            stockRotation.start()
        }
    }

    private fun applyFunds(funds: List<DemoMarketData.MarketPanel>, intervalMillis: Long) {
        if (funds.isEmpty()) return
        if (::fundRotation.isInitialized) {
            fundRotation.updateInterval(intervalMillis)
            fundRotation.updateItems(funds)
            fundRotation.start()
        } else {
            fundRotation = RotationController(
                items = funds,
                intervalMillis = intervalMillis,
                initialIndex = fundIndex,
                phaseOffsetMillis = rotationPhaseOffset(intervalMillis, SUB1_PHASE_OFFSET_MILLIS),
            ) { fund, index ->
                fundIndex = index
                intradayPanel.submit(fund.panel)
            }
            fundRotation.start()
        }
    }

    private fun restartFundRotation(
        funds: List<DemoMarketData.MarketPanel>,
        intervalMillis: Long,
    ) {
        if (funds.isEmpty()) return
        if (::fundRotation.isInitialized) fundRotation.stop()
        fundRotation = RotationController(
            items = funds,
            intervalMillis = intervalMillis,
            initialIndex = fundIndex,
            phaseOffsetMillis = rotationPhaseOffset(intervalMillis, SUB1_PHASE_OFFSET_MILLIS),
        ) { fund, index ->
            fundIndex = index
            intradayPanel.submit(fund.panel)
        }
        fundRotation.start()
    }

    private fun restartStockRotation(
        stocks: List<DemoMarketData.StockPanels>,
        intervalMillis: Long,
    ) {
        if (stocks.isEmpty()) return
        if (::stockRotation.isInitialized) stockRotation.stop()
        stockRotation = RotationController(
            items = stocks,
            intervalMillis = intervalMillis,
            initialIndex = stockIndex,
            onCycleStarted = { startRotationProgress() },
        ) { stock, index ->
            stockIndex = index
            mainPanel.submit(stock.main)
        }
        stockRotation.start()
    }

    private fun startRotationProgress() {
        rotationProgressAnimator?.cancel()
        rotationProgress.progress = 0
        rotationProgressAnimator = ObjectAnimator.ofInt(rotationProgress, "progress", 0, 1000).apply {
            duration = currentRotationIntervalMillis
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun restartMarketRotation(
        markets: List<DemoMarketData.MarketPanel>,
        intervalMillis: Long,
    ) {
        if (markets.isEmpty()) return
        if (::marketRotation.isInitialized) marketRotation.stop()
        marketRotation = RotationController(
            items = markets,
            intervalMillis = intervalMillis,
            initialIndex = marketIndex,
            phaseOffsetMillis = rotationPhaseOffset(intervalMillis, SUB2_PHASE_OFFSET_MILLIS),
        ) { market, index ->
            marketIndex = index
            marketPanel.submit(market.panel)
        }
        marketRotation.start()
    }

    private fun applyMarkets(markets: List<DemoMarketData.MarketPanel>, intervalMillis: Long) {
        if (markets.isEmpty()) return
        if (::marketRotation.isInitialized) {
            marketRotation.updateInterval(intervalMillis)
            marketRotation.updateItems(markets)
            marketRotation.start()
        } else {
            marketRotation = RotationController(
                items = markets,
                intervalMillis = intervalMillis,
                initialIndex = marketIndex,
                phaseOffsetMillis = rotationPhaseOffset(intervalMillis, SUB2_PHASE_OFFSET_MILLIS),
            ) { market, index ->
                marketIndex = index
                marketPanel.submit(market.panel)
            }
            marketRotation.start()
        }
    }

    private suspend fun refreshDisplayedData(
        settings: com.digihori.marketpanel.data.settings.MarketPanelSettings,
        stockIds: List<String>,
        japanStockIds: List<String>,
        marketIds: List<String>,
    ) {
        mainPanel.showStatus("更新中…")
        marketPanel.showStatus("更新中…")
        coroutineScope {
            listOf(
                async { loadStocksProgressively(stockIds, japanStockIds, settings.rotationIntervalMillis, true) },
                async {
                    loadJapanStocksProgressively(
                        settings.instruments.filter {
                            it.enabled && (it.assetType == AssetType.JAPAN_STOCK || it.assetType == AssetType.JAPAN_ETF)
                        },
                        stockIds,
                        japanStockIds,
                        settings.rotationIntervalMillis,
                    )
                },
                async {
                    loadFundsProgressively(
                        settings.instruments.filter { it.enabled && it.assetType == AssetType.FUND_REFERENCE },
                        settings.rotationIntervalMillis,
                        true,
                    )
                },
                async { loadMarketsProgressively(marketIds, settings.rotationIntervalMillis, true) },
            ).awaitAll()
        }
        if (!started) return
        val funds = settings.instruments.filter { it.enabled && it.assetType == AssetType.FUND_REFERENCE }
        restartAllRotations(stockIds, japanStockIds, funds, marketIds, settings.rotationIntervalMillis)
        persistResolvedStockNames(settings)
        apiUsageDisplay = apiUsageRepository.displayText()
        updateHeader()
    }

    private fun interleaveMainPanels(
        us: List<DemoMarketData.StockPanels>,
        japan: List<DemoMarketData.StockPanels>,
    ): List<DemoMarketData.StockPanels> {
        if (us.isEmpty()) return japan
        if (japan.isEmpty()) return us
        return buildList {
            repeat(maxOf(us.size, japan.size)) { index ->
                us.getOrNull(index)?.let(::add)
                japan.getOrNull(index)?.let(::add)
            }
        }
    }

    private fun restartAllRotations(
        stockIds: List<String>,
        japanStockIds: List<String>,
        funds: List<WatchInstrument>,
        marketIds: List<String>,
        intervalMillis: Long,
    ) {
        restartStockRotation(
            interleaveMainPanels(
                stockIds.mapNotNull(stockPanelsById::get),
                japanStockIds.mapNotNull(stockPanelsById::get),
            ),
            intervalMillis,
        )
        restartFundRotation(funds.mapNotNull { fundPanelsById[it.id] }, intervalMillis)
        restartMarketRotation(marketIds.mapNotNull(marketPanelsById::get), intervalMillis)
    }

    private fun updateHeader() {
        if (::apiUsageText.isInitialized) {
            apiUsageText.text = "$apiUsageDisplay  •  $batteryDisplay"
        }
    }

    private suspend fun persistResolvedStockNames(
        settings: com.digihori.marketpanel.data.settings.MarketPanelSettings,
    ) {
        if (BuildConfig.USE_DEMO_DATA) return
        val pendingNames = resolvedStockNamesBySymbol.filterKeys { it !in persistedAutoNameSymbols }
        if (pendingNames.isEmpty()) return
        val updated = resolveMainInstrumentDisplayNames(settings.instruments, resolvedStockNamesBySymbol)
        persistedAutoNameSymbols += pendingNames.keys
        if (updated != settings.instruments) {
            settingsStore.save(settings.copy(instruments = updated))
        }
    }

    private fun showDataStatus(panel: MarketPanelView, text: String, isError: Boolean) {
        if (BuildConfig.USE_DEMO_DATA) {
            panel.showStatus("DEMO DATA", true)
        } else {
            panel.showStatus(text, isError)
        }
    }

    private fun rotationPhaseOffset(intervalMillis: Long, offsetMillis: Long): Long =
        if (intervalMillis == DEBUG_ROTATION_INTERVAL_MILLIS) 0L else offsetMillis

    private fun nextRefreshDelayMillis(mode: DataRefreshMode): Long {
        if (mode == DataRefreshMode.DEBUG) return 60_000L
        val checkpoints = when (mode) {
            DataRefreshMode.CLOSE_ONLY -> listOf(8 * 60, 8 * 60 + 20, 12 * 60, 15 * 60 + 45, 16 * 60, 20 * 60)
            DataRefreshMode.JAPAN_INTRADAY -> listOf(
                8 * 60, 8 * 60 + 20, 9 * 60 + 5, 10 * 60 + 5, 11 * 60 + 35,
                12 * 60, 12 * 60 + 35, 13 * 60 + 35, 14 * 60 + 35, 15 * 60 + 45,
                16 * 60, 20 * 60,
            )
            DataRefreshMode.FOUR_HOURS -> listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60)
            DataRefreshMode.DEBUG -> return 60_000L
        }
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance(JST).apply { timeInMillis = now }
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val nextMinutes = checkpoints.firstOrNull { it > minuteOfDay }
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (nextMinutes == null) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.MINUTE, nextMinutes ?: checkpoints.first())
        }
        return (calendar.timeInMillis - now).coerceAtLeast(1_000L)
    }

    @Suppress("DEPRECATION")
    private fun applySystemSettings(
        settings: com.digihori.marketpanel.data.settings.MarketPanelSettings,
    ) {
        fullscreenEnabled = settings.fullscreen
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (settings.fullscreen) {
            enterImmersiveMode()
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private companion object {
        const val KEY_STOCK_INDEX = "stock_index"
        const val KEY_FUND_INDEX = "fund_index"
        const val KEY_MARKET_INDEX = "market_index"
        val MARKET_INDICATOR_IDS = setOf(
            "NIKKEI225",
            "SP500",
            "DOW30",
            "NASDAQ100",
            "VIX",
            "USDJPY",
        )
        val FUND_REFERENCE_FALLBACKS = mapOf(
            "EMAXIS_ALL_COUNTRY" to "ACWI",
            "EMAXIS_SP500" to "VOO",
            "IFREE_FANG_PLUS" to "FNGS",
            "SBI_S_SCHD_4X" to "SCHD",
            "TRACERS_NIKKEI_HD50" to "NIKKEI225",
        )
        const val DEBUG_ROTATION_INTERVAL_MILLIS = 5_000L
        const val SUB1_PHASE_OFFSET_MILLIS = 10_000L
        const val SUB2_PHASE_OFFSET_MILLIS = 20_000L
        const val NIGHT_CHECK_INTERVAL_MILLIS = 15_000L
        const val NIGHT_PREVIEW_MILLIS = 30_000L
        const val NIGHT_SCREEN_BRIGHTNESS = 0.01f
        val JST: TimeZone = TimeZone.getTimeZone("Asia/Tokyo")
    }
}
