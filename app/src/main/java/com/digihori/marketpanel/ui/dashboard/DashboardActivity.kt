package com.digihori.marketpanel.ui.dashboard

import android.app.Activity
import android.content.Intent
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
import com.digihori.marketpanel.data.settings.WatchInstrument
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
    private var rotationProgressAnimator: ObjectAnimator? = null
    private var started = false
    private var fullscreenEnabled = true
    private var stockIndex = 0
    private var fundIndex = 0
    private var marketIndex = 0
    private var currentRotationIntervalMillis = 60_000L
    private var configurationJob: Job? = null
    private var refreshJob: Job? = null
    private val stockPanelsById = linkedMapOf<String, DemoMarketData.StockPanels>()
    private val fundPanelsById = linkedMapOf<String, DemoMarketData.MarketPanel>()
    private val marketPanelsById = linkedMapOf<String, DemoMarketData.MarketPanel>()

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
        rotationProgress = findViewById(R.id.rotationProgress)
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
        configurationJob?.cancel()
        configurationJob = scope.launch {
            configureRotations()
        }
    }

    override fun onStop() {
        started = false
        configurationJob?.cancel()
        refreshJob?.cancel()
        if (::stockRotation.isInitialized) stockRotation.stop()
        if (::fundRotation.isInitialized) fundRotation.stop()
        if (::marketRotation.isInitialized) marketRotation.stop()
        rotationProgressAnimator?.cancel()
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
        rotationProgressAnimator?.cancel()
        rotationProgressAnimator = null
        rotationProgress.progress = 0
        applySystemSettings(settings)

        if (::stockRotation.isInitialized) stockRotation.stop()
        if (::fundRotation.isInitialized) fundRotation.stop()
        if (::marketRotation.isInitialized) marketRotation.stop()

        val enabled = settings.instruments.filter { it.enabled }
        val stocks = enabled.filter { it.assetType == AssetType.US_STOCK || it.assetType == AssetType.US_ETF }
        val funds = enabled.filter { it.assetType == AssetType.FUND_REFERENCE }
        val markets = enabled.filter { it.assetType == AssetType.MARKET_INDEX }
        val stockIds = stocks.map { it.symbol }
        val marketIds = markets.map { it.symbol }
        stockPanelsById.keys.retainAll(stockIds.toSet())
        fundPanelsById.keys.retainAll(funds.map { it.id }.toSet())
        marketPanelsById.keys.retainAll(marketIds.toSet())
        mainPanel.showStatus("読み込み中…")
        intradayPanel.showStatus("基準価額 • DEMO")
        marketPanel.showStatus("読み込み中…")
        coroutineScope {
            listOf(
                async { loadStocksProgressively(stockIds, settings.rotationIntervalMillis) },
                async { loadFundsProgressively(funds, settings.rotationIntervalMillis) },
                async { loadMarketsProgressively(marketIds, settings.rotationIntervalMillis) },
            ).awaitAll()
        }
        if (!started) return
        restartStockRotation(stockIds.mapNotNull(stockPanelsById::get), settings.rotationIntervalMillis)
        restartFundRotation(funds.mapNotNull { fundPanelsById[it.id] }, settings.rotationIntervalMillis)
        restartMarketRotation(marketIds.mapNotNull(marketPanelsById::get), settings.rotationIntervalMillis)
        apiUsageText.text = apiUsageRepository.displayText()

        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                delay(settings.updateIntervalMillis)
                refreshDisplayedData(settings, stockIds, marketIds)
            }
        }
    }

    private suspend fun loadFundsProgressively(
        instruments: List<WatchInstrument>,
        intervalMillis: Long,
        forceRefresh: Boolean = false,
    ) {
        var loaded = 0
        var failed = 0
        instruments.forEach { instrument ->
            val panel = if (instrument.symbol in MARKET_INDICATOR_IDS) {
                marketRepository.getMarkets(listOf(instrument.symbol), forceRefresh)
                    .items.firstOrNull()?.value?.toFundPanel(instrument)
            } else {
                marketRepository.getStocks(listOf(instrument.symbol), "1y", forceRefresh)
                    .items.firstOrNull()?.value?.toFundPanel(instrument)
            }
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
                fundPanelsById[instrument.id]?.let { LoadedValue(it, com.digihori.marketpanel.data.repository.DataOrigin.CACHE_FRESH) }
            }, failed)
            showDataStatus(
                intradayPanel,
                "${result.statusText()} • 表示可能$loaded/${instruments.size}",
                result.isErrorStatus(),
            )
        }
    }

    private suspend fun loadStocksProgressively(
        stockIds: List<String>,
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
                stockPanelsById[symbol] = result.value.toPanels()
                val panels = stockIds.mapNotNull(stockPanelsById::get)
                applyStocks(panels, intervalMillis)
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
                marketPanelsById[id] = result.value.toPanelData()
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
        marketIds: List<String>,
    ) {
        mainPanel.showStatus("更新中…")
        marketPanel.showStatus("更新中…")
        coroutineScope {
            listOf(
                async { loadStocksProgressively(stockIds, settings.rotationIntervalMillis, true) },
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
        apiUsageText.text = apiUsageRepository.displayText()
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
        val MARKET_INDICATOR_IDS = setOf("NIKKEI225", "SP500", "USDJPY")
        const val DEBUG_ROTATION_INTERVAL_MILLIS = 5_000L
        const val SUB1_PHASE_OFFSET_MILLIS = 10_000L
        const val SUB2_PHASE_OFFSET_MILLIS = 20_000L
    }
}
