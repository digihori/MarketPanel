package com.digihori.marketpanel.data.remote

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApiCreditLogEntry(
    val timestamp: Long,
    val method: String,
    val path: String,
    val status: Int,
    val cache: String,
    val estimatedCredits: Int,
)

class ApiCreditLog(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun append(entry: ApiCreditLogEntry) {
        val line = listOf(
            entry.timestamp,
            entry.method,
            entry.path,
            entry.status,
            entry.cache,
            entry.estimatedCredits,
        ).joinToString("\t")
        val lines = (preferences.getString(KEY_ENTRIES, "").orEmpty().lineSequence()
            .filter { it.isNotBlank() } + line)
            .toList()
            .takeLast(MAX_ENTRIES)
        preferences.edit().putString(KEY_ENTRIES, lines.joinToString("\n")).apply()
    }

    @Synchronized
    fun displayText(): String {
        val entries = entries()
        if (entries.isEmpty()) return "API呼び出しログはまだありません。"
        val total = entries.sumOf { it.estimatedCredits }
        val formatter = SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN)
        val details = entries.asReversed().joinToString("\n") { entry ->
            val result = if (entry.status in 200..299) entry.status.toString() else "ERROR ${entry.status}"
            "${formatter.format(Date(entry.timestamp))}  ${entry.path}  $result  ${entry.cache}  推定${entry.estimatedCredits}cr"
        }
        return "端末からの呼び出し ${entries.size}件 / 推定消費 $total cr\n" +
            "（MISS・エラーを推定消費、HIT・KVは0）\n\n$details"
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun entries(): List<ApiCreditLogEntry> = preferences.getString(KEY_ENTRIES, "")
        .orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val columns = line.split('\t')
            if (columns.size != 6) return@mapNotNull null
            ApiCreditLogEntry(
                timestamp = columns[0].toLongOrNull() ?: return@mapNotNull null,
                method = columns[1],
                path = columns[2],
                status = columns[3].toIntOrNull() ?: return@mapNotNull null,
                cache = columns[4],
                estimatedCredits = columns[5].toIntOrNull() ?: return@mapNotNull null,
            )
        }
        .toList()

    private companion object {
        const val PREFERENCES_NAME = "api_credit_log"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 500
    }
}
