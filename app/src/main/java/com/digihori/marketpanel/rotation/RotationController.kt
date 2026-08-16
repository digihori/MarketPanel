package com.digihori.marketpanel.rotation

import android.os.Handler
import android.os.Looper

interface RotationScheduler {
    fun schedule(task: Runnable, delayMillis: Long)
    fun cancel(task: Runnable)
}

class HandlerRotationScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : RotationScheduler {
    override fun schedule(task: Runnable, delayMillis: Long) {
        handler.postDelayed(task, delayMillis)
    }

    override fun cancel(task: Runnable) {
        handler.removeCallbacks(task)
    }
}

class RotationController<T>(
    items: List<T>,
    intervalMillis: Long,
    initialIndex: Int = 0,
    private val phaseOffsetMillis: Long = 0L,
    private val scheduler: RotationScheduler = HandlerRotationScheduler(),
    private val onCycleStarted: (() -> Unit)? = null,
    private val onItemChanged: (item: T, index: Int) -> Unit,
) {
    private var items: List<T> = items
    private var intervalMillis: Long = intervalMillis

    init {
        require(items.isNotEmpty()) { "Rotation items must not be empty" }
        require(intervalMillis > 0) { "Rotation interval must be positive" }
        require(phaseOffsetMillis >= 0) { "Rotation phase offset must not be negative" }
    }

    var currentIndex: Int = initialIndex.mod(items.size)
        private set

    private var running = false
    private val rotateTask = object : Runnable {
        override fun run() {
            if (!running) return
            currentIndex = (currentIndex + 1) % items.size
            onItemChanged(items[currentIndex], currentIndex)
            onCycleStarted?.invoke()
            scheduler.schedule(this, intervalMillis)
        }
    }

    fun start() {
        if (running) return
        running = true
        onItemChanged(items[currentIndex], currentIndex)
        onCycleStarted?.invoke()
        scheduler.schedule(rotateTask, intervalMillis + phaseOffsetMillis)
    }

    fun stop() {
        running = false
        scheduler.cancel(rotateTask)
    }

    fun updateItems(newItems: List<T>) {
        require(newItems.isNotEmpty()) { "Rotation items must not be empty" }
        items = newItems
        currentIndex %= items.size
        onItemChanged(items[currentIndex], currentIndex)
    }

    fun showIndex(index: Int) {
        currentIndex = index.mod(items.size)
        onItemChanged(items[currentIndex], currentIndex)
        if (running) {
            onCycleStarted?.invoke()
            scheduler.cancel(rotateTask)
            scheduler.schedule(rotateTask, intervalMillis + phaseOffsetMillis)
        }
    }

    fun updateInterval(newIntervalMillis: Long) {
        require(newIntervalMillis > 0) { "Rotation interval must be positive" }
        if (intervalMillis == newIntervalMillis) return
        intervalMillis = newIntervalMillis
        if (running) {
            scheduler.cancel(rotateTask)
            onCycleStarted?.invoke()
            scheduler.schedule(rotateTask, intervalMillis + phaseOffsetMillis)
        }
    }
}
