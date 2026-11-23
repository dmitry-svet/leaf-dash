package com.leafdash.stats

import com.leafdash.trip.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 23)   // a Monday

    private fun trip(date: LocalDate, km: Double, wh: Double, hour: Int = 12) = TripRecord(
        startMs = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        endMs = date.atTime(hour, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        startOdoKm = null, distKm = km, energyWh = wh, startKwh = 0.0, endKwh = 0.0,
        startSoc = null, endSoc = null, ah = null, soh = null, hx = null, startV = null, endV = null,
        cellMinV = null, maxSpreadMv = null, batTempC = null, extTempC = null,
    )

    private val trips = listOf(
        trip(today, 10.0, 1500.0),
        trip(today, 5.0, 800.0, hour = 18),            // same day, second trip
        trip(today.minusDays(1), 20.0, 3000.0),
        trip(today.minusDays(6), 7.0, 1000.0),         // first day of the week window
        trip(today.minusDays(7), 100.0, 15000.0),      // outside the week, inside the month
        trip(today.minusDays(29), 3.0, 500.0),         // last day of the month window
        trip(today.minusDays(30), 50.0, 9000.0),       // outside the month, inside the year
        trip(today.minusDays(400), 999.0, 99999.0),    // outside everything
    )

    @Test fun weekHasSevenDailyBucketsEndingToday() {
        val w = Stats.week(trips, today, zone)
        assertEquals(7, w.buckets.size)
        assertEquals(listOf(7.0, 0.0, 0.0, 0.0, 0.0, 20.0, 15.0), w.buckets.map { it.km })
        assertEquals(2300.0, w.buckets.last().wh, 1e-9)
        assertEquals(42.0, w.totalKm, 1e-9)
        assertEquals(6300.0, w.totalWh, 1e-9)
        assertEquals(4, w.trips)
    }

    @Test fun monthHasThirtyDailyBuckets() {
        val m = Stats.month(trips, today, zone)
        assertEquals(30, m.buckets.size)
        assertEquals(3.0, m.buckets.first().km, 1e-9)
        assertEquals(15.0, m.buckets.last().km, 1e-9)
        assertEquals(145.0, m.totalKm, 1e-9)
        assertEquals(6, m.trips)
    }

    @Test fun yearHasFiftyTwoWeeklyBucketsEndingThisWeek() {
        val y = Stats.year(trips, today, zone)
        assertEquals(52, y.buckets.size)
        // this week = Mon 23.11 only (today is Monday): 15 km; last week: 20 + 7 + 100
        assertEquals(15.0, y.buckets.last().km, 1e-9)
        assertEquals(127.0, y.buckets[50].km, 1e-9)
        assertEquals(195.0, y.totalKm, 1e-9)
        assertEquals(7, y.trips)
    }

    @Test fun whPerKmNullWithoutDistance() {
        val empty = Stats.week(emptyList(), today, zone)
        assertEquals(null, empty.whPerKm)
        assertEquals(150.0, Stats.week(trips, today, zone).whPerKm!!, 1e-9)
    }

    @Test fun bucketLabels() {
        val w = Stats.week(trips, today, zone)
        assertEquals("17", w.buckets.first().label)      // day of month
        assertEquals("23", w.buckets.last().label)
        val y = Stats.year(trips, today, zone)
        assertEquals("23.11", y.buckets.last().label)    // week start (Monday)
    }

    @Test
    fun gridStepGivesTwoToFiveLines() {
        assertEquals(20.0, Stats.gridStep(93.0), 0.0)      // 5 lines
        assertEquals(100.0, Stats.gridStep(413.0), 0.0)    // 5 lines
        assertEquals(500.0, Stats.gridStep(1054.0), 0.0)   // 3 lines
        assertEquals(5.0, Stats.gridStep(19.3), 0.0)       // 4 lines
        assertEquals(0.2, Stats.gridStep(0.8), 1e-9)       // 4 lines
        assertEquals(1.0, Stats.gridStep(0.0), 0.0)        // empty chart
    }
}
