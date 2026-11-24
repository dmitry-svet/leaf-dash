package com.leafdash.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class LeafSpyImportTest {

    // real rows from a LeafSpy Pro TripChrgLog (metric units, US date format)
    private val csv = """
        Date,Time,odo km,Duration,dist km,elv m,Energy,Gids,SGids,EGids,AHr,SOH,Hx,SHVolt,EHVolt,Drive,Regen,Charge,L1/L2,QC
        07/04/2026,11:52:24,175222,00:59:19,32,0,5293,0,186,186,44.171,67.54%,49.85,393.14,382.18,5424,-652,0,4603,166
        07/04/2026,12:58:00,175254,01:15:06,0,0,-273,-29,121,150,44.158,67.54%,49.85,383.47,387.50,5424,-652,-273,4604,166
        07/04/2026,14:50:20,175254,00:25:11,18,0,2623,0,0,0,44.158,67.52%,49.93,385.75,378.67,8046,-1177,-273,4604,166
        09/18/2026,18:43:43,0,00:02:30,179438,0,-528,0,0,0,43.811,66.99%,49.07,0.00,395.11,844,-758,-528,4701,185
        10/12/2026,8:05:39,180444,00:04:08,1,0,239,0,0,0,43.295,66.20%,47.89,381.40,389.93,437,-113,0,4728,186
    """.trimIndent()

    private val trips = LeafSpyImport.parse(csv, ZoneOffset.UTC)

    @Test fun keepsDrivesSkipsChargesAndCorruptRows() {
        assertEquals(3, trips.size)           // charge row (dist 0, energy < 0) and dist 179438 row dropped
        assertEquals(listOf(32.0, 18.0, 1.0), trips.map { it.distKm })
    }

    @Test fun mapsColumns() {
        val t = trips[0]
        assertEquals(1783165944000L, t.startMs)              // 2026-07-04 11:52:24 UTC
        assertEquals(t.startMs + (59 * 60 + 19) * 1000L, t.endMs)
        assertEquals(175222.0, t.startOdoKm!!, 0.0)
        assertEquals(5293.0, t.energyWh, 0.0)
        assertEquals(186 * 77.5 / 1000, t.startKwh, 1e-9)    // gids x 77.5 Wh
        assertEquals(44.171, t.ah!!, 1e-9)
        assertEquals(67.54, t.soh!!, 1e-9)                   // "%" stripped
        assertEquals(49.85, t.hx!!, 1e-9)
        assertEquals(393.14, t.startV!!, 1e-9)
        assertEquals(382.18, t.endV!!, 1e-9)
        assertNull(t.startSoc)
        assertNull(t.batTempC)
    }

    @Test fun zeroGidsAndZeroVoltsBecomeUnknown() {
        val t = trips[1]
        assertEquals(0.0, t.startKwh, 0.0)
        assertEquals(0.0, t.endKwh, 0.0)
        assertEquals(44.158, t.ah!!, 1e-9)
        assertNull(LeafSpyImport.parse(csv.replace("393.14", "0.00"), ZoneOffset.UTC)[0].startV)
    }

    @Test fun dayMonthDateFormatIsDetected() {
        val dmy = csv.replace("07/04/2026", "04/07/2026").replace("09/18/2026", "18/09/2026").replace("10/12/2026", "12/10/2026")
        assertEquals(trips.map { it.startMs }, LeafSpyImport.parse(dmy, ZoneOffset.UTC).map { it.startMs })
    }

    @Test fun milesAreConverted() {
        val mi = csv.replace("odo km", "odo mi").replace("dist km", "dist mi")
        val t = LeafSpyImport.parse(mi, ZoneOffset.UTC)[0]
        assertEquals(32 * 1.609344, t.distKm, 1e-9)
        assertEquals(175222 * 1.609344, t.startOdoKm!!, 1e-6)
    }

    @Test fun wrongHeaderGivesNothing() {
        assertEquals(0, LeafSpyImport.parse("Date,Time,foo\n1,2,3\n", ZoneOffset.UTC).size)
    }

    @Test fun mergeSkipsTripsOverlappingExistingOnes() {
        val existing = listOf(trips[1].copy(startMs = trips[1].startMs + 3 * 60_000, endMs = trips[1].endMs + 60_000))
        val merged = LeafSpyImport.merge(existing, trips)
        assertEquals(3, merged.size)                                   // existing + 2 new, overlapping one dropped
        assertEquals(merged.sortedBy { it.startMs }, merged)           // oldest first
        assertEquals(existing[0], merged[1])
        assertEquals(3, LeafSpyImport.merge(merged, trips).size)      // re-import adds nothing
    }
}
