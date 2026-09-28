package com.leafdash.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripLoggerTest {

    private val MIN = 60_000L

    private fun s(
        t: Long, dist: Double, kwh: Double, soc: Double = 90.0,
        cellMin: Double? = 3.9, cellMax: Double? = 3.92, session: Int = 1,
    ) = TripSample(
        tMs = t, session = session, odoKm = 1000.0 + dist, distKm = dist, kwh = kwh, soc = soc,
        packV = 380.0, cellMinV = cellMin, cellMaxV = cellMax, ah = 43.9,
        soh = 66.5, hx = 49.7, batTempC = 25.0, extTempC = 19.0,
    )

    @Test fun samplesWithinGapFormOneTrip() {
        val l = TripLogger()
        assertNull(l.onSample(s(0, 0.0, 14.0)))
        assertNull(l.onSample(s(10 * MIN, 8.0, 13.0, cellMin = 3.6, cellMax = 3.7)))
        val cur = l.current!!
        assertEquals(8.0, cur.distKm, 1e-9)
        assertEquals(1000.0, cur.energyWh, 1e-9)
        assertEquals(10 * MIN, cur.durationMs)
        assertEquals(3.6, cur.cellMinV!!, 1e-9)
        assertEquals(100.0, cur.maxSpreadMv!!, 1e-6)   // worst spread seen
    }

    @Test fun longGapFinishesTripAndStartsNew() {
        val l = TripLogger()
        l.onSample(s(0, 0.0, 14.0))
        l.onSample(s(20 * MIN, 15.0, 12.0))
        val done = l.onSample(s(20 * MIN + 31 * MIN, 15.0, 12.0))!!   // car was off
        assertEquals(15.0, done.distKm, 1e-9)
        assertEquals(2000.0, done.energyWh, 1e-9)
        assertEquals(0.0, l.current!!.distKm, 1e-9)                    // new trip started
    }

    @Test fun parkedSessionWithoutDrivingIsDropped() {
        val l = TripLogger()
        l.onSample(s(0, 0.0, 14.0))
        l.onSample(s(5 * MIN, 0.05, 13.95))                            // < 0.1 km
        assertNull(l.onSample(s(5 * MIN + 31 * MIN, 0.05, 13.95)))
    }

    @Test fun csvRoundTrip() {
        val l = TripLogger()
        l.onSample(s(1_789_000_000_000, 0.0, 14.0))
        l.onSample(s(1_789_000_000_000 + 22 * MIN, 15.0, 12.07, soc = 78.0))
        val r = l.current!!
        val back = TripRecord.fromCsv(r.toCsv())!!
        assertEquals(r, back)
    }

    @Test fun csvWithMissingCellDataRoundTrips() {
        val l = TripLogger()
        l.onSample(s(0, 0.0, 14.0, cellMin = null, cellMax = null))
        l.onSample(s(MIN, 1.0, 13.8, cellMin = null, cellMax = null))
        val r = l.current!!
        assertEquals(r, TripRecord.fromCsv(r.toCsv()))
    }

    @Test fun reconnectReanchorDoesNotLoseDistance() {
        // BT reconnect: new poller session re-anchors the smooth distance on the
        // integer-mile odometer, jumping back up to 1.6 km - must not be subtracted
        val l = TripLogger()
        l.onSample(s(0, 100.0, 14.0))
        l.onSample(s(MIN, 101.5, 13.8))                      // 1.5 km
        l.onSample(s(2 * MIN, 100.3, 13.8, session = 2))     // re-anchored lower
        l.onSample(s(3 * MIN, 100.9, 13.7, session = 2))     // +0.6 km
        assertEquals(2.1, l.current!!.distKm, 1e-9)
        assertEquals(300.0, l.current!!.energyWh, 1e-6)
    }

    @Test fun offAppDrivingWithinTripNotCounted() {
        // app lost the link while driving (odometer jumped between sessions):
        // like the economy windows, only app-connected driving is counted, so
        // distance and energy stay consistent for Wh/km
        val l = TripLogger()
        l.onSample(s(0, 100.0, 14.0))
        l.onSample(s(MIN, 102.0, 13.7))
        l.onSample(s(10 * MIN, 110.0, 12.5, session = 2))    // 8 km + 1.2 kWh off-app
        l.onSample(s(11 * MIN, 111.0, 12.4, session = 2))
        assertEquals(3.0, l.current!!.distKm, 1e-9)
        assertEquals(400.0, l.current!!.energyWh, 1e-6)
    }

    @Test fun parsesLegacyV069Row() {
        // real exported row: last two columns = start/end smooth distance
        val line = "2026-09-28,17:07:24,179913,00:24:45,1.0,205,203,82.31,81.02,12.891," +
            "12.686,43.50,65.91,49.53,387.56,386.31,3.920,63,20.0,20.0," +
            "1790604444442,1790605930319,179913.394,179914.405"
        val r = TripRecord.fromCsv(line, legacy = true)!!
        assertEquals(1.011, r.distKm, 1e-6)
        assertEquals(205.0, r.energyWh, 1e-6)
        assertEquals(r, TripRecord.fromCsv(r.toCsv()))   // re-saved in new format
    }

    @Test fun resumesPersistedCurrentTrip() {
        val a = TripLogger()
        a.onSample(s(0, 0.0, 14.0))
        a.onSample(s(5 * MIN, 4.0, 13.5))
        val b = TripLogger(a.current)            // app restarted mid-trip
        b.onSample(s(6 * MIN, 4.0, 13.5, session = 2))
        b.onSample(s(10 * MIN, 8.0, 13.0, session = 2))
        assertEquals(8.0, b.current!!.distKm, 1e-9)
        assertEquals(0L, b.current!!.startMs)
    }
}
