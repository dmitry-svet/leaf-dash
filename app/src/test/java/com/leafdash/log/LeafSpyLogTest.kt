package com.leafdash.log

import com.leafdash.can.LeafState
import org.junit.Assert.assertEquals
import org.junit.Test

class LeafSpyLogTest {

    private val cells = List(96) { 4040 + it % 20 }     // 4040..4059 mV
    private val leaf = LeafState(
        socPercent = 86.5123,
        ahCapacity = 43.30,
        packVolts = 389.17,
        aux12V = 12.96,
        ambientTempC = 20.0,
        speedKmh = 42.0,
        sohPercent = 66.21,
        hx = 47.93,
        batteryTempsC = listOf(21.3, 21.4, 20.6),
        cellMinV = 4.040,
        cellMaxV = 4.059,
        cellsMv = cells,
    )
    private val row = LeafSpyLog.row(
        tMs = 1_791_000_000_000, leaf = leaf, odoKm = 180328.4, phoneBattery = 67,
    )
    private fun col(name: String) = row[LeafSpyLog.HEADER.indexOf(name)]

    @Test fun headerMatchesLeafSpyLayout() {
        assertEquals(151, LeafSpyLog.HEADER.size)              // columns A..EU
        assertEquals("Date/Time", LeafSpyLog.HEADER[0])
        assertEquals("CP1", LeafSpyLog.HEADER[23])             // column X
        assertEquals("CP96", LeafSpyLog.HEADER[118])           // column DO
        assertEquals("OBC", LeafSpyLog.HEADER.last())          // column EU
    }

    @Test fun rowHasOneValuePerColumn() {
        assertEquals(LeafSpyLog.HEADER.size, row.size)
    }

    @Test fun socAndAhAreScaledIntegers() {
        assertEquals("865123", col("SOC"))
        assertEquals("433000", col("AHr"))
    }

    @Test fun cellStatsAndAllCells() {
        assertEquals("4059", col("Max CP mV"))
        assertEquals("4040", col("Min CP mV"))
        assertEquals("19", col("CP mV Diff"))
        assertEquals("4040", col("CP1"))
        assertEquals("4055", col("CP96"))                      // 4040 + 95 % 20
        // pack volts = average cell pair x 96, like LeafSpy
        assertEquals(String.format(java.util.Locale.US, "%.2f", cells.average() * 96 / 1000.0), col("Pack Volts"))
    }

    @Test fun temperaturesMapToSensors124() {
        // AZE0 has no sensor 3: our three readings are sensors 1, 2 and 4
        assertEquals("21.3", col("Pack T1 C"))
        assertEquals("70.3", col("Pack T1 F"))
        assertEquals("21.4", col("Pack T2 C"))
        assertEquals("na", col("Pack T3 C"))
        assertEquals("20.6", col("Pack T4 C"))
    }

    @Test fun otherFields() {
        assertEquals("47.93", col("Hx"))
        assertEquals("66.21", col("SOH"))
        assertEquals("12.96V", col("12v Bat Volts"))
        assertEquals("180328", col("Odo(km)"))
        assertEquals("68", col("Ambient"))                     // Fahrenheit
        assertEquals("67", col("BLevel"))
        assertEquals("1791000000", col("epoch time"))
        assertEquals("389.17", col("HVolt1"))
        assertEquals("", col("Gids"))                          // not readable: blank
    }
}
