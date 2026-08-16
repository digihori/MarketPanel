package com.digihori.marketpanel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "market_cache")
data class MarketCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val fetchedAtEpochMillis: Long,
)
