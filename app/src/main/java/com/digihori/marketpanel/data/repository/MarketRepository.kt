package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.local.MarketCacheDataSource
import com.digihori.marketpanel.data.provider.StockDataProvider
import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.StockSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class MarketRepository(
    private val provider: StockDataProvider,
    private val cache: MarketCacheDataSource,
    private val quoteLifetimeMillis: Long = 2 * 60 * 60 * 1_000L,
    private val chartLifetimeMillis: Long = 24 * 60 * 60 * 1_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun getStocks(
        symbols: List<String>,
        range: String,
        forceRefresh: Boolean = false,
    ): LoadBatch<StockSnapshot> = supervisorScope {
        val results = symbols.map { symbol ->
            async { getStock(symbol, range, forceRefresh) }
        }.awaitAll()
        LoadBatch(results.filterNotNull(), results.count { it == null })
    }

    suspend fun getMarkets(
        ids: List<String>,
        forceRefresh: Boolean = false,
    ): LoadBatch<MarketSnapshot> = supervisorScope {
        val results = ids.map { id ->
            async { getMarket(id, forceRefresh) }
        }.awaitAll()
        LoadBatch(results.filterNotNull(), results.count { it == null })
    }

    private suspend fun getStock(
        symbol: String,
        range: String,
        forceRefresh: Boolean,
    ): LoadedValue<StockSnapshot>? {
        val cached = cache.findStock(symbol, range)
        val currentTime = now()
        val quoteIsFresh = cached != null && currentTime - cached.fetchedAtEpochMillis < quoteLifetimeMillis
        val chartIsFresh = cached != null &&
            currentTime - cached.secondaryFetchedAtEpochMillis < chartLifetimeMillis &&
            cached.value.longTerm.isNotEmpty()
        if (quoteIsFresh && chartIsFresh) {
            return LoadedValue(cached.value, DataOrigin.CACHE_FRESH)
        }
        val quote = if (quoteIsFresh) cached?.value?.quote else
            runCatching { provider.getQuote(symbol) }.getOrNull() ?: cached?.value?.quote
        val chart = if (chartIsFresh) cached?.value?.longTerm.orEmpty() else
            runCatching { provider.getLongTermChart(symbol, range) }.getOrNull() ?: cached?.value?.longTerm.orEmpty()
        if (quote == null) return null
        val quoteFetchedAt = if (quoteIsFresh || quote == cached?.value?.quote) {
            cached?.fetchedAtEpochMillis ?: currentTime
        } else currentTime
        val chartFetchedAt = if (chartIsFresh || chart == cached?.value?.longTerm) {
            cached?.secondaryFetchedAtEpochMillis ?: 0L
        } else currentTime
        val snapshot = StockSnapshot(quote, chart, emptyList())
        cache.saveStock(symbol, range, snapshot, quoteFetchedAt, chartFetchedAt)
        val origin = if (quoteIsFresh && chartIsFresh) DataOrigin.CACHE_FRESH
        else if ((!quoteIsFresh && quote == cached?.value?.quote) || (!chartIsFresh && chart == cached?.value?.longTerm)) {
            DataOrigin.CACHE_STALE
        } else DataOrigin.NETWORK
        return LoadedValue(snapshot, origin)
    }

    private suspend fun getMarket(id: String, forceRefresh: Boolean): LoadedValue<MarketSnapshot>? {
        val cached = cache.findMarket(id)
        if (cached != null && now() - cached.fetchedAtEpochMillis < quoteLifetimeMillis) {
            return LoadedValue(cached.value, DataOrigin.CACHE_FRESH)
        }
        return runCatching { provider.getMarketIndicator(id) }
            .onSuccess { cache.saveMarket(id, it, now()) }
            .getOrNull()?.let { LoadedValue(it, DataOrigin.NETWORK) }
            ?: cached?.value?.let { LoadedValue(it, DataOrigin.CACHE_STALE) }
    }
}

enum class DataOrigin {
    NETWORK,
    CACHE_FRESH,
    CACHE_STALE,
}

data class LoadedValue<T>(
    val value: T,
    val origin: DataOrigin,
)

data class LoadBatch<T>(
    val items: List<LoadedValue<T>>,
    val failedCount: Int,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    fun statusText(): String = when {
        isEmpty -> "データ取得失敗"
        failedCount > 0 -> "一部取得失敗"
        items.any { it.origin == DataOrigin.CACHE_STALE } -> "オフライン • 保存データ"
        items.all { it.origin == DataOrigin.CACHE_FRESH } -> "キャッシュ"
        else -> "オンライン"
    }

    fun isErrorStatus(): Boolean = isEmpty || failedCount > 0 ||
        items.any { it.origin == DataOrigin.CACHE_STALE }
}
