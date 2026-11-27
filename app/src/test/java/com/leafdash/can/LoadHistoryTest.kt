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

    private fun pts(vararg kw: Double?) = kw.mapIndexed { i, v -> LoadPoint(i.toLong(), v, null, null) }

    @Test fun motorRangeKeepsZeroAtAThird() {
        val (lo, hi) = motorChartRange(pts(38.0, -10.0))       // traction decides
        assertEquals(38.0, hi, 1e-9)
        assertEquals(-19.0, lo, 1e-9)
        val (lo2, hi2) = motorChartRange(pts(10.0, -22.0))     // regen decides
        assertEquals(-22.0, lo2, 1e-9)
        assertEquals(44.0, hi2, 1e-9)
    }

    @Test fun motorRangeHasAFloor() {
        val (lo, hi) = motorChartRange(pts(0.4, null, -0.2))
        assertEquals(-5.0, lo, 1e-9)
        assertEquals(10.0, hi, 1e-9)
        assertEquals(-5.0 to 10.0, motorChartRange(emptyList()))
    }
}
