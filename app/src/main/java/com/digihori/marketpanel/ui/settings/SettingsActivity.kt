package com.digihori.marketpanel.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.digihori.marketpanel.R
import com.digihori.marketpanel.MarketPanelApplication
import com.digihori.marketpanel.data.settings.AssetType
import com.digihori.marketpanel.data.settings.InstrumentDataSource
import com.digihori.marketpanel.data.settings.MarketPanelSettings
import com.digihori.marketpanel.data.settings.SettingsStore
import com.digihori.marketpanel.data.settings.WatchInstrument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: SettingsStore
    private lateinit var intervalSpinner: Spinner
    private lateinit var updateSpinner: Spinner
    private lateinit var instrumentList: LinearLayout
    private var loadedSettings = MarketPanelSettings()
    private val instruments = mutableListOf<WatchInstrument>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SettingsStore(applicationContext)
        intervalSpinner = findViewById(R.id.rotationIntervalSpinner)
        updateSpinner = findViewById(R.id.updateIntervalSpinner)
        instrumentList = findViewById(R.id.instrumentList)
        intervalSpinner.adapter = spinnerAdapter(INTERVAL_LABELS)
        updateSpinner.adapter = spinnerAdapter(UPDATE_INTERVAL_LABELS)

        scope.launch { showSettings(store.load()) }
        findViewById<Button>(R.id.addInstrumentButton).setOnClickListener { showInstrumentDialog() }
        findViewById<Button>(R.id.apiLogButton).setOnClickListener { showApiLog() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveAndClose() }
    }

    private fun showApiLog() {
        val log = (application as MarketPanelApplication).container.apiCreditLog
        AlertDialog.Builder(this)
            .setTitle("APIクレジットログ")
            .setMessage(log.displayText())
            .setPositiveButton("閉じる", null)
            .setNegativeButton("ログを消去") { _, _ -> log.clear() }
            .show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showSettings(settings: MarketPanelSettings) {
        loadedSettings = settings
        instruments.clear()
        instruments += settings.instruments
        intervalSpinner.setSelection(INTERVAL_VALUES.indexOf(settings.rotationIntervalMillis).coerceAtLeast(0))
        updateSpinner.setSelection(UPDATE_INTERVAL_VALUES.indexOf(settings.updateIntervalMillis).coerceAtLeast(0))
        setChecked(R.id.autoStart, settings.autoStart)
        setChecked(R.id.keepScreenOn, settings.keepScreenOn)
        setChecked(R.id.fullscreen, settings.fullscreen)
        renderInstruments()
    }

    private fun renderInstruments() {
        instrumentList.removeAllViews()
        instruments.forEachIndexed { index, item -> instrumentList.addView(instrumentRow(item, index)) }
    }

    private fun instrumentRow(item: WatchInstrument, index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        val enabled = CheckBox(this).apply {
            isChecked = item.enabled
            setOnCheckedChangeListener { _, checked -> instruments[index] = item.copy(enabled = checked) }
        }
        val description = TextView(this).apply {
            text = "${item.displayName}\n${item.symbol}  •  ${item.assetType.label}  •  ${item.dataSource.label}"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 15f
        }
        row.addView(enabled, LinearLayout.LayoutParams(dp(48), dp(52)))
        row.addView(description, LinearLayout.LayoutParams(0, dp(58), 1f))
        row.addView(actionButton("↑") { move(index, -1) })
        row.addView(actionButton("↓") { move(index, 1) })
        row.addView(actionButton("編集") { showInstrumentDialog(index) })
        row.addView(actionButton("削除") {
            instruments.removeAt(index)
            renderInstruments()
        })
        return row
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(if (label.length > 1) 64 else 44), dp(44))
    }

    private fun move(index: Int, offset: Int) {
        val target = index + offset
        if (target !in instruments.indices) return
        val item = instruments.removeAt(index)
        instruments.add(target, item)
        renderInstruments()
    }

    private fun showInstrumentDialog(editIndex: Int? = null) {
        val current = editIndex?.let(instruments::get)
        val name = EditText(this).apply { hint = "表示名"; setText(current?.displayName.orEmpty()) }
        val symbol = EditText(this).apply {
            hint = "シンボル（例: AAPL）"
            setText(current?.symbol.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val type = Spinner(this).apply {
            adapter = spinnerAdapter(AssetType.entries.map { it.label })
            setSelection(current?.assetType?.ordinal ?: 0)
        }
        val source = Spinner(this).apply {
            adapter = spinnerAdapter(InstrumentDataSource.entries.map { it.label })
            setSelection(current?.dataSource?.ordinal ?: 0)
        }
        val enabled = CheckBox(this).apply { text = "表示する"; isChecked = current?.enabled ?: true }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(name); addView(symbol); addView(labeled("種別", type)); addView(labeled("データ供給元", source)); addView(enabled)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (current == null) "銘柄を追加" else "銘柄を編集")
            .setView(form)
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val symbolText = symbol.text.toString().trim().uppercase()
                val nameText = name.text.toString().trim()
                if (symbolText.isEmpty() || nameText.isEmpty()) return@setOnClickListener
                val updated = WatchInstrument(
                    id = current?.id ?: "custom_${UUID.randomUUID()}",
                    displayName = nameText,
                    symbol = symbolText,
                    assetType = AssetType.entries[type.selectedItemPosition],
                    dataSource = InstrumentDataSource.entries[source.selectedItemPosition],
                    enabled = enabled.isChecked,
                )
                if (editIndex == null) instruments += updated else instruments[editIndex] = updated
                renderInstruments()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun labeled(label: String, child: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@SettingsActivity).apply { text = label })
        addView(child)
    }

    private fun saveAndClose() {
        val enabledStocks = instruments.filter { it.enabled && it.assetType in setOf(AssetType.US_STOCK, AssetType.US_ETF) }
            .mapTo(mutableSetOf()) { it.symbol }
        val enabledMarkets = instruments.filter { it.enabled && it.assetType == AssetType.MARKET_INDEX }
            .mapTo(mutableSetOf()) { it.symbol }
        scope.launch {
            store.save(
                loadedSettings.copy(
                    rotationIntervalMillis = INTERVAL_VALUES[intervalSpinner.selectedItemPosition],
                    updateIntervalMillis = UPDATE_INTERVAL_VALUES[updateSpinner.selectedItemPosition],
                    enabledStocks = enabledStocks,
                    enabledMarkets = enabledMarkets,
                    autoStart = isChecked(R.id.autoStart),
                    keepScreenOn = isChecked(R.id.keepScreenOn),
                    fullscreen = isChecked(R.id.fullscreen),
                    instruments = instruments.toList(),
                ),
            )
            finish()
        }
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    private fun setChecked(id: Int, checked: Boolean) { findViewById<CheckBox>(id).isChecked = checked }
    private fun isChecked(id: Int): Boolean = findViewById<CheckBox>(id).isChecked
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val INTERVAL_LABELS = listOf("5秒（デバッグ用）", "30秒", "60秒", "3分", "5分")
        val INTERVAL_VALUES = listOf(5_000L, 30_000L, 60_000L, 180_000L, 300_000L)
        val UPDATE_INTERVAL_LABELS = listOf("1分", "5分", "15分", "30分")
        val UPDATE_INTERVAL_VALUES = listOf(60_000L, 300_000L, 900_000L, 1_800_000L)
    }
}
