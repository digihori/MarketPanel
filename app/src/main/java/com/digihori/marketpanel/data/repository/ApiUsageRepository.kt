package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.remote.MarketPanelApi
import com.digihori.marketpanel.data.remote.ApiCreditPacer

class ApiUsageRepository(
    private val api: MarketPanelApi,
    private val demoMode: Boolean,
    private val creditPacer: ApiCreditPacer,
) {
    private var cachedText: String? = null
    private var cachedAtMillis: Long = 0L

    suspend fun displayText(): String = if (demoMode) {
        "API DEMO"
    } else if (cachedText != null && System.currentTimeMillis() - cachedAtMillis < CACHE_MILLIS) {
        cachedText!!
    } else {
        runCatching {
            creditPacer.acquire(1)
            api.getUsage()
        }
            .map { "API ${it.dailyUsage} / ${it.dailyLimit}" }
            .getOrDefault("API -- / 800")
            .also {
                cachedText = it
                cachedAtMillis = System.currentTimeMillis()
            }
    }

    private companion object {
        const val CACHE_MILLIS = 4 * 60 * 60 * 1_000L
    }
}
