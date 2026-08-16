package com.digihori.marketpanel.domain.model

data class PanelData(
    val label: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val change: String,
    val updatedAt: String,
    val points: List<Float>,
    val isPositive: Boolean,
    val axisUnit: String = "",
    val xAxisLabels: List<String> = emptyList(),
)
