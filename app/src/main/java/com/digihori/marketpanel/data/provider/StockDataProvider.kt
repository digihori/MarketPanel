package com.digihori.marketpanel.data.provider

import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PricePoint
import com.digihori.marketpanel.domain.model.Quote

interface StockDataProvider {
    suspend fun getQuote(symbol: String): Quote
    suspend fun getLongTermChart(symbol: String, range: String): List<PricePoint>
    suspend fun getMarketIndicator(id: String): MarketSnapshot
}
