package com.digihori.marketpanel.ui.dashboard

import com.digihori.marketpanel.data.DemoMarketData
import com.digihori.marketpanel.data.settings.WatchInstrument
import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PanelData
import com.digihori.marketpanel.domain.model.Quote
import com.digihori.marketpanel.domain.model.StockSnapshot
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun StockSnapshot.toPanels() = DemoMarketData.StockPanels(
    id = quote.symbol,
    main = quote.toPanel(
        label = "MAIN1  •  米国株／米国ETF",
        subtitle = quote.exchange,
        points = longTerm.map { it.value.toFloat() },
        xAxisLabels = longTerm.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
    ),
    intraday = quote.toPanel(
        label = "WATCHLIST",
        subtitle = quote.exchange,
        points = emptyList(),
        xAxisLabels = emptyList(),
        includeAbsoluteChange = false,
    ),
)

fun MarketSnapshot.toJapanStockPanels(displayName: String) = DemoMarketData.StockPanels(
    id = id,
    main = quote.toPanel(
        label = "MAIN2  •  日本株／国内ETF",
        subtitle = quote.exchange,
        points = series.map { it.value.toFloat() },
        xAxisLabels = series.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
        title = "${quote.symbol}  ${if (displayName == quote.symbol) quote.name else displayName}",
    ),
    intraday = quote.toPanel(
        label = "MAIN2  •  日本株／国内ETF",
        subtitle = quote.exchange,
        points = emptyList(),
        xAxisLabels = emptyList(),
        includeAbsoluteChange = true,
    ),
)

fun MarketSnapshot.toPanelData(displayName: String? = null) = DemoMarketData.MarketPanel(
    id = id,
    panel = quote.toPanel(
        label = "SUB2  •  MARKET",
        subtitle = quote.exchange,
        points = series.map { it.value.toFloat() },
        xAxisLabels = series.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
        title = displayName?.let { "${quote.symbol}  $it" } ?: "${quote.symbol}  ${quote.name}",
    ),
)

fun StockSnapshot.toFundPanel(instrument: WatchInstrument) = DemoMarketData.MarketPanel(
    id = instrument.id,
    panel = quote.toPanel(
        label = "SUB1  •  参照ETFの値動き（実基準価額ではありません）",
        subtitle = "${instrument.displayName} • ${quote.exchange} • ${quote.currency}建て",
        points = longTerm.map { it.value.toFloat() },
        xAxisLabels = longTerm.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
        title = "${instrument.symbol}  ${instrument.displayName}",
    ),
)

fun MarketSnapshot.toFundPanel(instrument: WatchInstrument) = DemoMarketData.MarketPanel(
    id = instrument.id,
    panel = quote.toPanel(
        label = "SUB1  •  参考市場の値動き（実基準価額ではありません）",
        subtitle = "${instrument.displayName} • ${quote.exchange} • ${quote.currency}建て",
        points = series.map { it.value.toFloat() },
        xAxisLabels = series.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
        title = "${instrument.symbol}  ${instrument.displayName}",
    ),
)

fun MarketSnapshot.toActualFundPanel(instrument: WatchInstrument) = DemoMarketData.MarketPanel(
    id = instrument.id,
    panel = quote.toPanel(
        label = "SUB1  •  国内投信の実基準価額",
        subtitle = "過去1年・週次 • 1万口あたり",
        points = series.map { it.value.toFloat() },
        xAxisLabels = series.map { POINT_DATE_FORMAT.format(Date(it.timestampEpochSeconds * 1_000)) },
        includeAbsoluteChange = true,
        title = instrument.displayName,
    ).copy(
        updatedAt = "基準日 ${FUND_DATE_FORMAT.format(Date(quote.updatedAtEpochSeconds * 1_000))}",
    ),
)

private fun Quote.toPanel(
    label: String,
    subtitle: String,
    points: List<Float>,
    xAxisLabels: List<String>,
    includeAbsoluteChange: Boolean,
    title: String = "$symbol  $name",
): PanelData {
    val sign = if (change >= 0) "+" else ""
    val percent = "$sign${"%.2f".format(Locale.US, changePercent)}%"
    val changeText = if (includeAbsoluteChange) {
        "$sign${formatNumber(change)}  ($percent)"
    } else {
        percent
    }
    return PanelData(
        label = label,
        title = title,
        subtitle = subtitle,
        price = formatPrice(price, currency),
        change = changeText,
        updatedAt = "更新 ${TIME_FORMAT.format(Date(updatedAtEpochSeconds * 1_000))}",
        points = points,
        isPositive = change >= 0,
        axisUnit = currency,
        xAxisLabels = xAxisLabels,
    )
}

private fun formatPrice(value: Double, currency: String): String = when (currency) {
    "JPY" -> "${NumberFormat.getNumberInstance(Locale.JAPAN).format(value)}円"
    "USD" -> "\$${NumberFormat.getNumberInstance(Locale.US).format(value)}"
    "PCT" -> "${NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(value)}%"
    else -> "${NumberFormat.getNumberInstance().format(value)} $currency"
}

private fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(value)

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.JAPAN)
private val POINT_DATE_FORMAT = SimpleDateFormat("yy/MM", Locale.JAPAN)
private val FUND_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
