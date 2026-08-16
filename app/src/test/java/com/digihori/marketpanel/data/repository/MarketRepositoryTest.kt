package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.local.CachedValue
import com.digihori.marketpanel.data.local.MarketCacheDataSource
import com.digihori.marketpanel.data.provider.StockDataProvider
import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PricePoint
import com.digihori.marketpanel.domain.model.Quote
import com.digihori.marketpanel.domain.model.StockSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRepositoryTest {
    private val stock = stock("7203", 3_245.0)

    @Test
    fun freshCacheIsReturnedWithoutCallingProvider() = runTest {
        val cache = FakeCache(stock = CachedValue(stock, 900_000L))
        val provider = FakeProvider(stock)
        val repository = MarketRepository(provider, cache, now = { 1_000_000L })

        val result = repository.getStocks(listOf("7203"), "1y")

        assertEquals(DataOrigin.CACHE_FRESH, result.items.single().origin)
        assertEquals(0, provider.stockCalls)
    }

    @Test
    fun forceRefreshDoesNotBypassTwoHourQuoteCache() = runTest {
        val oldStock = stock("7203", 3_000.0)
        val cache = FakeCache(stock = CachedValue(oldStock, 900_000L))
        val provider = FakeProvider(stock)
        val repository = MarketRepository(provider, cache, now = { 1_000_000L })

        val result = repository.getStocks(listOf("7203"), "1y", forceRefresh = true)

        assertEquals(DataOrigin.CACHE_FRESH, result.items.single().origin)
        assertEquals(3_000.0, result.items.single().value.quote.price, 0.0)
        assertEquals(0, provider.stockCalls)
    }

    @Test
    fun providerFailureFallsBackToStaleCache() = runTest {
        val cache = FakeCache(stock = CachedValue(stock, 1L))
        val provider = FakeProvider(stock, fail = true)
        val repository = MarketRepository(provider, cache, now = { 10_000_000L })

        val result = repository.getStocks(listOf("7203"), "1y")

        assertEquals(DataOrigin.CACHE_STALE, result.items.single().origin)
        assertEquals("オフライン • 保存データ", result.statusText())
    }

    @Test
    fun totalFailureReturnsAnErrorBatch() = runTest {
        val repository = MarketRepository(FakeProvider(stock, fail = true), FakeCache(), now = { 1L })

        val result = repository.getStocks(listOf("7203"), "1y")

        assertTrue(result.isEmpty)
        assertEquals(1, result.failedCount)
        assertEquals("データ取得失敗", result.statusText())
    }

    private class FakeProvider(
        private val stock: StockSnapshot,
        private val fail: Boolean = false,
    ) : StockDataProvider {
        var stockCalls = 0

        override suspend fun getQuote(symbol: String): Quote {
            stockCalls++
            if (fail) error("offline")
            return stock.quote
        }

        override suspend fun getLongTermChart(symbol: String, range: String): List<PricePoint> =
            stock.longTerm

        override suspend fun getMarketIndicator(id: String): MarketSnapshot = error("not used")
    }

    private class FakeCache(
        private val stock: CachedValue<StockSnapshot>? = null,
    ) : MarketCacheDataSource {
        var savedStock: StockSnapshot? = null

        override suspend fun findStock(symbol: String, range: String) = stock
        override suspend fun saveStock(
            symbol: String,
            range: String,
            value: StockSnapshot,
            now: Long,
            chartFetchedAt: Long,
        ) {
            savedStock = value
        }

        override suspend fun findMarket(id: String): CachedValue<MarketSnapshot>? = null
        override suspend fun saveMarket(id: String, value: MarketSnapshot, now: Long) = Unit
    }

    private companion object {
        fun stock(symbol: String, price: Double): StockSnapshot {
            val quote = Quote(symbol, "Name", "Exchange", "JPY", price, 1.0, 0.1, 1L)
            val points = listOf(PricePoint(1L, price))
            return StockSnapshot(quote, points, points)
        }
    }
}
