package com.digihori.marketpanel.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MarketPanelApi {
    @GET("v1/usage")
    suspend fun getUsage(): ApiUsageResponse

    @GET("v1/quotes/{symbol}")
    suspend fun getQuote(@Path("symbol") symbol: String): ApiQuoteResponse

    @GET("v1/charts/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String,
    ): ApiSeriesResponse

    @GET("v1/markets/{id}?history=v4")
    suspend fun getMarket(@Path("id") id: String): ApiMarketResponse

    @GET("v1/funds/{id}?history=v2")
    suspend fun getFund(@Path("id") id: String): ApiMarketResponse

    @GET("v1/jp-stocks/{symbol}?history=v4")
    suspend fun getJapanStock(@Path("symbol") symbol: String): ApiMarketResponse
}
