package com.digihori.marketpanel.data.repository

import com.digihori.marketpanel.data.remote.MarketPanelApi
import com.digihori.marketpanel.data.remote.ApiCreditPacer

class ApiUsageRepository(
    private val api: MarketPanelApi,
    private val demoMode: Boolean,
    private val creditPacer: ApiCreditPacer,
) {
    suspend fun displayText(): String = if (demoMode) {
        "API DEMO"
    } else {
        runCatching {
            creditPacer.acquire(1)
            api.getUsage()
        }
            .map { "API ${it.dailyUsage} / ${it.dailyLimit}" }
            .getOrDefault("API -- / 800")
    }
}
