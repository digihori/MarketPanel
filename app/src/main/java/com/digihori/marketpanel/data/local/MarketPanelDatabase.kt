package com.digihori.marketpanel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MarketCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MarketPanelDatabase : RoomDatabase() {
    abstract fun marketCacheDao(): MarketCacheDao
}
