package com.digihori.marketpanel.data.local

import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PricePoint
import com.digihori.marketpanel.domain.model.Quote
import com.digihori.marketpanel.domain.model.StockSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class CachedPricePoint(val timestamp: Long, val value: Double)

@Serializable
data class CachedQuote(
    val symbol: String,
    val name: String,
    val exchange: String,
    val currency: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: Long,
)

@Serializable
data class CachedStockSnapshot(
    val quote: CachedQuote,
    val longTerm: List<CachedPricePoint>,
    val intraday: List<CachedPricePoint>,
    val chartFetchedAtEpochMillis: Long = 0L,
)

@Serializable
data class CachedMarketSnapshot(
    val id: String,
    val quote: CachedQuote,
    val series: List<CachedPricePoint>,
)

fun StockSnapshot.toCached(chartFetchedAtEpochMillis: Long) = CachedStockSnapshot(
    quote.toCached(),
    longTerm.map(PricePoint::toCached),
    intraday.map(PricePoint::toCached),
    chartFetchedAtEpochMillis,
)

fun CachedStockSnapshot.toDomain() = StockSnapshot(
    quote.toDomain(),
    longTerm.map(CachedPricePoint::toDomain),
    intraday.map(CachedPricePoint::toDomain),
)

fun MarketSnapshot.toCached() = CachedMarketSnapshot(
    id,
    quote.toCached(),
    series.map(PricePoint::toCached),
)

fun CachedMarketSnapshot.toDomain() = MarketSnapshot(
    id,
    quote.toDomain(),
    series.map(CachedPricePoint::toDomain),
)

private fun Quote.toCached() = CachedQuote(
    symbol, name, exchange, currency, price, change, changePercent, updatedAtEpochSeconds,
)

private fun CachedQuote.toDomain() = Quote(
    symbol, name, exchange, currency, price, change, changePercent, updatedAt,
)

private fun PricePoint.toCached() = CachedPricePoint(timestampEpochSeconds, value)
private fun CachedPricePoint.toDomain() = PricePoint(timestamp, value)
