package com.digihori.marketpanel.data.local

import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.StockSnapshot
import kotlinx.serialization.json.Json

interface MarketCacheDataSource {
    suspend fun findStock(symbol: String, range: String): CachedValue<StockSnapshot>?
    suspend fun saveStock(symbol: String, range: String, value: StockSnapshot, now: Long, chartFetchedAt: Long)
    suspend fun findMarket(id: String): CachedValue<MarketSnapshot>?
    suspend fun saveMarket(id: String, value: MarketSnapshot, now: Long)
}

class MarketCache(
    private val dao: MarketCacheDao,
    private val json: Json,
) : MarketCacheDataSource {
    override suspend fun findStock(symbol: String, range: String): CachedValue<StockSnapshot>? =
        dao.find(stockKey(symbol, range))?.let { entity ->
            runCatching {
                val stored = json.decodeFromString<CachedStockSnapshot>(entity.payload)
                CachedValue(
                    value = stored.toDomain(),
                    fetchedAtEpochMillis = entity.fetchedAtEpochMillis,
                    secondaryFetchedAtEpochMillis = stored.chartFetchedAtEpochMillis
                        .takeIf { it > 0 } ?: entity.fetchedAtEpochMillis,
                )
            }.getOrNull()
        }

    override suspend fun saveStock(symbol: String, range: String, value: StockSnapshot, now: Long, chartFetchedAt: Long) {
        dao.upsert(
            MarketCacheEntity(
                cacheKey = stockKey(symbol, range),
                payload = json.encodeToString(CachedStockSnapshot.serializer(), value.toCached(chartFetchedAt)),
                fetchedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun findMarket(id: String): CachedValue<MarketSnapshot>? =
        dao.find(marketKey(id))?.let { entity ->
            runCatching {
                CachedValue(
                    value = json.decodeFromString<CachedMarketSnapshot>(entity.payload).toDomain(),
                    fetchedAtEpochMillis = entity.fetchedAtEpochMillis,
                )
            }.getOrNull()
        }

    override suspend fun saveMarket(id: String, value: MarketSnapshot, now: Long) {
        dao.upsert(
            MarketCacheEntity(
                cacheKey = marketKey(id),
                payload = json.encodeToString(CachedMarketSnapshot.serializer(), value.toCached()),
                fetchedAtEpochMillis = now,
            ),
        )
    }

    private fun stockKey(symbol: String, range: String) = "stock:$symbol:$range"
    private fun marketKey(id: String) = "market:$id"
}

data class CachedValue<T>(
    val value: T,
    val fetchedAtEpochMillis: Long,
    val secondaryFetchedAtEpochMillis: Long = fetchedAtEpochMillis,
)
