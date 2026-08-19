package com.digihori.marketpanel.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digihori.marketpanel.MarketPanelApplication
import com.digihori.marketpanel.R
import com.digihori.marketpanel.data.settings.AssetType
import com.digihori.marketpanel.data.settings.DefaultWatchInstruments
import com.digihori.marketpanel.data.settings.InstrumentDataSource
import com.digihori.marketpanel.data.settings.MarketPanelSettings
import com.digihori.marketpanel.data.settings.SettingsBackupJson
import com.digihori.marketpanel.data.settings.SettingsStore
import com.digihori.marketpanel.data.settings.WatchInstrument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: SettingsStore
    private lateinit var intervalSpinner: Spinner
    private lateinit var updateSpinner: Spinner
    private lateinit var sectionTitle: TextView
    private lateinit var nightStartButton: Button
    private lateinit var nightEndButton: Button
    private lateinit var adapter: InstrumentAdapter
    private var loadedSettings = MarketPanelSettings()
    private val instruments = mutableListOf<WatchInstrument>()
    private var selectedPanel = SettingsPanel.MAIN
    private var nightStartMinutes = 23 * 60
    private var nightEndMinutes = 6 * 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SettingsStore(applicationContext)
        intervalSpinner = findViewById(R.id.rotationIntervalSpinner)
        updateSpinner = findViewById(R.id.updateIntervalSpinner)
        sectionTitle = findViewById(R.id.instrumentSectionTitle)
        nightStartButton = findViewById(R.id.nightStartButton)
        nightEndButton = findViewById(R.id.nightEndButton)
        intervalSpinner.adapter = spinnerAdapter(INTERVAL_LABELS)
        updateSpinner.adapter = spinnerAdapter(UPDATE_INTERVAL_LABELS)

        adapter = InstrumentAdapter(
            onEdit = ::showEditDialog,
            onEnabledChanged = ::setEnabled,
            onOrderChanged = ::applyPanelOrder,
        )
        findViewById<RecyclerView>(R.id.instrumentList).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
            this@SettingsActivity.adapter.attachTo(this)
        }

        findViewById<Button>(R.id.mainTab).setOnClickListener { selectPanel(SettingsPanel.MAIN) }
        findViewById<Button>(R.id.main2Tab).setOnClickListener { selectPanel(SettingsPanel.MAIN2) }
        findViewById<Button>(R.id.sub1Tab).setOnClickListener { selectPanel(SettingsPanel.SUB1) }
        findViewById<Button>(R.id.sub2Tab).setOnClickListener { selectPanel(SettingsPanel.SUB2) }
        findViewById<Button>(R.id.addInstrumentButton).setOnClickListener { showAddDialog() }
        findViewById<Button>(R.id.apiLogButton).setOnClickListener { showApiLog() }
        findViewById<Button>(R.id.backupButton).setOnClickListener { createBackupDocument() }
        findViewById<Button>(R.id.restoreButton).setOnClickListener { openBackupDocument() }
        nightStartButton.setOnClickListener { showTimePicker(nightStartMinutes) { nightStartMinutes = it; updateNightTimeButtons() } }
        nightEndButton.setOnClickListener { showTimePicker(nightEndMinutes) { nightEndMinutes = it; updateNightTimeButtons() } }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveAndClose() }

        selectPanel(SettingsPanel.MAIN)
        scope.launch { showSettings(store.load()) }
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
        setChecked(R.id.nightModeEnabled, settings.nightModeEnabled)
        nightStartMinutes = settings.nightStartMinutes
        nightEndMinutes = settings.nightEndMinutes
        updateNightTimeButtons()
        renderInstruments()
    }

    private fun selectPanel(panel: SettingsPanel) {
        selectedPanel = panel
        sectionTitle.text = panel.title
        listOf(
            R.id.mainTab to SettingsPanel.MAIN,
            R.id.main2Tab to SettingsPanel.MAIN2,
            R.id.sub1Tab to SettingsPanel.SUB1,
            R.id.sub2Tab to SettingsPanel.SUB2,
        ).forEach { (id, item) ->
            findViewById<Button>(id).apply {
                setTextColor(color(if (item == panel) R.color.text_primary else R.color.text_secondary))
                setBackgroundColor(color(if (item == panel) R.color.chart_primary else R.color.panel_surface))
            }
        }
        renderInstruments()
    }

    private fun renderInstruments() {
        if (::adapter.isInitialized) adapter.submit(instruments.filter(selectedPanel::contains))
    }

    private fun setEnabled(item: WatchInstrument, enabled: Boolean) {
        val index = instruments.indexOfFirst { it.id == item.id }
        if (index >= 0) instruments[index] = instruments[index].copy(enabled = enabled)
    }

    private fun applyPanelOrder(ordered: List<WatchInstrument>) {
        val positions = instruments.indices.filter { selectedPanel.contains(instruments[it]) }
        positions.forEachIndexed { orderIndex, sourceIndex -> instruments[sourceIndex] = ordered[orderIndex] }
    }

    private fun showAddDialog() {
        when (selectedPanel) {
            SettingsPanel.MAIN -> showMainEditor(null)
            SettingsPanel.MAIN2 -> showJapanEditor(null)
            SettingsPanel.SUB1 -> showFundEditor(null)
            SettingsPanel.SUB2 -> showMarketPicker()
        }
    }

    private fun showEditDialog(item: WatchInstrument) {
        when (SettingsPanel.of(item)) {
            SettingsPanel.MAIN -> showMainEditor(item)
            SettingsPanel.MAIN2 -> showJapanEditor(item)
            SettingsPanel.SUB1 -> showFundEditor(item)
            SettingsPanel.SUB2 -> showMarketEditor(item)
        }
    }

    private fun showMainEditor(current: WatchInstrument?) {
        val symbol = symbolInput(current?.symbol)
        val name = textInput("表示名（未入力ならシンボルを使用）", current?.displayName)
        val type = Spinner(this).apply {
            adapter = spinnerAdapter(listOf("米国株", "米国ETF"))
            setSelection(if (current?.assetType == AssetType.US_ETF) 1 else 0)
        }
        showEditorDialog(
            title = if (current == null) "米国株・ETFを追加" else "米国株・ETFを編集",
            fields = listOf(labeled("シンボル", symbol), labeled("表示名（任意）", name), labeled("種別", type)),
            current = current,
        ) {
            val symbolText = symbol.text.toString().trim().uppercase(Locale.US)
            if (symbolText.isBlank()) return@showEditorDialog null
            WatchInstrument(
                id = current?.id ?: "custom_${UUID.randomUUID()}",
                displayName = name.text.toString().trim().ifBlank { symbolText },
                symbol = symbolText,
                assetType = if (type.selectedItemPosition == 1) AssetType.US_ETF else AssetType.US_STOCK,
                dataSource = InstrumentDataSource.TWELVE_DATA,
                enabled = current?.enabled ?: true,
            )
        }
    }

    private fun showJapanEditor(current: WatchInstrument?) {
        val symbol = symbolInput(current?.symbol)
        val name = textInput("表示名（未入力なら取得時に補完）", current?.displayName)
        val type = Spinner(this).apply {
            adapter = spinnerAdapter(listOf("日本株", "国内ETF"))
            setSelection(if (current?.assetType == AssetType.JAPAN_ETF) 1 else 0)
        }
        showEditorDialog(
            title = if (current == null) "日本株・国内ETFを追加" else "日本株・国内ETFを編集",
            fields = listOf(
                labeled("証券コード（例: 7203、1489）", symbol),
                labeled("表示名（任意）", name),
                labeled("種別", type),
            ),
            current = current,
        ) {
            val symbolText = symbol.text.toString().trim().uppercase(Locale.US).removeSuffix(".T")
            if (!JAPAN_SYMBOL.matches(symbolText)) return@showEditorDialog null
            WatchInstrument(
                id = current?.id ?: "custom_${UUID.randomUUID()}",
                displayName = name.text.toString().trim().ifBlank { symbolText },
                symbol = symbolText,
                assetType = if (type.selectedItemPosition == 1) AssetType.JAPAN_ETF else AssetType.JAPAN_STOCK,
                dataSource = InstrumentDataSource.YAHOO_JAPAN,
                enabled = current?.enabled ?: true,
            )
        }
    }

    private fun showFundEditor(current: WatchInstrument?) {
        val presets = DefaultWatchInstruments.items.filter { it.assetType == AssetType.FUND_REFERENCE }
        val preset = Spinner(this).apply {
            adapter = spinnerAdapter(listOf("自由入力") + presets.map { "${it.displayName}（${it.symbol}）" })
        }
        val name = textInput("投信・参考対象の表示名", current?.displayName)
        val symbol = symbolInput(current?.symbol)
        if (current == null) {
            preset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position > 0) presets[position - 1].let {
                        name.setText(it.displayName)
                        symbol.setText(it.symbol)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        showEditorDialog(
            title = if (current == null) "国内投信を追加" else "国内投信を編集",
            fields = buildList {
                if (current == null) add(labeled("プリセット", preset))
                add(labeled("表示名", name))
                add(labeled("投信ID・投信コード・参照シンボル", symbol))
                add(note("プリセットと8桁の投信コードは実基準価額を表示します。それ以外は参照ETF・市場の値動きを表示します。"))
            },
            current = current,
        ) {
            val symbolText = symbol.text.toString().trim().uppercase(Locale.US)
            val nameText = name.text.toString().trim()
            if (symbolText.isBlank() || nameText.isBlank()) return@showEditorDialog null
            val marketReference = symbolText in MARKET_REFERENCE_IDS
            val directFund = presets.any { it.symbol == symbolText } || FUND_CODE.matches(symbolText)
            WatchInstrument(
                id = current?.id ?: "custom_${UUID.randomUUID()}",
                displayName = nameText,
                symbol = symbolText,
                assetType = AssetType.FUND_REFERENCE,
                dataSource = when {
                    directFund -> InstrumentDataSource.YAHOO_FUND
                    marketReference -> InstrumentDataSource.TWELVE_DATA
                    else -> InstrumentDataSource.REFERENCE_USD_JPY
                },
                enabled = current?.enabled ?: true,
            )
        }
    }

    private fun showMarketPicker() {
        val registeredIds = instruments.filter(SettingsPanel.SUB2::contains).mapTo(mutableSetOf()) { it.id }
        val choices = DefaultWatchInstruments.items.filter {
            it.assetType == AssetType.MARKET_INDEX && it.id !in registeredIds
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "追加できる指標はすべて登録済みです", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(choices.size)
        AlertDialog.Builder(this)
            .setTitle("市場指標・為替を追加")
            .setMultiChoiceItems(choices.map { "${it.displayName}\n${it.symbol}" }.toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("追加") { _, _ ->
                choices.forEachIndexed { index, item -> if (checked[index]) instruments += item }
                renderInstruments()
            }
            .show()
    }

    private fun showMarketEditor(current: WatchInstrument) {
        val name = textInput("表示名", current.displayName)
        showEditorDialog(
            title = "市場指標を編集",
            fields = listOf(
                labeled("表示名", name),
                note("内部ID: ${current.symbol}\n取得先はWorkerの対応済み定義を使用します。"),
            ),
            current = current,
        ) {
            val nameText = name.text.toString().trim()
            if (nameText.isBlank()) null else current.copy(displayName = nameText)
        }
    }

    private fun showEditorDialog(
        title: String,
        fields: List<View>,
        current: WatchInstrument?,
        createValue: () -> WatchInstrument?,
    ) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            fields.forEach(::addView)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("保存", null)
        if (current != null) builder.setNeutralButton("削除", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = createValue()
                if (updated == null) {
                    Toast.makeText(this, "必須項目を入力してください", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isDuplicate(updated, current?.id)) {
                    Toast.makeText(this, "同じシンボルがすでに登録されています", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val index = current?.let { value -> instruments.indexOfFirst { it.id == value.id } } ?: -1
                if (index >= 0) instruments[index] = updated else instruments += updated
                renderInstruments()
                dialog.dismiss()
            }
            if (current != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("「${current.displayName}」を削除しますか？")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("削除") { _, _ ->
                        instruments.removeAll { it.id == current.id }
                        renderInstruments()
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        dialog.show()
    }

    private fun isDuplicate(candidate: WatchInstrument, ignoredId: String?): Boolean = instruments.any {
        it.id != ignoredId && SettingsPanel.of(it) == SettingsPanel.of(candidate) && it.symbol == candidate.symbol
    }

    private fun createBackupDocument() {
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "MarketPanel-$date.json")
            },
            REQUEST_CREATE_BACKUP,
        )
    }

    private fun openBackupDocument() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            REQUEST_OPEN_BACKUP,
        )
    }

    @Deprecated("Uses the platform document picker for broad Fire OS compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_CREATE_BACKUP -> writeBackup(uri)
            REQUEST_OPEN_BACKUP -> restoreBackup(uri)
        }
    }

    private fun writeBackup(uri: Uri) {
        val settings = currentSettings()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(SettingsBackupJson.encode(settings))
                    } ?: error("保存先を開けません")
                }
            }.onSuccess {
                Toast.makeText(this@SettingsActivity, "バックアップを保存しました", Toast.LENGTH_LONG).show()
            }.onFailure(::showFileError)
        }
    }

    private fun restoreBackup(uri: Uri) {
        scope.launch {
            runCatching {
                val restored = withContext(Dispatchers.IO) {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("ファイルを開けません")
                    SettingsBackupJson.decode(text)
                }
                store.save(restored)
                restored
            }.onSuccess {
                showSettings(it)
                Toast.makeText(this@SettingsActivity, "設定を復元しました", Toast.LENGTH_LONG).show()
            }.onFailure(::showFileError)
        }
    }

    private fun showFileError(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("ファイルを処理できませんでした")
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showApiLog() {
        val container = (application as MarketPanelApplication).container
        val log = container.apiCreditLog
        val logText = log.displayText()
        val content = TextView(this).apply {
            text = logText
            setTextIsSelectable(true)
            setTextColor(color(R.color.text_primary))
            textSize = 12f
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("APIクレジットログ")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("共有") { _, _ -> shareApiLog(logText) }
            .setNegativeButton("ログを消去") { _, _ -> log.clear() }
            .setNeutralButton("取得停止を解除") { _, _ -> container.apiRetryPolicy.clearAll() }
            .show()
    }

    private fun shareApiLog(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MarketPanel APIログ")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "APIログを共有"))
    }

    private fun currentSettings(): MarketPanelSettings {
        val enabledStocks = instruments.filter { it.enabled && it.assetType in MAIN_TYPES }.mapTo(mutableSetOf()) { it.symbol }
        val enabledFunds = instruments.filter { it.enabled && it.assetType == AssetType.FUND_REFERENCE }.mapTo(mutableSetOf()) { it.id }
        val enabledMarkets = instruments.filter { it.enabled && it.assetType == AssetType.MARKET_INDEX }.mapTo(mutableSetOf()) { it.symbol }
        return loadedSettings.copy(
            rotationIntervalMillis = INTERVAL_VALUES[intervalSpinner.selectedItemPosition],
            updateIntervalMillis = UPDATE_INTERVAL_VALUES[updateSpinner.selectedItemPosition],
            enabledStocks = enabledStocks,
            enabledFunds = enabledFunds,
            enabledMarkets = enabledMarkets,
            autoStart = isChecked(R.id.autoStart),
            keepScreenOn = isChecked(R.id.keepScreenOn),
            fullscreen = isChecked(R.id.fullscreen),
            nightModeEnabled = isChecked(R.id.nightModeEnabled),
            nightStartMinutes = nightStartMinutes,
            nightEndMinutes = nightEndMinutes,
            instruments = instruments.toList(),
        )
    }

    private fun showTimePicker(initialMinutes: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onSelected(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            true,
        ).show()
    }

    private fun updateNightTimeButtons() {
        nightStartButton.text = "開始 ${formatTime(nightStartMinutes)}"
        nightEndButton.text = "終了 ${formatTime(nightEndMinutes)}"
    }

    private fun formatTime(minutes: Int) = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    private fun saveAndClose() {
        scope.launch {
            store.save(currentSettings())
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun symbolInput(value: String?) = textInput("例: AAPL", value).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        isSingleLine = true
    }

    private fun textInput(hintText: String, value: String?) = EditText(this).apply {
        hint = hintText
        setText(value.orEmpty())
        setTextColor(color(R.color.text_primary))
        setHintTextColor(color(R.color.text_secondary))
    }

    private fun labeled(label: String, child: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(TextView(this@SettingsActivity).apply {
            text = label
            setTextColor(color(R.color.text_secondary))
        })
        addView(child)
    }

    private fun note(value: String) = TextView(this).apply {
        text = value
        setTextColor(color(R.color.text_secondary))
        textSize = 12f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    private fun setChecked(id: Int, checked: Boolean) { findViewById<CheckBox>(id).isChecked = checked }
    private fun isChecked(id: Int) = findViewById<CheckBox>(id).isChecked
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    @Suppress("DEPRECATION")
    private fun color(id: Int) = resources.getColor(id)

    private enum class SettingsPanel(val title: String) {
        MAIN("MAIN1・米国株／ETF"),
        MAIN2("MAIN2・日本株／国内ETF"),
        SUB1("SUB1・国内投信の基準価額"),
        SUB2("SUB2・市場指標／為替");

        fun contains(item: WatchInstrument) = of(item) == this

        companion object {
            fun of(item: WatchInstrument) = when (item.assetType) {
                AssetType.US_STOCK, AssetType.US_ETF -> MAIN
                AssetType.JAPAN_STOCK, AssetType.JAPAN_ETF -> MAIN2
                AssetType.FUND_REFERENCE -> SUB1
                AssetType.MARKET_INDEX -> SUB2
            }
        }
    }

    private companion object {
        const val REQUEST_CREATE_BACKUP = 401
        const val REQUEST_OPEN_BACKUP = 402
        val MAIN_TYPES = setOf(AssetType.US_STOCK, AssetType.US_ETF, AssetType.JAPAN_STOCK, AssetType.JAPAN_ETF)
        val MARKET_REFERENCE_IDS = setOf("NIKKEI225", "SP500", "DOW30", "NASDAQ100", "VIX", "USDJPY")
        val FUND_CODE = Regex("^[0-9A-Z]{8}$")
        val JAPAN_SYMBOL = Regex("^[0-9A-Z]{4,6}$")
        val INTERVAL_LABELS = listOf("5秒（デバッグ用）", "30秒", "60秒", "3分", "5分")
        val INTERVAL_VALUES = listOf(5_000L, 30_000L, 60_000L, 180_000L, 300_000L)
        val UPDATE_INTERVAL_LABELS = listOf("1分", "5分", "15分", "30分")
        val UPDATE_INTERVAL_VALUES = listOf(60_000L, 300_000L, 900_000L, 1_800_000L)
    }
}
