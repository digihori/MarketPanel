package com.digihori.marketpanel.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ApiQuoteResponse(
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
data class ApiPricePoint(
    val timestamp: Long,
    val value: Double,
)

@Serializable
data class ApiSeriesResponse(
    val symbol: String,
    val range: String,
    val interval: String,
    val points: List<ApiPricePoint>,
)

@Serializable
data class ApiMarketResponse(
    val id: String,
    val quote: ApiQuoteResponse,
    val points: List<ApiPricePoint>,
)

@Serializable
data class ApiUsageResponse(
    val dailyUsage: Int,
    val dailyLimit: Int,
    val currentUsage: Int,
    val minuteLimit: Int,
)
