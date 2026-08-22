package com.digihori.marketpanel.data.settings

enum class DataRefreshMode(val label: String, val checkIntervalMillis: Long) {
    CLOSE_ONLY("終値中心（推奨）", 5 * 60_000L),
    JAPAN_INTRADAY("日本株のみ日中更新", 15 * 60_000L),
    FOUR_HOURS("すべて4時間更新", 30 * 60_000L),
    DEBUG("デバッグ（1分確認）", 60_000L),
}
