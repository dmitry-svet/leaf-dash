package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadTest {

    private val base = LeafState(
        packVolts = 350.0, packAmps = 50.0,          // 17.5 kW at the pack
        motorPowerW = 15_000, acPower250W = 8,       // 2.0 kW climate
        auxPower100W = 5,                            // 0.5 kW aux
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

    @Test fun motorIsRegenWhenPackCharges() {
        // VCM 1146 reads 0 under regen (car, v1.3): the motor is the pack power
        // left after climate and aux, -7 - 2 - 0.5
        val l = Load.of(base.copy(packAmps = -20.0, motorPowerW = 0))
        assertEquals(-9.5, l.motorKw!!, 1e-9)
        assertEquals(0.0, l.otherKw!!, 1e-9)
    }

    @Test fun regenNeedsNoVcmMotorReading() {
        assertEquals(-9.5, Load.of(base.copy(packAmps = -20.0, motorPowerW = null)).motorKw!!, 1e-9)
    }

    @Test fun motorIsTractionWhenPackDrains() {
        assertEquals(15.0, Load.of(base).motorKw!!, 1e-9)
    }

    @Test fun lightRegenUnderClimateLoadStillRegen() {
        // pack +1 kW, but climate + aux take 2.5 kW: the motor is feeding 1.5 kW back
        val l = Load.of(base.copy(packVolts = 400.0, packAmps = 2.5, motorPowerW = 0))
        assertEquals(-1.5, l.motorKw!!, 1e-9)
    }
}
