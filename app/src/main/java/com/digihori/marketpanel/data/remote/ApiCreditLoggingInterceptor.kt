package com.digihori.marketpanel.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class ApiCreditLoggingInterceptor(
    private val log: ApiCreditLog,
    private val now: () -> Long = System::currentTimeMillis,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        return try {
            chain.proceed(request).also { response ->
                val cache = response.header("x-marketpanel-cache") ?: "NO-CACHE"
                log.append(
                    ApiCreditLogEntry(
                        timestamp = now(),
                        method = request.method,
                        path = path,
                        status = response.code,
                        cache = cache,
                        estimatedCredits = estimatedCredits(path, cache, response.isSuccessful),
                    ),
                )
            }
        } catch (error: Exception) {
            log.append(
                ApiCreditLogEntry(now(), request.method, path, 0, "NETWORK-ERROR", endpointWeight(path)),
            )
            throw error
        }
    }

    private fun estimatedCredits(path: String, cache: String, success: Boolean): Int = when {
        cache == "HIT" || cache == "KV" -> 0
        cache == "MISS" || !success -> endpointWeight(path)
        else -> 0
    }

    private fun endpointWeight(path: String): Int = when {
        path.startsWith("/v1/markets/") -> 2
        path.startsWith("/v1/quotes/") || path.startsWith("/v1/charts/") || path == "/v1/usage" -> 1
        else -> 0
    }
}
