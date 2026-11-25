package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadTest {

    private val base = LeafState(
        packVolts = 350.0, packAmps = 50.0,          // 17.5 kW at the pack
        motorPowerW = 15_000, acPower250W = 8,       // 2.0 kW climate
        auxPower100W = 5, speedKmh = 100.0,          // 0.5 kW aux
    )

    @Test fun otherIsPackMinusComponents() {
        val l = Load.of(base)
        assertEquals(17.5, l.batteryKw!!, 1e-9)
        assertEquals(15.0, l.motorKw!!, 1e-9)
        assertEquals(2.0, l.climateKw!!, 1e-9)
        assertEquals(0.5, l.auxKw!!, 1e-9)
        assertEquals(0.0, l.otherKw!!, 1e-9)
    }

    @Test fun otherNeverNegative() {
        val l = Load.of(base.copy(packAmps = 40.0))  // 14 kW < components
        assertEquals(0.0, l.otherKw!!, 1e-9)
    }

    @Test fun otherUnknownWithoutPackOrMotor() {
        assertNull(Load.of(base.copy(packAmps = null)).otherKw)
        assertNull(Load.of(base.copy(motorPowerW = null)).otherKw)
    }

    @Test fun climateSplitFollowsVcmEstimates() {
        // estimates: A/C 0.5 kW (10 x 50 W), heater 1.5 kW (6 x 250 W): 1:3
        val l = Load.of(base.copy(estAcPower50W = 10, estHeaterPower250W = 6))
        assertEquals(0.5, l.acKw!!, 1e-9)
        assertEquals(1.5, l.heaterKw!!, 1e-9)
    }

    @Test fun climateSplitWithoutEstimatesUsesOutsideTemp() {
        val cold = Load.of(base.copy(ambientTempC = 2.0))
        assertEquals(0.0, cold.acKw!!, 1e-9)
        assertEquals(2.0, cold.heaterKw!!, 1e-9)
        val warm = Load.of(base.copy(ambientTempC = 25.0))
        assertEquals(2.0, warm.acKw!!, 1e-9)
        assertEquals(0.0, warm.heaterKw!!, 1e-9)
    }

    @Test fun instantConsumptionOnlyWhenMoving() {
        assertEquals(17.5, Load.of(base).kwhPer100!!, 1e-9)   // 17.5 kW at 100 km/h
        assertNull(Load.of(base.copy(speedKmh = 3.0)).kwhPer100)
    }
}
