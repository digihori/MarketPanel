package com.digihori.marketpanel.domain.model

data class PricePoint(
    val timestampEpochSeconds: Long,
    val value: Double,
)

data class Quote(
    val symbol: String,
    val name: String,
    val exchange: String,
    val currency: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAtEpochSeconds: Long,
)

data class StockSnapshot(
    val quote: Quote,
    val longTerm: List<PricePoint>,
    val intraday: List<PricePoint>,
)

data class MarketSnapshot(
    val id: String,
    val quote: Quote,
    val series: List<PricePoint>,
)
