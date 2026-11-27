package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadHistoryTest {

    private val l = Load(null, 10.0, 2.0, 0.5, 1.5, 0.3, 0.1)

    @Test fun keepsOnlyTheWindow() {
        val h = LoadHistory(windowMs = 300_000)
        h.add(0L, l)
        h.add(200_000L, l)
        h.add(301_000L, l)
        assertEquals(listOf(200_000L, 301_000L), h.points.map { it.tMs })
    }

    @Test fun pointHoldsMotorAndClimateSplit() {
        val h = LoadHistory()
        h.add(5L, l)
        assertEquals(LoadPoint(5L, 10.0, 0.5, 1.5), h.points.single())
    }

    @Test fun clockGoingBackClears() {
        val h = LoadHistory()
        h.add(100_000L, l)
        h.add(50_000L, l)
        assertEquals(listOf(50_000L), h.points.map { it.tMs })
    }
}
