package com.leafdash.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TempBandTest {
    @Test fun bandsByBatteryTemp() {
        assertEquals(TempBand.COLD, tempBand(-5.0))
        assertEquals(TempBand.COLD, tempBand(0.0))
        assertEquals(TempBand.OK, tempBand(0.5))
        assertEquals(TempBand.OK, tempBand(24.9))
        assertEquals(TempBand.WARM, tempBand(25.0))
        assertEquals(TempBand.WARM, tempBand(37.9))
        assertEquals(TempBand.HOT, tempBand(38.0))
    }
}
