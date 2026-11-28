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

    @Test fun idleDrainCounted() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.9, 1000.0, null, STOP)      // parked, heater/AC drain
        assertEquals(0.1, t.trip.kwh, 1e-9)
        assertEquals(0.0, t.trip.km, 1e-9)
    }

    @Test fun smallChargeWhileParkedNotSubtracted() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.0, 1005.0, null, DRIVE)     // 1 kWh spent driving
        t.onSample(19.5, 1005.0, null, STOP)      // parked, charging: not negative spend
        assertEquals(1.0, t.trip.kwh, 1e-9)
    }

    @Test fun idleDrainDoesNotSkewEma() {
        val t = TripTracker()
        val ema = t.avgKwhPer100
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.9, 1000.0, null, STOP)      // no distance -> no efficiency sample
        assertEquals(ema, t.avgKwhPer100, 1e-9)
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

    @Test fun transientOdoSpikeDoesNotLoseDistance() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.5, 5000.0, null, DRIVE)     // corrupt spike: baseline kept
        t.onSample(19.0, 1005.0, null, DRIVE)     // real reading resumes
        assertEquals(5.0, t.trip.km, 1e-9)
    }

    @Test fun backwardsOdoReadingIgnored() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.5, 1005.0, null, DRIVE)
        t.onSample(19.5, 900.0, null, DRIVE)      // corrupt backwards read
        t.onSample(19.0, 1006.0, null, DRIVE)     // real reading resumes
        assertEquals(6.0, t.trip.km, 1e-9)        // not 5 + 106
    }

    @Test fun persistentOdoShiftRebaselines() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(19.5, 5000.0, null, DRIVE)     // shift, sample 1: skipped
        t.onSample(19.5, 5001.0, null, DRIVE)     // shift, sample 2: rebaseline
        t.onSample(19.0, 5002.0, null, DRIVE)     // counted from new baseline
        assertEquals(1.0, t.trip.km, 1e-9)
    }

    @Test fun corruptKwhStepSkipsEnergyNotDistance() {
        val t = TripTracker()
        t.onSample(20.0, 1000.0, null, DRIVE)
        t.onSample(10.0, 1005.0, null, DRIVE)     // 10 kWh in one sample = corrupt
        assertEquals(5.0, t.trip.km, 1e-9)
        assertEquals(0.0, t.trip.kwh, 1e-9)
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

    private fun load(motor: Double?, climate: Double? = null, aux: Double? = null, other: Double? = null) =
        com.leafdash.can.Load(null, motor, climate, null, null, aux, other)

    @Test fun loadEnergyIntegratesPerTrip() {
        val t = TripTracker()
        t.onLoad(load(-20.0, 2.0, 0.4, 0.0), 0L)       // first sample: no interval yet
        t.onLoad(load(-20.0, 2.0, 0.4, 0.0), 9_000L)   // 9 s: regen 20 kW = 0.05 kWh
        t.onLoad(load(30.0, 2.0, 0.4, 0.2), 18_000L)   // 9 s: traction 30 kW = 0.075 kWh
        val e = t.loadEnergy
        assertEquals(0.075, e.tractionKwh, 1e-9)
        assertEquals(0.05, e.regenKwh, 1e-9)
        assertEquals(0.01, e.climateKwh, 1e-9)          // 2 kW x 18 s
        assertEquals(0.002 + 0.0005, e.otherKwh, 1e-9)  // aux 0.4 x 18 s + other 0.2 x 9 s
        t.resetTrip()
        assertEquals(LoadEnergy(), t.loadEnergy)
    }

    @Test fun loadEnergySkipsUnknownValues() {
        val t = TripTracker()
        t.onLoad(load(null), 0L)
        t.onLoad(load(null), 9_000L)
        assertEquals(LoadEnergy(), t.loadEnergy)
    }

    @Test fun loadEnergySkipsLongGapsAndRebaseline() {
        val start = LoadEnergy(regenKwh = 1.0, climateKwh = 2.0)
        val t = TripTracker(TripSnapshot(loadEnergy = start))
        t.onLoad(load(-20.0, 2.0), 0L)
        t.onLoad(load(-20.0, 2.0), 120_000L)   // 2 min gap: link stalled, not counted
        t.rebaseline()
        t.onLoad(load(-20.0, 2.0), 121_000L)   // first after rebaseline: no interval
        assertEquals(start, t.loadEnergy)
        assertEquals(start, t.snapshot().loadEnergy)
    }

    @Test fun batteryEnergyIsNetPackPowerPerTrip() {
        val t = TripTracker()
        val pack = { kw: Double -> com.leafdash.can.Load(kw, null, null, null, null, null, null) }
        t.onLoad(pack(10.0), 0L)
        t.onLoad(pack(20.0), 9_000L)    // 9 s at 20 kW = 0.05 kWh
        t.onLoad(pack(-10.0), 18_000L)  // 9 s regen at 10 kW = -0.025 kWh
        assertEquals(0.025, t.loadEnergy.batteryKwh, 1e-9)
        assertEquals(0.025, TripTracker(t.snapshot()).loadEnergy.batteryKwh, 1e-9)
        t.resetTrip()
        assertEquals(0.0, t.loadEnergy.batteryKwh, 1e-9)
    }
}
