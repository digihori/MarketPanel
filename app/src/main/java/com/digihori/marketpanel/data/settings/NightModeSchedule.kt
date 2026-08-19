package com.digihori.marketpanel.data.settings

fun isNightModeScheduled(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
    require(nowMinutes in 0..1439)
    require(startMinutes in 0..1439)
    require(endMinutes in 0..1439)
    if (startMinutes == endMinutes) return false
    return if (startMinutes < endMinutes) {
        nowMinutes in startMinutes until endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
}
