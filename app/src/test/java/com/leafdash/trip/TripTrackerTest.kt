package com.leafdash.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTrackerTest {

    private val DRIVE = 30.0
    private val STOP = 0.0

    @Test fun accumulatesWhileConnected() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)     // baseline
        t.onSample(19.0, 1005.0, null, DRIVE)     // +5 km, +1 kWh
        for (w in listOf(t.lastCharge, t.carOn, t.trip, t.lifetime)) {
            assertEquals(5.0, w.km, 1e-9)
            assertEquals(1.0, w.kwh, 1e-9)
        }
    }

    @Test fun offAppGapNotCounted() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)     // 5 km connected
        t.onSessionStart()                        // app disconnected...
        t.onSample(19.0, 1100.0, null, DRIVE)     // reconnect: odo +95 while off
        t.onSample(18.0, 1105.0, null, DRIVE)     // +5 km connected
        assertEquals(10.0, t.trip.km, 1e-9)       // 95 km off-gap excluded
        assertEquals(10.0, t.lifetime.km, 1e-9)
        assertEquals(5.0, t.carOn.km, 1e-9)       // carOn reset at session start
    }

    @Test fun withinSessionCatchUpCounted() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1080.0, null, DRIVE)     // 80 km in one gap, app on -> counts
        assertEquals(80.0, t.trip.km, 1e-9)
    }

    @Test fun regenReducesNetEnergy() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)     // +1
        t.onSample(19.5, 1010.0, null, DRIVE)     // regen -0.5
        assertEquals(0.5, t.trip.kwh, 1e-9)
        assertEquals(10.0, t.trip.km, 1e-9)
    }

    @Test fun energyCountedOnlyWhileMoving() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, STOP)      // stationary -> energy skipped
        assertEquals(0.0, t.trip.kwh, 1e-9)
        assertEquals(5.0, t.trip.km, 1e-9)
    }

    @Test fun chargingWhileParkedDoesNotSubtractEnergy() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 78.0, DRIVE)     // 5 km, 1 kWh
        t.onSample(25.0, 1005.0, 90.0, STOP)      // parked + charging -> ignored
        assertEquals(1.0, t.trip.kwh, 1e-9)
        assertEquals(0.0, t.lastCharge.km, 1e-9)  // charge reset lastCharge
        assertEquals(5.0, t.trip.km, 1e-9)
    }

    @Test fun resetTripClearsOnlyTrip() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.resetTrip()
        assertEquals(0.0, t.trip.km, 1e-9)
        assertEquals(5.0, t.carOn.km, 1e-9)
        assertEquals(5.0, t.lastCharge.km, 1e-9)
        assertEquals(5.0, t.lifetime.km, 1e-9)
    }

    @Test fun sessionStartClearsCarOn() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.onSessionStart()
        t.onSample(19.0, 1005.0, null, DRIVE)
        t.onSample(18.0, 1010.0, null, DRIVE)
        assertEquals(5.0, t.carOn.km, 1e-9)
        assertEquals(10.0, t.trip.km, 1e-9)
    }

    @Test fun snapshotRestoresPersistentWindows() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, 80.0, DRIVE)
        t.onSample(19.0, 1005.0, 78.0, DRIVE)
        val t2 = TripTracker(t.snapshot())
        assertEquals(5.0, t2.lastCharge.km, 1e-9)
        assertEquals(1.0, t2.lastCharge.kwh, 1e-9)
        assertEquals(5.0, t2.trip.km, 1e-9)
        assertEquals(5.0, t2.lifetime.km, 1e-9)
        assertEquals(0.0, t2.carOn.km, 1e-9)      // session window not persisted
        t2.onSample(19.0, 1005.0, 90.0, DRIVE)    // charge -> reset lastCharge
        assertEquals(0.0, t2.lastCharge.km, 1e-9)
    }
}
