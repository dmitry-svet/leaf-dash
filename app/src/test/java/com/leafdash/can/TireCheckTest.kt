package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TireCheckTest {
    private fun bar(vararg b: Double?) = b.map { it?.let { v -> v / TireCheck.BAR_PER_PSI } }

    @Test fun allGoodIsNoAlert() {
        assertNull(TireCheck.of(bar(2.4, 2.4, 2.3, 2.3), 2.2))
    }

    @Test fun noDataIsNoAlert() {
        assertNull(TireCheck.of(emptyList(), 2.2))
        assertNull(TireCheck.of(listOf(null, null, null, null), 2.2))
    }

    @Test fun lowTireBelowThreshold() {
        val a = TireCheck.of(bar(2.4, 2.1, 2.4, 2.4), 2.2)!!
        assertEquals(setOf(1), a.low)
    }

    @Test fun atThresholdIsNotLow() {
        assertNull(TireCheck.of(bar(2.2, 2.2, 2.2, 2.2), 2.2))
    }

    @Test fun spreadOfPointTwoIsUneven() {
        val a = TireCheck.of(bar(2.5, 2.3, 2.4, 2.4), 2.0)!!
        assertTrue(a.uneven)
        assertTrue(a.low.isEmpty())
        assertEquals(0.2, a.spreadBar, 1e-6)
    }

    @Test fun spreadBelowPointTwoIsFine() {
        assertNull(TireCheck.of(bar(2.5, 2.35, 2.4, 2.4), 2.0))
    }

    @Test fun unknownTiresIgnored() {
        val a = TireCheck.of(bar(2.4, null, 1.8, 2.4), 2.2)!!
        assertEquals(setOf(2), a.low)
        assertTrue(a.uneven)
        assertFalse(TireCheck.of(bar(2.4, null, null, null), 2.2) != null)
    }
}
