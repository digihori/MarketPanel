package com.digihori.marketpanel

import android.content.Context
import androidx.room.Room
import com.digihori.marketpanel.data.local.MarketCache
import com.digihori.marketpanel.data.local.MarketPanelDatabase
import com.digihori.marketpanel.data.provider.DemoStockDataProvider
import com.digihori.marketpanel.data.provider.RemoteStockDataProvider
import com.digihori.marketpanel.data.remote.MarketPanelApi
import com.digihori.marketpanel.data.remote.ApiCreditPacer
import com.digihori.marketpanel.data.remote.ApiCreditLog
import com.digihori.marketpanel.data.remote.ApiCreditLoggingInterceptor
import com.digihori.marketpanel.data.remote.ApiRetryPolicy
import com.digihori.marketpanel.data.repository.MarketRepository
import com.digihori.marketpanel.data.repository.ApiUsageRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val database = Room.databaseBuilder(
        context,
        MarketPanelDatabase::class.java,
        "market-panel.db",
    ).build()
    private val cache = MarketCache(database.marketCacheDao(), json)
    private val apiCreditPacer = ApiCreditPacer()
    val apiCreditLog = ApiCreditLog(context)
    private val apiRetryPolicy = ApiRetryPolicy(context, apiCreditLog)
    private val api: MarketPanelApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.MARKET_API_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(ApiCreditLoggingInterceptor(apiCreditLog))
                    .build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MarketPanelApi::class.java)
    }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            provider = if (BuildConfig.USE_DEMO_DATA) {
                DemoStockDataProvider()
            } else {
                RemoteStockDataProvider(api, apiCreditPacer, apiRetryPolicy)
            },
            cache = cache,
        )
    }

    val apiUsageRepository: ApiUsageRepository by lazy {
        ApiUsageRepository(api, BuildConfig.USE_DEMO_DATA, apiCreditPacer)
    }
}
