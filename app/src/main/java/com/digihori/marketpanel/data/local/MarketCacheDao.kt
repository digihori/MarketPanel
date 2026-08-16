package com.digihori.marketpanel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MarketCacheDao {
    @Query("SELECT * FROM market_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun find(key: String): MarketCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarketCacheEntity)

    @Query("DELETE FROM market_cache WHERE fetchedAtEpochMillis < :oldestAllowedEpochMillis")
    suspend fun deleteOlderThan(oldestAllowedEpochMillis: Long)
}
