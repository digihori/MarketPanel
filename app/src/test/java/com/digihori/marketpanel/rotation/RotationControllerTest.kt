package com.digihori.marketpanel.rotation

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationControllerTest {
    @Test
    fun startShowsCurrentItemAndScheduledRunsRotateInOrder() {
        val scheduler = FakeScheduler()
        val shown = mutableListOf<String>()
        val controller = RotationController(
            items = listOf("Toyota", "Sony", "NVIDIA"),
            intervalMillis = 60_000,
            scheduler = scheduler,
        ) { item, _ -> shown += item }

        controller.start()
        scheduler.runNext()
        scheduler.runNext()
        scheduler.runNext()

        assertEquals(listOf("Toyota", "Sony", "NVIDIA", "Toyota"), shown)
        assertEquals(0, controller.currentIndex)
    }

    @Test
    fun stopCancelsPendingRotation() {
        val scheduler = FakeScheduler()
        val shown = mutableListOf<Int>()
        val controller = RotationController(
            items = listOf(1, 2),
            intervalMillis = 1_000,
            scheduler = scheduler,
        ) { item, _ -> shown += item }

        controller.start()
        controller.stop()
        scheduler.runNext()

        assertEquals(listOf(1), shown)
    }

    @Test
    fun replacingItemsKeepsAValidCurrentPosition() {
        val scheduler = FakeScheduler()
        val shown = mutableListOf<String>()
        val controller = RotationController(
            items = listOf("A", "B", "C"),
            intervalMillis = 1_000,
            scheduler = scheduler,
        ) { item, _ -> shown += item }

        controller.start()
        scheduler.runNext()
        controller.updateItems(listOf("X"))

        assertEquals("X", shown.last())
        assertEquals(0, controller.currentIndex)
    }

    @Test
    fun showIndexDisplaysANewlyAddedItemImmediately() {
        val shown = mutableListOf<String>()
        val controller = RotationController(
            items = listOf("IBM"),
            intervalMillis = 60_000,
            scheduler = FakeScheduler(),
        ) { item, _ -> shown += item }

        controller.start()
        controller.updateItems(listOf("IBM", "MCD"))
        controller.showIndex(1)

        assertEquals("MCD", shown.last())
        assertEquals(1, controller.currentIndex)
    }

    @Test
    fun changingIntervalReschedulesThePendingRotationAndRestartsCycle() {
        val scheduler = FakeScheduler()
        var cycles = 0
        val controller = RotationController(
            items = listOf("IBM", "MCD"),
            intervalMillis = 60_000,
            scheduler = scheduler,
            onCycleStarted = { cycles++ },
        ) { _, _ -> }

        controller.start()
        controller.updateInterval(5_000)

        assertEquals(listOf(5_000L), scheduler.pendingDelays)
        assertEquals(2, cycles)
    }

    @Test
    fun phaseOffsetOnlyDelaysTheFirstRotation() {
        val scheduler = FakeScheduler()
        val controller = RotationController(
            items = listOf("A", "B"),
            intervalMillis = 30_000,
            phaseOffsetMillis = 10_000,
            scheduler = scheduler,
        ) { _, _ -> }

        controller.start()
        assertEquals(listOf(40_000L), scheduler.pendingDelays)

        scheduler.runNext()
        assertEquals(listOf(30_000L), scheduler.pendingDelays)
    }

    private class FakeScheduler : RotationScheduler {
        private data class ScheduledTask(val task: Runnable, val delayMillis: Long)

        private val tasks = ArrayDeque<ScheduledTask>()
        val pendingDelays: List<Long> get() = tasks.map { it.delayMillis }

        override fun schedule(task: Runnable, delayMillis: Long) {
            tasks += ScheduledTask(task, delayMillis)
        }

        override fun cancel(task: Runnable) {
            tasks.removeAll { it.task === task }
        }

        fun runNext() {
            tasks.removeFirstOrNull()?.task?.run()
        }
    }
}
