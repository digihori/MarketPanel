package com.digihori.marketpanel.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiCreditPacerTest {
    @Test
    fun spacesRequestsAccordingToTheirCreditCost() = runTest {
        var clock = 1_000L
        val waits = mutableListOf<Long>()
        val pacer = ApiCreditPacer(
            millisPerCredit = 8_000L,
            now = { clock },
            wait = {
                waits += it
                clock += it
            },
        )

        pacer.acquire(2)
        pacer.acquire(2)
        pacer.acquire(1)

        assertEquals(listOf(16_000L, 16_000L), waits)
    }
}
