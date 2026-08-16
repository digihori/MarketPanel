package com.digihori.marketpanel.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.digihori.marketpanel.R
import com.digihori.marketpanel.domain.model.PanelData
import kotlin.math.max
import kotlin.math.abs
import java.text.NumberFormat
import java.util.Locale

class MarketPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.panel_divider)
        strokeWidth = density
    }
    private val chartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var data: PanelData? = null
    private var statusText: String = ""
    private var statusIsError: Boolean = false

    fun submit(value: PanelData) {
        data = value
        chartPaint.color = color(if (value.isPositive) R.color.chart_primary else R.color.price_down)
        contentDescription = "${value.title} ${value.price} ${value.change}"
        invalidate()
    }

    fun showStatus(text: String, isError: Boolean = false) {
        statusText = text
        statusIsError = isError
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStatus(canvas)
        val value = data
        if (value == null) {
            drawText(
                canvas,
                statusText.ifEmpty { "データを準備しています" },
                18f * density,
                height / 2f,
                14f,
                if (statusIsError) R.color.price_down else R.color.text_secondary,
            )
            return
        }
        val compact = height < 300 * density
        val left = 18f * density
        val right = width - 18f * density

        drawText(canvas, value.label, left, 27f * density, 14f, R.color.text_secondary)
        val chartLeft = if (compact) width * 0.62f else width * 0.52f
        val titleLines = drawWrappedTitle(
            canvas,
            value.title,
            left,
            57f * density,
            chartLeft - left - 14f * density,
            if (compact) 21f else 25f,
        )
        val extraTitleHeight = (titleLines - 1) * 27f
        drawText(canvas, value.subtitle, left, (80f + extraTitleHeight) * density, 13f, R.color.text_secondary)

        val priceY = (if (compact) 116f else 126f) + extraTitleHeight
        drawText(canvas, value.price, left, priceY * density, if (compact) 27f else 35f, R.color.text_primary)
        drawText(
            canvas,
            value.change,
            left,
            (priceY + if (compact) 29f else 37f) * density,
            if (compact) 16f else 19f,
            if (value.isPositive) R.color.price_up else R.color.price_down,
        )

        if (value.points.isNotEmpty()) {
            val chartTop = 50f * density
            val chartBottom = height - 52f * density
            drawGrid(canvas, chartLeft, chartTop, right, chartBottom)
            drawChart(canvas, value.points, chartLeft, chartTop, right, chartBottom)
            drawAxes(canvas, value, chartLeft, chartTop, right, chartBottom)
        }

        drawText(canvas, value.updatedAt, left, height - 15f * density, 12f, R.color.text_secondary)
    }

    private fun drawStatus(canvas: Canvas) {
        if (statusText.isEmpty()) return
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            13f,
            resources.displayMetrics,
        )
        textPaint.color = color(if (statusIsError) R.color.price_down else R.color.price_up)
        val x = width - 18f * density - textPaint.measureText(statusText)
        canvas.drawText(statusText, x, 25f * density, textPaint)
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
    }

    private fun drawChart(
        canvas: Canvas,
        points: List<Float>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        if (points.size < 2) return
        val bounds = calculateChartBounds(points) ?: return
        val range = bounds.maximum - bounds.minimum
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + (right - left) * index / (points.size - 1)
            val y = bottom - (bottom - top) * (point - bounds.minimum) / range
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, chartPaint)
    }

    private fun drawAxes(
        canvas: Canvas,
        value: PanelData,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val bounds = calculateChartBounds(value.points) ?: return
        val middle = (bounds.minimum + bounds.maximum) / 2f
        drawAxisText(canvas, axisValue(bounds.maximum, value.axisUnit), left - 6f * density, top + 3f * density, Paint.Align.RIGHT)
        drawAxisText(canvas, axisValue(middle, value.axisUnit), left - 6f * density, (top + bottom) / 2f + 3f * density, Paint.Align.RIGHT)
        drawAxisText(canvas, axisValue(bounds.minimum, value.axisUnit), left - 6f * density, bottom + 3f * density, Paint.Align.RIGHT)

        val labels = value.xAxisLabels
        if (labels.isNotEmpty()) {
            drawAxisText(canvas, labels.first(), left, bottom + 17f * density, Paint.Align.LEFT)
            drawAxisText(canvas, labels[labels.lastIndex / 2], (left + right) / 2f, bottom + 17f * density, Paint.Align.CENTER)
            drawAxisText(canvas, labels.last(), right, bottom + 17f * density, Paint.Align.RIGHT)
        }
        drawAxisText(canvas, axisTitle(value.axisUnit), left, top - 10f * density, Paint.Align.LEFT)
    }

    private fun drawAxisText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        align: Paint.Align,
    ) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,
            resources.displayMetrics,
        )
        textPaint.color = color(R.color.text_secondary)
        textPaint.textAlign = align
        canvas.drawText(text, x, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawWrappedTitle(
        canvas: Canvas,
        title: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        sizeSp: Float,
    ): Int {
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        textPaint.color = color(R.color.text_primary)
        val fit = textPaint.breakText(title, true, maxWidth, null)
        if (fit >= title.length) {
            canvas.drawText(title, x, firstBaseline, textPaint)
            return 1
        }
        val whitespace = title.substring(0, fit).indexOfLast { it.isWhitespace() }
        val splitAt = if (whitespace > 0) whitespace else fit
        canvas.drawText(title.substring(0, splitAt).trimEnd(), x, firstBaseline, textPaint)
        val remaining = title.substring(splitAt).trimStart()
        val ellipsis = "…"
        val secondFit = textPaint.breakText(
            remaining,
            true,
            (maxWidth - textPaint.measureText(ellipsis)).coerceAtLeast(0f),
            null,
        )
        val secondLine = if (secondFit < remaining.length) {
            remaining.take(secondFit).trimEnd() + ellipsis
        } else remaining
        canvas.drawText(secondLine, x, firstBaseline + 27f * density, textPaint)
        return 2
    }

    private fun axisValue(value: Float, unit: String): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = if (unit == "JPY") 0 else 2
        }
        val formatted = formatter.format(value)
        return when (unit) {
            "USD" -> "\$$formatted"
            "JPY" -> "¥$formatted"
            else -> formatted
        }
    }

    private fun axisTitle(unit: String): String = when (unit) {
        "USD" -> "価格（USD）"
        "JPY" -> "価格（円）"
        "RATE" -> "為替レート"
        else -> "指数値"
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        sizeSp: Float,
        colorRes: Int,
    ) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            resources.displayMetrics,
        )
        textPaint.color = color(colorRes)
        canvas.drawText(text, x, y, textPaint)
    }

    @Suppress("DEPRECATION")
    private fun color(resource: Int): Int = resources.getColor(resource)
}

internal data class ChartBounds(val minimum: Float, val maximum: Float)

internal fun calculateChartBounds(points: List<Float>): ChartBounds? {
    val dataMinimum = points.minOrNull() ?: return null
    val dataMaximum = points.maxOrNull() ?: return null
    val dataRange = dataMaximum - dataMinimum
    val padding = if (dataRange > 0f) {
        dataRange * 0.20f
    } else {
        max(abs(dataMinimum) * 0.05f, 1f)
    }
    return ChartBounds(dataMinimum - padding, dataMaximum + padding)
}
