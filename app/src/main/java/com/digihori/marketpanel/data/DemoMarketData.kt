package com.digihori.marketpanel.data

import com.digihori.marketpanel.domain.model.PanelData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DemoMarketData {
    data class StockPanels(
        val id: String,
        val main: PanelData,
        val intraday: PanelData,
    )

    val stocks = listOf(
        stock("IBM", "IBM  International Business Machines", "NYSE • 米国株", "$286.40", "+$1.72  (+0.60%)", true, rising()),
        stock("MCD", "MCD  McDonald’s", "NYSE • 米国株", "$312.18", "-$0.84  (-0.27%)", false, falling()),
        stock("SPCX", "SPCX  SpaceX", "NASDAQ • 米国株", "$148.25", "+$2.31  (+1.58%)", true, rising()),
        stock("JEPQ", "JEPQ  JPMorgan Nasdaq Equity Premium Income", "NASDAQ • 米国ETF", "$59.82", "+$0.18  (+0.30%)", true, rising()),
        stock("QQQI", "QQQI  NEOS Nasdaq-100 High Income", "NASDAQ • 米国ETF", "$54.16", "-$0.11  (-0.20%)", false, falling()),
        stock("563A", "563A  Global X NASDAQ100 Covered Call", "JPX • 国内ETF", "1,084円", "+9円  (+0.84%)", true, rising()),
    )

    val funds = listOf(
        fund("EMAXIS_ALL_COUNTRY", "eMAXIS Slim 全世界株式", "オール・カントリー • 基準価額", "31,842円", "+226円  (+0.71%)", true, rising()),
        fund("IFREE_FANG_PLUS", "iFreeNEXT FANG+インデックス", "国内投信 • 基準価額", "91,805円", "+1,046円  (+1.15%)", true, listOf(68f, 73f, 70f, 78f, 75f, 83f, 80f, 88f, 85f, 93f, 91f, 100f)),
        fund("SBI_S_SCHD_4X", "SBI・S・米国高配当株式", "年4回決算型 • 基準価額", "12,486円", "-38円  (-0.30%)", false, listOf(91f, 94f, 93f, 96f, 95f, 98f, 97f, 101f, 99f, 102f, 101f, 100f)),
    )

    data class MarketPanel(val id: String, val panel: PanelData)

    val markets = listOf(
        indicator(
            id = "NIKKEI225",
            title = "日経平均",
            subtitle = "NIKKEI 225",
            price = "42,580.25",
            change = "-218.40  (-0.51%)",
            isPositive = false,
            points = listOf(70f, 66f, 68f, 61f, 64f, 58f, 55f, 57f, 49f, 52f, 45f, 42f),
        ),
        indicator(
            id = "SP500",
            title = "S&P 500",
            subtitle = "SPX",
            price = "6,449.80",
            change = "+11.35  (+0.18%)",
            isPositive = true,
            points = listOf(48f, 51f, 49f, 54f, 52f, 58f, 61f, 59f, 65f, 64f, 69f, 72f),
        ),
        indicator(
            id = "DOW30",
            title = "NYダウ参考（DIA）",
            subtitle = "DIA",
            price = "445.72",
            change = "+1.20  (+0.27%)",
            isPositive = true,
            points = listOf(61f, 63f, 62f, 66f, 65f, 68f, 70f, 69f, 72f, 74f, 73f, 76f),
        ),
        indicator(
            id = "NASDAQ100",
            title = "NASDAQ-100参考（QQQ）",
            subtitle = "QQQ",
            price = "575.30",
            change = "+2.48  (+0.43%)",
            isPositive = true,
            points = listOf(50f, 54f, 52f, 57f, 60f, 59f, 64f, 62f, 68f, 71f, 70f, 75f),
        ),
        indicator(
            id = "VIX",
            title = "VIX短期先物参考（VIXY）",
            subtitle = "VIXY",
            price = "32.18",
            change = "-0.84  (-2.54%)",
            isPositive = false,
            points = listOf(72f, 68f, 70f, 64f, 66f, 60f, 63f, 57f, 59f, 54f, 56f, 51f),
        ),
        indicator(
            id = "USDJPY",
            title = "米ドル／円",
            subtitle = "USD/JPY",
            price = "147.32",
            change = "+0.41  (+0.28%)",
            isPositive = true,
            points = listOf(45f, 44f, 48f, 47f, 51f, 49f, 54f, 53f, 57f, 56f, 60f, 62f),
        ),
    )

    private fun stock(
        id: String,
        title: String,
        market: String,
        price: String,
        change: String,
        isPositive: Boolean,
        longTerm: List<Float>,
        intraday: List<Float> = longTerm,
    ): StockPanels = StockPanels(
        id = id,
        main = PanelData(
            label = "MAIN  •  WATCHLIST",
            title = title,
            subtitle = market,
            price = price,
            change = change,
            updatedAt = "更新 15:00",
            points = scaleToCurrentPrice(longTerm, price),
            isPositive = isPositive,
            axisUnit = if (price.startsWith("$")) "USD" else "JPY",
            xAxisLabels = monthLabels(longTerm.size),
        ),
        intraday = PanelData(
            label = "SUB1  •  本日・5分足",
            title = title,
            subtitle = "MAINと同期",
            price = price,
            change = change.substringAfterLast("  ", change),
            updatedAt = "更新 15:00",
            points = scaleToCurrentPrice(intraday, price),
            isPositive = isPositive,
            axisUnit = if (price.startsWith("$")) "USD" else "JPY",
            xAxisLabels = monthLabels(intraday.size),
        ),
    )

    private fun fund(
        id: String,
        title: String,
        subtitle: String,
        price: String,
        change: String,
        isPositive: Boolean,
        history: List<Float>,
    ) = MarketPanel(
        id = id,
        panel = PanelData(
            label = "SUB1  •  FUND NAV",
            title = title,
            subtitle = subtitle,
            price = price,
            change = change,
            updatedAt = "基準日 2026/08/14 • DEMO",
            points = scaleToCurrentPrice(history, price),
            isPositive = isPositive,
            axisUnit = "JPY",
            xAxisLabels = monthLabels(history.size),
        ),
    )

    private fun rising() = listOf(82f, 86f, 84f, 89f, 87f, 92f, 90f, 94f, 93f, 97f, 96f, 100f)
    private fun falling() = listOf(112f, 109f, 111f, 107f, 108f, 104f, 106f, 102f, 103f, 99f, 101f, 100f)

    private fun scaleToCurrentPrice(points: List<Float>, displayPrice: String): List<Float> {
        val currentPrice = displayPrice.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: return points
        val last = points.lastOrNull()?.takeIf { it != 0f } ?: return points
        return points.map { it / last * currentPrice }
    }

    private fun indicator(
        id: String,
        title: String,
        subtitle: String,
        price: String,
        change: String,
        isPositive: Boolean,
        points: List<Float>,
    ) = MarketPanel(
        id = id,
        panel = PanelData(
            label = "SUB2  •  MARKET",
            title = title,
            subtitle = subtitle,
            price = price,
            change = change,
            updatedAt = "更新 15:00",
            points = points,
            isPositive = isPositive,
            axisUnit = if (subtitle == "USD/JPY") "RATE" else "INDEX",
            xAxisLabels = monthLabels(points.size),
        ),
    )

    private fun monthLabels(size: Int): List<String> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -(size - 1))
        val formatter = SimpleDateFormat("yy/MM", Locale.JAPAN)
        return List(size) {
            formatter.format(calendar.time).also { calendar.add(Calendar.MONTH, 1) }
        }
    }
}
