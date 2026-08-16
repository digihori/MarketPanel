package com.digihori.marketpanel.data.provider

import com.digihori.marketpanel.data.DemoMarketData
import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PricePoint
import com.digihori.marketpanel.domain.model.Quote

class DemoStockDataProvider : StockDataProvider {
    override suspend fun getQuote(symbol: String): Quote {
        return panelForSymbol(symbol).toQuote(symbol)
    }

    override suspend fun getLongTermChart(symbol: String, range: String): List<PricePoint> =
        panelForSymbol(symbol).points.toPricePoints()

    override suspend fun getMarketIndicator(id: String): MarketSnapshot {
        val market = DemoMarketData.markets.first { it.id == id }
        return MarketSnapshot(
            id = id,
            quote = market.panel.toQuote(id),
            series = market.panel.points.toPricePoints(),
        )
    }

    private fun com.digihori.marketpanel.domain.model.PanelData.toQuote(symbol: String) = Quote(
        symbol = symbol,
        name = title.substringAfter("  ", title),
        exchange = subtitle,
        currency = if (price.startsWith("$")) "USD" else "JPY",
        price = price.filter { it.isDigit() || it == '.' }.toDouble(),
        change = change.substringBefore("  ")
            .filter { it.isDigit() || it == '.' || it == '-' || it == '+' }
            .toDoubleOrNull() ?: 0.0,
        changePercent = change.substringAfter('(').substringBefore('%').toDoubleOrNull()
            ?: change.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }.toDoubleOrNull()
            ?: 0.0,
        updatedAtEpochSeconds = System.currentTimeMillis() / 1_000,
    )

    private fun panelForSymbol(symbol: String) =
        DemoMarketData.stocks.firstOrNull { it.id == symbol }?.main
            ?: when (symbol) {
                "ACWI" -> DemoMarketData.funds.first { it.id == "EMAXIS_ALL_COUNTRY" }.panel
                "FNGS" -> DemoMarketData.funds.first { it.id == "IFREE_FANG_PLUS" }.panel
                "SCHD" -> DemoMarketData.funds.first { it.id == "SBI_S_SCHD_4X" }.panel
                "VOO" -> DemoMarketData.markets.first { it.id == "SP500" }.panel
                else -> error("No demo data for symbol: $symbol")
            }

    private fun List<Float>.toPricePoints(): List<PricePoint> = mapIndexed { index, value ->
        PricePoint(timestampEpochSeconds = index.toLong(), value = value.toDouble())
    }
}
