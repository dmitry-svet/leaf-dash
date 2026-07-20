package com.leafdash.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTrackerTest {

    private val DRIVE = 30.0   // km/h, "moving"
    private val STOP = 0.0

    @Test fun distanceFromOdometerEndpoints() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)   // 5 km, 1 kWh
        for (w in listOf(t.lastCharge, t.carOn, t.trip)) {
            assertEquals(5.0, w.km, 1e-9)
            assertEquals(1.0, w.kwh, 1e-9)
            assertEquals(20.0, w.kwhPer100!!, 1e-9)
        }
    }

    @Test fun distanceSurvivesLargeOdometerGap() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1050.0, null, DRIVE)   // 50 km jump -> distance kept
        assertEquals(50.0, t.trip.km, 1e-9)
    }

    @Test fun regenReducesNetEnergy() {
        val t = TripTracker()
        t.onSample(20.0, 0.0, null, DRIVE)
        t.onSample(19.0, 5.0, null, DRIVE)      // +1 kWh
        t.onSample(19.5, 10.0, null, DRIVE)     // regen -0.5
        assertEquals(0.5, t.trip.kwh, 1e-9)
        assertEquals(10.0, t.trip.km, 1e-9)
    }

    @Test fun energyCountedOnlyWhileMoving() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, STOP)    // stationary -> energy ignored
        assertEquals(0.0, t.trip.kwh, 1e-9)
        assertEquals(5.0, t.trip.km, 1e-9)      // distance still from odometer
    }

    @Test fun chargingWhileParkedDoesNotSubtractEnergy() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 78.0, DRIVE)   // drove 5 km, used 1 kWh
        t.onSample(25.0, 1005.0, 90.0, STOP)    // parked + charging -> ignored
        assertEquals(1.0, t.trip.kwh, 1e-9)     // NOT 1 - 6
        assertEquals(1.0, t.carOn.kwh, 1e-9)
    }

    @Test fun chargeResetsLastChargeWhenSocClimbs() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 75.0, DRIVE)
        t.onSample(25.0, 1005.0, 90.0, STOP)    // soc 90 > 75+5 -> lastCharge reset
        assertEquals(0.0, t.lastCharge.km, 1e-9)
        assertEquals(5.0, t.carOn.km, 1e-9)
        assertEquals(5.0, t.trip.km, 1e-9)
    }

    @Test fun smallSocBumpDoesNotResetLastCharge() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 78.0, DRIVE)
        t.onSample(19.0, 1010.0, 80.0, DRIVE)   // +2% -> no reset
        assertEquals(10.0, t.lastCharge.km, 1e-9)
    }

    @Test fun resetTripClearsOnlyTrip() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.resetTrip()
        assertEquals(0.0, t.trip.km, 1e-9)
        assertEquals(5.0, t.carOn.km, 1e-9)
        assertEquals(5.0, t.lastCharge.km, 1e-9)
    }

    @Test fun sessionStartRebaselinesCarOn() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.onSessionStart()
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.onSample(18.0, 1010.0, null, DRIVE)
        assertEquals(5.0, t.carOn.km, 1e-9)
        assertEquals(10.0, t.trip.km, 1e-9)
    }

    @Test fun snapshotRestoresBaselineAndKwh() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 78.0, DRIVE)
        val t2 = TripTracker(t.snapshot())
        t2.onSample(19.0, 1005.0, 78.0, DRIVE)
        assertEquals(5.0, t2.lastCharge.km, 1e-9)
        assertEquals(1.0, t2.lastCharge.kwh, 1e-9)
        assertEquals(0.0, t2.carOn.km, 1e-9)
        t2.onSample(19.0, 1005.0, 90.0, DRIVE)   // charge -> reset
        assertEquals(0.0, t2.lastCharge.km, 1e-9)
    }
}
