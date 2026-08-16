package com.digihori.marketpanel.data.provider

import com.digihori.marketpanel.data.remote.ApiPricePoint
import com.digihori.marketpanel.data.remote.ApiQuoteResponse
import com.digihori.marketpanel.data.remote.ApiCreditPacer
import com.digihori.marketpanel.data.remote.ApiRetryPolicy
import com.digihori.marketpanel.data.remote.MarketPanelApi
import com.digihori.marketpanel.domain.model.MarketSnapshot
import com.digihori.marketpanel.domain.model.PricePoint
import com.digihori.marketpanel.domain.model.Quote
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class RemoteStockDataProvider(
    private val api: MarketPanelApi,
    private val creditPacer: ApiCreditPacer,
    private val retryPolicy: ApiRetryPolicy,
) : StockDataProvider {
    override suspend fun getQuote(symbol: String): Quote {
        return withRetryPolicy("quote:$symbol", "/v1/quotes/$symbol", credits = 1) {
            api.getQuote(symbol).toDomain()
        }
    }

    override suspend fun getLongTermChart(symbol: String, range: String): List<PricePoint> {
        return withRetryPolicy("chart:$symbol:$range", "/v1/charts/$symbol", credits = 1) {
            api.getChart(symbol, range, "1wk").points.map { it.toDomain() }
        }
    }

    override suspend fun getMarketIndicator(id: String): MarketSnapshot {
        // The Worker obtains a quote and a daily series for an indicator.
        val response = withRetryPolicy("market:$id", "/v1/markets/$id", credits = 2) { api.getMarket(id) }
        return MarketSnapshot(
            id = response.id,
            quote = response.quote.toDomain(),
            series = response.points.map { it.toDomain() },
        )
    }

    private suspend fun <T> withRetryPolicy(
        key: String,
        path: String,
        credits: Int,
        request: suspend () -> T,
    ): T {
        retryPolicy.requireAllowed(key, path)
        var retried = false
        while (true) {
            creditPacer.acquire(credits)
            try {
                return request().also { retryPolicy.recordSuccess(key) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val retryDelay = retryDelayMillis(error)
                if (!retried && retryDelay != null) {
                    retried = true
                    delay(retryDelay)
                    continue
                }
                retryPolicy.recordFailure(key, error)
                throw error
            }
        }
    }

    private fun retryDelayMillis(error: Throwable): Long? = when {
        error is HttpException && error.code() == 429 -> RATE_LIMIT_RETRY_MILLIS
        error is HttpException && error.code() in 500..599 -> TRANSIENT_RETRY_MILLIS
        error is IOException -> TRANSIENT_RETRY_MILLIS
        else -> null
    }

    private fun ApiQuoteResponse.toDomain() = Quote(
        symbol, name, exchange, currency, price, change, changePercent, updatedAt,
    )

    private fun ApiPricePoint.toDomain() = PricePoint(timestamp, value)

    private companion object {
        const val RATE_LIMIT_RETRY_MILLIS = 60_000L
        const val TRANSIENT_RETRY_MILLIS = 15_000L
    }
}
